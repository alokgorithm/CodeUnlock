-- ============================================================
-- Migration 003: Database-Native Leaderboard Ranking Recalculation RPC
-- File: supabase/migrations/003_ranking_rpc.sql
-- ============================================================

-- Create a plpgsql function to recompute global ranks in a single query.
-- This replaces the slow O(N) loop of separate database updates.
CREATE OR REPLACE FUNCTION public.recompute_all_global_ranks()
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
BEGIN
  WITH ranked_users AS (
    SELECT user_id, ROW_NUMBER() OVER (ORDER BY ranking_score DESC) as new_rank
    FROM public.user_rankings
  )
  UPDATE public.user_rankings ur
  SET global_rank = ru.new_rank
  FROM ranked_users ru
  WHERE ur.user_id = ru.user_id;
END;
$$;

-- Grant execution permissions on the function to enable calling via Supabase JS SDK.
GRANT EXECUTE ON FUNCTION public.recompute_all_global_ranks() TO service_role;
GRANT EXECUTE ON FUNCTION public.recompute_all_global_ranks() TO authenticated;
