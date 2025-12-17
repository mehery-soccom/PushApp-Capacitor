package com.mehery.pushapp;

import android.app.Activity;
import android.content.Context;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.util.Iterator;
import java.util.Map;

@CapacitorPlugin(name = "PushApp")
public class PushAppPlugin extends Plugin {

    // MARK: - initialize
    @PluginMethod
    public void initialize(PluginCall call) {
        String identifier = call.getString("identifier");
        if (identifier == null) {
            call.reject("identifier is required");
            return;
        }

        Boolean sandbox = call.getBoolean("sandbox", false);

        // Get context from the plugin
        Context context = getContext();
        if (context == null) {
            call.reject("Context is not available");
            return;
        }

        PushApp.Companion.getInstance().initialize(context, identifier, sandbox);

        // Set current activity if available
        Activity activity = getActivity();
        if (activity != null) {
            PushApp.Companion.getInstance().setPageName(activity, "app");
        }

        JSObject ret = new JSObject();
        ret.put("status", "initialized");
        call.resolve(ret);
    }

    // MARK: - login
    @PluginMethod
    public void login(PluginCall call) {
        String userId = call.getString("userId");
        if (userId == null) {
            call.reject("userId is required");
            return;
        }

        PushApp.Companion.getInstance().login(userId);

        JSObject ret = new JSObject();
        ret.put("status", "logged_in");
        call.resolve(ret);
    }

    // MARK: - get device headers
    @PluginMethod
    public void getDeviceHeaders(PluginCall call) {
        Map<String, String> headersMap = PushApp.Companion.getInstance().getDeviceHeaders();
        JSObject headers = new JSObject();
        for (Map.Entry<String, String> entry : headersMap.entrySet()) {
            headers.put(entry.getKey(), entry.getValue());
        }
        call.resolve(headers);
    }

    // MARK: - send event
    @PluginMethod
    public void sendEvent(PluginCall call) {
        String eventName = call.getString("eventName");
        if (eventName == null) {
            call.reject("eventName is required");
            return;
        }

        JSObject eventData = call.getObject("eventData");
        if (eventData == null) {
            call.reject("eventData is required and must be an object");
            return;
        }

        // Update current activity reference if available (without sending page_open event)
        Activity activity = getActivity();
        if (activity != null) {
            PushApp.Companion.getInstance().setCurrentActivity(activity);
        }

        // Convert JSObject to Map<String, Object>
        java.util.Map<String, Object> eventDataMap = new java.util.HashMap<>();
        try {
            for (Iterator<String> it = eventData.keys(); it.hasNext(); ) {
                String key = it.next();
                eventDataMap.put(key, eventData.get(key));
            }
        } catch (Exception e) {
            call.reject("Failed to parse eventData: " + e.getMessage());
            return;
        }

        PushApp.Companion.getInstance().sendEvent(eventName, eventDataMap);

        JSObject ret = new JSObject();
        ret.put("status", "event_sent");
        call.resolve(ret);
    }

    // MARK: - set page name
    @PluginMethod
    public void setPageName(PluginCall call) {
        String pageName = call.getString("pageName");
        if (pageName == null) {
            call.reject("pageName is required");
            return;
        }

        Activity activity = getActivity();
        PushApp.Companion.getInstance().setPageName(activity, pageName);

        JSObject ret = new JSObject();
        ret.put("status", "page_set");
        call.resolve(ret);
    }

    // MARK: - register placeholder
    @PluginMethod
    public void registerPlaceholder(PluginCall call) {
        String placeholderId = call.getString("placeholderId");
        if (placeholderId == null || placeholderId.isEmpty()) {
            call.reject("placeholderId is required");
            return;
        }

        Double x = call.getDouble("x");
        Double y = call.getDouble("y");
        Double width = call.getDouble("width");
        Double height = call.getDouble("height");

        if (x == null || y == null || width == null || height == null) {
            call.reject("x, y, width, and height are required");
            return;
        }

        Activity activity = getActivity();
        if (activity == null) {
            call.reject("Activity is not available");
            return;
        }

        // Create and add placeholder view on UI thread
        // Coordinates come from JavaScript as pixels, we'll use them directly
        activity.runOnUiThread(() -> {
            try {
                com.mehery.pushapp.PlaceholderViewManager.INSTANCE.createPlaceholderView(
                    activity,
                    placeholderId,
                    x.intValue(),  // Already in pixels from JS
                    y.intValue(),  // Already in pixels from JS
                    width.intValue(),  // Already in pixels from JS
                    height.intValue()  // Already in pixels from JS
                );

                JSObject ret = new JSObject();
                ret.put("status", "placeholder_registration_initiated");
                call.resolve(ret);
            } catch (Exception e) {
                call.reject("Failed to register placeholder: " + e.getMessage());
            }
        });
    }

    // MARK: - unregister placeholder
    @PluginMethod
    public void unregisterPlaceholder(PluginCall call) {
        String placeholderId = call.getString("placeholderId");
        if (placeholderId == null || placeholderId.isEmpty()) {
            call.reject("placeholderId is required");
            return;
        }

        Activity activity = getActivity();
        if (activity == null) {
            call.reject("Activity is not available");
            return;
        }

        activity.runOnUiThread(() -> {
            try {
                com.mehery.pushapp.PlaceholderViewManager.INSTANCE.removePlaceholderView(activity, placeholderId);

                JSObject ret = new JSObject();
                ret.put("status", "placeholder_unregistered");
                call.resolve(ret);
            } catch (Exception e) {
                call.reject("Failed to unregister placeholder: " + e.getMessage());
            }
        });
    }

    // MARK: - register tooltip target
    @PluginMethod
    public void registerTooltipTarget(PluginCall call) {
        String targetId = call.getString("targetId");
        if (targetId == null || targetId.isEmpty()) {
            call.reject("targetId is required");
            return;
        }

        Double x = call.getDouble("x");
        Double y = call.getDouble("y");
        Double width = call.getDouble("width");
        Double height = call.getDouble("height");

        if (x == null || y == null || width == null || height == null) {
            call.reject("x, y, width, and height are required");
            return;
        }

        Activity activity = getActivity();
        if (activity == null) {
            call.reject("Activity is not available");
            return;
        }

        activity.runOnUiThread(() -> {
            try {
                com.mehery.pushapp.PlaceholderViewManager.INSTANCE.registerTooltipTarget(
                    activity,
                    targetId,
                    x.intValue(),
                    y.intValue(),
                    width.intValue(),
                    height.intValue()
                );

                JSObject ret = new JSObject();
                ret.put("status", "tooltip_target_registration_initiated");
                call.resolve(ret);
            } catch (Exception e) {
                call.reject("Failed to register tooltip target: " + e.getMessage());
            }
        });
    }

    // MARK: - unregister tooltip target
    @PluginMethod
    public void unregisterTooltipTarget(PluginCall call) {
        String targetId = call.getString("targetId");
        if (targetId == null || targetId.isEmpty()) {
            call.reject("targetId is required");
            return;
        }

        Activity activity = getActivity();
        if (activity == null) {
            call.reject("Activity is not available");
            return;
        }

        activity.runOnUiThread(() -> {
            try {
                com.mehery.pushapp.PlaceholderViewManager.INSTANCE.unregisterTooltipTarget(activity, targetId);

                JSObject ret = new JSObject();
                ret.put("status", "tooltip_target_unregistered");
                call.resolve(ret);
            } catch (Exception e) {
                call.reject("Failed to unregister tooltip target: " + e.getMessage());
            }
        });
    }
}
