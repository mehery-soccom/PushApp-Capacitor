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
  initialize(options: { identifier: string; sandbox?: boolean }): Promise<{ status: string }>;
  login(options: { userId: string }): Promise<{ status: string }>;
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
}