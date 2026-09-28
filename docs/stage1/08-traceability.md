# 8. Feature to Design Traceability

Every feature traces to a use case that describes the interaction, the classes that implement it, the methods called, the sequence diagram that shows the runtime collaboration, and the design patterns involved. Section 9 expands each row into prose.

| Feature | Description | Type | Use Case | Classes | Key Methods | Sequence Diagram | Design Pattern(s) |
|---|---|---|---|---|---|---|---|
| F01 | Lifter profile, training goal, constraints and macro targets | Deterministic | UC01 | `ProfilePanel`, `CoachController`, `LifterProfile`, `DietaryConstraints`, `TrainingGoal`, `ProfileRepository`, `EventBus` | `onSaveClicked()`, `saveProfile()`, `hasMeetDate()`, `weeksUntilMeet()`, `save()`, `publish()` | SD09 | Facade, Observer, Repository |
| F02 | Workout import from Hevy CSV or manual entry | Deterministic | UC02, UC03, UC15 | `LogPanel`, `CoachController`, `CsvImportService`, `DataSourceFactory`, `HevyCsvAdapter`, `CsvFileReader`, `ExerciseCatalog`, `WorkoutSession`, `SetEntry`, `WorkoutRepository` | `onImportHevyClicked()`, `importWorkouts()`, `importFile()`, `detect()`, `create()`, `read()`, `byName()`, `closestMatches()`, `addAlias()`, `dedupe()`, `save()` | SD01 | Adapter, Factory Method, Facade, Observer, Repository |
| F03 | Recovery import from Whoop CSV | Deterministic | UC04 | `LogPanel`, `CoachController`, `CsvImportService`, `DataSourceFactory`, `WhoopCsvAdapter`, `RecoveryDay`, `RecoveryRepository`, `RecoveryRuleEngine`, `EventBus` | `onImportWhoopClicked()`, `importRecovery()`, `read()`, `save()`, `evaluate()`, `publish()` | SD01 | Adapter, Factory Method, Observer, Repository |
| F04 | Strength analytics: e1RM, tonnage, trends | Deterministic | UC05 | `AnalyticsPanel`, `CoachController`, `WorkoutRepository`, `StrengthAnalytics`, `RPEChart`, `AnalyticsSummary`, `Point` | `onLiftSelected()`, `getAnalytics()`, `findByLift()`, `computeE1RM()`, `percentOf1RM()`, `e1rmSeries()`, `weeklyTonnage()`, `bestSet()` | SD02 | Facade, Repository |
| F05 | Training block generation | AI | UC06, UC14 | `PlanPanel`, `CoachController`, `ProgressionStrategyFactory`, `ProgressionStrategy`, `BlockGenerationAgent`, `ToolManager`, `AnalyticsTool`, `ExerciseDBTool`, `PromptBuilder`, `ClaudeClient`, `ResponseParser`, `PlanValidator`, `TrainingBlock`, `BlockRepository` | `onGenerateClicked()`, `generateBlock()`, `forGoal()`, `run()`, `createToolset()`, `gatherContext()`, `buildPrompt()`, `complete()`, `execute()`, `toBlock()`, `validateBlock()`, `save()` | SD03 | Template Method, Strategy, Factory Method, Facade, Observer, Builder |
| F06 | Equipment substitution | AI | UC08, UC07, UC14 | `PlanPanel`, `CoachController`, `SubstitutionAgent`, `ExerciseDBTool`, `ExerciseCatalog`, `ClaudeClient`, `PlanValidator`, `PlanEditHistory`, `SwapExerciseCommand`, `TrainingBlock` | `onSubstituteClicked()`, `suggestSubstitute()`, `run()`, `candidates()`, `validateSubstitution()`, `applyEdit()`, `execute()`, `undo()` | SD04 | Template Method, Command, Factory Method, Facade |
| F07 | Recovery aware session adjustment | Hybrid | UC09, UC07, UC14 | `PlanPanel`, `CoachController`, `RecoveryRuleEngine`, `RecoveryFlag`, `RecoveryRepository`, `AdjustmentAgent`, `ClaudeClient`, `PlanValidator`, `PlanEditHistory`, `ApplyAdjustmentCommand`, `Session`, `EventBus` | `evaluate()`, `onAdjustClicked()`, `adjustSession()`, `run()`, `validateAdjustment()`, `fallbackAdjustment()`, `applyEdit()`, `publish()` | SD05 | Template Method, Command, Observer, Facade |
| F08 | Max testing and attempt planning | Hybrid | UC10, UC14 | `MeetPanel`, `CoachController`, `StrengthAnalytics`, `AttemptCalculator`, `MeetAttempts`, `TestDayPlan`, `AttemptRationaleAgent`, `ClaudeClient`, `PlanValidator` | `onCalculateClicked()`, `calculateAttempts()`, `currentE1RMs()`, `calculateMeetAttempts()`, `projectTestDay()`, `roundToPlate()`, `run()`, `validateRationale()` | SD06 | Template Method, Facade |
| F09 | Macro targets and adherence tracking | Deterministic | UC01, UC11 | `NutritionPanel`, `CoachController`, `FoodCatalog`, `FoodItem`, `FoodEntry`, `MacroTracker`, `MacroTarget`, `DailyIntake`, `MacroTotals`, `IntakeRepository`, `EventBus` | `onLogIntakeClicked()`, `logIntake()`, `byName()`, `add()`, `recordIntake()`, `getRemaining()`, `weeklyAdherence()`, `publish()` | SD09 | Facade, Observer, Repository |
| F10 | Vegetarian meal suggestion | AI | UC12, UC14 | `NutritionPanel`, `CoachController`, `MacroTracker`, `MealSuggestionAgent`, `ToolManager`, `FoodDBTool`, `FoodCatalog`, `DietaryConstraints`, `ClaudeClient`, `ResponseParser`, `PlanValidator`, `MealSuggestion` | `onSuggestMealClicked()`, `suggestMeal()`, `getRemaining()`, `run()`, `search()`, `permits()`, `toMeal()`, `validateMeal()` | SD07 | Template Method, Factory Method, Facade |
| F11 | Coaching chat with memory | AI | UC13, UC14 | `ChatPanel`, `CoachController`, `ChatAgent`, `MemoryManager`, `ConversationHistory`, `ToolManager`, `AnalyticsTool`, `PlanLookupTool`, `RecoveryLookupTool`, `ClaudeClient` | `onSendClicked()`, `chat()`, `run()`, `recall()`, `remember()`, `summarizeOlderTurns()`, `execute()`, `complete()` | SD08 | Template Method, Strategy, Factory Method, Facade |

## 8.1 Coverage Checks

**Every feature has a use case and a sequence diagram.** F01 to F11 each appear in at least one use case description in section 6 and at least one diagram in section 7.

**Every use case belongs to a feature.** UC01 to UC13 map to features directly; UC14 (Run Agent Task) is included by the six AI use cases and belongs to F05, F06, F07, F08, F10 and F11; UC15 (Resolve Unknown Exercise) extends UC02 and belongs to F02.

**Every pattern is used by more than one feature**, which is what distinguishes a structural decision from decoration.

| Pattern | Features |
|---|---|
| Facade | F01 to F11 |
| Strategy | F05, F11, and every feature that reaches the model through `LLMClient` |
| Observer | F01, F02, F03, F05, F06, F07, F09 |
| Command | F05, F06, F07 |
| Adapter | F02, F03 |
| Factory Method | F02, F03, F05, F06, F10, F11 |
| Template Method | F05, F06, F07, F08, F10, F11 |

**Deterministic and AI behaviour are separable for Stage 3.** The classes in the traceability table divide cleanly: `StrengthAnalytics`, `RPEChart`, `AttemptCalculator`, `MacroTracker`, `RecoveryRuleEngine`, `CsvImportService`, the adapters, the catalogs and the repositories are all unit testable with JUnit, while the six `CoachAgent` subclasses and their validators are what KUMA exercises. `PlanValidator` sits on the boundary and is testable both ways, which is deliberate: it is the component that decides whether model output is acceptable.
