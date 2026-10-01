-- M14 Migration: per-profile customization (order + visibility) and multi-profile support.
--
-- Two independent additions, both additive and idempotent so re-running is safe:
--
--  1. tavuno_customizations — a per-profile override of the catalog's natural order
--     and visibility for channels and categories. Nullable-free "pin + hide" model:
--     a row may set sort_order (pin an item) and/or is_hidden (drop it). Items with
--     no row keep the catalog's natural order, so an untouched profile sees exactly
--     what it saw before this table existed.
--
--  2. tavuno_profiles gains owner_profile / is_kids / avatar so one account can hold
--     several viewing profiles (the OwnTV "Who's watching?" model). owner_profile is
--     NULL on an account row and points at the account for each child profile.
--
--     email is relaxed to NULLABLE as part of the same change: it is UNIQUE, and a
--     child profile has no email of its own, so NOT NULL would force every child to
--     share one synthetic address and collide on the second insert. Postgres treats
--     NULLs as distinct in a UNIQUE index, which is exactly the behaviour needed.

CREATE TABLE IF NOT EXISTS tavuno_customizations (
    id BIGSERIAL PRIMARY KEY,
    profile INTEGER NOT NULL REFERENCES tavuno_profiles(id) ON DELETE CASCADE,
    kind TEXT NOT NULL,
    item_id INTEGER NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    is_hidden BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT tavuno_customizations_profile_kind_item_key UNIQUE (profile, kind, item_id)
);

CREATE INDEX IF NOT EXISTS idx_tavuno_customizations_profile_kind
    ON tavuno_customizations (profile, kind);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'tavuno_profiles' AND column_name = 'owner_profile'
    ) THEN
        ALTER TABLE tavuno_profiles
            ADD COLUMN owner_profile INTEGER REFERENCES tavuno_profiles(id) ON DELETE CASCADE;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'tavuno_profiles' AND column_name = 'is_kids'
    ) THEN
        ALTER TABLE tavuno_profiles ADD COLUMN is_kids BOOLEAN NOT NULL DEFAULT FALSE;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'tavuno_profiles' AND column_name = 'avatar'
    ) THEN
        ALTER TABLE tavuno_profiles ADD COLUMN avatar TEXT;
    END IF;
END $$;

-- A child profile carries no credentials, so the account-only email column must
-- accept NULL. Guarded because ALTER COLUMN is not idempotent in older Postgres.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'tavuno_profiles'
          AND column_name = 'email'
          AND is_nullable = 'NO'
    ) THEN
        ALTER TABLE tavuno_profiles ALTER COLUMN email DROP NOT NULL;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_tavuno_profiles_owner ON tavuno_profiles (owner_profile);