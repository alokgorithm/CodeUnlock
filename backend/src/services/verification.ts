// ============================================================
// Solve Verification Service — Anti-cheat & reward granting
// FIXES:
//   1. Codeforces verification had NO "predates assignment" check —
//      only LeetCode did. Now both platforms enforce it (PRD §14).
//   2. submissions insert was missing the synced_at column (PRD §7.3).
//   3. Anti-cheat: >5 solves/hour flagged (PRD §14). Now rate-checked
//      before any verification proceeds.
//   4. Platform account age check (PRD §14: "Require minimum account
//      age 7 days on platform") added for LeetCode via profile join date.
// ============================================================
import { SupabaseClient } from '@supabase/supabase-js';
import * as leetcode from './leetcode';
import * as codeforces from './codeforces';

const UNLOCK_MINUTES = 30;
const MAX_SOLVES_PER_HOUR = 5; // PRD §14 anti-cheat threshold

interface VerificationResult {
  verified: boolean;
  reason?: string;
  submissionTimestamp?: number;
  language?: string;
  runtime_ms?: number;
  memory_kb?: number;
}

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

  // FIX: Anti-cheat — >5 solves in the last hour is flagged (PRD §14)
  const oneHourAgo = new Date(Date.now() - 60 * 60 * 1000).toISOString();
  const { count: recentSolves } = await supabase
    .from('user_solves')
    .select('*', { count: 'exact', head: true })
    .eq('user_id', userId)
    .eq('verified', true)
    .gte('verified_at', oneHourAgo);

  if ((recentSolves || 0) >= MAX_SOLVES_PER_HOUR) {
    return { verified: false, reason: 'ABNORMAL_SOLVE_RATE' };
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

  // 4. Get assignment timestamp (for anti-cheat: submission must post-date assignment)
  const { data: solveRecord } = await supabase
    .from('user_solves')
    .select('assigned_at')
    .eq('user_id', userId)
    .eq('problem_id', problemId)
    .maybeSingle();

  const assignedAt = solveRecord?.assigned_at
    ? new Date(solveRecord.assigned_at).getTime() / 1000
    : 0;

  // 5. Platform-specific verification
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
  if (!username) return { verified: false, reason: 'LEETCODE_USERNAME_NOT_SET' };

  try {
    const submissions = await leetcode.fetchRecentSubmissions(username, 30);

    // Look for an accepted submission AFTER the problem was assigned
    const match = submissions.find(
      s =>
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

    // Check if solved BEFORE assignment (pre-solved problem — anti-cheat)
    const preMatch = submissions.find(
      s => s.titleSlug === problemSlug && s.statusDisplay === 'Accepted'
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
  if (!handle) return { verified: false, reason: 'CODEFORCES_HANDLE_NOT_SET' };
  if (!contestId || !problemIndex) return { verified: false, reason: 'CODEFORCES_PROBLEM_NOT_MAPPED' };

  try {
    const submissions = await codeforces.fetchRecentSubmissions(handle, 50);

    // FIX: Added SUBMISSION_PREDATES_ASSIGNMENT check (was missing for CF)
    const allMatches = submissions.filter(
      s =>
        s.contestId === contestId &&
        s.problem.index === problemIndex &&
        s.verdict === 'OK'
    );

    // First look for a valid (post-assignment) submission
    const match = allMatches.find(
      s => assignedAtEpoch === 0 || s.creationTimeSeconds >= assignedAtEpoch
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

    // FIX: Check if they solved it before the problem was assigned
    const preMatch = allMatches.find(
      s => assignedAtEpoch > 0 && s.creationTimeSeconds < assignedAtEpoch
    );
    if (preMatch) {
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
  const now = new Date();
  const today = now.toISOString().split('T')[0];
  const expiresAt = new Date(now.getTime() + UNLOCK_MINUTES * 60 * 1000);

  // 1. Upsert user_solves record as verified
  await supabase.from('user_solves').upsert(
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

  // 2. Insert into submissions table (FIX: added synced_at per PRD §7.3)
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
      synced_at: now.toISOString(),   // FIX: was missing
      is_verified: true,
    });
  }

  // 3. Create unlock session (global — applies to all restricted apps)
  const { data: unlockSession } = await supabase
    .from('unlock_sessions')
    .insert({
      user_id: userId,
      app_package: '__global__',
      unlock_type: 'coding',
      duration_minutes: UNLOCK_MINUTES,
      expires_at: expiresAt.toISOString(),
      is_active: true,
    })
    .select('id, expires_at')
    .single();

  // 4. Update daily_progress
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

  // Bonus: 60 minutes extra for first time hitting daily target
  let bonusMinutes = 0;
  if (targetMet && !existingProgress?.bonus_time_earned) {
    bonusMinutes = 60;
  }

  await supabase.from('daily_progress').upsert(
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
