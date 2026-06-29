import Foundation
import Capacitor

@available(iOS 15.2, *)
@objc(PushAppPlugin)
public class PushAppPlugin: CAPPlugin {

    private func reject(_ call: CAPPluginCall, code: String) {
        call.reject(PushAppErrorCodes.message(for: code), code)
    }

    // MARK: - initialize
    @objc func initialize(_ call: CAPPluginCall) {
        let appId = call.getString("appId") ?? call.getString("identifier")
        guard let appId = appId else {
            reject(call, code: PushAppErrorCodes.appIdRequired)
            return
        }

        let sandbox = call.getBool("sandbox") ?? false
        let debugMode = call.getBool("debugMode") ?? false
        let slackWebhookUrl = call.getString("slackWebhookUrl")

        let initialized = PushApp.shared.initialize(
            appId: appId,
            sandbox: sandbox,
            slackWebhookUrl: slackWebhookUrl,
            debugMode: debugMode
        )
        if !initialized {
            reject(call, code: PushAppErrorCodes.invalidAppId)
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
            reject(call, code: PushAppErrorCodes.emptyToken)
            return
        }
        if !PushApp.shared.isInitialized {
            reject(call, code: PushAppErrorCodes.notInitialized)
            return
        }
        let fcmToken = call.getString("fcmToken")
        PushApp.shared.register(apnsToken: apnsToken, fcmToken: fcmToken) { success in
            if success {
                call.resolve(["status": "registered", "success": true])
            } else {
                self.reject(call, code: PushAppErrorCodes.registerFailed)
            }
        }
    }

    // MARK: - login
    @objc func login(_ call: CAPPluginCall) {
        guard let userId = call.getString("userId"), !userId.isEmpty else {
            reject(call, code: PushAppErrorCodes.emptyUserId)
            return
        }

        if !PushApp.shared.isInitialized {
            reject(call, code: PushAppErrorCodes.notInitialized)
            return
        }

        let loggedIn = PushApp.shared.login(userId: userId)
        if !loggedIn {
            reject(call, code: PushAppErrorCodes.loginFailed)
            return
        }

        call.resolve([
            "status": "logged_in"
        ])
    }

    @objc func logout(_ call: CAPPluginCall) {
        if !PushApp.shared.isInitialized {
            reject(call, code: PushAppErrorCodes.notInitialized)
            return
        }

        PushApp.shared.logout { success in
            if success {
                call.resolve(["status": "logged_out"])
            } else {
                self.reject(call, code: PushAppErrorCodes.logoutFailed)
            }
        }
    }

    @objc func ping(_ call: CAPPluginCall) {

        PushApp.shared.ping()

        call.resolve([
            "status": "pinged"
        ])
    }

    
    @objc func saveUserData(_ call: CAPPluginCall) {
        PushAppLogger.debug("saveUserData called")
        guard let code = call.getString("code"), !code.isEmpty else {
            reject(call, code: PushAppErrorCodes.missingCode)
            return
        }
        guard let additionalInfo = call.getAny("additionalInfo"),
              let cohorts = call.getAny("cohorts") else {
            reject(call, code: PushAppErrorCodes.missingProfileData)
            return
        }
        guard let additionalInfoDict = additionalInfo as? [String: Any],
              let cohortsDict = cohorts as? [String: Any] else {
            reject(call, code: PushAppErrorCodes.invalidProfileData)
            return
        }
        PushApp.shared.updateCustomerProfile(code: code, cohorts: cohortsDict, additionalInfo: additionalInfoDict) { success in
            if success {
                call.resolve(["status": "saved_user_data", "success": true])
            } else {
                self.reject(call, code: PushAppErrorCodes.customerProfileFailed)
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
            reject(call, code: PushAppErrorCodes.missingEventName)
            return
        }
        
        guard let eventData = call.getObject("eventData") as? [String: Any] else {
            reject(call, code: PushAppErrorCodes.missingEventData)
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
            reject(call, code: PushAppErrorCodes.missingPageName)
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
            reject(call, code: PushAppErrorCodes.missingUiBounds)
        return
    }

    // Call the updated PushApp SDK method with coordinates
    let clipTop = call.getFloat("clipTop")
    PushApp.shared.registerPlaceholder(
            placeholderId: placeholderId,
            x: CGFloat(x),
            y: CGFloat(y),
            width: CGFloat(width),
            height: CGFloat(height),
            webView: self.bridge?.webView, // Pass the Capacitor Web View reference
            clipTop: clipTop.map { CGFloat($0) }
        )

        call.resolve([
            "status": "placeholder_registration_initiated"
        ])
    }

    @objc func updatePlaceholder(_ call: CAPPluginCall) {
        guard let placeholderId = call.getString("placeholderId"),
              let x = call.getFloat("x"),
              let y = call.getFloat("y"),
              let width = call.getFloat("width"),
              let height = call.getFloat("height") else {
                reject(call, code: PushAppErrorCodes.missingUiBounds)
            return
        }

        let clipTop = call.getFloat("clipTop")
        PushApp.shared.updatePlaceholder(
            placeholderId: placeholderId,
            x: CGFloat(x),
            y: CGFloat(y),
            width: CGFloat(width),
            height: CGFloat(height),
            clipTop: clipTop.map { CGFloat($0) }
        )

        call.resolve([
            "status": "placeholder_updated"
        ])
    }

    // MARK: - unregister placeholder
    @objc func unregisterPlaceholder(_ call: CAPPluginCall) {
        guard let placeholderId = call.getString("placeholderId"), !placeholderId.isEmpty else {
            reject(call, code: PushAppErrorCodes.missingPlaceholderId)
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
            reject(call, code: PushAppErrorCodes.missingUiBounds)
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
        guard let targetId = call.getString("targetId"), !targetId.isEmpty else {
            reject(call, code: PushAppErrorCodes.missingTargetId)
            return
        }

        PushApp.shared.unregisterTooltipTarget(targetId: targetId)

        call.resolve([
            "status": "tooltip_target_unregistered"
        ])
    }

    // MARK: - track push notification event
    @objc func trackPushNotificationEvent(_ call: CAPPluginCall) {
        guard let token = call.getString("token"), !token.isEmpty else {
            reject(call, code: PushAppErrorCodes.missingNotificationToken)
            return
        }
        guard let event = call.getString("event"), !event.isEmpty else {
            reject(call, code: PushAppErrorCodes.missingNotificationEvent)
            return
        }
        let ctaId = call.getString("ctaId")

        PushApp.shared.trackPushNotificationEvent(token: token, event: event, ctaId: ctaId) { success in
            call.resolve(["success": success])
        }
    }
}
