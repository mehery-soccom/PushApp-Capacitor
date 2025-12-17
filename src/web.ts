import { WebPlugin } from '@capacitor/core';
import type { PushAppPlugin, UIOptions } from './definitions'; // ⭐️ Ensure UIOptions is imported if used in definitions

// Use a combined type for placeholder/target registration parameters
interface TargetRegistrationOptions extends UIOptions {
    targetId: string;
}

// Define combined placeholder registration parameters (if you didn't use UIOptions in definitions)
interface PlaceholderRegistrationOptions {
    placeholderId: string;
    x?: number; 
    y?: number; 
    width?: number; 
    height?: number;
}


export class PushAppWeb extends WebPlugin implements PushAppPlugin {
  async initialize(options: { identifier: string; sandbox?: boolean }): Promise<{ status: string }> {
    console.log('Web implementation - initialize', options);
    return { status: 'web_not_supported' };
  }

  async login(options: { userId: string }): Promise<{ status: string }> {
    console.log('Web implementation - login', options);
    return { status: 'web_not_supported' };
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

  async registerPlaceholder(options: PlaceholderRegistrationOptions): Promise<{ status: string }> {
    console.log('Web implementation - registerPlaceholder', options);
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
}