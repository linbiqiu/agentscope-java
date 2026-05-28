create table if not exists agent_profile (
  id bigserial primary key,
  agent_code varchar(64) not null unique,
  agent_name varchar(128) not null,
  status varchar(16) not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists agent_skill_binding (
  id bigserial primary key,
  agent_id bigint not null references agent_profile(id),
  skill_name varchar(128) not null,
  binding_state varchar(16) not null,
  enabled boolean not null default true,
  created_by varchar(64) not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists conversation_log (
  id bigserial primary key,
  trace_id varchar(64) not null,
  conversation_id varchar(64) not null,
  agent_id bigint not null,
  user_open_id varchar(128) not null,
  message_role varchar(16) not null,
  message_content text not null,
  created_at timestamptz not null default now()
);

create table if not exists tool_call_log (
  id bigserial primary key,
  trace_id varchar(64) not null,
  conversation_id varchar(64) not null,
  skill_name varchar(128) not null,
  tool_name varchar(128) not null,
  status varchar(16) not null,
  latency_ms int not null default 0,
  created_at timestamptz not null default now()
);

create index if not exists idx_conversation_log_trace_created
  on conversation_log(trace_id, created_at);

create index if not exists idx_tool_call_log_trace_created
  on tool_call_log(trace_id, created_at);

create table if not exists publish_record (
  id bigserial primary key,
  target_type varchar(32) not null,
  target_id bigint not null,
  operator_id varchar(64) not null,
  trace_id varchar(64) not null,
  change_summary_json jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now()
);

create table if not exists sensitive_access_audit (
  id bigserial primary key,
  trace_id varchar(64) not null,
  conversation_id varchar(64) not null,
  skill_name varchar(128) not null,
  field_name varchar(32) not null,
  access_reason varchar(256) not null,
  created_at timestamptz not null default now()
);
