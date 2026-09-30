# Diagrams

Every diagram in this project is delivered in two formats, and they are the same picture:

- `<name>.uxf` — the UMLet file, which opens and edits in UMLet 15.1.
- `<name>.png` — UMLet's own export of that `.uxf`.

| Diagram | Files |
|---|---|
| System overview | `architecture` |
| Class diagram, complete model | `class-full` |
| Class diagram, presentation and application layers | `class-presentation-application` |
| Class diagram, domain layer | `class-domain` |
| Class diagram, agent layer | `class-agent` |
| Class diagram, infrastructure layer | `class-infrastructure` |
| Use case diagram | `usecase` |
| Sequence diagrams SD01 to SD12 | `SD01-import-csv` … `SD12-adopt-programme` |

## sources/

`sources/` holds the PlantUML files the `.uxf` files are generated from. They are build inputs, not deliverables. The class diagram in particular is one model, `sources/model.iuml`, filtered into five views, which is why the five class views cannot contradict each other.

UMLet has no PlantUML import, so the `.uxf` files are generated rather than redrawn:

```
python3 tools/uxf/puml_to_uxf.py          # five class views from model.iuml
python3 tools/uxf/make_usecase_uxf.py     # use case diagram
python3 tools/uxf/make_architecture_uxf.py
python3 tools/uxf/make_sequence_uxf.py    # SD01 to SD12
```

`puml_to_uxf.py` needs Graphviz (`dot`) for layout. The PNGs are then re-exported from UMLet:

```
java -Djava.awt.headless=true -jar umlet.jar -action=convert -format=png -filename=<name>.uxf
```

Every `.uxf` in this folder was opened and rendered this way to confirm it loads without errors.
