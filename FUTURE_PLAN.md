# FUTURE_PLAN.md — CodeUnlock Future Expansion & Roadmap

This document outlines the architectural plan and product vision for expanding **CodeUnlock** beyond software engineering into a **Universal Knowledge & Screen-Time Discipline Platform**.

---

## 1. Background & Vision

Currently, CodeUnlock requires users to solve algorithmic coding problems (LeetCode, Codeforces, DSA) to earn phone screen time and unlock restricted apps. 

While this is ideal for software engineers and computer science students, digital distraction affects everyone:
- Competitive exam aspirants (UPSC, GATE, CAT, GRE, SAT, JEE)
- Language learners (Spanish, German, Japanese, English vocabulary)
- Students memorizing coursework or formulas
- General smartphone users seeking digital detox through quick micro-learning

**The Vision**: Transform CodeUnlock into a dynamic, persona-driven platform where any user selects their learning background, and the entire app (dashboard, unlock challenges, tracks, and stats) adapts to their goals.

---

## 2. Onboarding & Persona Selection

Upon first launch (or anytime in Settings), the user selects their **Discipline Persona**:

```
                       ┌──────────────────────────────────────────────┐
                       │    How do you want to earn screen time?      │
                       └──────────────────────┬───────────────────────┘
                                              │
         ┌──────────────────┬─────────────────┼──────────────────┬──────────────────┐
         ▼                  ▼                 ▼                  ▼                  ▼
    💻 Coder          📚 Exam Aspirant  🌍 Language Learner   🧠 Trivia/Logic    🛠️ Custom (BYO)
  • LeetCode/CF      • Aptitude & GK    • Vocab & Grammer     • Brain teasers    • Import Anki/CSV
  • Code Editor      • 3-5 MCQs         • Flashcard recall    • Quick puzzles    • Custom Flashcards
```

---

## 3. Persona Comparison Matrix

| Feature | 💻 Coder (Current) | 📚 Exam Aspirant | 🌍 Language Learner | 🧠 General / Trivia | 🛠️ Custom / BYO |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Unlock Challenge** | Monaco/Code editor + test runner | 3–5 timed Multiple Choice Questions (MCQs) | Spaced-repetition flashcards / vocab match | Micro logic puzzle or trivia drill | Custom user flashcard deck |
| **Typical Solve Time**| 5–15 minutes | 1–3 minutes | 1–2 minutes | 1 minute | User defined |
| **Dashboard Stats** | LeetCode rating, Codeforces rank | Quiz accuracy %, questions answered | Words mastered, SRS retention rate | Daily trivia streak, Habit Score | Cards reviewed today |
| **Tracks Tab** | Arrays, Trees, Dynamic Programming | Quant, Verbal, Logical Reasoning, GK | A1 Beginner, B1 Intermediate, C1 Vocab | Mental Math, Critical Thinking | User imported categories |
| **Data Source** | LeetCode/CF APIs & local curated DSA | Local SQLite question banks / Supabase | Local vocabulary dictionary / Anki import | Daily trivia database | User CSV/JSON upload |

---

## 4. Proposed Technical Architecture

### 4.1. The Strategy Pattern (`ChallengeProvider`)
Decouple the locking and verification gate from coding-specific logic:

```kotlin
interface ChallengeProvider {
    val persona: UserPersona
    suspend fun getNextChallenge(): ChallengeModel
    suspend fun verifySubmission(submission: ChallengeSubmission): VerificationResult
}

class CodingChallengeProvider : ChallengeProvider { ... }
class QuizChallengeProvider : ChallengeProvider { ... }
class FlashcardChallengeProvider : ChallengeProvider { ... }
```

### 4.2. Local Storage (`Room` / SQLite)
- Store offline question banks (Aptitude, GRE Vocab, General Knowledge, Logic Puzzles) in an embedded Room database.
- Completely offline-first: non-coding challenges work instantly with zero internet connectivity.

### 4.3. Dynamic Dashboard
- Replace hardcoded LeetCode/Codeforces cards with modular Compose cards that observe the active `UserPersona`.
- For `CODING`: Render LeetCode & Codeforces sync cards.
- For `STUDENT_APTITUDE`: Render Question Accuracy %, Solved Today, and Topic Breakdown.
- For `LANGUAGE`: Render Flashcard Mastery Level & Words Reviewed.

---

## 5. Phased Implementation Roadmap

- [ ] **Phase 1: Persona Domain & Settings**
  - Add `UserPersona` enum (`CODING`, `EXAM_ASPIRANT`, `LANGUAGE_LEARNER`, `GENERAL_TRIVIA`).
  - Add persona selection card in Onboarding and Settings screen.
  - Persist active persona in `BlockingSettingsStore`.

- [ ] **Phase 2: Modular Challenge Engine**
  - Create `ChallengeProvider` abstraction.
  - Retain `CodingChallengeProvider` as default for `CODING`.
  - Implement `QuizChallengeProvider` with an initial local question bank (Quantitative Aptitude & Logic).

- [ ] **Phase 3: Universal Unlock UI**
  - Refactor `LockActivity` and `SolveScreen` into a unified `ChallengeScreen`:
    - Renders Code Editor for `CODING`.
    - Renders interactive MCQ cards with immediate explanations for `EXAM_ASPIRANT` and `GENERAL_TRIVIA`.
    - Renders SRS flashcards for `LANGUAGE_LEARNER`.

- [ ] **Phase 4: Dynamic Dashboard & Tracks**
  - Adapt Dashboard cards based on the selected persona.
  - Adapt Tracks screen to show relevant study tracks (Aptitude modules, Vocabulary levels, etc.).

- [ ] **Phase 5: Custom Flashcards & Anki Import (BYO)**
  - Allow users to import their own CSV or Anki flashcards to study their specific subjects/exams.
