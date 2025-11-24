-- V4__create_ingredients_table.sql
CREATE TABLE ingredients (
                             id BIGSERIAL PRIMARY KEY,
                             name VARCHAR(255) NOT NULL UNIQUE
);

CREATE INDEX idx_ingredient_name ON ingredients (name);
