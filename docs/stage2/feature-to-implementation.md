# Stage 2: feature to implementation mapping

Dev Parikh, 219957794
EECS 3311 Software Design, Fall 2026
Project: Spotter

Stage 1 specified eleven features, F01 to F11.
Three more, F12 to F14, were added in the Stage 2
plan as optional extensions. This maps each one
to the classes that implement it and states
plainly what is done, what is partial and what is
not built.

Counts at the time of writing: 149 classes in
`src/main/java`, 36 test classes, 410 tests
passing.

---

## Status summary

| ID | Feature | Status | Reachable from |
|---|---|---|---|
| F01 | Lifter Profile and Constraints | Done | GUI Profile screen |
| F02 | Workout Import | Done | `spotter import` |
| F03 | Recovery Import | Done | `spotter import`, GUI Recovery |
| F04 | Strength Analytics | Done | `spotter analytics`, GUI Analytics |
| F05 | Training Block Generation | Done | `spotter plan`, GUI Plan |
| F06 | Equipment Substitution | Done | `spotter substitute`, GUI Today |
| F07 | Recovery Aware Adjustment | Done | `spotter adjust`, GUI Today |
| F08 | Max Testing and Attempt Planning | Done | `spotter attempts` |
| F09 | Macro Targets and Adherence | Done | GUI Nutrition |
| F10 | Meal Suggestion | Done | `spotter meal`, GUI Nutrition |
| F11 | Coaching Chat with Memory | Done | GUI Coach |
| F12 | Plan Adherence and Progression | Partial | not yet reachable |
| F13 | Guided Onboarding | Done | GUI Profile screen |
| F14 | Adopt an Existing Programme | Not built | — |

Eleven of the eleven specified features are
complete. Of the three extensions, one is
complete, one is half built and one was dropped,
which is the order of sacrifice the Stage 2 plan
set out in advance.

---

## F01 Lifter Profile and Constraints

| | |
|---|---|
| **Designed** | `LifterProfile`, `Limitation`, `DietaryConstraints`, `ProfileRepository` |
| **Implemented** | `domain/LifterProfile`, `domain/Limitation`, `domain/DietaryConstraints`, `domain/Equipment`, `domain/ExperienceLevel`, `domain/TrainingGoal`, `domain/SplitPreference`, `domain/WeightUnit`, `infrastructure/persistence/ProfileRepository`, `SqliteProfileRepository`, `presentation/gui/panels/ProfilePanel` |
| **Status** | Done |

Stored across four tables, because equipment and
limitations are lists and a limitation carries
lists of its own. Saving rewrites the children
rather than reconciling them. Tested in
`SqliteProfileRepositoryTest`, including the case
this shape of code invites, which is saving twice
and accumulating duplicate equipment.

## F02 Workout Import

| | |
|---|---|
| **Designed** | `DataSource`, `HevyCsvAdapter`, `GenericCsvAdapter`, `ColumnMapping`, `CsvImportService`, `DataSourceFactory` |
| **Implemented** | `infrastructure/csv/DataSource`, `HevyCsvAdapter`, `CsvFileReader`, `CsvImportService`, `DataSourceFactory`, `presentation/cli/ImportCommand` |
| **Status** | Done, less the generic adapter |

`GenericCsvAdapter` and `ColumnMapping` were
designed to let a lifter map the columns of an
unrecognised export. They are not built. The Hevy
adapter handles the real exports this project was
built around, and the mapping feature would have
been a second import feature rather than a
completion of the first.

Reads a real 7318 row export, skipping 31 rows
and reporting why. Four wrong assumptions in the
Stage 1 design were corrected by meeting that
file, recorded in commit `f40b2ef`.

## F03 Recovery Import

| | |
|---|---|
| **Designed** | `WhoopCsvAdapter`, `RecoveryDay`, `RecoveryRepository` |
| **Implemented** | `infrastructure/csv/WhoopCsvAdapter`, `domain/RecoveryDay`, `domain/RecoverySource`, `infrastructure/persistence/RecoveryRepository`, `SqliteRecoveryRepository`, `presentation/cli/RecoveryCommand`, `presentation/gui/panels/RecoveryPanel` |
| **Status** | Done |

Reads a real Whoop physiological cycles export,
216 rows, keeping the longest sleep cycle per
date.

## F04 Strength Analytics

| | |
|---|---|
| **Designed** | `StrengthAnalytics`, `RPEChart`, `AnalyticsSummary`, `Point` |
| **Implemented** | `domain/StrengthAnalytics`, `domain/RPEChart`, `domain/AnalyticsSummary`, `domain/Point`, `domain/Range`, `presentation/cli/AnalyticsCommand`, `presentation/gui/panels/AnalyticsPanel` |
| **Status** | Done |

The RPE chart is a lookup table rather than a
formula, and refuses combinations outside it
rather than extrapolating. 34 tests across
`RPEChartTest` and `StrengthAnalyticsTest`.

## F05 Training Block Generation

| | |
|---|---|
| **Designed** | `BlockGenerationAgent`, `CoachAgent`, `PromptBuilder`, `ResponseParser`, `PlanValidator`, `ProgressionStrategy` |
| **Implemented** | `agent/agents/BlockGenerationAgent`, `agent/CoachAgent`, `PromptBuilder`, `ResponseParser`, `PlanValidator`, `ToolManager`, `agent/tools/AnalyticsTool`, `ExerciseDBTool`, `LoadRangeTool`, `domain/ProgressionStrategy` and four implementations, `domain/TrainingBlock`, `Week`, `Session`, `ExercisePrescription`, `infrastructure/persistence/BlockRepository`, `SqliteBlockRepository`, `presentation/cli/PlanCommand`, `presentation/gui/panels/PlanPanel` |
| **Status** | Done |

Verified against a real model on 3 November: a
four week block for three lifts, first attempt
refused for four loads outside the allowed range,
corrected on the retry and accepted. Nine model
calls, 18443 tokens in, 12460 out.

`LoadRangeTool` was added during that session and
is described in the design changes log.

## F06 Equipment Substitution

| | |
|---|---|
| **Designed** | `SubstitutionAgent`, `Substitution`, `SubstitutionReason` |
| **Implemented** | `agent/agents/SubstitutionAgent`, `domain/Substitution`, `domain/SubstitutionReason`, `agent/tools/ExerciseDBTool`, `presentation/cli/SubstituteCommand`, substitution controls in `presentation/gui/panels/TodayPanel` |
| **Status** | Done |

Four checks stand between the answer and the
lifter: the movement exists, they can perform it,
it trains what the original trained, and the load
is between 40 and 110 percent of the original.
Thirteen tests, mostly refusals.

## F07 Recovery Aware Session Adjustment

| | |
|---|---|
| **Designed** | `RecoveryRuleEngine`, `AdjustmentAgent`, `RecoveryFlag` |
| **Implemented** | `domain/RecoveryRuleEngine`, `domain/RecoveryFlag`, `agent/agents/AdjustmentAgent`, `agent/tools/RecoveryLookupTool`, `presentation/cli/AdjustCommand`, adjustment controls in `presentation/gui/panels/TodayPanel` |
| **Status** | Done |

Hybrid as designed. The rule engine decides
whether to act, the agent decides where the
reduction falls, and `fallbackAdjustment` is the
blunt version used when the model cannot be
reached. The GUI shows both side by side, which
makes the difference visible rather than claimed.

## F08 Max Testing and Attempt Planning

| | |
|---|---|
| **Designed** | `AttemptCalculator`, `MeetAttempts`, `TestDayPlan` |
| **Implemented** | `domain/AttemptCalculator`, `domain/MeetAttempts`, `domain/TestDayPlan`, `agent/agents/AttemptRationaleAgent`, `PlanValidator.validateRationale`, `presentation/cli/AttemptsCommand` |
| **Status** | Done |

The arithmetic half needs no model. The
explanation half is gated so that every figure in
the words must be one the calculator produced,
which is the narrowest and clearest division in
the project.

## F09 Macro Targets and Adherence Tracking

| | |
|---|---|
| **Designed** | `MacroTracker`, `MacroTarget`, `DailyIntake`, `FoodEntry` |
| **Implemented** | `domain/MacroTracker`, `MacroTarget`, `MacroTotals`, `DailyIntake`, `FoodEntry`, `FoodItem`, `infrastructure/persistence/NutritionRepository`, `SqliteNutritionRepository`, `presentation/gui/panels/NutritionPanel` |
| **Status** | Done |

No CLI command. Logging food from a terminal is a
worse experience than the panel and would have
been built for completeness rather than use.

## F10 Meal Suggestion

| | |
|---|---|
| **Designed** | `MealSuggestionAgent`, `FoodCatalog` |
| **Implemented** | `agent/agents/MealSuggestionAgent`, `domain/MealSuggestion`, `infrastructure/catalog/FoodCatalog`, `SqliteFoodCatalog`, `infrastructure/sample/SampleFoods`, `agent/tools/FoodDBTool`, `PlanValidator.validateMeal`, `presentation/cli/MealCommand`, meal controls in `NutritionPanel` |
| **Status** | Done |

Nutrition is computed from the catalogue after
the model answers. `MealSuggestion` does not
store its totals, so a stated figure cannot drift
from the foods it describes.

## F11 Coaching Chat with Memory

| | |
|---|---|
| **Designed** | `ChatAgent`, `MemoryManager`, `ConversationHistory`, `MemoryRepository` |
| **Implemented** | `agent/agents/ChatAgent`, `agent/MemoryManager`, `agent/ConversationHistory`, `domain/MemoryNote`, `domain/ChatReply`, `infrastructure/persistence/MemoryRepository`, `SqliteMemoryRepository`, `agent/tools/PlanLookupTool` and the other three lookups, `presentation/gui/panels/CoachPanel` |
| **Status** | Done |

Eleven of the twelve pieces this feature needs
were built for other features, which is the
strongest evidence available that the
architecture was right.

## F12 Plan Adherence and Progression Review

| | |
|---|---|
| **Designed** | `PlanAdherence`, a progression review agent |
| **Implemented** | `domain/PlanAdherence` with nine tests |
| **Status** | Partial |

The deterministic half is complete and tested. It
distinguishes a block that is not working from a
block that was not done, which the analytics
alone cannot do. The review agent that reads
those figures is not built, and nothing reaches
`PlanAdherence` from the CLI or the GUI yet.

## F13 Guided Onboarding

| | |
|---|---|
| **Designed** | Added in the Stage 2 plan |
| **Implemented** | `agent/agents/OnboardingAgent`, `domain/ProfileDraft`, `agent/ProfileValidator`, `presentation/support/PreparedProfile`, `presentation/gui/panels/ProfilePanel` |
| **Status** | Done |

## F14 Adopt an Existing Programme

| | |
|---|---|
| **Designed** | Added in the Stage 2 plan |
| **Implemented** | Nothing |
| **Status** | Not built |

Named in the Stage 2 plan as the hardest item and
the first to drop. It was dropped. Thirteen
working features was judged better than fourteen
half built ones, which is the trade the plan set
out in advance rather than a rationalisation made
afterwards.

---

## Design patterns

Stage 1 named seven patterns and the course
requires five. Five are implemented, which meets
the requirement, and two are not.

| Pattern | Designed classes | Implemented | Status |
|---|---|---|---|
| Facade | `CoachController` | `application/CoachController` | Done |
| Strategy | `LLMClient` and clients; `ProgressionStrategy` and implementations | `agent/LLMClient`, `ClaudeClient`, `ScriptedLLMClient`; `domain/ProgressionStrategy`, `AbstractProgression`, `RPEProgression`, `LinearProgression`, `NoviceLinearProgression`, `PercentageProgression` | Done |
| Template Method | `CoachAgent.run()` | `agent/CoachAgent`, seven concrete agents | Done |
| Adapter | `DataSource`, CSV adapters | `infrastructure/csv/DataSource`, `HevyCsvAdapter`, `WhoopCsvAdapter` | Done |
| Factory Method | `createToolset()`, `DataSourceFactory`, `ProgressionStrategyFactory` | All three present | Done |
| Observer | `EventBus`, `EventListener`, panels | Not built | Not built |
| Command | `PlanEditCommand`, `PlanEditHistory` | Not built | Not built |

Two supporting patterns from Stage 1 are also
present: Builder in `PromptBuilder`,
`LifterProfile.Builder` and `RecoveryDay.Builder`,
and Repository in `Repository<T>` and the five
implementations. Decorator was added during
implementation and was not in the Stage 1 list:
`LLMClientDecorator`, `CachingLLMClient` and
`RecordingLLMClient`.

**Why Observer was not built.** Panels refresh
when they are opened rather than when data
changes. The shell calls `refresh()` on every
panel it shows, which achieves the same thing for
an application with one window and one user, at a
fraction of the machinery. An event bus would
earn its place if two panels were visible at
once, which none are.

**Why Command was not built.** It was designed
for undoing edits to a generated plan. No edit
feature was built: a plan is accepted or
regenerated, so there is nothing to undo. Adding
the pattern without the feature would have been
pattern for its own sake, which is the failure
mode the lectures warn about.
