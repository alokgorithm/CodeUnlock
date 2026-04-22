// ============================================================
// JWT Auth Middleware — Validates Supabase JWT tokens
// ============================================================
import { createClient } from '@supabase/supabase-js';
import { Env, AuthUser } from '../types';

/**
 * Extracts and validates the JWT from the Authorization header.
 * Returns the authenticated user or null if invalid.
 */
export async function authenticateRequest(
  request: Request,
  env: Env
): Promise<AuthUser | null> {
  const authHeader = request.headers.get('Authorization');
  if (!authHeader?.startsWith('Bearer ')) {
    return null;
  }

  const token = authHeader.slice(7);
  if (!token) return null;

  try {
    const supabase = createClient(env.SUPABASE_URL, env.SUPABASE_ANON_KEY, {
      auth: { persistSession: false, autoRefreshToken: false },
    });

    const { data: { user }, error } = await supabase.auth.getUser(token);

    if (error || !user) return null;

    return {
      id: user.id,
      email: user.email || '',
    };
  } catch {
    return null;
  }
}
