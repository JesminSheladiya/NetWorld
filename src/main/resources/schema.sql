-- USERS TABLE (same as before)
CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(100) NOT NULL,
    password VARCHAR(255) NOT NULL,
    email VARCHAR(150) NOT NULL,
    role VARCHAR(50) NOT NULL DEFAULT 'USER',
    CONSTRAINT uk_users_username UNIQUE (username),
    CONSTRAINT uk_users_email UNIQUE (email)
);

-- RELATIONS TABLE (lookup)
CREATE TABLE IF NOT EXISTS relations (
    id BIGSERIAL PRIMARY KEY,
    relation_name VARCHAR(50) NOT NULL UNIQUE
);

-- CONTACT TABLE (FK to relations)
CREATE TABLE IF NOT EXISTS contact (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(150),
    phone VARCHAR(20) NOT NULL,
    email VARCHAR(150) NOT NULL,
    relation_id BIGINT,

    CONSTRAINT uk_contact_phone UNIQUE (phone),
    CONSTRAINT uk_contact_email UNIQUE (email),
    CONSTRAINT fk_contact_relation
        FOREIGN KEY (relation_id) REFERENCES relations(id)
);
