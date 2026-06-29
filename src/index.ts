import { Capacitor, registerPlugin } from '@capacitor/core';

import type { PushAppPlugin } from './definitions';
import { startPlaceholderSync, stopPlaceholderSync } from './placeholder-sync';

const PushAppNative = registerPlugin<PushAppPlugin>('PushApp', {
  web: () => import('./web').then((m) => new m.PushAppWeb()),
});

// Capacitor's registerPlugin returns a Proxy — spreading it drops all plugin methods.
// Use a Proxy so native methods (initialize, register, login, …) stay reachable while
// placeholder calls run through DOM sync on native platforms.
const PushApp: PushAppPlugin = new Proxy(PushAppNative, {
  get(target, prop, receiver) {
    if (prop === 'registerPlaceholder') {
      return (options: Parameters<PushAppPlugin['registerPlaceholder']>[0]) => {
        if (Capacitor.isNativePlatform()) {
          return startPlaceholderSync(PushAppNative, options);
        }
        return target.registerPlaceholder(options);
      };
    }
    if (prop === 'unregisterPlaceholder') {
      return (options: Parameters<PushAppPlugin['unregisterPlaceholder']>[0]) => {
        if (Capacitor.isNativePlatform()) {
          return stopPlaceholderSync(PushAppNative, options.placeholderId);
        }
        return target.unregisterPlaceholder(options);
      };
    }
    return Reflect.get(target, prop, receiver);
  },
});

export * from './definitions';
export * from './errors';
export { PushApp };
