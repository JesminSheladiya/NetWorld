-- =============================================
-- USERS
-- =============================================
INSERT INTO users (username, password, email, role)
VALUES ('admin',
        '$2a$12$79.h960LXubRcFLEZeSdF.aeU0nJen.z6hrMXDq0DX/ET4ABsaJv6',
        'admin@example.com',
        'ADMIN')
ON CONFLICT (username) DO NOTHING;

-- =============================================
-- RELATIONS MASTER DATA
-- =============================================
INSERT INTO relations (relation_name, generation_level, gender, relation_category) VALUES
('Father',         1,   'M', 'PARENT'),
('Mother',         1,   'F', 'PARENT'),
('Brother',        0,   'M', 'SIBLING'),
('Sister',         0,   'F', 'SIBLING'),
('Son',           -1,   'M', 'CHILD'),
('Daughter',      -1,   'F', 'CHILD'),
('Grandfather',    2,   'M', 'GRANDPARENT'),
('Grandmother',    2,   'F', 'GRANDPARENT'),
('Grandson',      -2,   'M', 'GRANDCHILD'),
('Granddaughter', -2,   'F', 'GRANDCHILD'),
('Uncle',         99,   'M', 'OTHER'),
('Aunt',          99,   'F', 'OTHER'),
('Cousin',        98,   'N', 'OTHER'),
('Husband',        0,   'M', 'SPOUSE'),
('Wife',           0,   'F', 'SPOUSE'),
('Nephew',        -1,   'M', 'OTHER'),
('Niece',         -1,   'F', 'OTHER')
ON CONFLICT (relation_name) DO NOTHING;


UPDATE relations SET relation_category = 'PARENT'      WHERE LOWER(relation_name) IN ('father', 'mother')                           AND (relation_category IS NULL OR relation_category = 'OTHER');
UPDATE relations SET relation_category = 'SIBLING'     WHERE LOWER(relation_name) IN ('brother', 'sister')                          AND (relation_category IS NULL OR relation_category = 'OTHER');
UPDATE relations SET relation_category = 'CHILD'       WHERE LOWER(relation_name) IN ('son', 'daughter')                            AND (relation_category IS NULL OR relation_category = 'OTHER');
UPDATE relations SET relation_category = 'SPOUSE'      WHERE LOWER(relation_name) IN ('husband', 'wife')                            AND (relation_category IS NULL OR relation_category = 'OTHER');
UPDATE relations SET relation_category = 'GRANDPARENT' WHERE LOWER(relation_name) IN ('grandfather', 'grandmother')                 AND (relation_category IS NULL OR relation_category = 'OTHER');
UPDATE relations SET relation_category = 'GRANDCHILD'  WHERE LOWER(relation_name) IN ('grandson', 'granddaughter')                  AND (relation_category IS NULL OR relation_category = 'OTHER');
UPDATE relations SET relation_category = 'OTHER'       WHERE LOWER(relation_name) IN ('uncle', 'aunt', 'cousin', 'nephew', 'niece') AND relation_category IS NULL;

-- =============================================
-- SAMPLE CONTACTS
-- =============================================
INSERT INTO contact (name, phone, email, relation_id)
SELECT 'John Doe', '9876543210', 'john@example.com', r.id
FROM relations r WHERE r.relation_name = 'Brother'
ON CONFLICT (phone) DO NOTHING;

INSERT INTO contact (name, phone, email, relation_id)
SELECT 'Jane Doe', '9876543211', 'jane@example.com', r.id
FROM relations r WHERE r.relation_name = 'Sister'
ON CONFLICT (phone) DO NOTHING;

-- =============================================
-- FUTURE RELATIONS ADD FORMAT:
-- INSERT INTO relations (relation_name, generation_level, gender, relation_category)
-- VALUES ('Step-Brother', 0, 'M', 'SIBLING')
-- ON CONFLICT (relation_name) DO NOTHING;
-- =============================================