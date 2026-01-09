// PushAppPlugin.m

#import <Foundation/Foundation.h>
#import <Capacitor/Capacitor.h>

CAP_PLUGIN(PushAppPlugin, "PushApp",
           CAP_PLUGIN_METHOD(initialize, CAPPluginReturnPromise);
           CAP_PLUGIN_METHOD(login, CAPPluginReturnPromise);
           CAP_PLUGIN_METHOD(ping, CAPPluginReturnPromise);
           CAP_PLUGIN_METHOD(saveUserData, CAPPluginReturnPromise);
           CAP_PLUGIN_METHOD(getDeviceHeaders, CAPPluginReturnPromise);
           CAP_PLUGIN_METHOD(sendEvent, CAPPluginReturnPromise);
           CAP_PLUGIN_METHOD(setPageName, CAPPluginReturnPromise);
           CAP_PLUGIN_METHOD(registerPlaceholder, CAPPluginReturnPromise);
           CAP_PLUGIN_METHOD(unregisterPlaceholder, CAPPluginReturnPromise);
           CAP_PLUGIN_METHOD(registerTooltipTarget, CAPPluginReturnPromise); // ⭐️ ADD THIS
           CAP_PLUGIN_METHOD(unregisterTooltipTarget, CAPPluginReturnPromise); // ⭐️ ADD THIS
)