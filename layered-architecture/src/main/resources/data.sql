-- Seed catalog and members (idempotent: runs on every start).
INSERT INTO books (isbn, title, author, total_copies) VALUES
    ('978-8550819754', 'Fundamentos da Arquitetura de Software', 'Mark Richards, Neal Ford', 2),
    ('978-8576082675', 'Código Limpo', 'Robert C. Martin', 3),
    ('978-8550804606', 'Arquitetura Limpa', 'Robert C. Martin', 1),
    ('978-8575227244', 'Domain-Driven Design', 'Eric Evans', 1)
ON CONFLICT DO NOTHING;

INSERT INTO members (id, name) VALUES
    ('ana', 'Ana Souza'),
    ('bruno', 'Bruno Lima'),
    ('carla', 'Carla Mendes')
ON CONFLICT DO NOTHING;
