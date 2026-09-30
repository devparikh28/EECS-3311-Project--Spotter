# Spotter — EECS 3311 Course Project

An AI agent that plans and adapts strength training and nutrition.

**Course:** EECS 3311 Software Design, York University, Fall 2026
**Author:** Dev Parikh

Spotter imports a lifter's training history and recovery data, computes strength metrics deterministically, and uses an LLM agent to plan training blocks, adapt sessions to recovery, substitute unavailable exercises, plan a heavy single, and answer coaching questions. It serves any strength trainee: the profile carries a training goal (meet prep, strength, hypertrophy, or general fitness) and a meet date is optional.

Deterministic components do all arithmetic and validation. The agent supplies judgement, and no model output reaches the domain model without passing a parser and a validator.

## Status

| Stage | State |
|---|---|
| Stage 1 — Design | Complete |
| Stage 2 — Implementation | Not started |
| Stage 3 — Testing | Not started |

## Stage 1 Report

The whole Stage 1 report is one document: [`docs/stage1/Spotter-Stage1-Design-Report.md`](docs/stage1/Spotter-Stage1-Design-Report.md). It carries all nine deliverables in order.

| Section | Contents |
|---|---|
| 1 | Project overview, target users, why an agent, AI model and integration, architecture, technology |
| 2 | Fourteen feature specifications |
| 3 | UML class diagram, five views, relationships and multiplicities |
| 4 | Eight design patterns, the SOLID principles, and the unit testing approach |
| 5 | Use case diagram |
| 6 | Nineteen use case descriptions |
| 7 | Twelve sequence diagrams |
| 8 | Feature to design traceability table |
| 9 | Feature realization by class and method |

## Diagrams

Every diagram is delivered in two formats, and they are the same picture: a UMLet `.uxf` file and the PNG UMLet exports from it. Both sit side by side in `diagrams/`.

```
diagrams/
  <name>.uxf      the UMLet file, opens and edits in UMLet 15.1
  <name>.png      UMLet's own export of that .uxf
  sources/        the PlantUML the .uxf files are generated from (build input, not a deliverable)
tools/uxf/        the generators that turn the PlantUML model into .uxf
```

There are nineteen diagrams: the system overview, five class views, the use case diagram, and twelve sequence diagrams. The class diagram is one model, `diagrams/sources/model.iuml`, filtered into five views, so the views cannot disagree with each other.

UMLet has no PlantUML import, so the `.uxf` files are generated rather than redrawn: `tools/uxf/puml_to_uxf.py` parses `model.iuml`, lays the classes out with Graphviz, and writes UMLet XML; `make_usecase_uxf.py`, `make_architecture_uxf.py` and `make_sequence_uxf.py` produce the rest, the sequence diagrams as UMLet `UMLSequenceAllInOne` elements whose text stays editable. Every `.uxf` was opened and rendered with UMLet 15.1 to confirm it loads without errors, and those renders are the committed PNGs. See [`diagrams/README.md`](diagrams/README.md) for the regeneration commands.

## Technology (Stage 2)

| Concern | Choice |
|---|---|
| Language | Java 21 |
| Build | Maven |
| GUI | JavaFX 21, tabbed dashboard, built in LineChart and BarChart for analytics |
| CLI | picocli, command name `spotter` |
| Storage | SQLite through `sqlite-jdbc`, behind a `Repository<T>` interface |
| LLM | Anthropic Claude through LangChain4j (`langchain4j`, `langchain4j-anthropic`), wrapped in `ClaudeClient` |
| JSON | Jackson, for parsing model output into domain objects |
| Data sources | CSV from any workout or recovery app behind a `DataSource` interface, plus direct logging and daily check ins |
| Unit testing | JUnit 5, Mockito, AssertJ |
| Agent behaviour testing | KUMA (Python SDK) driving the Java CLI through a small harness |

## Planned Source Layout (Stage 2)

```
pom.xml
src/main/java/ca/yorku/eecs3311/spotter/
  presentation/     DashboardApp, BasePanel and 7 panels, CoachCLI commands
  application/      CoachController, EventBus, PlanEditHistory, FxTaskRunner, AgentRegistry
  domain/           entities, StrengthAnalytics, AttemptCalculator, MacroTracker,
                    RecoveryRuleEngine, ProgressionStrategy and implementations
  agent/            CoachAgent and 6 agents, LLMClient, ClaudeClient, ScriptedLLMClient,
                    ToolManager, 5 tools, PromptBuilder, ResponseParser, PlanValidator,
                    MemoryManager, ConversationHistory
  infrastructure/   DataSource adapters, CsvImportService, catalogs, SQLite repositories
src/main/resources/ FXML layouts, exercise and food catalog seed data
src/test/java/      JUnit 5 tests mirroring the package structure
tools/kuma/         Python harness for Stage 3 agent behaviour tests
```

## Why a Small Amount of Python

The application is written entirely in Java. Python appears only in the Stage 3 test harness, where it cannot reasonably be replaced, which the course instructor confirmed is acceptable: use Java everywhere it is possible, and Python only where it is not.

KUMA is a Python SDK and its protocol is a loop the caller owns:

```python
while (test_input := run.get_input()) is not None:
    report = run.submit(execute_agent(test_input), logs=["trace.jsonl"])
```

`execute_agent` invokes the Java CLI as a subprocess and returns its output. The CLI appends one JSON object per tool call, validation result, and retry to `trace.jsonl`, which KUMA ingests as evidence. No application logic lives in Python: the harness under `tools/kuma/` only starts the Java process, passes the test input, and hands the result and trace back to KUMA.

## Roadmap Beyond the Course

The course deliverable is the fourteen features in the report. The design keeps four extensions open on purpose, documented in section 1.8 of the report: live Hevy and Whoop sync behind the existing `DataSource` interface, a REST layer over `CoachController` for a web or mobile client, and stricter safety limits in `PlanValidator` before anyone other than the author uses it.

## Architecture

![Spotter system overview](diagrams/architecture.png)

Five layers, each depending only on the layer beneath it or on an interface.

```
Presentation    DashboardApp (7 panels), CoachCLI
Application     CoachController (Facade), EventBus, PlanEditHistory, FxTaskRunner
Domain          entities, StrengthAnalytics, AttemptCalculator, MacroTracker,
                RecoveryRuleEngine, ProgressionStrategy
Agent           CoachAgent (Template Method) and 6 agents, ClaudeClient, ToolManager,
                5 tools, MemoryManager
Infrastructure  CSV adapters, catalogs, SQLite repositories
```

Agent calls take seconds, so `CoachController` runs them on a background thread through `FxTaskRunner` and delivers results to the panels with `Platform.runLater()`, keeping the JavaFX Application Thread free. The CLI calls the same controller methods and blocks, having no UI thread to protect.

Design patterns applied: Facade, Strategy, Observer, Command, Adapter, Factory Method, Template Method and Decorator. Builder and Repository appear as supporting patterns.
