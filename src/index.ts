import { registerPlugin } from '@capacitor/core';
import type { PushAppPlugin } from './definitions';

const PushApp = registerPlugin<PushAppPlugin>('PushApp', {
  web: () => import('./web').then((m) => new m.PushAppWeb()),
});

export * from './definitions';
export { PushApp };