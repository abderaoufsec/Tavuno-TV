-- M9 Migration: Extend tavuno_profiles and tavuno_devices for authentication
-- Add email, password_hash, role to tavuno_profiles
-- Add device_fingerprint, last_seen_at, revoked_at, platform to tavuno_devices
-- Make directus_user nullable to allow registration

-- Add email column if not exists
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'tavuno_profiles' AND column_name = 'email'
    ) THEN
        ALTER TABLE tavuno_profiles ADD COLUMN email TEXT UNIQUE NOT NULL DEFAULT '';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'tavuno_profiles' AND column_name = 'password_hash'
    ) THEN
        ALTER TABLE tavuno_profiles ADD COLUMN password_hash TEXT NOT NULL DEFAULT '';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'tavuno_profiles' AND column_name = 'role'
    ) THEN
        ALTER TABLE tavuno_profiles ADD COLUMN role TEXT NOT NULL DEFAULT 'user';
    END IF;
END $$;

-- Make directus_user nullable if it exists
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'tavuno_profiles' AND column_name = 'directus_user'
    ) THEN
        ALTER TABLE tavuno_profiles ALTER COLUMN directus_user DROP NOT NULL;
    END IF;
END $$;

-- Add device columns if not exists
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'tavuno_devices' AND column_name = 'device_fingerprint'
    ) THEN
        ALTER TABLE tavuno_devices ADD COLUMN device_fingerprint TEXT UNIQUE;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'tavuno_devices' AND column_name = 'last_seen_at'
    ) THEN
        ALTER TABLE tavuno_devices ADD COLUMN last_seen_at TIMESTAMP WITH TIME ZONE;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'tavuno_devices' AND column_name = 'revoked_at'
    ) THEN
        ALTER TABLE tavuno_devices ADD COLUMN revoked_at TIMESTAMP WITH TIME ZONE;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'tavuno_devices' AND column_name = 'platform'
    ) THEN
        ALTER TABLE tavuno_devices ADD COLUMN platform TEXT;
    END IF;
END $$;
