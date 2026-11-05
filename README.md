# Spotter — EECS 3311 Course Project

An AI agent that plans and adapts strength training and nutrition.

**Course:** EECS 3311 Software Design, York University, Fall 2026
**Author:** Dev Parikh, 219957794

Spotter imports a lifter's training history and recovery data, computes strength metrics deterministically, and uses an LLM agent to plan training blocks, adapt sessions to recovery, substitute unavailable exercises, plan a heavy single, and answer coaching questions. It serves any strength trainee: the profile carries a training goal (meet prep, strength, hypertrophy, or general fitness) and a meet date is optional.

The principle the whole design rests on is that the model proposes and deterministic code disposes. The arithmetic and the validation are ordinary Java. The agent supplies judgement, and no model output reaches the domain model without passing a parser and a validator.

## Status

| Stage | State |
|---|---|
| Stage 1, design | Complete, submitted |
| Stage 2, implementation | Code complete. All fourteen features and all eight design patterns implemented. 500 tests passing |
| Stage 3, testing | Not started. The `--json` output the harness drives is in place |

## Quick start

You need Java 21 and Maven. Nothing else, and no API key for most of it.

```
git clone https://github.com/devparikh28/EECS-3311-Project--Spotter.git
cd EECS-3311-Project--Spotter
mvn test
```

That runs five hundred tests with no network access and no API key, because the whole test suite uses `ScriptedLLMClient` rather than the real model.

To use the application:

```
mvn -q package
java -jar target/spotter.jar seed
java -jar target/spotter.jar status
java -jar target/spotter.jar analytics squat
```

`seed` loads a sample lifter with training history, recovery readings and a food catalogue, so every screen and command has something to show. For the import paths there are three small synthetic files in `sample-data/`, with the commands that exercise them in `sample-data/README.md`:

```
java -jar target/spotter.jar import sample-data/hevy-export-sample.csv
java -jar target/spotter.jar import sample-data/whoop-export-sample.csv
``` `analytics`, `attempts`, `review` and `recovery` work with no API key at all, because nothing in them involves a model.

To start the dashboard:

```
mvn javafx:run
```

## Configuring the model

The AI features need an Anthropic API key. Without one, every command that calls a model says so and exits rather than failing obscurely, and `status` reports whether a key is configured.

```
cp .env.example .env
```

Then open `.env` and set `LLM_API_KEY` to your own key. `.env` is gitignored and must stay that way. `.env.example` carries placeholders only and is the file that is committed.

Never put a key in a source file, a report, a screenshot or a commit. A key that reaches any of those is disclosed and has to be revoked.

The other values in `.env.example` have working defaults and are documented in place, including why the two model identifiers are pinned to the versions they are.

With a key configured:

```
java -jar target/spotter.jar plan --weeks 4 --days 4 --trace
java -jar target/spotter.jar substitute "Back Squat" --load 140
java -jar target/spotter.jar meal --calories 900
```

`--trace` prints every step the agent took, every tool it called, every proposal that was rejected and the reason, and what the run cost. It is the fastest way to see the architecture working.

Every command that calls a model also takes `--demo`, which uses a prepared reply instead of the API. That is how the features can be demonstrated without spending anything.

## The command line

| Command | What it does | Model |
|---|---|---|
| `status` | Configuration, database and whether a key is set | no |
| `seed` | Load a sample lifter so nothing is empty | no |
| `import` | Read a Hevy, Whoop or arbitrary CSV export | no |
| `log` | Log a session by hand, as prescribed or typed out | no |
| `analytics` | Estimated maxima, trends and an RPE chart | no |
| `recovery` | Recovery readings and what was flagged | no |
| `checkin` | Report soreness, energy and sleep for a day | no |
| `attempts` | Meet attempts, and `--explain` for the reasoning | `--explain` only |
| `testday` | A heavy single and its warmups | no |
| `review` | Plan adherence, and `--revise` to rewrite the rest | `--revise` only |
| `plan` | Generate a training block | yes |
| `substitute` | Find a replacement movement | yes |
| `adjust` | Ease off today's session, or `--fallback` for the rule engine's own version | `--fallback` is deterministic |
| `meal` | Suggest a meal that fits the macros left | yes |
| `adopt` | Adopt a programme the lifter already follows | prose only; typed and spreadsheet routes are deterministic |

Nine of the fifteen commands never call a model. That is the point rather than a limitation: the figures a lifter trains off are calculated, not generated, and they still work when the network does not.

Every command that answers a question also takes `--json`, which prints one object instead of prose for the Stage 3 harness. The shape is documented in `docs/stage2/feature-to-implementation.md`.

## Stage 2 deliverables

| Deliverable | Where |
|---|---|
| Complete source code | `src/`, 173 main and 44 test files |
| README and setup instructions | this file |
| AI tools used | [`docs/stage2/ai-tools.md`](docs/stage2/ai-tools.md) |
| LLM models used | [`docs/stage2/ai-tools.md`](docs/stage2/ai-tools.md), section 2 |
| AI human collaboration log | [`docs/stage2/ai-collaboration-log.md`](docs/stage2/ai-collaboration-log.md) |
| Feature to implementation mapping | [`docs/stage2/feature-to-implementation.md`](docs/stage2/feature-to-implementation.md) |
| Important design changes | [`docs/stage2/design-changes.md`](docs/stage2/design-changes.md) |

## Stage 1 report

The whole Stage 1 report is one document: [`docs/Spotter-Stage1-Design-Report.md`](docs/Spotter-Stage1-Design-Report.md). It carries all nine deliverables in order: the overview and architecture, fourteen feature specifications, the class diagram in five views, eight design patterns with the SOLID analysis, the use case diagram, nineteen use case descriptions, twelve sequence diagrams, the traceability table, the feature realization by class and method, and four appendices.

Where the implementation departs from it, `docs/stage2/design-changes.md` says what changed and why, and `docs/stage2/feature-to-implementation.md` maps each feature from its designed classes to the classes that exist.

## Architecture

![Spotter system overview](docs/diagrams/png/architecture.png)

Five layers, each depending only on the layer beneath it or on an interface.

```
Presentation    DashboardApp and 8 panels, CoachCLI and 15 commands
Application     CoachController (Facade), EventBus, PlanEditHistory
Domain          entities, StrengthAnalytics, RPEChart, AttemptCalculator,
                MacroTracker, RecoveryRuleEngine, ProgressionStrategy
Agent           CoachAgent (Template Method) and 9 agents, ClaudeClient,
                ToolManager and 7 tools, PromptBuilder, ResponseParser,
                PlanValidator, MemoryManager
Infrastructure  CSV adapters, catalogs, SQLite repositories
```

Agent calls take seconds, so the panels run them on a background thread through `FxTaskRunner` and deliver results with `Platform.runLater()`, keeping the JavaFX Application Thread free. The CLI calls the same code and blocks, having no interface thread to protect.

## Source layout

```
pom.xml
.env.example                      configuration template, placeholders only
src/main/java/ca/yorku/eecs3311/spotter/
  presentation/
    cli/                          CoachCLI, 15 commands, JsonOutput
    gui/                          DashboardApp, Panel, GuiContext, FxTaskRunner
    gui/panels/                   8 panels and the shared card widgets
    support/                      ModelAccess, DemoReplies, UsageReport
  application/
    CoachController.java          the Facade every screen and command goes through
    event/                        EventBus, EventListener, SpotterEvent
    plan/                         PlanEditCommand, PlanEditHistory, 3 commands
  domain/                         entities and the deterministic calculations
  agent/
    CoachAgent.java               the Template Method; run() is final
    agents/                       9 agents
    tools/                        7 tools the agents may call
    ClaudeClient.java             the only class importing LangChain4j
    ScriptedLLMClient.java        what the tests use instead
    PlanValidator.java            the gate every proposal passes
  infrastructure/
    csv/                          DataSource adapters and import services
    persistence/                  Database, Repository<T>, SQLite repositories
    catalog/                      exercise and food catalogues
    config/                       SpotterConfig
src/main/resources/gui/           the dashboard stylesheet
src/test/java/                    JUnit 5 tests mirroring the main structure
docs/
  Spotter-Stage1-Design-Report.md the whole Stage 1 report
  stage2/                         the five Stage 2 documents
  diagrams/                       19 diagrams as PNG and UMLet .uxf
tools/kuma/                       Python harness for Stage 3 (not yet added)
```

## Design patterns

All eight patterns named in Stage 1 are implemented. `docs/stage2/feature-to-implementation.md` carries the full table with the designed classes beside the implemented ones.

| Pattern | Where |
|---|---|
| Facade | `application/CoachController` |
| Strategy | `agent/LLMClient` with three implementations; `domain/ProgressionStrategy` with four |
| Template Method | `agent/CoachAgent`, whose `run()` is final, and nine concrete agents |
| Adapter | `infrastructure/csv/DataSource` with the Hevy, Whoop and generic CSV adapters |
| Factory Method | `DataSourceFactory`, `ProgressionStrategyFactory`, `CoachAgent.createToolset()` |
| Decorator | `LLMClientDecorator`, `CachingLLMClient`, `RecordingLLMClient` |
| Observer | `application/event/EventBus` with `Panel.changedBy()` |
| Command | `application/plan/PlanEditCommand`, `PlanEditHistory` and three commands |

Builder and Repository appear as supporting patterns, in `PromptBuilder`, `LifterProfile.Builder`, `RecoveryDay.Builder`, and in `Repository<T>` with five implementations.

Two of these were built after the rest of the application worked, and both are tied to behaviour rather than added as structure. Command exists because the Today screen needs an Undo button. Observer exists because panels were refreshing on every visit and now refresh when something they display has changed. The reasoning for both is in `docs/stage2/design-changes.md`, entries 14 and 15.

## Diagrams

Every diagram is delivered in two formats, and they are the same picture: a UMLet `.uxf` file and the PNG UMLet exports from it. Each pair shares a name. There are nineteen: the system overview, five class views, the use case diagram and twelve sequence diagrams.

The class diagram is one model, `docs/diagrams/sources/model.iuml`, filtered into five views, so the views cannot disagree with each other. UMLet has no PlantUML import, so the `.uxf` files are generated rather than redrawn. See [`docs/diagrams/README.md`](docs/diagrams/README.md) for the regeneration commands, and note the warning there: running a generator overwrites hand edits without asking.

The class diagram describes the Stage 1 design and has not yet been regenerated against the implemented code. Until it is, `docs/stage2/feature-to-implementation.md` is the accurate description of what exists.

## Technology

| Concern | Choice |
|---|---|
| Language | Java 21 |
| Build | Maven |
| GUI | JavaFX 21 |
| CLI | picocli, command name `spotter` |
| Storage | SQLite through `sqlite-jdbc`, behind a `Repository<T>` interface |
| LLM | Anthropic Claude through LangChain4j, wrapped in `ClaudeClient` |
| JSON | Jackson, for parsing model output and for `--json` |
| Data sources | CSV from any workout or recovery application behind a `DataSource` interface, plus direct logging and daily check ins |
| Unit testing | JUnit 5, Mockito, AssertJ |
| Agent behaviour testing | KUMA (Python SDK) driving the Java CLI through a small harness |

## Why a small amount of Python

The application is written entirely in Java. Python appears only in the Stage 3 test harness, where it cannot reasonably be replaced, which the course instructor confirmed is acceptable: use Java everywhere it is possible, and Python only where it is not.

KUMA is a Python SDK and its protocol is a loop the caller owns:

```python
while (test_input := run.get_input()) is not None:
    report = run.submit(execute_agent(test_input), logs=["trace.jsonl"])
```

`execute_agent` invokes the Java CLI as a subprocess and returns its output. The `--json` output added in Stage 2 is what it reads. No application logic lives in Python: the harness only starts the Java process, passes the test input, and hands the result and trace back to KUMA.

## Roadmap beyond the course

The course deliverable is the fourteen features in the report. The design keeps four extensions open on purpose, documented in section 1.8 of the Stage 1 report: live Hevy and Whoop sync behind the existing `DataSource` interface, a REST layer over `CoachController` for a web or mobile client, and stricter safety limits in `PlanValidator` before anyone other than the author uses it.

One limitation is known and recorded rather than fixed. A competition lift variation such as a close grip bench press is mapped to the competition lift, so the validator holds it to competition bench loads and refuses loads that are correct for the variation. The fix is to map only the competition movement itself, which touches how the catalogue marks competition lifts and therefore the estimated maxima as well. `docs/stage2/design-changes.md` entry 13 has the detail.
