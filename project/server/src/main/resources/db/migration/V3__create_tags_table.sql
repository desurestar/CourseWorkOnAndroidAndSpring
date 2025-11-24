-- V3__create_tags_table.sql
CREATE TABLE tags (
                      id BIGSERIAL PRIMARY KEY,
                      name VARCHAR(100) NOT NULL UNIQUE,
                      slug VARCHAR(100),
                      color VARCHAR(7)
);

CREATE INDEX idx_tag_slug ON tags (slug);
