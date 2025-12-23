INSERT INTO users (username, password, email, role)
VALUES ('admin',
        '$2a$10$7Qv7Xz6pXc9W2yP1q1nEGu6qz7K9vYxZy5C9qPZxYJXJ1h3W9G',
        'admin@example.com',
        'ADMIN')
ON CONFLICT (username) DO NOTHING;


INSERT INTO contact (name, phone, email, relation)
VALUES
('John Doe', '9876543210', 'john@example.com', 'BROTHER'),
('Jane Doe', '9876543211', 'jane@example.com', 'SISTER')
ON CONFLICT (phone) DO NOTHING;
