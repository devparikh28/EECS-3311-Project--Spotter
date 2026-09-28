#!/usr/bin/env python3
"""Generate the UMLet use case diagram (diagrams/uxf/usecase.uxf)."""

import os
from puml_to_uxf import write_uxf, GRID, border_point

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = os.path.join(ROOT, "diagrams", "uxf", "usecase.uxf")

UC_W, UC_H = 310, 50
COL_X = 340          # left column of use cases
COL2_X = 760         # included / extending use cases
TOP = 80
STEP = 80

USE_CASES = [
    ("UC01 Manage Lifter Profile", COL_X),
    ("UC02 Import Workout History", COL_X),
    ("UC03 Log Workout Manually", COL_X),
    ("UC04 Import Recovery Data", COL_X),
    ("UC05 View Strength Analytics", COL_X),
    ("UC06 Generate Training Block", COL_X),
    ("UC07 Edit Training Plan", COL_X),
    ("UC08 Substitute Unavailable Exercise", COL_X),
    ("UC09 Adjust Session for Poor Recovery", COL_X),
    ("UC10 Plan a Heavy Single", COL_X),
    ("UC11 Track Macros", COL_X),
    ("UC12 Get Meal Suggestion", COL_X),
    ("UC13 Chat with Coach", COL_X),
    ("UC16 Review Adherence and Progress Plan", COL_X),
    ("UC18 Complete Guided Onboarding", COL_X),
    ("UC19 Adopt an Existing Programme", COL_X),
]

INCLUDED = [("UC14 Run Agent Task", 7), ("UC15 Resolve Unknown Exercise", 1), ("UC17 Map an Unrecognised CSV", 3)]

INCLUDES = ["UC06", "UC08", "UC09", "UC10", "UC12", "UC13", "UC16", "UC18", "UC19"]


def main():
    elements = []
    positions = {}

    # system boundary
    height = TOP + len(USE_CASES) * STEP + 40
    elements.append(("UMLFrame", (300, 40, 830, height - 20),
                     "Spotter (GUI and CLI)\n--", ""))

    for i, (label, x) in enumerate(USE_CASES):
        y = TOP + i * STEP
        key = label.split()[0]
        positions[key] = (x, y, UC_W, UC_H)
        elements.append(("UMLUseCase", (x, y, UC_W, UC_H), label, ""))

    for label, slot in INCLUDED:
        y = TOP + slot * STEP
        key = label.split()[0]
        w = 310
        positions[key] = (COL2_X, y, w, UC_H)
        elements.append(("UMLUseCase", (COL2_X, y, w, UC_H), label, ""))

    # actors
    lifter_y = TOP + (len(USE_CASES) // 2) * STEP
    positions["Lifter"] = (60, lifter_y, 80, 100)
    elements.append(("UMLActor", (60, lifter_y, 80, 100), "Lifter", ""))

    positions["Hevy"] = (60, TOP + 40, 100, 100)
    elements.append(("UMLActor", (60, TOP + 40, 100, 100),
                     "Workout App\n<<external system>>", ""))

    positions["Whoop"] = (60, TOP + 3 * STEP + 20, 100, 100)
    elements.append(("UMLActor", (60, TOP + 3 * STEP + 20, 100, 100),
                     "Recovery Source\n<<external system>>", ""))

    claude_y = TOP + 5 * STEP
    positions["Claude"] = (1180, claude_y, 110, 100)
    elements.append(("UMLActor", (1180, claude_y, 110, 100),
                     "Claude LLM Service\n<<external system>>", ""))

    def centre(key):
        x, y, w, h = positions[key]
        return x + w / 2.0, y + h / 2.0

    def relation(a, b, style, label=""):
        ax, ay, aw, ah = positions[a]
        bx, by, bw, bh = positions[b]
        bcx, bcy = centre(b)
        acx, acy = centre(a)
        x1, y1 = border_point(ax, ay, aw, ah, bcx, bcy)
        x2, y2 = border_point(bx, by, bw, bh, acx, acy)
        minx, miny = min(x1, x2) - 10, min(y1, y2) - 10
        w, h = abs(x2 - x1) + 20, abs(y2 - y1) + 20
        minx = int(round(minx / GRID) * GRID)
        miny = int(round(miny / GRID) * GRID)
        w = max(int(round(w / GRID) * GRID), 20)
        h = max(int(round(h / GRID) * GRID), 20)
        pts = "%.1f;%.1f;%.1f;%.1f" % (x1 - minx, y1 - miny, x2 - minx, y2 - miny)
        panel = style if not label else "%s\n%s" % (style, label)
        elements.append(("Relation", (minx, miny, w, h), panel, pts))

    for key in positions:
        if key.startswith("UC") and key not in ("UC14", "UC15"):
            relation("Lifter", key, "lt=-")

    relation("Hevy", "UC02", "lt=-", "CSV export")
    relation("Whoop", "UC04", "lt=-", "CSV export")
    relation("UC14", "Claude", "lt=-")

    # UMLet draws the arrowhead at the FIRST point, and an include arrow
    # points from the base use case to the included one, so UC14 comes first.
    for uc in INCLUDES:
        relation("UC14", uc, "lt=<.", "<<include>>")
    relation("UC02", "UC15", "lt=<.", "<<extend>>\n[unrecognised name]")
    relation("UC02", "UC17", "lt=<.", "<<extend>>\n[unknown format]")
    relation("UC04", "UC17", "lt=<.", "<<extend>>\n[unknown format]")

    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    write_uxf(OUT, elements)
    print("%d elements -> %s" % (len(elements), os.path.relpath(OUT, ROOT)))


if __name__ == "__main__":
    main()
