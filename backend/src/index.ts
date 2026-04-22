// ============================================================
// CodeUnlock API — Main Router (Cloudflare Worker Entry Point)
// ============================================================
import { AutoRouter, cors, IRequest } from 'itty-router';
import { Env, AuthUser, VerifySolveRequest, UpdateSettingsRequest, WellbeingSyncRequest } from './types';
import { authenticateRequest } from './middleware/auth';
import { checkVerifyCooldown, checkApiRateLimit } from './middleware/rateLimit';
import { withCors } from './middleware/errorHandler';
import { createClient } from '@supabase/supabase-js';
import { createServiceClient, createUserClient } from './utils/supabase';
import * as response from './utils/response';

// Route handlers
import { handleNextProblem, handleListProblems } from './routes/problems';
import { handleVerifySolve } from './routes/verify';
import { handleGetSettings, handleUpdateSettings } from './routes/settings';
import { handleLeetCodeStats, handleCodeforcesStats, handleTrackProgress, handleWellbeingStats } from './routes/stats';
import { handleWellbeingSync, handleSyncTrigger } from './routes/sync';
import { handleGlobalRankings, handleCollegeRankings, handleClassroomRankings } from './routes/rankings';
import { handleGetActiveUnlocks, handleEmergencyUnlock, handleFreeUnlock } from './routes/unlock';
import {
  handleCreateClassroom, handleJoinClassroom,
  handleListFriends, handleFriendRequest, handleAcceptFriend,
  handleSearch
} from './routes/social';

const { preflight, corsify } = cors({ origin: '*' });

const router = AutoRouter<IRequest, [Env]>({
  before: [preflight],
  finally: [(r: Response) => corsify(r)],
});

// ---- Helper: Authenticate + Rate Limit ----
async function authGuard(request: Request, env: Env): Promise<{ user: AuthUser; token: string } | Response> {
  const authHeader = request.headers.get('Authorization');
  const token = authHeader?.startsWith('Bearer ') ? authHeader.slice(7) : '';

  const user = await authenticateRequest(request, env);
  if (!user) return response.unauthorized();

  const rateCheck = await checkApiRateLimit(user.id, env);
  if (!rateCheck.allowed) {
    return response.tooManyRequests(`Rate limited. Retry after ${rateCheck.retryAfterSeconds}s`);
  }

  return { user, token };
}

// ---- Health Check ----
router.get('/api/health', () => response.success({ status: 'ok', version: '2.0.0' }));

// ---- Problems ----
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

// ---- Verification ----
router.post('/api/verify-solve', async (request: IRequest, env: Env) => {
  const auth = await authGuard(request as unknown as Request, env);
  if (auth instanceof Response) return auth;

  // Verify-specific cooldown (30s)
  const cooldown = await checkVerifyCooldown(auth.user.id, env);
  if (!cooldown.allowed) {
    return response.tooManyRequests(`Cooldown active. Retry after ${cooldown.retryAfterSeconds}s`);
  }

  const body = await (request as unknown as Request).json() as VerifySolveRequest;
  return handleVerifySolve(createUserClient(env, auth.token), auth.user, body);
});

// ---- Unlock ----
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

// ---- Stats ----
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

// ---- Settings ----
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

// ---- Sync ----
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

// ---- Rankings ----
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

// ---- Social ----
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

router.get('/api/friends', async (request: IRequest, env: Env) => {
  const auth = await authGuard(request as unknown as Request, env);
  if (auth instanceof Response) return auth;
  return handleListFriends(createUserClient(env, auth.token), auth.user);
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

router.get('/api/search', async (request: IRequest, env: Env) => {
  const auth = await authGuard(request as unknown as Request, env);
  if (auth instanceof Response) return auth;
  return handleSearch(createUserClient(env, auth.token), auth.user, new URL(request.url).searchParams);
});

// ---- Admin: Manual Daily Sync Trigger (for testing) ----
router.post('/api/admin/daily-sync', async (request: IRequest, env: Env) => {
  const auth = await authGuard(request as unknown as Request, env);
  if (auth instanceof Response) return auth;

  // Use anon key + no auth context for cross-user sync access
  const { runDailySync } = await import('./services/dailySync');
  const anonClient = createAnonClient(env);
  const result = await runDailySync(anonClient);
  return response.success(result);
});

// ---- 404 Fallback ----
router.all('*', () => response.notFound('Endpoint not found'));

// Helper: create anon client without user context (for cron/admin sync)
function createAnonClient(env: Env) {
  return createClient(env.SUPABASE_URL, env.SUPABASE_ANON_KEY, {
    auth: { persistSession: false, autoRefreshToken: false },
  });
}

export default {
  fetch: (request: Request, env: Env, ctx: ExecutionContext) =>
    router.fetch(request, env, ctx),

  // Cloudflare Cron Trigger — PRD §8.1: Daily sync at 00:00 UTC
  async scheduled(event: ScheduledEvent, env: Env, ctx: ExecutionContext): Promise<void> {
    const { runDailySync } = await import('./services/dailySync');
    const { createClient } = await import('@supabase/supabase-js');

    // Try service role first, fall back to anon key
    const key = env.SUPABASE_SERVICE_ROLE_KEY || env.SUPABASE_ANON_KEY;
    const supabase = createClient(env.SUPABASE_URL, key, {
      auth: { persistSession: false, autoRefreshToken: false },
    });

    ctx.waitUntil(
      runDailySync(supabase)
        .then(result => {
          console.log(`[Cron] Daily sync complete: ${result.synced}/${result.totalUsers} users synced`);
        })
        .catch(err => {
          console.error('[Cron] Daily sync failed:', err);
        })
    );
  },
};
