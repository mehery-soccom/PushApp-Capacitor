import Foundation
import os.log

/// SDK logger with release-safe defaults.
/// - Release builds: errors only (redacted).
/// - Debug builds: verbose logs when `configure(debug: true)` is set via initialize.
enum PushAppLogger {
    enum Level {
        case none
        case error
        case debug
    }

    private static let sensitiveJsonKeyPattern =
        #""(token|fcm_token|apns_token|user_id|device_id|guest_id|channel_id|code|password|email|phone|authorization)"\s*:\s*"[^"]*""#
    private static let sensitiveHeaderPattern =
        #"(?i)(authorization|x-device-id)\s*[=:]\s*\S+"#
    private static let bearerPattern = #"Bearer\s+\S+"#

    private static var level: Level = .error
    private static let log = OSLog(subsystem: "com.mehery.pushapp", category: "PushApp")

    static func configure(debug: Bool) {
        #if DEBUG
        level = debug ? .debug : .error
        if level == .debug {
            os_log("PushAppLogger: verbose logging enabled (debugMode=true)", log: log, type: .debug)
        }
        #else
        level = .error
        #endif
    }

    static func logApiCall(
        method: String,
        url: String,
        requestBody: String?,
        statusCode: Int?,
        responseBody: String?,
        error: String? = nil
    ) {
        guard level == .debug else { return }
        debug("API \(method) \(url)")
        if let requestBody, !requestBody.isEmpty {
            debug("Request Body: \(requestBody)")
        }
        if let error {
            PushAppLogger.error("API error: \(error)")
            return
        }
        if let statusCode {
            debug("Response (\(statusCode)): \(responseBody ?? "")")
        }
    }

    static func error(_ message: String, tag: String = "PushApp") {
        guard level != .none else { return }
        let sanitized = sanitize(message)
        os_log("%{public}@ [%{public}@] %{public}@", log: log, type: .error, tag, sanitized)
    }

    static func warn(_ message: String, tag: String = "PushApp") {
        guard level != .none else { return }
        let sanitized = sanitize(message)
        if level == .debug {
            os_log("%{public}@ [%{public}@] %{public}@", log: log, type: .default, tag, sanitized)
        } else {
            error(sanitized, tag: tag)
        }
    }

    static func debug(_ message: String, tag: String = "PushApp") {
        guard level == .debug else { return }
        let sanitized = sanitize(message)
        os_log("%{public}@ [%{public}@] %{public}@", log: log, type: .debug, tag, sanitized)
    }

    /// Logs the raw push token when verbose debug logging is enabled (never redacted).
    static func logPushToken(_ label: String, token: String, tag: String = "PushApp") {
        guard level == .debug, !token.isEmpty else { return }
        os_log("%{public}@ [%{public}@] %{public}@: %{public}@", log: log, type: .debug, tag, label, token)
    }

    static func sanitize(_ message: String) -> String {
        var result = message
        if let regex = try? NSRegularExpression(pattern: sensitiveJsonKeyPattern, options: [.caseInsensitive]) {
            let range = NSRange(result.startIndex..<result.endIndex, in: result)
            result = regex.stringByReplacingMatches(
                in: result,
                options: [],
                range: range,
                withTemplate: "\"$1\":\"***REDACTED***\""
            )
        }
        if let regex = try? NSRegularExpression(pattern: sensitiveHeaderPattern, options: [.caseInsensitive]) {
            let nsRange = NSRange(result.startIndex..<result.endIndex, in: result)
            let matches = regex.matches(in: result, options: [], range: nsRange)
            for match in matches.reversed() {
                guard let range = Range(match.range, in: result) else { continue }
                let fragment = String(result[range])
                let parts = fragment.split(separator: ":", maxSplits: 1, omittingEmptySubsequences: false)
                if parts.count == 2 {
                    result.replaceSubrange(range, with: "\(parts[0]):***REDACTED***")
                }
            }
        }
        if let regex = try? NSRegularExpression(pattern: bearerPattern, options: [.caseInsensitive]) {
            let range = NSRange(result.startIndex..<result.endIndex, in: result)
            result = regex.stringByReplacingMatches(
                in: result,
                options: [],
                range: range,
                withTemplate: "Bearer ***REDACTED***"
            )
        }
        return result
    }
}
