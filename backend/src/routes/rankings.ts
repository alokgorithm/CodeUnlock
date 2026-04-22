// ============================================================
// Rankings Routes — /api/rankings/*
// ============================================================
import { SupabaseClient } from '@supabase/supabase-js';
import { AuthUser } from '../types';
import { success, error } from '../utils/response';

export async function handleGlobalRankings(
  supabase: SupabaseClient,
  user: AuthUser,
  query: URLSearchParams
): Promise<Response> {
  const limit = Math.min(parseInt(query.get('limit') || '50'), 100);
  const offset = parseInt(query.get('offset') || '0');

  const { data: rankings, count } = await supabase
    .from('user_rankings')
    .select(`
      user_id, global_rank, global_rank_last_week, ranking_score,
      total_verified_solves, consistency_streak_days,
      profiles!inner(username, avatar_emoji)
    `, { count: 'exact' })
    .order('ranking_score', { ascending: false })
    .range(offset, offset + limit - 1);

  // Get user's own rank
  const { data: myRank } = await supabase
    .from('user_rankings')
    .select('global_rank, ranking_score')
    .eq('user_id', user.id)
    .maybeSingle();

  return success({
    rankings: rankings?.map((r: Record<string, unknown>) => {
      const profile = r.profiles as { username: string; avatar_emoji: string };
      return {
        rank: r.global_rank,
        user_id: r.user_id,
        username: profile?.username || 'unknown',
        avatar_emoji: profile?.avatar_emoji || '🎯',
        ranking_score: r.ranking_score,
        total_solves: r.total_verified_solves,
        streak_days: r.consistency_streak_days,
        weekly_change: ((r.global_rank_last_week as number) || 0) - ((r.global_rank as number) || 0),
      };
    }) || [],
    my_rank: myRank ? { rank: myRank.global_rank, ranking_score: myRank.ranking_score } : null,
    total_users: count || 0,
  });
}

export async function handleCollegeRankings(
  supabase: SupabaseClient,
  user: AuthUser,
  query: URLSearchParams
): Promise<Response> {
  const { data: profile } = await supabase
    .from('profiles').select('college_name').eq('id', user.id).single();

  if (!profile?.college_name) {
    return error('NO_COLLEGE', 'Set your college in profile to see college rankings', 400);
  }

  const { data: collegeUsers } = await supabase
    .from('profiles').select('id').eq('college_name', profile.college_name);
  const userIds = collegeUsers?.map(u => u.id) || [];

  const { data: rankings } = await supabase
    .from('user_rankings')
    .select(`user_id, ranking_score, total_verified_solves, consistency_streak_days,
      profiles!inner(username, avatar_emoji)`)
    .in('user_id', userIds)
    .order('ranking_score', { ascending: false })
    .limit(50);

  return success({
    college: profile.college_name,
    rankings: rankings?.map((r: Record<string, unknown>, i: number) => {
      const p = r.profiles as { username: string; avatar_emoji: string };
      return {
        rank: i + 1, user_id: r.user_id, username: p?.username || 'unknown',
        avatar_emoji: p?.avatar_emoji || '🎯', ranking_score: r.ranking_score,
        total_solves: r.total_verified_solves, streak_days: r.consistency_streak_days,
      };
    }) || [],
  });
}

export async function handleClassroomRankings(
  supabase: SupabaseClient,
  user: AuthUser,
  classroomId: string
): Promise<Response> {
  // Verify user is a member
  const { data: membership } = await supabase
    .from('classroom_members')
    .select('user_id')
    .eq('classroom_id', classroomId).eq('user_id', user.id).maybeSingle();

  if (!membership) return error('NOT_A_MEMBER', 'You are not a member of this classroom', 403);

  const { data: members } = await supabase
    .from('classroom_members').select('user_id').eq('classroom_id', classroomId);
  const memberIds = members?.map(m => m.user_id) || [];

  const { data: rankings } = await supabase
    .from('user_rankings')
    .select(`user_id, ranking_score, total_verified_solves, consistency_streak_days,
      profiles!inner(username, avatar_emoji)`)
    .in('user_id', memberIds)
    .order('ranking_score', { ascending: false });

  const { data: classroom } = await supabase
    .from('classrooms').select('name').eq('id', classroomId).single();

  return success({
    classroom_name: classroom?.name || 'Unknown',
    rankings: rankings?.map((r: Record<string, unknown>, i: number) => {
      const p = r.profiles as { username: string; avatar_emoji: string };
      return {
        rank: i + 1, user_id: r.user_id, username: p?.username || 'unknown',
        avatar_emoji: p?.avatar_emoji || '🎯', ranking_score: r.ranking_score,
        total_solves: r.total_verified_solves, streak_days: r.consistency_streak_days,
      };
    }) || [],
  });
}
