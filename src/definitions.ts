// Example: In your plugin's definitions.ts or index.d.ts file

/**
 * Defines the common coordinate and dimension options for UI element registration.
 */
export interface UIOptions {
  x: number; // Must be required when calling from web
  y: number; // Must be required when calling from web
  width: number;
  height: number;
}

export interface PushAppPlugin {
  // Existing Core Methods
  /**
   * Initialize the SDK. Pass your **channel id** as **App ID** (`appId`).
   * The tenant subdomain is derived from the substring before the first `_` (e.g. `demo` from `demo_1763369170735`).
   * Use `identifier` only for backward compatibility; it is the same value as `appId`.
   */
  initialize(
    options: { appId: string; sandbox?: boolean } | { identifier: string; sandbox?: boolean },
  ): Promise<{ status: string }>;
  login(options: { userId: string }): Promise<{ status: string }>;
  /** Create or update customer profile (PUT). Call after login with code = userId_deviceId. */
  saveUserData(options: { code: string; additionalInfo: Record<string, unknown>; cohorts: Record<string, unknown> }): Promise<{ status: string; success: boolean }>;
  getDeviceHeaders(): Promise<{ [key: string]: string }>;
  sendEvent(options: { eventName: string; eventData: { [key: string]: any } }): Promise<{ status: string }>;
  setPageName(options: { pageName: string }): Promise<{ status: string }>;
  
  // Inline Placeholder Methods
  registerPlaceholder(options: { placeholderId: string } & UIOptions): Promise<{ status: string }>;
  unregisterPlaceholder(options: { placeholderId: string }): Promise<{ status: string }>; // Assuming you added this unregister method

  // ⭐️ NEW: Tooltip Target Methods
  /** Registers a UI element as an anchor target for native tooltips/popovers. */
  registerTooltipTarget(options: { targetId: string } & UIOptions): Promise<{ status: string }>;
  /** Unregisters a tooltip anchor target. */
  unregisterTooltipTarget(options: { targetId: string }): Promise<{ status: string }>;

  /** Tracks a push notification event (e.g. "opened" or "cta"). Use when user opens a notification or taps a CTA. */
  trackPushNotificationEvent(options: { token: string; event: string; ctaId?: string }): Promise<{ success: boolean }>;
}