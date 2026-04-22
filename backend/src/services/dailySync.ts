// ============================================================
// Daily Sync Cron Worker — PRD §6.5 + §8.1
// Runs at 00:00 UTC daily via Cloudflare Cron Trigger
// ============================================================
import { SupabaseClient } from '@supabase/supabase-js';
import * as lc from './leetcode';
import * as cf from './codeforces';
import { updateUserRanking } from './ranking';
import { updateBadgeProgress } from './badges';

interface SyncResult {
  userId: string;
  username: string;
  leetcode: string;
  codeforces: string;
  ranking: string;
  badges: string;
}

/**
 * Daily sync for all active users.
 * Called by Cloudflare Cron Trigger at 00:00 UTC.
 *
 * PRD §8.1 Flow:
 *   1. Get active users (logged in within 30 days)
 *   2. For each user (batched):
 *      a. Fetch LeetCode stats → upsert leetcode_daily_stats
 *      b. Fetch Codeforces stats → upsert codeforces_daily_stats
 *      c. Recompute ranking → update user_rankings + ranking_snapshots
 *      d. Update badge progress → upsert user_badges
 *      e. Update profiles.last_sync_at
 */
export async function runDailySync(supabase: SupabaseClient): Promise<{
  totalUsers: number;
  synced: number;
  failed: number;
  results: SyncResult[];
}> {
  const today = new Date().toISOString().split('T')[0];
  const thirtyDaysAgo = new Date();
  thirtyDaysAgo.setDate(thirtyDaysAgo.getDate() - 30);

  // 1. Get all active users with their platform usernames
  const { data: activeUsers, error: fetchErr } = await supabase
    .from('profiles')
    .select('id, username, leetcode_username, codeforces_handle, last_sync_at')
    .or(`last_sync_at.gte.${thirtyDaysAgo.toISOString()},last_sync_at.is.null`);

  if (fetchErr || !activeUsers) {
    console.error('Failed to fetch active users:', fetchErr?.message);
    return { totalUsers: 0, synced: 0, failed: 0, results: [] };
  }

  console.log(`[DailySync] Starting for ${activeUsers.length} active users`);

  const results: SyncResult[] = [];
  let synced = 0;
  let failed = 0;

  // 2. Process each user (sequential to avoid rate-limiting external APIs)
  for (const user of activeUsers) {
    const result: SyncResult = {
      userId: user.id,
      username: user.username || 'unknown',
      leetcode: 'skipped',
      codeforces: 'skipped',
      ranking: 'pending',
      badges: 'pending',
    };

    try {
      // 2a. Sync LeetCode stats
      if (user.leetcode_username) {
        try {
          const lcProfile = await lc.fetchUserProfile(user.leetcode_username);
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
          }, { onConflict: 'user_id,date' });
          result.leetcode = `synced (${lcProfile.totalSolved} solved)`;
        } catch (e) {
          result.leetcode = `failed: ${e instanceof Error ? e.message : 'unknown'}`;
        }
      }

      // 2b. Sync Codeforces stats
      if (user.codeforces_handle) {
        try {
          const cfInfo = await cf.fetchUserInfo(user.codeforces_handle);
          await supabase.from('codeforces_daily_stats').upsert({
            user_id: user.id,
            date: today,
            current_rating: cfInfo.currentRating,
            max_rating: cfInfo.maxRating,
            rank: cfInfo.rank,
            contest_count: cfInfo.contestCount,
          }, { onConflict: 'user_id,date' });
          result.codeforces = `synced (rating: ${cfInfo.currentRating})`;
        } catch (e) {
          result.codeforces = `failed: ${e instanceof Error ? e.message : 'unknown'}`;
        }
      }

      // Small delay between LeetCode/CF API calls to avoid rate limits
      await delay(200);

      // 2c. Recompute ranking
      try {
        await updateUserRanking(supabase, user.id);

        // Insert daily ranking snapshot
        const { data: ranking } = await supabase
          .from('user_rankings')
          .select('global_rank, ranking_score, total_verified_solves')
          .eq('user_id', user.id)
          .single();

        if (ranking) {
          await supabase.from('ranking_snapshots').upsert({
            user_id: user.id,
            date: today,
            global_rank: ranking.global_rank,
            ranking_score: ranking.ranking_score,
            solves_to_date: ranking.total_verified_solves,
          }, { onConflict: 'user_id,date' });
        }
        result.ranking = `rank #${ranking?.global_rank || '?'} (score: ${ranking?.ranking_score || 0})`;
      } catch (e) {
        result.ranking = `failed: ${e instanceof Error ? e.message : 'unknown'}`;
      }

      // 2d. Update badge progress
      try {
        await updateBadgeProgress(supabase, user.id);
        result.badges = 'updated';
      } catch (e) {
        result.badges = `failed: ${e instanceof Error ? e.message : 'unknown'}`;
      }

      // 2e. Update last_sync_at
      await supabase
        .from('profiles')
        .update({ last_sync_at: new Date().toISOString() })
        .eq('id', user.id);

      synced++;
    } catch (e) {
      console.error(`[DailySync] User ${user.id} failed:`, e);
      failed++;
    }

    results.push(result);

    // Rate limit: 500ms between users to be gentle on external APIs
    await delay(500);
  }

  console.log(`[DailySync] Complete: ${synced}/${activeUsers.length} synced, ${failed} failed`);

  return {
    totalUsers: activeUsers.length,
    synced,
    failed,
    results,
  };
}

function delay(ms: number): Promise<void> {
  return new Promise(resolve => setTimeout(resolve, ms));
}
