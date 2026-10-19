# ARCHITECTURE.md — System Architecture

## Architecture Overview

```mermaid
graph TD
    subgraph Android App ["Android Client (:app)"]
        UI["Jetpack Compose UI (Dashboard, Lock, Focus Timer)"]
        ABS["AppBlockerService (Accessibility Service)"]
        FTS["FocusTimerService (Foreground Service)"]
        UM["UsageManager (Quota & Focus Blocker Logic)"]
        USS["UnlockSessionStore & BlockingSettingsStore (Local Cache)"]
        REPO["CodeUnlockRepository (Retrofit)"]
    end

    subgraph Backend ["Serverless API (:backend)"]
        CF["Cloudflare Worker (Hono Router)"]
        AUTH["Supabase JWT Auth Middleware"]
        SYNC["Daily Sync & Progress Worker"]
        VERIFY["LeetCode / Codeforces Scraper & Verifier"]
    end

    subgraph Database ["Cloud Infrastructure"]
        SUPA["Supabase PostgreSQL (Profiles, Unlocks, Progress)"]
        KV["Cloudflare KV (Rate Limiting)"]
    end

    ABS -->|Event: Window Changed| UM
    UM -->|Check Focus Active| FTS
    UM -->|Read Local Quota| USS
    UM -->|If Blocked| UI
    UI -->|Verify Solve| REPO
    REPO -->|HTTPS REST| CF
    CF --> AUTH
    CF --> VERIFY
    CF --> SUPA
```

## Module Boundaries

1. **`:android-contracts`**:
   - DTOs, API Interfaces (`CodeUnlockApiService`), OkHttp client, Retrofit network module, repository abstractions.
2. **`:app`**:
   - **`service`**: `AppBlockerService` (accessibility window listener), `FocusTimerService` (Pomodoro timer + audio synthesis), `ReelsShortsInterceptor`, `SettingsGuardInterceptor`.
   - **`data`**: `UsageManager`, `UnlockSessionStore`, `BlockingSettingsStore`, `FocusSessionStore`.
   - **`ui`**: Material 3 screens: `DashboardScreen`, `LockActivity`, `FocusTimerScreen`, `PermissionSetupScreen`, `ProblemScreen`.
3. **`backend`**:
   - Cloudflare Workers REST API with routes for problems, verification, unlocks, wellbeing sync, rankings, and social classrooms.
4. **`GSD & Integrations`**:
   - Workflows in `.agent/workflows/`
   - Agent skills in `.agents/skills/`
   - MCP configurations for Supabase & Playwright in `mcp_config.json`.
   - CodeRabbit code review plugin in `.agents/plugins/coderabbit`.
