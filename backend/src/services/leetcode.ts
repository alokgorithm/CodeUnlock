// ============================================================
// LeetCode GraphQL Client — Fetches user stats and submissions
// ============================================================
import { LeetCodeRecentSubmission } from '../types';

const LEETCODE_GRAPHQL_URL = 'https://leetcode.com/graphql';

/**
 * Fetch recent accepted submissions for a LeetCode user.
 */
export async function fetchRecentSubmissions(
  username: string,
  limit = 20
): Promise<LeetCodeRecentSubmission[]> {
  const query = `
    query recentAcSubmissionList($username: String!, $limit: Int!) {
      recentAcSubmissionList(username: $username, limit: $limit) {
        title
        titleSlug
        timestamp
        statusDisplay
        lang
      }
    }
  `;

  const response = await fetch(LEETCODE_GRAPHQL_URL, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      query,
      variables: { username, limit },
    }),
  });

  if (!response.ok) {
    throw new Error(`LeetCode API error: ${response.status}`);
  }

  const json = await response.json() as {
    data?: { recentAcSubmissionList: LeetCodeRecentSubmission[] };
    errors?: { message: string }[];
  };

  if (json.errors?.length) {
    throw new Error(`LeetCode GraphQL error: ${json.errors[0].message}`);
  }

  return json.data?.recentAcSubmissionList || [];
}

/**
 * Fetch full user profile stats from LeetCode.
 */
export async function fetchUserProfile(username: string): Promise<{
  totalSolved: number;
  easySolved: number;
  mediumSolved: number;
  hardSolved: number;
  contestRating: number;
  globalRanking: number;
  reputation: number;
  submissionCount: number;
}> {
  const query = `
    query userProfile($username: String!) {
      matchedUser(username: $username) {
        submitStats {
          acSubmissionNum {
            difficulty
            count
          }
        }
        profile {
          reputation
          ranking
        }
      }
      userContestRanking(username: $username) {
        rating
        globalRanking
      }
    }
  `;

  const response = await fetch(LEETCODE_GRAPHQL_URL, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ query, variables: { username } }),
  });

  if (!response.ok) {
    throw new Error(`LeetCode API error: ${response.status}`);
  }

  const json = await response.json() as {
    data?: {
      matchedUser?: {
        submitStats?: {
          acSubmissionNum: { difficulty: string; count: number }[];
        };
        profile?: { reputation: number; ranking: number };
      };
      userContestRanking?: { rating: number; globalRanking: number } | null;
    };
  };

  const stats = json.data?.matchedUser?.submitStats?.acSubmissionNum || [];
  const profile = json.data?.matchedUser?.profile;
  const contest = json.data?.userContestRanking;

  const getCount = (d: string) => stats.find(s => s.difficulty === d)?.count || 0;

  return {
    totalSolved: getCount('All'),
    easySolved: getCount('Easy'),
    mediumSolved: getCount('Medium'),
    hardSolved: getCount('Hard'),
    contestRating: contest?.rating || 0,
    globalRanking: contest?.globalRanking || profile?.ranking || 0,
    reputation: profile?.reputation || 0,
    submissionCount: 0, // Not directly available from this query
  };
}
