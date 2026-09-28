# 9. How Each Feature Is Realized

Each feature is described by its use case, its sequence diagram, the classes involved with the responsibility of each, the important methods, and the execution path from the user's action to the result.

---

## F01 — Lifter Profile and Constraints

**Use case:** UC01 Manage Lifter Profile **Sequence diagram:** SD09

**Classes**

- `ProfilePanel` — collects bodyweight, training goal, optional meet date and weight class, training days, equipment and dietary flags.
- `CoachController` — validates and coordinates the save.
- `LifterProfile` — the root entity every other feature reads; owns `DietaryConstraints` and carries a `TrainingGoal` with an optional `meetDate`.
- `ProfileRepository` — persists it in SQLite.
- `EventBus` — announces the change so other panels refresh.

**Methods:** `ProfilePanel.onSaveClicked()`, `CoachController.saveProfile()`, `LifterProfile.hasMeetDate()`, `LifterProfile.weeksUntilMeet()`, `ProfileRepository.save()`, `EventBus.publish()`

**Execution.** The lifter fills the form and saves. `onSaveClicked()` builds a `LifterProfile` and passes it to `saveProfile()`, which checks the required fields, rejects a meet date in the past, and asks for one only when the goal is meet preparation. On success the profile is persisted and a `PROFILE_UPDATED` event is published, which the nutrition and plan panels observe. Because `meetDate` is optional, `weeksUntilMeet()` returns an `Optional<Integer>`, and every consumer, notably block generation and attempt planning, is written to handle its absence rather than assuming a date exists.

---

## F02 — Workout Logging and Import

**Use case:** UC02 Import Workout History, with UC03 for direct logging, UC15 for unknown names and UC17 for unrecognised files **Sequence diagram:** SD01

**Classes**

- `LogPanel` — file picker and manual entry form.
- `CoachController` — entry point for both paths.
- `DataSourceFactory` — identifies the file and creates the right adapter.
- `HevyCsvAdapter` — translates Hevy's columns into domain objects, using `CsvFileReader` for raw rows.
- `GenericCsvAdapter` and `ColumnMapping` — read any other app's export through a mapping the lifter confirmed once, converting date formats and pounds to kilograms.
- `ImportProfileRepository` — remembers each mapping by name, so a repeat import is one step.
- `CsvImportService` — resolves exercise names, removes duplicates, collects errors into an `ImportReport`.
- `ExerciseCatalog` — the known exercise list, including aliases learned from previous imports.
- `WorkoutSession` and `SetEntry` — the imported history.
- `WorkoutRepository` — persistence.

**Methods:** `LogPanel.onImportHevyClicked()`, `CoachController.importWorkouts()`, `CsvImportService.importFile()`, `DataSourceFactory.detect()` and `create()`, `HevyCsvAdapter.read()`, `ExerciseCatalog.byName()`, `closestMatches()` and `addAlias()`, `CsvImportService.dedupe()`, `WorkoutRepository.save()`

**Execution.** The lifter picks a file. The factory reads its header; a recognised one returns `HevyCsvAdapter`, and anything else returns a suggested `ColumnMapping` for the lifter to confirm, after which `GenericCsvAdapter` reads it. Either way the adapter produces an `ImportBatch` of sessions and per row errors, so nothing downstream knows or cares which app the data came from. A lifter who tracks nothing can instead click Log as Prescribed on a planned session, which records it as completed with only the deviations they enter. The service looks up each exercise name; an unrecognised name opens the UC15 dialog, where the lifter maps it, adds it, or skips it, and any mapping is stored as an alias so the next import resolves it silently. Duplicates are skipped by default, valid sessions are saved, `DATA_IMPORTED` is published, and the lifter sees a report of what was imported, skipped and rejected. Recovery import (F03) follows the identical path with `WhoopCsvAdapter`, which is precisely the point of the Adapter and Factory Method pair: a second source added no branching to the import service.

---

## F03 — Recovery Input

**Use case:** UC04 Record Recovery, with UC17 for unrecognised files **Sequence diagram:** SD01

**Classes**

- `LogPanel`, `CoachController`, `CsvImportService`, `DataSourceFactory` — as in F02.
- `WhoopCsvAdapter` — converts Whoop rows into `RecoveryDay` objects; `GenericCsvAdapter` does the same for any other wearable through a mapping.
- `RecoverySource` — records whether a day came from a wearable or from a check in, which decides how it is judged.
- `RecoveryRepository` — persistence, with lookup by date.
- `RecoveryRuleEngine` — evaluates upcoming sessions against thresholds immediately after import.
- `EventBus` — publishes `DATA_IMPORTED` and, where applicable, `RECOVERY_FLAGGED`.

**Methods:** `LogPanel.onImportWhoopClicked()`, `CoachController.importRecovery()`, `WhoopCsvAdapter.read()`, `RecoveryRepository.save()`, `RecoveryRuleEngine.evaluate()`

**Execution.** Recovery reaches the system three ways: a recognised export, any other export through a confirmed mapping, or a check in where the lifter gives sleep hours, soreness and energy. The last of these matters, because a lifter with no wearable would otherwise get a plan that ignores recovery entirely. After saving, the controller runs the rule engine over training days that now have recovery data behind them, and `RecoveryDay.hasObjectiveScore()` decides which thresholds apply: recovery score and sleep for a wearable, sleep with soreness and energy for a check in. Days below the thresholds produce a `RecoveryFlag`, and the plan panel shows a badge. A day missing fields is stored with those fields empty rather than rejected, and a missing day produces no flag rather than an assumed bad one, which keeps the absence of data from being read as evidence.

---

## F04 — Strength Analytics

**Use case:** UC05 View Strength Analytics **Sequence diagram:** SD02

**Classes**

- `AnalyticsPanel` — lift selector, chart and summary figures.
- `CoachController` — loads history and delegates.
- `WorkoutRepository` — supplies sessions for the selected lift.
- `StrengthAnalytics` — the calculations.
- `RPEChart` — the reps and RPE to percentage table.
- `AnalyticsSummary` and `Point` — the returned series, tonnage, best set and data quality warnings.

**Methods:** `AnalyticsPanel.onLiftSelected()`, `CoachController.getAnalytics()`, `WorkoutRepository.findByLift()`, `StrengthAnalytics.computeE1RM()`, `RPEChart.percentOf1RM()`, `StrengthAnalytics.e1rmSeries()`, `weeklyTonnage()`, `bestSet()`

**Execution.** For each recorded set, `computeE1RM()` converts weight, reps and RPE into an estimated one rep max through `RPEChart`, and the results are aggregated by week into the series that JavaFX plots. The loop also enforces data quality: a set with an RPE outside 6 to 10, or RPE 10 above a single, is excluded and reported as a warning instead of silently distorting the trend. An empty history returns `AnalyticsSummary.empty()` so the panel shows an empty state rather than an error. This class is the most heavily unit tested component in Stage 3, because every AI feature reads its output through `AnalyticsTool`.

---

## F05 — Training Block Generation

**Use case:** UC06 Generate Training Block, including UC14 Run Agent Task **Sequence diagram:** SD03

**Classes**

- `PlanPanel` — the Generate Block action and the rendered block.
- `CoachController` — selects the progression strategy and dispatches the agent on a background thread.
- `ProgressionStrategyFactory` and `ProgressionStrategy` — the load progression appropriate to the lifter's goal.
- `BlockGenerationAgent` — the concrete `CoachAgent` for this task.
- `ToolManager`, `AnalyticsTool`, `ExerciseDBTool` — supply current e1RMs and the exercises this gym supports.
- `PromptBuilder` — assembles instructions, context, output schema.
- `ClaudeClient` — the model call.
- `ResponseParser` — JSON into `TrainingBlock`, `Week`, `Session`, `ExercisePrescription`.
- `PlanValidator` — checks the result before it is accepted.
- `BlockRepository` — persistence.

**Methods:** `PlanPanel.onGenerateClicked()`, `CoachController.generateBlock()`, `ProgressionStrategyFactory.forGoal()`, `CoachAgent.run()`, `createToolset()`, `gatherContext()`, `buildPrompt()`, `ClaudeClient.complete()`, `ToolManager.execute()`, `ResponseParser.toBlock()`, `PlanValidator.validateBlock()`, `BlockRepository.save()`

**Execution.** The controller asks the factory for the progression strategy matching the training goal, then calls `run()` on the agent. The template method registers this agent's tools, gathers context, and builds a prompt carrying the lifter's goal, equipment, current e1RMs and a JSON schema. Claude replies asking for tools; `ToolManager` executes them and returns the results; Claude then returns the block as JSON. `ResponseParser` builds domain objects and `PlanValidator` checks loads against the progression strategy and confirms every exercise exists in the catalog. Only a valid block is saved, and `PLAN_UPDATED` refreshes the panel. An invalid block is retried once with the validation errors appended to the prompt; a second failure leaves any existing block untouched and reports the error. Because the call takes seconds, the controller runs it through `FxTaskRunner` and returns the result on the JavaFX thread.

---

## F06 — Equipment Substitution

**Use case:** UC08 Substitute Unavailable Exercise, with UC07 for applying the change **Sequence diagram:** SD04

**Classes**

- `PlanPanel` — the warning icon and Suggest Substitute action.
- `CoachController` — coordinates.
- `ExerciseDBTool` and `ExerciseCatalog` — filter candidates by movement pattern, muscle groups and available equipment.
- `SubstitutionAgent` — chooses among the candidates and explains the choice.
- `PlanValidator` — confirms the choice came from the candidate list.
- `PlanEditHistory`, `SwapExerciseCommand`, `TrainingBlock` — apply the accepted change reversibly.

**Methods:** `PlanPanel.onSubstituteClicked()`, `CoachController.suggestSubstitute()`, `ExerciseCatalog.candidates()`, `CoachAgent.run()`, `PlanValidator.validateSubstitution()`, `CoachController.applyEdit()`, `PlanEditHistory.execute()`, `SwapExerciseCommand.execute()` and `undo()`

**Execution.** Deterministic filtering happens first, so the model chooses from a list it did not invent, and the validator rejects anything outside that list. When the lifter accepts, the change is applied as a `SwapExerciseCommand` through `PlanEditHistory`, exactly like a manual edit, which is why an agent suggestion can be undone with the same Undo action. If no candidate exists the lifter is told to swap manually rather than being given a fabricated alternative.

---

## F07 — Recovery Aware Session Adjustment

**Use case:** UC09 Adjust Session for Poor Recovery **Sequence diagram:** SD05

**Classes**

- `RecoveryRuleEngine` and `RecoveryFlag` — deterministic flagging, and the fallback reduction.
- `PlanPanel` — badge and Adjust Session action.
- `CoachController` — coordinates.
- `AdjustmentAgent` — rewrites the session and explains why.
- `PlanValidator` — confirms the rewrite only reduces demand.
- `PlanEditHistory` and `ApplyAdjustmentCommand` — apply it reversibly.
- `EventBus` — badge and refresh events.

**Methods:** `RecoveryRuleEngine.evaluate()` and `fallbackAdjustment()`, `PlanPanel.onAdjustClicked()`, `CoachController.adjustSession()`, `CoachAgent.run()`, `PlanValidator.validateAdjustment()`, `CoachController.applyEdit()`

**Execution.** Flagging is deterministic and automatic; the agent runs only when the lifter asks for an adjustment, which keeps the model out of routine operation. The validator enforces direction: an adjustment may not raise a load or remove a competition lift, so the worst outcome of a poor model response is a session that was not lightened enough, never one made harder. If the agent or the API fails, `fallbackAdjustment()` offers a fixed reduction instead, so the feature degrades rather than disappearing. The lifter can reject the proposal, and an accepted one is undoable.

---

## F08 — Max Testing and Attempt Planning

**Use case:** UC10 Plan a Heavy Single **Sequence diagram:** SD06

**Classes**

- `MeetPanel` — the action and the displayed numbers.
- `CoachController` — coordinates.
- `StrengthAnalytics` — supplies current e1RMs.
- `AttemptCalculator` — produces the numbers, with plate rounding.
- `MeetAttempts` and `TestDayPlan` — the two output shapes.
- `AttemptRationaleAgent` — writes the explanation.
- `PlanValidator` — confirms the explanation matches the numbers.

**Methods:** `MeetPanel.onCalculateClicked()`, `CoachController.calculateAttempts()`, `StrengthAnalytics.currentE1RMs()`, `AttemptCalculator.calculateMeetAttempts()` or `projectTestDay()`, `roundToPlate()`, `CoachAgent.run()`, `PlanValidator.validateRationale()`

**Execution.** The numbers are computed before the agent is involved. With a meet date the calculator produces opener, second and third attempts at fixed percentages of e1RM; without one it produces a test day plan of warm up sets and a top single, so a lifter who never competes is still served. The agent only writes the rationale, and `validateRationale()` confirms every number quoted in that text matches the calculator's output. If it does not, or if the agent fails, the numbers are shown without a rationale. The model can therefore influence how the plan is explained but never what weight is on the bar.

---

## F09 — Macro Targets and Adherence Tracking

**Use case:** UC11 Track Macros, with targets set in UC01 **Sequence diagram:** SD09

**Classes**

- `NutritionPanel` — intake form and daily totals.
- `CoachController` — coordinates.
- `FoodCatalog` and `FoodItem` — the food reference data.
- `FoodEntry`, `DailyIntake`, `MacroTotals` — a logged amount, the day, and the arithmetic.
- `MacroTracker` and `MacroTarget` — remaining macros and weekly adherence.
- `IntakeRepository` — persistence.

**Methods:** `NutritionPanel.onLogIntakeClicked()`, `CoachController.logIntake()`, `FoodCatalog.byName()` and `add()`, `MacroTracker.recordIntake()`, `getRemaining()`, `weeklyAdherence()`

**Execution.** A logged food is looked up in the catalog; an unknown one prompts for macros per 100 g and is added, so the catalog grows with use. Implausible gram amounts are rejected. `getRemaining()` subtracts the day's totals from the target, and `weeklyAdherence()` averages only days that have entries, since counting an unlogged day as zero adherence would misrepresent the week. The remaining macros computed here are the input to F10.

---

## F10 — Vegetarian Meal Suggestion

**Use case:** UC12 Get Meal Suggestion **Sequence diagram:** SD07

**Classes**

- `NutritionPanel` — the Suggest a Meal action and the displayed breakdown.
- `CoachController` — coordinates.
- `MacroTracker` — supplies the macros remaining.
- `FoodDBTool`, `FoodCatalog`, `DietaryConstraints` — return only foods the lifter's constraints permit.
- `MealSuggestionAgent` — composes the meal.
- `ResponseParser` — builds `MealSuggestion`, reading nutrition values from the catalog.
- `PlanValidator` — final check on items and totals.

**Methods:** `NutritionPanel.onSuggestMealClicked()`, `CoachController.suggestMeal()`, `MacroTracker.getRemaining()`, `FoodCatalog.search()`, `DietaryConstraints.permits()`, `CoachAgent.run()`, `ResponseParser.toMeal()`, `PlanValidator.validateMeal()`

**Execution.** The dietary constraint is enforced in code before the model sees anything: `FoodDBTool` returns only permitted foods. The model chooses among them and proposes gram amounts, but every nutrition figure displayed comes from `FoodCatalog` through `ResponseParser.toMeal()`, so the breakdown cannot contain invented numbers. `validateMeal()` then confirms no item violates the constraints and the totals fit within the remaining macros plus a tolerance. When nothing fits, the agent explains the shortfall instead of inventing a meal. This feature is the clearest KUMA target in the project, because a vegetarian constraint is easy to state, easy to test adversarially, and unambiguous when violated.

---

## F11 — Coaching Chat with Memory

**Use case:** UC13 Chat with Coach **Sequence diagram:** SD08

**Classes**

- `ChatPanel` — the conversation view.
- `CoachController` — coordinates.
- `ChatAgent` — the only agent with long term memory.
- `MemoryManager` and `ConversationHistory` — recall, storage and summarising.
- `ToolManager` with `AnalyticsTool`, `PlanLookupTool` and `RecoveryLookupTool` — retrieval of the lifter's own data.
- `ClaudeClient` — the model call.

**Methods:** `ChatPanel.onSendClicked()`, `CoachController.chat()`, `CoachAgent.run()`, `MemoryManager.recall()`, `remember()`, `summarizeOlderTurns()`, `ToolManager.execute()`, `ClaudeClient.complete()`

**Execution.** Before the prompt is built, `recall()` retrieves relevant earlier turns, which is what lets "how did last week go" work across sessions. Claude then decides which tools to call, receives their results, and answers from them. The exchange is stored, and older turns are summarised once the history limit is reached so the context stays bounded. Three behaviours are designed rather than incidental: a question the tools cannot answer receives an honest statement that the data is unavailable, an ambiguous question receives a clarifying question, and a failing tool is reported rather than worked around. These are the behavioural requirements this feature contributes to Stage 3.

---

## F12 — Plan Adherence and Progression

**Use case:** UC16 Review Adherence and Progress the Plan **Sequence diagram:** SD10

**Classes**

- `PlanPanel` — Review Week and Progress Plan actions, and the comparison table.
- `CoachController` — loads the block and the logged sessions, then coordinates.
- `PlanAdherence` — the deterministic comparison; matches prescriptions to logged sets and assigns a verdict.
- `AdherenceReport`, `AdherenceEntry`, `AdherenceVerdict` — the result: completion rate, per exercise figures, and one of completed at target, completed below target RPE, completed above target RPE, missed reps or missed session.
- `AdherenceTool` — exposes that report to the agent.
- `ProgressionAgent` — proposes revised remaining weeks.
- `PlanValidator` — confirms the revision only advances load where the verdict allows, within a week over week cap.
- `PlanEditHistory` and `ApplyProgressionCommand` — apply it reversibly.

**Methods:** `PlanPanel.onReviewWeekClicked()`, `CoachController.reviewAdherence()`, `PlanAdherence.compare()`, `matchSession()`, `verdictFor()`, `AdherenceReport.hasShortfall()`, `CoachController.progressPlan()`, `CoachAgent.run()`, `PlanValidator.validateProgression()`, `CoachController.applyEdit()`

**Execution.** At the end of a week the lifter opens Review Week. The controller loads the active block and the sessions logged in that window, and `PlanAdherence.compare()` walks each prescription, finds the matching logged sets, and assigns a verdict. Three reps prescribed at RPE 8 but logged as two reps at RPE 7 becomes `MISSED_REPS` with actual RPE below target; the same prescription logged as three reps at RPE 6.5 becomes `COMPLETED_BELOW_TARGET_RPE`, which is the signal that load can advance. The report is shown regardless of what happens next, because it is deterministic and useful by itself.

If there is a shortfall, or the lifter asks, `ProgressionAgent` reads the report through `AdherenceTool` and proposes revisions to the remaining weeks with reasons. The validator is stricter here than elsewhere, because this path changes future training rather than describing it: loads may rise only where the verdict permits, increases are capped week over week, and competition lifts cannot be removed. An accepted revision goes through the same command mechanism as a manual edit, so it can be undone.

**Why this feature matters beyond the course.** Without it, Spotter plans and never learns. The estimated one rep max does update from logged sets, so a regenerated block reflects reality, but nothing notices a pattern of missed reps inside a block, and nothing adapts the weeks that remain. F12 is what makes the system behave like a coach rather than a plan generator, and it reuses classes that already exist rather than adding a new layer.

---

## 9.1 What This Design Is Meant to Demonstrate

Reading the eleven descriptions together, the same shape recurs: deterministic code establishes the facts and the constraints, the agent supplies judgement within them, and a validator decides whether that judgement is acceptable before it reaches the domain model. The lifter always keeps the final say, through acceptance, rejection and undo.

This is what makes the system testable in the two separate ways Stage 3 requires. The deterministic components have correct answers, so they take ordinary unit tests. The agent components have expected behaviours rather than expected outputs, which is what KUMA evaluates. `PlanValidator` is the seam between them, and it is deliberately a deterministic class: whether model output is acceptable is itself a decision with a correct answer, and it can be tested as such.
