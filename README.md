# Spotter: An AI Strength and Nutrition Coaching Agent

EECS 3311 Software Design, Fall 2026 course project, York University.

Spotter imports a lifter's training history and recovery data, computes strength metrics deterministically, and uses an LLM agent to plan training blocks, adapt sessions to recovery, substitute unavailable exercises, plan meet attempts, suggest meals against remaining macros, and answer coaching questions. It has a JavaFX dashboard with eight screens, a floating Ask coach drawer, light and dark themes, a guided tour and PDF and CSV report export, and a picocli command line with the same features.

The principle the design rests on is that the model proposes and deterministic code disposes. Every figure a lifter trains off is calculated in ordinary Java. No model output reaches the domain model without passing a parser and a validator.

## Project stages

| Stage | Description | Status |
|---|---|---|
| Stage 1 | Design report: [docs/Spotter_Stage1_Report.md](docs/Spotter_Stage1_Report.md) | Complete, submitted |
| Stage 2 | Implementation: [docs/Spotter_Stage2_Report.md](docs/Spotter_Stage2_Report.md) ([PDF](docs/Spotter_Stage2_Report.pdf)) | Complete. 14 of 14 features, 8 of 8 patterns, 510 tests |
| Stage 3 | Testing (KUMA agent testing) | Not started. The `--json` output the harness drives is in place |

**Student:** Dev Parikh, 219957794

## Stage 2: build, run and demo

The full write up is the [Stage 2 report](docs/Spotter_Stage2_Report.md). It maps every feature to its classes and methods, shows the updated class diagrams, records the design changes and their reasons, and indexes the demonstration videos. Supporting documents are listed under Project documents below.

## Prerequisites

- JDK 21
- Maven 3.9 or newer
- An Anthropic API key, optional. Without one everything deterministic still works.

More detail is in [SETUP.md](SETUP.md).

## Install dependencies and build

```
mvn -q compile
mvn test
```

The 510 tests use a scripted model client, so they need no network and no key.

## Local configuration (API key)

1. Copy the template: `cp .env.example .env`
2. Open `.env` and replace `YOUR_LOCAL_API_KEY_HERE` with your own key.
3. The two models are set by `LLM_MODEL` (Haiku, for choosing and short checked answers) and `LLM_MODEL_REASONING` (Sonnet, for writing whole blocks). Cost rates are `LLM_INPUT_PER_MILLION` and `LLM_OUTPUT_PER_MILLION`.

`.env` is git ignored and must stay that way. The database defaults to `spotter.db` and can be moved with `SPOTTER_DB_PATH`.

## Launch

Graphical dashboard:

```
mvn javafx:run
```

Command line:

```
mvn -q package
java -jar target/spotter.jar status
```

Guided tour of every screen, started automatically:

```
SPOTTER_TOUR=1 mvn javafx:run
```

## Safe sample data (try it without any real data)

```
java -jar target/spotter.jar seed
```

`seed` loads a sample lifter with twenty sessions, recovery readings, foods and the standard exercises. The `sample-data/` folder has three small synthetic files for the import paths, with the commands that use them in `sample-data/README.md`: `hevy-export-sample.csv`, `whoop-export-sample.csv` and `programme-sample.csv`.

## Example usage (CLI)

```
spotter status
spotter seed
spotter import sample-data/hevy-export-sample.csv
spotter import sample-data/whoop-export-sample.csv
spotter analytics squat
spotter recovery --weeks 4
spotter checkin --soreness 3 --energy 4 --sleep 7
spotter attempts squat --explain
spotter testday squat
spotter plan --weeks 4 --days 4 --trace
spotter substitute "Back Squat" --load 140
spotter adjust --fallback
spotter meal --calories 900
spotter review --revise
spotter adopt --keep --start 2026-11-09 --entry "1/1 Back Squat 5x5@120"
```

Add `--trace` to any agent command to see every tool call, every refused proposal and the reason, and the tokens and cost of the run. Add `--demo` to use a prepared reply instead of the API, and `--json` for machine readable output.

Nine of the fifteen commands never call a model.

## Features and where they live

| ID | Feature | Main classes |
|---|---|---|
| F01 | Lifter Profile and Constraints | `LifterProfile`, `ProfilePanel`, `ProfileRepository` |
| F02 | Workout Logging and Import | `DataSource`, `HevyCsvAdapter`, `GenericCsvAdapter`, `CsvImportService`, `ImportPanel` |
| F03 | Recovery Input | `WhoopCsvAdapter`, `RecoveryDay`, `RecoveryRuleEngine`, `CheckInCommand` |
| F04 | Strength Analytics | `StrengthAnalytics`, `RPEChart`, `AnalyticsPanel` |
| F05 | Training Block Generation | `BlockGenerationAgent`, `CoachAgent`, `PlanValidator`, `ProgressionStrategy` |
| F06 | Equipment Substitution | `SubstitutionAgent`, `Substitution`, `SwapExerciseCommand` |
| F07 | Recovery Aware Session Adjustment | `AdjustmentAgent`, `RecoveryRuleEngine`, `ApplyAdjustmentCommand` |
| F08 | Max Testing and Attempt Planning | `AttemptCalculator`, `AttemptRationaleAgent`, `TestDayPlan` |
| F09 | Macro Targets and Adherence Tracking | `MacroTracker`, `MacroTarget`, `NutritionPanel` |
| F10 | Meal Suggestion | `MealSuggestionAgent`, `FoodCatalog`, `FoodDBTool` |
| F11 | Coaching Chat with Memory | `ChatAgent`, `MemoryManager`, `ConversationHistory`, `CoachPanel` |
| F12 | Plan Adherence and Progression Review | `PlanAdherence`, `ProgressionAgent`, `ApplyProgressionCommand` |
| F13 | Guided Onboarding | `OnboardingAgent`, `ProfileDraft`, `ProfileValidator` |
| F14 | Adopt an Existing Programme | `AdoptCommand`, `ProgrammeParserAgent`, `ProgrammeImportService` |

Patterns: Facade (`CoachController`), Strategy (`LLMClient`, `ProgressionStrategy`), Template Method (`CoachAgent`), Adapter (`DataSource`), Factory Method (`DataSourceFactory`, `ProgressionStrategyFactory`), Decorator (`CachingLLMClient`, `RecordingLLMClient`), Observer (`EventBus`), Command (`PlanEditCommand`), with Builder and Repository as supporting patterns.

Layers: `presentation` (CLI and JavaFX), `application` (Facade, event bus, plan commands), `domain`, `agent` and `infrastructure`, under `src/main/java/ca/yorku/eecs3311/spotter`.

## How the AI is used (and kept honest)

- Every agent proposal is parsed by `ResponseParser` and checked by `PlanValidator`. A refused proposal goes back to the model with the reason, and nothing unchecked is saved.
- `--trace` shows each step the agent took, each tool it called, each rejection, and the tokens and cost at the end.
- Only `ClaudeClient` imports LangChain4j. Everything else depends on the `LLMClient` interface, which is how the tests run without a key.

## Project documents

- [Stage 1 design report](docs/Spotter_Stage1_Report.md)
- [Stage 2 implementation report](docs/Spotter_Stage2_Report.md) and [PDF](docs/Spotter_Stage2_Report.pdf)
- [Feature to implementation mapping](docs/stage2/feature-to-implementation.md)
- [Design changes](docs/stage2/design-changes.md)
- [AI tools used](docs/stage2/ai-tools.md)
- [AI human collaboration log](docs/stage2/ai-collaboration-log.md)
- [Diagrams, as PNG and UMLet](docs/diagrams/README.md)

## Demonstration videos

Three recordings cover every feature, indexed in section 7 of the Stage 2 report. Recording A is the deterministic command line ([watch](https://youtu.be/IFqLJAf8m-g)), Recording B is the agent from the command line ([watch](https://youtu.be/R0Z8aMzREhE)), and Recording C is the captioned GUI tour ([watch](https://youtu.be/QMOtI4h0Y5E)). Each link below jumps to that feature's segment.

| ID | Feature | Demo |
|---|---|---|
| F01 | Lifter Profile and Constraints | [GUI tour](https://youtu.be/QMOtI4h0Y5E?t=125) |
| F02 | Workout Logging and Import | [CLI, deterministic](https://youtu.be/IFqLJAf8m-g?t=13) |
| F03 | Recovery Input | [CLI, deterministic](https://youtu.be/IFqLJAf8m-g?t=106) |
| F04 | Strength Analytics | [CLI, deterministic](https://youtu.be/IFqLJAf8m-g?t=161), [GUI tour](https://youtu.be/QMOtI4h0Y5E?t=50) |
| F05 | Training Block Generation | [CLI, agent](https://youtu.be/R0Z8aMzREhE?t=19), [GUI tour](https://youtu.be/QMOtI4h0Y5E?t=38) |
| F06 | Equipment Substitution | [CLI, agent](https://youtu.be/R0Z8aMzREhE?t=166), [GUI tour](https://youtu.be/QMOtI4h0Y5E?t=25) |
| F07 | Recovery Aware Session Adjustment | [CLI, agent](https://youtu.be/R0Z8aMzREhE?t=206), [GUI tour](https://youtu.be/QMOtI4h0Y5E?t=25) |
| F08 | Max Testing and Attempt Planning | [CLI, deterministic](https://youtu.be/IFqLJAf8m-g?t=181), [CLI, agent](https://youtu.be/R0Z8aMzREhE?t=271) |
| F09 | Macro Targets and Adherence Tracking | [GUI tour](https://youtu.be/QMOtI4h0Y5E?t=71) |
| F10 | Meal Suggestion | [CLI, agent](https://youtu.be/R0Z8aMzREhE?t=294), [GUI tour](https://youtu.be/QMOtI4h0Y5E?t=71) |
| F11 | Coaching Chat with Memory | [GUI tour](https://youtu.be/QMOtI4h0Y5E?t=82) |
| F12 | Plan Adherence and Progression Review | [CLI, agent](https://youtu.be/R0Z8aMzREhE?t=319) |
| F13 | Guided Onboarding | [GUI tour](https://youtu.be/QMOtI4h0Y5E?t=125) |
| F14 | Adopt an Existing Programme | [CLI, agent](https://youtu.be/R0Z8aMzREhE?t=417) |

## Safety

No API keys or personal data are in this repository. `.env`, local databases and recorded model traffic are git ignored.
