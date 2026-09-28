#!/usr/bin/env python3
"""Generate the UMLet system overview (diagrams/uxf/architecture.uxf).

Same content as diagrams/architecture/architecture.puml, laid out in columns
so the flow reads left to right: user, interfaces, application, agent,
domain and infrastructure, external services.
"""

import os
from puml_to_uxf import write_uxf, GRID

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = os.path.join(ROOT, "diagrams", "uxf", "architecture.uxf")

# column x, box width
COLS = {
    "user": (30, 120),
    "pres": (200, 260),
    "app": (510, 300),
    "agent": (870, 360),
    "domain": (1290, 320),
    "infra": (1660, 300),
    "ext": (2010, 200),
}

BOXES = [
    # key, column, y, height, text
    ("GUI", "pres", 120, 90,
     "*JavaFX Dashboard*\n--\n7 tabs: Profile, Log, Analytics,\nPlan, Meet, Nutrition, Coach Chat"),
    ("CLI", "pres", 240, 80,
     "*CLI (picocli)*\n--\nsame operations, scriptable\nspotter plan generate 6"),

    ("Ctrl", "app", 110, 90,
     "*CoachController*\n<<Facade>>\n--\none method per feature; both\ninterfaces call only this class"),
    ("Fx", "app", 230, 70,
     "*FxTaskRunner*\n--\nagent calls run off the\nJavaFX thread"),
    ("Bus", "app", 330, 80,
     "*EventBus*\n<<Observer>>\n--\nPLAN_UPDATED, DATA_IMPORTED,\nRECOVERY_FLAGGED"),
    ("Hist", "app", 440, 80,
     "*PlanEditHistory*\n<<Command>>\n--\nundo stack for every plan\nchange, manual or agent"),

    ("Agent", "agent", 60, 130,
     "*CoachAgent*\n<<Template Method>>\n--\ngather context > build prompt >\ncall model > run tools > parse >\nvalidate > retry once\n--\nSix agents: BlockGeneration,\nSubstitution, Adjustment,\nAttemptRationale, MealSuggestion, Chat"),
    ("Tools", "agent", 220, 90,
     "*Tools*\n--\nwhat the agent may look up:\nAnalyticsTool, ExerciseDBTool,\nFoodDBTool, PlanLookupTool,\nRecoveryLookupTool"),
    ("Prompt", "agent", 340, 60,
     "*PromptBuilder*\n<<Builder>>\n--\ninstructions, context, JSON schema"),
    ("Parse", "agent", 420, 70,
     "*ResponseParser*\n--\nJSON into domain objects,\nfacts read from the catalogs"),
    ("Val", "agent", 520, 90,
     "*PlanValidator*\n--\nthe gate: loads in range,\nexercise exists, meal permitted,\nnumbers match the calculator"),
    ("Mem", "agent", 640, 60,
     "*MemoryManager*\n--\n+ ConversationHistory\nrecall, remember, summarise"),
    ("LLM", "agent", 720, 70,
     "*LLMClient*\n<<Strategy>>\n--\nClaudeClient via LangChain4j\nScriptedLLMClient for tests"),

    ("An", "domain", 60, 60,
     "*StrengthAnalytics* + RPEChart\n--\nestimated 1RM, tonnage, trends"),
    ("Calc", "domain", 140, 70,
     "*AttemptCalculator*\n--\nmeet attempts or test day plan,\nplate rounding"),
    ("Mac", "domain", 240, 60,
     "*MacroTracker*\n--\nremaining macros, adherence"),
    ("Rules", "domain", 320, 70,
     "*RecoveryRuleEngine*\n--\nflags poor recovery,\ndeterministic fallback"),
    ("Prog", "domain", 420, 70,
     "*ProgressionStrategy*\n<<Strategy>>\n--\nRPE, linear or percentage,\npicked from the training goal"),
    ("Ent", "domain", 520, 100,
     "*Entities*\n--\nLifterProfile, TrainingGoal,\nWorkoutSession, SetEntry, RecoveryDay,\nTrainingBlock > Week > Session >\nPrescription, DailyIntake"),

    ("DS", "infra", 120, 80,
     "*DataSource*\n<<Adapter>>\n--\nHevyCsvAdapter, WhoopCsvAdapter\nCsvImportService"),
    ("Cat", "infra", 240, 60,
     "*Catalogs*\n--\nExerciseCatalog, FoodCatalog"),
    ("Repo", "infra", 330, 80,
     "*Repository<T>*\n--\nprofile, workouts, recovery,\nblocks, intake, memory"),

    ("Claude", "ext", 700, 60, "*Claude*\n--\nAnthropic API"),
    ("Hevy", "ext", 120, 40, "Hevy CSV export"),
    ("Whoop", "ext", 180, 40, "Whoop CSV export"),
    ("DB", "ext", 340, 40, "SQLite"),
]

FRAMES = [
    ("Presentation: how it is used", "pres", 90, 250),
    ("Application: one way in", "app", 80, 460),
    ("Agent: judgement, inside limits", "agent", 30, 780),
    ("Domain: every number is computed here", "domain", 30, 610),
    ("Infrastructure: data in and out", "infra", 90, 340),
]

ARROWS = [
    ("User", "GUI", ""), ("User", "CLI", ""),
    ("GUI", "Ctrl", ""), ("CLI", "Ctrl", ""),
    ("Ctrl", "Fx", ""), ("Ctrl", "Bus", ""), ("Ctrl", "Hist", ""),
    ("Bus", "GUI", "refresh"),
    ("Fx", "Agent", "runs agents in\nthe background"),
    ("Ctrl", "An", ""), ("Ctrl", "Calc", ""), ("Ctrl", "Mac", ""),
    ("Ctrl", "Rules", ""), ("Ctrl", "DS", ""),
    ("Agent", "Tools", ""), ("Agent", "Prompt", ""), ("Agent", "Parse", ""),
    ("Agent", "Val", ""), ("Agent", "Mem", ""), ("Agent", "LLM", ""),
    ("LLM", "Claude", "tool schemas out,\nJSON replies back"),
    ("Tools", "An", ""), ("Tools", "Cat", ""), ("Tools", "Repo", ""),
    ("Val", "Prog", ""),
    ("Val", "Ent", "only validated output\nbecomes domain data"),
    ("Parse", "Cat", ""),
    ("Hist", "Ent", "apply / undo"),
    ("DS", "Hevy", ""), ("DS", "Whoop", ""), ("DS", "Repo", ""),
    ("Ent", "Repo", ""), ("Repo", "DB", ""),
]


def main():
    elements, pos = [], {}

    for title, col, y, h in FRAMES:
        x, w = COLS[col]
        elements.append(("UMLFrame", (x - 20, y, w + 40, h), title, ""))

    x, w = COLS["user"]
    pos["User"] = (x, 300, 80, 100)
    elements.append(("UMLActor", (x, 300, 80, 100), "Lifter", ""))

    for key, col, y, h, text in BOXES:
        cx, cw = COLS[col]
        pos[key] = (cx, y, cw, h)
        elements.append(("UMLClass", (cx, y, cw, h), text, ""))

    def anchor(a, b):
        ax, ay, aw, ah = pos[a]
        bx, by, bw, bh = pos[b]
        # leave on the right edge, arrive on the left edge, since flow is left to right
        if bx >= ax:
            p1 = (ax + aw, ay + ah / 2.0)
            p2 = (bx, by + bh / 2.0)
        else:
            p1 = (ax, ay + ah / 2.0)
            p2 = (bx + bw, by + bh / 2.0)
        return p1, p2

    for a, b, label in ARROWS:
        (x1, y1), (x2, y2) = anchor(a, b)
        minx, miny = min(x1, x2) - 10, min(y1, y2) - 10
        rw, rh = abs(x2 - x1) + 20, abs(y2 - y1) + 20
        minx = int(round(minx / GRID) * GRID)
        miny = int(round(miny / GRID) * GRID)
        rw = max(int(round(rw / GRID) * GRID), 20)
        rh = max(int(round(rh / GRID) * GRID), 20)
        pts = "%.1f;%.1f;%.1f;%.1f" % (x1 - minx, y1 - miny, x2 - minx, y2 - miny)
        panel = "lt=->" if not label else "lt=->\n%s" % label
        elements.append(("Relation", (minx, miny, rw, rh), panel, pts))

    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    write_uxf(OUT, elements)
    print("%d elements -> %s" % (len(elements), os.path.relpath(OUT, ROOT)))


if __name__ == "__main__":
    main()
