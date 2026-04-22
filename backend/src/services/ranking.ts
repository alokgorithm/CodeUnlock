// ============================================================
// Ranking Computation Service
// ============================================================
import { SupabaseClient } from '@supabase/supabase-js';

/**
 * Compute ranking score for a user using PRD formula (Section 9.1).
 */
export async function computeUserRankingScore(
  supabase: SupabaseClient,
  userId: string
): Promise<number> {
  const { count: totalSolves } = await supabase
    .from('user_solves').select('*', { count: 'exact', head: true })
    .eq('user_id', userId).eq('verified', true);

  const { data: lcStats } = await supabase
    .from('leetcode_daily_stats').select('global_ranking')
    .eq('user_id', userId).order('date', { ascending: false }).limit(1).maybeSingle();

  const { data: cfStats } = await supabase
    .from('codeforces_daily_stats').select('current_rating')
    .eq('user_id', userId).order('date', { ascending: false }).limit(1).maybeSingle();

  const streak = await calculateStreak(supabase, userId);

  const sevenDaysAgo = new Date();
  sevenDaysAgo.setDate(sevenDaysAgo.getDate() - 7);
  const { data: wb } = await supabase
    .from('wellbeing_daily').select('blocked_attempt_count')
    .eq('user_id', userId).gte('date', sevenDaysAgo.toISOString().split('T')[0]);
  const blocked = wb?.reduce((s, w) => s + (w.blocked_attempt_count || 0), 0) || 0;

  const lcRank = lcStats?.global_ranking || 999999;
  const score = (totalSolves || 0) * 10
    + streak * 5
    + (cfStats?.current_rating || 0) / 10
    + (lcRank > 0 ? 1000000 / lcRank : 0)
    - blocked * 0.5;

  return Math.round(score * 100) / 100;
}

async function calculateStreak(supabase: SupabaseClient, userId: string): Promise<number> {
  const { data } = await supabase
    .from('daily_progress').select('date, problems_solved')
    .eq('user_id', userId).order('date', { ascending: false }).limit(100);
  if (!data) return 0;

  let streak = 0;
  const today = new Date();
  for (let i = 0; i < data.length; i++) {
    const exp = new Date(today);
    exp.setDate(exp.getDate() - i);
    if (data[i]?.date === exp.toISOString().split('T')[0] && data[i].problems_solved > 0) {
      streak++;
    } else break;
  }
  return streak;
}

export async function updateUserRanking(supabase: SupabaseClient, userId: string): Promise<void> {
  const score = await computeUserRankingScore(supabase, userId);
  const streak = await calculateStreak(supabase, userId);
  const { count: solves } = await supabase
    .from('user_solves').select('*', { count: 'exact', head: true })
    .eq('user_id', userId).eq('verified', true);

  await supabase.from('user_rankings').upsert({
    user_id: userId, ranking_score: score,
    total_verified_solves: solves || 0, consistency_streak_days: streak,
    last_computed_at: new Date().toISOString(),
  }, { onConflict: 'user_id' });

  // Recompute global ranks
  const { data: all } = await supabase
    .from('user_rankings').select('user_id, ranking_score')
    .order('ranking_score', { ascending: false });
  if (all) {
    for (let i = 0; i < all.length; i++) {
      await supabase.from('user_rankings')
        .update({ global_rank: i + 1 }).eq('user_id', all[i].user_id);
    }
  }
}
