CREATE TABLE IF NOT EXISTS control_session_policy_cache (
    session_id VARCHAR(128) PRIMARY KEY,
    policy_mode VARCHAR(32) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS control_task_event_log (
    id BIGSERIAL PRIMARY KEY,
    topic VARCHAR(128) NOT NULL,
    payload_json TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS control_artifact_manifest (
    object_key VARCHAR(512) PRIMARY KEY,
    artifact_kind VARCHAR(32) NOT NULL DEFAULT 'asset',
    file_name VARCHAR(255) NOT NULL DEFAULT 'artifact.bin',
    owner_type VARCHAR(32),
    owner_id VARCHAR(128),
    owner_role VARCHAR(32),
    storage_backend VARCHAR(64) NOT NULL DEFAULT 'local',
    bucket_name VARCHAR(128),
    object_path VARCHAR(1024) NOT NULL,
    size_bytes BIGINT NOT NULL DEFAULT 0,
    artifact_status VARCHAR(32) NOT NULL DEFAULT 'AVAILABLE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS control_artifact_ref (
    id BIGSERIAL PRIMARY KEY,
    object_key VARCHAR(512) NOT NULL,
    owner_type VARCHAR(32) NOT NULL,
    owner_id VARCHAR(128) NOT NULL,
    owner_role VARCHAR(32) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE control_artifact_manifest ADD COLUMN IF NOT EXISTS artifact_kind VARCHAR(32) NOT NULL DEFAULT 'asset';
ALTER TABLE control_artifact_manifest ADD COLUMN IF NOT EXISTS file_name VARCHAR(255) NOT NULL DEFAULT 'artifact.bin';
ALTER TABLE control_artifact_manifest ADD COLUMN IF NOT EXISTS owner_type VARCHAR(32);
ALTER TABLE control_artifact_manifest ADD COLUMN IF NOT EXISTS owner_id VARCHAR(128);
ALTER TABLE control_artifact_manifest ADD COLUMN IF NOT EXISTS owner_role VARCHAR(32);
ALTER TABLE control_artifact_manifest ADD COLUMN IF NOT EXISTS size_bytes BIGINT NOT NULL DEFAULT 0;
ALTER TABLE control_artifact_manifest ADD COLUMN IF NOT EXISTS artifact_status VARCHAR(32) NOT NULL DEFAULT 'AVAILABLE';

CREATE UNIQUE INDEX IF NOT EXISTS idx_control_artifact_ref_unique
    ON control_artifact_ref(object_key, owner_type, owner_id, owner_role);

CREATE INDEX IF NOT EXISTS idx_control_artifact_ref_owner
    ON control_artifact_ref(owner_type, owner_id, owner_role);

MERGE INTO control_artifact_ref (object_key, owner_type, owner_id, owner_role)
KEY (object_key, owner_type, owner_id, owner_role)
SELECT object_key, owner_type, owner_id, owner_role
FROM control_artifact_manifest
WHERE owner_type IS NOT NULL AND owner_id IS NOT NULL AND owner_role IS NOT NULL;

CREATE TABLE IF NOT EXISTS control_task_state (
    task_id VARCHAR(128) PRIMARY KEY,
    project_name VARCHAR(255) NOT NULL,
    input_object_key VARCHAR(512) NOT NULL,
    config_object_key VARCHAR(512),
    output_object_key VARCHAR(512),
    session_id VARCHAR(128),
    policy_mode VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    current_stage VARCHAR(255) NOT NULL DEFAULT '',
    progress INTEGER NOT NULL DEFAULT 0,
    message TEXT NOT NULL DEFAULT '',
    logs_json TEXT NOT NULL DEFAULT '[]',
    stages_json TEXT NOT NULL DEFAULT '[]',
    recovery_previous_status VARCHAR(32),
    recovery_reason VARCHAR(128),
    recovered_at VARCHAR(64),
    created_at VARCHAR(64) NOT NULL,
    updated_at VARCHAR(64) NOT NULL
);

ALTER TABLE control_task_state ADD COLUMN IF NOT EXISTS recovery_previous_status VARCHAR(32);
ALTER TABLE control_task_state ADD COLUMN IF NOT EXISTS recovery_reason VARCHAR(128);
ALTER TABLE control_task_state ADD COLUMN IF NOT EXISTS recovered_at VARCHAR(64);

CREATE TABLE IF NOT EXISTS control_session_state (
    session_id VARCHAR(128) PRIMARY KEY,
    policy_mode VARCHAR(32) NOT NULL,
    control_plane VARCHAR(128) NOT NULL,
    worker_plane VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    current_step VARCHAR(255) NOT NULL DEFAULT '',
    progress INTEGER NOT NULL DEFAULT 0,
    total_steps INTEGER NOT NULL DEFAULT 0,
    error_message TEXT,
    config_file_name VARCHAR(255),
    input_file_name VARCHAR(255),
    output_file_name VARCHAR(255),
    config_object_key VARCHAR(512),
    input_object_key VARCHAR(512),
    output_object_key VARCHAR(512),
    library_files_json TEXT NOT NULL DEFAULT '[]',
    asset_files_json TEXT NOT NULL DEFAULT '[]',
    library_object_refs_json TEXT NOT NULL DEFAULT '{}',
    asset_object_refs_json TEXT NOT NULL DEFAULT '{}',
    created_at VARCHAR(64) NOT NULL,
    updated_at VARCHAR(64) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_control_task_event_log_topic_created_at
    ON control_task_event_log(topic, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_control_artifact_manifest_owner
    ON control_artifact_manifest(owner_type, owner_id, owner_role);

CREATE INDEX IF NOT EXISTS idx_control_task_state_status_updated_at
    ON control_task_state(status, updated_at DESC);
