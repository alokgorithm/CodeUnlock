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
  - [x] Phase 2.6: Offline-first fallback resilience (instant Top 200 catalog, Badges, Profiles, and Local Verification Solve sessions)

- [ ] **Milestone 3: Verification & Automated Testing**
  - [x] Phase 3.1: Android Contract Serialization & App Blocker unit tests (`testDebugUnitTest` passing cleanly)
  - [x] Phase 3.2: Backend Cloudflare Worker TypeScript typechecking (`tsc --noEmit` passing cleanly)
  - [ ] Phase 3.3: Playwright MCP integration tests for backend & dashboard web surfaces
  - [ ] Phase 3.4: CodeRabbit CLI host execution and security review pass

- [ ] **Milestone 4: Multi-Persona & Non-Coding Knowledge Tracks (Future)**
  - Detailed design & technical plan: [FUTURE_PLAN.md](../FUTURE_PLAN.md)
  - [ ] Phase 4.1: Persona selection during onboarding and settings (`UserPersona`: Coder, Exam Aspirant, Language Learner, Trivia)
  - [ ] Phase 4.2: Modular `ChallengeProvider` strategy engine (MCQs, Flashcards, Coding Editor)
  - [ ] Phase 4.3: Dynamic dashboard metrics adapting to user persona
  - [ ] Phase 4.4: Offline Room database for non-coding question banks & flashcard decks
