# 3. UML Class Diagram

The class diagram is maintained as PlantUML source in `diagrams/class/`. One shared model file (`model.iuml`) defines every class and relationship, and five view files render it so the full design and each layer can be read separately. Because every view is generated from the same source, the views can never disagree with each other.

| View | File | Contents |
|---|---|---|
| Complete | `class-full.svg` | Every class, interface, and relationship across all five layers |
| Presentation and Application | `class-presentation-application.png` | GUI panels, CLI, `CoachController` facade, event bus, plan edit commands |
| Domain | `class-domain.png` | Domain entities, domain services, progression strategies |
| Agent | `class-agent.png` | Agent template, agent subclasses, LLM client, tools, prompt and validation pipeline, memory |
| Infrastructure | `class-infrastructure.png` | CSV adapters, import service, catalogs, repositories, database |

## 3.1 Architecture Overview

The system is organised in five layers. Each layer depends only on the layer beneath it or on interfaces, never on a concrete class above it.

**Presentation** holds `DashboardGUI` with seven tab panels (all subclasses of `BasePanel`) and `CoachCLI`. Neither contains business logic. Both call exactly one class, `CoachController`.

**Application** holds `CoachController`, the single entry point for every feature, together with the `EventBus` that notifies the GUI of changes, the `PlanEditHistory` that records undoable plan edits, and `FxTaskRunner`, which keeps long running work off the JavaFX Application Thread.

**Domain** holds the data model (`LifterProfile`, `WorkoutSession`, `SetEntry`, `RecoveryDay`, `TrainingBlock` → `Week` → `Session` → `ExercisePrescription`, `DailyIntake`, `FoodEntry`, `MeetAttempts`) and the deterministic services (`StrengthAnalytics`, `AttemptCalculator`, `MacroTracker`, `RecoveryRuleEngine`). Nothing in this layer knows an LLM exists.

**Agent** holds the abstract `CoachAgent` and six concrete agents, the `LLMClient` interface with `ClaudeClient`, the `ToolManager` and five tools, `PromptBuilder`, `ResponseParser`, `PlanValidator`, and `MemoryManager`.

**Infrastructure** holds the CSV adapters behind `DataSource`, `CsvImportService`, the exercise and food catalogs, and the SQLite repositories behind a generic `Repository<T>` interface.

The LLM is Anthropic Claude, reached through LangChain4j's `AnthropicChatModel` inside `ClaudeClient`. The model name, token limit, and timeout are read from configuration rather than hard coded, so the model can be changed without code changes. Tools are passed to Claude as LangChain4j `ToolSpecification` objects built from our own `ToolSchema`, and structured outputs (training blocks, meal suggestions) are requested as JSON matching a schema supplied by `PromptBuilder`.

An agent call takes seconds, and JavaFX has a single UI thread, so `CoachController` dispatches agent work through `FxTaskRunner` on a background thread and delivers results and events back through `Platform.runLater()`. The GUI therefore stays responsive while Claude is working, and the panels still receive events on the thread that is allowed to touch them. The CLI calls the same controller methods and simply blocks, since it has no UI thread to protect.

The key principle is that **the LLM never writes directly to the domain**. Every model response passes through `ResponseParser` (structure) and `PlanValidator` (rules) before a domain object is created, and every number the agent reports (e1RM, attempts, macros) comes from a deterministic service or tool rather than from the model. This makes the deterministic layer fully unit testable and gives the agent layer clear behavioural contracts to test with KUMA.

## 3.2 Key Relationships and Multiplicities

| Relationship | Type | Multiplicity | Meaning |
|---|---|---|---|
| `DashboardGUI` → `BasePanel` | Composition | 1 to 7 | The window owns its tab panels |
| `BasePanel` → `CoachController` | Association | * to 1 | Every panel talks to the one facade |
| `LifterProfile` → `DietaryConstraints` | Composition | 1 to 1 | Constraints have no life outside the profile |
| `LifterProfile` → `WorkoutSession` | Association | 1 to 0..* | A lifter logs many sessions |
| `LifterProfile` → `TrainingBlock` | Association | 1 to 0..1 | At most one active block |
| `WorkoutSession` → `SetEntry` | Composition | 1 to 1..* | Sets are deleted with their session |
| `TrainingBlock` → `Week` → `Session` → `ExercisePrescription` | Composition chain | 1 to 1..* at each level | A block is a tree that lives and dies as a unit |
| `ExercisePrescription`, `SetEntry` → `Exercise` | Association | * to 1 | Many prescriptions reference one catalog exercise |
| `CoachAgent` → `ToolManager` | Composition | 1 to 1 | Each agent owns its own tool set |
| `ToolManager` → `Tool` | Aggregation | 1 to * | Tools are shared objects that outlive any one manager |
| `CoachAgent` → `LLMClient` | Aggregation | * to 1 | All agents share one injected client |
| `EventBus` → `EventListener` | Aggregation | 1 to * | Listeners register and unregister freely |
| `PlanEditHistory` → `PlanEditCommand` | Aggregation | 1 to * | The undo stack |
| `ChatAgent` → `MemoryManager` → `ConversationHistory` | Association, then composition | 1 to 1 | Only the chat agent carries long term memory |

Inheritance appears in `BasePanel` (7 panels), `CoachAgent` (6 agents), and interface realisation in `LLMClient`, `Tool`, `DataSource`, `ProgressionStrategy`, `PlanEditCommand`, `EventListener`, and `Repository<T>`.

## 3.3 Design Changes Since the Initial Outline

Several refinements were made while drawing the diagram. A separate `Planner` class was removed because tool selection is handled inside the agent loop (`CoachAgent.toolLoop()`) through Claude tool use, and a separate class would have had no responsibility of its own. `ScriptedLLMClient` was added as a second `LLMClient` implementation that replays fixed responses, so agent orchestration can be unit tested without network calls. The Factory Method pattern was moved from a standalone `ToolFactory` to `CoachAgent.createToolset()`, which is the textbook form of the pattern and removes a class. Finally, the meet date became optional and a `TrainingGoal` was added to `LifterProfile`, because not every lifter competes: the goal now selects the progression strategy, and F08 serves non competing lifters as a test day planner.

---

# 4. Design Patterns

Seven patterns are applied. Each one solves a problem that exists in this system independently of the requirement to use patterns.

## 4.1 Facade — `CoachController`

**Problem.** Every feature touches several subsystems. Generating a block, for example, needs the profile repository, workout repository, strength analytics, the block generation agent, the block repository, and the event bus. The system also has two user interfaces. Without a single entry point, both the GUI and the CLI would need to know about and coordinate all of these subsystems, and that orchestration logic would exist twice.

**Participants.** `CoachController` is the facade. The subsystem classes are `CsvImportService`, `StrengthAnalytics`, `AttemptCalculator`, `MacroTracker`, `RecoveryRuleEngine`, the repositories, `AgentRegistry`, `EventBus`, and `PlanEditHistory`. The clients are every `BasePanel` subclass and `CoachCLI`.

**Why it fits.** The requirement that every feature be reachable from both a GUI and a CLI is exactly the situation a facade is meant for: many clients, one complex subsystem.

**Without it.** Adding the CLI would mean duplicating all orchestration code, the two interfaces would drift apart, and KUMA tests driven through the CLI would not be testing the same code path the GUI uses.

## 4.2 Strategy — `LLMClient` and `ProgressionStrategy`

**Problem.** Two parts of the system have interchangeable algorithms. The LLM provider must be swappable, both for testing (a scripted fake) and so a cheaper or newer model can be tried without touching agent code. The progression scheme used to plan a block (RPE based, linear, or percentage based) changes which loads are valid, and both the prompt and the validator need to follow whichever scheme was chosen.

**Participants.** For the LLM: `LLMClient` is the strategy interface, `ClaudeClient` (which wraps LangChain4j) and `ScriptedLLMClient` are concrete strategies, and `CoachAgent` is the context. For progression: `ProgressionStrategy` is the interface, `RPEProgression`, `LinearProgression`, and `PercentageProgression` are concrete strategies, `ProgressionStrategyFactory.forGoal()` selects one from the lifter's `TrainingGoal`, and `BlockGenerationAgent` and `PlanValidator` are the contexts. A lifter preparing for a meet, one building general strength, and one training for fun need different load progressions, and this is what keeps that difference out of the planning code.

**Why it fits.** The contexts only need one operation each (`complete()` and `loadRange()`), and the choice is made once at configuration time.

**Without it.** Agents would depend directly on LangChain4j types, deterministic tests of the agent loop would require live API calls, and the validator would contain an `if scheme == ...` chain that must be kept in sync with the prompt.

## 4.3 Observer — `EventBus`, `EventListener`, `BasePanel`

**Problem.** One change often affects several screens. Importing a Hevy file must refresh the Log and Analytics tabs; applying a recovery adjustment must refresh the Plan tab and clear a warning badge. The controller and agents should not know which panels exist.

**Participants.** `EventBus` is the subject, `EventListener` is the observer interface, every `BasePanel` subclass is a concrete observer, `DomainEvent` and `EventType` carry the notification, and `CoachController` publishes events after each state change.

**Why it fits.** The relationship is one to many and the set of listeners is not known in advance. The CLI simply does not subscribe, and the same controller works for both interfaces.

**Without it.** The controller would hold references to specific panels, coupling application logic to the GUI and making the controller impossible to use from the CLI or from tests without a window.

## 4.4 Command — `PlanEditCommand` and `PlanEditHistory`

**Problem.** Lifters edit generated plans by swapping exercises, changing loads, and accepting or rejecting agent adjustments. These edits must be undoable, and an agent generated adjustment must be applied through exactly the same mechanism as a manual edit so it can be undone the same way.

**Participants.** `PlanEditCommand` is the command interface; `SwapExerciseCommand`, `ChangeLoadCommand`, and `ApplyAdjustmentCommand` are concrete commands; `PlanEditHistory` is the invoker; `TrainingBlock` is the receiver; `CoachController` is the client that creates commands.

**Why it fits.** Each command stores the previous value it replaced, which makes `undo()` trivial, and the history doubles as an audit log of how a plan evolved.

**Without it.** Undo would require snapshotting the whole block before every edit, and agent changes would bypass whatever manual undo logic existed.

## 4.5 Adapter — `DataSource`, `HevyCsvAdapter`, `WhoopCsvAdapter`

**Problem.** Hevy and Whoop exports have completely different column layouts, units, and date formats. The import service should see a single uniform interface that returns domain objects.

**Participants.** `DataSource` is the target interface, `HevyCsvAdapter` and `WhoopCsvAdapter` are adapters, `CsvFileReader` (raw rows as dictionaries) is the adaptee, and `CsvImportService` is the client.

**Why it fits.** The vendor formats are fixed and outside our control; only a translation layer can make them conform. This is also the extension point for a live API integration later: a `HevyApiAdapter` would implement the same interface with no change above it.

**Without it.** `CsvImportService` would contain vendor specific parsing branches, and every new data source would require modifying tested code.

## 4.6 Factory Method — `CoachAgent.createToolset()`

**Problem.** Each agent needs a different set of tools. The block generator needs analytics and the exercise database; the meal agent needs only the food database; the chat agent needs analytics, plan lookup, and recovery lookup. The common agent loop in `CoachAgent` must register tools without knowing which concrete tools a given agent uses.

**Participants.** `CoachAgent` is the creator and declares the abstract factory method `createToolset()`. Each concrete agent is a concrete creator that returns its own tools. `Tool` is the product interface and the five tool classes are concrete products. `DataSourceFactory` applies the related parameterised factory idea on the import side, choosing `HevyCsvAdapter` or `WhoopCsvAdapter` from the file header, and `ProgressionStrategyFactory` does the same for progression schemes, choosing one from the lifter's training goal.

**Why it fits.** Tool selection varies with exactly the same axis as the agent subclass, so letting the subclass decide keeps each agent's capabilities defined in one place.

**Without it.** The base class would need a conditional on agent type to decide which tools to create, and adding a new agent would require editing the base class.

## 4.7 Template Method — `CoachAgent.run()`

**Problem.** All six agents follow the same algorithm: gather context, build a prompt, call the model (running any requested tools), parse the reply, validate it, and retry once with the validation errors if it fails. Only the individual steps differ between agents.

**Participants.** `CoachAgent` is the abstract class and `run()` is the final template method. The primitive operations are `gatherContext()`, `buildPrompt()`, `parse()`, `validate()`, and `createToolset()`. `BlockGenerationAgent`, `SubstitutionAgent`, `AdjustmentAgent`, `AttemptRationaleAgent`, `MealSuggestionAgent`, and `ChatAgent` are the concrete classes.

**Why it fits.** The invariant parts (the tool calling loop, retry policy, error handling, and result wrapping) are the parts most likely to contain bugs and most important to test once. Subclasses cannot accidentally skip validation because they never control the sequence.

**Without it.** The loop would be copied six times, a fix to the retry logic would need to be made in six places, and it would be easy for one agent to return unvalidated output.

## 4.8 Supporting Patterns (not counted)

`PromptBuilder` follows the **Builder** pattern, assembling prompts step by step from system instructions, context, schema, and memory. The `Repository<T>` interface follows the **Repository** pattern, isolating SQLite behind an interface so tests can use an in memory database. These are listed for completeness but are not counted toward the five required patterns.

## 4.9 Pattern Summary

| Pattern | Key Classes | Features Using It |
|---|---|---|
| Facade | `CoachController` | All (F01 to F11) |
| Strategy | `LLMClient`, `ClaudeClient`, `ScriptedLLMClient`; `ProgressionStrategy` and implementations | F05 to F08, F10, F11 |
| Observer | `EventBus`, `EventListener`, `BasePanel` subclasses | F01 to F03, F05 to F07, F09 |
| Command | `PlanEditCommand`, `PlanEditHistory`, three concrete commands | F05, F06, F07 |
| Adapter | `DataSource`, `HevyCsvAdapter`, `WhoopCsvAdapter`, `CsvFileReader` | F02, F03 |
| Factory Method | `CoachAgent.createToolset()`, concrete agents, `Tool`; `DataSourceFactory`; `ProgressionStrategyFactory` | F02, F03, F05, F06, F10, F11 |
| Template Method | `CoachAgent.run()`, six concrete agents | F05 to F08, F10, F11 |
