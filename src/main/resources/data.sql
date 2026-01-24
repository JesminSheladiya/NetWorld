INSERT INTO users (username, password, email, role)
VALUES ('admin',
        '$2a$12$79.h960LXubRcFLEZeSdF.aeU0nJen.z6hrMXDq0DX/ET4ABsaJv6',
        'admin@example.com',
        'ADMIN')
ON CONFLICT (username) DO NOTHING;

-- RELATIONS MASTER DATA
INSERT INTO relations (relation_name) VALUES
('Father'),
('Mother'),
('Brother'),
('Sister'),
('Son'),
('Daughter'),
('Grandfather'),
('Grandmother'),
('Uncle'),
('Aunt'),
('Cousin'),
('Husband'),
('Wife')
ON CONFLICT (relation_name) DO NOTHING;

-- Example contacts mapped by relation name
INSERT INTO contact (name, phone, email, relation_id)
SELECT 'John Doe', '9876543210', 'john@example.com', r.id
FROM relations r WHERE r.relation_name = 'Brother'
ON CONFLICT (phone) DO NOTHING;

INSERT INTO contact (name, phone, email, relation_id)
SELECT 'Jane Doe', '9876543211', 'jane@example.com', r.id
FROM relations r WHERE r.relation_name = 'Sister'
ON CONFLICT (phone) DO NOTHING;
