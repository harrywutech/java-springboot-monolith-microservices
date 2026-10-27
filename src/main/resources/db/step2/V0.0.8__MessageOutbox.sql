-- Restoration reliability addition: state + outgoing event commit atomically.
CREATE TABLE mq_outbox (
  event_key VARCHAR(128) PRIMARY KEY,
  topic VARCHAR(128) NOT NULL,
  payload TEXT NOT NULL,
  delay_level INT NOT NULL DEFAULT 0,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  sent_at TIMESTAMP NULL,
  INDEX idx_unsent(sent_at, created_at)
) ENGINE=InnoDB CHARSET=utf8mb4;
