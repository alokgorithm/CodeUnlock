// ============================================================
// Settings Routes — GET/PUT /api/settings
// ============================================================
import { SupabaseClient } from '@supabase/supabase-js';
import { AuthUser, UpdateSettingsRequest } from '../types';
import { success, error, notFound } from '../utils/response';

export async function handleGetSettings(
  supabase: SupabaseClient,
  user: AuthUser
): Promise<Response> {
  const { data, error: err } = await supabase
    .from('user_preferences')
    .select('*')
    .eq('user_id', user.id)
    .single();

  if (err || !data) return notFound('Settings not found. Complete onboarding first.');

  const pendingChanges = data.pending_usage_mode || data.pending_daily_solve_target
    ? {
        usage_mode: data.pending_usage_mode,
        daily_solve_target: data.pending_daily_solve_target,
        apply_epoch_day: data.pending_apply_epoch_day,
      }
    : null;

  return success({
    usage_mode: data.usage_mode,
    daily_solve_target: data.daily_solve_target,
    restricted_apps: data.restricted_apps || [],
    per_app_limits: data.per_app_limits || {},
    emergency_unlock_enabled: data.emergency_unlock_enabled,
    free_unlock_enabled: data.free_unlock_enabled,
    is_first_setup_complete: data.is_first_setup_complete,
    pending_changes: pendingChanges,
  });
}

export async function handleUpdateSettings(
  supabase: SupabaseClient,
  user: AuthUser,
  body: UpdateSettingsRequest
): Promise<Response> {
  // Check if first setup
  const { data: existing } = await supabase
    .from('user_preferences')
    .select('is_first_setup_complete')
    .eq('user_id', user.id)
    .single();

  if (!existing) {
    return notFound('Settings not found. Complete onboarding first.');
  }

  const isFirstSetup = !existing.is_first_setup_complete;
  const now = new Date();
  const todayEpochDay = Math.floor(now.getTime() / 86400000);
  const tomorrowEpochDay = todayEpochDay + 1;

  if (isFirstSetup) {
    // First setup applies immediately
    const { error: err } = await supabase
      .from('user_preferences')
      .update({
        ...(body.usage_mode && { usage_mode: body.usage_mode }),
        ...(body.daily_solve_target && { daily_solve_target: body.daily_solve_target }),
        ...(body.restricted_apps && { restricted_apps: body.restricted_apps }),
        ...(body.per_app_limits && { per_app_limits: body.per_app_limits }),
        ...(body.emergency_unlock_enabled !== undefined && { emergency_unlock_enabled: body.emergency_unlock_enabled }),
        ...(body.free_unlock_enabled !== undefined && { free_unlock_enabled: body.free_unlock_enabled }),
        is_first_setup_complete: true,
        last_settings_applied_epoch_day: todayEpochDay,
        updated_at: now.toISOString(),
      })
      .eq('user_id', user.id);

    if (err) return error('UPDATE_FAILED', err.message, 500);
    return success({ applied: true, message: 'Settings applied immediately (first setup)' });
  }

  // Subsequent changes apply next day
  const { error: err } = await supabase
    .from('user_preferences')
    .update({
      ...(body.restricted_apps && { restricted_apps: body.restricted_apps }),
      ...(body.per_app_limits && { per_app_limits: body.per_app_limits }),
      ...(body.emergency_unlock_enabled !== undefined && { emergency_unlock_enabled: body.emergency_unlock_enabled }),
      ...(body.free_unlock_enabled !== undefined && { free_unlock_enabled: body.free_unlock_enabled }),
      ...(body.usage_mode && { pending_usage_mode: body.usage_mode }),
      ...(body.daily_solve_target && { pending_daily_solve_target: body.daily_solve_target }),
      pending_apply_epoch_day: tomorrowEpochDay,
      updated_at: now.toISOString(),
    })
    .eq('user_id', user.id);

  if (err) return error('UPDATE_FAILED', err.message, 500);

  return success({
    applied: false,
    pending_apply_date: new Date((tomorrowEpochDay) * 86400000).toISOString().split('T')[0],
    message: 'Mode/target changes will apply tomorrow. App list changes applied immediately.',
  });
}
