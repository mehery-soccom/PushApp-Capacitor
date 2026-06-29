import XCTest
@testable import PushAppPlugin

@available(iOS 15.2, *)
class PushAppErrorCodesTests: XCTestCase {
    private let nativeCodes = [
        PushAppErrorCodes.notInitialized,
        PushAppErrorCodes.invalidAppId,
        PushAppErrorCodes.appIdRequired,
        PushAppErrorCodes.contextUnavailable,
        PushAppErrorCodes.registerRequired,
        PushAppErrorCodes.emptyToken,
        PushAppErrorCodes.registerFailed,
        PushAppErrorCodes.emptyUserId,
        PushAppErrorCodes.loginFailed,
        PushAppErrorCodes.logoutFailed,
        PushAppErrorCodes.customerProfileFailed,
        PushAppErrorCodes.missingCode,
        PushAppErrorCodes.missingProfileData,
        PushAppErrorCodes.invalidProfileData,
        PushAppErrorCodes.missingEventName,
        PushAppErrorCodes.missingEventData,
        PushAppErrorCodes.missingPageName,
        PushAppErrorCodes.missingPlaceholderId,
        PushAppErrorCodes.missingUiBounds,
        PushAppErrorCodes.missingTargetId,
        PushAppErrorCodes.activityUnavailable,
        PushAppErrorCodes.placeholderFailed,
        PushAppErrorCodes.tooltipFailed,
        PushAppErrorCodes.invalidEventData,
        PushAppErrorCodes.missingNotificationToken,
        PushAppErrorCodes.missingNotificationEvent,
    ]

    func testNotInitializedMessage() {
        XCTAssertEqual(
            PushAppErrorCodes.message(for: PushAppErrorCodes.notInitialized),
            "Call PushApp.initialize() before this method."
        )
    }

    func testRegisterRequiredMessage() {
        XCTAssertEqual(
            PushAppErrorCodes.message(for: PushAppErrorCodes.registerRequired),
            "Call PushApp.register() before login()."
        )
    }

    func testLogoutFailedMessage() {
        XCTAssertEqual(
            PushAppErrorCodes.message(for: PushAppErrorCodes.logoutFailed),
            "Logout failed. Local session was cleared but the server delink call failed."
        )
    }

    func testAllNativeCodesHaveSpecificMessages() {
        for code in nativeCodes {
            XCTAssertNotEqual(
                PushAppErrorCodes.message(for: code),
                "PushApp operation failed.",
                "Expected specific message for \(code)"
            )
        }
    }
}
