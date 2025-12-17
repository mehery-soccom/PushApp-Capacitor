import Foundation
import Capacitor

@available(iOS 15.2, *)
@objc(PushAppPlugin)
public class PushAppPlugin: CAPPlugin {

    // MARK: - initialize
    @objc func initialize(_ call: CAPPluginCall) {
        guard let identifier = call.getString("identifier") else {
            call.reject("identifier is required")
            return
        }

        let sandbox = call.getBool("sandbox") ?? false

        PushApp.shared.initialize(identifier: identifier, sandbox: sandbox)

        call.resolve([
            "status": "initialized"
        ])
    }

    // MARK: - login
    @objc func login(_ call: CAPPluginCall) {
        guard let userId = call.getString("userId") else {
            call.reject("userId is required")
            return
        }

        PushApp.shared.login(userId: userId)

        call.resolve([
            "status": "logged_in"
        ])
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
}
