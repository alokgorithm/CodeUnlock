-- ============================================================
-- CodeUnlock — Complete Supabase Schema Migration
-- File: supabase/migrations/001_initial_schema.sql
--
-- Run this once against a fresh Supabase project.
-- Covers every table in PRD §7 plus all indexes, RLS policies,
-- and badge seed data from PRD §13.
-- ============================================================

-- ============================================================
-- EXTENSIONS
-- ============================================================
create extension if not exists "uuid-ossp";
create extension if not exists "pg_trgm"; -- for fast ILIKE search on usernames

-- ============================================================
-- CORE TABLES
-- ============================================================

-- 7.1 users / profiles
-- Note: id is the Supabase auth.uid() — auth.users is managed by Supabase,
-- this table extends it.
create table if not exists public.profiles (
  id                  uuid primary key references auth.users(id) on delete cascade,
  email               text not null,
  username            text not null unique,
  avatar_emoji        text not null default '🎯',
  college             text,
  leetcode_username   text,
  codeforces_handle   text,
  created_at          timestamptz not null default now(),
  last_sync_at        timestamptz,
  updated_at          timestamptz not null default now()
);

-- Indexes for search (PRD §12: search by username / LC / CF handle)
create index if not exists idx_profiles_username_trgm  on public.profiles using gin (username gin_trgm_ops);
create index if not exists idx_profiles_lc_trgm        on public.profiles using gin (leetcode_username gin_trgm_ops);
create index if not exists idx_profiles_cf_trgm        on public.profiles using gin (codeforces_handle gin_trgm_ops);
create index if not exists idx_profiles_college        on public.profiles (college);
create index if not exists idx_profiles_last_sync      on public.profiles (last_sync_at);

-- 7.1 settings (user_preferences)
create table if not exists public.user_preferences (
  user_id                       uuid primary key references public.profiles(id) on delete cascade,
  usage_mode                    text not null default 'focused'
                                  check (usage_mode in ('relaxed','focused','hardcore','custom')),
  daily_solve_target            integer not null default 3 check (daily_solve_target >= 2),
  restricted_apps               jsonb not null default '[]'::jsonb,
  per_app_limits                jsonb not null default '{}'::jsonb,
  emergency_unlock_enabled      boolean not null default true,
  free_unlock_enabled           boolean not null default true,
  is_first_setup_complete       boolean not null default false,
  -- Pending changes (apply next day — PRD §5)
  pending_usage_mode            text,
  pending_daily_solve_target    integer,
  pending_apply_epoch_day       bigint,
  last_settings_applied_epoch_day bigint,
  last_week_updated_at          timestamptz,
  created_at                    timestamptz not null default now(),
  updated_at                    timestamptz not null default now()
);

-- daily_progress (PRD §7.1)
create table if not exists public.daily_progress (
  user_id                     uuid not null references public.profiles(id) on delete cascade,
  date                        date not null,
  problems_solved             integer not null default 0,
  unlock_minutes_earned       integer not null default 0,
  unlock_minutes_used         integer not null default 0,
  free_unlock_used            boolean not null default false,
  emergency_unlock_used       boolean not null default false,
  daily_target_met            boolean not null default false,
  bonus_time_earned           boolean not null default false,
  total_screen_time_minutes   integer not null default 0,
  restricted_app_time_minutes integer not null default 0,
  updated_at                  timestamptz not null default now(),
  primary key (user_id, date)
);

create index if not exists idx_daily_progress_user_date on public.daily_progress (user_id, date desc);

-- unlock_sessions (PRD §7.1)
create table if not exists public.unlock_sessions (
  id              uuid primary key default uuid_generate_v4(),
  user_id         uuid not null references public.profiles(id) on delete cascade,
  app_package     text not null,
  unlock_type     text not null check (unlock_type in ('coding','free','emergency')),
  duration_minutes integer not null,
  expires_at      timestamptz not null,
  is_active       boolean not null default true,
  created_at      timestamptz not null default now()
);

create index if not exists idx_unlock_sessions_user_active on public.unlock_sessions (user_id, is_active, expires_at);

-- usage_logs (PRD §7.1)
create table if not exists public.usage_logs (
  id               uuid primary key default uuid_generate_v4(),
  user_id          uuid not null references public.profiles(id) on delete cascade,
  app_package      text not null,
  date             date not null,
  duration_seconds integer not null default 0,
  pickup_count     integer not null default 0,
  blocked_attempts integer not null default 0
);

create index if not exists idx_usage_logs_user_date on public.usage_logs (user_id, date);

-- ============================================================
-- PROBLEM TRACK TABLES (PRD §7.2)
-- ============================================================

-- problems
create table if not exists public.problems (
  id                       serial primary key,
  title                    text not null,
  slug                     text not null,      -- LC slug or CF "contestId-index"
  difficulty               text not null check (difficulty in ('Easy','Medium','Hard')),
  platform                 text not null check (platform in ('leetcode','codeforces')),
  topic                    text not null,
  topics                   text[] not null default '{}',
  pattern                  text not null default '',
  leetcode_id              integer,
  codeforces_contest_id    integer,
  codeforces_problem_index text,
  elo_rating               integer not null default 1200,
  track_position           integer not null unique,
  frequency_score          float not null default 0,
  companies                text[] not null default '{}',
  created_at               timestamptz not null default now()
);

create index if not exists idx_problems_track_position on public.problems (track_position);
create index if not exists idx_problems_platform        on public.problems (platform);
create index if not exists idx_problems_difficulty      on public.problems (difficulty);
create index if not exists idx_problems_topic           on public.problems (topic);

-- user_solves (replaces usertrackprogress — consolidates solve record + track status)
-- PRD calls this table "usertrackprogress" but also needs a "submissions" table.
-- user_solves is the master record; submissions is the append-only log.
create table if not exists public.user_solves (
  user_id        uuid not null references public.profiles(id) on delete cascade,
  problem_id     integer not null references public.problems(id) on delete cascade,
  status         text not null default 'locked' check (status in ('locked','available','solved')),
  verified       boolean not null default false,
  assigned_at    timestamptz,        -- when the problem was shown to user (anti-cheat anchor)
  solved_at      timestamptz,        -- platform submission timestamp
  verified_at    timestamptz,        -- when our backend confirmed it
  submission_id  text,               -- platform submission ID
  primary key (user_id, problem_id)
);

create index if not exists idx_user_solves_user_verified on public.user_solves (user_id, verified);
create index if not exists idx_user_solves_user_status   on public.user_solves (user_id, status);

-- ============================================================
-- EXTERNAL PLATFORM STATS — DAILY SNAPSHOTS (PRD §7.3)
-- ============================================================

create table if not exists public.leetcode_daily_stats (
  user_id               uuid not null references public.profiles(id) on delete cascade,
  date                  date not null,
  total_solved          integer not null default 0,
  easy_solved           integer not null default 0,
  medium_solved         integer not null default 0,
  hard_solved           integer not null default 0,
  contest_rating        float not null default 0,
  global_ranking        integer not null default 0,
  reputation            integer not null default 0,
  submission_count      integer not null default 0,
  problems_solved_today integer not null default 0,  -- delta from yesterday
  created_at            timestamptz not null default now(),
  primary key (user_id, date)
);

create table if not exists public.codeforces_daily_stats (
  user_id               uuid not null references public.profiles(id) on delete cascade,
  date                  date not null,
  current_rating        integer not null default 0,
  max_rating            integer not null default 0,
  rank                  text not null default 'unrated',
  problems_solved       integer not null default 0,
  problems_solved_today integer not null default 0,  -- delta from yesterday
  contest_count         integer not null default 0,
  created_at            timestamptz not null default now(),
  primary key (user_id, date)
);

-- submissions — append-only log of all verified accepted submissions (PRD §7.3)
create table if not exists public.submissions (
  id            uuid primary key default uuid_generate_v4(),
  user_id       uuid not null references public.profiles(id) on delete cascade,
  platform      text not null check (platform in ('leetcode','codeforces')),
  problem_slug  text not null,
  problem_title text not null default '',
  verdict       text not null,
  language      text not null default 'unknown',
  runtime_ms    integer,
  memory_kb     integer,
  submitted_at  timestamptz not null,
  synced_at     timestamptz not null default now(),
  is_verified   boolean not null default false
);

create index if not exists idx_submissions_user_platform  on public.submissions (user_id, platform, submitted_at desc);
create index if not exists idx_submissions_user_slug      on public.submissions (user_id, problem_slug);
create index if not exists idx_submissions_verified       on public.submissions (user_id, is_verified, submitted_at desc);

-- ============================================================
-- WELLBEING TABLES (PRD §7.4)
-- ============================================================

create table if not exists public.wellbeing_daily (
  user_id                         uuid not null references public.profiles(id) on delete cascade,
  date                            date not null,
  total_screen_time_minutes       integer not null default 0,
  restricted_app_time_minutes     integer not null default 0,
  productive_time_minutes         integer not null default 0,
  pickup_count                    integer not null default 0,
  blocked_attempt_count           integer not null default 0,
  unlock_count                    integer not null default 0,
  average_session_duration_minutes float not null default 0,
  longest_session_app             text,
  wellbeing_score                 float not null default 0 check (wellbeing_score >= 0 and wellbeing_score <= 100),
  updated_at                      timestamptz not null default now(),
  primary key (user_id, date)
);

create index if not exists idx_wellbeing_daily_user_date on public.wellbeing_daily (user_id, date desc);

create table if not exists public.wellbeing_app_detail (
  user_id          uuid not null references public.profiles(id) on delete cascade,
  date             date not null,
  app_package      text not null,
  app_name         text not null default '',
  duration_minutes integer not null default 0,
  pickup_count     integer not null default 0,
  category         text not null default 'other'
                     check (category in ('social','entertainment','productivity','other')),
  primary key (user_id, date, app_package)
);

create index if not exists idx_wellbeing_app_user_date on public.wellbeing_app_detail (user_id, date desc);

-- ============================================================
-- RANKING TABLES (PRD §7.5)
-- ============================================================

create table if not exists public.user_rankings (
  user_id                     uuid primary key references public.profiles(id) on delete cascade,
  global_rank                 integer not null default 999999,
  global_rank_last_week       integer,            -- snapshot for weekly delta
  college_rank                integer,
  classroom_ids               uuid[] not null default '{}',
  total_verified_solves       integer not null default 0,
  total_screen_time_reduction_pct float not null default 0,
  consistency_streak_days     integer not null default 0,
  ranking_score               float not null default 0,
  last_computed_at            timestamptz not null default now(),
  last_week_updated_at        timestamptz         -- when global_rank_last_week was last snapshotted
);

create index if not exists idx_user_rankings_score on public.user_rankings (ranking_score desc);
create index if not exists idx_user_rankings_rank  on public.user_rankings (global_rank);

create table if not exists public.ranking_snapshots (
  user_id       uuid not null references public.profiles(id) on delete cascade,
  date          date not null,
  global_rank   integer not null,
  ranking_score float not null,
  solves_to_date integer not null default 0,
  primary key (user_id, date)
);

create index if not exists idx_ranking_snapshots_user_date on public.ranking_snapshots (user_id, date desc);

-- ============================================================
-- SOCIAL TABLES (PRD §7.6)
-- ============================================================

create table if not exists public.classrooms (
  id          uuid primary key default uuid_generate_v4(),
  name        text not null,
  code        text not null unique,
  creator_id  uuid not null references public.profiles(id) on delete cascade,
  created_at  timestamptz not null default now()
);

create index if not exists idx_classrooms_code on public.classrooms (code);

create table if not exists public.classroom_members (
  classroom_id  uuid not null references public.classrooms(id) on delete cascade,
  user_id       uuid not null references public.profiles(id) on delete cascade,
  joined_at     timestamptz not null default now(),
  primary key (classroom_id, user_id)
);

create index if not exists idx_classroom_members_user on public.classroom_members (user_id);

create table if not exists public.friends (
  requester_id  uuid not null references public.profiles(id) on delete cascade,
  addressee_id  uuid not null references public.profiles(id) on delete cascade,
  status        text not null default 'pending' check (status in ('pending','accepted')),
  created_at    timestamptz not null default now(),
  primary key (requester_id, addressee_id),
  -- Prevent duplicate bidirectional rows
  constraint friends_no_self_friend check (requester_id <> addressee_id)
);

create index if not exists idx_friends_addressee on public.friends (addressee_id, status);
create index if not exists idx_friends_requester on public.friends (requester_id, status);

-- ============================================================
-- BADGE TABLES (PRD §7.7)
-- ============================================================

create table if not exists public.badges (
  id          serial primary key,
  name        text not null unique,
  category    text not null check (category in ('solving','discipline','consistency','special')),
  requirement jsonb not null,
  icon_emoji  text not null default '🏅',
  description text not null default ''
);

create table if not exists public.user_badges (
  user_id     uuid not null references public.profiles(id) on delete cascade,
  badge_id    integer not null references public.badges(id) on delete cascade,
  badge_type  text,
  badge_name  text,
  progress    float not null default 0 check (progress >= 0 and progress <= 1),
  earned_at   timestamptz,
  primary key (user_id, badge_id)
);

create index if not exists idx_user_badges_user    on public.user_badges (user_id);
create index if not exists idx_user_badges_earned  on public.user_badges (user_id, earned_at) where earned_at is not null;

-- ============================================================
-- BADGE SEED DATA (PRD §13)
-- ============================================================
insert into public.badges (name, category, requirement, icon_emoji, description) values
  -- Solving
  ('Starter',       'solving',     '{"solves": 10}',                             '🌱', 'Solve your first 10 problems'),
  ('Solver',        'solving',     '{"solves": 50}',                             '🔥', 'Solve 50 problems'),
  ('Crusher',       'solving',     '{"solves": 100}',                            '⚡', 'Solve 100 problems'),
  ('Legend',        'solving',     '{"solves": 200}',                            '👑', 'Complete all 200 problems'),
  -- Discipline
  ('Zen Master',    'discipline',  '{"restricted_app_hours_max": 1, "days": 7}', '🧘', 'Use restricted apps less than 1 hour/day for 7 consecutive days'),
  ('Guardian',      'discipline',  '{"restricted_app_hours_max": 0.5, "days": 30}','🛡️','Use restricted apps less than 30 min/day for 30 consecutive days'),
  ('Block Hero',    'discipline',  '{"no_emergency_unlocks_days": 30}',          '🚫', 'Use zero emergency unlocks for 30 consecutive days'),
  -- Consistency
  ('Week Warrior',  'consistency', '{"streak_days": 7}',                         '📅', '7-day solve streak'),
  ('Monthly Force', 'consistency', '{"streak_days": 30}',                        '📆', '30-day solve streak'),
  ('Century',       'consistency', '{"streak_days": 100}',                       '🏔️', '100-day solve streak'),
  ('Perfect Week',  'consistency', '{"perfect_days": 7}',                        '💎', 'Hit your daily solve target every day for 7 consecutive days'),
  -- Special
  ('First Blood',   'special',     '{"solves": 1}',                              '🎯', 'Solve your very first problem'),
  ('Top 100',       'special',     '{"global_top_n": 100}',                      '🌍', 'Reach the global top 100 ranking')
on conflict (name) do nothing;

-- ============================================================
-- ROW LEVEL SECURITY (PRD §7.8)
-- ============================================================
-- Pattern: users read/write their own rows; public leaderboard data is readable by all.

alter table public.profiles             enable row level security;
alter table public.user_preferences     enable row level security;
alter table public.daily_progress       enable row level security;
alter table public.unlock_sessions      enable row level security;
alter table public.usage_logs           enable row level security;
alter table public.problems             enable row level security;
alter table public.user_solves          enable row level security;
alter table public.leetcode_daily_stats enable row level security;
alter table public.codeforces_daily_stats enable row level security;
alter table public.submissions          enable row level security;
alter table public.wellbeing_daily      enable row level security;
alter table public.wellbeing_app_detail enable row level security;
alter table public.user_rankings        enable row level security;
alter table public.ranking_snapshots    enable row level security;
alter table public.classrooms           enable row level security;
alter table public.classroom_members    enable row level security;
alter table public.friends              enable row level security;
alter table public.badges               enable row level security;
alter table public.user_badges          enable row level security;

-- ---- profiles ----
create policy "Users can read own profile"
  on public.profiles for select using (auth.uid() = id);

create policy "Users can update own profile"
  on public.profiles for update using (auth.uid() = id);

create policy "Service role can do anything on profiles"
  on public.profiles for all using (auth.role() = 'service_role');

-- ---- user_preferences ----
create policy "Users manage own preferences"
  on public.user_preferences for all using (auth.uid() = user_id);

-- ---- daily_progress ----
create policy "Users manage own daily progress"
  on public.daily_progress for all using (auth.uid() = user_id);

-- ---- unlock_sessions ----
create policy "Users manage own unlock sessions"
  on public.unlock_sessions for all using (auth.uid() = user_id);

-- ---- usage_logs ----
create policy "Users manage own usage logs"
  on public.usage_logs for all using (auth.uid() = user_id);

-- ---- problems — publicly readable ----
create policy "Problems are publicly readable"
  on public.problems for select using (true);

create policy "Service role manages problems"
  on public.problems for all using (auth.role() = 'service_role');

-- ---- user_solves ----
create policy "Users manage own solves"
  on public.user_solves for all using (auth.uid() = user_id);

-- ---- leetcode_daily_stats ----
create policy "Users read own LC stats"
  on public.leetcode_daily_stats for select using (auth.uid() = user_id);

create policy "Service role manages LC stats"
  on public.leetcode_daily_stats for all using (auth.role() = 'service_role');

-- ---- codeforces_daily_stats ----
create policy "Users read own CF stats"
  on public.codeforces_daily_stats for select using (auth.uid() = user_id);

create policy "Service role manages CF stats"
  on public.codeforces_daily_stats for all using (auth.role() = 'service_role');

-- ---- submissions ----
create policy "Users read own submissions"
  on public.submissions for select using (auth.uid() = user_id);

create policy "Service role manages submissions"
  on public.submissions for all using (auth.role() = 'service_role');

-- ---- wellbeing_daily ----
create policy "Users manage own wellbeing"
  on public.wellbeing_daily for all using (auth.uid() = user_id);

-- ---- wellbeing_app_detail ----
create policy "Users manage own app detail"
  on public.wellbeing_app_detail for all using (auth.uid() = user_id);

-- ---- user_rankings — publicly readable (leaderboard) ----
create policy "Rankings are publicly readable"
  on public.user_rankings for select using (true);

create policy "Service role manages rankings"
  on public.user_rankings for all using (auth.role() = 'service_role');

-- ---- ranking_snapshots ----
create policy "Users read own ranking snapshots"
  on public.ranking_snapshots for select using (auth.uid() = user_id);

create policy "Service role manages ranking snapshots"
  on public.ranking_snapshots for all using (auth.role() = 'service_role');

-- ---- classrooms — members can read ----
create policy "Classroom members can read classrooms"
  on public.classrooms for select
  using (
    id in (
      select classroom_id from public.classroom_members where user_id = auth.uid()
    )
  );

create policy "Authenticated users can create classrooms"
  on public.classrooms for insert with check (auth.uid() = creator_id);

-- ---- classroom_members ----
create policy "Members can read classroom membership"
  on public.classroom_members for select
  using (
    classroom_id in (
      select classroom_id from public.classroom_members where user_id = auth.uid()
    )
  );

create policy "Users can join classrooms"
  on public.classroom_members for insert with check (auth.uid() = user_id);

-- ---- friends ----
create policy "Users can read their own friend relationships"
  on public.friends for select
  using (auth.uid() = requester_id or auth.uid() = addressee_id);

create policy "Users can send friend requests"
  on public.friends for insert with check (auth.uid() = requester_id);

create policy "Addressees can accept/update friend requests"
  on public.friends for update using (auth.uid() = addressee_id);

-- ---- badges — publicly readable ----
create policy "Badges are publicly readable"
  on public.badges for select using (true);

-- ---- user_badges — publicly readable (for profile page) ----
create policy "User badges are publicly readable"
  on public.user_badges for select using (true);

create policy "Service role manages user badges"
  on public.user_badges for all using (auth.role() = 'service_role');

-- ============================================================
-- HELPER FUNCTION: auto-create profile on first sign-in
-- Trigger fires on new row in auth.users (Google Sign-In).
-- This is a safety net; the /api/auth/setup endpoint does the
-- same thing but also creates preferences + rankings rows.
-- ============================================================
create or replace function public.handle_new_auth_user()
returns trigger
language plpgsql
security definer set search_path = public
as $$
declare
  base_username text;
  final_username text;
  attempt int := 0;
begin
  -- Derive username from email
  base_username := regexp_replace(split_part(new.email, '@', 1), '[^a-zA-Z0-9_]', '_', 'g');
  base_username := substring(base_username from 1 for 15);
  final_username := base_username;

  -- Ensure uniqueness
  loop
    exit when not exists (select 1 from public.profiles where username = final_username);
    attempt := attempt + 1;
    final_username := base_username || '_' || attempt::text;
  end loop;

  insert into public.profiles (id, email, username, avatar_emoji)
  values (new.id, new.email, final_username, '🎯')
  on conflict (id) do nothing;

  insert into public.user_preferences (user_id)
  values (new.id)
  on conflict (user_id) do nothing;

  insert into public.user_rankings (user_id)
  values (new.id)
  on conflict (user_id) do nothing;

  return new;
end;
$$;

-- Attach trigger to auth.users
drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
  after insert on auth.users
  for each row execute procedure public.handle_new_auth_user();

-- ============================================================
-- HELPER FUNCTION: expire old unlock sessions
-- Prevents stale is_active=true rows accumulating.
-- Can be called by a Supabase scheduled function or cron.
-- ============================================================
create or replace function public.expire_unlock_sessions()
returns void
language sql
security definer
as $$
  update public.unlock_sessions
  set is_active = false
  where is_active = true and expires_at < now();
$$;


-- ============================================================
-- Migration 002: Schema gaps identified in audit
-- Run after 001_initial_schema.sql
-- ============================================================

-- ── 1. user_preferences: add timer_mode columns ─────────────
-- PRD §7.1 settings table defines timermodeenabled + timerdurationminutes
-- These were in the PRD but missing from the initial schema.

alter table public.user_preferences
  add column if not exists timer_mode_enabled       boolean not null default false,
  add column if not exists timer_duration_minutes   integer not null default 25;

-- ── 2. codeforces_daily_stats: add problems_solved total ────
-- The daily sync only wrote problems_solved_today (delta) but never
-- the cumulative problems_solved column, so CF stats card showed 0.

alter table public.codeforces_daily_stats
  add column if not exists problems_solved integer not null default 0;

-- ── 3. leetcode_daily_stats: add submission_count ───────────
-- Defined in PRD §7.3 but missing from migration 001.

alter table public.leetcode_daily_stats
  add column if not exists submission_count integer not null default 0;

-- ── 4. user_rankings: rename college_rank references ─────────
-- rankings.ts was querying profiles.college_name but the column
-- is profiles.college. Column name is correct in profiles;
-- the bug was purely in TypeScript. No schema change needed —
-- this comment documents the fix location.
-- FIX IS IN: backend/src/routes/rankings.ts (sed fixed above)

-- ── 5. submissions: add index for verification lookups ───────
-- verifySolve() queries by (user_id, problem_slug, verdict, submitted_at).
-- Without this index each verification does a full table scan.

create index if not exists idx_submissions_verify_lookup
  on public.submissions (user_id, problem_slug, verdict, submitted_at desc);

-- ── 6. user_preferences: pending settings columns ───────────
-- daily sync applies pending_usage_mode + pending_daily_solve_target
-- but the original schema in migration 001 didn't add these columns.
-- (They were in the dailySync.ts code but not the SQL.)

alter table public.user_preferences
  add column if not exists pending_usage_mode            text,
  add column if not exists pending_daily_solve_target    integer,
  add column if not exists pending_apply_epoch_day       bigint,
  add column if not exists last_settings_applied_epoch_day bigint;

-- ── 7. blocked_attempts_last_7d view for ranking formula ────
-- PRD §9.1: "blocked_attempts_last_7d * 0.5" penalty in ranking score.
-- The ranking service reads from wellbeing_daily.blocked_attempt_count.
-- This view pre-aggregates for performance.

create or replace view public.user_blocked_attempts_7d as
  select
    user_id,
    coalesce(sum(blocked_attempt_count), 0) as blocked_attempts_last_7d
  from public.wellbeing_daily
  where date >= (current_date - interval '7 days')
  group by user_id;

-- ── 8. Daily expire function — expose as RPC ─────────────────
-- expire_unlock_sessions() was defined in 001 but not exposed
-- as an RPC so it couldn't be called from Cloudflare Workers.

grant execute on function public.expire_unlock_sessions() to service_role;
grant execute on function public.expire_unlock_sessions() to authenticated;

-- ── 9. Classroom rankings: add classroom_id to user_rankings ─
-- user_rankings has classroom_ids uuid[] but there's no FK index.

create index if not exists idx_user_rankings_classrooms
  on public.user_rankings using gin (classroom_ids);

-- ── 10. RLS: allow users to insert own daily_progress rows ───
-- The original RLS policy allowed all ops but didn't explicitly
-- add INSERT. Some Supabase versions need explicit INSERT policy.

drop policy if exists "Users can insert own daily progress" on public.daily_progress;
create policy "Users can insert own daily progress"
  on public.daily_progress for insert with check (auth.uid() = user_id);

drop policy if exists "Users can insert own wellbeing" on public.wellbeing_daily;
create policy "Users can insert own wellbeing"
  on public.wellbeing_daily for insert with check (auth.uid() = user_id);

drop policy if exists "Users can insert own app detail" on public.wellbeing_app_detail;
create policy "Users can insert own app detail"
  on public.wellbeing_app_detail for insert with check (auth.uid() = user_id);

drop policy if exists "Users can insert own unlock sessions" on public.unlock_sessions;
create policy "Users can insert own unlock sessions"
  on public.unlock_sessions for insert with check (auth.uid() = user_id);
