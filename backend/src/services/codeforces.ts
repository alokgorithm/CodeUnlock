// ============================================================
// Codeforces REST API Client
// ============================================================
import { CodeforcesSubmission } from '../types';

const CF_API_BASE = 'https://codeforces.com/api';

/**
 * Fetch recent submissions for a Codeforces user.
 */
export async function fetchRecentSubmissions(
  handle: string,
  count = 20
): Promise<CodeforcesSubmission[]> {
  const url = `${CF_API_BASE}/user.status?handle=${encodeURIComponent(handle)}&from=1&count=${count}`;

  const response = await fetch(url);

  if (!response.ok) {
    throw new Error(`Codeforces API error: ${response.status}`);
  }

  const json = await response.json() as {
    status: string;
    result?: CodeforcesSubmission[];
    comment?: string;
  };

  if (json.status !== 'OK') {
    throw new Error(`Codeforces API: ${json.comment || 'Unknown error'}`);
  }

  return json.result || [];
}

/**
 * Fetch Codeforces user info (rating, rank, etc).
 */
export async function fetchUserInfo(handle: string): Promise<{
  currentRating: number;
  maxRating: number;
  rank: string;
  contestCount: number;
}> {
  const url = `${CF_API_BASE}/user.info?handles=${encodeURIComponent(handle)}`;

  const response = await fetch(url);

  if (!response.ok) {
    throw new Error(`Codeforces API error: ${response.status}`);
  }

  const json = await response.json() as {
    status: string;
    result?: {
      rating?: number;
      maxRating?: number;
      rank?: string;
    }[];
  };

  const user = json.result?.[0];
  if (!user) throw new Error('Codeforces user not found');

  // Fetch contest count separately
  const ratingUrl = `${CF_API_BASE}/user.rating?handle=${encodeURIComponent(handle)}`;
  let contestCount = 0;
  try {
    const ratingResp = await fetch(ratingUrl);
    if (ratingResp.ok) {
      const ratingJson = await ratingResp.json() as { result?: unknown[] };
      contestCount = ratingJson.result?.length || 0;
    }
  } catch {
    // Non-critical, ignore
  }

  return {
    currentRating: user.rating || 0,
    maxRating: user.maxRating || 0,
    rank: user.rank || 'unrated',
    contestCount,
  };
}
