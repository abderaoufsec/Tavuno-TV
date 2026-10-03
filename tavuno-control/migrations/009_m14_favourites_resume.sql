-- M14/A6 Migration: favourites, per-profile resume progress, and the artwork
-- columns the catalog now reads.
--
-- Two additive tables plus a column reconciliation. All are idempotent, so
-- re-running is safe and a fresh database converges on the same shape.
--
--  0. The artwork columns (poster/backdrop/duration/release_year/thumbnail).
--     Migration 006 declared these inside CREATE TABLE IF NOT EXISTS, which does
--     NOT add a column to a table that already exists — so a database that
--     predates 006 never got them, and 006's own note only covered code that did
--     not *select* those columns. A6 is the first code to select them, so it is
--     also the first thing that breaks without the reconciliation below.
--
--  1. tavuno_favourites — one row per (profile, kind, item). This is a *like*, not
--     an ordering override: `tavuno_customizations` (008) already owns "where does
--     this sit in my rails / hide it", and overloading that table would make
--     "favourite" mean two different things depending on who asked. Keyed on
--     (profile, kind, item_id) so a double-tap upserts instead of erroring.
--
--  2. tavuno_resume — one row per (profile, kind, item) carrying how far in the
--     viewer got. `tavuno_playback_sessions` deliberately does NOT serve here: it
--     is a transient authorization lease that the reaper deletes, so progress
--     recorded there would evaporate. This table is the durable record, keyed the
--     same way so a caller can join them without a second lookup.
--
-- kind is a TEXT tag rather than a FK: 'channel', 'movie', 'series', 'episode'.
-- The item ids live in four different tables with no common parent, so a FK
-- could only ever cover one of them, and a polymorphic reference is the honest
-- shape. The service validates the kind and checks the item exists before writing.

ALTER TABLE tavuno_movies ADD COLUMN IF NOT EXISTS poster VARCHAR(255);
ALTER TABLE tavuno_movies ADD COLUMN IF NOT EXISTS backdrop VARCHAR(255);
ALTER TABLE tavuno_movies ADD COLUMN IF NOT EXISTS duration VARCHAR(32);
ALTER TABLE tavuno_series ADD COLUMN IF NOT EXISTS poster VARCHAR(255);
ALTER TABLE tavuno_series ADD COLUMN IF NOT EXISTS backdrop VARCHAR(255);
ALTER TABLE tavuno_series ADD COLUMN IF NOT EXISTS release_year INTEGER;
ALTER TABLE tavuno_seasons ADD COLUMN IF NOT EXISTS poster VARCHAR(255);
ALTER TABLE tavuno_episodes ADD COLUMN IF NOT EXISTS thumbnail VARCHAR(255);

CREATE TABLE IF NOT EXISTS tavuno_favourites (
    id BIGSERIAL PRIMARY KEY,
    profile INTEGER NOT NULL REFERENCES tavuno_profiles(id) ON DELETE CASCADE,
    kind TEXT NOT NULL,
    item_id INTEGER NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT tavuno_favourites_profile_kind_item_key UNIQUE (profile, kind, item_id)
);

-- The rails read "this profile's favourites, newest first", so the index covers
-- exactly that: (profile, kind) with created_at descending.
CREATE INDEX IF NOT EXISTS idx_tavuno_favourites_profile_kind
    ON tavuno_favourites (profile, kind, created_at DESC);

CREATE TABLE IF NOT EXISTS tavuno_resume (
    id BIGSERIAL PRIMARY KEY,
    profile INTEGER NOT NULL REFERENCES tavuno_profiles(id) ON DELETE CASCADE,
    kind TEXT NOT NULL,
    item_id INTEGER NOT NULL,
    position_ms BIGINT NOT NULL DEFAULT 0,
    duration_ms BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT tavuno_resume_profile_kind_item_key UNIQUE (profile, kind, item_id)
);

-- "Continue watching" is ordered by recency, so updated_at is part of the key.
CREATE INDEX IF NOT EXISTS idx_tavuno_resume_profile_kind
    ON tavuno_resume (profile, kind, updated_at DESC);