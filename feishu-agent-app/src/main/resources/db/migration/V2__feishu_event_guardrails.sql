create table if not exists feishu_event_dedup (
  event_id varchar(128) primary key,
  created_at timestamptz not null default now()
);

create table if not exists feishu_user_rate_limit (
  user_open_id varchar(128) not null,
  minute_bucket bigint not null,
  request_count int not null,
  updated_at timestamptz not null default now(),
  primary key (user_open_id, minute_bucket)
);

create table if not exists runtime_routing_config (
  agent_id bigint primary key,
  provider varchar(32) not null,
  model varchar(128) not null,
  api_key text not null default '',
  base_url text not null default '',
  fallback_model varchar(128) not null default '',
  dispatch_mode varchar(16) not null,
  manual_skill varchar(128) not null default '',
  temperature double precision not null default 0.2,
  max_tokens int not null default 2048,
  updated_by varchar(64) not null default 'system',
  updated_at timestamptz not null default now()
);
