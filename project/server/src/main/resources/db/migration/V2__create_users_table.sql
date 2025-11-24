-- V2__create_users_table.sql
CREATE TABLE users (
                       id BIGSERIAL PRIMARY KEY,
                       username VARCHAR(150) NOT NULL UNIQUE,
                       email VARCHAR(255) NOT NULL UNIQUE,
                       password_hash VARCHAR(255),
                       display_name VARCHAR(150),
                       avatar_url TEXT,
                       role VARCHAR(20) DEFAULT 'user',
                       date_joined TIMESTAMPTZ DEFAULT now(),
                       last_login TIMESTAMPTZ
);

CREATE INDEX idx_users_email ON users (email);
