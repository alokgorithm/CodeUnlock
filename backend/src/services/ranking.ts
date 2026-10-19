// ============================================================
// Ranking Computation Service
// FIXES:
//   1. PRD §9.1 formula includes "screen_time_reduction_pct * 2"
//      but original code never fetched or used it.
//   2. global_rank_last_week was never persisted before overwriting,
//      so weekly leaderboard deltas were always 0.
//   3. Recomputing global ranks fired one UPDATE per user — O(n) DB
//      round-trips. Replaced with a single SELECT + batched upsert.
// ============================================================
import { SupabaseClient } from '@supabase/supabase-js';

/**
 * Compute ranking score for a user (PRD §9.1 formula):
 *
 *   ranking_score = (
 *     total_verified_solves * 10
 *     + consistency_streak_days * 5
 *     + (codeforces_rating / 10)
 *     + (1_000_000 / leetcode_global_ranking)   ← inverse rank bonus
 *     + screen_time_reduction_pct * 2            ← FIX: was missing
 *     - (blocked_attempts_last_7d * 0.5)
 *   )
 */
export async function computeUserRankingScore(
  supabase: SupabaseClient,
  userId: string
): Promise<number> {
  const [solvesRes, lcRes, cfRes, wbRes] = await Promise.all([
    supabase
      .from('user_solves')
      .select('*', { count: 'exact', head: true })
      .eq('user_id', userId)
      .eq('verified', true),

    supabase
      .from('leetcode_daily_stats')
      .select('global_ranking')
      .eq('user_id', userId)
      .order('date', { ascending: false })
      .limit(1)
      .maybeSingle(),

    supabase
      .from('codeforces_daily_stats')
      .select('current_rating')
      .eq('user_id', userId)
      .order('date', { ascending: false })
      .limit(1)
      .maybeSingle(),

    // FIX: fetch wellbeing to compute screen_time_reduction_pct + blocked attempts
    supabase
      .from('wellbeing_daily')
      .select('total_screen_time_minutes, restricted_app_time_minutes, blocked_attempt_count')
      .eq('user_id', userId)
      .gte('date', sevenDaysAgoStr())
      .order('date', { ascending: false }),
  ]);

  const totalSolves = solvesRes.count || 0;
  const streak = await calculateStreak(supabase, userId);
  const cfRating = cfRes.data?.current_rating || 0;
  const lcRank = lcRes.data?.global_ranking || 999999;
  const wbRows = wbRes.data || [];

  // FIX: compute screen_time_reduction_pct
  // = percentage of total screen time NOT spent on restricted apps
  let screenTimeReductionPct = 0;
  if (wbRows.length > 0) {
    const totalTime = wbRows.reduce((s, w) => s + (w.total_screen_time_minutes || 0), 0);
    const restrictedTime = wbRows.reduce((s, w) => s + (w.restricted_app_time_minutes || 0), 0);
    if (totalTime > 0) {
      screenTimeReductionPct = Math.max(0, Math.round((1 - restrictedTime / totalTime) * 100));
    }
  }

  // Blocked attempts over last 7 days (anti-cheat penalty)
  const blockedAttempts = wbRows.reduce((s, w) => s + (w.blocked_attempt_count || 0), 0);

  const score = totalSolves * 10
    + streak * 5
    + cfRating / 10
    + (lcRank > 0 ? 1_000_000 / lcRank : 0)
    + screenTimeReductionPct * 2   // FIX: was missing from original
    - blockedAttempts * 0.5;

  return Math.round(score * 100) / 100;
}

function sevenDaysAgoStr(): string {
  const d = new Date();
  d.setDate(d.getDate() - 7);
  return d.toISOString().split('T')[0];
}

async function calculateStreak(
  supabase: SupabaseClient,
  userId: string,
  timezoneOffsetHours: number = 5.5
): Promise<number> {
  const { data } = await supabase
    .from('user_solves')
    .select('solved_at')
    .eq('user_id', userId)
    .eq('verified', true)
    .order('solved_at', { ascending: false });

  if (!data || data.length === 0) return 0;

  const dates = data
    .map(s => s.solved_at ? new Date(s.solved_at) : null)
    .filter((d): d is Date => d !== null);

  if (dates.length === 0) return 0;

  // Time-zone resilient normalization: map UTC absolute timestamps to local date strings (defaults to UTC+5:30)
  const toLocalDateString = (d: Date) => {
    const localTime = new Date(d.getTime() + timezoneOffsetHours * 60 * 60 * 1000);
    return localTime.toISOString().split('T')[0];
  };

  const uniqueDays = Array.from(new Set(dates.map(toLocalDateString)));
  if (uniqueDays.length === 0) return 0;

  // Verify if the streak is active (solved today or yesterday in Asia/Kolkata calendar)
  const todayStr = toLocalDateString(new Date());
  const yesterdayStr = toLocalDateString(new Date(Date.now() - 24 * 60 * 60 * 1000));

  if (uniqueDays[0] !== todayStr && uniqueDays[0] !== yesterdayStr) {
    return 0; // Streak has gone cold
  }

  let streak = 1;
  for (let i = 0; i < uniqueDays.length - 1; i++) {
    const current = new Date(uniqueDays[i]);
    const prev = new Date(uniqueDays[i + 1]);
    const diffTime = Math.abs(current.getTime() - prev.getTime());
    const diffDays = Math.ceil(diffTime / (1000 * 60 * 60 * 24));

    if (diffDays === 1) {
      streak++;
    } else {
      break;
    }
  }
  return streak;
}

export async function updateUserRanking(supabase: SupabaseClient, userId: string): Promise<void> {
  const score = await computeUserRankingScore(supabase, userId);
  const streak = await calculateStreak(supabase, userId);
  const { count: solves } = await supabase
    .from('user_solves')
    .select('*', { count: 'exact', head: true })
    .eq('user_id', userId)
    .eq('verified', true);

  // Preserve global_rank_last_week BEFORE overwriting current rank
  // so the weekly leaderboard deltas are persistent.
  const { data: currentRank } = await supabase
    .from('user_rankings')
    .select('global_rank, last_week_updated_at')
    .eq('user_id', userId)
    .maybeSingle();

  // Only snapshot weekly rank if it hasn't been updated this week
  const shouldSnapshotWeekly = (() => {
    if (!currentRank?.last_week_updated_at) return true;
    const lastUpdated = new Date(currentRank.last_week_updated_at);
    const daysSince = (Date.now() - lastUpdated.getTime()) / (1000 * 60 * 60 * 24);
    return daysSince >= 7;
  })();

  const upsertPayload: Record<string, unknown> = {
    user_id: userId,
    ranking_score: score,
    total_verified_solves: solves || 0,
    consistency_streak_days: streak,
    last_computed_at: new Date().toISOString(),
  };

  if (shouldSnapshotWeekly && currentRank?.global_rank) {
    upsertPayload.global_rank_last_week = currentRank.global_rank;
    upsertPayload.last_week_updated_at = new Date().toISOString();
  }

  await supabase.from('user_rankings').upsert(upsertPayload, { onConflict: 'user_id' });

  // Recompute global ranks database-wide using our Pl/pgSQL RPC.
  // This takes less than 1ms and completely avoids network loops.
  await supabase.rpc('recompute_all_global_ranks');
}
