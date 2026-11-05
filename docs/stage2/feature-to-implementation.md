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

Counts at the time of writing: 173 source files
in `src/main/java`, 44 test files, 500 tests
passing.

---

## Status summary

| ID | Feature | Status | Reachable from |
|---|---|---|---|
| F01 | Lifter Profile and Constraints | Done | GUI Profile screen |
| F02 | Workout Logging and Import | Done | `spotter import`, `spotter log` |
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
| F14 | Adopt an Existing Programme | Done | `spotter adopt` |

Thirteen features are complete as designed, and
one, F13, is complete in substance but built
differently from the Stage 1 specification.
Nothing is unbuilt.

F12, F13 and F14 are the three the Stage 2 plan
named in advance as the first to drop if time ran
short. All three were built.

The one departure from the Stage 1 design is
F13, where an eight question adaptive interview
became a single paragraph. The argument for that
change is set out under F13 rather than presented
as the plan all along.

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
| **Implemented** | `infrastructure/csv/DataSource`, `HevyCsvAdapter`, `GenericCsvAdapter`, `ColumnMapping`, `CsvFileReader`, `CsvImportService`, `DataSourceFactory`, `infrastructure/persistence/ImportProfileRepository`, `SqliteImportProfileRepository`, `presentation/cli/ImportCommand`, `LogCommand` |
| **Status** | Done |

Reads a real 7318 row Hevy export, skipping 31
rows and reporting why. Four wrong assumptions in
the Stage 1 design were corrected by meeting that
file, recorded in commit `f40b2ef`.

`GenericCsvAdapter` reads an export from an
application Spotter does not know, through a
mapping a person supplied. Stage 1 put that
conversation behind a dialog; on the command line
it is three options. Spotter suggests a mapping
from the header, `--set field=column` corrects
it, and `--save-as` means the question is never
asked again for that application. The suggestion
guesses the unit too, so an export in pounds is
converted without being told.

A mapping naming a column the file does not have
is refused rather than read, because the
alternative is importing nothing and reporting
success, which is the worst outcome available.

`spotter log` records training without an export,
either one movement typed in, with the closest
catalogue names offered when the spelling is
wrong, or `--as-prescribed`, which records
today's planned session as performed. The second
is the one that gets used: typing out a session
you have just completed exactly as it was written
down for you is the kind of work that makes
people stop logging.

## F03 Recovery Import

| | |
|---|---|
| **Designed** | `WhoopCsvAdapter`, `RecoveryDay`, `RecoveryRepository` |
| **Implemented** | `infrastructure/csv/WhoopCsvAdapter`, `domain/RecoveryDay`, `domain/RecoverySource`, `domain/RecoveryRuleEngine`, `domain/RecoveryFlag`, `infrastructure/persistence/RecoveryRepository`, `SqliteRecoveryRepository`, `presentation/cli/RecoveryCommand`, `CheckInCommand`, `presentation/gui/panels/RecoveryPanel` |
| **Status** | Done |

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
| **Implemented** | `agent/agents/SubstitutionAgent`, `domain/Substitution`, `domain/SubstitutionReason`, `agent/tools/ExerciseDBTool`, `presentation/cli/SubstituteCommand`, `application/plan/SwapExerciseCommand`, substitution controls in `presentation/gui/panels/TodayPanel` |
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
| **Implemented** | `domain/RecoveryRuleEngine`, `domain/RecoveryFlag`, `agent/agents/AdjustmentAgent`, `agent/tools/RecoveryLookupTool`, `presentation/cli/AdjustCommand`, `application/plan/ApplyAdjustmentCommand`, adjustment controls in `presentation/gui/panels/TodayPanel` |
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
| **Implemented** | `domain/PlanAdherence`, `agent/tools/AdherenceTool`, `agent/agents/ProgressionAgent`, `PlanValidator.validateProgression`, `application/plan/ApplyProgressionCommand`, `PlanEditHistory`, `presentation/cli/ReviewCommand` |
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

`ApplyProgressionCommand` is built. A revision
accepted with `--keep` goes through it rather than
being written directly, so the one place that
rebuilds a block with its original identity is
also the place that knows how to put the old one
back.

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
| **Implemented** | `infrastructure/csv/ProgrammeImportService`, `agent/agents/ProgrammeParserAgent`, `PlanValidator.validateAdoptedBlock`, `ResponseParser.toBlock` with an origin, `presentation/cli/AdoptCommand` |
| **Status** | Done |

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

The other two routes need no model at all, which
is the distinction that matters. A programme
given as a spreadsheet, or typed a line at a
time, already has a structure; nothing needs
interpreting, so nothing is sent anywhere.
Sending either to a model would be paying for
reading comprehension that is not required and
making a deterministic import depend on a network
call.

`fromManualEntry` reads lines such as
`1/2 Back Squat 5x5@140 rpe8`. Terse on purpose:
a lifter entering four weeks will type it forty
times, and every extra character is paid forty
times over.

`fromCsv` reuses `ColumnMapping` from F02 pointed
at different fields, because the problem is the
same one, a file whose columns nobody agreed on
and a person who can say what they mean.

All three routes pass through
`validateAdoptedBlock`, so a programme is held to
the same standard however it arrived.

This is also the only feature with no prepared
reply for running without an API key. Reading an
arbitrary document is not something keyword
matching can fake, and the command says so
outright rather than pretending.

---

## Design patterns

Stage 1 named eight patterns and the course
requires five. All eight are implemented.

| Pattern | Designed classes | Implemented | Status |
|---|---|---|---|
| Facade | `CoachController` | `application/CoachController` | Done |
| Strategy | `LLMClient` and clients; `ProgressionStrategy` and implementations | `agent/LLMClient`, `ClaudeClient`, `ScriptedLLMClient`; `domain/ProgressionStrategy`, `AbstractProgression`, `RPEProgression`, `LinearProgression`, `NoviceLinearProgression`, `PercentageProgression` | Done |
| Template Method | `CoachAgent.run()` | `agent/CoachAgent`, nine concrete agents | Done |
| Adapter | `DataSource`, CSV adapters | `infrastructure/csv/DataSource`, `HevyCsvAdapter`, `WhoopCsvAdapter` | Done |
| Factory Method | `createToolset()`, `DataSourceFactory`, `ProgressionStrategyFactory` | All three present | Done |
| Decorator | `LLMClientDecorator`, `CachingLLMClient`, `RecordingLLMClient` | All three present | Done |
| Observer | `EventBus`, `EventListener`, `BasePanel` | `application/event/EventBus`, `EventListener`, `SpotterEvent`, `Panel.changedBy()` | Done |
| Command | `PlanEditCommand`, `PlanEditHistory`, three concrete commands | `application/plan/PlanEditCommand`, `PlanEditHistory`, `ReplaceSessionCommand`, `SwapExerciseCommand`, `ApplyAdjustmentCommand`, `ApplyProgressionCommand` | Done |

Two supporting patterns from Stage 1 are also
present: Builder in `PromptBuilder`,
`LifterProfile.Builder` and `RecoveryDay.Builder`,
and Repository in `Repository<T>` and the five
implementations.

**Observer.** Stage 1 had `BasePanel`
subclasses listening on an `EventBus` so that
saving a profile refreshed every dependent panel.
The bus is built; the base class is not. `Panel`
stayed an interface with a default
`changedBy()` returning the kinds of change that
screen displays, which gives the same
subscription without making every screen inherit
from a shared parent in order to listen.

What the bus changed is the refresh rule. Before,
opening a panel refreshed it, every time, which
was correct and wasteful: walking the navigation
column re-queried the database for figures that
nothing had touched. Now a panel saving a plan
publishes `BLOCK_CHANGED` and does not refresh
itself; the shell refreshes the panel on screen at
once and marks the others stale, so they redraw
the next time they are opened and not before.

Three events exist, and only three, because those
are the changes the application makes to itself
while it is running: a plan saved, a profile
saved, food or a macro target recorded. Logging a
set and importing recovery readings change a
screen too, but both arrive from `spotter log`
and `spotter import` in a separate process, where
a bus in this window cannot hear them. A panel
that names no event is refreshed on every visit
as before, which is what Analytics does and why
its figures are never stale.

One rule in `EventBus` is worth naming: a
listener that throws does not stop the rest from
being told, and the failure is kept rather than
swallowed. A panel failing to redraw is a bad
panel; it should not also become the reason every
other screen quietly stops updating.

**Command.** Stage 1 put every plan edit on an
undo stack: `SwapExerciseCommand` for a
substitution, `ApplyAdjustmentCommand` for a
recovery reduction, `ApplyProgressionCommand` for
a revision. All three are built, on a shared
`ReplaceSessionCommand` base for the two that
replace one session, and the Today screen carries
the Undo button they exist for.

The case that justifies it is the one from the
Stage 1 walkthrough: a lifter eases off a session
at seven in the morning after a bad night, feels
fine by the evening, and should be able to put the
session back rather than regenerate it. Nothing
else in the application can do that, because
accepting a proposal overwrites what was
prescribed.

Two decisions are worth recording. A command
returns a new block rather than mutating the one
it was given, which makes undo a matter of keeping
the old value rather than of reversing arithmetic
correctly. And the history is bounded at twenty
edits, because an undo stack that grows all day is
a memory leak with a friendly name.

The stack lives in the window, not the database,
so it is a session's worth of undo rather than a
permanent record. On the command line that means
`spotter review --keep` uses the command for the
edit and the description it prints, but has no
stack to offer, since the process exits. Persisting
the history would be the next step if anyone wanted
undo across restarts, and nobody has asked for it.

## Machine readable output

Stage 3 drives the application from a harness
rather than from a terminal, so the eight commands
that answer a question take `--json` and print one
object instead of prose: `plan`, `substitute`,
`adjust`, `meal`, `attempts`, `review`, `adopt`
and `checkin`.

| | |
|---|---|
| **Implemented** | `presentation/cli/JsonOutput`, a `--json` option on the eight commands above, `presentation/cli/JsonOutputTest` |
| **Status** | Done |

Every command prints the same four keys:
`command`, `ok`, `value` and, for the commands
that call a model, `trace`. A refusal prints `ok`
false with a reason and keeps the exit code the
prose path used, so a harness can read either
signal. `JsonOutputTest` runs all eight through
the real command objects against a temporary
database, which means a stray print statement
anywhere in the prose path shows up as a parse
failure here rather than as a surprise in Stage 3.
