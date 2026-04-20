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

import kotlin.Unit;

@CapacitorPlugin(name = "PushApp")
public class PushAppPlugin extends Plugin {

    // MARK: - initialize
    @PluginMethod
    public void initialize(PluginCall call) {
        String appId = call.getString("appId");
        if (appId == null) {
            appId = call.getString("identifier");
        }
        if (appId == null) {
            call.reject("appId is required (channel id / App ID; use identifier only as a deprecated alias)");
            return;
        }

        Boolean sandbox = call.getBoolean("sandbox", false);

        // Get context from the plugin
        Context context = getContext();
        if (context == null) {
            call.reject("Context is not available");
            return;
        }

        PushApp.Companion.getInstance().initialize(context, appId, sandbox);

        // Set current activity if available
        Activity activity = getActivity();
        if (activity != null) {
            PushApp.Companion.getInstance().setPageName(activity, "app");
        }

        JSObject ret = new JSObject();
        ret.put("status", "initialized");
        call.resolve(ret);
    }

    /**
     * POST push token to {@code /pushapp/api/device/register}. Call after {@code initialize} when you have the FCM token.
     */
    @PluginMethod
    public void registerPushToken(PluginCall call) {
        String token = call.getString("token");
        if (token == null || token.isEmpty()) {
            call.reject("token is required");
            return;
        }
        PushApp.Companion.getInstance().registerPushToken(token, success -> {
            if (success) {
                JSObject ret = new JSObject();
                ret.put("status", "registered");
                ret.put("success", true);
                call.resolve(ret);
            } else {
                call.reject("Device register failed");
            }
            return Unit.INSTANCE;
        });
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

    // MARK: - save user data (create or update customer profile)
    @PluginMethod
    public void saveUserData(PluginCall call) {
        String code = call.getString("code");
        if (code == null || code.isEmpty()) {
            call.reject("code is required (e.g. userId_deviceId)");
            return;
        }

        JSObject additionalInfo = call.getObject("additionalInfo");
        JSObject cohorts = call.getObject("cohorts");
        if (additionalInfo == null || cohorts == null) {
            call.reject("additionalInfo and cohorts are required and must be objects");
            return;
        }

        java.util.Map<String, Object> additionalInfoMap = new java.util.HashMap<>();
        try {
            for (Iterator<String> it = additionalInfo.keys(); it.hasNext(); ) {
                String key = it.next();
                additionalInfoMap.put(key, additionalInfo.get(key));
            }
        } catch (Exception e) {
            call.reject("Failed to parse additionalInfo: " + e.getMessage());
            return;
        }

        java.util.Map<String, Object> cohortsMap = new java.util.HashMap<>();
        try {
            for (Iterator<String> it = cohorts.keys(); it.hasNext(); ) {
                String key = it.next();
                cohortsMap.put(key, cohorts.get(key));
            }
        } catch (Exception e) {
            call.reject("Failed to parse cohorts: " + e.getMessage());
            return;
        }

        PushApp.Companion.getInstance().createOrUpdateCustomerProfile(
            code,
            additionalInfoMap,
            cohortsMap,
            success -> {
                if (success) {
                    JSObject ret = new JSObject();
                    ret.put("status", "saved_user_data");
                    ret.put("success", true);
                    call.resolve(ret);
                } else {
                    call.reject("Customer profile update failed");
                }
                return Unit.INSTANCE;
            }
        );
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

    // MARK: - track push notification event
    @PluginMethod
    public void trackPushNotificationEvent(PluginCall call) {
        String token = call.getString("token");
        if (token == null || token.isEmpty()) {
            call.reject("token is required");
            return;
        }

        String event = call.getString("event");
        if (event == null || event.isEmpty()) {
            call.reject("event is required");
            return;
        }

        String ctaId = call.getString("ctaId");

        PushApp.Companion.getInstance().trackNotificationEvent(token, event, ctaId);

        JSObject ret = new JSObject();
        ret.put("success", true);
        call.resolve(ret);
    }
}
