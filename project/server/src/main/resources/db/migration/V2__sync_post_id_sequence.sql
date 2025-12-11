-- Sync posts_id_seq to the current max(id) to avoid duplicate key errors
SELECT setval(
    pg_get_serial_sequence('posts', 'id'),
    COALESCE((SELECT MAX(id) FROM posts), 0),
    true
);
