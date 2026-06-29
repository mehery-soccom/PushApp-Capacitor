/**
 * Stable error codes returned by native plugin methods via Capacitor `call.reject(message, code)`.
 * Inspect `error.code` in catch blocks (see `isPushAppError`).
 */
export const PushAppErrorCode = {
  NOT_INITIALIZED: 'NOT_INITIALIZED',
  INVALID_APP_ID: 'INVALID_APP_ID',
  APP_ID_REQUIRED: 'APP_ID_REQUIRED',
  CONTEXT_UNAVAILABLE: 'CONTEXT_UNAVAILABLE',
  REGISTER_REQUIRED: 'REGISTER_REQUIRED',
  EMPTY_TOKEN: 'EMPTY_TOKEN',
  REGISTER_FAILED: 'REGISTER_FAILED',
  EMPTY_USER_ID: 'EMPTY_USER_ID',
  LOGIN_FAILED: 'LOGIN_FAILED',
  LOGOUT_FAILED: 'LOGOUT_FAILED',
  CUSTOMER_PROFILE_FAILED: 'CUSTOMER_PROFILE_FAILED',
  MISSING_CODE: 'MISSING_CODE',
  MISSING_PROFILE_DATA: 'MISSING_PROFILE_DATA',
  INVALID_PROFILE_DATA: 'INVALID_PROFILE_DATA',
  MISSING_EVENT_NAME: 'MISSING_EVENT_NAME',
  MISSING_EVENT_DATA: 'MISSING_EVENT_DATA',
  INVALID_EVENT_DATA: 'INVALID_EVENT_DATA',
  MISSING_PAGE_NAME: 'MISSING_PAGE_NAME',
  MISSING_PLACEHOLDER_ID: 'MISSING_PLACEHOLDER_ID',
  MISSING_UI_BOUNDS: 'MISSING_UI_BOUNDS',
  MISSING_TARGET_ID: 'MISSING_TARGET_ID',
  ACTIVITY_UNAVAILABLE: 'ACTIVITY_UNAVAILABLE',
  PLACEHOLDER_FAILED: 'PLACEHOLDER_FAILED',
  TOOLTIP_FAILED: 'TOOLTIP_FAILED',
  MISSING_NOTIFICATION_TOKEN: 'MISSING_NOTIFICATION_TOKEN',
  MISSING_NOTIFICATION_EVENT: 'MISSING_NOTIFICATION_EVENT',
  WEB_NOT_SUPPORTED: 'WEB_NOT_SUPPORTED',
} as const;

export type PushAppErrorCode = (typeof PushAppErrorCode)[keyof typeof PushAppErrorCode];

/** Capacitor plugin rejection shape (see `@capacitor/core`). */
export interface PushAppError {
  message?: string;
  code?: PushAppErrorCode | string;
}

export function isPushAppError(error: unknown): error is PushAppError {
  if (typeof error !== 'object' || error === null || !('code' in error)) {
    return false;
  }
  const code = (error as PushAppError).code;
  return typeof code === 'string' && code.length > 0;
}

export function getPushAppErrorCode(error: unknown): PushAppErrorCode | undefined {
  if (!isPushAppError(error)) {
    return undefined;
  }
  const code = error.code;
  if (typeof code !== 'string') {
    return undefined;
  }
  return Object.values(PushAppErrorCode).includes(code as PushAppErrorCode) ? (code as PushAppErrorCode) : undefined;
}
