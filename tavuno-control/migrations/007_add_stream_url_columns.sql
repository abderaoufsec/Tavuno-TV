-- Migration: Add stream_url columns to VOD tables
-- This migration adds stream_url columns to support VOD playback

-- Add stream_url to tavuno_movies if it doesn't exist
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'tavuno_movies' AND column_name = 'stream_url'
    ) THEN
        ALTER TABLE tavuno_movies ADD COLUMN stream_url TEXT;
    END IF;
END $$;

-- Add stream_url to tavuno_episodes if it doesn't exist
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'tavuno_episodes' AND column_name = 'stream_url'
    ) THEN
        ALTER TABLE tavuno_episodes ADD COLUMN stream_url TEXT;
    END IF;
END $$;
