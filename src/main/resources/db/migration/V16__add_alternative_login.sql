CREATE TABLE login_email_code (
    email VARCHAR(128) NOT NULL PRIMARY KEY,
    code_hash VARCHAR(255) NOT NULL,
    expires_at DATETIME NOT NULL,
    attempts INT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE login_rate_limit (
    bucket_key VARCHAR(100) NOT NULL PRIMARY KEY,
    hits INT NOT NULL DEFAULT 0,
    expires_at DATETIME NOT NULL,
    KEY idx_login_rate_expiry (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE user_wechat (
    openid VARCHAR(128) NOT NULL PRIMARY KEY,
    user_id BIGINT UNSIGNED NOT NULL UNIQUE,
    CONSTRAINT fk_wechat_user FOREIGN KEY (user_id) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
