import { WebPlugin } from '@capacitor/core';

import type { PushAppPlugin } from './definitions';

export class PushAppWeb extends WebPlugin implements PushAppPlugin {
  async echo(options: { value: string }): Promise<{ value: string }> {
    console.log('ECHO', options);
    return options;
  }
}
