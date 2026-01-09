import UIKit
import Capacitor
import PushappIonic
import UserNotifications

@UIApplicationMain
class AppDelegate: UIResponder, UIApplicationDelegate, UNUserNotificationCenterDelegate {

    var window: UIWindow?

    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?) -> Bool {
        // Set UNUserNotificationCenter delegate to handle foreground notifications
        UNUserNotificationCenter.current().delegate = self
      var additionalInfo: [String: Any] = [
          "expiry_date": randomExpiryTimestampMoreThan5Years(),
          "dob": randomDOB(),
          "gender": randomGender()
      ]

      // add more fields if needed
      additionalInfo["source"] = "ios_app"

      var cohorts: [String: Any] = [
          "user_type": "test_user",
          "app_version": Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? ""
      ]

      if #available(iOS 15.2, *) {
        DispatchQueue.main.asyncAfter(deadline: .now()+5, execute: {
          PushApp.shared.updateCustomerProfile(cohorts: cohorts, additionalInfo: additionalInfo)
        })
        
      } else {
        // Fallback on earlier versions
      }

      
        return true
    }
  
  
  private func randomExpiryTimestampMoreThan5Years() -> Int {
      let fiveYearsInSeconds: TimeInterval = 5 * 365 * 24 * 60 * 60
      let extraRandomSeconds = TimeInterval.random(in: 0...(5 * 365 * 24 * 60 * 60))
      let futureDate = Date().addingTimeInterval(fiveYearsInSeconds + extraRandomSeconds)
      return Int(futureDate.timeIntervalSince1970)
  }

  private func randomDOB(minAge: Int = 18, maxAge: Int = 60) -> String {
      let calendar = Calendar.current
      let now = Date()

      let minDate = calendar.date(byAdding: .year, value: -maxAge, to: now)!
      let maxDate = calendar.date(byAdding: .year, value: -minAge, to: now)!

      let randomTimeInterval = TimeInterval.random(
          in: minDate.timeIntervalSince1970...maxDate.timeIntervalSince1970
      )

      let randomDate = Date(timeIntervalSince1970: randomTimeInterval)

      let formatter = DateFormatter()
      formatter.dateFormat = "yyyy-MM-dd" // common DOB format
      return formatter.string(from: randomDate)
  }

  private func randomGender() -> String {
      return Bool.random() ? "male" : "female"
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
        let userInfo = response.notification.request.content.userInfo
        
        // Handle notification tap
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
            
            // Handle notification tap through PushApp if needed
            // PushApp.shared.handleNotificationTap(notificationData)
        }
        
        completionHandler()
    }

}
