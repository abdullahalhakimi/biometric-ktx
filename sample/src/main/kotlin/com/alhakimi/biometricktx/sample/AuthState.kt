package com.alhakimi.biometricktx.sample

/**
 * How the user authenticated for the current session. Demo-only; not real auth.
 */
enum class LoginMethod {
    Biometric,
    Password,
}

/**
 * The sample's auth state. Swapping between [NotLoggedIn] and [LoggedIn] drives the UI between the
 * login flow and the home screen.
 */
sealed interface AuthState {
    /** Not authenticated: show the login flow. */
    data object NotLoggedIn : AuthState

    /** Authenticated: show the home screen, remembering how we got in. */
    data class LoggedIn(val method: LoginMethod) : AuthState
}
