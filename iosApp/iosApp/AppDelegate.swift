import UIKit
import FirebaseCore
import FirebaseMessaging
import ComposeApp

class AppDelegate: NSObject, UIApplicationDelegate, MessagingDelegate, UNUserNotificationCenterDelegate {

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        OrientationBridge.shared.setHandler(handler: SwiftOrientationHandler())
        FirebaseApp.configure()
        Messaging.messaging().delegate = self
        return true
    }

    func messaging(_ messaging: Messaging, didReceiveRegistrationToken fcmToken: String?) {
        if fcmToken != nil {
            PushTokenBridge.shared.onToken(hexToken: fcmToken ?? "null")
        } else {
            PushTokenBridge.shared.onFailed()
        }
    }

    func application(_ application: UIApplication,
                     didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        Messaging.messaging().apnsToken = deviceToken
        let hex = deviceToken.map { String(format: "%02.2hhx", $0) }.joined()
    }

    func application(
        _ application: UIApplication,
        didFailToRegisterForRemoteNotificationsWithError error: Error
    ) {
        PushTokenBridge.shared.onFailed()
    }

    // Fallback for iOS < 16
    func application(
        _ application: UIApplication,
        supportedInterfaceOrientationsFor window: UIWindow?
    ) -> UIInterfaceOrientationMask {
        return OrientationBridge.shared.allOrientationsEnabled ? .all : .portrait
    }
}

private class SwiftOrientationHandler: NSObject, OrientationHandler {
    func apply(allOrientations: Bool) {
        DispatchQueue.main.async {
            let mask: UIInterfaceOrientationMask = allOrientations ? .all : .portrait
            if #available(iOS 16.0, *) {
                guard let scene = UIApplication.shared.connectedScenes.first as? UIWindowScene else { return }
                scene.requestGeometryUpdate(.iOS(interfaceOrientations: mask)) { error in
                    // print("[GRAY] requestGeometryUpdate error: \(error)")
                }
                // Also tell the root view controller to re-check
                scene.keyWindow?.rootViewController?.setNeedsUpdateOfSupportedInterfaceOrientations()
            } else {
                UIViewController.attemptRotationToDeviceOrientation()
            }
        }
    }
}