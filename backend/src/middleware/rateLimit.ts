// ============================================================
// Rate Limiter — Per-user rate limiting via KV
// ============================================================
import { Env } from '../types';

interface RateLimitOptions {
  /** Max requests per window */
  maxRequests: number;
  /** Window duration in seconds */
  windowSeconds: number;
  /** Key prefix for the KV store */
  prefix: string;
}

/**
 * Checks and enforces rate limiting for a user.
 * Returns true if the request is allowed, false if rate limited.
 */
export async function checkRateLimit(
  userId: string,
  env: Env,
  options: RateLimitOptions
): Promise<{ allowed: boolean; retryAfterSeconds?: number }> {
  const key = `${options.prefix}:${userId}`;

  try {
    const existing = await env.RATE_LIMIT.get(key);

    if (existing) {
      const data = JSON.parse(existing) as { count: number; windowStart: number };
      const now = Date.now();
      const elapsed = (now - data.windowStart) / 1000;

      if (elapsed < options.windowSeconds) {
        if (data.count >= options.maxRequests) {
          return {
            allowed: false,
            retryAfterSeconds: Math.ceil(options.windowSeconds - elapsed),
          };
        }
        // Increment counter
        data.count++;
        await env.RATE_LIMIT.put(key, JSON.stringify(data), {
          expirationTtl: options.windowSeconds,
        });
        return { allowed: true };
      }
    }

    // New window
    await env.RATE_LIMIT.put(
      key,
      JSON.stringify({ count: 1, windowStart: Date.now() }),
      { expirationTtl: options.windowSeconds }
    );
    return { allowed: true };
  } catch {
    // If KV fails, allow the request (fail open)
    return { allowed: true };
  }
}

/**
 * Verify-specific cooldown: 30 seconds between verification attempts
 */
export async function checkVerifyCooldown(
  userId: string,
  env: Env
): Promise<{ allowed: boolean; retryAfterSeconds?: number }> {
  return checkRateLimit(userId, env, {
    maxRequests: 1,
    windowSeconds: 30,
    prefix: 'verify_cooldown',
  });
}

/**
 * General API rate limit: 60 requests per minute
 */
export async function checkApiRateLimit(
  userId: string,
  env: Env
): Promise<{ allowed: boolean; retryAfterSeconds?: number }> {
  return checkRateLimit(userId, env, {
    maxRequests: 60,
    windowSeconds: 60,
    prefix: 'api_rate',
  });
}
