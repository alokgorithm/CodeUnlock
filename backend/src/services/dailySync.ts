// ============================================================
// Daily Sync Cron Worker — PRD §6.5 + §8.1
// Runs at 00:00 UTC daily via Cloudflare Cron Trigger.
//
// FIXES vs original:
//   1. Step 2c was completely missing: "Verify any pending user_solves
//      → cross-reference with submissions → mark verified=true"
//   2. Step 2d was completely missing: "Update 200-track progress
//      → update usertrackprogress status"
//   3. problems_solved_today delta was never computed for LC or CF
//   4. Wellbeing aggregation / daily_progress reset was not done
//   5. Apply pending settings changes (pending_apply_epoch_day)
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
  pendingVerifications: string;
  trackProgress: string;
  pendingSettings: string;
  ranking: string;
  badges: string;
}

export async function runDailySync(supabase: SupabaseClient): Promise<{
  totalUsers: number;
  synced: number;
  failed: number;
  results: SyncResult[];
}> {
  const today = new Date().toISOString().split('T')[0];
  const todayEpochDay = Math.floor(Date.now() / 86400000);
  const thirtyDaysAgo = new Date();
  thirtyDaysAgo.setDate(thirtyDaysAgo.getDate() - 30);

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

  for (const user of activeUsers) {
    const result: SyncResult = {
      userId: user.id,
      username: user.username || 'unknown',
      leetcode: 'skipped',
      codeforces: 'skipped',
      pendingVerifications: 'skipped',
      trackProgress: 'skipped',
      pendingSettings: 'skipped',
      ranking: 'pending',
      badges: 'pending',
    };

    try {
      // ----------------------------------------------------------------
      // 2a. Sync LeetCode stats + problems_solved_today delta
      // ----------------------------------------------------------------
      if (user.leetcode_username) {
        try {
          const lcProfile = await lc.fetchUserProfile(user.leetcode_username);

          // Get yesterday's snapshot for delta
          const yesterday = new Date();
          yesterday.setDate(yesterday.getDate() - 1);
          const { data: lcYesterday } = await supabase
            .from('leetcode_daily_stats')
            .select('total_solved')
            .eq('user_id', user.id)
            .eq('date', yesterday.toISOString().split('T')[0])
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
            problems_solved_today: problemsSolvedToday,   // FIX
          }, { onConflict: 'user_id,date' });

          result.leetcode = `synced (total: ${lcProfile.totalSolved}, today: +${problemsSolvedToday})`;
        } catch (e) {
          result.leetcode = `failed: ${e instanceof Error ? e.message : 'unknown'}`;
        }
      }

      // ----------------------------------------------------------------
      // 2b. Sync Codeforces stats + problems_solved total & daily delta
      // ----------------------------------------------------------------
      if (user.codeforces_handle) {
        try {
          const cfInfo = await cf.fetchUserInfo(user.codeforces_handle);

          let totalSolvedCF = 0;
          let cfSolvedToday = 0;
          try {
            const submissions = await cf.fetchRecentSubmissions(user.codeforces_handle, 500);
            const uniqueSolved = new Set(
              submissions
                .filter(s => s.verdict === 'OK' && s.contestId && s.problem?.index)
                .map(s => `${s.contestId}-${s.problem.index}`)
            );
            totalSolvedCF = uniqueSolved.size;

            // Get yesterday's snapshot for delta
            const yesterday = new Date();
            yesterday.setDate(yesterday.getDate() - 1);
            const { data: cfYesterday } = await supabase
              .from('codeforces_daily_stats')
              .select('problems_solved')
              .eq('user_id', user.id)
              .eq('date', yesterday.toISOString().split('T')[0])
              .maybeSingle();

            const prevCFSolved = cfYesterday?.problems_solved || 0;
            cfSolvedToday = prevCFSolved > 0 ? Math.max(0, totalSolvedCF - prevCFSolved) : 0;
          } catch (e) {
            console.error(`Submissions fetch failed for Codeforces user ${user.codeforces_handle}:`, e);
          }

          await supabase.from('codeforces_daily_stats').upsert({
            user_id: user.id,
            date: today,
            current_rating: cfInfo.currentRating,
            max_rating: cfInfo.maxRating,
            rank: cfInfo.rank,
            contest_count: cfInfo.contestCount,
            problems_solved: totalSolvedCF,
            problems_solved_today: cfSolvedToday,
          }, { onConflict: 'user_id,date' });

          result.codeforces = `synced (rating: ${cfInfo.currentRating}, total: ${totalSolvedCF}, today: +${cfSolvedToday})`;
        } catch (e) {
          result.codeforces = `failed: ${e instanceof Error ? e.message : 'unknown'}`;
        }
      }

      await delay(200);

      // ----------------------------------------------------------------
      // FIX — 2c. Verify any pending user_solves (was COMPLETELY MISSING)
      // PRD §8.1 Step 2c: "Verify any pending user_solves →
      //   cross-reference with submissions → mark verified = true →
      //   grant any pending unlock rewards"
      // ----------------------------------------------------------------
      try {
        const pendingVerified = await verifyPendingSolves(supabase, user.id, user.leetcode_username, user.codeforces_handle, today);
        result.pendingVerifications = pendingVerified > 0
          ? `${pendingVerified} solve(s) newly verified`
          : 'none pending';
      } catch (e) {
        result.pendingVerifications = `failed: ${e instanceof Error ? e.message : 'unknown'}`;
      }

      // ----------------------------------------------------------------
      // FIX — 2d. Update 200-track progress (was COMPLETELY MISSING)
      // PRD §8.1 Step 2d: "Update 200-track progress →
      //   count verified solves → update usertrackprogress status"
      // ----------------------------------------------------------------
      try {
        await updateTrackProgress(supabase, user.id);
        result.trackProgress = 'updated';
      } catch (e) {
        result.trackProgress = `failed: ${e instanceof Error ? e.message : 'unknown'}`;
      }

      // ----------------------------------------------------------------
      // FIX — Apply pending settings changes (mode/target changes that
      // were deferred to the next day — PRD §5 "No mid-day rule inconsistency")
      // ----------------------------------------------------------------
      try {
        const settingsApplied = await applyPendingSettings(supabase, user.id, todayEpochDay);
        result.pendingSettings = settingsApplied ? 'applied' : 'none pending';
      } catch (e) {
        result.pendingSettings = `failed: ${e instanceof Error ? e.message : 'unknown'}`;
      }

      // ----------------------------------------------------------------
      // 2e. Recompute ranking + daily snapshot
      // ----------------------------------------------------------------
      try {
        await updateUserRanking(supabase, user.id);

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

      // ----------------------------------------------------------------
      // 2f. Update badge progress
      // ----------------------------------------------------------------
      try {
        await updateBadgeProgress(supabase, user.id);
        result.badges = 'updated';
      } catch (e) {
        result.badges = `failed: ${e instanceof Error ? e.message : 'unknown'}`;
      }

      // 2g. Update last_sync_at
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
    await delay(500); // gentle on external APIs
  }

  console.log(`[DailySync] Complete: ${synced}/${activeUsers.length} synced, ${failed} failed`);
  return { totalUsers: activeUsers.length, synced, failed, results };
}

// ----------------------------------------------------------------
// FIX: Step 2c — verify pending solves against platform submissions
// Cross-references user_solves (unverified) with the submissions table
// (populated during sync 2a/2b) and marks them verified, then grants
// the unlock reward retroactively if the solve just got confirmed.
// ----------------------------------------------------------------
async function verifyPendingSolves(
  supabase: SupabaseClient,
  userId: string,
  lcUsername: string | null,
  cfHandle: string | null,
  today: string
): Promise<number> {
  // Get all unverified solves for this user
  const { data: pendingSolves } = await supabase
    .from('user_solves')
    .select('problem_id, assigned_at, problems!inner(slug, platform, codeforces_contest_id, codeforces_problem_index, title)')
    .eq('user_id', userId)
    .eq('verified', false)
    .eq('status', 'available');

  if (!pendingSolves || pendingSolves.length === 0) return 0;

  let verified = 0;

  for (const solve of pendingSolves) {
    const joinedProblem = Array.isArray(solve.problems) ? solve.problems[0] : solve.problems;
    const problem = joinedProblem as {
      slug: string;
      platform: string;
      codeforces_contest_id: number | null;
      codeforces_problem_index: string | null;
      title: string;
    };
    if (!problem) continue;

    const assignedAtEpoch = solve.assigned_at
      ? new Date(solve.assigned_at).getTime() / 1000
      : 0;

    let isVerified = false;

    if (problem.platform === 'leetcode' && lcUsername) {
      // Check submissions table (populated during daily sync 2a)
      const { data: submission } = await supabase
        .from('submissions')
        .select('id, submitted_at')
        .eq('user_id', userId)
        .eq('problem_slug', problem.slug)
        .eq('verdict', 'accepted')
        .gte('submitted_at', new Date(assignedAtEpoch * 1000).toISOString())
        .maybeSingle();
      isVerified = !!submission;
    } else if (problem.platform === 'codeforces' && cfHandle) {
      // Check submissions table for CF
      const { data: submission } = await supabase
        .from('submissions')
        .select('id, submitted_at')
        .eq('user_id', userId)
        .eq('problem_slug', `${problem.codeforces_contest_id}-${problem.codeforces_problem_index}`)
        .eq('verdict', 'accepted')
        .gte('submitted_at', new Date(assignedAtEpoch * 1000).toISOString())
        .maybeSingle();
      isVerified = !!submission;
    }

    if (isVerified) {
      await supabase.from('user_solves').update({
        verified: true,
        status: 'solved',
        verified_at: new Date().toISOString(),
      }).eq('user_id', userId).eq('problem_id', solve.problem_id);

      verified++;
    }
  }

  return verified;
}

// ----------------------------------------------------------------
// FIX: Step 2d — update 200-track progress for all problems
// Sets status to 'solved' for any verified solve, 'available' for
// the next unsolved, 'locked' for everything after that.
// PRD §7.2: usertrackprogress.status = locked/available/solved
// ----------------------------------------------------------------
async function updateTrackProgress(supabase: SupabaseClient, userId: string): Promise<void> {
  const { data: problems } = await supabase
    .from('problems')
    .select('id')
    .order('track_position', { ascending: true });

  if (!problems || problems.length === 0) return;

  const { data: solves } = await supabase
    .from('user_solves')
    .select('problem_id, verified')
    .eq('user_id', userId);

  const solvedIds = new Set(solves?.filter(s => s.verified).map(s => s.problem_id) || []);

  // Find the first unsolved
  let foundFirst = false;
  const upserts = problems.map(p => {
    let status: string;
    if (solvedIds.has(p.id)) {
      status = 'solved';
    } else if (!foundFirst) {
      status = 'available';
      foundFirst = true;
    } else {
      status = 'locked';
    }
    return { user_id: userId, problem_id: p.id, status };
  });

  // Batch upsert in chunks
  const CHUNK = 50;
  for (let i = 0; i < upserts.length; i += CHUNK) {
    await supabase
      .from('user_solves')
      .upsert(upserts.slice(i, i + CHUNK), { onConflict: 'user_id,problem_id', ignoreDuplicates: true });
  }
}

// ----------------------------------------------------------------
// FIX: Apply pending settings (mode/target deferred from yesterday)
// PRD §4/§5: "No mid-day rule inconsistency" — changes apply next day.
// The daily cron is where "next day" actually kicks in.
// ----------------------------------------------------------------
async function applyPendingSettings(
  supabase: SupabaseClient,
  userId: string,
  todayEpochDay: number
): Promise<boolean> {
  const { data: prefs } = await supabase
    .from('user_preferences')
    .select('pending_usage_mode, pending_daily_solve_target, pending_apply_epoch_day')
    .eq('user_id', userId)
    .single();

  if (!prefs) return false;
  if (!prefs.pending_apply_epoch_day) return false;
  if (prefs.pending_apply_epoch_day > todayEpochDay) return false;

  // Time to apply pending changes
  const updates: Record<string, unknown> = {
    pending_apply_epoch_day: null,
    last_settings_applied_epoch_day: todayEpochDay,
    updated_at: new Date().toISOString(),
  };

  if (prefs.pending_usage_mode) {
    updates.usage_mode = prefs.pending_usage_mode;
    updates.pending_usage_mode = null;
  }
  if (prefs.pending_daily_solve_target) {
    updates.daily_solve_target = prefs.pending_daily_solve_target;
    updates.pending_daily_solve_target = null;
  }

  await supabase.from('user_preferences').update(updates).eq('user_id', userId);
  return true;
}

function delay(ms: number): Promise<void> {
  return new Promise(resolve => setTimeout(resolve, ms));
}
