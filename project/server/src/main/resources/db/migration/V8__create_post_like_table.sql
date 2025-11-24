-- V8__create_post_like_table.sql
CREATE TABLE post_like (
                           post_id BIGINT NOT NULL,
                           user_id BIGINT NOT NULL,
                           created_at TIMESTAMPTZ DEFAULT now(),
                           PRIMARY KEY (post_id, user_id)
);

ALTER TABLE post_like
    ADD CONSTRAINT fk_postlike_post
        FOREIGN KEY (post_id) REFERENCES posts (id) ON DELETE CASCADE;

ALTER TABLE post_like
    ADD CONSTRAINT fk_postlike_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE;

CREATE INDEX idx_post_like_user_id ON post_like (user_id);
CREATE INDEX idx_post_like_post_id ON post_like (post_id);
