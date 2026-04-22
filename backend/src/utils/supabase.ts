// ============================================================
// Supabase Client Factory
// ============================================================
import { createClient, SupabaseClient } from '@supabase/supabase-js';
import { Env } from '../types';

/**
 * Creates a Supabase client with the service role key.
 * This bypasses RLS — use for server-side operations only.
 */
export function createServiceClient(env: Env): SupabaseClient {
  return createClient(env.SUPABASE_URL, env.SUPABASE_SERVICE_ROLE_KEY, {
    auth: { persistSession: false, autoRefreshToken: false },
  });
}

/**
 * Creates a Supabase client scoped to the authenticated user's JWT.
 * This respects RLS policies.
 */
export function createUserClient(env: Env, accessToken: string): SupabaseClient {
  return createClient(env.SUPABASE_URL, env.SUPABASE_ANON_KEY, {
    global: { headers: { Authorization: `Bearer ${accessToken}` } },
    auth: { persistSession: false, autoRefreshToken: false },
  });
}
