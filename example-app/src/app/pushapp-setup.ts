import { PushApp } from 'pushapp-ionic';
import { Capacitor } from '@capacitor/core';

const REGISTER_RETRY_MS = 1000;
const REGISTER_MAX_ATTEMPTS = 8;

/**
 * Call after PushApp.initialize(). Posts to /pushapp/api/device/register.
 * Native SDK skips the API when the device is already registered with the same token.
 * On Android, retries until the native FCM service has cached a token (first launch only).
 */
export async function registerPushAppDevice(fcmToken?: string): Promise<void> {
  let lastError: unknown;

  for (let attempt = 1; attempt <= REGISTER_MAX_ATTEMPTS; attempt++) {
    try {
      const res = await PushApp.register({ fcmToken: fcmToken ?? '' });
      console.log('PushApp register success:', res);
      return;
    } catch (err) {
      lastError = err;
      console.warn(`PushApp register attempt ${attempt}/${REGISTER_MAX_ATTEMPTS} failed:`, err);
      if (attempt < REGISTER_MAX_ATTEMPTS) {
        await new Promise((resolve) => setTimeout(resolve, REGISTER_RETRY_MS));
      }
    }
  }

  throw lastError ?? new Error('PushApp.register() failed');
}

export async function initializeAndRegisterPushApp(options: {
  appId: string;
  sandbox?: boolean;
  slackWebhookUrl?: string;
}): Promise<void> {
  await PushApp.initialize(options);
  if (Capacitor.getPlatform() === 'android' || Capacitor.getPlatform() === 'ios') {
    await registerPushAppDevice();
  }
}
