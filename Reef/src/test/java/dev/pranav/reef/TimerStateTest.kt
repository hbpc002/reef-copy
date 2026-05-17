package dev.pranav.reef

import dev.pranav.reef.timer.PomodoroConfig
import dev.pranav.reef.timer.PomodoroPhase
import dev.pranav.reef.timer.TimerSessionState
import dev.pranav.reef.timer.TimerStateManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class TimerStateTest {

    @Test
    fun initialState_isNotRunning() {
        val state = TimerStateManager.state.value
        assertFalse(state.isRunning)
        assertFalse(state.isPaused)
        assertEquals(PomodoroPhase.FOCUS, state.pomodoroPhase)
        assertEquals(0, state.timeRemaining)
    }

    @Test
    fun updateState_changesIsRunning() {
        TimerStateManager.updateState { copy(isRunning = true) }
        assertTrue(TimerStateManager.state.value.isRunning)
        TimerStateManager.reset()
    }

    @Test
    fun updateState_changesPausedState() {
        TimerStateManager.updateState { copy(isRunning = true, isPaused = true) }
        val state = TimerStateManager.state.value
        assertTrue(state.isPaused)
        TimerStateManager.reset()
    }

    @Test
    fun updateState_changesTimeRemaining() {
        TimerStateManager.updateState { copy(timeRemaining = 1500000) }
        assertEquals(1500000, TimerStateManager.state.value.timeRemaining)
        TimerStateManager.reset()
    }

    @Test
    fun updateState_pomodoroPhaseTransition() {
        TimerStateManager.updateState { copy(pomodoroPhase = PomodoroPhase.SHORT_BREAK) }
        assertEquals(PomodoroPhase.SHORT_BREAK, TimerStateManager.getCurrentPhase())
        TimerStateManager.reset()
    }

    @Test
    fun updateState_longBreakPhase() {
        TimerStateManager.updateState { copy(pomodoroPhase = PomodoroPhase.LONG_BREAK) }
        assertEquals(PomodoroPhase.LONG_BREAK, TimerStateManager.getCurrentPhase())
        TimerStateManager.reset()
    }

    @Test
    fun updateState_completePhase() {
        TimerStateManager.updateState { copy(pomodoroPhase = PomodoroPhase.COMPLETE) }
        assertEquals(PomodoroPhase.COMPLETE, TimerStateManager.getCurrentPhase())
        TimerStateManager.reset()
    }

    @Test
    fun getTimeRemaining_returnsCorrectValue() {
        TimerStateManager.updateState { copy(timeRemaining = 600000) }
        assertEquals(600000, TimerStateManager.getTimeRemaining())
        TimerStateManager.reset()
    }

    @Test
    fun isInBreak_returnsTrueForShortBreak() {
        TimerStateManager.updateState { copy(pomodoroPhase = PomodoroPhase.SHORT_BREAK) }
        assertTrue(TimerStateManager.isInBreak())
        TimerStateManager.reset()
    }

    @Test
    fun isInBreak_returnsTrueForLongBreak() {
        TimerStateManager.updateState { copy(pomodoroPhase = PomodoroPhase.LONG_BREAK) }
        assertTrue(TimerStateManager.isInBreak())
        TimerStateManager.reset()
    }

    @Test
    fun isInBreak_returnsFalseForFocus() {
        TimerStateManager.updateState { copy(pomodoroPhase = PomodoroPhase.FOCUS) }
        assertFalse(TimerStateManager.isInBreak())
        TimerStateManager.reset()
    }

    @Test
    fun isInBreak_returnsFalseForComplete() {
        TimerStateManager.updateState { copy(pomodoroPhase = PomodoroPhase.COMPLETE) }
        assertFalse(TimerStateManager.isInBreak())
        TimerStateManager.reset()
    }

    @Test
    fun setPomodoroConfig_storesConfig() {
        val config = PomodoroConfig(
            focusDuration = 1500000,
            shortBreakDuration = 300000,
            longBreakDuration = 900000,
            cyclesBeforeLongBreak = 4
        )
        TimerStateManager.setPomodoroConfig(config)
        val retrieved = TimerStateManager.getPomodoroConfig()
        assertNotNull(retrieved)
        assertEquals(1500000, retrieved!!.focusDuration)
        assertEquals(300000, retrieved.shortBreakDuration)
        assertEquals(900000, retrieved.longBreakDuration)
        assertEquals(4, retrieved.cyclesBeforeLongBreak)
        TimerStateManager.reset()
    }

    @Test
    fun reset_clearsAllState() {
        TimerStateManager.updateState {
            copy(
                isRunning = true,
                isPaused = false,
                timeRemaining = 1500000,
                pomodoroPhase = PomodoroPhase.SHORT_BREAK
            )
        }
        TimerStateManager.setPomodoroConfig(
            PomodoroConfig(1500000, 300000, 900000, 4)
        )

        TimerStateManager.reset()

        val state = TimerStateManager.state.value
        assertFalse(state.isRunning)
        assertFalse(state.isPaused)
        assertEquals(0, state.timeRemaining)
        assertEquals(PomodoroPhase.FOCUS, state.pomodoroPhase)
        assertNull(TimerStateManager.getPomodoroConfig())
    }

    @Test
    fun stateFlow_emitsUpdatedValues() = runBlocking {
        TimerStateManager.updateState { copy(isRunning = true) }
        val state = TimerStateManager.state.first { it.isRunning }
        assertTrue(state.isRunning)
        TimerStateManager.reset()
    }

    @Test
    fun updateState_multipleTransitions() {
        val states = listOf(
            TimerSessionState(isRunning = true, isPaused = false),
            TimerSessionState(isRunning = true, isPaused = true),
            TimerSessionState(isRunning = false, isPaused = false),
            TimerSessionState(
                isRunning = true,
                pomodoroPhase = PomodoroPhase.SHORT_BREAK
            ),
            TimerSessionState(
                isRunning = true,
                pomodoroPhase = PomodoroPhase.LONG_BREAK
            )
        )
        for (s in states) {
            TimerStateManager.updateState { s }
            assertEquals(s, TimerStateManager.state.value)
        }
        TimerStateManager.reset()
    }

    @Test
    fun pomodoroConfig_defaultIsNull() {
        assertNull(TimerStateManager.getPomodoroConfig())
    }

    @Test
    fun runningTimer_sessionState() {
        TimerStateManager.updateState {
            copy(
                isRunning = true,
                timeRemaining = 2500000,
                isPomodoroMode = true,
                currentCycle = 2,
                totalCycles = 4,
                isStrictMode = true
            )
        }
        val state = TimerStateManager.state.value
        assertTrue(state.isRunning)
        assertEquals(2500000, state.timeRemaining)
        assertTrue(state.isPomodoroMode)
        assertEquals(2, state.currentCycle)
        assertEquals(4, state.totalCycles)
        assertTrue(state.isStrictMode)
        TimerStateManager.reset()
    }
}
