-- =============================================
-- USERS
-- =============================================
INSERT INTO users (username, password, email, role)
VALUES ('admin',
        '$2a$12$79.h960LXubRcFLEZeSdF.aeU0nJen.z6hrMXDq0DX/ET4ABsaJv6',
        'admin@example.com', 'ADMIN')
ON CONFLICT (email) DO NOTHING;

-- =============================================
-- RELATIONS MASTER DATA
-- =============================================
INSERT INTO relations (relation_name, generation_level, gender, relation_category) VALUES
('Father',           1,  'M', 'PARENT'),
('Mother',           1,  'F', 'PARENT'),
('Brother',          0,  'M', 'SIBLING'),
('Sister',           0,  'F', 'SIBLING'),
('Son',             -1,  'M', 'CHILD'),
('Daughter',        -1,  'F', 'CHILD'),
('Grandfather',      2,  'M', 'GRANDPARENT'),
('Grandmother',      2,  'F', 'GRANDPARENT'),
('Grandson',        -2,  'M', 'GRANDCHILD'),
('Granddaughter',   -2,  'F', 'GRANDCHILD'),
('Uncle',           99,  'M', 'OTHER'),
('Aunt',            99,  'F', 'OTHER'),
('Cousin',          98,  'N', 'OTHER'),
('Husband',          0,  'M', 'SPOUSE'),
('Wife',             0,  'F', 'SPOUSE'),
('Nephew',          -1,  'M', 'OTHER'),
('Niece',           -1,  'F', 'OTHER'),
('Father-in-law',    1,  'M', 'INLAW'),
('Mother-in-law',    1,  'F', 'INLAW'),
('Son-in-law',      -1,  'M', 'INLAW'),
('Daughter-in-law', -1,  'F', 'INLAW'),
('Brother-in-law',   0,  'M', 'INLAW'),
('Sister-in-law',    0,  'F', 'INLAW')
ON CONFLICT (relation_name) DO NOTHING;


UPDATE relations SET relation_category = 'PARENT'
    WHERE LOWER(relation_name) IN ('father','mother') AND (relation_category IS NULL OR relation_category = 'OTHER');
UPDATE relations SET relation_category = 'SIBLING'
    WHERE LOWER(relation_name) IN ('brother','sister') AND (relation_category IS NULL OR relation_category = 'OTHER');
UPDATE relations SET relation_category = 'CHILD'
    WHERE LOWER(relation_name) IN ('son','daughter') AND (relation_category IS NULL OR relation_category = 'OTHER');
UPDATE relations SET relation_category = 'SPOUSE'
    WHERE LOWER(relation_name) IN ('husband','wife') AND (relation_category IS NULL OR relation_category = 'OTHER');
UPDATE relations SET relation_category = 'GRANDPARENT'
    WHERE LOWER(relation_name) IN ('grandfather','grandmother') AND (relation_category IS NULL OR relation_category = 'OTHER');
UPDATE relations SET relation_category = 'GRANDCHILD'
    WHERE LOWER(relation_name) IN ('grandson','granddaughter') AND (relation_category IS NULL OR relation_category = 'OTHER');
UPDATE relations SET relation_category = 'INLAW'
    WHERE LOWER(relation_name) IN ('father-in-law','mother-in-law','son-in-law','daughter-in-law','brother-in-law','sister-in-law') AND (relation_category IS NULL OR relation_category = 'OTHER');
UPDATE relations SET relation_category = 'OTHER'
    WHERE LOWER(relation_name) IN ('uncle','aunt','cousin','nephew','niece') AND relation_category IS NULL;
UPDATE relations SET relation_category = 'PIBLING'
    WHERE LOWER(relation_name) IN ('uncle','aunt') AND (relation_category = 'OTHER' OR relation_category IS NULL);
UPDATE relations SET relation_category = 'NIBLING'
    WHERE LOWER(relation_name) IN ('nephew','niece') AND (relation_category = 'OTHER' OR relation_category IS NULL);
UPDATE relations SET relation_category = 'COUSIN'
    WHERE LOWER(relation_name) = 'cousin' AND (relation_category = 'OTHER' OR relation_category IS NULL);

-- =============================================
-- SAMPLE CONTACTS
-- =============================================
--INSERT INTO contact (name, phone, email, relation_id)
--SELECT 'John Doe', '9876543210', 'john@example.com', r.id
--FROM relations r WHERE r.relation_name = 'Brother'
--ON CONFLICT (phone) DO NOTHING;
--
--INSERT INTO contact (name, phone, email, relation_id)
--SELECT 'Jane Doe', '9876543211', 'jane@example.com', r.id
--FROM relations r WHERE r.relation_name = 'Sister'
--ON CONFLICT (phone) DO NOTHING;

-- =============================================
-- INFERENCE RULES
-- =============================================
INSERT INTO relation_inference_rules
    (category_a, gender_a, category_b, gender_b, inferred_relation_name) VALUES

-- SIBLING + SIBLING
('SIBLING','M','SIBLING','M','Brother'),
('SIBLING','M','SIBLING','F','Brother'),
('SIBLING','F','SIBLING','M','Sister'),
('SIBLING','F','SIBLING','F','Sister'),

-- PARENT + PARENT → Spouse
('PARENT','M','PARENT','F','Husband'),
('PARENT','F','PARENT','M','Wife'),

('PARENT','M','PARENT','M','Brother'),
('PARENT','M','PARENT','F','Brother'),
('PARENT','F','PARENT','M','Sister'),
('PARENT','F','PARENT','F','Sister'),
('PARENT','N','PARENT','M','Brother'),
('PARENT','N','PARENT','F','Sister'),
('PARENT','N','PARENT','N','Brother'),

-- PARENT + SIBLING → Parent of sibling
('PARENT','M','SIBLING','M','Father'),
('PARENT','M','SIBLING','F','Father'),
('PARENT','F','SIBLING','M','Mother'),
('PARENT','F','SIBLING','F','Mother'),

-- SIBLING + PARENT → Child of parent
('SIBLING','M','PARENT','M','Son'),
('SIBLING','M','PARENT','F','Son'),
('SIBLING','F','PARENT','M','Daughter'),
('SIBLING','F','PARENT','F','Daughter'),

-- CHILD + CHILD → Siblings
('CHILD','M','CHILD','M','Brother'),
('CHILD','M','CHILD','F','Brother'),
('CHILD','F','CHILD','M','Sister'),
('CHILD','F','CHILD','F','Sister'),

-- PARENT + CHILD → Grandparent of grandchild
('PARENT','M','CHILD','M','Grandfather'),
('PARENT','M','CHILD','F','Grandfather'),
('PARENT','F','CHILD','M','Grandmother'),
('PARENT','F','CHILD','F','Grandmother'),

-- CHILD + PARENT → Grandchild of grandparent
('CHILD','M','PARENT','M','Grandson'),
('CHILD','M','PARENT','F','Grandson'),
('CHILD','F','PARENT','M','Granddaughter'),
('CHILD','F','PARENT','F','Granddaughter'),

-- GRANDPARENT + PARENT
('GRANDPARENT','M','PARENT','M','Father'),
('GRANDPARENT','M','PARENT','F','Father-in-law'),
('GRANDPARENT','F','PARENT','M','Mother'),
('GRANDPARENT','F','PARENT','F','Mother-in-law'),

-- GRANDPARENT + SIBLING
('GRANDPARENT','M','SIBLING','M','Grandfather'),
('GRANDPARENT','M','SIBLING','F','Grandfather'),
('GRANDPARENT','F','SIBLING','M','Grandmother'),
('GRANDPARENT','F','SIBLING','F','Grandmother'),

-- GRANDPARENT + CHILD
('GRANDPARENT','M','CHILD','M','Grandfather'),
('GRANDPARENT','M','CHILD','F','Grandfather'),
('GRANDPARENT','F','CHILD','M','Grandmother'),
('GRANDPARENT','F','CHILD','F','Grandmother'),

-- GRANDPARENT + SPOUSE
('GRANDPARENT','M','SPOUSE','M','Father-in-law'),
('GRANDPARENT','M','SPOUSE','F','Father-in-law'),
('GRANDPARENT','F','SPOUSE','M','Mother-in-law'),
('GRANDPARENT','F','SPOUSE','F','Mother-in-law'),

-- GRANDCHILD + PARENT
('GRANDCHILD','M','PARENT','M','Grandson'),
('GRANDCHILD','M','PARENT','F','Grandson'),
('GRANDCHILD','F','PARENT','M','Granddaughter'),
('GRANDCHILD','F','PARENT','F','Granddaughter'),

-- GRANDCHILD + SIBLING
('GRANDCHILD','M','SIBLING','M','Grandson'),
('GRANDCHILD','M','SIBLING','F','Grandson'),
('GRANDCHILD','F','SIBLING','M','Granddaughter'),
('GRANDCHILD','F','SIBLING','F','Granddaughter'),

-- SPOUSE + SIBLING
('SPOUSE','M','SIBLING','M','Father'),
('SPOUSE','M','SIBLING','F','Father'),
('SPOUSE','F','SIBLING','M','Mother'),
('SPOUSE','F','SIBLING','F','Mother'),

-- SPOUSE + CHILD
('SPOUSE','M','CHILD','M','Father'),
('SPOUSE','M','CHILD','F','Father'),
('SPOUSE','F','CHILD','M','Mother'),
('SPOUSE','F','CHILD','F','Mother'),

-- SPOUSE + PARENT
('SPOUSE','M','PARENT','M','Son-in-law'),
('SPOUSE','M','PARENT','F','Son-in-law'),
('SPOUSE','F','PARENT','M','Daughter-in-law'),
('SPOUSE','F','PARENT','F','Daughter-in-law'),

-- SIBLING + CHILD → Uncle/Aunt
('SIBLING','M','CHILD','M','Uncle'),
('SIBLING','M','CHILD','F','Uncle'),
('SIBLING','F','CHILD','M','Aunt'),
('SIBLING','F','CHILD','F','Aunt'),

-- CHILD + SIBLING → Nephew/Niece
('CHILD','M','SIBLING','M','Nephew'),
('CHILD','M','SIBLING','F','Nephew'),
('CHILD','F','SIBLING','M','Niece'),
('CHILD','F','SIBLING','F','Niece'),

-- INLAW + PARENT
('INLAW','M','PARENT','M','Son-in-law'),
('INLAW','M','PARENT','F','Son-in-law'),
('INLAW','F','PARENT','M','Daughter-in-law'),
('INLAW','F','PARENT','F','Daughter-in-law'),

-- INLAW + SIBLING
('INLAW','M','SIBLING','M','Brother-in-law'),
('INLAW','M','SIBLING','F','Brother-in-law'),
('INLAW','F','SIBLING','M','Sister-in-law'),
('INLAW','F','SIBLING','F','Sister-in-law'),

-- INLAW + SPOUSE
('INLAW','M','SPOUSE','M','Brother-in-law'),
('INLAW','M','SPOUSE','F','Brother-in-law'),
('INLAW','F','SPOUSE','M','Sister-in-law'),
('INLAW','F','SPOUSE','F','Sister-in-law'),

-- PARENT + INLAW
('PARENT','M','INLAW','M','Father-in-law'),
('PARENT','M','INLAW','F','Father-in-law'),
('PARENT','F','INLAW','M','Mother-in-law'),
('PARENT','F','INLAW','F','Mother-in-law'),

-- SIBLING + INLAW
('SIBLING','M','INLAW','M','Brother-in-law'),
('SIBLING','M','INLAW','F','Brother-in-law'),
('SIBLING','F','INLAW','M','Sister-in-law'),
('SIBLING','F','INLAW','F','Sister-in-law')

ON CONFLICT (category_a, gender_a, category_b, gender_b) DO NOTHING;