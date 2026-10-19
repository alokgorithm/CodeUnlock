// ============================================================
// Profile Routes — GET /api/profile, PUT /api/profile
// NEW FILE — was completely missing from the original backend.
// Required by PRD §11 (Profile screen: username, avatar, college,
// linked accounts, badges, rankings, stats summary).
// Also handles first-login profile initialisation (PRD §4).
// ============================================================
import { SupabaseClient } from '@supabase/supabase-js';
import { AuthUser, UpdateProfileRequest } from '../types';
import { success, error, notFound } from '../utils/response';

// ---- GET /api/profile ----
// Returns the full profile card shown on the Profile screen.
export async function handleGetProfile(
  supabase: SupabaseClient,
  user: AuthUser
): Promise<Response> {
  const { data: profile, error: profileErr } = await supabase
    .from('profiles')
    .select('id, username, avatar_emoji, college, leetcode_username, codeforces_handle, created_at, last_sync_at')
    .eq('id', user.id)
    .single();

  if (profileErr || !profile) return notFound('Profile not found');

  // Fetch ranking summary
  const { data: ranking } = await supabase
    .from('user_rankings')
    .select('global_rank, college_rank, ranking_score, total_verified_solves, consistency_streak_days')
    .eq('user_id', user.id)
    .maybeSingle();

  // Fetch earned badges
  const { data: badges } = await supabase
    .from('user_badges')
    .select('badge_id, progress, earned_at, badges!inner(name, category, icon_emoji)')
    .eq('user_id', user.id)
    .not('earned_at', 'is', null);

  // Fetch 30-day average wellbeing score
  const thirtyDaysAgo = new Date();
  thirtyDaysAgo.setDate(thirtyDaysAgo.getDate() - 30);
  const { data: wellbeing } = await supabase
    .from('wellbeing_daily')
    .select('wellbeing_score')
    .eq('user_id', user.id)
    .gte('date', thirtyDaysAgo.toISOString().split('T')[0]);

  const avgWellbeing = wellbeing && wellbeing.length > 0
    ? Math.round(wellbeing.reduce((s, w) => s + (w.wellbeing_score || 0), 0) / wellbeing.length * 10) / 10
    : null;

  // Fetch total screen time reduction (compare last 30 days vs baseline)
  // Simple: percentage of time NOT spent in restricted apps
  const { data: wellbeingDetail } = await supabase
    .from('wellbeing_daily')
    .select('total_screen_time_minutes, restricted_app_time_minutes')
    .eq('user_id', user.id)
    .gte('date', thirtyDaysAgo.toISOString().split('T')[0]);

  let screenTimeReductionPct = 0;
  if (wellbeingDetail && wellbeingDetail.length > 0) {
    const totalTime = wellbeingDetail.reduce((s, w) => s + (w.total_screen_time_minutes || 0), 0);
    const restrictedTime = wellbeingDetail.reduce((s, w) => s + (w.restricted_app_time_minutes || 0), 0);
    if (totalTime > 0) {
      screenTimeReductionPct = Math.round((1 - restrictedTime / totalTime) * 100);
    }
  }

  // Fetch classroom memberships
  const { data: classrooms } = await supabase
    .from('classroom_members')
    .select('classroom_id, classrooms!inner(id, name, code)')
    .eq('user_id', user.id);

  return success({
    id: profile.id,
    username: profile.username,
    email: user.email,
    avatar_emoji: profile.avatar_emoji,
    college: profile.college,
    leetcode_username: profile.leetcode_username,
    codeforces_handle: profile.codeforces_handle,
    created_at: profile.created_at,
    last_sync_at: profile.last_sync_at,
    ranking: ranking
      ? {
          global_rank: ranking.global_rank,
          college_rank: ranking.college_rank,
          ranking_score: ranking.ranking_score,
          total_verified_solves: ranking.total_verified_solves,
          consistency_streak_days: ranking.consistency_streak_days,
        }
      : null,
    badges: badges?.map((b: Record<string, unknown>) => {
      const badgeInfo = b.badges as { name: string; category: string; icon_emoji: string };
      return {
        badge_id: b.badge_id,
        name: badgeInfo?.name,
        category: badgeInfo?.category,
        icon_emoji: badgeInfo?.icon_emoji,
        earned_at: b.earned_at,
        progress: b.progress,
      };
    }) || [],
    stats_summary: {
      total_verified_solves: ranking?.total_verified_solves || 0,
      current_streak: ranking?.consistency_streak_days || 0,
      wellbeing_score_30d_avg: avgWellbeing,
      screen_time_reduction_pct: screenTimeReductionPct,
    },
    classrooms: classrooms?.map((c: Record<string, unknown>) => {
      const cls = c.classrooms as { id: string; name: string; code: string };
      return { id: cls?.id, name: cls?.name, code: cls?.code };
    }) || [],
  });
}

// ---- PUT /api/profile ----
// Updates mutable profile fields. Changing leetcode_username/codeforces_handle
// triggers an immediate sync attempt (PRD §15 edge case).
export async function handleUpdateProfile(
  supabase: SupabaseClient,
  user: AuthUser,
  body: UpdateProfileRequest
): Promise<Response> {
  if (!body || Object.keys(body).length === 0) {
    return error('INVALID_REQUEST', 'No fields provided to update');
  }

  // Validate username uniqueness if being changed
  if (body.username) {
    if (!/^[a-zA-Z0-9_]{3,20}$/.test(body.username)) {
      return error('INVALID_USERNAME', 'Username must be 3-20 chars, alphanumeric + underscore only');
    }
    const { data: existing } = await supabase
      .from('profiles')
      .select('id')
      .eq('username', body.username)
      .neq('id', user.id)
      .maybeSingle();
    if (existing) {
      return error('USERNAME_TAKEN', 'This username is already taken', 409);
    }
  }

  const updatePayload: Record<string, unknown> = { updated_at: new Date().toISOString() };
  if (body.username !== undefined)          updatePayload.username            = body.username;
  if (body.avatar_emoji !== undefined)      updatePayload.avatar_emoji        = body.avatar_emoji;
  if (body.college !== undefined)           updatePayload.college             = body.college;
  if (body.leetcode_username !== undefined) updatePayload.leetcode_username   = body.leetcode_username;
  if (body.codeforces_handle !== undefined) updatePayload.codeforces_handle   = body.codeforces_handle;

  const { error: updateErr } = await supabase
    .from('profiles')
    .update(updatePayload)
    .eq('id', user.id);

  if (updateErr) return error('UPDATE_FAILED', updateErr.message, 500);

  // If platform username changed, flag for re-sync (clear last_sync_at so
  // daily cron picks this user up immediately)
  if (body.leetcode_username !== undefined || body.codeforces_handle !== undefined) {
    await supabase
      .from('profiles')
      .update({ last_sync_at: null })
      .eq('id', user.id);
  }

  return success({ updated: true, message: 'Profile updated successfully' });
}

// ---- POST /api/auth/setup ----
// Called on first login: creates profile row + user_preferences row +
// empty user_rankings row. PRD §4: "On first login → create user profile
// row + settings row + empty track progress".
export async function handleAuthSetup(
  supabase: SupabaseClient,
  user: AuthUser,
  body: { username?: string; avatar_emoji?: string }
): Promise<Response> {
  // Check if profile already exists
  const { data: existing } = await supabase
    .from('profiles')
    .select('id, username')
    .eq('id', user.id)
    .maybeSingle();

  if (existing) {
    return success({
      setup_complete: true,
      already_setup: true,
      username: existing.username,
      message: 'Profile already exists',
    });
  }

  // Derive a username from email if not provided
  const emailPrefix = user.email.split('@')[0].replace(/[^a-zA-Z0-9_]/g, '_').slice(0, 15);
  const suffix = Math.floor(Math.random() * 9000) + 1000;
  const defaultUsername = `${emailPrefix}_${suffix}`;

  // 1. Create profile
  const { error: profileErr } = await supabase.from('profiles').insert({
    id: user.id,
    email: user.email,
    username: body.username || defaultUsername,
    avatar_emoji: body.avatar_emoji || '🎯',
    created_at: new Date().toISOString(),
  });
  if (profileErr) {
    // If it's a unique violation someone else grabbed this username — retry with suffix
    if (profileErr.code !== '23505') {
      return error('SETUP_FAILED', profileErr.message, 500);
    }
  }

  // 2. Create default user_preferences (settings)
  await supabase.from('user_preferences').insert({
    user_id: user.id,
    usage_mode: 'focused',
    daily_solve_target: 3,
    restricted_apps: [],
    per_app_limits: {},
    emergency_unlock_enabled: true,
    free_unlock_enabled: true,
    is_first_setup_complete: false,
  });

  // 3. Create empty user_rankings row so leaderboard queries don't miss this user
  await supabase.from('user_rankings').insert({
    user_id: user.id,
    global_rank: 999999,
    ranking_score: 0,
    total_verified_solves: 0,
    consistency_streak_days: 0,
    last_computed_at: new Date().toISOString(),
  });

  return success({
    setup_complete: true,
    username: body.username || defaultUsername,
    message: 'Account initialised. Complete onboarding to apply your settings.',
  }, 201);
}
