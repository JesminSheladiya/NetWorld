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

CREATE TABLE relation_rules (
    id BIGSERIAL PRIMARY KEY,
    person_a_relation VARCHAR(100) NOT NULL,
    person_b_relation VARCHAR(100) NOT NULL,
    inferred_relation  VARCHAR(100) NOT NULL,
    UNIQUE(person_a_relation, person_b_relation)
);

-- Rules: A ki user se relation + B ki user se relation = A ki B se relation
INSERT INTO relation_rules (person_a_relation, person_b_relation, inferred_relation) VALUES
('Father',  'Brother',  'Father'),
('Father',  'Sister',   'Father'),
('Father',  'Son',      'GrandFather'),
('Father',  'Daughter', 'GrandFather'),
('Mother',  'Brother',  'Mother'),
('Mother',  'Sister',   'Mother'),
('Mother',  'Son',      'GrandMother'),
('Mother',  'Daughter', 'GrandMother'),
('Brother', 'Brother',  'Brother'),
('Brother', 'Sister',   'Brother'),
('Sister',  'Brother',  'Sister'),
('Sister',  'Sister',   'Sister'),
('Son',     'Son',      'Brother'),
('Son',     'Daughter', 'Brother'),
('Daughter','Son',      'Sister'),
('Daughter','Daughter', 'Sister'),
('Husband', 'Son',      'Father'),
('Husband', 'Daughter', 'Father'),
('Wife',    'Son',      'Mother'),
('Wife',    'Daughter', 'Mother');
