import Foundation
import Capacitor

@available(iOS 15.2, *)
@objc(PushAppPlugin)
public class PushAppPlugin: CAPPlugin {

    // MARK: - initialize
    @objc func initialize(_ call: CAPPluginCall) {
        let appId = call.getString("appId") ?? call.getString("identifier")
        guard let appId = appId else {
            call.reject("appId is required (channel id / App ID; identifier is accepted as a deprecated alias)")
            return
        }

        let sandbox = call.getBool("sandbox") ?? false
        let slackWebhookUrl = call.getString("slackWebhookUrl")

        let initialized = PushApp.shared.initialize(appId: appId, sandbox: sandbox, slackWebhookUrl: slackWebhookUrl)
        if !initialized {
            call.reject("Initialize failed. Check Xcode console for PushApp errors (invalid appId or setup issue).")
            return
        }

        call.resolve([
            "status": "initialized"
        ])
    }

    /// POST push token to `/pushapp/api/device/register`. Call after `initialize` when you have APNs (hex) or FCM token.
    @objc func register(_ call: CAPPluginCall) {
        let apnsToken = call.getString("apnsToken") ?? call.getString("token")
        guard let apnsToken = apnsToken, !apnsToken.isEmpty else {
            call.reject("apnsToken is required on iOS")
            return
        }
        if !PushApp.shared.isInitialized {
            print("PushAppPlugin: register() called before initialize()")
            call.reject("Call PushApp.initialize() before register()")
            return
        }
        let fcmToken = call.getString("fcmToken")
        PushApp.shared.register(apnsToken: apnsToken, fcmToken: fcmToken) { success in
            if success {
                call.resolve(["status": "registered", "success": true])
            } else {
                call.reject("Device register failed. Ensure initialize() was called first and the token is valid.")
            }
        }
    }

    // MARK: - login
    @objc func login(_ call: CAPPluginCall) {
        guard let userId = call.getString("userId") else {
            call.reject("userId is required")
            return
        }

        let loggedIn = PushApp.shared.login(userId: userId)
        if !loggedIn {
            call.reject("Call PushApp.initialize() then register() before login(). See Xcode console tag PushApp.")
            return
        }

        call.resolve([
            "status": "logged_in"
        ])
    }

    @objc func ping(_ call: CAPPluginCall) {

        PushApp.shared.ping()

        call.resolve([
            "status": "pinged"
        ])
    }

    
    @objc func saveUserData(_ call: CAPPluginCall) {
        print("🔔 saveUserData called with code: \(call.getString("code"))")
        print("🔔 saveUserData called with additionalInfo: \(call.getAny("additionalInfo"))")
        print("🔔 saveUserData called with cohorts: \(call.getAny("cohorts"))")
        guard let code = call.getString("code") else {
            call.reject("code is required (e.g. userId_deviceId)")
            return
        }
        guard let additionalInfo = call.getAny("additionalInfo") else {
            call.reject("additionalInfo is required")
            return
        }
        guard let cohorts = call.getAny("cohorts") else {
            call.reject("cohorts is required")
            return
        }
        guard let additionalInfoDict = additionalInfo as? [String: Any],
              let cohortsDict = cohorts as? [String: Any] else {
            call.reject("additionalInfo and cohorts must be objects")
            return
        }
        PushApp.shared.updateCustomerProfile(code: code, cohorts: cohortsDict, additionalInfo: additionalInfoDict) { success in
            if success {
                call.resolve(["status": "saved_user_data", "success": true])
            } else {
                call.reject("Customer profile update failed")
            }
        }
    }

    // MARK: - get device headers
    @objc func getDeviceHeaders(_ call: CAPPluginCall) {
        let headers = PushApp.shared.getDeviceHeaders()
        call.resolve(headers)
    }
    
    // MARK: - send event
    @objc func sendEvent(_ call: CAPPluginCall) {
        guard let eventName = call.getString("eventName") else {
            call.reject("eventName is required")
            return
        }
        
        guard let eventData = call.getObject("eventData") as? [String: Any] else {
            call.reject("eventData is required and must be an object")
            return
        }
        
        PushApp.shared.sendEvent(eventName: eventName, eventData: eventData)
        
        call.resolve([
            "status": "event_sent"
        ])
    }
    
    // MARK: - set page name
    @objc func setPageName(_ call: CAPPluginCall) {
        guard let pageName = call.getString("pageName") else {
            call.reject("pageName is required")
            return
        }
        
        // Update current context if available - use bridge's view controller
//        if let viewController = self.bridge?.viewController {
//            PushApp.shared.currentContext = viewController
//        }
        
        PushApp.shared.sendEvent(eventName: "page_open", eventData: ["page": pageName])
        
        call.resolve([
            "status": "page_set"
        ])
    }
    
    // MARK: - register placeholder
@objc func registerPlaceholder(_ call: CAPPluginCall) {
    guard let placeholderId = call.getString("placeholderId"),
          let x = call.getFloat("x"),
          let y = call.getFloat("y"),
          let width = call.getFloat("width"),
          let height = call.getFloat("height") else {
        call.reject("placeholderId, x, y, width, and height are required")
        return
    }

    // Call the updated PushApp SDK method with coordinates
    PushApp.shared.registerPlaceholder(
            placeholderId: placeholderId,
            x: CGFloat(x),
            y: CGFloat(y),
            width: CGFloat(width),
            height: CGFloat(height),
            webView: self.bridge?.webView // Pass the Capacitor Web View reference
        )

        call.resolve([
            "status": "placeholder_registration_initiated"
        ])
    }

    // MARK: - unregister placeholder
    @objc func unregisterPlaceholder(_ call: CAPPluginCall) {
        guard let placeholderId = call.getString("placeholderId") else {
            call.reject("placeholderId is required")
            return
        }

        PushApp.shared.unregisterPlaceholder(placeholderId: placeholderId)

        call.resolve([
            "status": "placeholder_unregistered"
        ])
    }

    // MARK: - ⭐️ NEW: register tooltip target
    @objc func registerTooltipTarget(_ call: CAPPluginCall) {
        guard let targetId = call.getString("targetId"), // Note: The client uses 'targetId'
              let x = call.getFloat("x"),
              let y = call.getFloat("y"),
              let width = call.getFloat("width"),
              let height = call.getFloat("height") else {
            call.reject("targetId, x, y, width, and height are required")
            return
        }

        // Call the new PushApp SDK method
        PushApp.shared.registerTooltipTarget(
            targetId: targetId,
            x: CGFloat(x),
            y: CGFloat(y),
            width: CGFloat(width),
            height: CGFloat(height)
        )

        call.resolve([
            "status": "tooltip_target_registration_initiated"
        ])
    }
    
    // MARK: - ⭐️ NEW: unregister tooltip target
    @objc func unregisterTooltipTarget(_ call: CAPPluginCall) {
        guard let targetId = call.getString("targetId") else {
            call.reject("targetId is required")
            return
        }

        PushApp.shared.unregisterTooltipTarget(targetId: targetId)

        call.resolve([
            "status": "tooltip_target_unregistered"
        ])
    }

    // MARK: - track push notification event
    @objc func trackPushNotificationEvent(_ call: CAPPluginCall) {
        guard let token = call.getString("token") else {
            call.reject("token is required")
            return
        }
        guard let event = call.getString("event") else {
            call.reject("event is required")
            return
        }
        let ctaId = call.getString("ctaId")

        PushApp.shared.trackPushNotificationEvent(token: token, event: event, ctaId: ctaId) { success in
            call.resolve(["success": success])
        }
    }
}
