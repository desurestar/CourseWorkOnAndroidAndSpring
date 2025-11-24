-- V7__create_recipe_step_table.sql
CREATE TABLE recipe_step (
                             id BIGSERIAL PRIMARY KEY,
                             post_id BIGINT NOT NULL,
                             step_order INTEGER NOT NULL,
                             description TEXT,
                             image_url TEXT,
                             CONSTRAINT uk_post_step_order UNIQUE (post_id, step_order)
);

ALTER TABLE recipe_step
    ADD CONSTRAINT fk_recipestep_post
        FOREIGN KEY (post_id) REFERENCES posts (id) ON DELETE CASCADE;

CREATE INDEX idx_recipe_step_post_id ON recipe_step (post_id);
CREATE INDEX idx_recipe_step_post_order ON recipe_step (post_id, step_order);
