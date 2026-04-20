import Foundation
import SwiftUICore
import UIKit
import UserNotifications
import WebKit
import SwiftUI
import EasyTipView

@available(iOS 15.2, *)
public class PushApp: NSObject {
    public static let shared = PushApp()

    private var serverUrl : String = ""
    var userId: String?
    private var guestId: String?
    internal var tenant: String = ""
    private var channelId: String = ""
    private var socketManager: WebSocketManager?
    private var inAppDisplay: InAppDisplay?
    private var currentContext: UIViewController?
    internal var sandbox: Bool = false
    private var placeholderViews = [String: UIView]()
    private var tooltipTargetRects = [String: CGRect]()
    private var currentTooltip: EasyTipView?
    
    let slackWebhookURL = URL(string: "https://hooks.slack.com/services/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")!
    

    private override init() {}

    public func initialize(appId: String, sandbox: Bool = false) {
        self.sandbox = sandbox
        guard let context = self.topViewController() else {
                self.slackPrint("⚠️ Unable to find top view controller")
                return
            }
        self.currentContext = context
        // App ID is the full channel id (e.g. demo_1763369170735). Tenant is the prefix before the first '_'.
        if let u = appId.firstIndex(of: "_"),
           u > appId.startIndex,
           u < appId.index(before: appId.endIndex) {
            self.tenant = String(appId[..<u])
            self.channelId = appId
            self.slackPrint(self.tenant)
            self.slackPrint(self.channelId)
        } else {
            self.slackPrint("Invalid app id: expected channel id with tenant prefix before first '_', e.g. demo_1763369170735")
        }
        
        if sandbox {
            // sandbox: tenant.mehery.com
            self.serverUrl = "https://\(self.tenant).pushapp.com"
        } else {
            // production: tenant.mehery.com/pushapp
            self.serverUrl = "https://\(self.tenant).pushapp.co.in"
        }
        self.slackPrint("Server URL set to: \(self.serverUrl)")

        self.inAppDisplay = InAppDisplay(context: context)

        if let savedUserId = UserDefaults.standard.string(forKey: "pushapp_user_id") {
             self.slackPrint("SavedUserId: \(savedUserId)")
            self.userId = savedUserId
            self.sendEvent(eventName: "app_open", eventData: ["compare": channelId])
            self.connectSocket()
        } else {
            registerDeviceToken()
        }
        
        registerNotificationCategories()
    }

    // Inside PushApp class

    // Inside PushApp class (add this alongside registerPlaceholder)

public func registerTooltipTarget(targetId: String,
                                  x: CGFloat,
                                  y: CGFloat,
                                  width: CGFloat,
                                  height: CGFloat) {
    
    guard self.tooltipTargetRects[targetId] == nil else {
        slackPrint("Tooltip target \(targetId) already registered. Updating coordinates.")
        // If the view moves (e.g., rotation), we update the rect.
        self.tooltipTargetRects[targetId] = CGRect(x: x, y: y, width: width, height: height)
        return
    }
    
    // Store the coordinates for later use during poll processing
    let targetRect = CGRect(x: x, y: y, width: width, height: height)
    self.tooltipTargetRects[targetId] = targetRect
    
    slackPrint("✅ Tooltip target rect for \(targetId) registered: \(targetRect)")

    // Send the widget_open event immediately, just like the banner
    // This event triggers the polling logic which will look for this ID.
    self.sendEvent(eventName: "widget_open", eventData: ["compare": targetId])
}

// Optional cleanup function (similar to unregisterPlaceholder)
public func unregisterTooltipTarget(targetId: String) {
    if self.tooltipTargetRects.removeValue(forKey: targetId) != nil {
        self.slackPrint("✅ Tooltip target rect \(targetId) removed.")
    }
}

public func registerPlaceholder(placeholderId: String,
                                  x: CGFloat,
                                  y: CGFloat,
                                  width: CGFloat,
                                  height: CGFloat,
                                  webView: WKWebView?) {

    guard self.placeholderViews[placeholderId] == nil else {
        slackPrint("Placeholder \(placeholderId) already registered.")
        return
    }

    guard let webView = webView, let superview = webView.superview else {
        slackPrint("Cannot register placeholder: WebView or Superview not available.")
        return
    }

    // 1. Create a container view for the native widget
    let containerView = UIView(frame: CGRect(x: x, y: y, width: width, height: height))
    containerView.backgroundColor = .clear // Initially clear
    containerView.tag = 999 // Optional: for debugging/easy finding
    
    // 2. Add the container view as an overlay to the web view's superview
    // This places it on top of the web content at the specified coordinates
    superview.addSubview(containerView)
    self.placeholderViews[placeholderId] = containerView
    slackPrint("✅ Native container view for \(placeholderId) added at (\(x), \(y)) with size \(width)x\(height)")

    // 3. Immediately send the widget_open event
    // The poll function (called 2s later by sendEvent) will look for this placeholder ID
    self.sendEvent(eventName: "widget_open", eventData: ["compare": placeholderId])
}

public func unregisterPlaceholder(placeholderId: String) {
    if let view = self.placeholderViews.removeValue(forKey: placeholderId) {
        // Remove the native view from the hierarchy
        DispatchQueue.main.async {
            view.removeFromSuperview()
            self.slackPrint("✅ Native placeholder view \(placeholderId) removed.")
        }
    }
}
    
    func slackPrint(_ message: String) {
        // Print to console
        print(message)
        
        // Send to Slack
//        var request = URLRequest(url: slackWebhookURL)
//        request.httpMethod = "POST"
//        request.addValue("application/json", forHTTPHeaderField: "Content-Type")
//        
//        let payload = ["text": message]
//        
//        do {
//            request.httpBody = try JSONSerialization.data(withJSONObject: payload, options: [])
//        } catch {
//            print("⚠️ Failed to encode Slack message: \(error)")
//            return
//        }
//        
//        let task = URLSession.shared.dataTask(with: request) { _, response, error in
//            if let error = error {
//                print("⚠️ Slack send error: \(error)")
//            } else if let httpResponse = response as? HTTPURLResponse, httpResponse.statusCode != 200 {
//                print("⚠️ Slack returned status: \(httpResponse.statusCode)")
//            }
//        }
//        
//        task.resume()
    }

    func getDeviceHeaders() -> [String: String] {
        var headers: [String: String] = [:]

        // Device ID (non-persistent, per vendor)
        let deviceId = getPersistentDeviceId()
        headers["X-Device-ID"] = deviceId


        // Locale
        let locale = Locale.current.identifier

        // Screen resolution & orientation
        let screen = UIScreen.main
        let screenWidth = Int(screen.bounds.width * screen.scale)
        let screenHeight = Int(screen.bounds.height * screen.scale)
        let orientation = UIDevice.current.orientation.isLandscape ? "Landscape" : "Portrait"

        // Timezone
        let timezone = TimeZone.current.identifier

        // System info
        let device = UIDevice.current
        let osName = device.systemName
        let osVersion = device.systemVersion
        let deviceModel = getDeviceModel() // detailed like "iPhone14,3"
        let manufacturer = "Apple"

        // App info
        let bundle = Bundle.main
        let bundleId = bundle.bundleIdentifier ?? ""
        let appVersion = bundle.infoDictionary?["CFBundleShortVersionString"] as? String ?? ""
        let buildNumber = bundle.infoDictionary?["CFBundleVersion"] as? String ?? ""

        // Fill headers
        headers["X-Device-ID"] = deviceId
        headers["X-Screen-Resolution"] = "\(screenWidth)x\(screenHeight)"
        headers["X-Device-Orientation"] = orientation
        headers["X-Locale"] = locale
        headers["X-Timezone"] = timezone
        headers["X-OS-Name"] = osName
        headers["X-OS-Version"] = osVersion
        headers["X-Device-Model"] = deviceModel
        headers["X-Manufacturer"] = manufacturer
        headers["X-Bundle-ID"] = bundleId
        headers["X-App-Version"] = appVersion
        headers["X-Build-Number"] = buildNumber

        return headers
    }

    // Helper: detailed device model (e.g. "iPhone14,3")
    func getDeviceModel() -> String {
        var systemInfo = utsname()
        uname(&systemInfo)
        let modelCode = withUnsafePointer(to: &systemInfo.machine) { ptr in
            ptr.withMemoryRebound(to: CChar.self, capacity: 1) {
                String(validatingUTF8: $0) ?? "Unknown"
            }
        }
        return modelCode
    }


    private func registerDeviceToken() {
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge]) { granted, _ in
            guard granted else { return }
            DispatchQueue.main.async {
                UIApplication.shared.registerForRemoteNotifications()
            }
        }
    }

    public func handleDeviceToken(_ deviceToken: Data) {
        let tokenString = deviceToken.map { String(format: "%02x", $0) }.joined()
        self.slackPrint("APNs Token: \(tokenString)")

        let defaults = UserDefaults.standard
        let lastToken = defaults.string(forKey: "apns_token")

        if lastToken != tokenString {
            self.slackPrint("📡 New APNs token detected, sending to server...")
            sendTokenToServer(platform: "apns", token: tokenString, fcmToken: nil, completion: nil)
        } else {
            self.slackPrint("✅ Token unchanged, not sending to server")
        }
    }

    /// POST to `/pushapp/api/device/register`.
    /// iOS sends APNs token in `token`; optional Firebase token can be sent in `fcmToken`.
    public func registerPushToken(token: String, fcmToken: String?, completion: @escaping (Bool) -> Void) {
        sendTokenToServer(platform: "apns", token: token, fcmToken: fcmToken, completion: completion)
    }

    private func sendTokenToServer(platform: String, token: String, fcmToken: String? = nil, completion: ((Bool) -> Void)? = nil) {
        guard let deviceId = getPersistentDeviceId() as String? else {
            self.slackPrint("❌ Failed to get deviceId")
            if let completion = completion {
                DispatchQueue.main.async { completion(false) }
            }
            return
        }

        let urlString = "\(serverUrl)/pushapp/api/device/register"
        guard let url = URL(string: urlString) else {
            self.slackPrint("❌ Invalid URL: \(urlString)")
            if let completion = completion {
                DispatchQueue.main.async { completion(false) }
            }
            return
        }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")

        // Attach device headers
        let deviceHeaders = getDeviceHeaders()
        for (key, value) in deviceHeaders {
            request.setValue(value, forHTTPHeaderField: key)
        }

        var payload: [String: Any] = [
            "platform": platform,
            "token": token,
            "device_id": deviceId,
            "channel_id": channelId
        ]
        if let fcmToken = fcmToken, !fcmToken.isEmpty {
            payload["fcm_token"] = fcmToken
        }

        guard let jsonData = try? JSONSerialization.data(withJSONObject: payload, options: .prettyPrinted),
              let jsonString = String(data: jsonData, encoding: .utf8) else {
            self.slackPrint("❌ Failed to serialize payload")
            if let completion = completion {
                DispatchQueue.main.async { completion(false) }
            }
            return
        }
        self.slackPrint("📤 Sending Token Request: \(urlString)")
        self.slackPrint("📦 Payload: \(jsonString)")
        request.httpBody = jsonData

        URLSession.shared.dataTask(with: request) { data, response, error in
            if let error = error {
                self.slackPrint("❌ Token send failed: \(error.localizedDescription)")
                if let completion = completion {
                    DispatchQueue.main.async { completion(false) }
                }
                return
            }

            if let httpResponse = response as? HTTPURLResponse {
                self.slackPrint("🌐 HTTP Status: \(httpResponse.statusCode)")
                self.slackPrint("🌐 Headers: \(httpResponse.allHeaderFields)")
            }

            guard let data = data else {
                self.slackPrint("❌ No response data received")
                if let completion = completion {
                    DispatchQueue.main.async { completion(false) }
                }
                return
            }

            if let rawString = String(data: data, encoding: .utf8) {
                self.slackPrint("📥 Raw Response: \(rawString)")
            }

            let status = (response as? HTTPURLResponse)?.statusCode ?? 0
            let httpOk = (200..<300).contains(status)

            do {
                let json = try JSONSerialization.jsonObject(with: data, options: [.allowFragments])
                self.slackPrint("✅ Parsed JSON: \(json)")

                if let responseDict = json as? [String: Any],
                   let device = responseDict["device"] as? [String: Any],
                   let guest = device["user_id"] as? String {
                    let defaults = UserDefaults.standard
                    defaults.set(token, forKey: "apns_token")
                    self.guestId = guest
                    self.slackPrint("🎉 Guest ID set: \(guest)")
                } else {
                    self.slackPrint("⚠️ JSON structure did not match expected format")
                }
            } catch {
                self.slackPrint("❌ Token send JSON parse error: \(error)")
            }

            if let completion = completion {
                DispatchQueue.main.async { completion(httpOk) }
            }
        }.resume()
    }


    public func login(userId: String) {
        self.slackPrint("🔑 Starting login for user: \(userId)")

        self.userId = userId
        UserDefaults.standard.setValue(userId, forKey: "pushapp_user_id")
        self.slackPrint("💾 Saved userId in UserDefaults")

        connectSocket()
        self.slackPrint("🔌 Socket connection initiated")

        guard let deviceId = getPersistentDeviceId() as String? else {
            self.slackPrint("❌ Failed to get deviceId")
            return
        }

        let urlString = "\(serverUrl)/pushapp/api/device/link"
        guard let url = URL(string: urlString) else {
            self.slackPrint("❌ Invalid URL: \(urlString)")
            return
        }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")

        // Attach device headers
        let deviceHeaders = getDeviceHeaders()
        for (key, value) in deviceHeaders {
            request.setValue(value, forHTTPHeaderField: key)
        }
        self.slackPrint("📩 Attached device headers: \(deviceHeaders)")

        let payload: [String: Any] = [
            "user_id": userId,
            "device_id": deviceId,
            "channel_id": channelId
        ]

        if let jsonData = try? JSONSerialization.data(withJSONObject: payload, options: .prettyPrinted),
           let jsonString = String(data: jsonData, encoding: .utf8) {
            self.slackPrint("📤 Sending Login Request: \(urlString)")
            self.slackPrint("📦 Payload: \(jsonString)")
            request.httpBody = jsonData
        } else {
            self.slackPrint("❌ Failed to serialize login payload")
            return
        }

        URLSession.shared.dataTask(with: request) { data, response, error in
            if let error = error {
                self.slackPrint("❌ Login request failed: \(error.localizedDescription)")
                return
            }

            if let httpResponse = response as? HTTPURLResponse {
                self.slackPrint("🌐 Login Response Status: \(httpResponse.statusCode)")
                self.slackPrint("🌐 Headers: \(httpResponse.allHeaderFields)")
            }

            guard let data = data else {
                self.slackPrint("❌ No response data received for login")
                return
            }

            if let rawString = String(data: data, encoding: .utf8) {
                self.slackPrint("📥 Raw Login Response: \(rawString)")
            }

            do {
                let json = try JSONSerialization.jsonObject(with: data, options: [.allowFragments])
                self.slackPrint("✅ Parsed Login JSON: \(json)")
            } catch {
                self.slackPrint("❌ Login JSON parse error: \(error)")
            }
        }.resume()
    }

    public func ping() {

        guard let deviceId = getPersistentDeviceId() as String? else {
            self.slackPrint("❌ Failed to get deviceId")
            return
        }

        var contact_id = ""

        if let savedUserId = UserDefaults.standard.string(forKey: "pushapp_user_id") {
                contact_id = savedUserId + "_" + deviceId
            } else {
                self.slackPrint("❌ No saved userId for in-app polling")
            }

        let urlString = "\(serverUrl)/pushapp/api/ping"
        guard let url = URL(string: urlString) else {
            self.slackPrint("❌ Invalid URL: \(urlString)")
            return
        }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")

        // Attach device headers
        let deviceHeaders = getDeviceHeaders()
        for (key, value) in deviceHeaders {
            request.setValue(value, forHTTPHeaderField: key)
        }
        self.slackPrint("📩 Attached device headers: \(deviceHeaders)")

        let payload: [String: Any] = [
            "channel_id": channelId,
            "contact_id": contact_id
        ]

        if let jsonData = try? JSONSerialization.data(withJSONObject: payload, options: .prettyPrinted),
           let jsonString = String(data: jsonData, encoding: .utf8) {
            self.slackPrint("📤 Sending Login Request: \(urlString)")
            self.slackPrint("📦 Payload: \(jsonString)")
            request.httpBody = jsonData
        } else {
            self.slackPrint("❌ Failed to serialize login payload")
            return
        }

        URLSession.shared.dataTask(with: request) { data, response, error in
            if let error = error {
                self.slackPrint("❌ Login request failed: \(error.localizedDescription)")
                return
            }

            if let httpResponse = response as? HTTPURLResponse {
                self.slackPrint("🌐 Login Response Status: \(httpResponse.statusCode)")
                self.slackPrint("🌐 Headers: \(httpResponse.allHeaderFields)")
            }

            guard let data = data else {
                self.slackPrint("❌ No response data received for login")
                return
            }

            if let rawString = String(data: data, encoding: .utf8) {
                self.slackPrint("📥 Raw Login Response: \(rawString)")
            }

            do {
                let json = try JSONSerialization.jsonObject(with: data, options: [.allowFragments])
                self.slackPrint("✅ Parsed Login JSON: \(json)")
            } catch {
                self.slackPrint("❌ Login JSON parse error: \(error)")
            }
        }.resume()
    }


    /// Creates or updates customer profile (PUT). Call after login with the same `code` your app uses (e.g. userId_deviceId).
    /// - Parameters:
    ///   - code: Customer code (e.g. userId_deviceId), required by the API.
    ///   - cohorts: Free JSON for cohort data.
    ///   - additionalInfo: Free JSON for additional profile data.
    ///   - completion: Called on main thread with success true if status 2xx, else false.
    public func updateCustomerProfile(
        code: String,
        cohorts: [String: Any],
        additionalInfo: [String: Any],
        completion: ((Bool) -> Void)? = nil
    ) {
        guard !code.isEmpty else {
            self.slackPrint("❌ code is required for customer profile")
            DispatchQueue.main.async { completion?(false) }
            return
        }

        let urlString = "\(serverUrl)/pushapp/api/v1/customer/profile?code=\(code.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? code)"
        guard let url = URL(string: urlString) else {
            self.slackPrint("❌ Invalid URL: \(urlString)")
            DispatchQueue.main.async { completion?(false) }
            return
        }
        self.slackPrint("✅ Customer profile URL: \(urlString)")

        var request = URLRequest(url: url)
        request.httpMethod = "PUT"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")

        let deviceHeaders = getDeviceHeaders()
        for (key, value) in deviceHeaders {
            request.setValue(value, forHTTPHeaderField: key)
        }
        self.slackPrint("📩 Attached device headers: \(deviceHeaders)")

        let payload: [String: Any] = [
            "additionalInfo": additionalInfo,
            "cohorts": cohorts,
            "code": code
        ]

        do {
            let jsonData = try JSONSerialization.data(withJSONObject: payload, options: [])
            request.httpBody = jsonData

            if let jsonString = String(data: jsonData, encoding: .utf8) {
                self.slackPrint("📤 Sending Customer Profile PUT: \(urlString)")
                self.slackPrint("📦 Payload: \(jsonString)")
            }
        } catch {
            self.slackPrint("❌ Failed to serialize customer profile payload: \(error)")
            DispatchQueue.main.async { completion?(false) }
            return
        }

        URLSession.shared.dataTask(with: request) { [weak self] data, response, error in
            guard let self = self else { return }
            let success: Bool
            if let error = error {
                self.slackPrint("❌ Customer profile request failed: \(error.localizedDescription)")
                success = false
            } else if let httpResponse = response as? HTTPURLResponse {
                self.slackPrint("🌐 Profile Response Status: \(httpResponse.statusCode)")
                success = (200..<300).contains(httpResponse.statusCode)
                if !success, let data = data, let raw = String(data: data, encoding: .utf8) {
                    self.slackPrint("❌ Profile response body: \(raw)")
                }
            } else {
                success = false
            }
            DispatchQueue.main.async { completion?(success) }
        }.resume()
    }



    private func connectSocket() {
        guard let id = userId else { return }
        DispatchQueue.main.async {
            self.socketManager = WebSocketManager(userId: id, onMessage: { data in
                self.handleNotification(data)
            })
            self.socketManager?.connect()
        }
    }
    
    public static func showTooltip(text: String, for view: UIView, in container: UIView? = nil) {
        var preferences = EasyTipView.Preferences()
        preferences.drawing.font = UIFont.systemFont(ofSize: 14)
        preferences.drawing.foregroundColor = .white
        preferences.drawing.backgroundColor = .black
        preferences.drawing.arrowPosition = .top

        let tooltip = EasyTipView(text: text, preferences: preferences, delegate: nil)
        tooltip.show(forView: view, withinSuperview: container)
    }

    public func sendEvent(eventName: String, eventData: [String: Any]) {
        guard let userIdToUse = userId ?? guestId else {
            self.slackPrint("❌ No userId or guestId available. Event not sent.")
            return
        }

        self.slackPrint("📢 Event Triggered: \(eventName)")
        self.slackPrint("🗂 Event Data: \(eventData)")

        let urlString = "\(serverUrl)/pushapp/api/v1/event"
        guard let url = URL(string: urlString) else {
            self.slackPrint("❌ Invalid URL for sending event: \(urlString)")
            return
        }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")

        // Attach device headers
        let deviceHeaders = getDeviceHeaders()
        for (key, value) in deviceHeaders {
            request.setValue(value, forHTTPHeaderField: key)
        }
        self.slackPrint("📩 Device headers attached: \(deviceHeaders)")

        let payload: [String: Any] = [
            "user_id": userIdToUse,
            "channel_id": channelId,
            "event_name": eventName,
            "event_data": eventData
        ]

        if let jsonData = try? JSONSerialization.data(withJSONObject: payload, options: .prettyPrinted),
           let jsonString = String(data: jsonData, encoding: .utf8) {
            self.slackPrint("📤 Sending Event Request to: \(urlString)")
            self.slackPrint("📦 Event Payload: \(jsonString)")
            request.httpBody = jsonData
        } else {
            self.slackPrint("❌ Failed to serialize event payload")
            return
        }

        // Poll in-app notifications after 2 seconds
        DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) {
            if let savedUserId = UserDefaults.standard.string(forKey: "pushapp_user_id") {
                self.slackPrint("⏱ Polling in-app notifications for userId: \(savedUserId)")
                self.pollForInApp(userId: savedUserId)
            } else {
                self.slackPrint("❌ No saved userId for in-app polling")
            }
        }

        URLSession.shared.dataTask(with: request) { data, response, error in
            if let error = error {
                self.slackPrint("❌ Event request failed: \(error.localizedDescription)")
                return
            }

            if let httpResponse = response as? HTTPURLResponse {
                self.slackPrint("🌐 Event Response Status: \(httpResponse.statusCode)")
                self.slackPrint("🌐 Response Headers: \(httpResponse.allHeaderFields)")
            }

            guard let data = data else {
                self.slackPrint("❌ No data received for event request")
                return
            }

            if let rawString = String(data: data, encoding: .utf8) {
                self.slackPrint("📥 Raw Event Response: \(rawString)")
            }

            do {
                let json = try JSONSerialization.jsonObject(with: data, options: [.allowFragments])
                self.slackPrint("✅ Parsed Event JSON: \(json)")
            } catch {
                self.slackPrint("❌ Event JSON parse error: \(error)")
            }
        }.resume()
    }


    private func handleNotification(_ data: [String: Any]) {
//        if let messageType = data["message_type"] as? String, messageType == "rule_triggered",
//           let ruleId = data["rule_id"] as? String {
////            pollForInApp(ruleId: ruleId)
//        } else {
//            inAppDisplay?.showInApp(from: data)
//        }
    }

    public func pollForInApp(userId: String) {
        guard let deviceId = getPersistentDeviceId() as String? else {
            self.slackPrint("❌ Failed to get deviceId")
            return
        }
        
        let urlString = "\(serverUrl)/pushapp/api/v1/notification/in-app/poll"
        guard let url = URL(string: urlString) else {
            self.slackPrint("❌ Invalid URL for in-app polling: \(urlString)")
            return
        }
        
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        
        // Attach device headers
        let deviceHeaders = getDeviceHeaders()
        for (key, value) in deviceHeaders {
            request.setValue(value, forHTTPHeaderField: key)
        }
        self.slackPrint("📩 Device headers attached for in-app poll: \(deviceHeaders)")
        
        let bodyPayload = ["contact_id": userId + "_" + deviceId]
        if let jsonData = try? JSONSerialization.data(withJSONObject: bodyPayload, options: .prettyPrinted),
           let jsonString = String(data: jsonData, encoding: .utf8) {
            self.slackPrint("📤 Sending in-app poll request to: \(urlString)")
            self.slackPrint("📦 In-App Poll Payload: \(jsonString)")
            request.httpBody = jsonData
        } else {
            self.slackPrint("❌ Failed to serialize in-app poll payload")
            return
        }
        
        URLSession.shared.dataTask(with: request) { data, response, error in
            if let error = error {
                self.slackPrint("❌ In-app poll request failed: \(error.localizedDescription)")
                return
            }
            
            if let httpResponse = response as? HTTPURLResponse {
                self.slackPrint("🌐 In-app poll Response Status: \(httpResponse.statusCode)")
                self.slackPrint("🌐 Response Headers: \(httpResponse.allHeaderFields)")
            }
            
            guard let data = data else {
                self.slackPrint("❌ No data received for in-app poll request")
                return
            }
            
            if let rawString = String(data: data, encoding: .utf8) {
                self.slackPrint("📥 Raw In-App Poll Response: \(rawString)")
            }
            
            do {
                if let responseDict = try JSONSerialization.jsonObject(with: data, options: [.allowFragments]) as? [String: Any] {
                    let success = responseDict["success"] as? Bool ?? false
                    self.slackPrint("✅ Poll success: \(success)")
                    
                    if success, let notifications = responseDict["results"] as? [[String: Any]] {
                        self.slackPrint("📬 Notifications received: \(notifications.count)")
                        for (index, notif) in notifications.enumerated() {
                            self.slackPrint("🔹 Notification \(index + 1): \(notif)")
                        }
                        if notifications.count > 0{
                            DispatchQueue.main.async {
                                // --- Existing Overlay Notifications Logic ---
                                // Filter out notifications where style.layout == "inline"
                                let overlayNotifications = notifications.filter { notification in
                                    self.slackPrint("🔹 Notification: \(notification["template"])")
                                    guard let template = notification["template"] as? [String : Any],
                                        let style = template["style"] as? [String: Any],
                                        let layout = style["code"] as? String else {
                                        // Treat notifications without style.layout as non-inline (i.e., overlay)
                                        return true
                                    }
                                    self.slackPrint("🔹 Layout: \(layout)")
                                    return layout != "inline"
                                }
                                self.slackPrint("🔹 Overlay Notifications: \(overlayNotifications)")
                                self.inAppDisplay?.showInApp(from: overlayNotifications)

                                // --- Corrected Inline Notifications Logic ---
                                // Filter for notifications where style.layout == "inline"
                                let inlineNotifications = notifications.filter { notification in
                                    guard let template = notification["template"] as? [String : Any],
                                        let style = template["style"] as? [String: Any],
                                        let layout = style["code"] as? String else {
                                        return false // Must have style.layout to be an inline notification
                                    }
                                    return layout == "inline"
                                }
                                self.slackPrint("🔹 Inline Notifications: \(inlineNotifications)")
                                
                                for notification in inlineNotifications {
                                    let event = notification["event"] as? [String : Any]
                                    let eventData = event!["event_data"] as? [String : Any]
                                    let compareId = eventData!["compare"] as? String
                                    let messageId = notification["messageId"] as? String
                                    let template = notification["template"] as? [String : Any]
                                    let style = template!["style"] as? [String: Any]
                                    let html = style!["html"] as? String
                                     let containerView = self.placeholderViews[compareId!]
                                    self.slackPrint("🔹 Template: \(notification["template"] as? [String : Any])")
                                    self.slackPrint("🔹 Style: \(template!["style"] as? [String: Any])")
                                    self.slackPrint("🔹 HTML: \(style!["html"] as? String)")
                                    self.slackPrint("🔹 Placeholder: \(self.placeholderViews[compareId!]!)")
                                    self.renderInlineHtml(html: html!, messageId: messageId!, in: containerView!)
                                    // if let messageId = notification["messageId"] as? String,
                                    // // Note: The placeholder ID is correctly extracted from the inAppMetadata/compare field
                                    // let template = notification["template"] as? [String : Any],
                                    // let style = template["style"] as? [String: Any],
                                    // let htmlContent = style["html"] as? String,
                                    // let containerView = self.placeholderViews[compareId] {
                                        
                                    //     // ⭐️ Found a matching native container view
                                    //     self.renderInlineHtml(html: htmlContent, messageId: messageId, in: containerView)
                                        
                                    // } else {
                                    //     self.slackPrint("⚠️ Inline notification received for \(compareId), but no matching native view, content, or metadata found.")
                                    // }
                                }

                                // 3. ⭐️ --- NEW TOOLTIP Notifications Logic ---
                                            let tooltipNotifications = notifications.filter { notification in
                                                // ... (existing filter for layout == "tooltip") ...
                                                guard let template = notification["template"] as? [String : Any],
                                                      let style = template["style"] as? [String: Any],
                                                      let layout = style["code"] as? String else { return false }
                                                return layout == "tooltip"
                                            }
                                            self.slackPrint("🔹 Tooltip Notifications: \(tooltipNotifications)")

                                            for notification in tooltipNotifications {
                                                let event = notification["event"] as? [String : Any]
                                                let eventData = event?["event_data"] as? [String : Any]
                                                let compareId = eventData?["compare"] as? String
                                                let messageId = notification["messageId"] as? String
                                                let template = notification["template"] as? [String : Any]
                                                let style = template?["style"] as? [String: Any]
                                                
                                                let line1 = style?["line_1"] as? String ?? ""
                                                let richline1 = style?["richline_1"] as? String
                                                let line2 = style?["line_2"] as? String ?? ""
                                                let iconHtml = style?["line1_icon"] as? String
                                                let line1Color = style?["line1_font_color"] as? String
                                                let line2Color = style?["line2_font_color"] as? String
                                                let bgColorHex = style?["bg_color"] as? String ?? "#fffffe"
                                                let line2FontSize: Double = (style?["line2_font_size"] as? NSNumber)?.doubleValue ?? (style?["line2_font_size"] as? Int).map { Double($0) } ?? 12
                                                let line2TextStyles = style?["line2_text_styles"] as? [String]
                                                let line1FontSize: Double = (style?["line1_font_size"] as? NSNumber)?.doubleValue ?? (style?["line1_font_size"] as? Int).map { Double($0) } ?? 14
                                                let line1TextStyles = style?["line1_text_styles"] as? [String]
                                                
                                                if let compareId = compareId,
                                                   let messageId = messageId,
                                                   let targetRect = self.tooltipTargetRects[compareId] {
                                                    
                                                    self.slackPrint("🔹 Rendering TOOLTIP: \(compareId) at \(targetRect)")
                                                    
                                                    self.renderTooltipContent(
                                                        line1: line1,
                                                        richline1: richline1,
                                                        line2: line2,
                                                        iconHtml: iconHtml,
                                                        line1Color: line1Color,
                                                        line2Color: line2Color,
                                                        line1FontSize: line1FontSize,
                                                        line2FontSize: line2FontSize,
                                                        line1TextStyles: line1TextStyles,
                                                        line2TextStyles: line2TextStyles,
                                                        bgColorHex: bgColorHex,
                                                        messageId: messageId,
                                                        targetRect: targetRect
                                                    )
                                                    
                                                } else {
                                                    self.slackPrint("⚠️ Tooltip render failure for compareId: \(compareId ?? "nil"). Target Rect Found: \((compareId != nil && self.tooltipTargetRects[compareId!] != nil))")
                                                }
                                            }
                            }
                        }
                    } else {
                        self.slackPrint("⚠️ No notifications available or success=false")
                    }
                } else {
                    self.slackPrint("❌ In-app poll response is not a valid dictionary")
                }
            } catch {
                self.slackPrint("❌ Error parsing in-app poll response: \(error)")
            }
        }.resume()
    }

    // Inside PushApp class

    // Inside PushApp class (add this alongside renderInlineHtml)

    private func renderTooltipContent(line1: String,
                                      richline1: String?,
                                      line2: String,
                                      iconHtml: String?,
                                      line1Color: String?,
                                      line2Color: String?,
                                      line1FontSize: Double,
                                      line2FontSize: Double,
                                      line1TextStyles: [String]?,
                                      line2TextStyles: [String]?,
                                      bgColorHex: String,
                                      messageId: String,
                                      targetRect: CGRect) {
        guard self.topViewController() != nil else {
            self.slackPrint("❌ Cannot render tooltip: Top View Controller not found.")
            return
        }
        
        let mutableAttributedText = NSMutableAttributedString()
        if let rich = richline1, !rich.isEmpty, let attr = rich.parseHTMLToAttributedString(fontSize: CGFloat(line1FontSize), color: UIColor(hex: line1Color ?? "#000001") ?? .black) {
            var iconString = ""
            if let html = iconHtml {
                iconString = html.decodingHTMLEntities() + " "
            }
            if !iconString.isEmpty {
                mutableAttributedText.append(NSAttributedString(string: iconString, attributes: tooltipAttributes(fontSize: CGFloat(line1FontSize), colorHex: line1Color, textStyles: line1TextStyles)))
            }
            mutableAttributedText.append(attr)
            mutableAttributedText.append(NSAttributedString(string: "\n\n"))
        } else {
            var iconString = ""
            if let html = iconHtml {
                iconString = html.decodingHTMLEntities() + " "
            }
            let line1Attrs = tooltipAttributes(fontSize: CGFloat(line1FontSize), colorHex: line1Color, textStyles: line1TextStyles)
            mutableAttributedText.append(NSAttributedString(string: iconString + line1 + "\n\n", attributes: line1Attrs))
        }
        let line2Attrs = tooltipAttributes(fontSize: CGFloat(line2FontSize), colorHex: line2Color, textStyles: line2TextStyles)
        mutableAttributedText.append(NSAttributedString(string: line2, attributes: line2Attrs))

        // 2. Create the temporary, transparent anchor view
        let anchorView = UIView(frame: targetRect)
        // ⭐️ DEBUG: Temporarily set a background color to check if the view is visible
        anchorView.backgroundColor = .clear
        
        // Find the Key Window's root view
        guard let keyWindow = UIApplication.shared.connectedScenes
                .compactMap({ $0 as? UIWindowScene })
                .first(where: { $0.activationState == .foregroundActive })?
                .windows
                .first(where: { $0.isKeyWindow }) else {
                    self.slackPrint("❌ Cannot render tooltip: Key window not found.")
                    return
        }
        
        DispatchQueue.main.async {
                keyWindow.addSubview(anchorView)

                // 3. Configure EasyTipView preferences
                var preferences = EasyTipView.Preferences()
                preferences.drawing.backgroundColor = UIColor(hex: bgColorHex) ?? .white
                preferences.drawing.borderWidth = 0.5
                preferences.drawing.borderColor = UIColor.lightGray.withAlphaComponent(0.4)
                preferences.drawing.cornerRadius = 8
                // No shadow - avoids UIDropShadowView layout off main thread crash
                
                // Positioning & Dismissal
                preferences.drawing.arrowPosition = .any
                preferences.positioning.contentInsets = UIEdgeInsets(top: 10, left: 15, bottom: 10, right: 15)
//                preferences.positioning.superviewPad = 5.0

//                preferences.dismissOn.tap = true
//                preferences.hasCloseButton = true
                preferences.animating.dismissDuration = 0.5
                preferences.animating.showDuration = 0.5
                
                // 4. Create and show the tooltip
            let tooltip = EasyTipView(text: mutableAttributedText, preferences: preferences, delegate: nil)
                
                // Show with withinSuperview: nil
                tooltip.show(forView: anchorView, withinSuperview: nil)
                    
                self.slackPrint("✅ Tooltip displayed for messageId: \(messageId).")
            
            let closeButtonSize: CGFloat = 24
            let closeButton = UIButton(type: .system)
            closeButton.frame = CGRect(
                x: tooltip.bounds.width - closeButtonSize - 8,
                y: 8,
                width: closeButtonSize,
                height: closeButtonSize
            )
            closeButton.backgroundColor = UIColor.black.withAlphaComponent(0.5)
            closeButton.layer.cornerRadius = closeButtonSize / 2
            closeButton.setTitle("✕", for: .normal)
            closeButton.setTitleColor(.white, for: .normal)
            closeButton.titleLabel?.font = UIFont.boldSystemFont(ofSize: 14)
            closeButton.addTarget(nil, action: #selector(self.closeTooltip(_:)), for: .touchUpInside)
            tooltip.addSubview(closeButton)
            
            self.currentTooltip = tooltip
                    
                // 5. Clean up
//                DispatchQueue.main.asyncAfter(deadline: .now() + 15.0) {
//                    if tooltip.superview != nil {
//                        tooltip.dismiss()
//                    }
//                    anchorView.removeFromSuperview()
//                    self.slackPrint("✅ Tooltip anchor view and/or tooltip cleaned up.")
//                }
            }
    }
    
    @objc private func closeTooltip(_ sender: UIButton) {
        currentTooltip?.dismiss()
        currentTooltip = nil
//        isShowing = false
//        showNextIfNeeded()
    }

    private func renderInlineHtml(html: String, messageId: String, in containerView: UIView) {
        // Clear any previous views in the container
        containerView.subviews.forEach { $0.removeFromSuperview() }

        // 1. Create a WKWebView to render the HTML
        let webView = WKWebView(frame: containerView.bounds)
        webView.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        
        // Optional: Hide scrollbars and make background transparent for clean look
        webView.scrollView.isScrollEnabled = false
        webView.isOpaque = false
        webView.backgroundColor = UIColor.clear

        // 2. Load the HTML content
        webView.loadHTMLString(html, baseURL: nil)

        // 3. Add to the native container
        containerView.addSubview(webView)
        
        self.slackPrint("✅ HTML content loaded into inline WKWebView for messageId: \(messageId)")
        
        // You should also track the "display" event here
        // trackInAppEvent(messageId: messageId, event: "display", ...)
    }
    
    func ackInApp(contactId: String, messageId: String, completion: @escaping (Bool) -> Void) {
        let urlString = "\(serverUrl)/pushapp/api/v1/notification/in-app/ack"
        guard let url = URL(string: urlString) else {
            self.slackPrint("❌ Invalid URL for in-app ack: \(urlString)")
            completion(false)
            return
        }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")

        // Attach device headers
        let deviceHeaders = getDeviceHeaders()
        for (key, value) in deviceHeaders {
            request.setValue(value, forHTTPHeaderField: key)
        }

        let bodyPayload = [
            "contact_id": contactId,
            "messageId": messageId
        ]
        do {
            let jsonData = try JSONSerialization.data(withJSONObject: bodyPayload, options: [])
            request.httpBody = jsonData
        } catch {
            self.slackPrint("❌ Failed to serialize ack payload: \(error)")
            completion(false)
            return
        }

        URLSession.shared.dataTask(with: request) { data, response, error in
            if let error = error {
                self.slackPrint("❌ In-app ack request failed: \(error.localizedDescription)")
                completion(false)
                return
            }

            if let httpResponse = response as? HTTPURLResponse {
                self.slackPrint("🌐 In-app ack Response Status: \(httpResponse.statusCode)")
            }

            completion(true)
        }.resume()
    }
    
    
    func trackInAppEvent(messageId: String,
                         event: String,
                         filterId: String? = nil,
                         ctaId: String? = nil,
                         completion: @escaping (Bool) -> Void) {
        
        let urlString = "\(serverUrl)/pushapp/api/v1/notification/in-app/track"
        print(urlString)
        guard let url = URL(string: urlString) else {
            self.slackPrint("❌ Invalid URL for in-app track: \(urlString)")
            completion(false)
            return
        }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")

        // Attach device headers
        let deviceHeaders = getDeviceHeaders()
        for (key, value) in deviceHeaders {
            request.setValue(value, forHTTPHeaderField: key)
        }

        // Build payload
        var bodyPayload: [String: Any] = [
            "messageId": messageId,
            "event": event
        ]
        if let filterId = filterId {
            bodyPayload["filterId"] = filterId
        }
        if let ctaId = ctaId {
            bodyPayload["data"] = ["ctaId": ctaId]
        }
        
        if let jsonData = try? JSONSerialization.data(withJSONObject: bodyPayload, options: .prettyPrinted),
           let jsonString = String(data: jsonData, encoding: .utf8) {
            print("✅ Payload for in app track:\n\(jsonString)")
        } else {
            print("❌ Failed to serialize payload to JSON")
        }

        do {
            let jsonData = try JSONSerialization.data(withJSONObject: bodyPayload, options: [])
            request.httpBody = jsonData
        } catch {
            self.slackPrint("❌ Failed to serialize track payload: \(error)")
            completion(false)
            return
        }

        URLSession.shared.dataTask(with: request) { data, response, error in
            if let error = error {
                self.slackPrint("❌ In-app track request failed: \(error.localizedDescription)\(self.serverUrl)/api/v1/notification/in-app/track")
                completion(false)
                return
            }

            if let httpResponse = response as? HTTPURLResponse {
                self.slackPrint("🌐 In-app track Response Status: \(httpResponse.statusCode) for ")
            }

            completion(true)
        }.resume()
    }
    
    
    
    public func trackPushNotificationEvent(token: String,
                                    event: String,
                                    ctaId: String? = nil,
                                    completion: @escaping (Bool) -> Void) {
        let urlString = "\(serverUrl)/pushapp/api/v1/notification/push/track"
        print("🔵 Track push URL:", urlString)
        
        guard let url = URL(string: urlString) else {
            print("❌ Invalid push track URL: \(urlString)")
            completion(false)
            return
        }
        
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        
        // Add headers if needed (like device info)
        let deviceHeaders = getDeviceHeaders()
        for (key, value) in deviceHeaders {
            request.setValue(value, forHTTPHeaderField: key)
        }
        
        // Prepare payload
        var payload: [String: Any] = [
            "t": token,
            "event": event
        ]
        
        if let ctaId = ctaId {
            payload["data"] = ["ctaId": ctaId]
        }
        
        // Print for debugging
        if let jsonData = try? JSONSerialization.data(withJSONObject: payload, options: .prettyPrinted),
           let jsonString = String(data: jsonData, encoding: .utf8) {
            print("📦 Push Track Payload:\n\(jsonString)")
        }
        
        // Send request
        do {
            let jsonData = try JSONSerialization.data(withJSONObject: payload, options: [])
            request.httpBody = jsonData
        } catch {
            print("❌ JSON serialization failed: \(error)")
            completion(false)
            return
        }
        
        URLSession.shared.dataTask(with: request) { data, response, error in
            if let error = error {
                print("❌ Push track request failed: \(error.localizedDescription)")
                completion(false)
                return
            }
            if let httpResponse = response as? HTTPURLResponse {
                print("🌐 Push Track Response Status: \(httpResponse.statusCode)")
            }
            completion(true)
        }.resume()
    }







    
    private func registerNotificationCategories() {
        let categoryMap: [(String, [(title: String, id: String)])] = [
            ("CONFIRMATION_CATEGORY", [("Yes", "PUSHAPP_YES"), ("No", "PUSHAPP_NO")]),
            ("RESPONSE_CATEGORY", [("Accept", "PUSHAPP_ACCEPT"), ("Reject", "PUSHAPP_REJECT")]),
            ("SUBSCRIPTION_CATEGORY", [("Subscribe", "PUSHAPP_SUB"), ("Unsubscribe", "PUSHAPP_UNSUB")]),
            ("TRANSACTION_CATEGORY", [("Buy", "PUSHAPP_BUY"), ("Sell", "PUSHAPP_SELL")]),
            ("CONTENT_CATEGORY", [("View", "PUSHAPP_VIEW"), ("Add", "PUSHAPP_ADD")]),
            ("CHECKOUT_CATEGORY", [("Cart", "PUSHAPP_CART"), ("Pay", "PUSHAPP_PAY")]),
            ("FORM_ACTION_CATEGORY", [("Save", "PUSHAPP_SAVE"), ("Submit", "PUSHAPP_SUBMIT")]),
            ("DESTRUCTIVE_ACTION_CATEGORY", [("Cancel", "PUSHAPP_CANCEL"), ("Delete", "PUSHAPP_DELETE")]),
            ("CONTACT_CATEGORY", [("Call", "PUSHAPP_CALL"), ("Email", "PUSHAPP_EMAIL")])
        ]

        var categories: Set<UNNotificationCategory> = []

        for (categoryId, actionsInfo) in categoryMap {
            let actions = actionsInfo.map { info in
                UNNotificationAction(identifier: info.id, title: info.title, options: [.foreground])
            }

            let category = UNNotificationCategory(
                identifier: categoryId,
                actions: actions,
                intentIdentifiers: [],
                options: []
            )
            categories.insert(category)
        }

        UNUserNotificationCenter.current().setNotificationCategories(categories)
    }
    
    private func topViewController(base: UIViewController? = UIApplication.shared.connectedScenes
        .compactMap { ($0 as? UIWindowScene)?.keyWindow }
        .first?.rootViewController) -> UIViewController? {
        
        if let nav = base as? UINavigationController {
            return topViewController(base: nav.visibleViewController)
        } else if let tab = base as? UITabBarController {
            return topViewController(base: tab.selectedViewController)
        } else if let presented = base?.presentedViewController {
            return topViewController(base: presented)
        }
        return base
    }
    
    
    // Weak wrapper to avoid retaining Coordinator (and hence WKWebView)
    private class WeakInlineCoordinator {
        weak var coordinator: PushAppInlineView.Coordinator?
        init(_ coordinator: PushAppInlineView.Coordinator) { self.coordinator = coordinator }
    }

    // map placeholderId -> array of weak coordinators (support multiple instances for same id)
    private var inlineViews: [String: [WeakInlineCoordinator]] = [:]

    // register
    func registerInlineView(placeholderId: String, coordinator: PushAppInlineView.Coordinator) {
        DispatchQueue.main.async {
            var list = self.inlineViews[placeholderId] ?? []
            // avoid duplicate registrations of same coordinator
            if !list.contains(where: { $0.coordinator === coordinator }) {
                list.append(WeakInlineCoordinator(coordinator))
                self.inlineViews[placeholderId] = list
            }
            self.cleanupInlineViews()
        }
    }

    // unregister specific coordinator (called from Coordinator.deinit)
    func unregisterInlineView(placeholderId: String, coordinator: PushAppInlineView.Coordinator) {
        DispatchQueue.main.async {
            guard var list = self.inlineViews[placeholderId] else { return }
            list.removeAll { $0.coordinator === coordinator || $0.coordinator == nil }
            if list.isEmpty {
                self.inlineViews.removeValue(forKey: placeholderId)
            } else {
                self.inlineViews[placeholderId] = list
            }
        }
    }

    // remove nil entries
    private func cleanupInlineViews() {
        for (key, list) in inlineViews {
            let filtered = list.filter { $0.coordinator?.webView != nil }
            if filtered.isEmpty {
                inlineViews.removeValue(forKey: key)
            } else {
                inlineViews[key] = filtered
            }
        }
    }

    // render HTML into all coordinators registered for this placeholderId (SwiftUI) and into native placeholder container (registerPlaceholder) when present
    func renderInlineIfNeeded(placeholderId: String, html: String, messageId: String = "") {
        DispatchQueue.main.async {
            self.cleanupInlineViews()
            // 1. SwiftUI inline views (PushAppInlineView)
            if let list = self.inlineViews[placeholderId] {
                for weakWrapper in list {
                    weakWrapper.coordinator?.loadContent(html)
                }
                self.slackPrint("✅ Inline content injected into SwiftUI view(s) for placeholderId: \(placeholderId)")
            }
            // 2. Native placeholder container (from JS registerPlaceholder) – so content shows in Capacitor/Ionic placeholders
            if let containerView = self.placeholderViews[placeholderId] {
                self.renderInlineHtml(html: html, messageId: messageId.isEmpty ? "inline" : messageId, in: containerView)
                self.slackPrint("✅ Inline content injected into native placeholder for placeholderId: \(placeholderId)")
            }
        }
    }

}





@available(iOS 15.2, *)
public struct PushAppInlineView: UIViewRepresentable {
    public typealias UIViewType = WKWebView

    public let placeholderId: String

    // Coordinator holds a weak reference to the actual WKWebView
    public class Coordinator {
        public weak var webView: WKWebView?
        public let placeholderId: String

        public init(placeholderId: String) {
            self.placeholderId = placeholderId
            // register with SDK manager on creation
            PushApp.shared.registerInlineView(placeholderId: placeholderId, coordinator: self)
        }

        deinit {
            // unregister when coordinator is deallocated
            PushApp.shared.unregisterInlineView(placeholderId: placeholderId, coordinator: self)
        }

        // thread-safe HTML load
        public func loadContent(_ html: String) {
            DispatchQueue.main.async { [weak self] in
                self?.webView?.loadHTMLString(html, baseURL: nil)
            }
        }
    }

    public init(placeholderId: String) {
        self.placeholderId = placeholderId
    }

    public func makeCoordinator() -> Coordinator {
        Coordinator(placeholderId: placeholderId)
    }

    public func makeUIView(context: Context) -> WKWebView {
        let config = WKWebViewConfiguration()
        let webView = WKWebView(frame: .zero, configuration: config)
        webView.translatesAutoresizingMaskIntoConstraints = false
        context.coordinator.webView = webView
        
        PushApp.shared.sendEvent(
            eventName: "widget_open",
            eventData: ["compare": placeholderId]
        )
        return webView
    }

    public func updateUIView(_ uiView: WKWebView, context: Context) {
        // nothing here — content will be injected by PushApp when notification arrives
    }
}

// MARK: - WebSocketManager

@available(iOS 13.0, *)
class WebSocketManager: NSObject {
    private let userId: String
    private let onMessage: ([String: Any]) -> Void
    private var webSocketTask: URLSessionWebSocketTask?
    let slackWebhookURL = URL(string: "https://hooks.slack.com/services/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")!
    @available(iOS 15.2, *)
    private var url: URL {
        let baseUrl: String
        if PushApp.shared.sandbox {
            baseUrl = "https://\(PushApp.shared.tenant).mehery.com"
        } else {
            baseUrl = "https://\(PushApp.shared.tenant).mehery.com"
        }
        return URL(string: baseUrl.replacingOccurrences(of: "https", with: "wss") + "/pushapp")!
    }

    init(userId: String, onMessage: @escaping ([String: Any]) -> Void) {
        self.userId = userId
        self.onMessage = onMessage
    }

    @available(iOS 15.2, *)
    func connect() {
        let session = URLSession(configuration: .default, delegate: self, delegateQueue: OperationQueue())
        webSocketTask = session.webSocketTask(with: url)
        webSocketTask?.resume()
        listen()
        sendAuth()
    }
    
    func slackPrint(_ message: String) {
        // Print to console
        print(message)
        
        // Send to Slack
//        var request = URLRequest(url: slackWebhookURL)
//        request.httpMethod = "POST"
//        request.addValue("application/json", forHTTPHeaderField: "Content-Type")
//        
//        let payload = ["text": message]
//        
//        do {
//            request.httpBody = try JSONSerialization.data(withJSONObject: payload, options: [])
//        } catch {
//            print("⚠️ Failed to encode Slack message: \(error)")
//            return
//        }
//        
//        let task = URLSession.shared.dataTask(with: request) { _, response, error in
//            if let error = error {
//                print("⚠️ Slack send error: \(error)")
//            } else if let httpResponse = response as? HTTPURLResponse, httpResponse.statusCode != 200 {
//                print("⚠️ Slack returned status: \(httpResponse.statusCode)")
//            }
//        }
//        
//        task.resume()
    }

    private func sendAuth() {
        let authMessage: [String: Any] = [
            "type": "auth",
            "userId": userId
        ]
        if let data = try? JSONSerialization.data(withJSONObject: authMessage) {
            webSocketTask?.send(.data(data)) { error in
                if let error = error {
                    self.slackPrint("Auth error: \(error)")
                }
            }
        }
    }
    private func listen() {
        webSocketTask?.receive { [weak self] result in
            guard let self = self else { return }
            switch result {
            case .success(let message):
                switch message {
                case .data(let data):
                    do {
                        if let json = try JSONSerialization.jsonObject(with: data) as? [String: Any] {
                            self.slackPrint("WebSocket Received JSON (data): \(json)")
                            self.onMessage(json)
                        } else {
                            self.slackPrint("Failed to cast JSON from data to [String: Any]")
                        }
                    } catch {
                        self.slackPrint("JSON parsing error from data: \(error.localizedDescription)")
                    }

                case .string(let text):
                    if let data = text.data(using: .utf8) {
                        do {
                            if let json = try JSONSerialization.jsonObject(with: data) as? [String: Any] {
                                self.slackPrint("WebSocket Received JSON (string): \(json)")
                                self.onMessage(json)
                            } else {
                                self.slackPrint("Failed to cast JSON from string to [String: Any]")
                            }
                        } catch {
                            self.slackPrint("JSON parsing error from string: \(error.localizedDescription)")
                        }
                    } else {
                        self.slackPrint("Failed to convert string to Data")
                    }

                @unknown default:
                    self.slackPrint("Received unknown WebSocket message type")
                }
                // Continue listening for next message
                self.listen()

            case .failure(let error):
                self.slackPrint("WebSocket receive error: \(error.localizedDescription)")
                // Optionally, you can reconnect or handle error here
            }
        }
    }


}

@available(iOS 13.0, *)
extension WebSocketManager: URLSessionWebSocketDelegate {
    func urlSession(_ session: URLSession,
                    webSocketTask: URLSessionWebSocketTask,
                    didCloseWith closeCode: URLSessionWebSocketTask.CloseCode,
                    reason: Data?) {
        self.slackPrint("WebSocket closed")
    }
}

// MARK: - InAppDisplay

@available(iOS 13.0, *)
class InAppDisplay {
    private weak var context: UIViewController?
    private var queue: [[String: Any]] = []
    private var isShowing: Bool = false
    private var bannerWindow: UIWindow?
    private var floaterWindow: UIWindow?
    let slackWebhookURL = URL(string: "https://hooks.slack.com/services/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")!

    init(context: UIViewController) {
        self.context = context
        self.slackPrint("✅ InAppDisplay initialized with context: \(String(describing: context))")
    }

    @available(iOS 14.0, *)
    func showInApp(from notifications: [[String: Any]]) {
        self.slackPrint("📥 showInApp called with \(notifications.count) notifications")
        queue.append(contentsOf: notifications)
        showNextIfNeeded()
    }
    
    func slackPrint(_ message: String) {
        // Print to console
        print(message)
        
        // Send to Slack
//        var request = URLRequest(url: slackWebhookURL)
//        request.httpMethod = "POST"
//        request.addValue("application/json", forHTTPHeaderField: "Content-Type")
//        
//        let payload = ["text": message]
//        
//        do {
//            request.httpBody = try JSONSerialization.data(withJSONObject: payload, options: [])
//        } catch {
//            print("⚠️ Failed to encode Slack message: \(error)")
//            return
//        }
//        
//        let task = URLSession.shared.dataTask(with: request) { _, response, error in
//            if let error = error {
//                print("⚠️ Slack send error: \(error)")
//            } else if let httpResponse = response as? HTTPURLResponse, httpResponse.statusCode != 200 {
//                print("⚠️ Slack returned status: \(httpResponse.statusCode)")
//            }
//        }
//        
//        task.resume()
    }

    private func showNextIfNeeded() {
        self.slackPrint("🔄 showNextIfNeeded called. isShowing=\(isShowing), queue.count=\(queue.count)")
        guard !isShowing, !queue.isEmpty else {
            self.slackPrint("⏹ Nothing to show or already showing")
            return
        }
        isShowing = true

        let notif = queue.removeFirst()
        self.slackPrint("📤 Preparing notification: \(notif)")
        
        var contactId = ""
        
        if #available(iOS 15.2, *) {
            contactId = PushApp.shared.userId! + "_" + PushApp.shared.getPersistentDeviceId()
        } else {
            // Fallback on earlier versions
        }

        guard let template = notif["template"] as? [String: Any],
              let style = template["style"] as? [String: Any],
              let layout = style["code"] as? String,
              let event = notif["event"] as? [String: Any],
              let messageId = notif["messageId"] as? String,
              let filterId = notif["filterId"] as? String else {
            self.slackPrint("❌ Invalid notification format, skipping")
            isShowing = false
            showNextIfNeeded()
            return
        }

        let html = style["html"] as? String // optionackal

        // 🔔 Call ACK before showing
        if #available(iOS 15.2, *) {
            PushApp.shared.ackInApp(contactId: contactId, messageId: messageId) { [weak self] success in
                DispatchQueue.main.async {
                    guard let self = self else { return }
                    
                    guard success else {
                        self.slackPrint("⚠️ Failed to ack messageId: \(messageId), skipping display")
                        self.isShowing = false
                        self.showNextIfNeeded()
                        return
                    }
                    
                    self.slackPrint("✅ Ack sent for messageId: \(messageId)")
                    self.slackPrint("📐 Layout: \(layout)")
                    
                    // 🔹 Check if placeholderId exists in event_data.compare - dispatch to placeholder instead of showing as banner/roadblock
                    let eventData = event["event_data"] as? [String: Any]
                    if let placeholderId = eventData?["compare"] as? String, !placeholderId.isEmpty {
                        if let html = html {
                            self.slackPrint("📦 Dispatching to placeholder: \(placeholderId)")
                            PushApp.shared.renderInlineIfNeeded(placeholderId: placeholderId, html: html, messageId: messageId)
                            self.slackPrint("✅ Content dispatched to placeholder: \(placeholderId)")
                            // finish this notification and move to next
                            self.isShowing = false
                            self.showNextIfNeeded()
                            return
                        }
                    }
                    
                    if layout.contains("tooltip") {
                        let eventData = event["event_data"] as? [String: Any]
                        var placeholderId = template["code"] as? String ?? ""
                        if let compare = eventData?["compare"] as? String {
                            placeholderId = compare
                        }
                        print(placeholderId)
                        
                        let line1Plain = style["line_1"] as? String ?? ""
                        let richline1 = style["richline_1"] as? String
                        let message = style["line_2"] as? String ?? ""
                        let bgColorHex = style["bg_color"] as? String ?? "#000000"
                        let bgColor = UIColor(hex: bgColorHex) ?? .black
                        let line2FontSize: Double = (style["line2_font_size"] as? NSNumber)?.doubleValue ?? (style["line2_font_size"] as? Int).map { Double($0) } ?? 14
                        let line2ColorHex = style["line2_font_color"] as? String
                        let line2TextStyles = style["line2_text_styles"] as? [String]
                        let line1FontSize: Double = (style["line1_font_size"] as? NSNumber)?.doubleValue ?? (style["line1_font_size"] as? Int).map { Double($0) } ?? 16
                        let line1ColorHex = style["line1_font_color"] as? String
                        
                        if let targetView = TooltipRegistry.shared.view(for: placeholderId) {
                            var preferences = EasyTipView.Preferences()
                            preferences.drawing.backgroundColor = bgColor
                            preferences.drawing.arrowPosition = .any
                            preferences.positioning.maxWidth = UIScreen.main.bounds.width * 0.8
                            
                            let attributedText = NSMutableAttributedString()
                            if let rich = richline1, !rich.isEmpty, let attr = rich.parseHTMLToAttributedString(fontSize: CGFloat(line1FontSize), color: UIColor(hex: line1ColorHex ?? "#000001") ?? .black) {
                                attributedText.append(attr)
                                attributedText.append(NSAttributedString(string: "\n\n"))
                            } else {
                                let line1Attrs = tooltipAttributes(fontSize: CGFloat(line1FontSize), colorHex: line1ColorHex, textStyles: style["line1_text_styles"] as? [String])
                                attributedText.append(NSAttributedString(string: line1Plain + "\n\n", attributes: line1Attrs))
                            }
                            let line2Attrs = tooltipAttributes(fontSize: CGFloat(line2FontSize), colorHex: line2ColorHex, textStyles: line2TextStyles)
                            attributedText.append(NSAttributedString(string: message, attributes: line2Attrs))
                            
                            let tipView = EasyTipView(text: attributedText, preferences: preferences, delegate: nil)
                            tipView.show(forView: targetView)
                            self.slackPrint("✅ Tooltip shown for placeholderId: \(placeholderId)")
                            
                            let closeButtonSize: CGFloat = 24
                            let closeButton = UIButton(type: .system)
                            closeButton.frame = CGRect(
                                x: tipView.bounds.width - closeButtonSize - 8,
                                y: 8,
                                width: closeButtonSize,
                                height: closeButtonSize
                            )
                            closeButton.backgroundColor = UIColor.black.withAlphaComponent(0.5)
                            closeButton.layer.cornerRadius = closeButtonSize / 2
                            closeButton.setTitle("✕", for: .normal)
                            closeButton.setTitleColor(.white, for: .normal)
                            closeButton.titleLabel?.font = UIFont.boldSystemFont(ofSize: 14)
                            closeButton.addTarget(nil, action: #selector(self.closeTooltip(_:)), for: .touchUpInside)
                            tipView.addSubview(closeButton)
                            
                            self.currentTooltip = tipView
                        }
                    } else if layout.contains("inline") {
                        if let html = style["html"] as? String {
                            // Placeholder id: prefer event_data.compare (so backend can target registered id e.g. "login_banner"), else template code (e.g. "ne_inli")
                            var placeholderId = template["code"] as? String ?? ""
                            if let eventData = event["event_data"] as? [String: Any],
                               let compare = eventData["compare"] as? String, !compare.isEmpty {
                                placeholderId = compare
                            }

                            PushApp.shared.renderInlineIfNeeded(placeholderId: placeholderId, html: html, messageId: messageId)
                            self.slackPrint("✅ Inline content injected for placeholderId: \(placeholderId)")
                        } else {
                            self.slackPrint("⚠️ inline layout but no html found")
                        }
                        // finish this notification and move to next
                        self.isShowing = false
                        self.showNextIfNeeded()
                    } else if layout.contains("roadblock") {
                        self.showPopup(messageId: messageId,filterId: filterId,html!) { [weak self] in
                            self?.slackPrint("✅ Popup closed")
                            self?.isShowing = false
                            self?.showNextIfNeeded()
                        }
                    } else if layout.contains("bottomsheet") {
                        self.showBottomSheet(messageId: messageId, filterId: filterId,html!) { [weak self] in
                            self?.slackPrint("✅ Bottom Sheet closed")
                            PushApp.shared.trackInAppEvent(messageId: messageId, event: "dismissed") { success in
                                if success {
                                    self?.slackPrint("📩 Dismiss event tracked")
                                }
                            }
                            self?.isShowing = false
                            self?.showNextIfNeeded()
                        }
                    } else if layout.contains("banner") {
                        self.showBanner(messageId: messageId, filterId: filterId,html!) { [weak self] in
                            self?.slackPrint("✅ Banner closed")
                            PushApp.shared.trackInAppEvent(messageId: messageId, event: "dismissed") { success in
                                if success {
                                    self?.slackPrint("📩 Dismiss event tracked")
                                }
                            }
                            self?.isShowing = false
                            self?.showNextIfNeeded()
                        }
                    } else if layout.contains("picture-in-picture") {
                        let v_a = (style["vertical_align"] as? String) ?? "flex_end"
                        let h_a = (style["horizontal_align"] as? String) ?? "flex_end"
                        self.showPictureInPicture(messageId: messageId, filterId: filterId,html!, h_a: h_a, v_a: v_a) { [weak self] in
                            self?.slackPrint("✅ PIP closed")
                            PushApp.shared.trackInAppEvent(messageId: messageId, event: "dismissed") { success in
                                if success {
                                    self?.slackPrint("📩 Dismiss event tracked")
                                }
                            }
                            self?.isShowing = false
                            self?.showNextIfNeeded()
                        }
                    } else if layout.contains("floater") {
                        let v_a = (style["vertical_align"] as? String) ?? "flex_end"
                        let h_a = (style["horizontal_align"] as? String) ?? "flex_end"
                        self.showFloater(html!, h_a: h_a, v_a: v_a) { [weak self] in
                            self?.slackPrint("✅ Floater closed")
                            PushApp.shared.trackInAppEvent(messageId: messageId, event: "dismissed") { success in
                                if success {
                                    self?.slackPrint("📩 Dismiss event tracked")
                                }
                            }
                            self?.isShowing = false
                            self?.showNextIfNeeded()
                        }
                    } else {
                        self.slackPrint("❌ Unknown layout: \(layout)")
                        self.isShowing = false
                        self.showNextIfNeeded()
                    }
                }
            }
        } else {
            // Fallback on earlier versions
        }
    }

    
    // MARK: - Selector for close button
    @objc private func closeTooltip(_ sender: UIButton) {
        currentTooltip?.dismiss()
        currentTooltip = nil
        isShowing = false
        showNextIfNeeded()
    }

    // MARK: - Property to hold current tooltip
    private var currentTooltip: EasyTipView?
    
    
    class ScriptMessageHandler: NSObject, WKScriptMessageHandler {
        private let callback: (String) -> Void
        private let messageId: String  // we need this to track CTA against a specific messageId
        private let filterId: String  // we need this to track CTA against a specific messageId

        init(messageId: String, filterId: String, callback: @escaping (String) -> Void) {
            self.messageId = messageId
            self.filterId = filterId
            self.callback = callback
        }

        func userContentController(_ userContentController: WKUserContentController,
                                   didReceive message: WKScriptMessage) {
            if let body = message.body as? String {
                print("📩 JS -> Native message: \(body)")
                
                if let data = body.data(using: .utf8) {
                    do {
                        if let messageObject = try JSONSerialization.jsonObject(with: data) as? [String: Any] {
                            
                            // Extract CTA ID from messageObject["data"]["code"]
                            if let dataDict = messageObject["data"] as? [String: Any],
                               let ctaId = dataDict["value"] as? String {
                                
                                if let url = URL(string: ctaId), url.scheme != nil, UIApplication.shared.canOpenURL(url) {
                                        // Case 1: ctaId is a URL → open it
                                        DispatchQueue.main.async {
                                            UIApplication.shared.open(url, options: [:], completionHandler: nil)
                                        }
                                    } else {
                                        // Case 2: ctaId is just a string → handle accordingly
                                        print("CTA String ID: \(ctaId)")
                                        // e.g., navigate to a screen, call an API, etc.
                                    }
                                
                                // Call your in-app tracking API
                                if #available(iOS 15.2, *) {
                                    PushApp.shared.trackInAppEvent(messageId: messageId,
                                                                   event: "cta", filterId: filterId,
                                                                   ctaId: ctaId) { success in
                                        if success {
                                            print("✅ CTA tracked with ctaId=\(ctaId)")
                                        } else {
                                            print("❌ Failed to track CTA")
                                        }
                                    }
                                } else {
                                    // Fallback on earlier versions
                                }
                            }
                        }
                    } catch {
                        print("❌ Failed to parse JS message JSON: \(error)")
                    }
                }
                
                // Ensure callback is called on main thread to avoid UI modification errors
                DispatchQueue.main.async {
                    self.callback(body) // still returning raw string
                }
            } else if let dict = message.body as? [String: Any] {
                if let data = try? JSONSerialization.data(withJSONObject: dict),
                   let json = String(data: data, encoding: .utf8) {
                    print("📩 JS -> Native message: \(json)")
                    // Ensure callback is called on main thread to avoid UI modification errors
                    DispatchQueue.main.async {
                        self.callback(json)
                    }
                }
            }
        }
    }


    private func makeWebView(filterId : String,messageId: String, html: String, onMessage: @escaping (String) -> Void, loadImmediately: Bool = true) -> WKWebView {
        // Ensure we're on the main thread for all view creation
        assert(Thread.isMainThread, "makeWebView must be called on main thread")
        
        let contentController = WKUserContentController()

        // 1. Inject your bridge function
        let js = """
        window.handleClick = function(eventType,lab,val) {
          console.log(">>> Patched handleClick called", eventType, val);
          var message = JSON.stringify({
            event: eventType,
            timestamp: Date.now(),
            data: { url: "", label: lab, value: val }
          });
          window.webkit.messageHandlers.inApp.postMessage(message);
        };
        """
        let userScript = WKUserScript(source: js, injectionTime: .atDocumentEnd, forMainFrameOnly: true)
        contentController.addUserScript(userScript)

        // 2. Inject autoplay script for iOS
        let autoplayJS = """
        document.addEventListener('DOMContentLoaded', function() {
            document.querySelectorAll('video').forEach(function(v) {
                v.muted = true;
                v.playsInline = true;
                var playPromise = v.play();
                if (playPromise !== undefined) {
                    playPromise.catch(function(error) {
                        console.log('iOS autoplay blocked', error);
                    });
                }
            });
        });
        """
        let autoplayScript = WKUserScript(source: autoplayJS, injectionTime: .atDocumentEnd, forMainFrameOnly: true)
        contentController.addUserScript(autoplayScript)

        // 3. Register message handler
        let handler = ScriptMessageHandler(messageId: messageId,filterId : filterId, callback: onMessage)
        contentController.add(handler, name: "inApp")

        // 4. Create WKWebView with configuration
        let config = WKWebViewConfiguration()
        config.userContentController = contentController
        config.allowsInlineMediaPlayback = true
        if #available(iOS 10.0, *) {
            config.mediaTypesRequiringUserActionForPlayback = [] // allow autoplay
        } else {
            config.requiresUserActionForMediaPlayback = false
        }

        let webView = WKWebView(frame: .zero, configuration: config)
        // Disable scrolling inside WKWebView
        webView.scrollView.isScrollEnabled = false
        webView.scrollView.bounces = false
        
        // Only load HTML immediately if requested, otherwise caller will load after adding to hierarchy
        if loadImmediately {
            webView.loadHTMLString(html, baseURL: nil)
        }
        
        return webView
    }


    @available(iOS 15.2, *)
    private func showPopup(messageId : String,filterId : String,_ html: String,
                           onClose: @escaping () -> Void) {
        guard let context = self.context else { return }

        DispatchQueue.main.async {
            // Handler that just closes the popup
            let webView = self.makeWebView(filterId: filterId, messageId: messageId,html: html) { _ in
                DispatchQueue.main.async {
                    context.dismiss(animated: true, completion: onClose)
                }
            }

            let vc = UIViewController()
            vc.view.backgroundColor = UIColor.black.withAlphaComponent(0.7)
            vc.modalPresentationStyle = .overFullScreen
            vc.view.addSubview(webView)

            webView.translatesAutoresizingMaskIntoConstraints = false
            NSLayoutConstraint.activate([
                webView.topAnchor.constraint(equalTo: vc.view.topAnchor),
                webView.bottomAnchor.constraint(equalTo: vc.view.bottomAnchor),
                webView.leadingAnchor.constraint(equalTo: vc.view.leadingAnchor),
                webView.trailingAnchor.constraint(equalTo: vc.view.trailingAnchor)
            ])

            // Add close button (manual close)
            let closeBtn = UIButton(type: .custom)
            closeBtn.setTitle("✕", for: .normal)
            closeBtn.setTitleColor(.white, for: .normal)
            closeBtn.titleLabel?.font = UIFont.boldSystemFont(ofSize: 18)
            closeBtn.backgroundColor = UIColor.black.withAlphaComponent(0.6)
            closeBtn.layer.cornerRadius = 20
            closeBtn.clipsToBounds = true
            closeBtn.translatesAutoresizingMaskIntoConstraints = false
            closeBtn.addTargetClosure { _ in
                PushApp.shared.trackInAppEvent(messageId: messageId, event: "dismissed") { success in
                    if success {
                        print("📩 Dismiss event tracked")
                    }
                }
                vc.dismiss(animated: true, completion: onClose)
            }
            vc.view.addSubview(closeBtn)

            NSLayoutConstraint.activate([
                closeBtn.topAnchor.constraint(equalTo: vc.view.safeAreaLayoutGuide.topAnchor, constant: 20),
                closeBtn.trailingAnchor.constraint(equalTo: vc.view.trailingAnchor, constant: -20),
                closeBtn.widthAnchor.constraint(equalToConstant: 40),
                closeBtn.heightAnchor.constraint(equalToConstant: 40)
            ])

            context.present(vc, animated: true)
        }
    }

    
    
    private func showBottomSheet(messageId: String,filterId: String,_ html: String, onClose: @escaping () -> Void) {
        guard let context = self.context else {
            self.slackPrint("❌ No context for bottom sheet")
            onClose()
            return
        }

        self.slackPrint("📢 Showing bottom sheet")

        DispatchQueue.main.async {
            let vc = UIViewController()
            vc.view.backgroundColor = UIColor.black.withAlphaComponent(0.4) // dim background
            vc.modalPresentationStyle = .overCurrentContext
            vc.modalTransitionStyle = .crossDissolve

            // Container view for sheet
            let container = UIView()
            container.translatesAutoresizingMaskIntoConstraints = false
            container.backgroundColor = .white
            container.layer.cornerRadius = 16
            container.layer.maskedCorners = [.layerMinXMinYCorner, .layerMaxXMinYCorner]
            container.clipsToBounds = true
            vc.view.addSubview(container)

            // Keep a reference to bottom constraint
            let bottomConstraint = container.bottomAnchor.constraint(
                equalTo: vc.view.bottomAnchor,
                constant: vc.view.bounds.height * 0.5
            )

            NSLayoutConstraint.activate([
                container.leadingAnchor.constraint(equalTo: vc.view.leadingAnchor),
                container.trailingAnchor.constraint(equalTo: vc.view.trailingAnchor),
                container.heightAnchor.constraint(equalTo: vc.view.heightAnchor, multiplier: 0.5),
                bottomConstraint
            ])

            // WebView (auto-dismiss on JS message)
            let webView = self.makeWebView(filterId: filterId, messageId : messageId,html: html) { _ in
                DispatchQueue.main.async {
                    self.slackPrint("📩 Bottom sheet message received, dismissing")
                    vc.dismiss(animated: true, completion: onClose)
                }
            }
            webView.translatesAutoresizingMaskIntoConstraints = false
            container.addSubview(webView)

            NSLayoutConstraint.activate([
                webView.topAnchor.constraint(equalTo: container.topAnchor),
                webView.bottomAnchor.constraint(equalTo: container.bottomAnchor),
                webView.leadingAnchor.constraint(equalTo: container.leadingAnchor),
                webView.trailingAnchor.constraint(equalTo: container.trailingAnchor)
            ])

            // Close button
            let closeBtn = UIButton(type: .custom)
            closeBtn.setTitle("✕", for: .normal)
            closeBtn.setTitleColor(.white, for: .normal)
            closeBtn.titleLabel?.font = UIFont.boldSystemFont(ofSize: 18)
            closeBtn.backgroundColor = UIColor.black.withAlphaComponent(0.6)
            closeBtn.layer.cornerRadius = 20
            closeBtn.clipsToBounds = true
            closeBtn.addTargetClosure { _ in
                self.slackPrint("❌ Bottom sheet close button tapped")
                vc.dismiss(animated: true, completion: onClose)
            }

            closeBtn.translatesAutoresizingMaskIntoConstraints = false
            container.addSubview(closeBtn)

            NSLayoutConstraint.activate([
                closeBtn.topAnchor.constraint(equalTo: container.topAnchor, constant: 10),
                closeBtn.trailingAnchor.constraint(equalTo: container.trailingAnchor, constant: -10),
                closeBtn.widthAnchor.constraint(equalToConstant: 40),
                closeBtn.heightAnchor.constraint(equalToConstant: 40)
            ])

            // Present VC
            context.present(vc, animated: false) {
                self.slackPrint("✅ Bottom sheet presented")

                // Animate slide-up
                vc.view.layoutIfNeeded()
                bottomConstraint.constant = 0
                UIView.animate(withDuration: 0.3) {
                    vc.view.layoutIfNeeded()
                }
            }
        }
    }


    private func showBanner(messageId : String,filterId : String,_ html: String, onClose: @escaping () -> Void) {
        guard let context = self.context else {
            self.slackPrint("❌ No context for banner")
            onClose()
            return
        }
        self.slackPrint("📢 Showing banner")

        // Ensure we're on the main thread
        if Thread.isMainThread {
            self._showBannerOnMainThread(messageId: messageId, filterId: filterId, html: html, context: context, onClose: onClose)
        } else {
            DispatchQueue.main.async { [weak self] in
                guard let self = self else { return }
                self._showBannerOnMainThread(messageId: messageId, filterId: filterId, html: html, context: context, onClose: onClose)
            }
        }
    }
    
    private func _showBannerOnMainThread(messageId: String, filterId: String, html: String, context: UIViewController, onClose: @escaping () -> Void) {
        assert(Thread.isMainThread, "This method must be called on main thread")
        
        // Dedicated window so banner hierarchy is isolated from the app. When WKWebView loads it can
        // trigger layout on a background thread; if the banner were in context.view, that layout
        // would touch the app's hierarchy and any UIDropShadowView there → crash. Here only our
        // window's hierarchy is involved and it has a root view controller.
        let window: UIWindow
        if #available(iOS 13.0, *) {
            let keyWindow = UIApplication.shared.connectedScenes
                .compactMap { $0 as? UIWindowScene }
                .flatMap { $0.windows }
                .first { $0.isKeyWindow }
            if let scene = keyWindow?.windowScene {
                window = UIWindow(windowScene: scene)
            } else {
                window = UIWindow(frame: UIScreen.main.bounds)
            }
        } else {
            window = UIWindow(frame: UIScreen.main.bounds)
        }
        window.windowLevel = .alert + 1
        window.backgroundColor = .clear
        window.isUserInteractionEnabled = true
        if let keyWindow = UIApplication.shared.connectedScenes
            .compactMap({ $0 as? UIWindowScene })
            .flatMap({ $0.windows })
            .first(where: { $0.isKeyWindow }) {
            window.frame = keyWindow.bounds
        } else {
            window.frame = UIScreen.main.bounds
        }
        let rootVC = UIViewController()
        rootVC.view.backgroundColor = .clear
        window.rootViewController = rootVC
        self.bannerWindow = window

        var bannerView: WKWebView?
        let contentView = rootVC.view!

        // Banner container (no layer shadow - avoids UIDropShadowView)
        let shadowContainer = UIView()
        shadowContainer.translatesAutoresizingMaskIntoConstraints = false
        shadowContainer.layer.cornerRadius = 8
        shadowContainer.layer.masksToBounds = true
        shadowContainer.backgroundColor = UIColor.systemBackground

        contentView.addSubview(shadowContainer)
        NSLayoutConstraint.activate([
            shadowContainer.leadingAnchor.constraint(equalTo: contentView.leadingAnchor, constant: 16),
            shadowContainer.trailingAnchor.constraint(equalTo: contentView.trailingAnchor, constant: -16),
            shadowContainer.topAnchor.constraint(equalTo: contentView.safeAreaLayoutGuide.topAnchor, constant: 16),
            shadowContainer.heightAnchor.constraint(equalToConstant: 100)
        ])

        let btn = UIButton(type: .custom)
        btn.setTitle("✕", for: .normal)
        btn.setTitleColor(.black, for: .normal)
        btn.backgroundColor = UIColor.white.withAlphaComponent(0.7)
        btn.layer.cornerRadius = 12
        btn.translatesAutoresizingMaskIntoConstraints = false

        let dismissBanner: () -> Void = { [weak self] in
            DispatchQueue.main.async {
                guard let self = self else { return }
                shadowContainer.removeFromSuperview()
                btn.removeFromSuperview()
                self.bannerWindow?.isHidden = true
                self.bannerWindow?.rootViewController = nil
                self.bannerWindow = nil
                onClose()
            }
        }

        bannerView = self.makeWebView(filterId: filterId, messageId: messageId, html: html, loadImmediately: false) { _ in
            self.slackPrint("📩 Banner message received, dismissing")
            dismissBanner()
        }

        guard let bannerViewUnwrapped = bannerView else { return }

        shadowContainer.addSubview(bannerViewUnwrapped)
        bannerViewUnwrapped.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            bannerViewUnwrapped.leadingAnchor.constraint(equalTo: shadowContainer.leadingAnchor),
            bannerViewUnwrapped.trailingAnchor.constraint(equalTo: shadowContainer.trailingAnchor),
            bannerViewUnwrapped.topAnchor.constraint(equalTo: shadowContainer.topAnchor),
            bannerViewUnwrapped.bottomAnchor.constraint(equalTo: shadowContainer.bottomAnchor)
        ])
        bannerViewUnwrapped.layer.cornerRadius = 8
        bannerViewUnwrapped.clipsToBounds = true

        contentView.addSubview(btn)
        NSLayoutConstraint.activate([
            btn.topAnchor.constraint(equalTo: shadowContainer.topAnchor, constant: 8),
            btn.trailingAnchor.constraint(equalTo: shadowContainer.trailingAnchor, constant: -8),
            btn.widthAnchor.constraint(equalToConstant: 24),
            btn.heightAnchor.constraint(equalToConstant: 24)
        ])

        contentView.layoutIfNeeded()
        shadowContainer.layoutIfNeeded()

        window.makeKeyAndVisible()

        DispatchQueue.main.asyncAfter(deadline: .now() + 0.05) {
            guard Thread.isMainThread else { return }
            bannerViewUnwrapped.loadHTMLString(html, baseURL: nil)
        }

        btn.addTargetClosure { _ in
            self.slackPrint("❌ Banner close button tapped")
            dismissBanner()
        }
    }


    private var pipWindow: UIWindow?
    @available(iOS 15.2, *)
private func showPictureInPicture(
    messageId: String,
    filterId: String,
    _ html: String,
    h_a: String = "flex_end",
    v_a: String = "flex_end",
    onClose: @escaping () -> Void
) {
    DispatchQueue.main.async { [weak self] in
        guard let self = self else { return }

        self.slackPrint("📢 Showing PIP")

        let window = UIWindow(frame: UIScreen.main.bounds)
        window.windowLevel = .alert + 1
        window.backgroundColor = .clear

        let rootVC = UIViewController()
        rootVC.view.backgroundColor = .clear
        window.rootViewController = rootVC
        window.makeKeyAndVisible()

        self.pipWindow = window

        self.buildPIP(
            hostView: rootVC.view,
            messageId: messageId,
            filterId: filterId,
            html: html,
            h_a: h_a,
            v_a: v_a,
            onClose: {
                DispatchQueue.main.async {
                    self.pipWindow?.isHidden = true
                    self.pipWindow = nil
                    onClose()
                }
            }
        )
    }
}

/// Normalizes vertical/horizontal alignment from API (flex-start, flex-end, center) to a single form for switching.
private func normalizeAlignment(_ value: String) -> String {
    switch value.lowercased() {
    case "flex-start", "flex_start": return "flex_start"
    case "flex-end", "flex_end": return "flex_end"
    case "center": return "center"
    default: return "flex_end"
    }
}

@MainActor
private func buildPIP(
    hostView: UIView,
    messageId: String,
    filterId: String,
    html: String,
    h_a: String,
    v_a: String,
    onClose: @escaping () -> Void
) {
    let h = normalizeAlignment(h_a)
    let v = normalizeAlignment(v_a)

    let container = UIView()
    container.translatesAutoresizingMaskIntoConstraints = false
    container.layer.cornerRadius = 8
    container.layer.masksToBounds = true
    // No layer shadow - avoids UIDropShadowView layout off main thread crash

    // Config with autoplay for video (muted + playsInline + play on load)
    let contentController = WKUserContentController()
    let autoplayJS = """
    document.addEventListener('DOMContentLoaded', function() {
        document.querySelectorAll('video').forEach(function(v) {
            v.muted = true;
            v.playsInline = true;
            var playPromise = v.play();
            if (playPromise !== undefined) {
                playPromise.catch(function(err) { console.log('PIP autoplay', err); });
            }
        });
    });
    """
    contentController.addUserScript(WKUserScript(source: autoplayJS, injectionTime: .atDocumentEnd, forMainFrameOnly: true))
    let config = WKWebViewConfiguration()
    config.userContentController = contentController
    config.allowsInlineMediaPlayback = true
    if #available(iOS 10.0, *) {
        config.mediaTypesRequiringUserActionForPlayback = []
    }

    let webView = WKWebView(frame: .zero, configuration: config)
    webView.translatesAutoresizingMaskIntoConstraints = false
    webView.loadHTMLString(html, baseURL: nil)
    webView.isOpaque = false
    webView.backgroundColor = .clear
    webView.scrollView.isScrollEnabled = false

    container.addSubview(webView)
    hostView.addSubview(container)

    let margin: CGFloat = 20
    let bottomMargin: CGFloat = 40
    let guide = hostView.safeAreaLayoutGuide

    var constraints: [NSLayoutConstraint] = [
        container.widthAnchor.constraint(equalToConstant: UIScreen.main.bounds.width / 3),
        container.heightAnchor.constraint(equalToConstant: UIScreen.main.bounds.height / 3),
        webView.topAnchor.constraint(equalTo: container.topAnchor),
        webView.bottomAnchor.constraint(equalTo: container.bottomAnchor),
        webView.leadingAnchor.constraint(equalTo: container.leadingAnchor),
        webView.trailingAnchor.constraint(equalTo: container.trailingAnchor)
    ]

    switch v {
    case "flex_start":
        constraints.append(container.topAnchor.constraint(equalTo: guide.topAnchor, constant: margin))
    case "center":
        constraints.append(container.centerYAnchor.constraint(equalTo: hostView.centerYAnchor))
    case "flex_end", _:
        constraints.append(container.bottomAnchor.constraint(equalTo: guide.bottomAnchor, constant: -bottomMargin))
    }

    switch h {
    case "flex_start":
        constraints.append(container.leadingAnchor.constraint(equalTo: guide.leadingAnchor, constant: margin))
    case "center":
        constraints.append(container.centerXAnchor.constraint(equalTo: hostView.centerXAnchor))
    case "flex_end", _:
        constraints.append(container.trailingAnchor.constraint(equalTo: guide.trailingAnchor, constant: -margin))
    }

    NSLayoutConstraint.activate(constraints)

    hostView.layoutIfNeeded()

    let expand = UIButton(type: .system)
    expand.translatesAutoresizingMaskIntoConstraints = false
    expand.setImage(UIImage(systemName: "arrow.up.left.and.arrow.down.right"), for: .normal)
    expand.tintColor = .white
    expand.backgroundColor = UIColor.black.withAlphaComponent(0.5)
    expand.layer.cornerRadius = 15

    container.addSubview(expand)

    NSLayoutConstraint.activate([
        expand.topAnchor.constraint(equalTo: container.topAnchor, constant: 5),
        expand.trailingAnchor.constraint(equalTo: container.trailingAnchor, constant: -5),
        expand.widthAnchor.constraint(equalToConstant: 30),
        expand.heightAnchor.constraint(equalToConstant: 30)
    ])

    expand.addAction(UIAction { _ in
        container.removeFromSuperview()
        DispatchQueue.main.async {
                        self.slackPrint("🔄 PIP expand button tapped")
                        container.removeFromSuperview()
                        onClose()
                        if #available(iOS 15.2, *) {
                            self.showPopup(
                                messageId: messageId,
                                filterId: filterId,
                                html,
                                onClose: {}
                            )
                        } else {
                            // Fallback on earlier versions
                        }
                    }
    }, for: .touchUpInside)
}





    
    private func showFloater(
        _ html: String,
        h_a: String = "flex_end",
        v_a: String = "flex_end",
        onClose: @escaping () -> Void
    ) {
        self.slackPrint("📢 Showing Floater")

        DispatchQueue.main.async { [weak self] in
            guard let self = self else { return }
            // Dedicated window so floater hierarchy is isolated (avoids layout-off-main-thread crash when WKWebView loads)
            let window: UIWindow
            if #available(iOS 13.0, *) {
                let keyWindow = UIApplication.shared.connectedScenes
                    .compactMap { $0 as? UIWindowScene }
                    .flatMap { $0.windows }
                    .first { $0.isKeyWindow }
                if let scene = keyWindow?.windowScene {
                    window = UIWindow(windowScene: scene)
                } else {
                    window = UIWindow(frame: UIScreen.main.bounds)
                }
            } else {
                window = UIWindow(frame: UIScreen.main.bounds)
            }
            window.windowLevel = .alert + 1
            window.backgroundColor = .clear
            window.isUserInteractionEnabled = true
            if let keyWindow = UIApplication.shared.connectedScenes
                .compactMap({ $0 as? UIWindowScene })
                .flatMap({ $0.windows })
                .first(where: { $0.isKeyWindow }) {
                window.frame = keyWindow.bounds
            } else {
                window.frame = UIScreen.main.bounds
            }
            let rootVC = UIViewController()
            rootVC.view.backgroundColor = .clear
            window.rootViewController = rootVC
            self.floaterWindow = window
            let contentView = rootVC.view!

            let contentController = WKUserContentController()
            let autoplayJS = """
            document.addEventListener('DOMContentLoaded', function() {
                document.querySelectorAll('video').forEach(function(v) {
                    v.muted = true;
                    v.playsInline = true;
                    var p = v.play();
                    if (p !== undefined) { p.catch(function() {}); }
                });
            });
            """
            contentController.addUserScript(WKUserScript(source: autoplayJS, injectionTime: .atDocumentEnd, forMainFrameOnly: true))
            let config = WKWebViewConfiguration()
            config.userContentController = contentController
            config.allowsInlineMediaPlayback = true
            if #available(iOS 10.0, *) {
                config.mediaTypesRequiringUserActionForPlayback = []
            }
            let floaterView = WKWebView(frame: .zero, configuration: config)
            floaterView.translatesAutoresizingMaskIntoConstraints = false
            floaterView.layer.cornerRadius = 8
            floaterView.clipsToBounds = true
            floaterView.isOpaque = false
            floaterView.backgroundColor = .clear
            floaterView.scrollView.backgroundColor = .clear
            floaterView.scrollView.isScrollEnabled = false
            floaterView.scrollView.bounces = false

            let container = UIView()
            container.translatesAutoresizingMaskIntoConstraints = false
            container.backgroundColor = .clear
            container.addSubview(floaterView)
            contentView.addSubview(container)

            let screenBounds = UIScreen.main.bounds
            let floaterWidth = screenBounds.width / 3
            let floaterHeight = screenBounds.height / 3
            let v = self.normalizeAlignment(v_a)
            let h = self.normalizeAlignment(h_a)
            let margin: CGFloat = 20
            let guide = contentView.safeAreaLayoutGuide

            var constraints: [NSLayoutConstraint] = [
                container.widthAnchor.constraint(equalToConstant: floaterWidth),
                container.heightAnchor.constraint(equalToConstant: floaterHeight),
                floaterView.topAnchor.constraint(equalTo: container.topAnchor),
                floaterView.bottomAnchor.constraint(equalTo: container.bottomAnchor),
                floaterView.leadingAnchor.constraint(equalTo: container.leadingAnchor),
                floaterView.trailingAnchor.constraint(equalTo: container.trailingAnchor)
            ]
            switch v {
            case "flex_start":
                constraints.append(container.topAnchor.constraint(equalTo: guide.topAnchor, constant: margin))
            case "center":
                constraints.append(container.centerYAnchor.constraint(equalTo: contentView.centerYAnchor))
            case "flex_end", _:
                constraints.append(container.bottomAnchor.constraint(equalTo: guide.bottomAnchor, constant: -margin))
            }
            switch h {
            case "flex_start":
                constraints.append(container.leadingAnchor.constraint(equalTo: guide.leadingAnchor, constant: margin))
            case "center":
                constraints.append(container.centerXAnchor.constraint(equalTo: contentView.centerXAnchor))
            case "flex_end", _:
                constraints.append(container.trailingAnchor.constraint(equalTo: guide.trailingAnchor, constant: -margin))
            }
            NSLayoutConstraint.activate(constraints)
            contentView.layoutIfNeeded()
            window.makeKeyAndVisible()

            DispatchQueue.main.asyncAfter(deadline: .now() + 0.05) {
                guard Thread.isMainThread else { return }
                floaterView.loadHTMLString(html, baseURL: nil)
            }

            let panGesture = UIPanGestureRecognizer(target: self, action: #selector(self.handleFloaterPan(_:)))
            container.addGestureRecognizer(panGesture)
        }
    }

    @objc private func handleFloaterPan(_ gesture: UIPanGestureRecognizer) {
        guard let view = gesture.view else { return }
        let translation = gesture.translation(in: view.superview)

        switch gesture.state {
        case .changed:
            view.center = CGPoint(
                x: view.center.x + translation.x,
                y: view.center.y + translation.y
            )
            gesture.setTranslation(.zero, in: view.superview)
        default:
            break
        }
    }
}

typealias UIControlTargetClosure = (UIControl) -> ()

class ClosureSleeve {
    let closure: UIControlTargetClosure
    init(_ closure: @escaping UIControlTargetClosure) {
        self.closure = closure
    }
    @objc func invoke(_ sender: UIControl) {
        closure(sender)
    }
}

extension UIControl {
    func addTargetClosure(_ closure: @escaping UIControlTargetClosure) {
        let sleeve = ClosureSleeve(closure)
        addTarget(sleeve, action: #selector(ClosureSleeve.invoke(_:)), for: .touchUpInside)
        objc_setAssociatedObject(self, String(format: "[%d]", arc4random()), sleeve, .OBJC_ASSOCIATION_RETAIN)
    }
}


private extension UIViewController {
    @objc func dismissVC() {
        dismiss(animated: true)
    }
}

@available(iOS 15.2, *)
public struct PageTrackingModifier: SwiftUI.ViewModifier {
   
    let name: String

    @available(iOS 15.2, *)
    public func body(content: Content) -> some View {
        content
            .onAppear {
                PushApp.shared.sendEvent(eventName: "page_open", eventData: ["page": name,"compare" : name])
            }
            .onDisappear {
                PushApp.shared.sendEvent(eventName: "page_closed", eventData: ["page": name, "compare" : name])
            }
    }
}

@available(iOS 15.2, *)
public extension View {
    func trackPage(name: String) -> some View {
        self.modifier(PageTrackingModifier(name: name))
    }
}


// MARK: - Tooltip Modifier (placeholder only)
public struct TooltipModifier: ViewModifier {
    let placeholderId: String?

    public func body(content: Content) -> some View {
        content
            .background(
                TooltipAnchor(
                    content: content,
                    placeholderId: placeholderId
                )
            )
    }
}

// MARK: - Tooltip Anchor (placeholder)
public struct TooltipAnchor<Content: View>: UIViewRepresentable {
    let content: Content
    let placeholderId: String?

    public func makeUIView(context: Context) -> UIView {
        let container = UIView()
        container.backgroundColor = .clear

        let hosting = UIHostingController(rootView: content)
        hosting.view.backgroundColor = .clear
        hosting.view.translatesAutoresizingMaskIntoConstraints = false
        container.addSubview(hosting.view)

        NSLayoutConstraint.activate([
            hosting.view.leadingAnchor.constraint(equalTo: container.leadingAnchor),
            hosting.view.trailingAnchor.constraint(equalTo: container.trailingAnchor),
            hosting.view.topAnchor.constraint(equalTo: container.topAnchor),
            hosting.view.bottomAnchor.constraint(equalTo: container.bottomAnchor)
        ])

        // Register the view in the global registry using placeholderId
        if let id = placeholderId {
            TooltipRegistry.shared.register(view: hosting.view, for: id)
//            self.slackPrint("✅ Registered placeholder view for id: \(id)")
        }

        return container
    }

    public func updateUIView(_ uiView: UIView, context: Context) {}
}

// MARK: - View Extension
public extension View {
    func tooltip(placeholderId: String?) -> some View {
        self.modifier(TooltipModifier(placeholderId: placeholderId))
    }
}

// MARK: - Global Tooltip Registry
public class TooltipRegistry {
    public static let shared = TooltipRegistry()
    private init() {}

    private var viewMap = [String: UIView]()

    public func register(view: UIView, for key: String) {
        viewMap[key] = view
    }

    public func view(for key: String) -> UIView? {
        return viewMap[key]
    }

    public func unregister(key: String) {
        viewMap.removeValue(forKey: key)
    }
}


// You will also need a simple UIColor initializer for Hex strings (if missing):
extension UIColor {
    convenience init?(hex: String) {
        let r, g, b, a: CGFloat

        if hex.hasPrefix("#") {
            let start = hex.index(hex.startIndex, offsetBy: 1)
            let hexColor = String(hex[start...])

            if hexColor.count == 6 {
                let scanner = Scanner(string: hexColor)
                var hexNumber: UInt64 = 0

                if scanner.scanHexInt64(&hexNumber) {
                    r = CGFloat((hexNumber & 0xff0000) >> 16) / 255
                    g = CGFloat((hexNumber & 0x00ff00) >> 8) / 255
                    b = CGFloat(hexNumber & 0x0000ff) / 255
                    a = 1.0

                    self.init(red: r, green: g, blue: b, alpha: a)
                    return
                }
            }
        }
        return nil
    }
}

extension String {
    func decodingHTMLEntities() -> String {
        guard let data = self.data(using: .utf8) else { return self }
        let options: [NSAttributedString.DocumentReadingOptionKey: Any] = [
            .documentType: NSAttributedString.DocumentType.html,
            .characterEncoding: String.Encoding.utf8.rawValue
        ]
        guard let attributedString = try? NSAttributedString(data: data, options: options, documentAttributes: nil) else {
            return self
        }
        return attributedString.string
    }

    /// Parses HTML (e.g. richline_1) to NSAttributedString for tooltip display (variables + inline styles).
    func parseHTMLToAttributedString(fontSize: CGFloat = 14, color: UIColor = .black) -> NSAttributedString? {
        let wrapped = """
        <!DOCTYPE html><html><head><meta charset=\"UTF-8\"><style>body{font-family:-apple-system,BlinkMacSystemFont,sans-serif;font-size:\(Int(fontSize))px;color:\(color.hexString);margin:0;}*{box-sizing:border-box;}</style></head><body>\(self)</body></html>
        """
        guard let data = wrapped.data(using: .utf8) else { return nil }
        let options: [NSAttributedString.DocumentReadingOptionKey: Any] = [
            .documentType: NSAttributedString.DocumentType.html,
            .characterEncoding: String.Encoding.utf8.rawValue
        ]
        return try? NSAttributedString(data: data, options: options, documentAttributes: nil)
    }
}

extension UIColor {
    var hexString: String {
        var r: CGFloat = 0, g: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
        getRed(&r, green: &g, blue: &b, alpha: &a)
        return String(format: "#%02X%02X%02X", Int(r*255), Int(g*255), Int(b*255))
    }
}

/// Build font and attributes for tooltip line from style arrays (e.g. line2_text_styles: bold, italic).
private func tooltipFont(size: CGFloat, textStyles: [String]?) -> UIFont {
    var traits: UIFontDescriptor.SymbolicTraits = []
    let styles = (textStyles ?? []).map { $0.lowercased() }
    if styles.contains("bold") { traits.insert(.traitBold) }
    if styles.contains("italic") { traits.insert(.traitItalic) }
    let desc = UIFontDescriptor.preferredFontDescriptor(withTextStyle: .body).withSymbolicTraits(traits) ?? UIFontDescriptor.preferredFontDescriptor(withTextStyle: .body)
    return UIFont(descriptor: desc, size: size)
}

private func tooltipAttributes(fontSize: CGFloat, colorHex: String?, textStyles: [String]?) -> [NSAttributedString.Key: Any] {
    var attrs: [NSAttributedString.Key: Any] = [
        .font: tooltipFont(size: fontSize, textStyles: textStyles),
        .foregroundColor: UIColor(hex: colorHex ?? "#000001") ?? .black
    ]
    let styles = (textStyles ?? []).map { $0.lowercased() }
    if styles.contains("underline") {
        attrs[.underlineStyle] = NSUnderlineStyle.single.rawValue
    }
    return attrs
}

@available(iOS 15.2, *)
extension PushApp {
    private static let deviceIdKey = "pushapp_device_id"

    /// Returns a persistent device id (vendor id + timestamp) for the whole app lifecycle
    public func getPersistentDeviceId() -> String {
        if let saved = UserDefaults.standard.string(forKey: PushApp.deviceIdKey) {
            return saved
        }

        // Base id = vendor id or random UUID
        let baseId = UIDevice.current.identifierForVendor?.uuidString ?? UUID().uuidString
        let timestamp = Int(Date().timeIntervalSince1970)
        let newId = "\(baseId)_\(timestamp)"

        // Save permanently
        UserDefaults.standard.setValue(newId, forKey: PushApp.deviceIdKey)
        self.slackPrint("🆕 Generated new persistent device id: \(newId)")

        return newId
    }
}







