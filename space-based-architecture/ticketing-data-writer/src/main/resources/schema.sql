CREATE TABLE IF NOT EXISTS events (
    id              VARCHAR(64)  PRIMARY KEY,
    name            VARCHAR(200) NOT NULL,
    total_seats     INT          NOT NULL,
    available_seats INT          NOT NULL
);

CREATE TABLE IF NOT EXISTS reservations (
    id           VARCHAR(64) PRIMARY KEY,
    event_id     VARCHAR(64) NOT NULL REFERENCES events (id),
    customer_id  VARCHAR(64) NOT NULL,
    quantity     INT         NOT NULL,
    confirmed_at TIMESTAMP   NOT NULL
);

-- demo seed (idempotent)
INSERT INTO events (id, name, total_seats, available_seats)
SELECT 'mega-concert-2026', 'Mega Concert - Final Tour', 1000, 1000
WHERE NOT EXISTS (SELECT 1 FROM events WHERE id = 'mega-concert-2026');
