// ============================================================
// Stats Routes — /api/stats/*
// ============================================================
import { SupabaseClient } from '@supabase/supabase-js';
import { AuthUser } from '../types';
import { success, notFound } from '../utils/response';

export async function handleLeetCodeStats(supabase: SupabaseClient, user: AuthUser): Promise<Response> {
  const { data } = await supabase
    .from('leetcode_daily_stats').select('*')
    .eq('user_id', user.id).order('date', { ascending: false }).limit(1).maybeSingle();

  if (!data) return success({ message: 'No LeetCode stats synced yet', data: null });

  return success({
    total_solved: data.total_solved, easy_solved: data.easy_solved,
    medium_solved: data.medium_solved, hard_solved: data.hard_solved,
    contest_rating: data.contest_rating, global_ranking: data.global_ranking,
    problems_solved_today: data.problems_solved_today, as_of: data.date,
  });
}

export async function handleCodeforcesStats(supabase: SupabaseClient, user: AuthUser): Promise<Response> {
  const { data } = await supabase
    .from('codeforces_daily_stats').select('*')
    .eq('user_id', user.id).order('date', { ascending: false }).limit(1).maybeSingle();

  if (!data) return success({ message: 'No Codeforces stats synced yet', data: null });

  return success({
    current_rating: data.current_rating, max_rating: data.max_rating,
    rank: data.rank, problems_solved: data.problems_solved,
    problems_solved_today: data.problems_solved_today,
    contest_count: data.contest_count, as_of: data.date,
  });
}

export async function handleTrackProgress(supabase: SupabaseClient, user: AuthUser): Promise<Response> {
  const { count: totalProblems } = await supabase.from('problems').select('*', { count: 'exact', head: true });
  const { data: solves } = await supabase
    .from('user_solves').select('problem_id, problems!inner(difficulty, topic)')
    .eq('user_id', user.id).eq('verified', true);

  const solved = solves?.length || 0;
  const diffBreakdown: Record<string, number> = {};
  const topicBreakdown: Record<string, number> = {};

  solves?.forEach((s: Record<string, unknown>) => {
    const prob = s.problems as Record<string, string> | Record<string, string>[];
    const p = Array.isArray(prob) ? prob[0] : prob;
    if (!p) return;
    const d = p.difficulty;
    const t = p.topic;
    if (d) diffBreakdown[d] = (diffBreakdown[d] || 0) + 1;
    if (t) topicBreakdown[t] = (topicBreakdown[t] || 0) + 1;
  });

  // Get streak
  const { data: dp } = await supabase
    .from('daily_progress').select('date, problems_solved')
    .eq('user_id', user.id).order('date', { ascending: false }).limit(100);
  let streak = 0;
  if (dp) {
    const today = new Date();
    for (let i = 0; i < dp.length; i++) {
      const exp = new Date(today);
      exp.setDate(exp.getDate() - i);
      if (dp[i]?.date === exp.toISOString().split('T')[0] && dp[i].problems_solved > 0) streak++;
      else break;
    }
  }

  // Next problem
  const solvedIds = new Set(solves?.map(s => s.problem_id) || []);
  const { data: problems } = await supabase
    .from('problems').select('id, title, slug')
    .order('track_position', { ascending: true });
  const next = problems?.find(p => !solvedIds.has(p.id)) || null;

  return success({
    total_problems: totalProblems || 200, solved, current_streak: streak,
    difficulty_breakdown: diffBreakdown, topic_breakdown: topicBreakdown,
    next_problem: next,
  });
}

export async function handleWellbeingStats(supabase: SupabaseClient, user: AuthUser): Promise<Response> {
  const today = new Date().toISOString().split('T')[0];
  const { data: todayData } = await supabase
    .from('wellbeing_daily').select('*').eq('user_id', user.id).eq('date', today).maybeSingle();

  const sevenDaysAgo = new Date();
  sevenDaysAgo.setDate(sevenDaysAgo.getDate() - 7);
  const { data: weeklyData } = await supabase
    .from('wellbeing_daily').select('date, wellbeing_score, total_screen_time_minutes')
    .eq('user_id', user.id).gte('date', sevenDaysAgo.toISOString().split('T')[0])
    .order('date', { ascending: true });

  return success({
    today: todayData ? {
      total_screen_time_minutes: todayData.total_screen_time_minutes,
      restricted_app_time_minutes: todayData.restricted_app_time_minutes,
      pickup_count: todayData.pickup_count,
      wellbeing_score: todayData.wellbeing_score,
    } : null,
    weekly_trend: weeklyData?.map(d => ({
      date: d.date, score: d.wellbeing_score, screen_time: d.total_screen_time_minutes,
    })) || [],
  });
}
