// ============================================================
// Sync Routes — /api/sync/*, /api/wellbeing/sync
// FIXES:
//   1. Wellbeing score formula was missing "+ (solves_today * 5)"
//      per PRD §8.3: score = 100 - (restrictedapptime/totaltime*50)
//                            - (excessivepickups*2) + (solves_today*5)
//   2. handleWellbeingSync was not writing blocked_attempt_count
//      or unlock_count to wellbeing_daily (PRD §7.4)
//   3. handleWellbeingSync was not writing longest_session_app (PRD §7.4)
//   4. handleSyncTrigger did not compute problems_solved_today delta
//      (PRD §6.5.1: "Delta from yesterday") for either platform
// ============================================================
import { SupabaseClient } from '@supabase/supabase-js';
import { AuthUser, WellbeingSyncRequest } from '../types';
import { success, error } from '../utils/response';
import * as lc from '../services/leetcode';
import * as cf from '../services/codeforces';

export async function handleWellbeingSync(
  supabase: SupabaseClient,
  user: AuthUser,
  body: WellbeingSyncRequest
): Promise<Response> {
  const {
    date,
    apps,
    total_screen_time_minutes,
    pickup_count,
    // FIX: These fields were in PRD §8.3 but missing from the original handler
    blocked_attempt_count = 0,
    unlock_count = 0,
    longest_session_app,
  } = body;

  if (!date || !apps) return error('INVALID_REQUEST', 'date and apps are required');

  // Upsert per-app details
  for (const app of apps) {
    await supabase.from('wellbeing_app_detail').upsert({
      user_id: user.id,
      date,
      app_package: app.app_package,
      app_name: app.app_name,
      duration_minutes: Math.round(app.duration_seconds / 60),
      pickup_count: app.pickup_count,
      category: app.category,
    }, { onConflict: 'user_id,date,app_package' });
  }

  // Aggregate times
  const restrictedTime = apps
    .filter(a => a.category === 'social' || a.category === 'entertainment')
    .reduce((s, a) => s + Math.round(a.duration_seconds / 60), 0);
  const productiveTime = apps
    .filter(a => a.category === 'productivity')
    .reduce((s, a) => s + Math.round(a.duration_seconds / 60), 0);

  // Derive longest session app from the data if not explicitly provided
  const longestApp = longest_session_app || (() => {
    if (apps.length === 0) return null;
    return apps.reduce((a, b) => a.duration_seconds > b.duration_seconds ? a : b).app_package;
  })();

  // Get today's solve count so we can factor it into wellbeing score
  const { data: dpRow } = await supabase
    .from('daily_progress')
    .select('problems_solved')
    .eq('user_id', user.id)
    .eq('date', date)
    .maybeSingle();
  const solvesToday = dpRow?.problems_solved || 0;

  // FIX: PRD §8.3 wellbeing score formula:
  // score = 100 - (restrictedapptime / totaltime * 50) - (excessivepickups * 2) + (solves_today * 5)
  // Original code was missing the "+ (solves_today * 5)" term.
  const totalTime = Math.max(total_screen_time_minutes || 1, 1);
  const excessivePickups = Math.max(0, (pickup_count || 0) - 30); // >30 pickups is excessive
  const score = Math.max(0, Math.min(100,
    100
    - (restrictedTime / totalTime * 50)
    - (excessivePickups * 2)
    + (solvesToday * 5)          // ← was missing in original
  ));

  // Compute average session duration
  const avgSessionDuration = apps.length > 0
    ? (apps.reduce((s, a) => s + a.duration_seconds, 0) / apps.length / 60)
    : 0;

  // FIX: Now writing all columns PRD §7.4 requires
  await supabase.from('wellbeing_daily').upsert({
    user_id: user.id,
    date,
    total_screen_time_minutes: total_screen_time_minutes || 0,
    restricted_app_time_minutes: restrictedTime,
    productive_time_minutes: productiveTime,
    pickup_count: pickup_count || 0,
    blocked_attempt_count: blocked_attempt_count,   // FIX: was not written
    unlock_count: unlock_count,                      // FIX: was not written
    average_session_duration_minutes: Math.round(avgSessionDuration * 10) / 10,
    longest_session_app: longestApp,                 // FIX: was not written
    wellbeing_score: Math.round(score * 10) / 10,
    updated_at: new Date().toISOString(),
  }, { onConflict: 'user_id,date' });

  // Also sync restricted_app_time into daily_progress for unified dashboard
  await supabase.from('daily_progress').upsert({
    user_id: user.id,
    date,
    total_screen_time_minutes: total_screen_time_minutes || 0,
    restricted_app_time_minutes: restrictedTime,
    updated_at: new Date().toISOString(),
  }, { onConflict: 'user_id,date' });

  return success({
    wellbeing_score: Math.round(score * 10) / 10,
    restricted_app_time_minutes: restrictedTime,
    productive_time_minutes: productiveTime,
  });
}

export async function handleSyncTrigger(
  supabase: SupabaseClient,
  user: AuthUser
): Promise<Response> {
  const today = new Date().toISOString().split('T')[0];

  const { data: profile } = await supabase
    .from('profiles').select('leetcode_username, codeforces_handle')
    .eq('id', user.id).single();

  if (!profile) return error('PROFILE_NOT_FOUND', 'User profile not found');

  const results: string[] = [];

  // ---- LeetCode Sync ----
  if (profile.leetcode_username) {
    try {
      const lcProfile = await lc.fetchUserProfile(profile.leetcode_username);

      // FIX: Compute problems_solved_today delta from yesterday's snapshot
      // PRD §6.5.1: "problemssolvedtoday — Delta from yesterday"
      const yesterday = new Date();
      yesterday.setDate(yesterday.getDate() - 1);
      const yesterdayStr = yesterday.toISOString().split('T')[0];
      const { data: lcYesterday } = await supabase
        .from('leetcode_daily_stats')
        .select('total_solved')
        .eq('user_id', user.id)
        .eq('date', yesterdayStr)
        .maybeSingle();

      const problemsSolvedToday = lcYesterday
        ? Math.max(0, lcProfile.totalSolved - (lcYesterday.total_solved || 0))
        : 0;

      await supabase.from('leetcode_daily_stats').upsert({
        user_id: user.id,
        date: today,
        total_solved: lcProfile.totalSolved,
        easy_solved: lcProfile.easySolved,
        medium_solved: lcProfile.mediumSolved,
        hard_solved: lcProfile.hardSolved,
        contest_rating: lcProfile.contestRating,
        global_ranking: lcProfile.globalRanking,
        reputation: lcProfile.reputation,
        problems_solved_today: problemsSolvedToday,  // FIX: was always 0/missing
      }, { onConflict: 'user_id,date' });

      results.push(`leetcode: synced (${lcProfile.totalSolved} total, +${problemsSolvedToday} today)`);
    } catch (e) {
      results.push(`leetcode: failed (${e instanceof Error ? e.message : 'unknown'})`);
    }
  }

  // ---- Codeforces Sync ----
  if (profile.codeforces_handle) {
    try {
      const cfInfo = await cf.fetchUserInfo(profile.codeforces_handle);

      // FIX: Compute problems_solved_today delta for Codeforces too
      const yesterday = new Date();
      yesterday.setDate(yesterday.getDate() - 1);
      const yesterdayStr = yesterday.toISOString().split('T')[0];
      const { data: cfYesterday } = await supabase
        .from('codeforces_daily_stats')
        .select('problems_solved')
        .eq('user_id', user.id)
        .eq('date', yesterdayStr)
        .maybeSingle();

      // For CF we fetch submissions to get today's count accurately
      let cfSolvedToday = 0;
      try {
        const submissions = await cf.fetchRecentSubmissions(profile.codeforces_handle, 50);
        const todayStart = new Date(today).getTime() / 1000;
        const uniqueProblems = new Set(
          submissions
            .filter(s => s.verdict === 'OK' && s.creationTimeSeconds >= todayStart)
            .map(s => `${s.contestId}-${s.problem.index}`)
        );
        cfSolvedToday = uniqueProblems.size;
      } catch {
        // fall back to delta approach
        cfSolvedToday = cfYesterday
          ? Math.max(0, (cfInfo.contestCount || 0) - (cfYesterday.problems_solved || 0))
          : 0;
      }

      await supabase.from('codeforces_daily_stats').upsert({
        user_id: user.id,
        date: today,
        current_rating: cfInfo.currentRating,
        max_rating: cfInfo.maxRating,
        rank: cfInfo.rank,
        contest_count: cfInfo.contestCount,
        problems_solved_today: cfSolvedToday,   // FIX: was missing
      }, { onConflict: 'user_id,date' });

      results.push(`codeforces: synced (rating: ${cfInfo.currentRating}, +${cfSolvedToday} today)`);
    } catch (e) {
      results.push(`codeforces: failed (${e instanceof Error ? e.message : 'unknown'})`);
    }
  }

  // Update last_sync_at
  await supabase.from('profiles')
    .update({ last_sync_at: new Date().toISOString() })
    .eq('id', user.id);

  return success({ synced: results, synced_at: new Date().toISOString() });
}
