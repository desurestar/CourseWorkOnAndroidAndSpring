-- V3__add_client_id_to_posts.sql
-- Adds client_id column for offline-first draft synchronization
-- Adds unique constraint to prevent duplicate syncs

-- Add client_id column (nullable UUID)
ALTER TABLE posts ADD COLUMN IF NOT EXISTS client_id VARCHAR(36);

-- Add unique constraint on (author_id, client_id) where client_id is not null
-- This prevents duplicate posts from being created during synchronization
CREATE UNIQUE INDEX IF NOT EXISTS idx_posts_author_client_id 
    ON posts (author_id, client_id) 
    WHERE client_id IS NOT NULL;

-- Add index for querying posts by client_id
CREATE INDEX IF NOT EXISTS idx_posts_client_id ON posts (client_id) 
    WHERE client_id IS NOT NULL;
