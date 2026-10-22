export class ApiError extends Error {
  constructor(status, code, message) {
    super(message);
    this.status = status;
    this.code = code;
  }
}

export class ProviderError extends Error {
  constructor(status, code, message, options = {}) {
    super(message, options);
    this.status = status;
    this.code = code;
  }
}

export function mapProviderStatus(status) {
  if (status === 401) {
    return new ProviderError(502, 'PROVIDER_AUTH_ERROR', 'The train data provider rejected its credentials.');
  }
  if (status === 403) {
    return new ProviderError(502, 'PROVIDER_ACCESS_DENIED', 'The provider key lacks access to this endpoint or plan.');
  }
  if (status === 402) {
    return new ProviderError(402, 'PROVIDER_CREDITS_EXHAUSTED', 'The provider account has insufficient credits for this request.');
  }
  if (status === 404) {
    return new ProviderError(404, 'PROVIDER_NOT_FOUND', 'The requested train data was not found.');
  }
  if (status === 429) {
    return new ProviderError(503, 'PROVIDER_RATE_LIMITED', 'The train data provider rate limit was reached.');
  }
  if (status === 410) {
    return new ProviderError(502, 'PROVIDER_VERSION_UNAVAILABLE', 'The provider API version is no longer available.');
  }
  if (status === 504) {
    return new ProviderError(504, 'PROVIDER_TIMEOUT', 'The train data provider did not respond in time.');
  }
  if (status >= 500) {
    return new ProviderError(502, 'PROVIDER_UNAVAILABLE', 'The train data provider is temporarily unavailable.');
  }
  return new ProviderError(502, 'PROVIDER_REQUEST_FAILED', 'The train data provider could not complete the request.');
}
