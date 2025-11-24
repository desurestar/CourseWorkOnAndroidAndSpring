-- V9__create_user_subscriptions_table.sql
CREATE TABLE user_subscriptions (
                                    subscriber_id BIGINT NOT NULL,
                                    subscribed_to_id BIGINT NOT NULL,
                                    created_at TIMESTAMPTZ DEFAULT now(),
                                    PRIMARY KEY (subscriber_id, subscribed_to_id)
);

ALTER TABLE user_subscriptions
    ADD CONSTRAINT fk_usersub_subscriber FOREIGN KEY (subscriber_id) REFERENCES users (id) ON DELETE CASCADE;

ALTER TABLE user_subscriptions
    ADD CONSTRAINT fk_usersub_subscribed_to FOREIGN KEY (subscribed_to_id) REFERENCES users (id) ON DELETE CASCADE;

CREATE INDEX idx_user_subscriber ON user_subscriptions (subscriber_id);
CREATE INDEX idx_user_subscribed_to ON user_subscriptions (subscribed_to_id);
