// ============================================================
// Problems Routes — GET /api/next-problem
// ============================================================
import { SupabaseClient } from '@supabase/supabase-js';
import { AuthUser } from '../types';
import { success, error, notFound } from '../utils/response';

export async function handleNextProblem(
  supabase: SupabaseClient,
  user: AuthUser
): Promise<Response> {
  // Get all solved problem IDs for this user
  const { data: solvedProblems } = await supabase
    .from('user_solves')
    .select('problem_id')
    .eq('user_id', user.id)
    .eq('verified', true);

  const solvedIds = new Set(solvedProblems?.map(s => s.problem_id) || []);
  const solvedCount = solvedIds.size;

  // Get next unsolved problem in track order
  const { data: problems } = await supabase
    .from('problems')
    .select('*')
    .order('track_position', { ascending: true });

  if (!problems || problems.length === 0) {
    return notFound('No problems available');
  }

  const nextProblem = problems.find(p => !solvedIds.has(p.id));

  if (!nextProblem) {
    return success({
      problem: null,
      progress: { solved_count: solvedCount, total_count: problems.length, current_position: problems.length + 1 },
      message: 'All problems completed! 🎉'
    });
  }

  // Ensure user_solves record exists for assignment tracking
  await supabase.from('user_solves').upsert({
    user_id: user.id,
    problem_id: nextProblem.id,
    status: 'available',
    assigned_at: new Date().toISOString(),
  }, { onConflict: 'user_id,problem_id', ignoreDuplicates: true });

  const url = nextProblem.platform === 'leetcode'
    ? `https://leetcode.com/problems/${nextProblem.slug}/`
    : `https://codeforces.com/problemset/problem/${nextProblem.codeforces_contest_id}/${nextProblem.codeforces_problem_index}`;

  return success({
    problem: { ...nextProblem, url },
    progress: {
      solved_count: solvedCount,
      total_count: problems.length,
      current_position: nextProblem.track_position,
    },
  });
}

export async function handleListProblems(
  supabase: SupabaseClient,
  user: AuthUser,
  query: URLSearchParams
): Promise<Response> {
  const topic = query.get('topic');
  const difficulty = query.get('difficulty');
  const platform = query.get('platform');

  let q = supabase.from('problems').select('*').order('track_position', { ascending: true });
  if (topic) q = q.eq('topic', topic);
  if (difficulty) q = q.eq('difficulty', difficulty);
  if (platform) q = q.eq('platform', platform);

  const { data: problems, error: err } = await q;
  if (err) return error('QUERY_ERROR', err.message, 500);

  // Get user's solved status
  const { data: solves } = await supabase
    .from('user_solves').select('problem_id, verified, status')
    .eq('user_id', user.id);

  const solveMap = new Map(solves?.map(s => [s.problem_id, s]) || []);

  const result = problems?.map(p => ({
    ...p,
    user_status: solveMap.get(p.id)?.status || 'locked',
    verified: solveMap.get(p.id)?.verified || false,
  }));

  return success({ problems: result, total: result?.length || 0 });
}
