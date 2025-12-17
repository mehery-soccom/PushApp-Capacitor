import UIKit
import Capacitor
import PushappIonic
import UserNotifications

@UIApplicationMain
class AppDelegate: UIResponder, UIApplicationDelegate, UNUserNotificationCenterDelegate {

    var window: UIWindow?

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?
    ) -> Bool {

        // Set UNUserNotificationCenter delegate to handle foreground notifications
        UNUserNotificationCenter.current().delegate = self

        return true
    }

    func applicationWillResignActive(_ application: UIApplication) {
        // Sent when the application is about to move from active to inactive state.
    }

    func applicationDidEnterBackground(_ application: UIApplication) {
        // Use this method to release shared resources.
    }

    func applicationWillEnterForeground(_ application: UIApplication) {
        // Called as part of the transition from the background to the active state.
    }

    func applicationDidBecomeActive(_ application: UIApplication) {
        // Restart any tasks that were paused while the application was inactive.
    }

    func applicationWillTerminate(_ application: UIApplication) {
        // Called when the application is about to terminate.
    }

    func application(
        _ app: UIApplication,
        open url: URL,
        options: [UIApplication.OpenURLOptionsKey: Any] = [:]
    ) -> Bool {

        return ApplicationDelegateProxy.shared.application(
            app,
            open: url,
            options: options
        )
    }

    func application(
        _ application: UIApplication,
        continue userActivity: NSUserActivity,
        restorationHandler: @escaping ([UIUserActivityRestoring]?) -> Void
    ) -> Bool {

        return ApplicationDelegateProxy.shared.application(
            application,
            continue: userActivity,
            restorationHandler: restorationHandler
        )
    }

    func application(
        _ application: UIApplication,
        didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data
    ) {
        if #available(iOS 15.2, *) {
            PushApp.shared.handleDeviceToken(deviceToken)
        } else {
            // Fallback on earlier versions
        }
    }

    func application(
        _ application: UIApplication,
        didFailToRegisterForRemoteNotificationsWithError error: Error
    ) {
        print("Failed to register for remote notifications: \(error)")
    }

    // MARK: - UNUserNotificationCenterDelegate

    // Called when a notification arrives while the app is in the foreground
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {

        let userInfo = notification.request.content.userInfo

        // Show notification banner even when app is in foreground
        if #available(iOS 14.0, *) {
            completionHandler([.banner, .sound, .badge])
        } else {
            completionHandler([.alert, .sound, .badge])
        }

        // Existing PushApp-related logic (unchanged)
        if #available(iOS 15.2, *) {

            var notificationData: [String: Any] = [:]

            if let title = userInfo["title"] as? String {
                notificationData["title"] = title
            }
            if let body = userInfo["body"] as? String {
                notificationData["body"] = body
            }

            for (key, value) in userInfo {
                notificationData[String(describing: key)] = value
            }

            // PushApp.shared.handleNotification(notificationData)
        }
    }

    // Called when user taps on a notification
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {

        let userInfo = response.notification.request.content.userInfo

        // Existing PushApp-related logic (unchanged)
        if #available(iOS 15.2, *) {

            var notificationData: [String: Any] = [:]

            if let title = userInfo["title"] as? String {
                notificationData["title"] = title
            }
            if let body = userInfo["body"] as? String {
                notificationData["body"] = body
            }

            for (key, value) in userInfo {
                notificationData[String(describing: key)] = value
            }

            // PushApp.shared.handleNotificationTap(notificationData)
        }

        completionHandler()
    }
}
