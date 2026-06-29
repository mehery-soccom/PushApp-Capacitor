package com.mehery.pushapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PushAppErrorCodesTest {

    @Test
    fun notInitializedMessage() {
        assertEquals(
            "Call PushApp.initialize() before this method.",
            PushAppErrorCodes.message(PushAppErrorCodes.NOT_INITIALIZED),
        )
    }

    @Test
    fun registerRequiredMessage() {
        assertEquals(
            "Call PushApp.register() before login().",
            PushAppErrorCodes.message(PushAppErrorCodes.REGISTER_REQUIRED),
        )
    }

    @Test
    fun logoutFailedMessage() {
        assertEquals(
            "Logout failed. Local session was cleared but the server delink call failed.",
            PushAppErrorCodes.message(PushAppErrorCodes.LOGOUT_FAILED),
        )
    }

    @Test
    fun allNativeCodesHaveSpecificMessages() {
        val codes = listOf(
            PushAppErrorCodes.NOT_INITIALIZED,
            PushAppErrorCodes.INVALID_APP_ID,
            PushAppErrorCodes.APP_ID_REQUIRED,
            PushAppErrorCodes.CONTEXT_UNAVAILABLE,
            PushAppErrorCodes.REGISTER_REQUIRED,
            PushAppErrorCodes.EMPTY_TOKEN,
            PushAppErrorCodes.REGISTER_FAILED,
            PushAppErrorCodes.EMPTY_USER_ID,
            PushAppErrorCodes.LOGIN_FAILED,
            PushAppErrorCodes.LOGOUT_FAILED,
            PushAppErrorCodes.CUSTOMER_PROFILE_FAILED,
            PushAppErrorCodes.MISSING_CODE,
            PushAppErrorCodes.MISSING_PROFILE_DATA,
            PushAppErrorCodes.INVALID_PROFILE_DATA,
            PushAppErrorCodes.MISSING_EVENT_NAME,
            PushAppErrorCodes.MISSING_EVENT_DATA,
            PushAppErrorCodes.INVALID_EVENT_DATA,
            PushAppErrorCodes.MISSING_PAGE_NAME,
            PushAppErrorCodes.MISSING_PLACEHOLDER_ID,
            PushAppErrorCodes.MISSING_UI_BOUNDS,
            PushAppErrorCodes.MISSING_TARGET_ID,
            PushAppErrorCodes.ACTIVITY_UNAVAILABLE,
            PushAppErrorCodes.PLACEHOLDER_FAILED,
            PushAppErrorCodes.TOOLTIP_FAILED,
            PushAppErrorCodes.MISSING_NOTIFICATION_TOKEN,
            PushAppErrorCodes.MISSING_NOTIFICATION_EVENT,
        )
        for (code in codes) {
            assertNotEquals("PushApp operation failed.", PushAppErrorCodes.message(code))
        }
    }
}
