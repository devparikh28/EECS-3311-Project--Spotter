# Stage 1 — Design Report

The complete Stage 1 report, in nine sections numbered to match the Stage 1 deliverables list.

| Section | Document | Contents |
|---|---|---|
| 1. Project Overview | [01-overview.md](01-overview.md) | Problem, target users, what the agent does, why an agent fits, the LLM and how it integrates, architecture, technology, scope |
| 2. Feature Specifications | [02-features.md](02-features.md) | Fourteen features, each with description, GUI and CLI interaction, input, output, AI classification, workflow and error cases |
| 3. UML Class Diagram | [03-class-diagram-and-patterns.md](03-class-diagram-and-patterns.md) | Five rendered views, relationships and multiplicities, design changes |
| 4. Design Patterns | [03-class-diagram-and-patterns.md](03-class-diagram-and-patterns.md#4-design-patterns) | Eight patterns, each with problem, participants, rationale and the cost of omitting it |
| 5. Use Case Diagram | [05-use-cases.md](05-use-cases.md) | Actors and use cases, with include and extend relationships |
| 6. Use Case Descriptions | [05-use-cases.md](05-use-cases.md#6-use-case-descriptions) | Nineteen descriptions, each with the ten required fields |
| 7. Sequence Diagrams | [07-sequence-diagrams.md](07-sequence-diagrams.md) | Twelve diagrams covering every feature, using real class and method names |
| 8. Traceability Table | [08-traceability.md](08-traceability.md) | Feature to use case, classes, methods, sequence diagram and patterns, with coverage checks |
| 9. Feature Realization | [09-feature-realization.md](09-feature-realization.md) | How the classes and methods collaborate to deliver each feature |

Diagrams live in [`diagrams/`](../../diagrams): PlantUML source rendered to PNG and SVG, and the same diagrams as UMLet `.uxf` files with UMLet's own exports under `diagrams/uxf/png/`.
