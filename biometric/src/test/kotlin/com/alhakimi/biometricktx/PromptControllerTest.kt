package com.alhakimi.biometricktx

import com.alhakimi.biometricktx.internal.PromptController
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PromptControllerTest {

    private fun request(title: String = "Title") = BiometricRequest { this.title = title }

    @Test
    fun `authenticate returns Success when prompt succeeds`() = runTest {
        val launcher = FakePromptLauncher().apply { autoOutcome = FakePromptLauncher.Outcome.Success }
        val controller = PromptController(launcher)

        val result = controller.authenticate(request())

        assertEquals(BiometricResult.Success, result)
    }

    @Test
    fun `authenticate maps error code to typed error`() = runTest {
        val launcher = FakePromptLauncher().apply {
            autoOutcome = FakePromptLauncher.Outcome.Error(
                code = 7, // ERROR_LOCKOUT
                message = "Too many attempts",
            )
        }
        val controller = PromptController(launcher)

        val result = controller.authenticate(request())

        assertTrue(result is BiometricResult.Error)
        val error = (result as BiometricResult.Error).error
        assertTrue(error is BiometricError.Lockout)
        assertEquals(7, error.code)
        assertEquals("Too many attempts", error.message)
    }

    @Test
    fun `concurrent authenticate returns PromptAlreadyRunning for the second caller`() = runTest {
        // Launcher that never resolves on its own, so the first call stays suspended.
        val launcher = FakePromptLauncher()
        val controller = PromptController(launcher)

        val first = async { controller.authenticate(request()) }
        runCurrent() // let the first call acquire the mutex and suspend

        val second = controller.authenticate(request())
        assertTrue(second is BiometricResult.Error)
        assertTrue((second as BiometricResult.Error).error is BiometricError.PromptAlreadyRunning)

        // Finish the first call so the test doesn't leak a suspended coroutine.
        launcher.emitSuccess()
        advanceUntilIdle()
        assertEquals(BiometricResult.Success, first.await())
    }

    @Test
    fun `cancelling the caller dismisses the prompt`() = runTest {
        val launcher = FakePromptLauncher()
        val controller = PromptController(launcher)

        val job = launch { controller.authenticate(request()) }
        runCurrent()
        assertEquals(1, launcher.authenticateCount)

        job.cancel()
        runCurrent()

        assertTrue(job.isCancelled)
        assertEquals(1, launcher.cancelCount)
    }

    @Test
    fun `flow emits Started then Succeeded and completes`() = runTest {
        val launcher = FakePromptLauncher()
        val controller = PromptController(launcher)

        val events = mutableListOf<BiometricEvent>()
        val job = launch {
            controller.authenticateFlow(request()).collect { events.add(it) }
        }
        runCurrent()
        launcher.emitSuccess()
        advanceUntilIdle()

        assertEquals(listOf<BiometricEvent>(BiometricEvent.Started, BiometricEvent.Succeeded), events)
        assertTrue(job.isCompleted)
    }

    @Test
    fun `flow emits Failed for a recoverable failure then continues`() = runTest {
        val launcher = FakePromptLauncher()
        val controller = PromptController(launcher)

        val events = mutableListOf<BiometricEvent>()
        val job = launch {
            controller.authenticateFlow(request()).collect { events.add(it) }
        }
        runCurrent()
        launcher.emitFailed()
        runCurrent()
        launcher.emitSuccess()
        advanceUntilIdle()

        assertEquals(3, events.size)
        assertTrue(events[0] is BiometricEvent.Started)
        assertTrue(events[1] is BiometricEvent.Failed)
        assertTrue(events[2] is BiometricEvent.Succeeded)
        assertTrue(job.isCompleted)
    }

    @Test
    fun `prompt info sets title and default strong authenticators`() {
        val req = BiometricRequest {
            title = "Confirm"
            subtitle = "Sub"
        }
        // The pure request carries the requested values.
        assertEquals("Confirm", req.title)
        assertEquals("Sub", req.subtitle)
        assertEquals(Authenticators.BIOMETRIC_STRONG, req.allowedAuthenticators)
        assertEquals("Cancel", req.negativeButtonText)
        assertTrue(req.confirmationRequired)
    }

    @Test
    fun `request requires a non-empty title`() {
        val thrown = runCatching { BiometricRequest { } }.exceptionOrNull()
        assertTrue(thrown is IllegalArgumentException)
    }

    @Test
    fun `error mapping covers unknown codes`() {
        val mapped = BiometricError.from(9999, "weird")
        assertTrue(mapped is BiometricError.Unknown)
        assertEquals(9999, mapped.code)
        assertEquals("weird", mapped.message)
    }

    @Test
    fun `error mapping maps all standard codes`() {
        // Reference the real androidx constants (compile-time inlined ints) so the mapping is
        // verified against the actual platform codes, not hardcoded guesses.
        fun code(c: Int) = BiometricError.from(c, "m")

        assertTrue(code(androidx.biometric.BiometricPrompt.ERROR_HW_UNAVAILABLE) is BiometricError.HardwareUnavailable)
        assertTrue(code(androidx.biometric.BiometricPrompt.ERROR_NO_BIOMETRICS) is BiometricError.NoBiometricsEnrolled)
        assertTrue(code(androidx.biometric.BiometricPrompt.ERROR_UNABLE_TO_PROCESS) is BiometricError.UnableToProcess)
        assertTrue(code(androidx.biometric.BiometricPrompt.ERROR_USER_CANCELED) is BiometricError.UserCanceled)
        assertTrue(code(androidx.biometric.BiometricPrompt.ERROR_LOCKOUT) is BiometricError.Lockout)
        assertTrue(code(androidx.biometric.BiometricPrompt.ERROR_LOCKOUT_PERMANENT) is BiometricError.LockoutPermanent)
        assertTrue(code(androidx.biometric.BiometricPrompt.ERROR_VENDOR) is BiometricError.Vendor)
        assertTrue(code(androidx.biometric.BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL) is BiometricError.NoDeviceCredential)
        assertTrue(code(androidx.biometric.BiometricPrompt.ERROR_TIMEOUT) is BiometricError.Timeout)
        assertTrue(code(androidx.biometric.BiometricPrompt.ERROR_CANCELED) is BiometricError.Canceled)
    }
}
