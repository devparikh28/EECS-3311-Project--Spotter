# LiftPilot — EECS 3311 Course Project

An AI agent that plans and adapts powerlifting training and nutrition.

**Course:** EECS 3311 Software Design, York University, Fall 2026
**Author:** Dev Parikh

LiftPilot imports a lifter's training history and recovery data, computes strength metrics deterministically, and uses an LLM agent to plan training blocks, adapt sessions to recovery, substitute unavailable exercises, select meet attempts, and answer coaching questions. Deterministic components do all arithmetic and validation; the agent handles judgement, and no model output reaches the domain model without passing a parser and a validator.

## Status

| Stage | State |
|---|---|
| Stage 1 — Design | In progress |
| Stage 2 — Implementation | Not started |
| Stage 3 — Testing | Not started |

## Stage 1 Report

The report is written as sections under `docs/stage1/`, numbered to match the Stage 1 deliverables list.

| Section | Document | State |
|---|---|---|
| 1. Project Overview | `docs/stage1/01-overview.md` | To do |
| 2. Feature Specifications | `docs/stage1/02-features.md` | Done |
| 3. UML Class Diagram | `docs/stage1/03-class-diagram-and-patterns.md` | Done |
| 4. Design Patterns | `docs/stage1/03-class-diagram-and-patterns.md` | Done |
| 5. Use Case Diagram | `docs/stage1/05-use-cases.md` | Done |
| 6. Use Case Descriptions | `docs/stage1/05-use-cases.md` | Done |
| 7. Sequence Diagrams | `docs/stage1/07-sequence-diagrams.md` | Done |
| 8. Traceability Table | `docs/stage1/08-traceability.md` | To do |
| 9. Feature Realization | `docs/stage1/09-feature-realization.md` | To do |

## Diagrams

All diagrams are written as PlantUML source and rendered to PNG and SVG. The class diagram uses one shared model (`diagrams/class/model.iuml`) rendered into five views, so the views cannot disagree with each other.

```
diagrams/
  class/      model.iuml, five views (full, presentation and application, domain, agent, infrastructure)
  usecase/    usecase.puml
  sequence/   SD01 to SD09
```

To regenerate after editing a source file:

```
java -jar plantuml.jar -tpng diagrams/**/*.puml
java -jar plantuml.jar -tsvg diagrams/**/*.puml
```

## Planned Technology (Stage 2)

| Concern | Choice |
|---|---|
| Language | Java 21 |
| Build | Maven |
| GUI | JavaFX, tabbed dashboard |
| CLI | picocli, command name `liftpilot` |
| Storage | SQLite through JDBC, behind a `Repository` interface |
| LLM | Anthropic Claude through LangChain4j (`langchain4j-anthropic`), wrapped in `ClaudeClient` |
| Data sources | Hevy and Whoop CSV exports behind a `DataSource` interface |
| Unit testing | JUnit 5 and Mockito |
| Agent behaviour testing | KUMA, driven through the CLI by a small Python harness |

## Architecture

Five layers, each depending only on the layer beneath it or on an interface.

```
Presentation    DashboardGUI (7 panels), CoachCLI
Application     CoachController (Facade), EventBus, PlanEditHistory
Domain          entities, StrengthAnalytics, AttemptCalculator, MacroTracker, RecoveryRuleEngine
Agent           CoachAgent (Template Method) and 6 agents, ClaudeClient, ToolManager, 5 tools, MemoryManager
Infrastructure  CSV adapters, catalogs, SQLite repositories
```

Design patterns applied: Facade, Strategy, Observer, Command, Adapter, Factory Method, Template Method. Builder and Repository appear as supporting patterns.
