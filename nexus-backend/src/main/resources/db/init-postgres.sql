-- NEXUS 阶段一 · PostgreSQL 初始化 SQL（供 docker 本地联调与云上 RDS 执行）
-- 权威定义见 docs/database-design.md 第 4 节，二者同步维护
CREATE TABLE IF NOT EXISTS users (
    id            BIGINT PRIMARY KEY,
    username      VARCHAR(32)  NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    nickname      VARCHAR(32),
    email         VARCHAR(128),
    status        SMALLINT     NOT NULL DEFAULT 1,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_users_username UNIQUE (username)
);

CREATE TABLE IF NOT EXISTS world_entities (
    id          BIGINT PRIMARY KEY,
    user_id     BIGINT       NOT NULL REFERENCES users(id),
    entity_type VARCHAR(32)  NOT NULL,
    name        VARCHAR(128) NOT NULL,
    attributes  TEXT         NOT NULL DEFAULT '{}',
    status      SMALLINT     NOT NULL DEFAULT 1,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_entity_type CHECK (entity_type IN ('person','asset','event','document','org'))
);
CREATE INDEX IF NOT EXISTS idx_we_user_type ON world_entities (user_id, entity_type);
CREATE INDEX IF NOT EXISTS idx_we_user_name ON world_entities (user_id, name);

CREATE TABLE IF NOT EXISTS world_sources (
    id          BIGINT PRIMARY KEY,
    user_id     BIGINT       NOT NULL REFERENCES users(id),
    source_type VARCHAR(16)  NOT NULL,
    file_name   VARCHAR(255),
    obs_key     VARCHAR(512),
    mime_type   VARCHAR(128),
    file_size   BIGINT,
    sha256      CHAR(64),                                -- 文件指纹（SHA-256，去重用）
    page_count  INT,                                     -- 文档页数（OCR/解析回填）
    uploaded_at TIMESTAMP,                               -- 原始上传时间
    status      VARCHAR(16)  NOT NULL DEFAULT 'pending',
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_source_type CHECK (source_type IN ('upload','ocr','manual')),
    CONSTRAINT chk_source_status CHECK (status IN ('pending','parsing','parsed','failed'))
);
CREATE INDEX IF NOT EXISTS idx_ws_user_status ON world_sources (user_id, status);
CREATE INDEX IF NOT EXISTS idx_ws_user_sha256 ON world_sources (user_id, sha256);

CREATE TABLE IF NOT EXISTS world_facts (
    id           BIGINT PRIMARY KEY,
    user_id      BIGINT       NOT NULL REFERENCES users(id),
    subject_id   BIGINT       NOT NULL REFERENCES world_entities(id),
    predicate    VARCHAR(64)  NOT NULL,
    object_value TEXT         NOT NULL,
    value_type   VARCHAR(16)  NOT NULL DEFAULT 'string', -- 宾值类型：string/number/date/boolean
    unit         VARCHAR(16),                             -- 单位（如 元/万元/%）
    normalized_value TEXT,                               -- 归一化宾值（如金额数字化）
    evidence     TEXT,                                   -- 证据原文摘录（可追溯）
    source_id    BIGINT       REFERENCES world_sources(id),
    confidence   NUMERIC(4,3) NOT NULL DEFAULT 0.500,
    valid_from   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    valid_until  TIMESTAMP,
    status       VARCHAR(16)  NOT NULL DEFAULT 'active',
    fact_status  VARCHAR(16)  NOT NULL DEFAULT 'confirmed', -- 抽取侧事实状态：confirmed/inferred/unverified（B 已确认）
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_fact_status CHECK (status IN ('active','superseded','retracted')),
    -- 命名为 _enum 后缀：chk_fact_status 已被 status 列约束占用（B review 反馈）
    CONSTRAINT chk_fact_status_enum CHECK (fact_status IN ('confirmed','inferred','unverified')),
    CONSTRAINT chk_fact_confidence CHECK (confidence >= 0 AND confidence <= 1)
);
CREATE INDEX IF NOT EXISTS idx_wf_user_subject_pred ON world_facts (user_id, subject_id, predicate);
CREATE INDEX IF NOT EXISTS idx_wf_user_source ON world_facts (user_id, source_id);
-- P1-5 拍板（联调 2026-11-22）：同源同主体同谓词视为同一事实版本，唯一索引挡重抽重复；
-- source_id 为 NULL 的人工录入不参与唯一约束（标准 NULL 语义），重抽写入走服务层链式失效（upsert）
CREATE UNIQUE INDEX IF NOT EXISTS uk_wf_src_fact ON world_facts (user_id, source_id, subject_id, predicate);

CREATE TABLE IF NOT EXISTS world_events (
    id          BIGINT PRIMARY KEY,
    user_id     BIGINT        NOT NULL REFERENCES users(id),
    entity_id   BIGINT        REFERENCES world_entities(id),
    event_type  VARCHAR(32)   NOT NULL,
    title       VARCHAR(255)  NOT NULL,
    description TEXT,
    occurred_at TIMESTAMP,
    source_id   BIGINT        REFERENCES world_sources(id),
    created_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_event_type CHECK (event_type IN ('education','career','asset','health','contract','other'))
);
CREATE INDEX IF NOT EXISTS idx_wv_user_time ON world_events (user_id, occurred_at);
CREATE INDEX IF NOT EXISTS idx_wv_user_entity ON world_events (user_id, entity_id);

CREATE TABLE IF NOT EXISTS world_risks (
    id          BIGINT PRIMARY KEY,
    user_id     BIGINT        NOT NULL REFERENCES users(id),
    risk_type   VARCHAR(32)   NOT NULL,
    level       VARCHAR(8)    NOT NULL,
    title       VARCHAR(255)  NOT NULL,
    description TEXT,
    entity_id   BIGINT        REFERENCES world_entities(id),
    rule_code   VARCHAR(64)   NOT NULL,
    status      VARCHAR(16)   NOT NULL DEFAULT 'open',
    created_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_risk_level CHECK (level IN ('low','medium','high')),
    CONSTRAINT chk_risk_status CHECK (status IN ('open','acknowledged','resolved'))
);
CREATE INDEX IF NOT EXISTS idx_wr_user_status_level ON world_risks (user_id, status, level);

-- P1-6 拍板（联调 2026-11-22）：风险-证据事实关联表，一条风险可对应多条证据事实；
-- 不用数组列：违反范式、无法反查"某事实影响了哪些风险"、无法加外键
CREATE TABLE IF NOT EXISTS world_risk_facts (
    risk_id BIGINT NOT NULL REFERENCES world_risks(id),
    fact_id BIGINT NOT NULL REFERENCES world_facts(id),
    PRIMARY KEY (risk_id, fact_id)
);
CREATE INDEX IF NOT EXISTS idx_wrf_fact ON world_risk_facts (fact_id);

CREATE TABLE IF NOT EXISTS tasks (
    id          BIGINT PRIMARY KEY,
    user_id     BIGINT        NOT NULL REFERENCES users(id),
    title       VARCHAR(255)  NOT NULL,
    description TEXT,
    status      VARCHAR(16)   NOT NULL DEFAULT 'todo',
    priority    SMALLINT      NOT NULL DEFAULT 0,
    due_date    DATE,
    source_type VARCHAR(16)   NOT NULL DEFAULT 'manual',
    risk_id     BIGINT        REFERENCES world_risks(id),
    related_fact_id BIGINT    REFERENCES world_facts(id),
    created_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_task_status CHECK (status IN ('todo','in_progress','done','cancelled')),
    CONSTRAINT chk_task_priority CHECK (priority IN (0,1,2)),
    CONSTRAINT chk_task_source CHECK (source_type IN ('manual','risk','agent','extracted'))
);
CREATE INDEX IF NOT EXISTS idx_tk_user_status ON tasks (user_id, status);
CREATE INDEX IF NOT EXISTS idx_tk_user_due ON tasks (user_id, due_date);

CREATE TABLE IF NOT EXISTS agent_runs (
    id           BIGINT PRIMARY KEY,
    user_id      BIGINT        NOT NULL REFERENCES users(id),
    trigger_type VARCHAR(16)   NOT NULL,
    intent       VARCHAR(255)  NOT NULL,
    status       VARCHAR(16)   NOT NULL DEFAULT 'running',
    summary      TEXT,
    started_at   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at  TIMESTAMP,
    created_at   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_run_trigger CHECK (trigger_type IN ('manual','scheduled','event')),
    CONSTRAINT chk_run_status CHECK (status IN ('running','succeeded','failed','cancelled'))
);
CREATE INDEX IF NOT EXISTS idx_ar_user_status ON agent_runs (user_id, status);

CREATE TABLE IF NOT EXISTS agent_action_logs (
    id          BIGINT PRIMARY KEY,
    user_id     BIGINT        NOT NULL REFERENCES users(id),
    run_id      BIGINT        NOT NULL REFERENCES agent_runs(id),
    action_type VARCHAR(64)   NOT NULL,
    level       VARCHAR(4)    NOT NULL,
    target_type VARCHAR(32),
    target_id   BIGINT,
    payload     TEXT          NOT NULL DEFAULT '{}',
    result      VARCHAR(16)   NOT NULL DEFAULT 'pending',
    authorized  BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_log_level CHECK (level IN ('L0','L1','L2')),
    CONSTRAINT chk_log_result CHECK (result IN ('pending','success','failure','denied'))
);
CREATE INDEX IF NOT EXISTS idx_wal_user_run ON agent_action_logs (user_id, run_id);
CREATE INDEX IF NOT EXISTS idx_wal_user_level ON agent_action_logs (user_id, level);