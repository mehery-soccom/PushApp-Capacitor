import Foundation

enum PushAppErrorCodes {
    static let notInitialized = "NOT_INITIALIZED"
    static let invalidAppId = "INVALID_APP_ID"
    static let appIdRequired = "APP_ID_REQUIRED"
    static let contextUnavailable = "CONTEXT_UNAVAILABLE"
    static let registerRequired = "REGISTER_REQUIRED"
    static let emptyToken = "EMPTY_TOKEN"
    static let registerFailed = "REGISTER_FAILED"
    static let emptyUserId = "EMPTY_USER_ID"
    static let loginFailed = "LOGIN_FAILED"
    static let logoutFailed = "LOGOUT_FAILED"
    static let customerProfileFailed = "CUSTOMER_PROFILE_FAILED"
    static let missingCode = "MISSING_CODE"
    static let missingProfileData = "MISSING_PROFILE_DATA"
    static let invalidProfileData = "INVALID_PROFILE_DATA"
    static let missingEventName = "MISSING_EVENT_NAME"
    static let missingEventData = "MISSING_EVENT_DATA"
    static let missingPageName = "MISSING_PAGE_NAME"
    static let missingPlaceholderId = "MISSING_PLACEHOLDER_ID"
    static let missingUiBounds = "MISSING_UI_BOUNDS"
    static let missingTargetId = "MISSING_TARGET_ID"
    static let activityUnavailable = "ACTIVITY_UNAVAILABLE"
    static let placeholderFailed = "PLACEHOLDER_FAILED"
    static let tooltipFailed = "TOOLTIP_FAILED"
    static let invalidEventData = "INVALID_EVENT_DATA"
    static let missingNotificationToken = "MISSING_NOTIFICATION_TOKEN"
    static let missingNotificationEvent = "MISSING_NOTIFICATION_EVENT"

    static func message(for code: String) -> String {
        switch code {
        case notInitialized:
            return "Call PushApp.initialize() before this method."
        case invalidAppId:
            return "Initialize failed. appId must include a tenant prefix before the first '_' (e.g. demo_1763369170735)."
        case appIdRequired:
            return "appId is required (channel id / App ID)."
        case contextUnavailable:
            return "Android context is not available."
        case registerRequired:
            return "Call PushApp.register() before login()."
        case emptyToken:
            return "apnsToken is required on iOS."
        case registerFailed:
            return "Device registration failed. Check the push token and network connection."
        case emptyUserId:
            return "userId is required."
        case loginFailed:
            return "Login failed. The server did not confirm the device link."
        case logoutFailed:
            return "Logout failed. Local session was cleared but the server delink call failed."
        case customerProfileFailed:
            return "Customer profile update failed."
        case missingCode:
            return "code is required (e.g. userId_deviceId)."
        case missingProfileData:
            return "additionalInfo and cohorts are required and must be objects."
        case invalidProfileData:
            return "additionalInfo and cohorts must be objects."
        case missingEventName:
            return "eventName is required."
        case missingEventData:
            return "eventData is required and must be an object."
        case missingPageName:
            return "pageName is required."
        case missingPlaceholderId:
            return "placeholderId is required."
        case missingUiBounds:
            return "placeholderId, x, y, width, and height are required."
        case missingTargetId:
            return "targetId is required."
        case activityUnavailable:
            return "Activity is not available."
        case placeholderFailed:
            return "Placeholder operation failed."
        case tooltipFailed:
            return "Tooltip target operation failed."
        case invalidEventData:
            return "Failed to parse eventData."
        case missingNotificationToken:
            return "token is required."
        case missingNotificationEvent:
            return "event is required."
        default:
            return "PushApp operation failed."
        }
    }
}
