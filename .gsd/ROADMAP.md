# ROADMAP.md — Project Roadmap

## Milestones & Phases

- [x] **Milestone 0: Groundwork & Architecture Contracts**
  - [x] Phase 0.1: Contract definitions & DTOs in `android-contracts`
  - [x] Phase 0.2: Backend Cloudflare worker endpoints & Supabase migrations

- [x] **Milestone 1: Core Android Blocker & Focus Mode**
  - [x] Phase 1.1: `AppBlockerService` accessibility implementation
  - [x] Phase 1.2: `UsageManager` daily quota evaluation & screen time tracking
  - [x] Phase 1.3: `FocusTimerService` & Procedural ambient audio synthesis
  - [x] Phase 1.4: Strict lockdown of distracting apps (YouTube, Reels, etc.) during active Focus Sessions
  - [x] Phase 1.5: Accessibility service performance tuning (dropped heavy window content events, node recycling)

- [x] **Milestone 2: Resilience, Anti-Bypass & UX Polish**
  - [x] Phase 2.1: GSD integration, GitHub CLI, CodeRabbit, Supabase MCP, Playwright MCP
  - [x] Phase 2.2: Blocker back-press protection (prevent back button leaking underlying distracting app)
  - [x] Phase 2.3: Accessibility Service active status detection & one-click recovery banner on Dashboard
  - [x] Phase 2.4: Broad package filtering refinement (prevent ignoring `com.android.chrome` or popular apps)
  - [x] Phase 2.5: Manage Restricted Apps quick access directly from Dashboard

- [ ] **Milestone 3: Verification & Automated Testing**
  - [ ] Phase 3.1: Playwright MCP web testing on dashboard / backend endpoints
  - [ ] Phase 3.2: Android UI automated test suite
  - [ ] Phase 3.3: CodeRabbit review run & autofix pass
