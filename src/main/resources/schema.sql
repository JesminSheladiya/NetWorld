-- USERS TABLE
CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(100) NOT NULL,
    password VARCHAR(255) NOT NULL,
    email VARCHAR(150) NOT NULL,
    role VARCHAR(50) NOT NULL DEFAULT 'USER',

    CONSTRAINT uk_users_username UNIQUE (username),
    CONSTRAINT uk_users_email UNIQUE (email)
);

-- CONTACT TABLE
CREATE TABLE IF NOT EXISTS contact (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(150),
    phone VARCHAR(20) NOT NULL,
    email VARCHAR(150) NOT NULL,
    relation VARCHAR(50),

    CONSTRAINT uk_contact_phone UNIQUE (phone),
    CONSTRAINT uk_contact_email UNIQUE (email)
);
