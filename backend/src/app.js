import crypto from 'node:crypto';
import express from 'express';
import { rateLimit } from 'express-rate-limit';
import { ApiError } from './errors.js';
import { RailCoreClient } from './provider/railCoreClient.js';
import { createApiRouter } from './routes/api.js';

export function createApp({ client = new RailCoreClient(), rateLimitMax = Number(process.env.RATE_LIMIT_MAX || 60),
  rateLimitWindowMs = Number(process.env.RATE_LIMIT_WINDOW_MS || 900000) } = {}) {
  const app = express();
  app.disable('x-powered-by');
  app.set('trust proxy', 1);
  app.use((req, res, next) => {
    req.id = crypto.randomUUID();
    res.setHeader('X-Request-Id', req.id);
    next();
  });

  app.get('/health', (req, res) => res.json({ success: true, data: { status: 'ok' }, meta: { requestId: req.id } }));
  app.use('/api', rateLimit({
    windowMs: rateLimitWindowMs,
    limit: rateLimitMax,
    standardHeaders: 'draft-8',
    legacyHeaders: false,
    handler: (req, res) => sendError(res, req, new ApiError(429, 'RATE_LIMITED', 'Too many requests. Please try again later.')),
  }));
  app.use('/api', createApiRouter(client));
  app.use((req, res) => sendError(res, req, new ApiError(404, 'NOT_FOUND', 'The requested endpoint was not found.')));
  app.use((error, req, res, _next) => {
    if (res.headersSent) return;
    if (error instanceof ApiError || error?.status && error?.code) return sendError(res, req, error);
    return sendError(res, req, new ApiError(500, 'INTERNAL_ERROR', 'The server could not complete the request.'));
  });
  return app;
}

function sendError(res, req, error) {
  return res.status(error.status || 500).json({
    success: false,
    error: { code: error.code || 'INTERNAL_ERROR', message: error.message || 'The server could not complete the request.' },
    meta: { requestId: req.id },
  });
}
