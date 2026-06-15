import { Capacitor, registerPlugin } from '@capacitor/core';
import type { PushAppPlugin } from './definitions';
import { startPlaceholderSync, stopPlaceholderSync } from './placeholder-sync';

const PushAppNative = registerPlugin<PushAppPlugin>('PushApp', {
  web: () => import('./web').then((m) => new m.PushAppWeb()),
});

const PushApp: PushAppPlugin = {
  ...PushAppNative,
  registerPlaceholder(options) {
    if (Capacitor.isNativePlatform()) {
      return startPlaceholderSync(PushAppNative, options);
    }
    return PushAppNative.registerPlaceholder(options);
  },
  unregisterPlaceholder(options) {
    if (Capacitor.isNativePlatform()) {
      return stopPlaceholderSync(PushAppNative, options.placeholderId);
    }
    return PushAppNative.unregisterPlaceholder(options);
  },
};

export * from './definitions';
export { PushApp };
