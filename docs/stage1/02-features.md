# 2. Feature Specifications

Eleven features, each classified as deterministic, AI, or hybrid. That classification decides how the feature is tested in Stage 3: deterministic behaviour through automated unit and integration tests, agent behaviour through KUMA.

| ID | Feature | Type |
|---|---|---|
| F01 | Lifter Profile and Constraints | Deterministic |
| F02 | Workout Import | Deterministic |
| F03 | Recovery Import | Deterministic |
| F04 | Strength Analytics | Deterministic |
| F05 | Training Block Generation | AI |
| F06 | Equipment Substitution | AI |
| F07 | Recovery Aware Session Adjustment | Hybrid |
| F08 | Meet Attempt Selection | Hybrid |
| F09 | Macro Targets and Adherence Tracking | Deterministic |
| F10 | Vegetarian Meal Suggestion | AI |
| F11 | Coaching Chat with Memory | AI |

## F01 — Lifter Profile and Constraints

- **Description:** Stores the lifter's identity, competition class, meet date, training frequency, gym equipment inventory, dietary constraints, and macro targets. Every other feature reads from this record.
- **User Interaction:** Profile tab in the GUI, with fields for bodyweight, weight class, meet date, training days per week, an equipment checklist, and dietary flags. CLI: `liftpilot profile set`, `liftpilot profile show`.
- **Input:** Bodyweight, weight class, meet date, training days, equipment list, dietary constraints, macro targets.
- **Output:** A saved `LifterProfile` record.
- **AI Involvement:** Deterministic.
- **Expected Workflow:** The lifter fills the form and saves; `CoachController.saveProfile()` validates required fields and persists through `ProfileRepository`; a `PROFILE_UPDATED` event refreshes dependent panels.
- **Error and Alternative Cases:** A missing required field blocks the save with an inline message. A meet date in the past is rejected. An empty equipment list is allowed and defaults to bodyweight only, with a warning that plan generation will be limited.

## F02 — Workout Import

- **Description:** Imports a Hevy CSV export, or accepts manual entry, to build the training history.
- **User Interaction:** Log tab, Import Hevy CSV button and a manual entry form. CLI: `liftpilot import hevy <file>`, `liftpilot log add`.
- **Input:** A Hevy export CSV file, or a manually typed exercise with sets, reps, weight, and RPE.
- **Output:** `WorkoutSession` and `SetEntry` records in the database.
- **AI Involvement:** Deterministic.
- **Expected Workflow:** `DataSourceFactory` identifies the file, `HevyCsvAdapter` converts rows into domain objects, `CsvImportService` resolves exercise names through `ExerciseCatalog` and removes duplicates, valid sessions are saved, and an `ImportReport` summarises the result.
- **Error and Alternative Cases:** A file whose header matches no known format is rejected before anything is saved. Malformed rows are skipped and listed in the report while valid rows continue. A duplicate session is skipped by default, with an option to import anyway. An unrecognised exercise name opens the mapping dialog, where the lifter maps it, adds it as a new exercise, or skips it.

## F03 — Recovery Import

- **Description:** Imports a Whoop CSV export containing daily sleep, HRV, and recovery score data.
- **User Interaction:** Log tab, Import Whoop CSV button. CLI: `liftpilot import whoop <file>`.
- **Input:** A Whoop export CSV file.
- **Output:** `RecoveryDay` records in the database, and recovery flags on upcoming sessions.
- **AI Involvement:** Deterministic.
- **Expected Workflow:** `WhoopCsvAdapter` parses the file, `CsvImportService` validates dates and numeric fields, records are saved, and `RecoveryRuleEngine` evaluates upcoming training days.
- **Error and Alternative Cases:** A malformed file is rejected. Missing fields for a day are stored as empty rather than blocking the import. Overlapping date ranges replace older values after a confirmation.

## F04 — Strength Analytics

- **Description:** Computes an estimated one rep max per lift from RPE based sets, weekly tonnage, and progression trends over time.
- **User Interaction:** Analytics tab with a lift selector, chart, and summary figures. CLI: `liftpilot analytics <lift>`.
- **Input:** Stored `WorkoutSession` and `SetEntry` history for the selected lift.
- **Output:** An `AnalyticsSummary` with an e1RM series, weekly tonnage, best set, and data quality warnings.
- **AI Involvement:** Deterministic.
- **Expected Workflow:** `StrengthAnalytics.computeE1RM()` applies `RPEChart` to each set, aggregates by week, and returns the series the GUI plots.
- **Error and Alternative Cases:** No history shows an empty state rather than an error. A set with an invalid RPE (outside 6 to 10), or RPE 10 above one rep, is excluded and reported as a data quality warning.

## F05 — Training Block Generation

- **Description:** The agent generates a multi week training block from the profile, recent history, current e1RMs, and the time remaining until the meet.
- **User Interaction:** Plan tab, Generate Block with a week count. CLI: `liftpilot plan generate <weeks>`.
- **Input:** Block length in weeks, plus the stored profile and history.
- **Output:** A `TrainingBlock` of `Week`, `Session`, and `ExercisePrescription` objects, editable in the GUI.
- **AI Involvement:** AI.
- **Expected Workflow:** `BlockGenerationAgent` collects e1RMs through `AnalyticsTool` and available exercises through `ExerciseDBTool`, `PromptBuilder` assembles the request with an output schema, `ClaudeClient.complete()` calls Claude, `ResponseParser.toBlock()` builds domain objects, and `PlanValidator.validateBlock()` checks loads against the active `ProgressionStrategy` before the block is saved.
- **Error and Alternative Cases:** Invalid JSON or a failed validation triggers one retry with the errors appended to the prompt; a second failure shows an error and leaves any existing block unchanged. An API timeout is retried once, then reported. Thin history for a lift produces a warning and conservative loads. An existing block is replaced only after confirmation.

## F06 — Equipment Substitution

- **Description:** When a prescribed exercise needs equipment the lifter's gym does not have, the agent proposes a substitute with a matching movement pattern and muscle groups.
- **User Interaction:** Plan tab, warning icon on the affected exercise, Suggest Substitute. CLI: `liftpilot plan substitute <prescriptionId>`.
- **Input:** The unavailable exercise and the lifter's equipment list.
- **Output:** A replacement exercise with a short rationale, applied as an undoable plan edit once accepted.
- **AI Involvement:** AI.
- **Expected Workflow:** `ExerciseDBTool` filters candidates deterministically, the model chooses among those candidates and explains the choice, `PlanValidator.validateSubstitution()` confirms the choice came from the list, and acceptance applies a `SwapExerciseCommand`.
- **Error and Alternative Cases:** No candidates means the lifter is told to swap manually. A choice outside the candidate list fails validation and is retried; a second failure shows the candidate list for manual selection. Rejecting the suggestion leaves the plan unchanged.

## F07 — Recovery Aware Session Adjustment

- **Description:** Flags training days that follow poor recovery and lets the agent rewrite that session's volume or intensity.
- **User Interaction:** Plan tab, recovery badge on the session, Adjust Session. CLI: `liftpilot plan flags`, `liftpilot plan adjust <sessionId>`.
- **Input:** The flagged session and the matching `RecoveryDay` data.
- **Output:** A revised `Session` with an adjustment note explaining what changed and why.
- **AI Involvement:** Hybrid. `RecoveryRuleEngine` flags deterministically; the agent rewrites only when asked.
- **Expected Workflow:** The rule engine compares recovery score and sleep hours against thresholds, `AdjustmentAgent` proposes a lighter session, `PlanValidator` confirms nothing was made heavier and no competition lift was removed, and acceptance applies an `ApplyAdjustmentCommand`.
- **Error and Alternative Cases:** Missing recovery data means no flag rather than an assumed bad day. If the agent or the API fails, `RecoveryRuleEngine.fallbackAdjustment()` offers a deterministic reduction instead. The lifter can reject the adjustment.

## F08 — Meet Attempt Selection

- **Description:** Calculates opener, second, and third attempts for each competition lift, with reasoning based on how the block went.
- **User Interaction:** Meet tab, Calculate Attempts. CLI: `liftpilot meet attempts`.
- **Input:** Current e1RMs for squat, bench, and deadlift, plus the recent training trend.
- **Output:** Three attempts per lift, rounded to 2.5 kg, with a written rationale.
- **AI Involvement:** Hybrid. The numbers are deterministic; only the explanation comes from the model.
- **Expected Workflow:** `AttemptCalculator.calculate()` produces attempts at fixed percentages of e1RM and rounds to plate increments, then `AttemptRationaleAgent` writes the rationale, which `PlanValidator.validateRationale()` checks against those numbers.
- **Error and Alternative Cases:** Thin recent data uses a conservative percentage with a warning. A rationale that contradicts the calculator, or an agent failure, results in the attempts being shown without a rationale. The lifter can override any attempt manually.

## F09 — Macro Targets and Adherence Tracking

- **Description:** Stores daily calorie and macro targets and tracks adherence against logged intake.
- **User Interaction:** Nutrition tab with an intake form and daily totals. CLI: `liftpilot macros log <food> <grams>`, `liftpilot macros today`, `liftpilot macros week`.
- **Input:** Macro targets from the profile and daily food entries.
- **Output:** Remaining macros for the day and weekly adherence.
- **AI Involvement:** Deterministic.
- **Expected Workflow:** `MacroTracker.recordIntake()` adds the entry to the day's `DailyIntake`, `getRemaining()` subtracts from the target, and `weeklyAdherence()` averages across days that have entries.
- **Error and Alternative Cases:** An unknown food prompts for macros per 100 g and is added to `FoodCatalog`. Zero, negative, or implausible gram amounts are rejected. Days with no entries are excluded from the weekly average rather than counted as zero.

## F10 — Vegetarian Meal Suggestion

- **Description:** Given the macros remaining for the day and the lifter's dietary constraints, the agent proposes a meal.
- **User Interaction:** Nutrition tab, Suggest a Meal. CLI: `liftpilot meal suggest`.
- **Input:** Remaining macros and the dietary constraints stored in the profile.
- **Output:** A `MealSuggestion` with items, gram amounts, and a full nutrition breakdown.
- **AI Involvement:** AI.
- **Expected Workflow:** `FoodDBTool` returns only foods permitted by `DietaryConstraints`, the model composes a meal from those foods, `ResponseParser.toMeal()` reads nutrition values from `FoodCatalog` rather than from the model, and `PlanValidator.validateMeal()` checks the items and totals.
- **Error and Alternative Cases:** If no combination fits, the agent explains the shortfall instead of inventing a meal. A non permitted item fails validation and is retried. If the day's targets are already met, no suggestion is made.

## F11 — Coaching Chat with Memory

- **Description:** A conversational interface for coaching questions, grounded in the lifter's own data and in earlier conversations.
- **User Interaction:** Coach Chat tab. CLI: `liftpilot chat "<question>"` or an interactive session.
- **Input:** A natural language question.
- **Output:** An answer, usually citing figures retrieved through tools.
- **AI Involvement:** AI.
- **Expected Workflow:** `MemoryManager.recall()` supplies relevant earlier turns, the model selects among `AnalyticsTool`, `PlanLookupTool`, and `RecoveryLookupTool`, the results are returned to it, and the answer is stored by `MemoryManager.remember()` for later recall.
- **Error and Alternative Cases:** A question the tools cannot answer receives an honest statement that the data is unavailable rather than a fabricated figure. An ambiguous question prompts a clarifying question. A failing tool is reported as unavailable. Conversations longer than the history limit have older turns summarised.
