-- V5__create_post_tags_table.sql
CREATE TABLE post_tags (
                           post_id BIGINT NOT NULL,
                           tag_id BIGINT NOT NULL,
                           PRIMARY KEY (post_id, tag_id)
);

ALTER TABLE post_tags
    ADD CONSTRAINT fk_posttags_post
        FOREIGN KEY (post_id) REFERENCES posts (id) ON DELETE CASCADE;

ALTER TABLE post_tags
    ADD CONSTRAINT fk_posttags_tag
        FOREIGN KEY (tag_id) REFERENCES tags (id) ON DELETE CASCADE;

CREATE INDEX idx_post_tags_tag_id ON post_tags (tag_id);
CREATE INDEX idx_post_tags_post_id ON post_tags (post_id);
