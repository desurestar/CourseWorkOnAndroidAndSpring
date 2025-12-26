-- V4__create_comments_table.sql
-- Creates comments table and indexes. Application updates posts.comments_count.

CREATE TABLE IF NOT EXISTS comments (
    id BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    post_id BIGINT NOT NULL,
    author_id BIGINT NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE,
    client_id VARCHAR(36),
    CONSTRAINT fk_comments_post FOREIGN KEY (post_id) REFERENCES posts(id) ON DELETE CASCADE,
    CONSTRAINT fk_comments_author FOREIGN KEY (author_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_comments_post ON comments (post_id);
CREATE INDEX IF NOT EXISTS idx_comments_author ON comments (author_id);
