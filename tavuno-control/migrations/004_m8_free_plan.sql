-- M8.3 Migration: Add default free plan for FREE_LAUNCH mode
-- This plan is used when FREE_LAUNCH=true to automatically assign subscriptions to new registrations

-- Insert default free plan if it doesn't exist
INSERT INTO tavuno_plans (name, code, description, max_devices, max_concurrent_streams, is_active)
VALUES ('Free Launch', 'free_launch', 'Default plan for free launch with unlimited devices and 2 concurrent streams', 999, 2, TRUE)
ON CONFLICT (code) DO NOTHING;

-- Set the plan ID to 1 for easy reference (if this is the first plan)
-- This is for documentation purposes - the actual ID is auto-generated
-- Users should set DEFAULT_PLAN_ID=1 in their environment configuration
