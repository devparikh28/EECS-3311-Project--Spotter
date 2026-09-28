# 2. Feature Specifications

Twelve features, each classified as deterministic, AI, or hybrid. The system serves any strength trainee: a meet date is optional, and the training goal recorded in F01 drives planning for everyone else. That classification decides how the feature is tested in Stage 3: deterministic behaviour through automated unit and integration tests, agent behaviour through KUMA.

| ID | Feature | Type |
|---|---|---|
| F01 | Lifter Profile and Constraints | Deterministic |
| F02 | Workout Logging and Import | Deterministic |
| F03 | Recovery Input | Deterministic |
| F04 | Strength Analytics | Deterministic |
| F05 | Training Block Generation | AI |
| F06 | Equipment Substitution | AI |
| F07 | Recovery Aware Session Adjustment | Hybrid |
| F08 | Max Testing and Attempt Planning | Hybrid |
| F09 | Macro Targets and Adherence Tracking | Deterministic |
| F10 | Vegetarian Meal Suggestion | AI |
| F11 | Coaching Chat with Memory | AI |
| F12 | Plan Adherence and Progression | Hybrid |

## F01 — Lifter Profile and Constraints

- **Description:** Stores the lifter's identity, training goal, optional competition details, training frequency, gym equipment inventory, dietary constraints, and macro targets. Every other feature reads from this record.
- **User Interaction:** Profile tab in the GUI, with fields for bodyweight, training goal, split preference, an optional meet date and weight class, training days per week, an equipment checklist, and dietary flags. CLI: `spotter profile set`, `spotter profile show`.
- **Input:** Bodyweight, training goal (meet prep, strength, hypertrophy, or general fitness), split preference (auto, full body, upper lower, push pull legs, or competition lift focus), optional meet date and weight class, training days, equipment list, dietary constraints, macro targets.
- **Output:** A saved `LifterProfile` record.
- **AI Involvement:** Deterministic.
- **Expected Workflow:** The lifter fills the form and saves; `CoachController.saveProfile()` validates required fields and persists through `ProfileRepository`; a `PROFILE_UPDATED` event refreshes dependent panels.
- **Error and Alternative Cases:** A missing required field blocks the save with an inline message. A meet date is optional; if given it must be in the future. Selecting the meet prep goal without a meet date prompts for one, while every other goal ignores the field entirely. An empty equipment list is allowed and defaults to bodyweight only, with a warning that plan generation will be limited.

## F02 — Workout Logging and Import

- **Description:** Builds the training history from whichever source the lifter actually uses: a recognised export (Hevy), any other app's CSV through a saved column mapping, a one tap confirmation that a prescribed session was completed, or manual entry.
- **User Interaction:** Log tab with Import CSV, a mapping dialog for unrecognised files, and a manual entry form; Plan tab has Log as Prescribed on each session. CLI: `spotter import hevy <file>`, `spotter import csv <file> <profile>`, `spotter log prescribed <sessionId>`, `spotter log add`.
- **Input:** A CSV export from any workout app, or a confirmation with only the sets that differed from the plan, or a manually typed exercise with sets, reps, weight and RPE.
- **Output:** `WorkoutSession` and `SetEntry` records in the database.
- **AI Involvement:** Deterministic.
- **Expected Workflow:** `DataSourceFactory.detect()` recognises a known header and returns the matching adapter. An unrecognised file goes to `suggestMapping()`, which proposes a `ColumnMapping` the lifter confirms or corrects; `GenericCsvAdapter` then reads the file through that mapping, converting units and date formats. The mapping is saved as a named import profile, so the next export from the same app imports in one step. From there the path is identical for every source: `CsvImportService` resolves exercise names, removes duplicates, saves, and returns an `ImportReport`. Log as Prescribed takes the prescribed session and records it as logged, with only the deviations the lifter enters.
- **Error and Alternative Cases:** A file whose header matches no known format opens the mapping dialog rather than being rejected; a mapping missing a required column (date, exercise, weight, reps) fails `ColumnMapping.validate()` with the missing field named. Malformed rows are skipped and listed in the report while valid rows continue. A duplicate session is skipped by default, with an option to import anyway. An unrecognised exercise name opens the mapping dialog, where the lifter maps it, adds it as a new exercise, or skips it.

## F03 — Recovery Input

- **Description:** Records how recovered the lifter is, from a wearable export or from the lifter directly. Whoop is recognised automatically, any other recovery export (Garmin, Oura, Apple Health and similar) is read through a saved column mapping, and a lifter with no wearable at all can complete a short daily check in instead.
- **User Interaction:** Log tab, Import CSV, or the Check In panel with sleep hours, soreness and energy. CLI: `spotter import whoop <file>`, `spotter import csv <file> <profile>`, `spotter checkin`.
- **Input:** A recovery CSV from any source, or sleep hours plus soreness and energy on a one to five scale.
- **Output:** `RecoveryDay` records carrying a `RecoverySource` of wearable export or manual check in, and recovery flags on upcoming sessions.
- **AI Involvement:** Deterministic.
- **Expected Workflow:** A recognised file goes through `WhoopCsvAdapter`, an unrecognised one through `GenericCsvAdapter` and a confirmed mapping, and a check in creates a `RecoveryDay` directly. `RecoveryRuleEngine` then evaluates upcoming training days: against the recovery score and sleep when an objective score exists, and against sleep, soreness and energy thresholds when the source is a check in. The rule engine reads `hasObjectiveScore()` rather than assuming a wearable.
- **Error and Alternative Cases:** A malformed file is rejected; an unrecognised one opens the mapping dialog. Missing fields for a day are stored as empty rather than blocking the import. Overlapping date ranges replace older values after a confirmation. A day with neither an import nor a check in produces no flag, so absence of data is never read as poor recovery.

## F04 — Strength Analytics

- **Description:** Computes an estimated one rep max per lift from RPE based sets, weekly tonnage, and progression trends over time.
- **User Interaction:** Analytics tab with a lift selector, chart, and summary figures. CLI: `spotter analytics <lift>`.
- **Input:** Stored `WorkoutSession` and `SetEntry` history for the selected lift.
- **Output:** An `AnalyticsSummary` with an e1RM series, weekly tonnage, best set, and data quality warnings.
- **AI Involvement:** Deterministic.
- **Expected Workflow:** `StrengthAnalytics.computeE1RM()` applies `RPEChart` to each set, aggregates by week, and returns the series the GUI plots.
- **Error and Alternative Cases:** No history shows an empty state rather than an error. A set with an invalid RPE (outside 6 to 10), or RPE 10 above one rep, is excluded and reported as a data quality warning.

## F05 — Training Block Generation

- **Description:** The agent generates a multi week training block from the profile, the training goal, recent history, current e1RMs, and, when one is set, the time remaining until the meet.
- **User Interaction:** Plan tab, Generate Block with a week count. CLI: `spotter plan generate <weeks>`.
- **Input:** Block length in weeks, plus the stored profile, goal, and history. With a meet date the length defaults to the weeks remaining.
- **Output:** A `TrainingBlock` of `Week`, `Session` and `ExercisePrescription` objects, editable in the GUI. The block records the split it used, and every session carries a focus label such as Upper A, Squat day or Full body, so the plan reads as a programme rather than a list of exercises.
- **AI Involvement:** AI.
- **Expected Workflow:** `BlockGenerationAgent` collects e1RMs through `AnalyticsTool` and available exercises through `ExerciseDBTool`, is told the lifter's training days and split preference, `PromptBuilder` assembles the request with an output schema, `ClaudeClient.complete()` calls Claude, `ResponseParser.toBlock()` builds domain objects, and `PlanValidator.validateBlock()` checks loads against the `ProgressionStrategy` that `ProgressionStrategyFactory.forGoal()` selected for the lifter's goal, and `validateSplit()` confirms the session count matches the lifter's training days and the structure matches the chosen split, before the block is saved.
- **Error and Alternative Cases:** A split preference of auto imposes no structural constraint and lets the agent choose; any other value is enforced, and a block whose structure does not match is rejected with that reason. Invalid JSON or a failed validation triggers one retry with the errors appended to the prompt; a second failure shows an error and leaves any existing block unchanged. An API timeout is retried once, then reported. Thin history for a lift produces a warning and conservative loads. An existing block is replaced only after confirmation.

## F06 — Equipment Substitution

- **Description:** When a prescribed exercise needs equipment the lifter's gym does not have, the agent proposes a substitute with a matching movement pattern and muscle groups.
- **User Interaction:** Plan tab, warning icon on the affected exercise, Suggest Substitute. CLI: `spotter plan substitute <prescriptionId>`.
- **Input:** The unavailable exercise and the lifter's equipment list.
- **Output:** A replacement exercise with a short rationale, applied as an undoable plan edit once accepted.
- **AI Involvement:** AI.
- **Expected Workflow:** `ExerciseDBTool` filters candidates deterministically, the model chooses among those candidates and explains the choice, `PlanValidator.validateSubstitution()` confirms the choice came from the list, and acceptance applies a `SwapExerciseCommand`.
- **Error and Alternative Cases:** No candidates means the lifter is told to swap manually. A choice outside the candidate list fails validation and is retried; a second failure shows the candidate list for manual selection. Rejecting the suggestion leaves the plan unchanged.

## F07 — Recovery Aware Session Adjustment

- **Description:** Flags training days that follow poor recovery and lets the agent rewrite that session's volume or intensity.
- **User Interaction:** Plan tab, recovery badge on the session, Adjust Session. CLI: `spotter plan flags`, `spotter plan adjust <sessionId>`.
- **Input:** The flagged session and the matching `RecoveryDay` data.
- **Output:** A revised `Session` with an adjustment note explaining what changed and why.
- **AI Involvement:** Hybrid. `RecoveryRuleEngine` flags deterministically; the agent rewrites only when asked.
- **Expected Workflow:** The rule engine compares recovery score and sleep hours against thresholds, `AdjustmentAgent` proposes a lighter session, `PlanValidator` confirms nothing was made heavier and no competition lift was removed, and acceptance applies an `ApplyAdjustmentCommand`.
- **Error and Alternative Cases:** Missing recovery data means no flag rather than an assumed bad day. If the agent or the API fails, `RecoveryRuleEngine.fallbackAdjustment()` offers a deterministic reduction instead. The lifter can reject the adjustment.

## F08 — Max Testing and Attempt Planning

- **Description:** Plans a heavy single for each main lift, with reasoning based on how the block went. With a meet date set, it produces competition attempts (opener, second, third); without one, it produces a test day plan (warm up progression and a top single) for a lifter who simply wants to see where their strength is.
- **User Interaction:** Meet tab, Plan Test Day or Calculate Attempts depending on the goal. CLI: `spotter meet attempts`, `spotter test plan`.
- **Input:** Current e1RMs for the main lifts, the recent training trend, and whether a meet date is set.
- **Output:** With a meet date, three attempts per lift rounded to 2.5 kg. Without one, a `TestDayPlan` with warm up sets and a top single. Both carry a written rationale.
- **AI Involvement:** Hybrid. The numbers are deterministic; only the explanation comes from the model.
- **Expected Workflow:** `AttemptCalculator.calculateMeetAttempts()` or `AttemptCalculator.projectTestDay()` produces the numbers at fixed percentages of e1RM, rounded to plate increments, then `AttemptRationaleAgent` writes the rationale, which `PlanValidator.validateRationale()` checks against those numbers.
- **Error and Alternative Cases:** Thin recent data uses a conservative percentage with a warning. A rationale that contradicts the calculator, or an agent failure, results in the numbers being shown without a rationale. The lifter can override any value manually.

## F09 — Macro Targets and Adherence Tracking

- **Description:** Stores daily calorie and macro targets and tracks adherence against logged intake.
- **User Interaction:** Nutrition tab with an intake form and daily totals. CLI: `spotter macros log <food> <grams>`, `spotter macros today`, `spotter macros week`.
- **Input:** Macro targets from the profile and daily food entries.
- **Output:** Remaining macros for the day and weekly adherence.
- **AI Involvement:** Deterministic.
- **Expected Workflow:** `MacroTracker.recordIntake()` adds the entry to the day's `DailyIntake`, `getRemaining()` subtracts from the target, and `weeklyAdherence()` averages across days that have entries.
- **Error and Alternative Cases:** An unknown food prompts for macros per 100 g and is added to `FoodCatalog`. Zero, negative, or implausible gram amounts are rejected. Days with no entries are excluded from the weekly average rather than counted as zero.

## F10 — Vegetarian Meal Suggestion

- **Description:** Given the macros remaining for the day and the lifter's dietary constraints, the agent proposes a meal.
- **User Interaction:** Nutrition tab, Suggest a Meal. CLI: `spotter meal suggest`.
- **Input:** Remaining macros and the dietary constraints stored in the profile.
- **Output:** A `MealSuggestion` with items, gram amounts, and a full nutrition breakdown.
- **AI Involvement:** AI.
- **Expected Workflow:** `FoodDBTool` returns only foods permitted by `DietaryConstraints`, the model composes a meal from those foods, `ResponseParser.toMeal()` reads nutrition values from `FoodCatalog` rather than from the model, and `PlanValidator.validateMeal()` checks the items and totals.
- **Error and Alternative Cases:** If no combination fits, the agent explains the shortfall instead of inventing a meal. A non permitted item fails validation and is retried. If the day's targets are already met, no suggestion is made.

## F11 — Coaching Chat with Memory

- **Description:** A conversational interface for coaching questions, grounded in the lifter's own data and in earlier conversations.
- **User Interaction:** Coach Chat tab. CLI: `spotter chat "<question>"` or an interactive session.
- **Input:** A natural language question.
- **Output:** An answer, usually citing figures retrieved through tools.
- **AI Involvement:** AI.
- **Expected Workflow:** `MemoryManager.recall()` supplies relevant earlier turns, the model selects among `AnalyticsTool`, `PlanLookupTool`, and `RecoveryLookupTool`, the results are returned to it, and the answer is stored by `MemoryManager.remember()` for later recall.
- **Error and Alternative Cases:** A question the tools cannot answer receives an honest statement that the data is unavailable rather than a fabricated figure. An ambiguous question prompts a clarifying question. A failing tool is reported as unavailable. Conversations longer than the history limit have older turns summarised.

## F12 — Plan Adherence and Progression

- **Description:** Compares what the block prescribed against what was actually logged, and uses that comparison to advance or hold the remaining weeks. This is what closes the coaching loop: without it the system plans and never learns whether the plan was followed.
- **User Interaction:** Plan tab, Review Week shows the comparison, Progress Plan applies a revision. CLI: `spotter plan review <week>`, `spotter plan progress <week>`.
- **Input:** The active `TrainingBlock` and the `WorkoutSession` records logged during that week.
- **Output:** An `AdherenceReport` with a completion rate and, per exercise, prescribed against completed sets and reps, target against actual RPE, and a verdict. Optionally a revised set of remaining weeks.
- **AI Involvement:** Hybrid. The comparison is deterministic; only the revision is generated.
- **Expected Workflow:** `PlanAdherence.compare()` matches each prescription to the logged sets and assigns a verdict such as `COMPLETED_BELOW_TARGET_RPE` or `MISSED_REPS`. If there is a shortfall, or the lifter asks, `ProgressionAgent` revises the remaining weeks using the report through `AdherenceTool`, and `PlanValidator.validateProgression()` confirms loads advance only where the verdict permits, that week over week increases stay within a cap, and that no competition lift was dropped. An accepted revision is applied as an `ApplyProgressionCommand`, so it can be undone.
- **Error and Alternative Cases:** A session with no matching log is reported as `MISSED_SESSION` rather than assumed complete. A logged set with no RPE is compared on reps alone. If the agent fails or its revision fails validation twice, the report is still shown, since the comparison is deterministic and useful on its own. The lifter can reject a revision.

**Worked example.** A prescription of three reps at RPE 8 with a logged set of two reps at RPE 7 produces `MISSED_REPS` with the note that actual RPE was below target: the volume was not completed but the effort was low, which points to a stopped set or drifting RPE calibration rather than a load that was too heavy. A prescription of three reps at RPE 8 logged as three reps at RPE 6.5 produces `COMPLETED_BELOW_TARGET_RPE`, which is the signal to advance load next week.
