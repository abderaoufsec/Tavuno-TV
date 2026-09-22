-- Migration 005: Make directus_user nullable to allow registration
-- This fixes the NOT NULL constraint that was blocking registration

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'tavuno_profiles' AND column_name = 'directus_user'
    ) THEN
        ALTER TABLE tavuno_profiles ALTER COLUMN directus_user DROP NOT NULL;
    END IF;
END $$;
