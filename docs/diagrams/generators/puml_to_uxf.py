#!/usr/bin/env python3
"""
Generate UMLet .uxf class diagrams from the PlantUML class model.

The PlantUML source in docs/diagrams/sources/model.iuml stays the single source of
truth. This script parses it, lays the classes out with Graphviz, and writes
UMLet .uxf files so the same design can be opened and edited in UMLet.

Usage:  python3 docs/diagrams/generators/puml_to_uxf.py
Output: docs/diagrams/uxf/class-<view>.uxf
"""

import os
import re
import subprocess
import xml.etree.ElementTree as ET

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
MODEL = os.path.join(ROOT, "docs", "diagrams", "sources", "model.iuml")
OUTDIR = os.path.join(ROOT, "docs", "diagrams", "uxf")

ZOOM = 10
CHAR_W = 8          # approximate character width at zoom 10 (UMLet font)
LINE_H = 14         # line height at zoom 10
PAD_W = 30
PAD_H = 20
GRID = 10

VIEWS = {
    "full": {"pres", "app", "dom", "agent", "infra"},
    "presentation-application": {"pres", "app"},
    "domain": {"dom"},
    "agent": {"agent"},
    "infrastructure": {"infra"},
}

# PlantUML relation token -> (UMLet lt= style, arrow points from first to second listed element)
# In UMLet the decoration sits at the START of the point list, so for
# generalization the points run parent -> child.
REL_STYLES = {
    "<|--": ("lt=<<-", "parent_first"),
    "<|..": ("lt=<<.", "parent_first"),
    "..|>": ("lt=<<.", "child_first"),
    "--|>": ("lt=<<-", "child_first"),
    "*--": ("lt=<<<<<-", "whole_first"),
    "o--": ("lt=<<<<-", "whole_first"),
    "-->": ("lt=<-", "target_first"),
    "..>": ("lt=<.", "target_first"),
    "--": ("lt=-", "plain"),
    "-": ("lt=-", "plain"),
}


def strip_tag(text):
    return re.sub(r"\s*\$\w+\s*$", "", text.strip())


def parse_model(path):
    """Return (classes, relations).

    classes: dict name -> {kind, stereotype, tag, members[]}
    relations: list of dicts {a, b, token, m1, m2, label}
    """
    classes, relations = {}, []
    current, tag_of_current = None, None
    in_note = False

    decl = re.compile(
        r"^\s*(?P<abstract>abstract\s+)?"
        r"(?P<kind>class|interface|enum)\s+"
        r"(?P<name>\"[^\"]+\"|[A-Za-z_][\w<>]*)"
        r"(?:\s+as\s+(?P<alias>[A-Za-z_]\w*))?"
        r"(?:\s+<<(?P<stereo>[^>]+)>>)?"
        r"(?:\s+\$(?P<tag>\w+))?"
        r"(?:\s+<<(?P<stereo2>[^>]+)>>)?"
        r"\s*(?P<brace>\{)?\s*$"
    )

    for raw in open(path, encoding="utf-8"):
        line = raw.rstrip("\n")
        stripped = line.strip()

        if stripped.startswith("note "):
            in_note = True
            continue
        if in_note:
            if stripped.startswith("end note"):
                in_note = False
            continue
        if not stripped or stripped.startswith("'"):
            continue

        if current is not None:
            if stripped == "}":
                current = None
                continue
            classes[current]["members"].append(stripped)
            continue

        m = decl.match(line)
        if m and not stripped.startswith("package"):
            name = m.group("alias") or m.group("name").strip('"')
            display = m.group("name").strip('"')
            kind = m.group("kind")
            if m.group("abstract"):
                kind = "abstract"
            stereo = m.group("stereo") or m.group("stereo2")
            tag = m.group("tag")
            classes[name] = {
                "display": display,
                "kind": kind,
                "stereotype": stereo,
                "tag": tag,
                "members": [],
            }
            if m.group("brace"):
                current = name
            continue

        rel = parse_relation(stripped)
        if rel:
            relations.append(rel)

    return classes, relations


REL_LINE = re.compile(
    r"^(?P<a>\"[^\"]+\"|[A-Za-z_]\w*)\s*"
    r"(?:\"(?P<m1>[^\"]+)\"\s*)?"
    r"(?P<token><\|--|<\|\.\.|\.\.\|>|--\|>|\*--|o--|-->|\.\.>|--|-)\s*"
    r"(?:\"(?P<m2>[^\"]+)\"\s*)?"
    r"(?P<b>\"[^\"]+\"|[A-Za-z_]\w*)\s*"
    r"(?::\s*(?P<label>.+))?$"
)


def parse_relation(line):
    if "[hidden]" in line or line.startswith("@") or line.startswith("!"):
        return None
    m = REL_LINE.match(line)
    if not m:
        return None
    return {
        "a": m.group("a").strip('"'),
        "b": m.group("b").strip('"'),
        "token": m.group("token"),
        "m1": m.group("m1"),
        "m2": m.group("m2"),
        "label": (m.group("label") or "").strip(),
    }


def panel_text(name, info):
    """UMLet class body text."""
    lines = []
    if info["kind"] == "interface":
        lines.append("<<interface>>")
    elif info["kind"] == "enum":
        lines.append("<<enum>>")
    if info["stereotype"]:
        lines.append("<<%s>>" % info["stereotype"])
    title = info["display"]
    if info["kind"] == "abstract":
        title = "/%s/" % title
    lines.append(title)

    if info["kind"] == "enum":
        # enum constants have no visibility markers; list them as they are
        if info["members"]:
            lines.append("--")
            lines.extend(m.strip() for m in info["members"])
        return "\n".join(lines)

    fields = [m for m in info["members"] if is_field(m)]
    methods = [m for m in info["members"] if not is_field(m)]
    if fields:
        lines.append("--")
        lines.extend(to_umlet_member(f) for f in fields)
    if methods:
        lines.append("--")
        lines.extend(to_umlet_member(m) for m in methods)
    if not fields and not methods:
        lines.append("--")
    return "\n".join(lines)


def is_field(member):
    if member.startswith("+") or member.startswith("-") or member.startswith("#"):
        return "(" not in member
    return "(" not in member


def to_umlet_member(member):
    raw = member.strip()
    is_static = "{static}" in raw
    is_abstract = "{abstract}" in raw
    m = raw.replace("{static}", "").replace("{abstract}", "").replace("{final}", "")
    m = re.sub(r"\s+", " ", m).strip()
    if is_abstract:
        m = "/%s/" % m          # UMLet italics
    if is_static:
        m = "_%s_" % m          # UMLet underline
    return m


def size_for(text):
    lines = text.split("\n")
    w = max(len(l) for l in lines) * CHAR_W + PAD_W
    h = len(lines) * LINE_H + PAD_H
    w = int(round(w / GRID) * GRID)
    h = int(round(h / GRID) * GRID)
    return max(w, 100), max(h, 40)


def layout(nodes, edges):
    """Graphviz layout. nodes: name -> (w, h) in px. Returns name -> (x, y) top left."""
    lines = ["digraph G {", "  graph [rankdir=TB, nodesep=0.6, ranksep=0.9, splines=false];",
             "  node [shape=box, fixedsize=true];"]
    for name, (w, h) in nodes.items():
        lines.append('  "%s" [width=%.3f, height=%.3f];' % (name, w / 72.0, h / 72.0))
    for a, b in edges:
        if a in nodes and b in nodes:
            lines.append('  "%s" -> "%s";' % (a, b))
    lines.append("}")
    dot_src = "\n".join(lines)

    out = subprocess.run(["dot", "-Tplain"], input=dot_src, capture_output=True,
                         text=True, check=True).stdout

    pos, scale, height = {}, 72.0, 0.0
    for line in out.splitlines():
        parts = line.split()
        if parts[0] == "graph":
            height = float(parts[3])
        elif parts[0] == "node":
            name = parts[1].strip('"')
            cx, cy = float(parts[2]), float(parts[3])
            w, h = nodes[name]
            x = cx * scale - w / 2.0
            y = (height - cy) * scale - h / 2.0
            pos[name] = (int(round(x / GRID) * GRID), int(round(y / GRID) * GRID))
    minx = min(p[0] for p in pos.values()) if pos else 0
    miny = min(p[1] for p in pos.values()) if pos else 0
    return {n: (x - minx + 20, y - miny + 20) for n, (x, y) in pos.items()}


def anchors(box_a, box_b):
    """Point on each box border along the line joining their centres."""
    (ax, ay, aw, ah), (bx, by, bw, bh) = box_a, box_b
    acx, acy = ax + aw / 2.0, ay + ah / 2.0
    bcx, bcy = bx + bw / 2.0, by + bh / 2.0
    return border_point(ax, ay, aw, ah, bcx, bcy), border_point(bx, by, bw, bh, acx, acy)


def border_point(x, y, w, h, tx, ty):
    cx, cy = x + w / 2.0, y + h / 2.0
    dx, dy = tx - cx, ty - cy
    if dx == 0 and dy == 0:
        return cx, cy
    sx = (w / 2.0) / abs(dx) if dx else float("inf")
    sy = (h / 2.0) / abs(dy) if dy else float("inf")
    s = min(sx, sy)
    return cx + dx * s, cy + dy * s


def relation_element(p1, p2, style, m1, m2, label):
    x1, y1 = p1
    x2, y2 = p2
    minx, miny = min(x1, x2) - 10, min(y1, y2) - 10
    w, h = abs(x2 - x1) + 20, abs(y2 - y1) + 20
    minx, miny = int(round(minx / GRID) * GRID), int(round(miny / GRID) * GRID)
    w, h = max(int(round(w / GRID) * GRID), 20), max(int(round(h / GRID) * GRID), 20)
    rel = [x1 - minx, y1 - miny, x2 - minx, y2 - miny]
    # UMLet parses one property per line: lt=, m1=, m2= must not share a line
    panel = [style]
    if m1:
        panel.append("m1=%s" % m1)
    if m2:
        panel.append("m2=%s" % m2)
    if label:
        panel.append(label)
    text = "\n".join(panel)
    return (minx, miny, w, h), text, ";".join("%.1f" % v for v in rel)


def write_uxf(path, elements, zoom=ZOOM):
    diagram = ET.Element("diagram", {"program": "umlet", "version": "15.1"})
    ET.SubElement(diagram, "zoom_level").text = str(zoom)
    for eid, (x, y, w, h), panel, additional in elements:
        el = ET.SubElement(diagram, "element")
        ET.SubElement(el, "id").text = eid
        coords = ET.SubElement(el, "coordinates")
        for tag, val in (("x", x), ("y", y), ("w", w), ("h", h)):
            ET.SubElement(coords, tag).text = str(int(val))
        ET.SubElement(el, "panel_attributes").text = panel
        ET.SubElement(el, "additional_attributes").text = additional
    ET.indent(diagram, space="  ")
    xml = ET.tostring(diagram, encoding="unicode")
    with open(path, "w", encoding="utf-8") as fh:
        fh.write('<?xml version="1.0" encoding="UTF-8" standalone="no"?>\n')
        fh.write(xml)
        fh.write("\n")


def build_view(name, tags, classes, relations):
    members = {n: i for n, i in classes.items() if i["tag"] in tags}
    rels = [r for r in relations if r["a"] in members and r["b"] in members]

    texts = {n: panel_text(n, i) for n, i in members.items()}
    sizes = {n: size_for(t) for n, t in texts.items()}
    edges = [(r["a"], r["b"]) for r in rels]
    pos = layout(sizes, edges)

    elements = []
    boxes = {}
    for n in members:
        x, y = pos[n]
        w, h = sizes[n]
        boxes[n] = (x, y, w, h)
        elements.append(("UMLClass", (x, y, w, h), texts[n], ""))

    for r in rels:
        style, order = REL_STYLES.get(r["token"], ("lt=-", "plain"))
        a, b = r["a"], r["b"]
        m1, m2 = r["m1"], r["m2"]
        if order in ("parent_first", "whole_first"):
            first, second = a, b
        elif order in ("child_first", "target_first"):
            first, second = b, a
            m1, m2 = m2, m1
        else:
            first, second = a, b
        p1, p2 = anchors(boxes[first], boxes[second])
        coords, panel, additional = relation_element(p1, p2, style, m1, m2, r["label"])
        elements.append(("Relation", coords, panel, additional))

    out = os.path.join(OUTDIR, "class-%s.uxf" % name)
    write_uxf(out, elements)
    return out, len(members), len(rels)


def main():
    os.makedirs(OUTDIR, exist_ok=True)
    classes, relations = parse_model(MODEL)
    print("parsed %d classes, %d relations" % (len(classes), len(relations)))
    for view, tags in VIEWS.items():
        path, nc, nr = build_view(view, tags, classes, relations)
        print("%-28s %3d classes %3d relations -> %s" % (view, nc, nr, os.path.relpath(path, ROOT)))


if __name__ == "__main__":
    main()
