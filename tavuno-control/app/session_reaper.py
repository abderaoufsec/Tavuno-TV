"""Session reaper for expired playback sessions."""

import logging
from typing import Any

logger = logging.getLogger("tavuno-control.session_reaper")


def reap_expired_sessions(connection: Any) -> int:
    """
    Reap expired playback sessions by updating their status to 'expired'.
    
    Finds sessions where status = 'active' AND expires_at < NOW()
    and updates them to status = 'expired'.
    
    Returns the number of sessions reaped.
    """
    result = connection.execute(
        """
        UPDATE tavuno_playback_sessions
        SET status = 'expired'
        WHERE status = 'active' AND expires_at < NOW()
        RETURNING id
        """
    )
    
    reaped_count = len(result.fetchall()) if hasattr(result, 'fetchall') else result.rowcount
    return reaped_count
