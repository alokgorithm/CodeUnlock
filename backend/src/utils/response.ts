// ============================================================
// Standardized API Response Helpers
// ============================================================
import { ApiSuccess, ApiError } from '../types';

export function success<T>(data: T, status = 200): Response {
  const body: ApiSuccess<T> = { success: true, data };
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

export function error(code: string, message: string, status = 400, details?: unknown): Response {
  const body: ApiError = {
    success: false,
    error: { code, message, ...(details !== undefined && { details }) },
  };
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

export function notFound(message = 'Resource not found'): Response {
  return error('NOT_FOUND', message, 404);
}

export function unauthorized(message = 'Authentication required'): Response {
  return error('UNAUTHORIZED', message, 401);
}

export function forbidden(message = 'Access denied'): Response {
  return error('FORBIDDEN', message, 403);
}

export function tooManyRequests(message = 'Rate limit exceeded'): Response {
  return error('RATE_LIMITED', message, 429);
}

export function serverError(message = 'Internal server error'): Response {
  return error('INTERNAL_ERROR', message, 500);
}
