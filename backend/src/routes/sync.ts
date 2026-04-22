// ============================================================
// Sync Routes — /api/sync/*, /api/wellbeing/sync
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
  const { date, apps, total_screen_time_minutes, pickup_count } = body;
  if (!date || !apps) return error('INVALID_REQUEST', 'date and apps are required');

  // Upsert per-app details
  for (const app of apps) {
    await supabase.from('wellbeing_app_detail').upsert({
      user_id: user.id, date, app_package: app.app_package,
      app_name: app.app_name, duration_minutes: Math.round(app.duration_seconds / 60),
      pickup_count: app.pickup_count, category: app.category,
    }, { onConflict: 'user_id,date,app_package' });
  }

  // Compute aggregates
  const restrictedTime = apps
    .filter(a => a.category === 'social' || a.category === 'entertainment')
    .reduce((s, a) => s + Math.round(a.duration_seconds / 60), 0);
  const productiveTime = apps
    .filter(a => a.category === 'productivity')
    .reduce((s, a) => s + Math.round(a.duration_seconds / 60), 0);

  // Wellbeing score formula from PRD
  const totalTime = total_screen_time_minutes || 1;
  const score = Math.max(0, Math.min(100,
    100 - (restrictedTime / totalTime * 50) - (Math.max(0, pickup_count - 30) * 2)
  ));

  await supabase.from('wellbeing_daily').upsert({
    user_id: user.id, date,
    total_screen_time_minutes, restricted_app_time_minutes: restrictedTime,
    productive_time_minutes: productiveTime, pickup_count: pickup_count || 0,
    wellbeing_score: Math.round(score * 10) / 10,
    updated_at: new Date().toISOString(),
  }, { onConflict: 'user_id,date' });

  return success({ wellbeing_score: Math.round(score * 10) / 10 });
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

  // Sync LeetCode
  if (profile.leetcode_username) {
    try {
      const lcProfile = await lc.fetchUserProfile(profile.leetcode_username);
      await supabase.from('leetcode_daily_stats').upsert({
        user_id: user.id, date: today,
        total_solved: lcProfile.totalSolved, easy_solved: lcProfile.easySolved,
        medium_solved: lcProfile.mediumSolved, hard_solved: lcProfile.hardSolved,
        contest_rating: lcProfile.contestRating, global_ranking: lcProfile.globalRanking,
        reputation: lcProfile.reputation,
      }, { onConflict: 'user_id,date' });
      results.push('leetcode: synced');
    } catch (e) {
      results.push(`leetcode: failed (${e instanceof Error ? e.message : 'unknown'})`);
    }
  }

  // Sync Codeforces
  if (profile.codeforces_handle) {
    try {
      const cfInfo = await cf.fetchUserInfo(profile.codeforces_handle);
      await supabase.from('codeforces_daily_stats').upsert({
        user_id: user.id, date: today,
        current_rating: cfInfo.currentRating, max_rating: cfInfo.maxRating,
        rank: cfInfo.rank, contest_count: cfInfo.contestCount,
      }, { onConflict: 'user_id,date' });
      results.push('codeforces: synced');
    } catch (e) {
      results.push(`codeforces: failed (${e instanceof Error ? e.message : 'unknown'})`);
    }
  }

  // Update last_sync_at
  await supabase.from('profiles').update({ last_sync_at: new Date().toISOString() }).eq('id', user.id);

  return success({ synced: results });
}
