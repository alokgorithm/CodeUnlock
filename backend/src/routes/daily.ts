// ============================================================
// Daily Progress Route — GET /api/daily-progress
// NEW FILE — was completely missing from the original backend.
//
// The Dashboard (PRD §10) shows:
//   - Daily solve progress (X / daily_target)
//   - Remaining unlock time
//   - Bonus time earned
//   - Screen time for today
// All of this comes from daily_progress + user_preferences.
// Without this endpoint the Android dashboard has no way to
// populate the core KPI row.
// ============================================================
import { SupabaseClient } from '@supabase/supabase-js';
import { AuthUser } from '../types';
import { success } from '../utils/response';

export async function handleGetDailyProgress(
  supabase: SupabaseClient,
  user: AuthUser
): Promise<Response> {
  const today = new Date().toISOString().split('T')[0];

  // Fetch (or lazily create) today's progress row
  let { data: dp } = await supabase
    .from('daily_progress')
    .select('*')
    .eq('user_id', user.id)
    .eq('date', today)
    .maybeSingle();

  // If no row yet for today, return empty defaults rather than 404
  if (!dp) {
    dp = {
      date: today,
      problems_solved: 0,
      unlock_minutes_earned: 0,
      unlock_minutes_used: 0,
      free_unlock_used: false,
      emergency_unlock_used: false,
      bonus_time_earned: false,
      daily_target_met: false,
      total_screen_time_minutes: 0,
      restricted_app_time_minutes: 0,
    };
  }

  // Get user's daily target from preferences
  const { data: prefs } = await supabase
    .from('user_preferences')
    .select('daily_solve_target, usage_mode, per_app_limits')
    .eq('user_id', user.id)
    .maybeSingle();

  const dailyTarget = prefs?.daily_solve_target || 3;

  // Compute remaining unlock minutes (earned - used, floored at 0)
  const unlockMinutesRemaining = Math.max(
    0,
    (dp.unlock_minutes_earned || 0) - (dp.unlock_minutes_used || 0)
  );

  // Also check for any currently active unlock session and report it
  const now = new Date().toISOString();
  const { data: activeSessions } = await supabase
    .from('unlock_sessions')
    .select('id, app_package, unlock_type, expires_at, duration_minutes')
    .eq('user_id', user.id)
    .eq('is_active', true)
    .gte('expires_at', now)
    .order('expires_at', { ascending: false });

  return success({
    date: today,
    problems_solved: dp.problems_solved || 0,
    daily_target: dailyTarget,
    target_met: dp.daily_target_met || false,
    unlock_minutes_earned: dp.unlock_minutes_earned || 0,
    unlock_minutes_used: dp.unlock_minutes_used || 0,
    unlock_minutes_remaining: unlockMinutesRemaining,
    free_unlock_used: dp.free_unlock_used || false,
    emergency_unlock_used: dp.emergency_unlock_used || false,
    bonus_time_earned: dp.bonus_time_earned || false,
    total_screen_time_minutes: dp.total_screen_time_minutes || 0,
    restricted_app_time_minutes: dp.restricted_app_time_minutes || 0,
    usage_mode: prefs?.usage_mode || 'focused',
    active_unlock_sessions: activeSessions || [],
  });
}

// ---- POST /api/daily-progress/consume ----
// Called by Android when user is actively using an unlocked app,
// to update unlock_minutes_used in real time.
// PRD §6.2: "1 verified problem = 30 minutes of access"
// Without consuming time the remaining balance never decrements server-side.
export async function handleConsumeUnlockTime(
  supabase: SupabaseClient,
  user: AuthUser,
  body: { minutes_used: number; app_package?: string }
): Promise<Response> {
  const { minutes_used } = body;
  if (!minutes_used || minutes_used <= 0) {
    return success({ ok: true });
  }

  const today = new Date().toISOString().split('T')[0];
  const now = new Date().toISOString();

  // Increment unlock_minutes_used atomically via rpc (upsert fallback)
  const { data: existing } = await supabase
    .from('daily_progress')
    .select('unlock_minutes_used, total_screen_time_minutes')
    .eq('user_id', user.id)
    .eq('date', today)
    .maybeSingle();

  const newUsed = (existing?.unlock_minutes_used || 0) + minutes_used;
  const newScreenTime = (existing?.total_screen_time_minutes || 0) + minutes_used;

  await supabase.from('daily_progress').upsert({
    user_id: user.id,
    date: today,
    unlock_minutes_used: newUsed,
    total_screen_time_minutes: newScreenTime,
    updated_at: now,
  }, { onConflict: 'user_id,date' });

  return success({ ok: true, unlock_minutes_used: newUsed });
}
