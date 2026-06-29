import type { PushAppPlugin } from './definitions';

type PlaceholderSyncEntry = {
  placeholderId: string;
  elementId: string;
  clipTopSelector: string;
  registered: boolean;
  rafId: number;
  resizeObserver: { observe(target: Element): void; disconnect(): void } | null;
};

const syncEntries = new Map<string, PlaceholderSyncEntry>();

let listenersAttached = false;
let onScrollHandler: (() => void) | null = null;
let onResizeHandler: (() => void) | null = null;
let onViewportScrollHandler: (() => void) | null = null;
let onViewportResizeHandler: (() => void) | null = null;

export interface PlaceholderSyncOptions {
  placeholderId: string;
  elementId?: string;
  clipTopSelector?: string;
}

function getClipTop(selector: string): number {
  let bottom = 0;
  document.querySelectorAll(selector).forEach((el) => {
    const rect = el.getBoundingClientRect();
    if (rect.height > 0 && rect.bottom > bottom) {
      bottom = rect.bottom;
    }
  });
  return Math.ceil(bottom);
}

function getElementRect(elementId: string): DOMRect | null {
  const el = document.getElementById(elementId);
  if (!el) {
    return null;
  }
  const rect = el.getBoundingClientRect();
  if (rect.width <= 0 || rect.height <= 0) {
    return null;
  }
  return rect;
}

async function waitForLayout(elementId: string, maxAttempts = 20): Promise<HTMLElement | null> {
  for (let attempt = 0; attempt < maxAttempts; attempt++) {
    const el = document.getElementById(elementId);
    if (el) {
      const rect = el.getBoundingClientRect();
      if (rect.width > 0 && rect.height > 0) {
        return el;
      }
    }
    await new Promise((resolve) => setTimeout(resolve, 50));
  }
  return document.getElementById(elementId);
}

async function syncEntry(entry: PlaceholderSyncEntry, native: PushAppPlugin, register: boolean): Promise<void> {
  const rect = getElementRect(entry.elementId);
  if (!rect) {
    return;
  }

  const options = {
    placeholderId: entry.placeholderId,
    x: Math.round(rect.left),
    y: Math.round(rect.top),
    width: Math.round(rect.width),
    height: Math.round(rect.height),
    clipTop: getClipTop(entry.clipTopSelector),
  };

  if (register || !entry.registered) {
    await native.registerPlaceholder(options);
    entry.registered = true;
  } else {
    await native.updatePlaceholder(options);
  }
}

function scheduleSync(entry: PlaceholderSyncEntry, native: PushAppPlugin): void {
  if (entry.rafId) {
    cancelAnimationFrame(entry.rafId);
  }
  entry.rafId = requestAnimationFrame(() => {
    entry.rafId = 0;
    syncEntry(entry, native, false).catch(() => undefined);
  });
}

function scheduleAllSync(native: PushAppPlugin): void {
  syncEntries.forEach((entry) => scheduleSync(entry, native));
}

function attachGlobalListeners(native: PushAppPlugin): void {
  if (listenersAttached) {
    return;
  }
  listenersAttached = true;

  onScrollHandler = () => scheduleAllSync(native);
  onResizeHandler = () => scheduleAllSync(native);
  onViewportScrollHandler = () => scheduleAllSync(native);
  onViewportResizeHandler = () => scheduleAllSync(native);

  window.addEventListener('scroll', onScrollHandler, true);
  window.addEventListener('resize', onResizeHandler);
  window.visualViewport?.addEventListener('scroll', onViewportScrollHandler);
  window.visualViewport?.addEventListener('resize', onViewportResizeHandler);
}

function detachGlobalListeners(): void {
  if (!listenersAttached) {
    return;
  }
  listenersAttached = false;

  if (onScrollHandler) {
    window.removeEventListener('scroll', onScrollHandler, true);
  }
  if (onResizeHandler) {
    window.removeEventListener('resize', onResizeHandler);
  }
  if (onViewportScrollHandler) {
    window.visualViewport?.removeEventListener('scroll', onViewportScrollHandler);
  }
  if (onViewportResizeHandler) {
    window.visualViewport?.removeEventListener('resize', onViewportResizeHandler);
  }

  onScrollHandler = null;
  onResizeHandler = null;
  onViewportScrollHandler = null;
  onViewportResizeHandler = null;
}

function attachResizeObserver(entry: PlaceholderSyncEntry, element: HTMLElement, native: PushAppPlugin): void {
  const ResizeObserverCtor = (
    window as Window & {
      ResizeObserver?: new (callback: () => void) => { observe(target: Element): void; disconnect(): void };
    }
  ).ResizeObserver;

  if (!ResizeObserverCtor) {
    return;
  }

  entry.resizeObserver?.disconnect();
  entry.resizeObserver = new ResizeObserverCtor(() => scheduleSync(entry, native));
  entry.resizeObserver.observe(element);
}

function teardownEntry(entry: PlaceholderSyncEntry): void {
  if (entry.rafId) {
    cancelAnimationFrame(entry.rafId);
  }
  entry.resizeObserver?.disconnect();
}

export async function startPlaceholderSync(
  native: PushAppPlugin,
  options: PlaceholderSyncOptions,
): Promise<{ status: string }> {
  const elementId = options.elementId ?? options.placeholderId;
  const clipTopSelector = options.clipTopSelector ?? 'ion-header';

  const element = await waitForLayout(elementId);
  if (!element) {
    throw new Error(`Placeholder element #${elementId} not found`);
  }

  const existing = syncEntries.get(options.placeholderId);
  if (existing) {
    teardownEntry(existing);
  }

  const entry: PlaceholderSyncEntry = {
    placeholderId: options.placeholderId,
    elementId,
    clipTopSelector,
    registered: false,
    rafId: 0,
    resizeObserver: null,
  };

  syncEntries.set(options.placeholderId, entry);
  attachGlobalListeners(native);
  attachResizeObserver(entry, element, native);
  await syncEntry(entry, native, true);

  return { status: 'placeholder_registration_initiated' };
}

export async function stopPlaceholderSync(native: PushAppPlugin, placeholderId: string): Promise<{ status: string }> {
  const entry = syncEntries.get(placeholderId);
  if (entry) {
    teardownEntry(entry);
    syncEntries.delete(placeholderId);
  }

  if (syncEntries.size === 0) {
    detachGlobalListeners();
  }

  return native.unregisterPlaceholder({ placeholderId });
}
