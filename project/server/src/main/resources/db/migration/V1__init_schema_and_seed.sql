-- V1__init_schema_and_seed.sql
-- Единая миграция: создаёт все таблицы для кулинарного блога и добавляет начальные данные.
-- Использует IF NOT EXISTS для безопасного применения в чистой БД.

-- ==========================
-- 1) Таблица users
-- ==========================
CREATE TABLE IF NOT EXISTS users (
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

CREATE INDEX IF NOT EXISTS idx_users_email ON users (email);

-- ==========================
-- 2) Таблица posts
-- Если она уже существует с другим набором колонок — лучше свериться вручную.
-- ==========================
CREATE TABLE IF NOT EXISTS posts (
                                     id BIGSERIAL PRIMARY KEY,
                                     author_id BIGINT NOT NULL,
                                     post_type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PUBLISHED',
    title VARCHAR(300) NOT NULL,
    excerpt TEXT,
    content TEXT,
    cover_url TEXT,
    calories INTEGER,
    cooking_time_minutes INTEGER,
    likes_count INTEGER DEFAULT 0,
    comments_count INTEGER DEFAULT 0,
    views_count BIGINT DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT now(),
    updated_at TIMESTAMPTZ DEFAULT now()
    );

CREATE INDEX IF NOT EXISTS idx_posts_status_created_at ON posts (status, created_at);
CREATE INDEX IF NOT EXISTS idx_posts_author_id ON posts (author_id);

-- ==========================
-- 3) Таблица tags
-- ==========================
CREATE TABLE IF NOT EXISTS tags (
                                    id BIGSERIAL PRIMARY KEY,
                                    name VARCHAR(100) NOT NULL UNIQUE,
    slug VARCHAR(100),
    color VARCHAR(7)
    );

CREATE INDEX IF NOT EXISTS idx_tag_slug ON tags (slug);

-- ==========================
-- 4) Таблица ingredients
-- ==========================
CREATE TABLE IF NOT EXISTS ingredients (
                                           id BIGSERIAL PRIMARY KEY,
                                           name VARCHAR(255) NOT NULL UNIQUE
    );

CREATE INDEX IF NOT EXISTS idx_ingredient_name ON ingredients (name);

-- ==========================
-- 5) Таблица post_tags (many-to-many)
-- ==========================
CREATE TABLE IF NOT EXISTS post_tags (
                                         post_id BIGINT NOT NULL,
                                         tag_id BIGINT NOT NULL,
                                         PRIMARY KEY (post_id, tag_id)
    );

-- FK добавим условно (если таблицы существуют и FK ещё нет)
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint WHERE conname = 'fk_posttags_post'
  ) THEN
BEGIN
ALTER TABLE post_tags
    ADD CONSTRAINT fk_posttags_post
        FOREIGN KEY (post_id) REFERENCES posts (id) ON DELETE CASCADE;
EXCEPTION WHEN undefined_table OR undefined_object THEN
      -- если posts ещё не существует, пропускаем; посты уже созданы ранее в этом скрипте
      RAISE NOTICE 'fk_posttags_post: cannot create constraint yet';
END;
END IF;

  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint WHERE conname = 'fk_posttags_tag'
  ) THEN
BEGIN
ALTER TABLE post_tags
    ADD CONSTRAINT fk_posttags_tag
        FOREIGN KEY (tag_id) REFERENCES tags (id) ON DELETE CASCADE;
EXCEPTION WHEN undefined_table OR undefined_object THEN
      RAISE NOTICE 'fk_posttags_tag: cannot create constraint yet';
END;
END IF;
END
$$;

CREATE INDEX IF NOT EXISTS idx_post_tags_tag_id ON post_tags (tag_id);

-- ==========================
-- 6) Таблица post_ingredient (join entity)
-- ==========================
CREATE TABLE IF NOT EXISTS post_ingredient (
                                               id BIGSERIAL PRIMARY KEY,
                                               post_id BIGINT NOT NULL,
                                               ingredient_id BIGINT NOT NULL,
                                               quantity_value numeric,
                                               quantity_unit VARCHAR(50),
    CONSTRAINT uk_post_ingredient UNIQUE (post_id, ingredient_id)
    );

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_postingredient_post') THEN
ALTER TABLE post_ingredient
    ADD CONSTRAINT fk_postingredient_post
        FOREIGN KEY (post_id) REFERENCES posts (id) ON DELETE CASCADE;
END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_postingredient_ingredient') THEN
ALTER TABLE post_ingredient
    ADD CONSTRAINT fk_postingredient_ingredient
        FOREIGN KEY (ingredient_id) REFERENCES ingredients (id) ON DELETE CASCADE;
END IF;
END
$$;

CREATE INDEX IF NOT EXISTS idx_post_ingredient_post_id ON post_ingredient (post_id);
CREATE INDEX IF NOT EXISTS idx_post_ingredient_ingredient_id ON post_ingredient (ingredient_id);

-- ==========================
-- 7) Таблица recipe_step
-- ==========================
CREATE TABLE IF NOT EXISTS recipe_step (
                                           id BIGSERIAL PRIMARY KEY,
                                           post_id BIGINT NOT NULL,
                                           step_order INTEGER NOT NULL,
                                           description TEXT,
                                           image_url TEXT,
                                           CONSTRAINT uk_post_step_order UNIQUE (post_id, step_order)
    );

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_recipestep_post') THEN
ALTER TABLE recipe_step
    ADD CONSTRAINT fk_recipestep_post
        FOREIGN KEY (post_id) REFERENCES posts (id) ON DELETE CASCADE;
END IF;
END
$$;

CREATE INDEX IF NOT EXISTS idx_recipe_step_post_id ON recipe_step (post_id);
CREATE INDEX IF NOT EXISTS idx_recipe_step_post_order ON recipe_step (post_id, step_order);

-- ==========================
-- 8) Таблица post_like
-- ==========================
CREATE TABLE IF NOT EXISTS post_like (
                                         post_id BIGINT NOT NULL,
                                         user_id BIGINT NOT NULL,
                                         created_at TIMESTAMPTZ DEFAULT now(),
    PRIMARY KEY (post_id, user_id)
    );

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_postlike_post') THEN
ALTER TABLE post_like
    ADD CONSTRAINT fk_postlike_post
        FOREIGN KEY (post_id) REFERENCES posts (id) ON DELETE CASCADE;
END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_postlike_user') THEN
ALTER TABLE post_like
    ADD CONSTRAINT fk_postlike_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE;
END IF;
END
$$;

CREATE INDEX IF NOT EXISTS idx_post_like_user_id ON post_like (user_id);
CREATE INDEX IF NOT EXISTS idx_post_like_post_id ON post_like (post_id);

-- ==========================
-- 9) Таблица user_subscriptions (self-referencing many-to-many)
-- ==========================
CREATE TABLE IF NOT EXISTS user_subscriptions (
                                                  subscriber_id BIGINT NOT NULL,
                                                  subscribed_to_id BIGINT NOT NULL,
                                                  created_at TIMESTAMPTZ DEFAULT now(),
    PRIMARY KEY (subscriber_id, subscribed_to_id)
    );

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_usersub_subscriber') THEN
ALTER TABLE user_subscriptions
    ADD CONSTRAINT fk_usersub_subscriber FOREIGN KEY (subscriber_id) REFERENCES users (id) ON DELETE CASCADE;
END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_usersub_subscribed_to') THEN
ALTER TABLE user_subscriptions
    ADD CONSTRAINT fk_usersub_subscribed_to FOREIGN KEY (subscribed_to_id) REFERENCES users (id) ON DELETE CASCADE;
END IF;
END
$$;

CREATE INDEX IF NOT EXISTS idx_user_subscriber ON user_subscriptions (subscriber_id);
CREATE INDEX IF NOT EXISTS idx_user_subscribed_to ON user_subscriptions (subscribed_to_id);

-- ==========================
-- 10) Доп. индексы/триггеры: (по желанию) полнотекстовый вектор
-- Этот блок закомментирован — включи при необходимости.
-- ==========================
/*
ALTER TABLE posts ADD COLUMN IF NOT EXISTS search_vector tsvector;
UPDATE posts SET search_vector = to_tsvector('russian', coalesce(title,'') || ' ' || coalesce(excerpt,'') || ' ' || coalesce(content,''));
CREATE INDEX IF NOT EXISTS idx_posts_search_vector ON posts USING GIN (search_vector);
CREATE FUNCTION posts_search_vector_trigger() RETURNS trigger AS $$
begin
  new.search_vector :=
    to_tsvector('russian', coalesce(new.title,'') || ' ' || coalesce(new.excerpt,'') || ' ' || coalesce(new.content,''));
  return new;
end
$$ LANGUAGE plpgsql;
CREATE TRIGGER trg_posts_search_vector BEFORE INSERT OR UPDATE
    ON posts FOR EACH ROW EXECUTE FUNCTION posts_search_vector_trigger();
*/

-- ==========================
-- 11) SEED: начальные тестовые данные
-- Проверяем, есть ли уже данные, чтобы не дублировать.
-- ==========================
-- Добавим пару пользователей
INSERT INTO users (username, email, password_hash, display_name, role)
SELECT 'admin', 'admin@example.local', NULL, 'Admin', 'admin'
    WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'admin');

INSERT INTO users (username, email, password_hash, display_name, role)
SELECT 'qppak', 'soumernt@gmail.com', NULL, 'Дмитрий', 'admin'
    WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'qppak');

INSERT INTO users (username, email, password_hash, display_name, role)
SELECT 'user1', 'user1@example.local', NULL, 'User One', 'user'
    WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'user1');

-- Добавим теги
INSERT INTO tags (name, slug, color)
SELECT 'Выпечка', 'vypechka', '#A1887F'
    WHERE NOT EXISTS (SELECT 1 FROM tags WHERE name = 'Выпечка');

INSERT INTO tags (name, slug, color)
SELECT 'Быстро и просто', 'bystro-i-prosto', '#4FC3F7'
    WHERE NOT EXISTS (SELECT 1 FROM tags WHERE name = 'Быстро и просто');

INSERT INTO tags (name, slug, color)
SELECT 'Фермерские продукты', 'fermerskie-produkty', '#6FBF73'
    WHERE NOT EXISTS (SELECT 1 FROM tags WHERE name = 'Фермерские продукты');

-- Добавим ингредиенты
INSERT INTO ingredients (name)
SELECT 'Картофель' WHERE NOT EXISTS (SELECT 1 FROM ingredients WHERE name = 'Картофель');

INSERT INTO ingredients (name)
SELECT 'Фарш куриный' WHERE NOT EXISTS (SELECT 1 FROM ingredients WHERE name = 'Фарш куриный');

INSERT INTO ingredients (name)
SELECT 'Помидоры' WHERE NOT EXISTS (SELECT 1 FROM ingredients WHERE name = 'Помидоры');

INSERT INTO ingredients (name)
SELECT 'Сыр пармезан' WHERE NOT EXISTS (SELECT 1 FROM ingredients WHERE name = 'Сыр пармезан');

INSERT INTO ingredients (name)
SELECT 'Сметана' WHERE NOT EXISTS (SELECT 1 FROM ingredients WHERE name = 'Сметана');

INSERT INTO ingredients (name)
SELECT 'Чеснок' WHERE NOT EXISTS (SELECT 1 FROM ingredients WHERE name = 'Чеснок');

-- Создадим один тестовый пост (если нет поста с таким заголовком)
WITH author AS (
    SELECT id FROM users WHERE username = 'qppak' LIMIT 1
    )
INSERT INTO posts (author_id, post_type, status, title, excerpt, content, cover_url, calories, cooking_time_minutes, likes_count, comments_count, views_count)
SELECT author.id, 'recipe', 'published', 'Картошка с фаршем и помидорами в духовке', 'Самая пикантная картошка. Быстро и вкусно', 'Картошка с фаршем и помидорами в духовке - сытное и вкусное блюдо...', '/media/posts/covers/example-cover.jpg', 117, 45, 1, 1, 6
FROM author
WHERE NOT EXISTS (SELECT 1 FROM posts WHERE title = 'Картошка с фаршем и помидорами в духовке');

-- Получим id созданного поста и свяжем tags/ingredients/steps/likes
-- Используем DO $$ ... чтобы выполнять условные вставки
DO $$
DECLARE
p_id BIGINT;
  ing_id BIGINT;
  tag_id BIGINT;
  uid BIGINT;
BEGIN
SELECT id INTO p_id FROM posts WHERE title = 'Картошка с фаршем и помидорами в духовке' LIMIT 1;
IF p_id IS NULL THEN
    RETURN;
END IF;

  -- Свяжем теги (по именам)
FOR tag_id IN SELECT id FROM tags WHERE name IN ('Выпечка','Быстро и просто','Фермерские продукты') LOOP
BEGIN
INSERT INTO post_tags (post_id, tag_id)
VALUES (p_id, tag_id);
EXCEPTION WHEN unique_violation THEN
      -- пропустить если уже есть
      NULL;
END;
END LOOP;

  -- Свяжем ингредиенты с количеством (пример)
  -- Картофель (5 шт)
SELECT id INTO ing_id FROM ingredients WHERE name = 'Картофель' LIMIT 1;
IF ing_id IS NOT NULL THEN
BEGIN
INSERT INTO post_ingredient (post_id, ingredient_id, quantity_value, quantity_unit)
VALUES (p_id, ing_id, 5, 'шт');
EXCEPTION WHEN unique_violation THEN NULL;
END;
END IF;

  -- Фарш куриный (300 г)
SELECT id INTO ing_id FROM ingredients WHERE name = 'Фарш куриный' LIMIT 1;
IF ing_id IS NOT NULL THEN
BEGIN
INSERT INTO post_ingredient (post_id, ingredient_id, quantity_value, quantity_unit)
VALUES (p_id, ing_id, 300, 'г');
EXCEPTION WHEN unique_violation THEN NULL;
END;
END IF;

  -- Помидоры (4 шт)
SELECT id INTO ing_id FROM ingredients WHERE name = 'Помидоры' LIMIT 1;
IF ing_id IS NOT NULL THEN
BEGIN
INSERT INTO post_ingredient (post_id, ingredient_id, quantity_value, quantity_unit)
VALUES (p_id, ing_id, 4, 'шт');
EXCEPTION WHEN unique_violation THEN NULL;
END;
END IF;

  -- Сыр пармезан (100 г)
SELECT id INTO ing_id FROM ingredients WHERE name = 'Сыр пармезан' LIMIT 1;
IF ing_id IS NOT NULL THEN
BEGIN
INSERT INTO post_ingredient (post_id, ingredient_id, quantity_value, quantity_unit)
VALUES (p_id, ing_id, 100, 'г');
EXCEPTION WHEN unique_violation THEN NULL;
END;
END IF;

  -- Сметана (4 столовые ложки) — храним как число 4 + unit 'ст.л.'
SELECT id INTO ing_id FROM ingredients WHERE name = 'Сметана' LIMIT 1;
IF ing_id IS NOT NULL THEN
BEGIN
INSERT INTO post_ingredient (post_id, ingredient_id, quantity_value, quantity_unit)
VALUES (p_id, ing_id, 4, 'ст.л.');
EXCEPTION WHEN unique_violation THEN NULL;
END;
END IF;

  -- Чеснок (5 шт)
SELECT id INTO ing_id FROM ingredients WHERE name = 'Чеснок' LIMIT 1;
IF ing_id IS NOT NULL THEN
BEGIN
INSERT INTO post_ingredient (post_id, ingredient_id, quantity_value, quantity_unit)
VALUES (p_id, ing_id, 5, 'шт');
EXCEPTION WHEN unique_violation THEN NULL;
END;
END IF;

  -- Добавим шаги (проверяем наличие по post + step_order)
BEGIN
INSERT INTO recipe_step (post_id, step_order, description, image_url)
VALUES (p_id, 1, 'Для приготовления картофеля с фаршем и помидорами в духовке подготовить все необходимые продукты по списку.', '/media/posts/steps/step1.jpg');
EXCEPTION WHEN unique_violation THEN NULL;
END;

BEGIN
INSERT INTO recipe_step (post_id, step_order, description, image_url)
VALUES (p_id, 2, 'Мясной фарш поместить в глубокую мисочку, добавить соль и перец, хорошо перемешать. Также добавить измельчённый чеснок.', '/media/posts/steps/step2.jpg');
EXCEPTION WHEN unique_violation THEN NULL;
END;

BEGIN
INSERT INTO recipe_step (post_id, step_order, description, image_url)
VALUES (p_id, 3, 'Почистить картофель и нарезать его тонкими кружочками. К картофелю добавить соль и перец, а также прованские травы или другие по вкусу.', '/media/posts/steps/step3.jpg');
EXCEPTION WHEN unique_violation THEN NULL;
END;

  -- Добавим один лайк от user1 (если нет)
SELECT id INTO uid FROM users WHERE username = 'user1' LIMIT 1;
IF uid IS NOT NULL THEN
BEGIN
INSERT INTO post_like (post_id, user_id) VALUES (p_id, uid);
EXCEPTION WHEN unique_violation THEN NULL;
END;
END IF;

  -- Добавим подписку: user1 подписан на qppak (если нет)
SELECT id INTO uid FROM users WHERE username = 'user1' LIMIT 1;
IF uid IS NOT NULL THEN
BEGIN
INSERT INTO user_subscriptions (subscriber_id, subscribed_to_id) VALUES (uid, (SELECT id FROM users WHERE username = 'qppak' LIMIT 1));
EXCEPTION WHEN unique_violation THEN NULL;
END;
END IF;

END
$$;

-- ==========================
-- 12) Финальные рекомендации/ноты
-- ==========================
-- 1) Если в твоей базе уже есть таблицы с другими именами/структурой, внимательно проверь — в противном случае Flyway может пометить миграцию как выполненную, а структура не соответствовать ожиданиям.
-- 2) Этот скрипт даёт рабочую стартовую схему и небольшой набор данных для тестирования.
-- 3) При деплое на продакшн: вероятно, захочется вынести seed (начальные теги/ингредиенты) в отдельную миграцию и не добавлять тестовые посты.
-- 4) При желании могу адаптировать миграцию под твой реальный V1 (если у тебя уже был V1__create_posts_table.sql) — пришли текущий файл, и я скорректирую объединённую миграцию так, чтобы не было дублирования и конфликтов.

-- End of V1__init_schema_and_seed.sql
