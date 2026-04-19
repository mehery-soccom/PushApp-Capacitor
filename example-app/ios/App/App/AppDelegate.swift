import UIKit
import Capacitor
import PushappIonic
import UserNotifications

@UIApplicationMain
class AppDelegate: UIResponder, UIApplicationDelegate, UNUserNotificationCenterDelegate {

    var window: UIWindow?

    override init() {
        super.init()
        // Set delegate as early as possible so we receive notification taps even when app launches from a notification
        UNUserNotificationCenter.current().delegate = self
        print("🔔 AppDelegate set as UNUserNotificationCenter delegate (init)")
    }

    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        print("🔔 AppDelegate set as UNUserNotificationCenter delegate (didFinishLaunching)")

        // Re-assert delegate after bridge loads so we are not overwritten by any plugin
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) { [weak self] in
            guard let self = self else { return }
            UNUserNotificationCenter.current().delegate = self
            print("🔔 AppDelegate re-set as UNUserNotificationCenter delegate (post-bridge)")
        }
        return true
    }
    func applicationWillResignActive(_ application: UIApplication) {
        // Sent when the application is about to move from active to inactive state. This can occur for certain types of temporary interruptions (such as an incoming phone call or SMS message) or when the user quits the application and it begins the transition to the background state.
        // Use this method to pause ongoing tasks, disable timers, and invalidate graphics rendering callbacks. Games should use this method to pause the game.
    }

    func applicationDidEnterBackground(_ application: UIApplication) {
        // Use this method to release shared resources, save user data, invalidate timers, and store enough application state information to restore your application to its current state in case it is terminated later.
        // If your application supports background execution, this method is called instead of applicationWillTerminate: when the user quits.
    }

    func applicationWillEnterForeground(_ application: UIApplication) {
        // Called as part of the transition from the background to the active state; here you can undo many of the changes made on entering the background.
    }

    func applicationDidBecomeActive(_ application: UIApplication) {
        // Re-assert so we stay the notification delegate after returning from background
        UNUserNotificationCenter.current().delegate = self
        // Restart any tasks that were paused (or not yet started) while the application was inactive. If the application was previously in the background, optionally refresh the user interface.
    }

    func applicationWillTerminate(_ application: UIApplication) {
        // Called when the application is about to terminate. Save data if appropriate. See also applicationDidEnterBackground:.
    }

    func application(_ app: UIApplication, open url: URL, options: [UIApplication.OpenURLOptionsKey: Any] = [:]) -> Bool {
        // Called when the app was launched with a url. Feel free to add additional processing here,
        // but if you want the App API to support tracking app url opens, make sure to keep this call
        return ApplicationDelegateProxy.shared.application(app, open: url, options: options)
    }

    func application(_ application: UIApplication, continue userActivity: NSUserActivity, restorationHandler: @escaping ([UIUserActivityRestoring]?) -> Void) -> Bool {
        // Called when the app was launched with an activity, including Universal Links.
        // Feel free to add additional processing here, but if you want the App API to support
        // tracking app url opens, make sure to keep this call
        return ApplicationDelegateProxy.shared.application(application, continue: userActivity, restorationHandler: restorationHandler)
    }
  
    func application(_ application: UIApplication,
                     didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
      if #available(iOS 15.2, *) {
        PushApp.shared.handleDeviceToken(deviceToken)
      } else {
        // Fallback on earlier versions
      }
    }

    func application(_ application: UIApplication,
                     didFailToRegisterForRemoteNotificationsWithError error: Error) {
        print("Failed to register for remote notifications: \(error)")
    }
  
  func application(_ application: UIApplication,
                   didReceiveRemoteNotification userInfo: [AnyHashable: Any],
                   fetchCompletionHandler completionHandler: @escaping (UIBackgroundFetchResult) -> Void) {
      print("Received silent push:", userInfo)

      if #available(iOS 15.2, *) {
        PushApp.shared.ping()
      } else {
        // Fallback on earlier versions
      }

      completionHandler(.newData)
  }
    
    // MARK: - UNUserNotificationCenterDelegate
    
    // Called when a notification arrives while the app is in the foreground
    func userNotificationCenter(_ center: UNUserNotificationCenter,
                                willPresent notification: UNNotification,
                                withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void) {
        print("🔔 willPresent: notification received while app in foreground")
        let userInfo = notification.request.content.userInfo
        
        // Show notification banner even when app is in foreground
        if #available(iOS 14.0, *) {
            // iOS 14+ supports banner, sound, and badge
            completionHandler([.banner, .sound, .badge])
        } else {
            // iOS 13 and below use alert instead of banner
            completionHandler([.alert, .sound, .badge])
        }
        
        // Handle the notification data if needed
        if #available(iOS 15.2, *) {
            // Convert notification to dictionary format for PushApp
            var notificationData: [String: Any] = [:]
            
            // Extract standard notification fields
            if let title = userInfo["title"] as? String {
                notificationData["title"] = title
            }
            if let body = userInfo["body"] as? String {
                notificationData["body"] = body
            }
            
            // Copy all userInfo data
            for (key, value) in userInfo {
              notificationData[key as! String] = value
            }
            
            // Handle notification through PushApp if needed
            // PushApp.shared.handleNotification(notificationData)
        }
    }
    
    // Called when user taps on a notification
    func userNotificationCenter(_ center: UNUserNotificationCenter,
                                didReceive response: UNNotificationResponse,
                                withCompletionHandler completionHandler: @escaping () -> Void) {
        print("🔔 didReceive: user tapped notification or CTA")
        let id = response.notification.request.identifier
        print("📩 Received notification with ID = \(id)")

        let userInfo = response.notification.request.content.userInfo
        let actionId = response.actionIdentifier
        print("🧾 userInfo:", userInfo)

        guard let token = userInfo["click_token"] as? String else {
            print("❌ Missing click_token in payload")
            completionHandler()
            return
        }

        var event = "opened"
        var ctaId: String? = nil

        // Handle CTA button
        if let buttons = userInfo["buttons"] as? [[String: Any]] {
            if let matchingButton = buttons.first(where: { $0["id"] as? String == actionId }) {
                event = "cta"
                ctaId = matchingButton["id"] as? String

                if let urlString = matchingButton["url"] as? String,
                   let url = URL(string: urlString),
                   UIApplication.shared.canOpenURL(url) {
                    UIApplication.shared.open(url, options: [:], completionHandler: nil)
                }
            }
        }

        // Track the event (opened or CTA)
        if #available(iOS 15.2, *) {
            PushApp.shared.trackPushNotificationEvent(token: token, event: event, ctaId: ctaId) { success in
                if success {
                    print("✅ Push track (\(event)) sent successfully.")
                } else {
                    print("❌ Failed to send push track (\(event)).")
                }
            }
        }

        completionHandler()
    }

}
