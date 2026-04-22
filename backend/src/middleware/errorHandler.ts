// ============================================================
// Global Error Handler
// ============================================================
import { serverError } from '../utils/response';

/**
 * Wraps a route handler with try/catch and returns a standardized error response.
 */
export function withErrorHandler(
  handler: (request: Request, ...args: unknown[]) => Promise<Response>
) {
  return async (request: Request, ...args: unknown[]): Promise<Response> => {
    try {
      return await handler(request, ...args);
    } catch (err) {
      console.error('Unhandled error:', err);
      const message = err instanceof Error ? err.message : 'Unknown error';
      return serverError(message);
    }
  };
}

/**
 * Adds CORS headers to a response.
 */
export function withCors(response: Response): Response {
  const headers = new Headers(response.headers);
  headers.set('Access-Control-Allow-Origin', '*');
  headers.set('Access-Control-Allow-Methods', 'GET, POST, PUT, DELETE, OPTIONS');
  headers.set('Access-Control-Allow-Headers', 'Content-Type, Authorization');
  headers.set('Access-Control-Max-Age', '86400');
  return new Response(response.body, {
    status: response.status,
    statusText: response.statusText,
    headers,
  });
}
