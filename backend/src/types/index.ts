// ============================================================
// CodeUnlock API — Type Definitions
// ============================================================

export interface Env {
  SUPABASE_URL: string;
  SUPABASE_SERVICE_ROLE_KEY: string;
  SUPABASE_ANON_KEY: string;
  RATE_LIMIT: KVNamespace;
}

// ---- Auth ----
export interface AuthUser {
  id: string;
  email: string;
}

// ---- Problems ----
export interface Problem {
  id: number;
  title: string;
  slug: string;
  difficulty: 'Easy' | 'Medium' | 'Hard';
  platform: 'leetcode' | 'codeforces';
  topic: string;
  topics: string[];
  pattern: string;
  platform_id: number | null;
  platform_slug: string;
  track_position: number;
  frequency_score: number;
  companies: string[];
  elo_rating: number;
  codeforces_contest_id: number | null;
  codeforces_problem_index: string | null;
}

export interface NextProblemResponse {
  problem: Problem & { url: string };
  progress: {
    solved_count: number;
    total_count: number;
    current_position: number;
  };
}

// ---- Verification ----
export interface VerifySolveRequest {
  problem_id: number;
  platform: 'leetcode' | 'codeforces';
}

export interface VerifySolveResponse {
  verified: boolean;
  unlock_granted_minutes: number;
  daily_progress: {
    problems_solved: number;
    daily_target: number;
    target_met: boolean;
    unlock_minutes_earned: number;
    unlock_minutes_used: number;
  };
  unlock_session: {
    id: string;
    expires_at: string;
    duration_minutes: number;
  } | null;
}

// ---- Settings ----
export interface UserSettings {
  usage_mode: 'relaxed' | 'focused' | 'hardcore' | 'custom';
  daily_solve_target: number;
  restricted_apps: string[];
  per_app_limits: Record<string, number>;
  emergency_unlock_enabled: boolean;
  free_unlock_enabled: boolean;
  is_first_setup_complete: boolean;
  pending_changes: {
    usage_mode?: string;
    daily_solve_target?: number;
    apply_date: string;
  } | null;
}

export interface UpdateSettingsRequest {
  usage_mode?: 'relaxed' | 'focused' | 'hardcore' | 'custom';
  daily_solve_target?: number;
  restricted_apps?: string[];
  per_app_limits?: Record<string, number>;
  emergency_unlock_enabled?: boolean;
  free_unlock_enabled?: boolean;
}

// ---- Wellbeing ----
// FIX: Added missing blocked_attempt_count, unlock_count, longest_session_app
// per PRD §6.5.1 "Digital Wellbeing" sync fields and §8.3 Device Wellbeing Push
export interface WellbeingSyncRequest {
  date: string;
  apps: {
    app_package: string;
    app_name: string;
    duration_seconds: number;
    pickup_count: number;
    category: 'social' | 'entertainment' | 'productivity' | 'other';
  }[];
  total_screen_time_minutes: number;
  pickup_count: number;
  // NEW — required for wellbeing_daily.blocked_attempt_count
  blocked_attempt_count?: number;
  // NEW — required for wellbeing_daily.unlock_count
  unlock_count?: number;
  // NEW — required for wellbeing_daily.longest_session_app
  longest_session_app?: string;
}

// ---- Profile ----
// NEW: Profile update request (PRD §11)
export interface UpdateProfileRequest {
  username?: string;
  avatar_emoji?: string;
  college?: string;
  leetcode_username?: string;
  codeforces_handle?: string;
}

// ---- Daily Progress ----
// NEW: Response shape for GET /api/daily-progress (PRD §10 Dashboard)
export interface DailyProgressResponse {
  date: string;
  problems_solved: number;
  daily_target: number;
  target_met: boolean;
  unlock_minutes_earned: number;
  unlock_minutes_used: number;
  unlock_minutes_remaining: number;
  free_unlock_used: boolean;
  emergency_unlock_used: boolean;
  bonus_time_earned: boolean;
  total_screen_time_minutes: number;
  restricted_app_time_minutes: number;
}

// ---- Stats ----
export interface LeetCodeStats {
  total_solved: number;
  easy_solved: number;
  medium_solved: number;
  hard_solved: number;
  contest_rating: number;
  global_ranking: number;
  problems_solved_today: number;
  as_of: string;
}

export interface CodeforcesStats {
  current_rating: number;
  max_rating: number;
  rank: string;
  problems_solved: number;
  problems_solved_today: number;
  contest_count: number;
  as_of: string;
}

export interface TrackProgress {
  total_problems: number;
  solved: number;
  current_streak: number;
  difficulty_breakdown: Record<string, number>;
  topic_breakdown: Record<string, number>;
  next_problem: { id: number; title: string; slug: string } | null;
}

// ---- Rankings ----
export interface RankingEntry {
  rank: number;
  user_id: string;
  username: string;
  avatar_emoji: string;
  ranking_score: number;
  total_solves: number;
  streak_days: number;
  weekly_change: number;
}

export interface RankingsResponse {
  rankings: RankingEntry[];
  my_rank: { rank: number; ranking_score: number } | null;
  total_users: number;
}

// ---- Unlock ----
export interface UnlockSession {
  id: string;
  app_package: string;
  unlock_type: 'coding' | 'free' | 'emergency';
  duration_minutes: number;
  granted_at: string;
  expires_at: string;
  is_active: boolean;
}

// ---- API Response Envelope ----
export interface ApiSuccess<T> {
  success: true;
  data: T;
}

export interface ApiError {
  success: false;
  error: {
    code: string;
    message: string;
    details?: unknown;
  };
}

export type ApiResponse<T> = ApiSuccess<T> | ApiError;

// ---- LeetCode GraphQL Types ----
export interface LeetCodeRecentSubmission {
  title: string;
  titleSlug: string;
  timestamp: string;
  statusDisplay: string;
  lang: string;
  runtime?: string;
  memory?: string;
}

// ---- Codeforces API Types ----
export interface CodeforcesSubmission {
  id: number;
  contestId: number;
  creationTimeSeconds: number;
  problem: {
    contestId: number;
    index: string;
    name: string;
    rating?: number;
  };
  programmingLanguage: string;
  verdict: string;
  timeConsumedMillis: number;
  memoryConsumedBytes: number;
  passedTestCount: number;
}
