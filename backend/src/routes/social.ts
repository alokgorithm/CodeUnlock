// ============================================================
// Social Routes — /api/classrooms/*, /api/friends/*, /api/search
// ============================================================
import { SupabaseClient } from '@supabase/supabase-js';
import { AuthUser } from '../types';
import { success, error, notFound } from '../utils/response';

// ---- Classrooms ----
export async function handleCreateClassroom(
  supabase: SupabaseClient, user: AuthUser, body: { name: string }
): Promise<Response> {
  if (!body.name) return error('INVALID_REQUEST', 'Classroom name is required');
  const code = generateJoinCode();
  const { data, error: err } = await supabase
    .from('classrooms').insert({ name: body.name, code, creator_id: user.id })
    .select().single();
  if (err) return error('CREATE_FAILED', err.message, 500);

  // Auto-join creator
  await supabase.from('classroom_members').insert({ classroom_id: data.id, user_id: user.id });
  return success(data, 201);
}

export async function handleJoinClassroom(
  supabase: SupabaseClient, user: AuthUser, body: { code: string }
): Promise<Response> {
  if (!body.code) return error('INVALID_REQUEST', 'Join code is required');
  const { data: classroom } = await supabase
    .from('classrooms').select('id, name').eq('code', body.code.toUpperCase()).single();
  if (!classroom) return notFound('Classroom not found');

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
    .select(`requester_id, addressee_id, status, created_at`)
    .or(`requester_id.eq.${user.id},addressee_id.eq.${user.id}`)
    .eq('status', 'accepted');

  const friendIds = data?.map(f =>
    f.requester_id === user.id ? f.addressee_id : f.requester_id
  ) || [];

  if (friendIds.length === 0) return success({ friends: [] });

  const { data: profiles } = await supabase
    .from('profiles').select('id, username, avatar_emoji')
    .in('id', friendIds);

  return success({ friends: profiles || [] });
}

export async function handleFriendRequest(
  supabase: SupabaseClient, user: AuthUser, body: { username: string }
): Promise<Response> {
  if (!body.username) return error('INVALID_REQUEST', 'Username is required');

  const { data: target } = await supabase
    .from('profiles').select('id').eq('username', body.username).single();
  if (!target) return notFound('User not found');
  if (target.id === user.id) return error('INVALID_REQUEST', 'Cannot friend yourself');

  const { error: err } = await supabase.from('friends').insert({
    requester_id: user.id, addressee_id: target.id, status: 'pending',
  });
  if (err?.code === '23505') return error('ALREADY_EXISTS', 'Friend request already exists');
  if (err) return error('REQUEST_FAILED', err.message, 500);

  return success({ message: 'Friend request sent' }, 201);
}

export async function handleAcceptFriend(
  supabase: SupabaseClient, user: AuthUser, body: { requester_id: string }
): Promise<Response> {
  const { error: err } = await supabase
    .from('friends')
    .update({ status: 'accepted' })
    .eq('requester_id', body.requester_id)
    .eq('addressee_id', user.id)
    .eq('status', 'pending');
  if (err) return error('ACCEPT_FAILED', err.message, 500);
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

  return success({ results: data || [] });
}

function generateJoinCode(): string {
  const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
  let code = '';
  for (let i = 0; i < 6; i++) code += chars[Math.floor(Math.random() * chars.length)];
  return code;
}
