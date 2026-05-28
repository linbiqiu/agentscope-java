create table if not exists feishu_user_profile (
  actor_open_id varchar(128) primary key,
  actor_union_id varchar(128),
  actor_user_id varchar(128),
  actor_name varchar(128),
  actor_mobile varchar(64),
  actor_employee_no varchar(64),
  actor_email varchar(256),
  last_seen_at timestamptz not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create index if not exists idx_feishu_user_profile_mobile
  on feishu_user_profile (actor_mobile);

create index if not exists idx_feishu_user_profile_employee_no
  on feishu_user_profile (actor_employee_no);
