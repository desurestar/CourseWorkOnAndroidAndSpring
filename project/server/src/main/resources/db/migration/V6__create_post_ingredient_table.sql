-- V6__create_post_ingredient_table.sql
CREATE TABLE post_ingredient (
                                 id BIGSERIAL PRIMARY KEY,
                                 post_id BIGINT NOT NULL,
                                 ingredient_id BIGINT NOT NULL,
                                 quantity_value numeric, -- use numeric for precision; map to Double/BigDecimal in Java
                                 quantity_unit VARCHAR(50),
                                 CONSTRAINT uk_post_ingredient UNIQUE (post_id, ingredient_id)
);

ALTER TABLE post_ingredient
    ADD CONSTRAINT fk_postingredient_post
        FOREIGN KEY (post_id) REFERENCES posts (id) ON DELETE CASCADE;

ALTER TABLE post_ingredient
    ADD CONSTRAINT fk_postingredient_ingredient
        FOREIGN KEY (ingredient_id) REFERENCES ingredients (id) ON DELETE CASCADE;

CREATE INDEX idx_post_ingredient_post_id ON post_ingredient (post_id);
CREATE INDEX idx_post_ingredient_ingredient_id ON post_ingredient (ingredient_id);
