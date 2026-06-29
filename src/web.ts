import { WebPlugin } from '@capacitor/core';

import type { PushAppPlugin, PlaceholderRegisterOptions, UIOptions } from './definitions';

// Use a combined type for placeholder/target registration parameters
interface TargetRegistrationOptions extends UIOptions {
  targetId: string;
}

export class PushAppWeb extends WebPlugin implements PushAppPlugin {
  async initialize(
    options: { appId: string; sandbox?: boolean } | { identifier: string; sandbox?: boolean },
  ): Promise<{ status: string }> {
    console.log('Web implementation - initialize', options);
    return { status: 'web_not_supported' };
  }

  async register(options: {
    apnsToken?: string;
    fcmToken?: string;
    token?: string;
  }): Promise<{ status: string; success: boolean }> {
    console.log('Web implementation - register', options);
    return { status: 'web_not_supported', success: false };
  }

  async login(options: { userId: string }): Promise<{ status: string }> {
    console.log('Web implementation - login', options);
    return { status: 'web_not_supported' };
  }

  async logout(): Promise<{ status: string }> {
    console.log('Web implementation - logout');
    return { status: 'web_not_supported' };
  }

  async saveUserData(options: {
    code: string;
    additionalInfo: Record<string, unknown>;
    cohorts: Record<string, unknown>;
  }): Promise<{ status: string; success: boolean }> {
    console.log('Web implementation - saveUserData', options);
    return { status: 'web_not_supported', success: false };
  }

  async getDeviceHeaders(): Promise<{ [key: string]: string }> {
    console.log('Web implementation - getDeviceHeaders');
    return {};
  }

  async sendEvent(options: { eventName: string; eventData: { [key: string]: any } }): Promise<{ status: string }> {
    console.log('Web implementation - sendEvent', options);
    return { status: 'web_not_supported' };
  }

  async setPageName(options: { pageName: string }): Promise<{ status: string }> {
    console.log('Web implementation - setPageName', options);
    return { status: 'web_not_supported' };
  }

  async registerPlaceholder(options: PlaceholderRegisterOptions): Promise<{ status: string }> {
    console.log('Web implementation - registerPlaceholder', options);
    return { status: 'web_not_supported' };
  }

  async updatePlaceholder(options: PlaceholderRegisterOptions & UIOptions): Promise<{ status: string }> {
    console.log('Web implementation - updatePlaceholder', options);
    return { status: 'web_not_supported' };
  }

  async unregisterPlaceholder(options: { placeholderId: string }): Promise<{ status: string }> {
    console.log('Web implementation - unregisterPlaceholder', options);
    return { status: 'web_not_supported' };
  }

  // ⭐️ NEW: Implement registerTooltipTarget
  async registerTooltipTarget(options: TargetRegistrationOptions): Promise<{ status: string }> {
    console.log('Web implementation - registerTooltipTarget', options);
    // Tooltips are native elements and do not render on the web
    return { status: 'web_not_supported' };
  }

  // ⭐️ NEW: Implement unregisterTooltipTarget
  async unregisterTooltipTarget(options: { targetId: string }): Promise<{ status: string }> {
    console.log('Web implementation - unregisterTooltipTarget', options);
    // Tooltips are native elements and do not render on the web
    return { status: 'web_not_supported' };
  }

  async trackPushNotificationEvent(options: {
    token: string;
    event: string;
    ctaId?: string;
  }): Promise<{ success: boolean }> {
    console.log('Web implementation - trackPushNotificationEvent', options);
    return { success: false };
  }
}
