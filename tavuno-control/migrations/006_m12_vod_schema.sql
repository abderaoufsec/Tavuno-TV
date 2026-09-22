-- M12 Migration: VOD Schema for Movies and Series
-- Adds tables for movies, series, seasons, and episodes

-- tavuno_movies table
CREATE TABLE IF NOT EXISTS tavuno_movies (
    id SERIAL PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    slug VARCHAR(255) NOT NULL UNIQUE,
    category_id INTEGER REFERENCES tavuno_categories(id) ON DELETE SET NULL,
    synopsis TEXT,
    release_year INTEGER,
    poster VARCHAR(255), -- UUID as string
    backdrop VARCHAR(255), -- UUID as string
    duration VARCHAR(32), -- e.g., "1h 45m" or "105m"
    external_id VARCHAR(120), -- Provider movie ID
    provider VARCHAR(64), -- e.g., "dispatcharr"
    stream_url TEXT, -- Playback URL from provider
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- tavuno_series table
CREATE TABLE IF NOT EXISTS tavuno_series (
    id SERIAL PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    slug VARCHAR(255) NOT NULL UNIQUE,
    category_id INTEGER REFERENCES tavuno_categories(id) ON DELETE SET NULL,
    synopsis TEXT,
    poster VARCHAR(255), -- UUID as string
    backdrop VARCHAR(255), -- UUID as string
    release_year INTEGER,
    external_id VARCHAR(120), -- Provider series ID
    provider VARCHAR(64), -- e.g., "dispatcharr"
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- tavuno_seasons table
CREATE TABLE IF NOT EXISTS tavuno_seasons (
    id SERIAL PRIMARY KEY,
    series INTEGER NOT NULL REFERENCES tavuno_series(id) ON DELETE CASCADE,
    season_number INTEGER NOT NULL,
    title VARCHAR(255),
    poster VARCHAR(255), -- UUID as string
    external_id VARCHAR(120), -- Provider season ID
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    UNIQUE(series, season_number)
);

-- tavuno_episodes table
CREATE TABLE IF NOT EXISTS tavuno_episodes (
    id SERIAL PRIMARY KEY,
    season INTEGER NOT NULL REFERENCES tavuno_seasons(id) ON DELETE CASCADE,
    episode_number INTEGER NOT NULL,
    title VARCHAR(255) NOT NULL,
    synopsis TEXT,
    duration VARCHAR(32), -- e.g., "45m"
    thumbnail VARCHAR(255), -- UUID as string
    external_id VARCHAR(120), -- Provider episode ID
    stream_url TEXT, -- Playback URL from provider
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    UNIQUE(season, episode_number)
);

-- Indexes for performance
CREATE INDEX IF NOT EXISTS idx_tavuno_movies_slug ON tavuno_movies(slug);
CREATE INDEX IF NOT EXISTS idx_tavuno_movies_category ON tavuno_movies(category_id);
CREATE INDEX IF NOT EXISTS idx_tavuno_movies_external ON tavuno_movies(external_id);
CREATE INDEX IF NOT EXISTS idx_tavuno_series_slug ON tavuno_series(slug);
CREATE INDEX IF NOT EXISTS idx_tavuno_series_category ON tavuno_series(category_id);
CREATE INDEX IF NOT EXISTS idx_tavuno_series_external ON tavuno_series(external_id);
CREATE INDEX IF NOT EXISTS idx_tavuno_seasons_series ON tavuno_seasons(series);
CREATE INDEX IF NOT EXISTS idx_tavuno_seasons_number ON tavuno_seasons(season_number);
CREATE INDEX IF NOT EXISTS idx_tavuno_episodes_season ON tavuno_episodes(season);
CREATE INDEX IF NOT EXISTS idx_tavuno_episodes_number ON tavuno_episodes(episode_number);
CREATE INDEX IF NOT EXISTS idx_tavuno_episodes_external ON tavuno_episodes(external_id);

-- Updated at trigger function (reuse if exists, create if not)
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Apply triggers
DROP TRIGGER IF EXISTS update_tavuno_movies_updated_at ON tavuno_movies;
CREATE TRIGGER update_tavuno_movies_updated_at BEFORE UPDATE ON tavuno_movies
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

DROP TRIGGER IF EXISTS update_tavuno_series_updated_at ON tavuno_series;
CREATE TRIGGER update_tavuno_series_updated_at BEFORE UPDATE ON tavuno_series
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

DROP TRIGGER IF EXISTS update_tavuno_seasons_updated_at ON tavuno_seasons;
CREATE TRIGGER update_tavuno_seasons_updated_at BEFORE UPDATE ON tavuno_seasons
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

DROP TRIGGER IF EXISTS update_tavuno_episodes_updated_at ON tavuno_episodes;
CREATE TRIGGER update_tavuno_episodes_updated_at BEFORE UPDATE ON tavuno_episodes
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
