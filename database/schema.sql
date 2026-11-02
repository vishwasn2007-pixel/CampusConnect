-- ============================================================
--  CampusConnect (Omegle-style text chat) - MySQL 8 schema
--  Run this file once:  mysql -u root -p < database/schema.sql
--  Safe to re-run: tables are only created if missing.
-- ============================================================

CREATE DATABASE IF NOT EXISTS campusconnect
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE campusconnect;

-- ------------------------------------------------------------
-- USERS: one row per registered account
-- Constraints: PRIMARY KEY, UNIQUE, NOT NULL, CHECK
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
    id            INT AUTO_INCREMENT PRIMARY KEY,
    username      VARCHAR(30)  NOT NULL UNIQUE,
    email         VARCHAR(100) NOT NULL UNIQUE,
    password_hash CHAR(64)     NOT NULL,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_username_length CHECK (CHAR_LENGTH(username) >= 3)
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- WAITING_QUEUE: users currently looking for a stranger.
-- last_seen is refreshed by the browser every ~1.5s, so rows of
-- users who closed the tab become "stale" and are ignored.
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS waiting_queue (
    user_id   INT       PRIMARY KEY,
    joined_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_seen TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_queue_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE,
    INDEX idx_queue_joined (joined_at)
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- ROOMS: one private chat session between two matched users
-- (No ON DELETE actions on user1/user2 because they are used
--  inside a CHECK constraint, which MySQL does not allow.)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS rooms (
    id         INT AUTO_INCREMENT PRIMARY KEY,
    user1_id   INT NOT NULL,
    user2_id   INT NOT NULL,
    status     ENUM('ACTIVE', 'ENDED') NOT NULL DEFAULT 'ACTIVE',
    ended_by   INT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ended_at   TIMESTAMP NULL DEFAULT NULL,
    CONSTRAINT fk_rooms_user1    FOREIGN KEY (user1_id) REFERENCES users (id),
    CONSTRAINT fk_rooms_user2    FOREIGN KEY (user2_id) REFERENCES users (id),
    CONSTRAINT fk_rooms_ended_by FOREIGN KEY (ended_by) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT chk_different_users CHECK (user1_id <> user2_id),
    INDEX idx_rooms_user1_status (user1_id, status),
    INDEX idx_rooms_user2_status (user2_id, status)
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- MESSAGES: every chat message belongs to a room and a sender
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS messages (
    id        INT AUTO_INCREMENT PRIMARY KEY,
    room_id   INT          NOT NULL,
    sender_id INT          NOT NULL,
    content   VARCHAR(500) NOT NULL,
    sent_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_messages_room   FOREIGN KEY (room_id)   REFERENCES rooms (id) ON DELETE CASCADE,
    CONSTRAINT fk_messages_sender FOREIGN KEY (sender_id) REFERENCES users (id),
    CONSTRAINT chk_message_not_blank CHECK (CHAR_LENGTH(TRIM(content)) > 0),
    INDEX idx_messages_room_id (room_id, id)
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- VIEW: messages joined with the sender's username (JOIN)
-- The Java backend reads messages through this view.
-- ------------------------------------------------------------
CREATE OR REPLACE VIEW message_details AS
SELECT m.id,
       m.room_id,
       m.sender_id,
       u.username               AS sender_username,
       m.content,
       UNIX_TIMESTAMP(m.sent_at) AS sent_epoch
FROM messages m
JOIN users u ON u.id = m.sender_id;

-- ------------------------------------------------------------
-- VIEW: quick numbers for the dashboard (aggregates + subqueries)
-- ------------------------------------------------------------
CREATE OR REPLACE VIEW chat_stats AS
SELECT
    (SELECT COUNT(*) FROM users)                                   AS total_users,
    (SELECT COUNT(*) FROM rooms WHERE status = 'ACTIVE')           AS active_chats,
    (SELECT COUNT(*) FROM rooms)                                   AS total_chats,
    (SELECT COUNT(*) FROM messages)                                AS total_messages,
    (SELECT COUNT(*) FROM waiting_queue
      WHERE last_seen > (NOW() - INTERVAL 10 SECOND))              AS users_waiting;
