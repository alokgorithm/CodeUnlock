# SPEC.md — CodeUnlock Specification

> **Status**: `FINALIZED`
>
> ⚠️ **Planning Lock**: Requirements and scope are finalized.

## Vision
CodeUnlock is an anti-distraction and algorithmic discipline Android application paired with a serverless backend. It locks distracting mobile apps (YouTube, Instagram, TikTok, Twitter, Reddit, etc.) and requires the user to solve algorithmic coding challenges (LeetCode, Codeforces) or complete deep-work coding focus sessions to earn screen time unlock allowances.

## Core Pillars & Goals
1. **Uncompromising App Blocking**: Enforce daily time quotas and instantaneous lockdown during active Focus Sessions (Pomodoro sprints) via Android Accessibility Service (`AppBlockerService`), with anti-bypass guardrails.
2. **Coding-Gated Unlocks**: Seamlessly reward users with 30-minute screen time sessions upon verified problem completions on LeetCode / Codeforces.
3. **Focus Timer & Audio Synthesis**: Foreground Pomodoro timer with procedural ambient soundscapes (`AmbientSoundManager`) that strictly block distractions during focus sprints.
4. **Offline-First Resilience**: All core blocking and tracking logic functions 100% offline with zero latency (0ms local startup), with asynchronous cloud synchronization to Supabase when connectivity is available.
5. **Anti-Uninstall & Device Integrity**: Device Administrator protection (`CodeUnlockDeviceAdminReceiver`) and settings guard interceptors preventing unauthorized bypassing.

## Non-Goals (Out of Scope)
- No invasive spyware or keystroke recording (only window and package change detection).
- No blocking of essential system utilities (Phone, SMS, Emergency Dialer, System Settings navigation).
- No paid paywalls for core screen time discipline features.

## Technical Architecture
- **Android App**: Kotlin, Jetpack Compose, Material 3, Hilt DI, Retrofit, OkHttp, Coroutines, Room/DataStore.
- **Backend**: Cloudflare Workers, TypeScript, Hono, Supabase PostgreSQL with Row Level Security (RLS).
- **Enforcement Layer**: Android `AccessibilityService` (`AppBlockerService`), `UsageStatsManager`, `ActivityManager`.
- **Integrations**: GitHub CLI (`gh`), CodeRabbit automated reviewer, Playwright MCP, Supabase MCP.

## Success Criteria
- [x] Distracting apps (YouTube, Instagram, etc.) are strictly locked during active Focus Sessions.
- [x] Free unlock 1-minute grace period is suspended during focus sprints.
- [x] Accessibility service state is actively monitored and recovered with clear user guidance.
- [x] Zero UI stutter / GC frame drops during accessibility inspection.
- [x] Emergency unlock is properly disabled during focus mode.
- [x] Offline dashboard loads immediately with local state fallback.

---
*Last updated: 2026-10-20*
