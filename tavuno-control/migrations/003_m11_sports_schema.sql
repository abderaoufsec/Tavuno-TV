-- M11 Sports Schema
-- Adds sports-specific tables for competitions, teams, and matches

-- tavuno_competitions table
CREATE TABLE IF NOT EXISTS tavuno_competitions (
    id SERIAL PRIMARY KEY,
    name VARCHAR(120) NOT NULL UNIQUE,
    slug VARCHAR(120) NOT NULL UNIQUE,
    sport VARCHAR(64) NOT NULL, -- e.g., "football", "basketball", "tennis"
    category_id INTEGER REFERENCES tavuno_categories(id) ON DELETE SET NULL,
    external_id VARCHAR(120), -- Provider competition ID
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- tavuno_teams table
CREATE TABLE IF NOT EXISTS tavuno_teams (
    id SERIAL PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    slug VARCHAR(120) NOT NULL UNIQUE,
    competition_id INTEGER REFERENCES tavuno_competitions(id) ON DELETE SET NULL,
    logo VARCHAR(255), -- UUID as string
    external_id VARCHAR(120), -- Provider team ID
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- tavuno_matches table
CREATE TABLE IF NOT EXISTS tavuno_matches (
    id SERIAL PRIMARY KEY,
    competition_id INTEGER NOT NULL REFERENCES tavuno_competitions(id) ON DELETE CASCADE,
    home_team_id INTEGER NOT NULL REFERENCES tavuno_teams(id) ON DELETE CASCADE,
    away_team_id INTEGER NOT NULL REFERENCES tavuno_teams(id) ON DELETE CASCADE,
    channel_id INTEGER REFERENCES tavuno_channels(id) ON DELETE SET NULL,
    kickoff TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(32) DEFAULT 'upcoming', -- 'upcoming', 'live', 'finished', 'postponed'
    home_score INTEGER,
    away_score INTEGER,
    external_id VARCHAR(120), -- Provider match ID
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Indexes for performance
CREATE INDEX IF NOT EXISTS idx_tavuno_competitions_sport ON tavuno_competitions(sport);
CREATE INDEX IF NOT EXISTS idx_tavuno_competitions_category ON tavuno_competitions(category_id);
CREATE INDEX IF NOT EXISTS idx_tavuno_teams_competition ON tavuno_teams(competition_id);
CREATE INDEX IF NOT EXISTS idx_tavuno_matches_competition ON tavuno_matches(competition_id);
CREATE INDEX IF NOT EXISTS idx_tavuno_matches_kickoff ON tavuno_matches(kickoff);
CREATE INDEX IF NOT EXISTS idx_tavuno_matches_status ON tavuno_matches(status);
CREATE INDEX IF NOT EXISTS idx_tavuno_matches_channel ON tavuno_matches(channel_id);

-- Updated at trigger function (reuse or create)
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Apply triggers
CREATE TRIGGER update_tavuno_competitions_updated_at BEFORE UPDATE ON tavuno_competitions
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_tavuno_teams_updated_at BEFORE UPDATE ON tavuno_teams
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_tavuno_matches_updated_at BEFORE UPDATE ON tavuno_matches
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
