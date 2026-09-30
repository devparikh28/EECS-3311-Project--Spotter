# Diagrams

Every diagram is delivered in two formats, and they are the same picture:

- `uxf/<name>.uxf` — the UMLet file, which opens and edits in UMLet 15.1 or at [umletino.com](https://www.umletino.com/umletino.html).
- `png/<name>.png` — UMLet's own export of that `.uxf`.

Each pair shares a name, so `uxf/class-domain.uxf` and `png/class-domain.png` are the same diagram.

| Diagram | Name |
|---|---|
| System overview, layered architecture | `architecture` |
| Class diagram, complete model | `class-full` |
| Class diagram, presentation and application layers | `class-presentation-application` |
| Class diagram, domain layer | `class-domain` |
| Class diagram, agent layer | `class-agent` |
| Class diagram, infrastructure layer | `class-infrastructure` |
| Use case diagram | `usecase` |
| Sequence diagrams SD01 to SD12 | `SD01-import-csv` … `SD12-adopt-programme` |

## sources/ and generators/

`sources/` holds the PlantUML the `.uxf` files are generated from. They are build inputs, not deliverables. The class diagram in particular is one model, `sources/model.iuml`, filtered into five views, which is why the five class views cannot contradict each other.

UMLet has no PlantUML import, so `generators/` holds four scripts that write the UMLet XML directly:

```
cd docs/diagrams/generators
python3 puml_to_uxf.py           # five class views from model.iuml
python3 make_usecase_uxf.py      # use case diagram
python3 make_architecture_uxf.py # system overview
python3 make_sequence_uxf.py     # SD01 to SD12
```

`puml_to_uxf.py` needs Graphviz (`dot`) for layout. The PNGs are then re-exported from UMLet:

```
cd docs/diagrams/uxf
java -Djava.awt.headless=true -jar umlet.jar -action=convert -format=png -filename=<name>.uxf
mv *.png ../png/
```

Every `.uxf` here was opened and rendered this way to confirm it loads without errors.

**One workflow at a time.** Either edit the PlantUML and regenerate, or hand-edit the `.uxf` in UMLet. Do not do both: running a generator overwrites hand edits without warning.
