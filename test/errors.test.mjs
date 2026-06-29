import { test } from 'node:test';
import assert from 'node:assert/strict';
import {
  PushAppErrorCode,
  getPushAppErrorCode,
  isPushAppError,
} from '../dist/esm/errors.js';

test('PushAppErrorCode exposes stable string constants', () => {
  assert.equal(PushAppErrorCode.NOT_INITIALIZED, 'NOT_INITIALIZED');
  assert.equal(PushAppErrorCode.REGISTER_FAILED, 'REGISTER_FAILED');
  assert.equal(PushAppErrorCode.LOGOUT_FAILED, 'LOGOUT_FAILED');
});

test('isPushAppError identifies Capacitor rejection shape', () => {
  assert.equal(isPushAppError({ code: 'EMPTY_TOKEN', message: 'token required' }), true);
  assert.equal(isPushAppError({ message: 'no code' }), false);
  assert.equal(isPushAppError(null), false);
  assert.equal(isPushAppError('string'), false);
});

test('getPushAppErrorCode returns known codes only', () => {
  assert.equal(getPushAppErrorCode({ code: 'LOGIN_FAILED' }), 'LOGIN_FAILED');
  assert.equal(getPushAppErrorCode({ code: 'UNKNOWN_CODE' }), undefined);
  assert.equal(getPushAppErrorCode(new Error('fail')), undefined);
});

test('lifecycle-related codes exist for consumer retry UX', () => {
  const lifecycleCodes = [
    'NOT_INITIALIZED',
    'REGISTER_REQUIRED',
    'REGISTER_FAILED',
    'EMPTY_TOKEN',
    'LOGIN_FAILED',
    'LOGOUT_FAILED',
  ];
  for (const code of lifecycleCodes) {
    assert.equal(PushAppErrorCode[code], code);
  }
});
