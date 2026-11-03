# Stage 2: feature to implementation mapping

Dev Parikh, 219957794
EECS 3311 Software Design, Fall 2026
Project: Spotter

Stage 1 specified fourteen features, F01 to F14.
The Stage 2 plan prioritised F01 to F11 and named
F12, F13 and F14 as the three to drop if time
ran short. This maps each feature to the classes
that implement it and states plainly what is
done, what is partial and what is not built.

Counts at the time of writing: 149 classes in
`src/main/java`, 36 test classes, 410 tests
passing.

---

## Status summary

| ID | Feature | Status | Reachable from |
|---|---|---|---|
| F01 | Lifter Profile and Constraints | Done | GUI Profile screen |
| F02 | Workout Logging and Import | Partial | `spotter import` |
| F03 | Recovery Input | Partial | `spotter import`, GUI Recovery |
| F04 | Strength Analytics | Done | `spotter analytics`, GUI Analytics |
| F05 | Training Block Generation | Done | `spotter plan`, GUI Plan |
| F06 | Equipment Substitution | Done | `spotter substitute`, GUI Today |
| F07 | Recovery Aware Adjustment | Done | `spotter adjust`, GUI Today |
| F08 | Max Testing and Attempt Planning | Done | `spotter attempts` |
| F09 | Macro Targets and Adherence | Done | GUI Nutrition |
| F10 | Meal Suggestion | Done | `spotter meal`, GUI Nutrition |
| F11 | Coaching Chat with Memory | Done | GUI Coach |
| F12 | Plan Adherence and Progression | Partial | not yet reachable |
| F13 | Guided Onboarding | Done, differently | GUI Profile screen |
| F14 | Adopt an Existing Programme | Not built | — |

Ten features are complete as designed, two are
complete in substance but built differently from
the Stage 1 specification, one is half built and
one was dropped.

The two that fell short, F12 and F14, are the
ones the Stage 2 plan named in advance as the
first to go, so this is the order of sacrifice
the plan set rather than a rationalisation made
afterwards.

The two marked partial, F02 and F03, have their
import halves complete and their manual entry
halves missing. That is a deliberate narrowing
rather than an oversight: both features were
specified to accept typed input as well as a
file, and a lifter who already exports from Hevy
and Whoop never types a set in by hand. It is
recorded here rather than quietly rounded up to
done.

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
| **Status** | Partial |

Four parts of the Stage 1 specification are not
built, and all four concern input that is not a
Hevy file.

`GenericCsvAdapter` and `ColumnMapping` were to
let a lifter map the columns of an unrecognised
export, with the mapping saved as a named import
profile for next time. The `import_profile` table
exists in the schema and nothing writes to it.

Manual entry of a session, and Log as Prescribed,
which records a planned session as performed with
only the deviations typed in, are also absent.

The Hevy adapter handles the real exports this
project was built around. The rest would have
been a second import feature rather than a
completion of the first, and nothing else in the
system depends on them.

Reads a real 7318 row export, skipping 31 rows
and reporting why. Four wrong assumptions in the
Stage 1 design were corrected by meeting that
file, recorded in commit `f40b2ef`.

## F03 Recovery Import

| | |
|---|---|
| **Designed** | `WhoopCsvAdapter`, `RecoveryDay`, `RecoveryRepository` |
| **Implemented** | `infrastructure/csv/WhoopCsvAdapter`, `domain/RecoveryDay`, `domain/RecoverySource`, `infrastructure/persistence/RecoveryRepository`, `SqliteRecoveryRepository`, `presentation/cli/RecoveryCommand`, `presentation/gui/panels/RecoveryPanel` |
| **Status** | Partial |

Reads a real Whoop physiological cycles export,
216 rows, keeping the longest sleep cycle per
date.

`RecoverySource.MANUAL_CHECK_IN` exists in the
domain and nothing produces one. Stage 1
specified a check in for soreness and energy on
days with no wearable data, and the rule engine
already reads both fields; only the way in is
missing. This is the smallest gap in the project
and the easiest to close.

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

`TestDayPlan` and `projectTestDay` are built and
tested for the lifter with no meet date, and
reachable from `CoachController`, but no command
or screen calls them. Stage 1 specified
`spotter test plan`; it was not written.

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
| **Designed** | `PlanAdherence.compare()` assigning per prescription verdicts such as `COMPLETED_BELOW_TARGET_RPE` and `MISSED_REPS`, a `ProgressionAgent`, `AdherenceTool`, `PlanValidator.validateProgression()`, `ApplyProgressionCommand` |
| **Implemented** | `domain/PlanAdherence` with nine tests |
| **Status** | Partial |

The deterministic half is built and tested, but
simpler than designed. Stage 1 called for a
verdict per prescription; the implementation
compares planned against logged tonnage per
session, plus the heaviest load reached against
the heaviest prescribed per lift. That is enough
to separate a block that is not working from a
block that was not done, which is what the
progression decision turns on, and it needs no
judgement about what a near miss on one set
means.

Not built: `ProgressionAgent`, `AdherenceTool`,
`validateProgression` and the command that would
apply a revision. Nothing reaches `PlanAdherence`
from the CLI or the GUI.

## F13 Guided Onboarding

| | |
|---|---|
| **Designed** | `OnboardingAgent` asking at most eight questions one at a time and adapting to the answers, `ProfileDraft`, `PlanValidator.validateProfileDraft()`, `StartingStrength.needsCalibration()` |
| **Implemented** | `agent/agents/OnboardingAgent`, `domain/ProfileDraft`, `agent/ProfileValidator`, `presentation/support/PreparedProfile`, `presentation/gui/panels/ProfilePanel` |
| **Status** | Done, differently |

Stage 1 designed an interview: up to eight
questions, asked one at a time, adapting to what
had been answered. The implementation reads a
single paragraph and reports what it understood
alongside what the lifter has not said.

The change was made because the interview is the
expensive version of the same thing. Eight
adaptive questions is eight model calls and eight
screens; a paragraph is one call and one screen,
and a lifter who leaves something out is asked
for exactly that, which is what the adaptive part
of the interview existed to achieve. The
unanswered questions are listed rather than
asked, so the flow is shorter without guessing
anything.

Two designed pieces are absent.
`validateProfileDraft` became `ProfileValidator`,
recorded in the design changes log. And
`StartingStrength.needsCalibration()`, which
would decide whether to ask for known maxes or
offer a calibration week, is not built, so a
lifter with no training history finishes
onboarding without a first step.

## F14 Adopt an Existing Programme

| | |
|---|---|
| **Designed** | Parsing an existing written programme and adopting it as a block |
| **Implemented** | Nothing |
| **Status** | Not built |

Named in the Stage 2 plan as the hardest item in
the design and the first to drop. It was dropped.
Twelve working features was judged better than
fourteen half built ones.

---

## Design patterns

Stage 1 named eight patterns and the course
requires five. Six are implemented, which meets
the requirement, and two are not.

| Pattern | Designed classes | Implemented | Status |
|---|---|---|---|
| Facade | `CoachController` | `application/CoachController` | Done |
| Strategy | `LLMClient` and clients; `ProgressionStrategy` and implementations | `agent/LLMClient`, `ClaudeClient`, `ScriptedLLMClient`; `domain/ProgressionStrategy`, `AbstractProgression`, `RPEProgression`, `LinearProgression`, `NoviceLinearProgression`, `PercentageProgression` | Done |
| Template Method | `CoachAgent.run()` | `agent/CoachAgent`, seven concrete agents | Done |
| Adapter | `DataSource`, CSV adapters | `infrastructure/csv/DataSource`, `HevyCsvAdapter`, `WhoopCsvAdapter` | Done |
| Factory Method | `createToolset()`, `DataSourceFactory`, `ProgressionStrategyFactory` | All three present | Done |
| Decorator | `LLMClientDecorator`, `CachingLLMClient`, `RecordingLLMClient` | All three present | Done |
| Observer | `EventBus`, `EventListener`, `BasePanel` | Not built | Not built |
| Command | `PlanEditCommand`, `PlanEditHistory`, three concrete commands | Not built | Not built |

Two supporting patterns from Stage 1 are also
present: Builder in `PromptBuilder`,
`LifterProfile.Builder` and `RecoveryDay.Builder`,
and Repository in `Repository<T>` and the five
implementations.

**Why Observer was not built.** Stage 1 had
`BasePanel` subclasses listening on an `EventBus`
so that saving a profile refreshed every
dependent panel. The implementation has no
`BasePanel` either: `Panel` is an interface, and
the shell calls `refresh()` on a panel when it is
shown. For a single window showing one panel at a
time that produces the same result with far less
machinery, and nothing can go stale because
nothing is visible to go stale. An event bus
would earn its place the moment two panels are
visible at once.

**Why Command was not built.** Stage 1 put every
plan edit on an undo stack:
`SwapExerciseCommand` for a substitution,
`ApplyAdjustmentCommand` for a recovery
reduction, `ApplyProgressionCommand` for a
revision. The implementation offers the change
and writes nothing until the lifter accepts it,
so the thing undo protects against, a plan
altered without consent, cannot happen. Undo
after acceptance would still be a convenience,
and it is the first thing worth adding. Building
the pattern without it would have been pattern
for its own sake, which is the failure mode the
lectures warn about.
