# DECISIONS.md — Architecture & Design Decisions

## 1. Focus Mode Strict Lockdown
- **Date**: 2026-10-19
- **Context**: Users were able to bypass Focus Mode and open YouTube / entertainment apps because daily usage allowance was set to 4 hours in "focused" mode.
- **Decision**: Whenever `FocusTimerService.isRunning.value && !FocusTimerService.isBreakTime.value`, evaluate `shouldBlock = true` with reason `FOCUS_SESSION_ACTIVE` for any restricted or distracting package (`UsageManager.isDistractingApp()`).
- **Consequence**: Focus sprints guarantee deep-work isolation regardless of unused daily screen time.

## 2. No Free Unlocks or Emergency Bypasses During Sprints
- **Date**: 2026-10-19
- **Context**: 1-minute daily free unlocks and 30-minute emergency unlocks could defeat the purpose of an active Pomodoro session.
- **Decision**: In `AppBlockerService.kt`, bypass `tryGrantFreeUnlock` when `isFocusActive` is true. In `LockActivity.kt`, hide/disable emergency unlock during active focus sprints.

## 3. GSD Protocol for Antigravity
- **Date**: 2026-10-20
- **Context**: Context rot in extended AI sessions leads to hallucinations, regressions, and lost decisions.
- **Decision**: Adopt the Get Shit Done (GSD) spec-driven methodology with dedicated subagents (`gsd-planner`, `gsd-executor`, `gsd-verifier`) and persistent state files in `.gsd/`.
