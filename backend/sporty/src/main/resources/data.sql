-- =========================================================
-- SPORTY TEST DATA
--
-- USER 1
-- email: a@b.c
-- password: 1234567890
--
-- USER 2
-- email: b@c.d
-- password: 1234567890
-- =========================================================


-- =========================================================
-- USER
-- =========================================================

INSERT INTO `user` (
    id,
    email,
    password,
    gender,
    status,
    withdrawn_at,
    created_at,
    updated_at
) VALUES
(
    1,
    'a@b.c',
    '$2a$10$8Mg41VV5HPbImtJgcuKej.taVDFFGdqk/a.Zpu3TMMCmK0ffwtUhO',
    'MALE',
    'ACTIVE',
    NULL,
    NOW(),
    NOW()
),
(
    2,
    'b@c.d',
    '$2a$10$8Mg41VV5HPbImtJgcuKej.taVDFFGdqk/a.Zpu3TMMCmK0ffwtUhO',
    'FEMALE',
    'ACTIVE',
    NULL,
    NOW(),
    NOW()
);


-- =========================================================
-- PROFILE
--
-- user 1: a@b.c -> 마포구
-- user 2: b@c.d -> 강남구
-- =========================================================

INSERT INTO profile (
    id,
    nickname,
    district,
    image_url,
    created_at,
    updated_at,
    user_id
) VALUES
(
    1,
    '테스트유저1',
    'MAPO',
    NULL,
    NOW(),
    NOW(),
    1
),
(
    2,
    '테스트유저2',
    'GANGNAM',
    NULL,
    NOW(),
    NOW(),
    2
);


-- =========================================================
-- SPORT PREFERENCE
--
-- user 1 / profile 1:
--   FUTSAL
--
-- user 2 / profile 2:
--   FUTSAL
--   BASKETBALL
-- =========================================================

INSERT INTO sport_preference (
    id,
    sport_type,
    created_at,
    updated_at,
    profile_id
) VALUES
(
    1,
    'FUTSAL',
    NOW(),
    NOW(),
    1
),
(
    2,
    'FUTSAL',
    NOW(),
    NOW(),
    2
),
(
    3,
    'BASKETBALL',
    NOW(),
    NOW(),
    2
);