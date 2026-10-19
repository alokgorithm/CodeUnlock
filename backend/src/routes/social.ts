// ============================================================
// Social Routes — /api/classrooms/*, /api/friends/*, /api/search
// FIXES:
//   1. Added GET /api/classrooms — list user's own classrooms (was missing)
//   2. Added GET /api/friends/pending — list incoming friend requests (was missing)
//   3. Fixed classroom join: validate code is uppercased consistently
// ============================================================
import { SupabaseClient } from '@supabase/supabase-js';
import { AuthUser } from '../types';
import { success, error, notFound } from '../utils/response';

// ---- Classrooms ----

// NEW: GET /api/classrooms — list classrooms the user belongs to
export async function handleListClassrooms(
  supabase: SupabaseClient,
  user: AuthUser
): Promise<Response> {
  const { data: memberships } = await supabase
    .from('classroom_members')
    .select('classroom_id, joined_at, classrooms!inner(id, name, code, creator_id)')
    .eq('user_id', user.id);

  if (!memberships || memberships.length === 0) {
    return success({ classrooms: [] });
  }

  const classroomIds = memberships.map((m: Record<string, unknown>) => {
    const cls = m.classrooms as { id: string };
    return cls?.id;
  }).filter(Boolean);

  // Get member counts for each classroom
  const { data: memberCounts } = await supabase
    .from('classroom_members')
    .select('classroom_id')
    .in('classroom_id', classroomIds);

  const countMap: Record<string, number> = {};
  memberCounts?.forEach((m) => {
    countMap[m.classroom_id] = (countMap[m.classroom_id] || 0) + 1;
  });

  return success({
    classrooms: memberships.map((m: Record<string, unknown>) => {
      const cls = m.classrooms as { id: string; name: string; code: string; creator_id: string };
      return {
        id: cls?.id,
        name: cls?.name,
        code: cls?.code,
        is_creator: cls?.creator_id === user.id,
        member_count: countMap[cls?.id] || 1,
        joined_at: m.joined_at,
      };
    }),
  });
}

export async function handleCreateClassroom(
  supabase: SupabaseClient, user: AuthUser, body: { name: string }
): Promise<Response> {
  if (!body.name?.trim()) return error('INVALID_REQUEST', 'Classroom name is required');

  const code = generateJoinCode();
  const { data, error: err } = await supabase
    .from('classrooms').insert({ name: body.name.trim(), code, creator_id: user.id })
    .select().single();
  if (err) return error('CREATE_FAILED', err.message, 500);

  // Auto-join creator
  await supabase.from('classroom_members').insert({ classroom_id: data.id, user_id: user.id });
  return success({ ...data, member_count: 1 }, 201);
}

export async function handleJoinClassroom(
  supabase: SupabaseClient, user: AuthUser, body: { code: string }
): Promise<Response> {
  if (!body.code) return error('INVALID_REQUEST', 'Join code is required');

  const { data: classroom } = await supabase
    .from('classrooms')
    .select('id, name')
    .eq('code', body.code.toUpperCase().trim())
    .single();

  if (!classroom) return notFound('Classroom not found. Check the code and try again.');

  const { error: err } = await supabase
    .from('classroom_members').insert({ classroom_id: classroom.id, user_id: user.id });

  if (err?.code === '23505') return error('ALREADY_MEMBER', 'You are already in this classroom');
  if (err) return error('JOIN_FAILED', err.message, 500);

  return success({ classroom_id: classroom.id, name: classroom.name });
}

// ---- Friends ----

export async function handleListFriends(supabase: SupabaseClient, user: AuthUser): Promise<Response> {
  const { data } = await supabase
    .from('friends')
    .select('requester_id, addressee_id, status, created_at')
    .or(`requester_id.eq.${user.id},addressee_id.eq.${user.id}`)
    .eq('status', 'accepted');

  const friendIds = data?.map(f =>
    f.requester_id === user.id ? f.addressee_id : f.requester_id
  ) || [];

  if (friendIds.length === 0) return success({ friends: [] });

  const { data: profiles } = await supabase
    .from('profiles').select('id, username, avatar_emoji, leetcode_username, codeforces_handle')
    .in('id', friendIds);

  // Attach ranking for friend comparison
  const { data: rankings } = await supabase
    .from('user_rankings')
    .select('user_id, global_rank, total_verified_solves, consistency_streak_days')
    .in('user_id', friendIds);

  const rankMap = new Map(rankings?.map(r => [r.user_id, r]) || []);

  return success({
    friends: profiles?.map(p => ({
      ...p,
      ranking: rankMap.get(p.id) || null,
    })) || [],
  });
}

// NEW: GET /api/friends/pending — incoming + outgoing requests
export async function handleListPendingFriends(
  supabase: SupabaseClient,
  user: AuthUser
): Promise<Response> {
  const { data } = await supabase
    .from('friends')
    .select('requester_id, addressee_id, created_at')
    .or(`requester_id.eq.${user.id},addressee_id.eq.${user.id}`)
    .eq('status', 'pending');

  if (!data || data.length === 0) {
    return success({ incoming: [], outgoing: [] });
  }

  const incomingIds = data
    .filter(f => f.addressee_id === user.id)
    .map(f => f.requester_id);

  const outgoingIds = data
    .filter(f => f.requester_id === user.id)
    .map(f => f.addressee_id);

  const allIds = [...incomingIds, ...outgoingIds];
  const { data: profiles } = await supabase
    .from('profiles').select('id, username, avatar_emoji')
    .in('id', allIds);

  const profileMap = new Map(profiles?.map(p => [p.id, p]) || []);

  return success({
    incoming: incomingIds.map(id => ({
      requester_id: id,
      ...profileMap.get(id),
    })),
    outgoing: outgoingIds.map(id => ({
      addressee_id: id,
      ...profileMap.get(id),
    })),
  });
}

export async function handleFriendRequest(
  supabase: SupabaseClient, user: AuthUser, body: { username: string }
): Promise<Response> {
  if (!body.username) return error('INVALID_REQUEST', 'Username is required');

  const { data: target } = await supabase
    .from('profiles').select('id').eq('username', body.username).single();
  if (!target) return notFound('User not found');
  if (target.id === user.id) return error('INVALID_REQUEST', 'Cannot send friend request to yourself');

  // Check for existing relationship in either direction
  const { data: existing } = await supabase
    .from('friends')
    .select('status')
    .or(
      `and(requester_id.eq.${user.id},addressee_id.eq.${target.id}),` +
      `and(requester_id.eq.${target.id},addressee_id.eq.${user.id})`
    )
    .maybeSingle();

  if (existing) {
    if (existing.status === 'accepted') return error('ALREADY_FRIENDS', 'You are already friends');
    return error('REQUEST_ALREADY_EXISTS', 'A friend request already exists between you two');
  }

  const { error: err } = await supabase.from('friends').insert({
    requester_id: user.id, addressee_id: target.id, status: 'pending',
  });
  if (err) return error('REQUEST_FAILED', err.message, 500);

  return success({ message: 'Friend request sent' }, 201);
}

export async function handleAcceptFriend(
  supabase: SupabaseClient, user: AuthUser, body: { requester_id: string }
): Promise<Response> {
  if (!body.requester_id) return error('INVALID_REQUEST', 'requester_id is required');

  const { data, error: err } = await supabase
    .from('friends')
    .update({ status: 'accepted' })
    .eq('requester_id', body.requester_id)
    .eq('addressee_id', user.id)
    .eq('status', 'pending')
    .select();

  if (err) return error('ACCEPT_FAILED', err.message, 500);
  if (!data || data.length === 0) return notFound('Friend request not found');

  return success({ message: 'Friend request accepted' });
}

// ---- Search ----

export async function handleSearch(
  supabase: SupabaseClient, _user: AuthUser, query: URLSearchParams
): Promise<Response> {
  const q = query.get('q');
  if (!q || q.length < 2) return error('INVALID_REQUEST', 'Query must be at least 2 characters');

  const { data } = await supabase
    .from('profiles')
    .select('id, username, avatar_emoji, leetcode_username, codeforces_handle')
    .or(`username.ilike.%${q}%,leetcode_username.ilike.%${q}%,codeforces_handle.ilike.%${q}%`)
    .limit(20);

  return success({ results: data || [], count: data?.length || 0 });
}

// ---- Helpers ----

function generateJoinCode(): string {
  const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
  let code = '';
  for (let i = 0; i < 6; i++) code += chars[Math.floor(Math.random() * chars.length)];
  return code;
}
