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

    private static void rejectWithCode(PluginCall call, String code) {
        call.reject(PushAppErrorCodes.message(code), code);
    }

    // MARK: - initialize
    @PluginMethod
    public void initialize(PluginCall call) {
        String appId = call.getString("appId");
        if (appId == null) {
            appId = call.getString("identifier");
        }
        if (appId == null) {
            rejectWithCode(call, PushAppErrorCodes.APP_ID_REQUIRED);
            return;
        }

        Boolean sandbox = call.getBoolean("sandbox", false);
        Boolean debugMode = call.getBoolean("debugMode", false);
        String slackWebhookUrl = call.getString("slackWebhookUrl");

        // Get context from the plugin
        Context context = getContext();
        if (context == null) {
            rejectWithCode(call, PushAppErrorCodes.CONTEXT_UNAVAILABLE);
            return;
        }

        boolean initialized = PushApp.Companion.getInstance().initialize(context, appId, sandbox, slackWebhookUrl, debugMode);
        if (!initialized) {
            rejectWithCode(call, PushAppErrorCodes.INVALID_APP_ID);
            return;
        }

        // Set current activity if available
        Activity activity = getActivity();
        if (activity != null) {
            PushApp.Companion.getInstance().setPageName(activity, "app");
            PushApp.Companion.getInstance().ensureNotificationPermission(activity);
        }

        JSObject ret = new JSObject();
        ret.put("status", "initialized");
        call.resolve(ret);
    }

    /**
     * POST push token to {@code /pushapp/api/device/register}. Call after {@code initialize} when you have the FCM token.
     */
    @PluginMethod
    public void register(PluginCall call) {
        // Exposed API accepts both fields; Android uses FCM token as `token` in backend payload.
        String fcmToken = call.getString("fcmToken");
        if (fcmToken == null || fcmToken.isEmpty()) {
            // Backward-compatible fallback
            fcmToken = call.getString("token");
        }
        // Android: allow empty token — native layer uses FCM token cached by Firebase service
        if (fcmToken == null) {
            fcmToken = "";
        }
        if (!PushApp.Companion.getInstance().isInitialized()) {
            rejectWithCode(call, PushAppErrorCodes.NOT_INITIALIZED);
            return;
        }
        PushApp.Companion.getInstance().register(fcmToken, (success) -> {
            if (success) {
                JSObject ret = new JSObject();
                ret.put("status", "registered");
                ret.put("success", true);
                call.resolve(ret);
            } else {
                rejectWithCode(call, PushAppErrorCodes.REGISTER_FAILED);
            }
            return Unit.INSTANCE;
        });
    }

    // MARK: - login
    @PluginMethod
    public void login(PluginCall call) {
        String userId = call.getString("userId");
        if (userId == null) {
            rejectWithCode(call, PushAppErrorCodes.EMPTY_USER_ID);
            return;
        }

        if (!PushApp.Companion.getInstance().isInitialized()) {
            rejectWithCode(call, PushAppErrorCodes.NOT_INITIALIZED);
            return;
        }

        boolean loggedIn = PushApp.Companion.getInstance().login(userId);
        if (!loggedIn) {
            rejectWithCode(call, PushAppErrorCodes.LOGIN_FAILED);
            return;
        }

        JSObject ret = new JSObject();
        ret.put("status", "logged_in");
        call.resolve(ret);
    }

    // MARK: - save user data (create or update customer profile)
    @PluginMethod
    public void saveUserData(PluginCall call) {
        String code = call.getString("code");
        if (code == null || code.isEmpty()) {
            rejectWithCode(call, PushAppErrorCodes.MISSING_CODE);
            return;
        }

        JSObject additionalInfo = call.getObject("additionalInfo");
        JSObject cohorts = call.getObject("cohorts");
        if (additionalInfo == null || cohorts == null) {
            rejectWithCode(call, PushAppErrorCodes.MISSING_PROFILE_DATA);
            return;
        }

        java.util.Map<String, Object> additionalInfoMap = new java.util.HashMap<>();
        try {
            for (Iterator<String> it = additionalInfo.keys(); it.hasNext(); ) {
                String key = it.next();
                additionalInfoMap.put(key, additionalInfo.get(key));
            }
        } catch (Exception e) {
            rejectWithCode(call, PushAppErrorCodes.INVALID_PROFILE_DATA);
            return;
        }

        java.util.Map<String, Object> cohortsMap = new java.util.HashMap<>();
        try {
            for (Iterator<String> it = cohorts.keys(); it.hasNext(); ) {
                String key = it.next();
                cohortsMap.put(key, cohorts.get(key));
            }
        } catch (Exception e) {
            rejectWithCode(call, PushAppErrorCodes.INVALID_PROFILE_DATA);
            return;
        }

        PushApp.Companion.getInstance().createOrUpdateCustomerProfile(code, additionalInfoMap, cohortsMap, (success) -> {
            if (success) {
                JSObject ret = new JSObject();
                ret.put("status", "saved_user_data");
                ret.put("success", true);
                call.resolve(ret);
            } else {
                rejectWithCode(call, PushAppErrorCodes.CUSTOMER_PROFILE_FAILED);
            }
            return Unit.INSTANCE;
        });
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
            rejectWithCode(call, PushAppErrorCodes.MISSING_EVENT_NAME);
            return;
        }

        JSObject eventData = call.getObject("eventData");
        if (eventData == null) {
            rejectWithCode(call, PushAppErrorCodes.MISSING_EVENT_DATA);
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
            rejectWithCode(call, PushAppErrorCodes.INVALID_EVENT_DATA);
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
            rejectWithCode(call, PushAppErrorCodes.MISSING_PAGE_NAME);
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
            rejectWithCode(call, PushAppErrorCodes.MISSING_PLACEHOLDER_ID);
            return;
        }

        Double x = call.getDouble("x");
        Double y = call.getDouble("y");
        Double width = call.getDouble("width");
        Double height = call.getDouble("height");
        Double clipTop = call.getDouble("clipTop");

        if (x == null || y == null || width == null || height == null) {
            rejectWithCode(call, PushAppErrorCodes.MISSING_UI_BOUNDS);
            return;
        }

        Activity activity = getActivity();
        if (activity == null) {
            rejectWithCode(call, PushAppErrorCodes.ACTIVITY_UNAVAILABLE);
            return;
        }

        Integer clipTopPx = clipTop != null ? clipTop.intValue() : null;

        activity.runOnUiThread(() -> {
            try {
                com.mehery.pushapp.PlaceholderViewManager.INSTANCE.createPlaceholderView(
                    activity,
                    placeholderId,
                    x.intValue(), // Already in pixels from JS
                    y.intValue(), // Already in pixels from JS
                    width.intValue(), // Already in pixels from JS
                    height.intValue(), // Already in pixels from JS
                    clipTopPx
                );

                JSObject ret = new JSObject();
                ret.put("status", "placeholder_registration_initiated");
                call.resolve(ret);
            } catch (Exception e) {
                rejectWithCode(call, PushAppErrorCodes.PLACEHOLDER_FAILED);
            }
        });
    }

    @PluginMethod
    public void updatePlaceholder(PluginCall call) {
        String placeholderId = call.getString("placeholderId");
        if (placeholderId == null || placeholderId.isEmpty()) {
            rejectWithCode(call, PushAppErrorCodes.MISSING_PLACEHOLDER_ID);
            return;
        }

        Double x = call.getDouble("x");
        Double y = call.getDouble("y");
        Double width = call.getDouble("width");
        Double height = call.getDouble("height");
        Double clipTop = call.getDouble("clipTop");

        if (x == null || y == null || width == null || height == null) {
            rejectWithCode(call, PushAppErrorCodes.MISSING_UI_BOUNDS);
            return;
        }

        Activity activity = getActivity();
        if (activity == null) {
            rejectWithCode(call, PushAppErrorCodes.ACTIVITY_UNAVAILABLE);
            return;
        }

        Integer clipTopPx = clipTop != null ? clipTop.intValue() : null;

        activity.runOnUiThread(() -> {
            com.mehery.pushapp.PlaceholderViewManager.INSTANCE.updatePlaceholderView(
                activity,
                placeholderId,
                x.intValue(),
                y.intValue(),
                width.intValue(),
                height.intValue(),
                clipTopPx
            );

            JSObject ret = new JSObject();
            ret.put("status", "placeholder_updated");
            call.resolve(ret);
        });
    }

    // MARK: - unregister placeholder
    @PluginMethod
    public void unregisterPlaceholder(PluginCall call) {
        String placeholderId = call.getString("placeholderId");
        if (placeholderId == null || placeholderId.isEmpty()) {
            rejectWithCode(call, PushAppErrorCodes.MISSING_PLACEHOLDER_ID);
            return;
        }

        Activity activity = getActivity();
        if (activity == null) {
            rejectWithCode(call, PushAppErrorCodes.ACTIVITY_UNAVAILABLE);
            return;
        }

        activity.runOnUiThread(() -> {
            try {
                com.mehery.pushapp.PlaceholderViewManager.INSTANCE.removePlaceholderView(activity, placeholderId);

                JSObject ret = new JSObject();
                ret.put("status", "placeholder_unregistered");
                call.resolve(ret);
            } catch (Exception e) {
                rejectWithCode(call, PushAppErrorCodes.PLACEHOLDER_FAILED);
            }
        });
    }

    // MARK: - register tooltip target
    @PluginMethod
    public void registerTooltipTarget(PluginCall call) {
        String targetId = call.getString("targetId");
        if (targetId == null || targetId.isEmpty()) {
            rejectWithCode(call, PushAppErrorCodes.MISSING_TARGET_ID);
            return;
        }

        Double x = call.getDouble("x");
        Double y = call.getDouble("y");
        Double width = call.getDouble("width");
        Double height = call.getDouble("height");

        if (x == null || y == null || width == null || height == null) {
            rejectWithCode(call, PushAppErrorCodes.MISSING_UI_BOUNDS);
            return;
        }

        Activity activity = getActivity();
        if (activity == null) {
            rejectWithCode(call, PushAppErrorCodes.ACTIVITY_UNAVAILABLE);
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
                rejectWithCode(call, PushAppErrorCodes.TOOLTIP_FAILED);
            }
        });
    }

    // MARK: - unregister tooltip target
    @PluginMethod
    public void unregisterTooltipTarget(PluginCall call) {
        String targetId = call.getString("targetId");
        if (targetId == null || targetId.isEmpty()) {
            rejectWithCode(call, PushAppErrorCodes.MISSING_TARGET_ID);
            return;
        }

        Activity activity = getActivity();
        if (activity == null) {
            rejectWithCode(call, PushAppErrorCodes.ACTIVITY_UNAVAILABLE);
            return;
        }

        activity.runOnUiThread(() -> {
            try {
                com.mehery.pushapp.PlaceholderViewManager.INSTANCE.unregisterTooltipTarget(activity, targetId);

                JSObject ret = new JSObject();
                ret.put("status", "tooltip_target_unregistered");
                call.resolve(ret);
            } catch (Exception e) {
                rejectWithCode(call, PushAppErrorCodes.TOOLTIP_FAILED);
            }
        });
    }

    // MARK: - track push notification event
    @PluginMethod
    public void trackPushNotificationEvent(PluginCall call) {
        String token = call.getString("token");
        if (token == null || token.isEmpty()) {
            rejectWithCode(call, PushAppErrorCodes.MISSING_NOTIFICATION_TOKEN);
            return;
        }

        String event = call.getString("event");
        if (event == null || event.isEmpty()) {
            rejectWithCode(call, PushAppErrorCodes.MISSING_NOTIFICATION_EVENT);
            return;
        }

        String ctaId = call.getString("ctaId");

        PushApp.Companion.getInstance().trackNotificationEvent(token, event, ctaId);

        JSObject ret = new JSObject();
        ret.put("success", true);
        call.resolve(ret);
    }

    @PluginMethod
    public void logout(PluginCall call) {
        if (!PushApp.Companion.getInstance().isInitialized()) {
            rejectWithCode(call, PushAppErrorCodes.NOT_INITIALIZED);
            return;
        }

        PushApp.Companion.getInstance().logout((success) -> {
            if (success) {
                JSObject ret = new JSObject();
                ret.put("status", "logged_out");
                call.resolve(ret);
            } else {
                rejectWithCode(call, PushAppErrorCodes.LOGOUT_FAILED);
            }
            return Unit.INSTANCE;
        });
    }

    @Override
    protected void handleOnResume() {
        super.handleOnResume();
        Activity activity = getActivity();
        if (activity != null) {
            PushApp.Companion.getInstance().setCurrentActivity(activity);
            PushApp.Companion.getInstance().ensureNotificationPermission(activity);
        }
        if (PushApp.Companion.getInstance().isInitialized()) {
            PushApp.Companion.getInstance().onAppForegrounded();
        }
    }
}
