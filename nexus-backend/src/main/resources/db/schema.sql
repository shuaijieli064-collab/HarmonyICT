-- NEXUS 阶段一本地初始化脚本（H2 PostgreSQL 兼容模式）
-- 权威 DDL 以 docs/database-design.md 为准，二者保持同步；云上 RDS 使用其中的 PostgreSQL 初始化 SQL
CREATE TABLE IF NOT EXISTS users (
    id            BIGINT PRIMARY KEY,
    username      VARCHAR(32)  NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    nickname      VARCHAR(32),
    email         VARCHAR(128),
    status        SMALLINT     NOT NULL DEFAULT 1,
    created_at    TIMESTAMP    NOT NULL,
    updated_at    TIMESTAMP    NOT NULL,
    CONSTRAINT uk_users_username UNIQUE (username)
);
CREATE INDEX IF NOT EXISTS idx_users_username ON users (username);