import Foundation

@objc public class PushApp: NSObject {
    @objc public func echo(_ value: String) -> String {
        print(value)
        return value
    }
}
