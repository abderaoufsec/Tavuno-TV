-- Migration: Add unique constraint for channel_sources idempotency
-- Ensures that (channel, provider, external_id) combinations are unique
-- This is critical for reliable sync operations with Dispatcharr 0.28

DO $$
BEGIN
    -- First, remove any existing duplicates
    DELETE FROM tavuno_channel_sources ct1
    USING tavuno_channel_sources ct2
    WHERE ct1.id < ct2.id
      AND ct1.channel = ct2.channel
      AND ct1.provider = ct2.provider
      AND ct1.external_id = ct2.external_id;

    -- Add the unique constraint
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'tavuno_channel_sources_channel_provider_external_id_unique'
    ) THEN
        ALTER TABLE tavuno_channel_sources
        ADD CONSTRAINT tavuno_channel_sources_channel_provider_external_id_unique
        UNIQUE (channel, provider, external_id);
    END IF;
END $$;