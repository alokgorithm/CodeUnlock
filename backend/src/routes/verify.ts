// ============================================================
// Verify Routes — POST /api/verify-solve
// ============================================================
import { SupabaseClient } from '@supabase/supabase-js';
import { AuthUser, VerifySolveRequest } from '../types';
import { success, error } from '../utils/response';
import { verifySolve, grantUnlockReward } from '../services/verification';
import { updateUserRanking } from '../services/ranking';
import { updateBadgeProgress } from '../services/badges';

export async function handleVerifySolve(
  supabase: SupabaseClient,
  user: AuthUser,
  body: VerifySolveRequest
): Promise<Response> {
  const { problem_id } = body;

  if (!problem_id) {
    return error('INVALID_REQUEST', 'problem_id is required');
  }

  // Verify the solve via platform API
  const result = await verifySolve(supabase, user.id, problem_id);

  if (!result.verified) {
    const statusCode = result.reason === 'ALREADY_VERIFIED' ? 409
      : result.reason?.startsWith('PLATFORM_UNAVAILABLE') ? 503
      : result.reason === 'SUBMISSION_PREDATES_ASSIGNMENT' ? 422
      : 400;

    return error(result.reason || 'VERIFICATION_FAILED',
      getErrorMessage(result.reason || ''), statusCode);
  }

  // Grant unlock reward
  const reward = await grantUnlockReward(supabase, user.id, problem_id, result);

  // Update ranking and badges (fire-and-forget, don't block response)
  try {
    await Promise.all([
      updateUserRanking(supabase, user.id),
      updateBadgeProgress(supabase, user.id),
    ]);
  } catch (err) {
    console.error('Post-verification update failed:', err);
  }

  return success({
    verified: true,
    unlock_granted_minutes: reward.duration_minutes,
    daily_progress: reward.daily_progress,
    unlock_session: {
      id: reward.unlock_session_id,
      expires_at: reward.expires_at,
      duration_minutes: reward.duration_minutes,
    },
  });
}

function getErrorMessage(reason: string): string {
  const messages: Record<string, string> = {
    PROBLEM_NOT_FOUND: 'Problem not found in the database',
    ALREADY_VERIFIED: 'You have already solved and verified this problem',
    PROFILE_NOT_FOUND: 'User profile not found',
    LEETCODE_USERNAME_NOT_SET: 'Please set your LeetCode username in settings',
    CODEFORCES_HANDLE_NOT_SET: 'Please set your Codeforces handle in settings',
    CODEFORCES_PROBLEM_NOT_MAPPED: 'This Codeforces problem is not properly configured',
    NO_ACCEPTED_SUBMISSION: 'No accepted submission found for this problem',
    SUBMISSION_PREDATES_ASSIGNMENT: 'Your submission was made before the problem was assigned',
  };
  return messages[reason] || `Verification failed: ${reason}`;
}
