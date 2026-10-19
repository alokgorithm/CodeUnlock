// ============================================================
// CodeUnlock API — Main Router (Cloudflare Worker Entry Point)
// v2.1 — wires all previously missing routes
// ============================================================
import { AutoRouter, cors, IRequest } from 'itty-router';
import {
  Env, AuthUser,
  VerifySolveRequest, UpdateSettingsRequest,
  WellbeingSyncRequest, UpdateProfileRequest,
} from './types';
import { authenticateRequest } from './middleware/auth';
import { checkVerifyCooldown, checkApiRateLimit } from './middleware/rateLimit';
import { createClient } from '@supabase/supabase-js';
import { createUserClient } from './utils/supabase';
import * as response from './utils/response';

// ---- Route handlers ----
import { handleNextProblem, handleListProblems } from './routes/problems';
import { handleVerifySolve } from './routes/verify';
import { handleGetSettings, handleUpdateSettings } from './routes/settings';
import { handleLeetCodeStats, handleCodeforcesStats, handleTrackProgress, handleWellbeingStats } from './routes/stats';
import { handleWellbeingSync, handleSyncTrigger } from './routes/sync';
import { handleGlobalRankings, handleCollegeRankings, handleClassroomRankings } from './routes/rankings';
import { handleGetActiveUnlocks, handleEmergencyUnlock, handleFreeUnlock } from './routes/unlock';
import {
  handleListClassrooms, handleCreateClassroom, handleJoinClassroom,
  handleListFriends, handleListPendingFriends, handleFriendRequest, handleAcceptFriend,
  handleSearch,
} from './routes/social';

// NEW routes
import { handleGetProfile, handleUpdateProfile, handleAuthSetup } from './routes/profile';
import { handleGetDailyProgress, handleConsumeUnlockTime } from './routes/daily';
import { handleGetBadges } from './routes/badges';

const { preflight, corsify } = cors({ origin: '*' });

const router = AutoRouter<IRequest, [Env]>({
  before: [preflight],
  finally: [(r: Response) => corsify(r)],
});

// ---- Auth helper ----
async function authGuard(request: Request, env: Env): Promise<{ user: AuthUser; token: string } | Response> {
  const authHeader = request.headers.get('Authorization');
  const token = authHeader?.startsWith('Bearer ') ? authHeader.slice(7) : '';
  if (!token) return response.unauthorized();

  const user = await authenticateRequest(request, env);
  if (!user) return response.unauthorized();

  const rateCheck = await checkApiRateLimit(user.id, env);
  if (!rateCheck.allowed) {
    return response.tooManyRequests(`Rate limited. Retry after ${rateCheck.retryAfterSeconds}s`);
  }

  return { user, token };
}

// ============================================================
// Health
// ============================================================
router.get('/api/health', () => response.success({ status: 'ok', version: '2.1.0' }));

// ============================================================
// Auth / Onboarding — NEW
// POST /api/auth/setup  — first-login profile + settings + rankings init (PRD §4)
// ============================================================
router.post('/api/auth/setup', async (request: IRequest, env: Env) => {
  const auth = await authGuard(request as unknown as Request, env);
  if (auth instanceof Response) return auth;
  const body = await (request as unknown as Request).json().catch(() => ({})) as { username?: string; avatar_emoji?: string };
  return handleAuthSetup(createUserClient(env, auth.token), auth.user, body);
});

// ============================================================
// Profile — NEW (GET + PUT /api/profile)
// ============================================================
router.get('/api/profile', async (request: IRequest, env: Env) => {
  const auth = await authGuard(request as unknown as Request, env);
  if (auth instanceof Response) return auth;
  return handleGetProfile(createUserClient(env, auth.token), auth.user);
});

router.put('/api/profile', async (request: IRequest, env: Env) => {
  const auth = await authGuard(request as unknown as Request, env);
  if (auth instanceof Response) return auth;
  const body = await (request as unknown as Request).json() as UpdateProfileRequest;
  return handleUpdateProfile(createUserClient(env, auth.token), auth.user, body);
});

// ============================================================
// Daily Progress — NEW
// GET  /api/daily-progress          (PRD §10 Dashboard KPI row)
// POST /api/daily-progress/consume  (unlock time usage tracking)
// ============================================================
router.get('/api/daily-progress', async (request: IRequest, env: Env) => {
  const auth = await authGuard(request as unknown as Request, env);
  if (auth instanceof Response) return auth;
  return handleGetDailyProgress(createUserClient(env, auth.token), auth.user);
});

router.post('/api/daily-progress/consume', async (request: IRequest, env: Env) => {
  const auth = await authGuard(request as unknown as Request, env);
  if (auth instanceof Response) return auth;
  const body = await (request as unknown as Request).json().catch(() => ({})) as { minutes_used: number; app_package?: string };
  return handleConsumeUnlockTime(createUserClient(env, auth.token), auth.user, body);
});

// ============================================================
// Problems
// ============================================================
router.get('/api/next-problem', async (request: IRequest, env: Env) => {
  const auth = await authGuard(request as unknown as Request, env);
  if (auth instanceof Response) return auth;
  return handleNextProblem(createUserClient(env, auth.token), auth.user);
});

router.get('/api/problems', async (request: IRequest, env: Env) => {
  const auth = await authGuard(request as unknown as Request, env);
  if (auth instanceof Response) return auth;
  return handleListProblems(createUserClient(env, auth.token), auth.user, new URL(request.url).searchParams);
});

// ============================================================
// Verification
// ============================================================
router.post('/api/verify-solve', async (request: IRequest, env: Env) => {
  const auth = await authGuard(request as unknown as Request, env);
  if (auth instanceof Response) return auth;

  const cooldown = await checkVerifyCooldown(auth.user.id, env);
  if (!cooldown.allowed) {
    return response.tooManyRequests(`Cooldown active. Retry after ${cooldown.retryAfterSeconds}s`);
  }

  const body = await (request as unknown as Request).json() as VerifySolveRequest;
  return handleVerifySolve(createUserClient(env, auth.token), auth.user, body);
});

// ============================================================
// Unlock
// ============================================================
router.get('/api/unlock/active', async (request: IRequest, env: Env) => {
  const auth = await authGuard(request as unknown as Request, env);
  if (auth instanceof Response) return auth;
  return handleGetActiveUnlocks(createUserClient(env, auth.token), auth.user);
});

router.post('/api/unlock/emergency', async (request: IRequest, env: Env) => {
  const auth = await authGuard(request as unknown as Request, env);
  if (auth instanceof Response) return auth;
  const body = await (request as unknown as Request).json().catch(() => ({})) as { app_package?: string };
  return handleEmergencyUnlock(createUserClient(env, auth.token), auth.user, body);
});

router.post('/api/unlock/free', async (request: IRequest, env: Env) => {
  const auth = await authGuard(request as unknown as Request, env);
  if (auth instanceof Response) return auth;
  const body = await (request as unknown as Request).json() as { app_package: string };
  return handleFreeUnlock(createUserClient(env, auth.token), auth.user, body);
});

// ============================================================
// Stats
// ============================================================
router.get('/api/stats/leetcode', async (request: IRequest, env: Env) => {
  const auth = await authGuard(request as unknown as Request, env);
  if (auth instanceof Response) return auth;
  return handleLeetCodeStats(createUserClient(env, auth.token), auth.user);
});

router.get('/api/stats/codeforces', async (request: IRequest, env: Env) => {
  const auth = await authGuard(request as unknown as Request, env);
  if (auth instanceof Response) return auth;
  return handleCodeforcesStats(createUserClient(env, auth.token), auth.user);
});

router.get('/api/stats/progress', async (request: IRequest, env: Env) => {
  const auth = await authGuard(request as unknown as Request, env);
  if (auth instanceof Response) return auth;
  return handleTrackProgress(createUserClient(env, auth.token), auth.user);
});

router.get('/api/stats/wellbeing', async (request: IRequest, env: Env) => {
  const auth = await authGuard(request as unknown as Request, env);
  if (auth instanceof Response) return auth;
  return handleWellbeingStats(createUserClient(env, auth.token), auth.user);
});

router.get('/api/badges', async (request: IRequest, env: Env) => {
  const auth = await authGuard(request as unknown as Request, env);
  if (auth instanceof Response) return auth;
  return handleGetBadges(createUserClient(env, auth.token), auth.user);
});

// ============================================================
// Settings
// ============================================================
router.get('/api/settings', async (request: IRequest, env: Env) => {
  const auth = await authGuard(request as unknown as Request, env);
  if (auth instanceof Response) return auth;
  return handleGetSettings(createUserClient(env, auth.token), auth.user);
});

router.put('/api/settings', async (request: IRequest, env: Env) => {
  const auth = await authGuard(request as unknown as Request, env);
  if (auth instanceof Response) return auth;
  const body = await (request as unknown as Request).json() as UpdateSettingsRequest;
  return handleUpdateSettings(createUserClient(env, auth.token), auth.user, body);
});

// ============================================================
// Sync
// ============================================================
router.post('/api/wellbeing/sync', async (request: IRequest, env: Env) => {
  const auth = await authGuard(request as unknown as Request, env);
  if (auth instanceof Response) return auth;
  const body = await (request as unknown as Request).json() as WellbeingSyncRequest;
  return handleWellbeingSync(createUserClient(env, auth.token), auth.user, body);
});

router.post('/api/sync/trigger', async (request: IRequest, env: Env) => {
  const auth = await authGuard(request as unknown as Request, env);
  if (auth instanceof Response) return auth;
  return handleSyncTrigger(createUserClient(env, auth.token), auth.user);
});

// ============================================================
// Rankings
// ============================================================
router.get('/api/rankings/global', async (request: IRequest, env: Env) => {
  const auth = await authGuard(request as unknown as Request, env);
  if (auth instanceof Response) return auth;
  return handleGlobalRankings(createUserClient(env, auth.token), auth.user, new URL(request.url).searchParams);
});

router.get('/api/rankings/college', async (request: IRequest, env: Env) => {
  const auth = await authGuard(request as unknown as Request, env);
  if (auth instanceof Response) return auth;
  return handleCollegeRankings(createUserClient(env, auth.token), auth.user, new URL(request.url).searchParams);
});

router.get('/api/rankings/classroom/:id', async (request: IRequest, env: Env) => {
  const auth = await authGuard(request as unknown as Request, env);
  if (auth instanceof Response) return auth;
  return handleClassroomRankings(createUserClient(env, auth.token), auth.user, request.params.id);
});

// ============================================================
// Social — Classrooms
// ============================================================

// NEW: list classrooms the user belongs to
router.get('/api/classrooms', async (request: IRequest, env: Env) => {
  const auth = await authGuard(request as unknown as Request, env);
  if (auth instanceof Response) return auth;
  return handleListClassrooms(createUserClient(env, auth.token), auth.user);
});

router.post('/api/classrooms', async (request: IRequest, env: Env) => {
  const auth = await authGuard(request as unknown as Request, env);
  if (auth instanceof Response) return auth;
  const body = await (request as unknown as Request).json() as { name: string };
  return handleCreateClassroom(createUserClient(env, auth.token), auth.user, body);
});

router.post('/api/classrooms/join', async (request: IRequest, env: Env) => {
  const auth = await authGuard(request as unknown as Request, env);
  if (auth instanceof Response) return auth;
  const body = await (request as unknown as Request).json() as { code: string };
  return handleJoinClassroom(createUserClient(env, auth.token), auth.user, body);
});

// ============================================================
// Social — Friends
// ============================================================
router.get('/api/friends', async (request: IRequest, env: Env) => {
  const auth = await authGuard(request as unknown as Request, env);
  if (auth instanceof Response) return auth;
  return handleListFriends(createUserClient(env, auth.token), auth.user);
});

// NEW: list pending friend requests (incoming + outgoing)
router.get('/api/friends/pending', async (request: IRequest, env: Env) => {
  const auth = await authGuard(request as unknown as Request, env);
  if (auth instanceof Response) return auth;
  return handleListPendingFriends(createUserClient(env, auth.token), auth.user);
});

router.post('/api/friends/request', async (request: IRequest, env: Env) => {
  const auth = await authGuard(request as unknown as Request, env);
  if (auth instanceof Response) return auth;
  const body = await (request as unknown as Request).json() as { username: string };
  return handleFriendRequest(createUserClient(env, auth.token), auth.user, body);
});

router.post('/api/friends/accept', async (request: IRequest, env: Env) => {
  const auth = await authGuard(request as unknown as Request, env);
  if (auth instanceof Response) return auth;
  const body = await (request as unknown as Request).json() as { requester_id: string };
  return handleAcceptFriend(createUserClient(env, auth.token), auth.user, body);
});

// ============================================================
// Search
// ============================================================
router.get('/api/search', async (request: IRequest, env: Env) => {
  const auth = await authGuard(request as unknown as Request, env);
  if (auth instanceof Response) return auth;
  return handleSearch(createUserClient(env, auth.token), auth.user, new URL(request.url).searchParams);
});

// ============================================================
// Admin — Manual Daily Sync (protected, service role only)
// ============================================================
router.post('/api/admin/daily-sync', async (request: IRequest, env: Env) => {
  // Require service-role secret header to prevent accidental triggers
  const secret = request.headers.get('X-Admin-Secret');
  if (!secret || secret !== env.SUPABASE_SERVICE_ROLE_KEY) {
    return response.forbidden('Admin access required');
  }

  const { runDailySync } = await import('./services/dailySync');
  const supabase = createClient(env.SUPABASE_URL, env.SUPABASE_SERVICE_ROLE_KEY, {
    auth: { persistSession: false, autoRefreshToken: false },
  });
  const result = await runDailySync(supabase);
  return response.success(result);
});

// ============================================================
// 404 Fallback
// ============================================================
router.all('*', () => response.notFound('Endpoint not found'));

// ============================================================
// Worker Export
// ============================================================
export default {
  fetch: (request: Request, env: Env, ctx: ExecutionContext) =>
    router.fetch(request, env, ctx),

  // Cloudflare Cron Trigger — PRD §8.1: 00:00 UTC every day
  async scheduled(_event: ScheduledEvent, env: Env, ctx: ExecutionContext): Promise<void> {
    const { runDailySync } = await import('./services/dailySync');
    const key = env.SUPABASE_SERVICE_ROLE_KEY || env.SUPABASE_ANON_KEY;
    const supabase = createClient(env.SUPABASE_URL, key, {
      auth: { persistSession: false, autoRefreshToken: false },
    });

    ctx.waitUntil(
      runDailySync(supabase)
        .then(r => console.log(`[Cron] Daily sync: ${r.synced}/${r.totalUsers} users`))
        .catch(e => console.error('[Cron] Daily sync failed:', e))
    );
  },
};
