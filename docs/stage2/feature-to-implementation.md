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
| F03 | Recovery Input | Done | `spotter import`, `spotter checkin`, GUI Recovery |
| F04 | Strength Analytics | Done | `spotter analytics`, GUI Analytics |
| F05 | Training Block Generation | Done | `spotter plan`, GUI Plan |
| F06 | Equipment Substitution | Done | `spotter substitute`, GUI Today |
| F07 | Recovery Aware Adjustment | Done | `spotter adjust`, GUI Today |
| F08 | Max Testing and Attempt Planning | Done | `spotter attempts` |
| F09 | Macro Targets and Adherence | Done | GUI Nutrition |
| F10 | Meal Suggestion | Done | `spotter meal`, GUI Nutrition |
| F11 | Coaching Chat with Memory | Done | GUI Coach |
| F12 | Plan Adherence and Progression | Done | `spotter review` |
| F13 | Guided Onboarding | Done, differently | GUI Profile screen |
| F14 | Adopt an Existing Programme | Partial | `spotter adopt` |

Eleven features are complete as designed, one is
complete in substance but built differently from
the Stage 1 specification, and two are partial.
Nothing was dropped entirely.

F12 and F14 are the two the Stage 2 plan named in
advance as the first to go. Both have a working
half: F12 has the adherence arithmetic without
the review agent, and F14 has the route that
needed a model without the two that did not.

F02 remains partial. Its import half is complete
and its manual entry half is missing, which is a
deliberate narrowing rather than an oversight: a
lifter who already exports from Hevy never types
a set in by hand. It is recorded here rather than
quietly rounded up to done.

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

`spotter checkin` records soreness, energy and
sleep for a day with no wearable data, as
`RecoverySource.MANUAL_CHECK_IN`, and prints the
rule engine's verdict immediately. Without it the
recovery feature was conditional on owning a
particular device: a lifter without a Whoop could
log months of training and never have a session
questioned.

Deliberately three numbers rather than a
questionnaire, because anything longer gets
skipped on exactly the mornings it matters.

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

`spotter testday` plans a heavy single with warm
ups for the lifter with no meet date, which is
most of them. The arithmetic had existed since
the attempt calculator landed and only the way in
was missing, so a feature built for everybody was
reachable only by someone preparing to compete.
Stage 1 called the command `spotter test plan`;
it is one word rather than two.

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
| **Implemented** | `domain/PlanAdherence`, `agent/tools/AdherenceTool`, `agent/agents/ProgressionAgent`, `PlanValidator.validateProgression`, `presentation/cli/ReviewCommand` |
| **Status** | Done |

The adherence arithmetic is simpler than
designed. Stage 1 called for a verdict per
prescription, such as `COMPLETED_BELOW_TARGET_RPE`
or `MISSED_REPS`; the implementation compares
planned against logged tonnage per session, plus
the heaviest load reached against the heaviest
prescribed per lift. That is enough to separate a
block that is not working from one that was not
done, which is what the progression decision
turns on, and it needs no judgement about what a
near miss on a single set means.

`validateProgression` is the tightest gate in the
project, because this is the only agent that
rewrites training the lifter is part way through.
A revision may not drop a session or a
competition lift, may not add load to a lift that
fell short of its plan, and may not raise
anything by more than five percent in a week. The
middle rule is the one that carries the feature:
a lift falling behind is rarely a sign that it
was too easy.

`spotter review` reports adherence with no model
involved, and `--revise` asks the coach to
rewrite the remaining weeks. The report stands on
its own when there is no API key, because it was
measured rather than generated.

Not implemented: `ApplyProgressionCommand`, since
there is no undo stack.

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

One designed piece changed name:
`validateProfileDraft` became
`ProfileValidator`, recorded in the design
changes log.

`StartingStrength` is built and shown on the
Profile screen once a profile is saved, so
onboarding ends with something to do rather than
with an empty application. It distinguishes a
lifter with nothing logged, who must supply
something before anything can be planned, from
one with a little history, who only has to keep
going. Telling the second group to type in their
maxima would be asking for numbers Spotter is
about to work out for itself.

## F14 Adopt an Existing Programme

| | |
|---|---|
| **Designed** | Three routes: `ProgrammeImportService.fromManualEntry()`, `fromCsv()` with a column mapping, and `ProgrammeParserAgent` for pasted text, all gated by `PlanValidator.validateAdoptedBlock()` |
| **Implemented** | `agent/agents/ProgrammeParserAgent`, `PlanValidator.validateAdoptedBlock`, `ResponseParser.toBlock` with an origin, `presentation/cli/AdoptCommand` |
| **Status** | Partial: the free text route only |

Stage 1 called this the hardest feature in the
design, and the Stage 2 plan named it the first
to drop. The free text route turned out to be the
cheap one, which is worth recording because the
reason is a design decision made two months
earlier.

`CoachAgent` supplies the retry. `ResponseParser`
already resolves exercise names through the
catalogue and already builds weeks and sessions
from JSON. `BlockRepository` already stores a
block and `BlockOrigin.ADOPTED` already existed.
What had to be written was a prompt, a gate and a
command. That saving is the whole argument for
Template Method, arriving two months after the
decision to use it.

The gate is deliberately not the one generated
blocks face. An adopted programme was written by
somebody else, so holding it to the lifter's
progression would reject every programme that
does not match the scheme Spotter would have
chosen, which is most of them. `validateAdoptedBlock`
checks possibility instead: the movements exist
and can be performed, the week is not busier than
the lifter said they train, and no load is one
nobody could lift. A load far above a current
maximum nearly always means the text was misread
rather than that the coach is bold.

Not built: the manual entry and CSV routes.
`fromCsv` would need `GenericCsvAdapter` and
`ColumnMapping` from F02, which are also unbuilt,
so that route is two missing features stacked
rather than one.

This is also the only feature with no prepared
reply for running without an API key. Reading an
arbitrary document is not something keyword
matching can fake, and the command says so
outright rather than pretending.

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
