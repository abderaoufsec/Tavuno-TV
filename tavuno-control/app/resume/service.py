"""Durable per-profile resume positions (A6).

The natural place to record "the viewer is 40 minutes in" is the playback
session, and it is the wrong place: ``tavuno_playback_sessions`` is an
authorization lease that :mod:`app.session_reaper` deletes once it expires, so a
progress write there would be gone minutes later. This table is the durable
record; nothing expires it except the profile.

Two behaviours worth naming, because both are decisions rather than defaults:

* **A finished item is forgotten.** Past :data:`COMPLETION_RATIO` the viewer has
  effectively finished, so continuing to surface it in "continue watching" with a
  nearly full bar is noise. :meth:`ResumeService.record` clears the row there.
* **Rewinding to the start clears it too.** Position 0 with nothing to resume is
  the same as no progress, and keeping the row would put a 0% item on the rail.
"""

from contextlib import contextmanager
from typing import Any, Dict, Iterable, List, Optional

from ..content_refs import fetch_items, validate_kind
from .models import ProgressPayload

# At or past this fraction the item counts as watched. Matches the convention
# players use for their "resume or start over" prompt.
COMPLETION_RATIO = 0.95


class ResumeService:
    def __init__(self, services):
        self.services = services

    @contextmanager
    def _db(self):
        with self.services.connection() as connection:
            yield connection

    @staticmethod
    def ratio(position_ms: int, duration_ms: int) -> Optional[float]:
        """Progress as 0..1, or ``None`` when the duration is unknown.

        ``None`` (not ``0.0``) for an unknown duration is deliberate: live
        channels have no end, and a 0.0 would render an empty progress bar on
        every live row. ``None`` means "no bar".
        """
        if duration_ms <= 0:
            return None
        return max(0.0, min(1.0, position_ms / duration_ms))

    def _payload(self, kind: str, item_id: int, position_ms: int, duration_ms: int) -> ProgressPayload:
        return ProgressPayload(
            kind=kind,
            item_id=item_id,
            position_ms=position_ms,
            duration_ms=duration_ms,
            progress=self.ratio(position_ms, duration_ms),
        )

    def record(
        self,
        profile_id: int,
        kind: str,
        item_id: int,
        position_ms: int,
        duration_ms: int = 0,
    ) -> Optional[ProgressPayload]:
        """Store a position. Returns the stored payload, or ``None`` if cleared."""
        validate_kind(kind)
        position = max(0, int(position_ms))
        duration = max(0, int(duration_ms))
        ratio = self.ratio(position, duration)
        finished = ratio is not None and ratio >= COMPLETION_RATIO
        restarted = position <= 0

        with self._db() as conn:
            if finished or restarted:
                conn.execute(
                    "DELETE FROM tavuno_resume WHERE profile = %s AND kind = %s AND item_id = %s",
                    (int(profile_id), kind, int(item_id)),
                )
                conn.commit()
                return None

            row = conn.execute(
                "INSERT INTO tavuno_resume (profile, kind, item_id, position_ms, duration_ms)"
                " VALUES (%s, %s, %s, %s, %s)"
                " ON CONFLICT (profile, kind, item_id) DO UPDATE"
                " SET position_ms = EXCLUDED.position_ms,"
                "     duration_ms = EXCLUDED.duration_ms,"
                "     updated_at = NOW()"
                " RETURNING position_ms, duration_ms",
                (int(profile_id), kind, int(item_id), position, duration),
            ).fetchone()
            conn.commit()

        return self._payload(
            kind,
            int(item_id),
            int(row["position_ms"]) if row else position,
            int(row["duration_ms"]) if row else duration,
        )

    def get(self, profile_id: int, kind: str, item_id: int) -> Optional[ProgressPayload]:
        """One item's progress, or ``None`` when there is nothing to resume."""
        validate_kind(kind)
        with self._db() as conn:
            row = conn.execute(
                "SELECT position_ms, duration_ms FROM tavuno_resume"
                " WHERE profile = %s AND kind = %s AND item_id = %s",
                (int(profile_id), kind, int(item_id)),
            ).fetchone()
        if row is None:
            return None
        return self._payload(kind, int(item_id), int(row["position_ms"]), int(row["duration_ms"]))

    def by_kind(
        self,
        profile_id: int,
        kind: str,
        item_ids: Iterable[int],
    ) -> Dict[int, ProgressPayload]:
        """Progress for a specific set of items, keyed by id.

        The counterpart to :meth:`FavouritesService.marked`: the catalog already
        holds these ids, so this asks only about them rather than loading every
        row the profile has ever watched.
        """
        validate_kind(kind)
        ids = [int(value) for value in item_ids]
        if not ids:
            return {}
        placeholders = ",".join(["%s"] * len(ids))
        with self._db() as conn:
            rows = conn.execute(
                "SELECT item_id, position_ms, duration_ms FROM tavuno_resume"
                f" WHERE profile = %s AND kind = %s AND item_id IN ({placeholders})",
                tuple([int(profile_id), kind] + ids),
            ).fetchall()
        return {
            int(row["item_id"]): self._payload(
                kind,
                int(row["item_id"]),
                int(row["position_ms"]),
                int(row["duration_ms"]),
            )
            for row in rows
        }

    def recent(self, profile_id: int, limit: int = 20) -> List[Dict[str, Any]]:
        """Every resumable item, most recently watched first.

        Mixed-kind on purpose: "continue watching" is one rail spanning movies,
        series and episodes, so it reads across kinds and only then resolves the
        rows (skipping any the catalog has stopped listing).

        Each returned row is the catalog row **plus** the four resume keys —
        ``kind``, ``position_ms``, ``duration_ms``, ``progress``. The kind is not
        decoration: the ids live in four different tables, so a caller cannot
        resolve a row without being told which one it came from.
        """
        with self._db() as conn:
            rows = conn.execute(
                "SELECT kind, item_id, position_ms, duration_ms FROM tavuno_resume"
                " WHERE profile = %s"
                " ORDER BY updated_at DESC, id DESC",
                (int(profile_id),),
            ).fetchall()

        grouped: Dict[str, List[Dict[str, Any]]] = {}
        for row in rows:
            grouped.setdefault(str(row["kind"]), []).append(row)

        results: List[Dict[str, Any]] = []
        with self._db() as conn:
            for kind, resume_rows in grouped.items():
                try:
                    fetched = fetch_items(conn, kind, [int(r["item_id"]) for r in resume_rows])
                except ValueError:
                    # A row under a kind this build does not know must not take
                    # the whole rail down.
                    continue
                for resume_row in resume_rows:
                    item = fetched.get(int(resume_row["item_id"]))
                    if item is None:
                        continue
                    position = int(resume_row["position_ms"])
                    duration = int(resume_row["duration_ms"])
                    results.append(
                        {
                            **item,
                            "kind": kind,
                            "position_ms": position,
                            "duration_ms": duration,
                            "progress": self.ratio(position, duration),
                        }
                    )

        return results[:limit] if limit else results