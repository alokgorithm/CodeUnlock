// ============================================================
// Badge Progress Computation Service
// FIXES:
//   1. Only handled "solves" and "streak_days" requirement types.
//      PRD §13 defines 3 more categories:
//        - Discipline: restricted_app_hours, no_emergency_unlocks
//        - Consistency: perfect_week (daily target hit 7 days in a row)
//        - Special: global_top_n (reach global top 100)
//   2. earned_at was overwritten on every update even after already earned
// ============================================================
import { SupabaseClient } from '@supabase/supabase-js';

export async function updateBadgeProgress(
  supabase: SupabaseClient,
  userId: string
): Promise<void> {
  const { data: badges } = await supabase.from('badges').select('*');
  if (!badges) return;

  // Collect all user stats once to avoid N+1 inside the badge loop
  const stats = await collectUserStats(supabase, userId);

  for (const badge of badges) {
    const req = badge.requirement as Record<string, number | string>;

    let progress = 0;

    // ---- Solving badges: solves ≥ N ----
    if (req.solves !== undefined) {
      progress = Math.min((stats.totalSolves) / Number(req.solves), 1.0);
    }

    // ---- Consistency badges: consecutive daily solve streak ----
    else if (req.streak_days !== undefined) {
      progress = Math.min(stats.currentStreak / Number(req.streak_days), 1.0);
    }

    // ---- FIX: Discipline — restricted app usage below threshold for N days ----
    // Zen Master: <1 hr restricted apps for 7 days
    // Guardian:   <30 min restricted apps for 30 days
    else if (req.restricted_app_hours_max !== undefined && req.days !== undefined) {
      progress = computeRestrictedAppDisciplineBadge(
        stats.wellbeingRows,
        Number(req.restricted_app_hours_max),
        Number(req.days)
      );
    }

    // ---- FIX: Discipline — no emergency unlocks for N days ----
    // Block Hero: 0 emergency unlocks for 30 days
    else if (req.no_emergency_unlocks_days !== undefined) {
      progress = computeNoEmergencyBadge(
        stats.dailyProgressRows,
        Number(req.no_emergency_unlocks_days)
      );
    }

    // ---- FIX: Consistency — hit daily solve target every day for 7 days ----
    // Perfect Week
    else if (req.perfect_days !== undefined) {
      progress = computePerfectDaysBadge(
        stats.dailyProgressRows,
        Number(req.perfect_days)
      );
    }

    // ---- FIX: Special — reach global top N ----
    // Top 100
    else if (req.global_top_n !== undefined) {
      const rank = stats.globalRank || 999999;
      progress = rank <= Number(req.global_top_n) ? 1.0 : 0;
    }

    // Fetch existing earned_at so we don't overwrite it once earned
    const { data: existing } = await supabase
      .from('user_badges')
      .select('earned_at')
      .eq('user_id', userId)
      .eq('badge_id', badge.id)
      .maybeSingle();

    // FIX: Don't overwrite earned_at if badge was already earned
    const earnedAt = existing?.earned_at
      ? existing.earned_at
      : progress >= 1.0
        ? new Date().toISOString()
        : null;

    await supabase.from('user_badges').upsert({
      user_id: userId,
      badge_id: badge.id,
      badge_type: badge.category,
      badge_name: badge.name,
      progress: Math.round(progress * 1000) / 1000,
      ...(earnedAt && { earned_at: earnedAt }),
    }, { onConflict: 'user_id,badge_id' });
  }
}

// ---- Stat collection ----

interface UserStats {
  totalSolves: number;
  currentStreak: number;
  globalRank: number | null;
  wellbeingRows: { date: string; restricted_app_time_minutes: number }[];
  dailyProgressRows: { date: string; problems_solved: number; emergency_unlock_used: boolean; daily_target_met: boolean }[];
}

async function collectUserStats(supabase: SupabaseClient, userId: string): Promise<UserStats> {
  const [solvesRes, rankRes, wellbeingRes, dpRes] = await Promise.all([
    supabase
      .from('user_solves')
      .select('*', { count: 'exact', head: true })
      .eq('user_id', userId)
      .eq('verified', true),

    supabase
      .from('user_rankings')
      .select('global_rank')
      .eq('user_id', userId)
      .maybeSingle(),

    supabase
      .from('wellbeing_daily')
      .select('date, restricted_app_time_minutes')
      .eq('user_id', userId)
      .order('date', { ascending: false })
      .limit(60),

    supabase
      .from('daily_progress')
      .select('date, problems_solved, emergency_unlock_used, daily_target_met')
      .eq('user_id', userId)
      .order('date', { ascending: false })
      .limit(100),
  ]);

  const totalSolves = solvesRes.count || 0;
  const globalRank = rankRes.data?.global_rank || null;
  const wellbeingRows = wellbeingRes.data || [];
  const dailyProgressRows = dpRes.data || [];

  // Compute streak from daily_progress
  let currentStreak = 0;
  const today = new Date();
  for (let i = 0; i < dailyProgressRows.length; i++) {
    const exp = new Date(today);
    exp.setDate(exp.getDate() - i);
    if (
      dailyProgressRows[i]?.date === exp.toISOString().split('T')[0] &&
      dailyProgressRows[i].problems_solved > 0
    ) {
      currentStreak++;
    } else {
      break;
    }
  }

  return { totalSolves, currentStreak, globalRank, wellbeingRows, dailyProgressRows };
}

// ---- Badge-specific helpers ----

/**
 * Compute discipline badge progress for restricted app usage.
 * Checks consecutive days where restricted app time was below the max threshold.
 */
function computeRestrictedAppDisciplineBadge(
  wellbeingRows: { date: string; restricted_app_time_minutes: number }[],
  maxHours: number,
  requiredDays: number
): number {
  const maxMinutes = maxHours * 60;
  let consecutiveDays = 0;
  const today = new Date();

  for (let i = 0; i < Math.min(requiredDays, wellbeingRows.length); i++) {
    const expected = new Date(today);
    expected.setDate(expected.getDate() - i);
    const row = wellbeingRows[i];
    if (
      row?.date === expected.toISOString().split('T')[0] &&
      (row.restricted_app_time_minutes || 0) < maxMinutes
    ) {
      consecutiveDays++;
    } else {
      break; // must be consecutive
    }
  }

  return Math.min(consecutiveDays / requiredDays, 1.0);
}

/**
 * Compute Block Hero badge: 0 emergency unlocks for N consecutive days.
 */
function computeNoEmergencyBadge(
  dpRows: { date: string; emergency_unlock_used: boolean }[],
  requiredDays: number
): number {
  let consecutiveDays = 0;
  const today = new Date();

  for (let i = 0; i < Math.min(requiredDays, dpRows.length); i++) {
    const expected = new Date(today);
    expected.setDate(expected.getDate() - i);
    const row = dpRows[i];
    if (
      row?.date === expected.toISOString().split('T')[0] &&
      !row.emergency_unlock_used
    ) {
      consecutiveDays++;
    } else {
      break;
    }
  }

  return Math.min(consecutiveDays / requiredDays, 1.0);
}

/**
 * Compute Perfect Week badge: hit daily_target every day for N days.
 */
function computePerfectDaysBadge(
  dpRows: { date: string; daily_target_met: boolean }[],
  requiredDays: number
): number {
  let perfectDays = 0;
  const today = new Date();

  for (let i = 0; i < Math.min(requiredDays, dpRows.length); i++) {
    const expected = new Date(today);
    expected.setDate(expected.getDate() - i);
    const row = dpRows[i];
    if (
      row?.date === expected.toISOString().split('T')[0] &&
      row.daily_target_met
    ) {
      perfectDays++;
    } else {
      break;
    }
  }

  return Math.min(perfectDays / requiredDays, 1.0);
}
