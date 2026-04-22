// ============================================================
// Solve Verification Service — Anti-cheat & reward granting
// ============================================================
import { SupabaseClient } from '@supabase/supabase-js';
import * as leetcode from './leetcode';
import * as codeforces from './codeforces';

interface VerificationResult {
  verified: boolean;
  reason?: string;
  submissionTimestamp?: number;
  language?: string;
  runtime_ms?: number;
  memory_kb?: number;
}

/**
 * Verify that a user has solved a specific problem on the given platform.
 * Anti-cheat: ensures submission happened AFTER the problem was assigned.
 */
export async function verifySolve(
  supabase: SupabaseClient,
  userId: string,
  problemId: number
): Promise<VerificationResult> {
  // 1. Get problem details
  const { data: problem, error: problemErr } = await supabase
    .from('problems')
    .select('*')
    .eq('id', problemId)
    .single();

  if (problemErr || !problem) {
    return { verified: false, reason: 'PROBLEM_NOT_FOUND' };
  }

  // 2. Check if already verified
  const { data: existingSolve } = await supabase
    .from('user_solves')
    .select('id, verified')
    .eq('user_id', userId)
    .eq('problem_id', problemId)
    .eq('verified', true)
    .maybeSingle();

  if (existingSolve) {
    return { verified: false, reason: 'ALREADY_VERIFIED' };
  }

  // 3. Get user profile for platform username
  const { data: profile, error: profileErr } = await supabase
    .from('profiles')
    .select('leetcode_username, codeforces_handle')
    .eq('id', userId)
    .single();

  if (profileErr || !profile) {
    return { verified: false, reason: 'PROFILE_NOT_FOUND' };
  }

  // 4. Get assignment timestamp (for anti-cheat)
  const { data: solveRecord } = await supabase
    .from('user_solves')
    .select('assigned_at')
    .eq('user_id', userId)
    .eq('problem_id', problemId)
    .maybeSingle();

  const assignedAt = solveRecord?.assigned_at
    ? new Date(solveRecord.assigned_at).getTime() / 1000
    : 0;

  // 5. Verify on platform
  if (problem.platform === 'leetcode') {
    return verifyLeetCode(profile.leetcode_username, problem.slug, assignedAt);
  } else if (problem.platform === 'codeforces') {
    return verifyCodeforces(
      profile.codeforces_handle,
      problem.codeforces_contest_id,
      problem.codeforces_problem_index,
      assignedAt
    );
  }

  return { verified: false, reason: 'UNSUPPORTED_PLATFORM' };
}

async function verifyLeetCode(
  username: string | null,
  problemSlug: string,
  assignedAtEpoch: number
): Promise<VerificationResult> {
  if (!username) {
    return { verified: false, reason: 'LEETCODE_USERNAME_NOT_SET' };
  }

  try {
    const submissions = await leetcode.fetchRecentSubmissions(username, 30);

    const match = submissions.find(
      (s) =>
        s.titleSlug === problemSlug &&
        s.statusDisplay === 'Accepted' &&
        (assignedAtEpoch === 0 || parseInt(s.timestamp) >= assignedAtEpoch)
    );

    if (match) {
      return {
        verified: true,
        submissionTimestamp: parseInt(match.timestamp),
        language: match.lang,
      };
    }

    // Check if there's a submission but it predates assignment
    const preMatch = submissions.find(
      (s) => s.titleSlug === problemSlug && s.statusDisplay === 'Accepted'
    );

    if (preMatch && assignedAtEpoch > 0 && parseInt(preMatch.timestamp) < assignedAtEpoch) {
      return { verified: false, reason: 'SUBMISSION_PREDATES_ASSIGNMENT' };
    }

    return { verified: false, reason: 'NO_ACCEPTED_SUBMISSION' };
  } catch (err) {
    return {
      verified: false,
      reason: `PLATFORM_UNAVAILABLE: ${err instanceof Error ? err.message : 'Unknown'}`,
    };
  }
}

async function verifyCodeforces(
  handle: string | null,
  contestId: number | null,
  problemIndex: string | null,
  assignedAtEpoch: number
): Promise<VerificationResult> {
  if (!handle) {
    return { verified: false, reason: 'CODEFORCES_HANDLE_NOT_SET' };
  }
  if (!contestId || !problemIndex) {
    return { verified: false, reason: 'CODEFORCES_PROBLEM_NOT_MAPPED' };
  }

  try {
    const submissions = await codeforces.fetchRecentSubmissions(handle, 30);

    const match = submissions.find(
      (s) =>
        s.contestId === contestId &&
        s.problem.index === problemIndex &&
        s.verdict === 'OK' &&
        (assignedAtEpoch === 0 || s.creationTimeSeconds >= assignedAtEpoch)
    );

    if (match) {
      return {
        verified: true,
        submissionTimestamp: match.creationTimeSeconds,
        language: match.programmingLanguage,
        runtime_ms: match.timeConsumedMillis,
        memory_kb: Math.round(match.memoryConsumedBytes / 1024),
      };
    }

    return { verified: false, reason: 'NO_ACCEPTED_SUBMISSION' };
  } catch (err) {
    return {
      verified: false,
      reason: `PLATFORM_UNAVAILABLE: ${err instanceof Error ? err.message : 'Unknown'}`,
    };
  }
}

/**
 * Grant unlock time after a verified solve.
 * Returns the created unlock session.
 */
export async function grantUnlockReward(
  supabase: SupabaseClient,
  userId: string,
  problemId: number,
  verificationResult: VerificationResult
): Promise<{
  unlock_session_id: string;
  expires_at: string;
  duration_minutes: number;
  daily_progress: {
    problems_solved: number;
    daily_target: number;
    target_met: boolean;
    unlock_minutes_earned: number;
    unlock_minutes_used: number;
  };
}> {
  const UNLOCK_MINUTES = 30;
  const now = new Date();
  const today = now.toISOString().split('T')[0];
  const expiresAt = new Date(now.getTime() + UNLOCK_MINUTES * 60 * 1000);

  // 1. Upsert user_solves record
  await supabase
    .from('user_solves')
    .upsert(
      {
        user_id: userId,
        problem_id: problemId,
        verified: true,
        verified_at: now.toISOString(),
        solved_at: verificationResult.submissionTimestamp
          ? new Date(verificationResult.submissionTimestamp * 1000).toISOString()
          : now.toISOString(),
        status: 'solved',
      },
      { onConflict: 'user_id,problem_id', ignoreDuplicates: false }
    );

  // 2. Insert submission record
  const { data: problem } = await supabase
    .from('problems')
    .select('slug, title, platform')
    .eq('id', problemId)
    .single();

  if (problem) {
    await supabase.from('submissions').insert({
      user_id: userId,
      platform: problem.platform,
      problem_slug: problem.slug,
      problem_title: problem.title,
      verdict: 'accepted',
      language: verificationResult.language || 'unknown',
      runtime_ms: verificationResult.runtime_ms || null,
      memory_kb: verificationResult.memory_kb || null,
      submitted_at: verificationResult.submissionTimestamp
        ? new Date(verificationResult.submissionTimestamp * 1000).toISOString()
        : now.toISOString(),
      is_verified: true,
    });
  }

  // 3. Create unlock session
  const { data: unlockSession } = await supabase
    .from('unlock_sessions')
    .insert({
      user_id: userId,
      app_package: '__global__', // Global unlock (all restricted apps)
      unlock_type: 'coding',
      duration_minutes: UNLOCK_MINUTES,
      expires_at: expiresAt.toISOString(),
      is_active: true,
    })
    .select('id, expires_at')
    .single();

  // 4. Upsert daily_progress
  const { data: existingProgress } = await supabase
    .from('daily_progress')
    .select('*')
    .eq('user_id', userId)
    .eq('date', today)
    .maybeSingle();

  const { data: userPrefs } = await supabase
    .from('user_preferences')
    .select('daily_solve_target')
    .eq('user_id', userId)
    .single();

  const dailyTarget = userPrefs?.daily_solve_target || 3;
  const newSolvedCount = (existingProgress?.problems_solved || 0) + 1;
  const newUnlockEarned = (existingProgress?.unlock_minutes_earned || 0) + UNLOCK_MINUTES;
  const targetMet = newSolvedCount >= dailyTarget;

  // Bonus time for meeting daily target
  let bonusMinutes = 0;
  if (targetMet && !existingProgress?.bonus_time_earned) {
    bonusMinutes = 60; // 1 hour bonus for meeting target
  }

  await supabase
    .from('daily_progress')
    .upsert(
      {
        user_id: userId,
        date: today,
        problems_solved: newSolvedCount,
        unlock_minutes_earned: newUnlockEarned + bonusMinutes,
        unlock_minutes_used: existingProgress?.unlock_minutes_used || 0,
        daily_target_met: targetMet,
        bonus_time_earned: targetMet,
        updated_at: now.toISOString(),
      },
      { onConflict: 'user_id,date' }
    );

  return {
    unlock_session_id: unlockSession?.id || '',
    expires_at: expiresAt.toISOString(),
    duration_minutes: UNLOCK_MINUTES + bonusMinutes,
    daily_progress: {
      problems_solved: newSolvedCount,
      daily_target: dailyTarget,
      target_met: targetMet,
      unlock_minutes_earned: newUnlockEarned + bonusMinutes,
      unlock_minutes_used: existingProgress?.unlock_minutes_used || 0,
    },
  };
}
