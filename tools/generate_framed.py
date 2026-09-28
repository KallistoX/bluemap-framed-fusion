#!/usr/bin/env python3
"""Generates the Framed Blocks resources of the addon: blockstates that route Framed Blocks to the addon's
renderer, and box models for their shapes.

Framed Blocks builds its shapes in code. The boxes below follow its shape definitions (Framed Blocks 10.6, class
names in the comments); double blocks are the pairs of single blocks Framed Blocks defines for them
(calculateBlockPair). Stairs use vanilla's rotation table, read from a Minecraft 1.21.1 client jar, as Framed's
stairs share vanilla's shape; walls use vanilla's wall blockstate and templates.

    python3 tools/generate_framed.py <minecraft-client-1.21.1.jar>

Model faces use the texture variable #camo (first camouflage) or #camo_two (second camouflage of double blocks);
without camouflage the renderer shows the empty frame.
"""
import itertools, json, os, shutil, sys, zipfile

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets")
RENDERER = "framedfusion:framed"
FRAME = "framedblocks:block/framed_block"
DIRS = ["north", "east", "south", "west"]
ROT = {"north": 0, "east": 90, "south": 180, "west": 270}
OPP = {"north": "south", "south": "north", "east": "west", "west": "east"}
CW = {"north": "east", "east": "south", "south": "west", "west": "north"}
CCW = {v: k for k, v in CW.items()}
BOOL = ["false", "true"]
SHAPES = ["straight", "inner_left", "inner_right", "outer_left", "outer_right"]
STAIRS_TYPES = ["vertical", "top_fwd", "top_ccw", "top_both", "bottom_fwd", "bottom_ccw", "bottom_both"]

# ---------------------------------------------------------------------------------------------------------------
# Box models (pixels), each written twice: "<name>" with #camo and "<name>_two" with #camo_two

BOXES = {
    "cube": [((0, 0, 0), (16, 16, 16))],
    # CommonShapes.SLAB
    "slab": [((0, 0, 0), (16, 8, 16))],
    "slab_top": [((0, 8, 0), (16, 16, 16))],
    # CommonShapes.PANEL, facing north
    "panel": [((0, 0, 0), (16, 16, 8))],
    # CommonShapes.SLAB_EDGE, facing north
    "slab_edge": [((0, 0, 0), (16, 8, 8))],
    "slab_edge_top": [((0, 8, 0), (16, 16, 8))],
    # CommonShapes.CORNER_PILLAR, facing north
    "corner_pillar": [((0, 0, 0), (8, 16, 8))],
    # SlabCornerShapes, facing north
    "slab_corner": [((0, 0, 0), (8, 8, 8))],
    "slab_corner_top": [((0, 8, 0), (8, 16, 8))],
    # HalfStairsShapes, facing south: (top, right)
    "half_stairs": [((8, 0, 0), (16, 8, 16)), ((8, 8, 8), (16, 16, 16))],
    "half_stairs_right": [((0, 0, 0), (8, 8, 16)), ((0, 8, 8), (8, 16, 16))],
    "half_stairs_top": [((8, 8, 0), (16, 16, 16)), ((8, 0, 8), (16, 8, 16))],
    "half_stairs_top_right": [((0, 8, 0), (8, 16, 16)), ((0, 0, 8), (8, 8, 16))],
    # VerticalHalfStairsShapes, for north (used with the opposite of facing)
    "vertical_half_stairs": [((0, 0, 8), (16, 8, 16)), ((8, 0, 0), (16, 8, 8))],
    "vertical_half_stairs_top": [((0, 8, 8), (16, 16, 16)), ((8, 8, 0), (16, 16, 8))],
    # VerticalStairsShapes / CommonShapes.STRAIGHT_VERTICAL_STAIRS (base direction in VERTICAL_BASE)
    "vertical_stairs_vertical": [((0, 0, 8), (16, 16, 16)), ((8, 0, 0), (16, 16, 8))],
    "vertical_stairs_top_fwd": [((0, 0, 0), (8, 16, 16)), ((8, 0, 0), (16, 8, 8))],
    "vertical_stairs_top_ccw": [((0, 0, 0), (16, 16, 8)), ((0, 0, 8), (8, 8, 16))],
    "vertical_stairs_top_both": [((8, 0, 8), (16, 16, 16)), ((8, 0, 0), (16, 8, 8)), ((0, 0, 8), (8, 8, 16))],
    "vertical_stairs_bottom_fwd": [((0, 0, 0), (8, 16, 16)), ((8, 8, 0), (16, 16, 8))],
    "vertical_stairs_bottom_ccw": [((0, 0, 0), (16, 16, 8)), ((0, 8, 8), (8, 16, 16))],
    "vertical_stairs_bottom_both": [((8, 0, 8), (16, 16, 16)), ((8, 8, 0), (16, 16, 8)), ((0, 8, 8), (8, 16, 16))],
    # Vanilla stairs geometry (facing east, like minecraft:block/stairs); rotations from vanilla's blockstate
    "stairs": [((0, 0, 0), (16, 8, 16)), ((8, 8, 0), (16, 16, 16))],
    "stairs_inner": [((0, 0, 0), (16, 8, 16)), ((8, 8, 0), (16, 16, 16)), ((0, 8, 8), (8, 16, 16))],
    "stairs_outer": [((0, 0, 0), (16, 8, 16)), ((8, 8, 8), (16, 16, 16))],
}
VERTICAL_BASE = {"vertical": "south", "top_fwd": "north", "top_ccw": "north", "top_both": "south",
                 "bottom_fwd": "north", "bottom_ccw": "north", "bottom_both": "south"}

models = {}


def write_models():
    for name, boxes in BOXES.items():
        for suffix, camo in (("", "camo"), ("_two", "camo_two")):
            faces = {d: {"texture": "#" + camo} for d in ("down", "up", "north", "south", "west", "east")}
            models[name + suffix] = {
                "textures": {camo: FRAME, "particle": FRAME},
                "elements": [{"from": list(f), "to": list(t), "faces": faces} for f, t in boxes],
            }


# ---------------------------------------------------------------------------------------------------------------
# Single blocks: properties -> list of (model, x, y)

STAIRS_TABLE = {}  # (facing, half, shape) -> (model, x, y), from vanilla


def load_stairs(client):
    with zipfile.ZipFile(client) as z:
        state = json.loads(z.read("assets/minecraft/blockstates/oak_stairs.json"))
    names = {"minecraft:block/oak_stairs": "stairs", "minecraft:block/oak_stairs_inner": "stairs_inner",
             "minecraft:block/oak_stairs_outer": "stairs_outer"}
    for key, v in state["variants"].items():
        p = dict(kv.split("=") for kv in key.split(","))
        STAIRS_TABLE[(p["facing"], p["half"], p["shape"])] = (names[v["model"]], v.get("x", 0), v.get("y", 0))


def y(direction, base="north"):
    return ROT[direction] - ROT[base]


SINGLE = {
    # name: (properties, function(props) -> [(model, x, y)])
    "framed_cube": ({}, lambda p: [("cube", 0, 0)]),
    "framed_slab": ({"top": BOOL}, lambda p: [("slab_top" if p["top"] == "true" else "slab", 0, 0)]),
    "framed_panel": ({"facing": DIRS}, lambda p: [("panel", 0, y(p["facing"]))]),
    "framed_slab_edge": ({"facing": DIRS, "top": BOOL},
                         lambda p: [("slab_edge_top" if p["top"] == "true" else "slab_edge", 0, y(p["facing"]))]),
    "framed_corner_pillar": ({"facing": DIRS}, lambda p: [("corner_pillar", 0, y(p["facing"]))]),
    "framed_slab_corner": ({"facing": DIRS, "top": BOOL},
                           lambda p: [("slab_corner_top" if p["top"] == "true" else "slab_corner", 0, y(p["facing"]))]),
    "framed_half_stairs": ({"facing": DIRS, "top": BOOL, "right": BOOL},
                           lambda p: [("half_stairs" + ("_top" if p["top"] == "true" else "") + ("_right" if p["right"] == "true" else ""),
                                       0, y(p["facing"], "south"))]),
    "framed_vertical_half_stairs": ({"facing": DIRS, "top": BOOL},
                                    lambda p: [("vertical_half_stairs" + ("_top" if p["top"] == "true" else ""), 0, y(OPP[p["facing"]]))]),
    "framed_vertical_stairs": ({"facing": DIRS, "type": STAIRS_TYPES},
                               lambda p: [("vertical_stairs_" + p["type"], 0, y(p["facing"], VERTICAL_BASE[p["type"]]))]),
    # ThreewayCornerPillarShapes: top=false has the arms at the bottom (top_both), top=true at the top
    "framed_threeway_corner_pillar": ({"facing": DIRS, "top": BOOL},
                                      lambda p: [("vertical_stairs_" + ("bottom_both" if p["top"] == "true" else "top_both"),
                                                  0, y(p["facing"], "south"))]),
    "framed_stairs": ({"facing": DIRS, "half": ["bottom", "top"], "shape": SHAPES},
                      lambda p: [STAIRS_TABLE[(p["facing"], p["half"], p["shape"])]]),
}


def part(block, **props):
    return SINGLE[block][1](props)


def neg(b):
    return "false" if b == "true" else "true"


# Double blocks: properties -> (first part, second part), each a list of (model, x, y)
DOUBLE = {
    # FramedDoubleSlabBlock
    "framed_double_slab": ({}, lambda p: (part("framed_slab", top="false"), part("framed_slab", top="true"))),
    # FramedDoublePanelBlock
    "framed_double_panel": ({"facing": DIRS}, lambda p: (part("framed_panel", facing=p["facing"]),
                                                         part("framed_panel", facing=OPP[p["facing"]]))),
    # FramedDividedPanelBlock
    "framed_divided_panel_horizontal": ({"facing": DIRS}, lambda p: (
        part("framed_slab_edge", facing=p["facing"], top="false"), part("framed_slab_edge", facing=p["facing"], top="true"))),
    "framed_divided_panel_vertical": ({"facing": DIRS}, lambda p: (
        part("framed_corner_pillar", facing=p["facing"]), part("framed_corner_pillar", facing=CW[p["facing"]]))),
    # FramedDoubleHalfStairsBlock
    "framed_double_half_stairs": ({"facing": DIRS, "top": BOOL, "right": BOOL}, lambda p: (
        part("framed_half_stairs", facing=p["facing"], top=p["top"], right=p["right"]),
        part("framed_slab_corner", facing=OPP[p["facing"]] if p["right"] == "true" else CCW[p["facing"]], top=neg(p["top"])))),
    # FramedDoubleStairsBlock
    "framed_double_stairs": ({"facing": DIRS, "half": ["bottom", "top"], "shape": SHAPES}, lambda p: (
        part("framed_stairs", **p), double_stairs_two(p))),
    # FramedVerticalDoubleStairsBlock
    "framed_vertical_double_stairs": ({"facing": DIRS, "type": STAIRS_TYPES}, lambda p: (
        part("framed_vertical_stairs", **p), vertical_double_two(p))),
    # FramedSlicedStairsSlabBlock
    "framed_sliced_stairs_slab": ({"facing": DIRS, "half": ["bottom", "top"], "shape": SHAPES}, lambda p: (
        part("framed_slab", top="true" if p["half"] == "top" else "false"), sliced_slab_two(p))),
    # FramedSlicedStairsPanelBlock
    "framed_sliced_stairs_panel": ({"facing": DIRS, "half": ["bottom", "top"], "shape": SHAPES}, lambda p: sliced_panel(p)),
}


def double_stairs_two(p):
    f, top = p["facing"], "true" if p["half"] == "top" else "false"
    return {
        "straight": lambda: part("framed_slab_edge", facing=OPP[f], top=neg(top)),
        "inner_left": lambda: part("framed_slab_corner", facing=OPP[f], top=neg(top)),
        "inner_right": lambda: part("framed_slab_corner", facing=CCW[f], top=neg(top)),
        "outer_left": lambda: part("framed_vertical_half_stairs", facing=OPP[f], top=neg(top)),
        "outer_right": lambda: part("framed_vertical_half_stairs", facing=CCW[f], top=neg(top)),
    }[p["shape"]]()


def vertical_double_two(p):
    f = p["facing"]
    return {
        "vertical": lambda: part("framed_corner_pillar", facing=OPP[f]),
        "top_fwd": lambda: part("framed_half_stairs", facing=OPP[f], top="true", right="false"),
        "top_ccw": lambda: part("framed_half_stairs", facing=CW[f], top="true", right="true"),
        "top_both": lambda: part("framed_vertical_stairs", facing=OPP[f], type="bottom_both"),
        "bottom_fwd": lambda: part("framed_half_stairs", facing=OPP[f], top="false", right="false"),
        "bottom_ccw": lambda: part("framed_half_stairs", facing=CW[f], top="false", right="true"),
        "bottom_both": lambda: part("framed_vertical_stairs", facing=OPP[f], type="top_both"),
    }[p["type"]]()


def sliced_slab_two(p):
    f, top = p["facing"], "true" if p["half"] == "top" else "false"
    return {
        "straight": lambda: part("framed_slab_edge", facing=f, top=neg(top)),
        "inner_left": lambda: part("framed_vertical_half_stairs", facing=f, top=neg(top)),
        "inner_right": lambda: part("framed_vertical_half_stairs", facing=CW[f], top=neg(top)),
        "outer_left": lambda: part("framed_slab_corner", facing=f, top=neg(top)),
        "outer_right": lambda: part("framed_slab_corner", facing=CW[f], top=neg(top)),
    }[p["shape"]]()


def sliced_panel(p):
    f, top = p["facing"], "true" if p["half"] == "top" else "false"
    return {
        "straight": lambda: (part("framed_panel", facing=f), part("framed_slab_edge", facing=OPP[f], top=top)),
        "inner_left": lambda: (part("framed_vertical_stairs", facing=f, type="vertical"),
                               part("framed_slab_corner", facing=OPP[f], top=top)),
        "inner_right": lambda: (part("framed_vertical_stairs", facing=CW[f], type="vertical"),
                                part("framed_slab_corner", facing=CCW[f], top=top)),
        "outer_left": lambda: (part("framed_corner_pillar", facing=f),
                               part("framed_vertical_half_stairs", facing=OPP[f], top=top)),
        "outer_right": lambda: (part("framed_corner_pillar", facing=CW[f]),
                                part("framed_vertical_half_stairs", facing=CCW[f], top=top)),
    }[p["shape"]]()



# ---------------------------------------------------------------------------------------------------------------


def variant(model, x, y, two=False):
    v = {"renderer": RENDERER, "model": "framedfusion:block/" + model + ("_two" if two else "")}
    x, y = x % 360, y % 360
    if x: v["x"] = x
    if y: v["y"] = y
    if x or y: v["uvlock"] = True
    return v


def combos(props):
    keys = list(props)
    for values in itertools.product(*(props[k] for k in keys)):
        yield dict(zip(keys, values))


def key(p):
    return ",".join(f"{k}={v}" for k, v in p.items())


def blockstates():
    out = {}
    for name, (props, fn) in SINGLE.items():
        out[name] = {"variants": {key(p): variant(*fn(p)[0]) for p in combos(props)}}
    for name, (props, fn) in DOUBLE.items():
        parts = []
        for p in combos(props):
            first, second = fn(p)
            when = {"when": dict(p)} if p else {}
            parts += [{**when, "apply": variant(*m)} for m in first]
            parts += [{**when, "apply": variant(*m, two=True)} for m in second]
        out[name] = {"multipart": parts}
    return out


# Shapes with sloped faces come from the addon's code (FramedShapes); their blockstate only picks the renderer.
CODED = ["framed_slope", "framed_double_slope", "framed_slope_edge", "framed_elevated_slope_edge",
         "framed_elevated_double_slope_edge", "framed_slope_panel", "framed_extended_slope_panel",
         "framed_extended_double_slope_panel", "framed_compound_slope_panel", "framed_prism"]


def wall(client):
    with zipfile.ZipFile(client) as z:
        state = json.loads(z.read("assets/minecraft/blockstates/cobblestone_wall.json"))
    names = {"minecraft:block/cobblestone_wall_post": "minecraft:block/template_wall_post",
             "minecraft:block/cobblestone_wall_side": "minecraft:block/template_wall_side",
             "minecraft:block/cobblestone_wall_side_tall": "minecraft:block/template_wall_side_tall"}
    parts = []
    for p in state["multipart"]:
        apply = {"renderer": RENDERER, **p["apply"], "model": names[p["apply"]["model"]]}
        parts.append({**({"when": p["when"]} if "when" in p else {}), "apply": apply})
    return {"multipart": parts}


def main():
    client = sys.argv[1]
    load_stairs(client)
    write_models()
    states = blockstates()
    states["framed_wall"] = wall(client)  # FramedWallBlock: vanilla wall shape
    models["empty"] = {"textures": {"particle": FRAME}, "elements": []}
    for name in CODED:
        states[name] = {"variants": {"": {"renderer": RENDERER, "model": "framedfusion:block/empty"}}}

    for sub in ("framedblocks/blockstates", "framedfusion/models/block"):
        shutil.rmtree(os.path.join(OUT, sub), ignore_errors=True)
        os.makedirs(os.path.join(OUT, sub))
    for name, data in sorted(states.items()):
        with open(os.path.join(OUT, "framedblocks", "blockstates", name + ".json"), "w") as f:
            json.dump(data, f, indent=1)
            f.write("\n")
    for name, data in sorted(models.items()):
        with open(os.path.join(OUT, "framedfusion", "models", "block", name + ".json"), "w") as f:
            json.dump(data, f, indent=1)
            f.write("\n")
    print(f"{len(states)} blockstates, {len(models)} models")


if __name__ == "__main__":
    main()
