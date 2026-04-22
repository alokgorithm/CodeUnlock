// ============================================================
// Unlock Routes — /api/unlock/*
// ============================================================
import { SupabaseClient } from '@supabase/supabase-js';
import { AuthUser } from '../types';
import { success, error } from '../utils/response';

export async function handleGetActiveUnlocks(
  supabase: SupabaseClient, user: AuthUser
): Promise<Response> {
  const now = new Date().toISOString();
  const { data } = await supabase
    .from('unlock_sessions')
    .select('*')
    .eq('user_id', user.id)
    .eq('is_active', true)
    .gte('expires_at', now)
    .order('expires_at', { ascending: false });

  return success({ active_unlocks: data || [] });
}

export async function handleEmergencyUnlock(
  supabase: SupabaseClient, user: AuthUser, body: { app_package?: string }
): Promise<Response> {
  const today = new Date().toISOString().split('T')[0];

  // Check if emergency already used today
  const { data: dp } = await supabase
    .from('daily_progress')
    .select('emergency_unlock_used')
    .eq('user_id', user.id).eq('date', today).maybeSingle();

  if (dp?.emergency_unlock_used) {
    return error('EMERGENCY_ALREADY_USED', 'Emergency unlock already used today', 429);
  }

  const now = new Date();
  const expiresAt = new Date(now.getTime() + 30 * 60 * 1000);

  const { data: session } = await supabase.from('unlock_sessions').insert({
    user_id: user.id,
    app_package: body.app_package || '__global__',
    unlock_type: 'emergency',
    duration_minutes: 30,
    expires_at: expiresAt.toISOString(),
    is_active: true,
  }).select('id, expires_at, duration_minutes').single();

  // Mark emergency used
  await supabase.from('daily_progress').upsert({
    user_id: user.id, date: today, emergency_unlock_used: true,
    updated_at: now.toISOString(),
  }, { onConflict: 'user_id,date' });

  return success({
    unlock_session: session,
    message: 'Emergency unlock granted for 30 minutes',
  });
}

export async function handleFreeUnlock(
  supabase: SupabaseClient, user: AuthUser, body: { app_package: string }
): Promise<Response> {
  if (!body.app_package) return error('INVALID_REQUEST', 'app_package is required');

  const today = new Date().toISOString().split('T')[0];
  const { data: dp } = await supabase
    .from('daily_progress')
    .select('free_unlock_used')
    .eq('user_id', user.id).eq('date', today).maybeSingle();

  if (dp?.free_unlock_used) {
    return error('FREE_UNLOCK_ALREADY_USED', 'Free unlock already used today', 429);
  }

  const now = new Date();
  const expiresAt = new Date(now.getTime() + 1 * 60 * 1000); // 1 minute

  const { data: session } = await supabase.from('unlock_sessions').insert({
    user_id: user.id,
    app_package: body.app_package,
    unlock_type: 'free',
    duration_minutes: 1,
    expires_at: expiresAt.toISOString(),
    is_active: true,
  }).select('id, expires_at, duration_minutes').single();

  await supabase.from('daily_progress').upsert({
    user_id: user.id, date: today, free_unlock_used: true,
    updated_at: now.toISOString(),
  }, { onConflict: 'user_id,date' });

  return success({ unlock_session: session, message: 'Free unlock: 1 minute' });
}
