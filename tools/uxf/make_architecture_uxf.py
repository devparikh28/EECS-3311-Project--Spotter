#!/usr/bin/env python3
"""Generate the UMLet system overview (diagrams/architecture.uxf).

One page, every component, laid out as stacked layer bands so that arrows only
ever cross between neighbouring layers. Agent and Domain sit side by side
because the Application layer talks to both and the Agent layer talks to the
Domain layer; stacking them would force arrows across a band.

Same content as diagrams/sources/architecture.puml.
"""

import os
from puml_to_uxf import write_uxf, GRID

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = os.path.join(ROOT, "diagrams", "architecture.uxf")

CHAR_W = 8
LINE_H = 16
PAD_H = 26
GAP = 30          # gap between boxes inside a band
FPAD = 25         # frame padding
TITLE_H = 40      # space for the frame title

# ---------------------------------------------------------------- content

PRES = [
    ("*JavaFX Dashboard*", "seven tabs: Profile, Log, Analytics,", "Plan, Meet, Nutrition, Coach Chat"),
    ("*CLI (picocli)*", "the same operations, scriptable", "spotter plan generate 6"),
]

APP = [
    ("*CoachController*\n<<Facade>>", "one method per feature;", "both interfaces call only this class"),
    ("*FxTaskRunner*", "runs agent work off the", "JavaFX thread"),
    ("*EventBus*\n<<Observer>>", "PLAN_UPDATED, DATA_IMPORTED,", "RECOVERY_FLAGGED"),
    ("*PlanEditHistory*\n<<Command>>", "undo stack for every plan change,", "manual or agent"),
]

DOM = [
    ("*StrengthAnalytics and RPEChart*", "estimated 1RM, tonnage, trends"),
    ("*PlanAdherence*", "prescribed against logged"),
    ("*AttemptCalculator*", "meet attempts or test day plan,", "plate rounding"),
    ("*MacroTracker*", "remaining macros, adherence"),
    ("*RecoveryRuleEngine*", "flags poor recovery,", "deterministic fallback"),
    ("*ProgressionStrategy*\n<<Strategy>>", "RPE, linear or percentage,", "picked from goal and experience"),
    ("*Entities*", "LifterProfile, TrainingGoal,", "WorkoutSession, SetEntry,",
     "RecoveryDay, DailyIntake,", "TrainingBlock > Week >", "Session > Prescription"),
]

AGENT = [
    ("*CoachAgent*\n<<Template Method>>", "gather context > build prompt > call model >",
     "run tools > parse > validate > retry once", "seven agents, one per AI feature"),
    ("*Tools*", "what the agent may look up: AnalyticsTool,", "ExerciseDBTool, FoodDBTool, PlanLookupTool,",
     "RecoveryLookupTool, AdherenceTool"),
    ("*PromptBuilder*\n<<Builder>>", "instructions, context, JSON schema"),
    ("*ResponseParser*", "JSON into domain objects;", "facts read from the catalogs"),
    ("*PlanValidator*", "the gate: loads in range,", "exercise exists, meal permitted,", "numbers match the calculator"),
    ("*MemoryManager and ConversationHistory*", "recall, remember, summarise"),
    ("*LLMClient*\n<<Strategy>>", "ClaudeClient via LangChain4j;", "ScriptedLLMClient for tests"),
]

INFRA = [
    ("*DataSource*\n<<Adapter>>", "HevyCsvAdapter, WhoopCsvAdapter,", "GenericCsvAdapter, CsvImportService"),
    ("*Catalogs*", "ExerciseCatalog, FoodCatalog"),
    ("*Repository<T>*", "profile, workouts, recovery,", "blocks, intake, memory"),
]

# band title, boxes, number of inner columns
BANDS = [
    ("pres",  "Presentation: how it is used",            PRES,  2),
    ("app",   "Application: one way in",                 APP,   4),
    ("dom",   "Domain: every number is computed here",   DOM,   2),
    ("agent", "Agent: judgement, inside limits",         AGENT, 2),
    ("infra", "Infrastructure: data in and out",         INFRA, 3),
]

EXTERNAL = [
    ("claude", "*Claude*\n--\nAnthropic API"),
    ("csv",    "*Tracker exports*\n--\nHevy, Whoop, or any\nother app's CSV"),
    ("db",     "*SQLite*\n--\nlocal file, one lifter"),
]


def snap(v):
    return int(round(v / GRID) * GRID)


def box_text(parts):
    head = parts[0]
    body = [p for p in parts[1:] if p]
    return head + ("\n--\n" + "\n".join(body) if body else "")


def box_size(parts, width):
    lines = box_text(parts).split("\n")
    return width, snap(len(lines) * LINE_H + PAD_H)


def main():
    elements = []

    # --- geometry -------------------------------------------------------
    LEFT = 210                      # leaves room for the actor
    FULL_W = 1600                   # width of a full-width band
    DOM_W, AGENT_W = 640, 840       # side by side; DOM_W + MID_GAP + AGENT_W = FULL_W
    MID_GAP = 120                   # room for the Agent to Domain label
    EXT_X, EXT_W = LEFT + FULL_W + 170, 230

    def lay(boxes, cols, x0, band_w, y0):
        """Place boxes in a grid inside a band. Returns (elements, band height)."""
        inner = band_w - 2 * FPAD
        bw = snap((inner - (cols - 1) * GAP) / cols)
        out, y, row_h, i = [], y0 + TITLE_H, 0, 0
        while i < len(boxes):
            row = boxes[i:i + cols]
            sizes = [box_size(b, bw) for b in row]
            row_h = max(h for _, h in sizes)
            for j, b in enumerate(row):
                x = x0 + FPAD + j * (bw + GAP)
                out.append(("UMLClass", (x, y, bw, row_h), box_text(b), ""))
            y += row_h + GAP
            i += cols
        return out, (y - GAP) - y0 + FPAD

    y = 40
    frames, band_box = [], {}

    # Presentation
    els, h = lay(PRES, 2, LEFT, FULL_W, y)
    frames.append(("Presentation: how it is used", LEFT, y, FULL_W, h)); elements += els
    band_box["pres"] = (LEFT, y, FULL_W, h); y += h + 70

    # Application
    els, h = lay(APP, 4, LEFT, FULL_W, y)
    frames.append(("Application: one way in", LEFT, y, FULL_W, h)); elements += els
    band_box["app"] = (LEFT, y, FULL_W, h); y += h + 80

    # Domain and Agent side by side
    row_y = y
    els_d, hd = lay(DOM, 2, LEFT, DOM_W, row_y)
    els_a, ha = lay(AGENT, 2, LEFT + DOM_W + MID_GAP, AGENT_W, row_y)
    h = max(hd, ha)
    frames.append(("Domain: every number is computed here", LEFT, row_y, DOM_W, h))
    frames.append(("Agent: judgement, inside limits", LEFT + DOM_W + MID_GAP, row_y, AGENT_W, h))
    elements += els_d + els_a
    band_box["dom"] = (LEFT, row_y, DOM_W, h)
    band_box["agent"] = (LEFT + DOM_W + MID_GAP, row_y, AGENT_W, h)
    y += h + 80

    # Infrastructure
    els, h = lay(INFRA, 3, LEFT, FULL_W, y)
    frames.append(("Infrastructure: data in and out", LEFT, y, FULL_W, h)); elements += els
    band_box["infra"] = (LEFT, y, FULL_W, h)

    for title, fx, fy, fw, fh in frames:
        elements.append(("UMLFrame", (fx, fy - 10, fw, fh + 20), title, ""))

    # actor, beside the presentation band
    px, py, pw, ph = band_box["pres"]
    actor = (40, py + 20, 90, 110)
    elements.append(("UMLActor", actor, "Lifter", ""))

    # external systems
    ax, ay, aw, ah = band_box["agent"]
    ix, iy, iw, ih = band_box["infra"]
    ext_pos = {
        "claude": (EXT_X, ay + ah - 130, EXT_W, 80),
        "csv":    (EXT_X, iy + 30, EXT_W, 90),
        "db":     (EXT_X, iy + 150, EXT_W, 70),
    }
    for key, text in EXTERNAL:
        elements.append(("UMLClass", ext_pos[key], text, ""))

    # --- arrows ---------------------------------------------------------
    def arrow(p1, p2, label="", style="lt=->"):
        (x1, y1), (x2, y2) = p1, p2
        minx, miny = snap(min(x1, x2) - 15), snap(min(y1, y2) - 15)
        w = max(snap(abs(x2 - x1) + 30), 20)
        h = max(snap(abs(y2 - y1) + 30), 20)
        pts = "%.1f;%.1f;%.1f;%.1f" % (x1 - minx, y1 - miny, x2 - minx, y2 - miny)
        panel = style if not label else style + "\n" + label
        elements.append(("Relation", (minx, miny, w, h), panel, pts))

    def bottom(k, frac): b = band_box[k]; return (b[0] + b[2] * frac, b[1] + b[3])
    def top(k, frac):    b = band_box[k]; return (b[0] + b[2] * frac, b[1] - 10)

    ap, pp = band_box["app"], band_box["pres"]
    dm, ag, inf = band_box["dom"], band_box["agent"], band_box["infra"]

    arrow((actor[0] + actor[2], actor[1] + 40), (pp[0], pp[1] + 60))
    arrow(bottom("pres", 0.30), top("app", 0.30),
          "every action, GUI or CLI,\nenters through CoachController")
    arrow(top("pres", 0.985), bottom("app", 0.985),
          "events refresh\nthe panels", "lt=<-")
    arrow(bottom("app", 0.18), top("dom", 0.45),
          "deterministic features\ncall the domain directly")
    arrow(bottom("app", 0.72), top("agent", 0.50),
          "agent work runs off\nthe UI thread")
    arrow((ag[0], ag[1] + ag[3] * 0.88), (dm[0] + dm[2], dm[1] + dm[3] * 0.88),
          "only validated output\nbecomes domain data")
    arrow(bottom("dom", 0.50), (inf[0] + inf[2] * 0.22, inf[1] - 10),
          "entities are saved\nand loaded")
    arrow((ag[0] + ag[2] * 0.50, ag[1] + ag[3]), (inf[0] + inf[2] * 0.72, inf[1] - 10),
          "tools read facts,\nthey never write")
    arrow((ag[0] + ag[2], ext_pos["claude"][1] + 40), (EXT_X, ext_pos["claude"][1] + 40),
          "tool schemas out,\nJSON replies back")
    arrow((inf[0] + inf[2], ext_pos["csv"][1] + 45), (EXT_X, ext_pos["csv"][1] + 45))
    arrow((inf[0] + inf[2], ext_pos["db"][1] + 35), (EXT_X, ext_pos["db"][1] + 35))

    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    write_uxf(OUT, elements)
    print("%d elements -> %s" % (len(elements), os.path.relpath(OUT, ROOT)))


if __name__ == "__main__":
    main()
