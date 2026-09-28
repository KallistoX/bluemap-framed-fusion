package io.github.kallistox.framedfusion.fusion;

import static io.github.kallistox.framedfusion.fusion.ConnectionDirection.*;

import java.util.Locale;

/**
 * Tile layouts of Fusion's connecting textures. A texture is a grid of {@code width x height} tiles; which tile a
 * face shows depends on the connection mask (one bit per {@link ConnectionDirection}, set when the neighbour in
 * that direction connects). The tile positions follow Fusion's documented layouts.
 */
public enum Layout {
    FULL(8, 6),
    HORIZONTAL(4, 1),
    VERTICAL(1, 4),
    SIMPLE(4, 4),
    /** Five whole tiles; every quarter of a face picks its tile from its two sides and the corner between them. */
    PIECED(5, 1);

    public final int width, height;

    Layout(int width, int height) {
        this.width = width;
        this.height = height;
    }

    /** Layout for Fusion's {@code "layout"} value; {@code null} if unsupported (compact, overlay). */
    public static Layout byName(String name) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "full" -> FULL;
            case "horizontal" -> HORIZONTAL;
            case "vertical" -> VERTICAL;
            case "simple" -> SIMPLE;
            case "pieced" -> PIECED;
            default -> null;
        };
    }

    /** Tile index ({@code x + y * width}) for a whole face. Not for {@link #PIECED}, see {@link #piecedTile}. */
    public int tile(int mask) {
        boolean t = has(mask, TOP), r = has(mask, RIGHT), b = has(mask, BOTTOM), l = has(mask, LEFT);
        return switch (this) {
            case HORIZONTAL -> l ? (r ? 2 : 3) : (r ? 1 : 0);
            case VERTICAL -> (t ? (b ? 2 : 3) : (b ? 1 : 0)) * width;
            case SIMPLE -> index(simpleTile(t, r, b, l));
            case FULL -> index(fullTile(mask, t, r, b, l));
            case PIECED -> throw new UnsupportedOperationException("pieced layout picks a tile per quarter");
        };
    }

    /**
     * Tile for one quarter of a face in the {@link #PIECED} layout: the quarter at the top or bottom, left or right.
     * Tiles: 0 alone, 1 connected all around, 2 vertical, 3 horizontal, 4 all sides but not the corner.
     */
    public static int piecedTile(boolean top, boolean left, int mask) {
        boolean side = has(mask, left ? LEFT : RIGHT);
        boolean vertical = has(mask, top ? TOP : BOTTOM);
        boolean corner = has(mask, top ? (left ? TOP_LEFT : TOP_RIGHT) : (left ? BOTTOM_LEFT : BOTTOM_RIGHT));
        if (side && vertical) return corner ? 1 : 4;
        if (side) return 3;
        if (vertical) return 2;
        return 0;
    }

    private int index(int[] xy) {
        return xy[0] + xy[1] * width;
    }

    private static boolean has(int mask, ConnectionDirection d) {
        return (mask & d.bit()) != 0;
    }

    private static int[] simpleTile(boolean t, boolean r, boolean b, boolean l) {
        int sides = (t ? 1 : 0) + (r ? 1 : 0) + (b ? 1 : 0) + (l ? 1 : 0);
        return switch (sides) {
            case 0 -> xy(0, 0);
            case 1 -> l ? xy(3, 0) : t ? xy(3, 1) : r ? xy(2, 1) : xy(2, 0);
            case 2 -> l && r ? xy(0, 1) : t && b ? xy(1, 1)
                    : l && t ? xy(3, 3) : t && r ? xy(2, 3) : r && b ? xy(2, 2) : xy(3, 2);
            case 3 -> !l ? xy(0, 2) : !t ? xy(1, 2) : !r ? xy(1, 3) : xy(0, 3);
            default -> xy(1, 0);
        };
    }

    /** The 47 tiles of the full layout: corners only count where both of their sides connect. */
    private static int[] fullTile(int mask, boolean t, boolean r, boolean b, boolean l) {
        boolean tl = has(mask, TOP_LEFT), tr = has(mask, TOP_RIGHT), br = has(mask, BOTTOM_RIGHT), bl = has(mask, BOTTOM_LEFT);
        int sides = (t ? 1 : 0) + (r ? 1 : 0) + (b ? 1 : 0) + (l ? 1 : 0);
        switch (sides) {
            case 0:
                return xy(0, 0);
            case 1:
                return l ? xy(3, 0) : t ? xy(0, 3) : r ? xy(1, 0) : xy(0, 1);
            case 2:
                if (l && r) return xy(2, 0);
                if (t && b) return xy(0, 2);
                if (l && t) return tl ? xy(3, 3) : xy(5, 1);
                if (t && r) return tr ? xy(1, 3) : xy(4, 1);
                if (r && b) return br ? xy(1, 1) : xy(4, 0);
                return bl ? xy(3, 1) : xy(5, 0);
            case 3:
                // the side that is missing, then the two corners on the connected half
                if (!l) return pick(tr, br, xy(1, 2), xy(4, 2), xy(6, 2), xy(6, 0));
                if (!t) return pick(bl, br, xy(2, 1), xy(7, 2), xy(5, 2), xy(7, 0));
                if (!r) return pick(tl, bl, xy(3, 2), xy(7, 3), xy(5, 3), xy(7, 1));
                return pick(tl, tr, xy(2, 3), xy(4, 3), xy(6, 3), xy(6, 1));
            default:
                int corners = (tl ? 1 : 0) + (tr ? 1 : 0) + (br ? 1 : 0) + (bl ? 1 : 0);
                switch (corners) {
                    case 4: return xy(2, 2);
                    case 3: return !tl ? xy(7, 5) : !tr ? xy(6, 5) : !bl ? xy(7, 4) : xy(6, 4);
                    case 2:
                        if (tr && bl) return xy(0, 4);
                        if (tl && br) return xy(0, 5);
                        if (bl && br) return xy(3, 4);
                        if (tl && bl) return xy(3, 5);
                        if (tl && tr) return xy(2, 5);
                        return xy(2, 4);
                    case 1: return tl ? xy(5, 5) : tr ? xy(4, 5) : br ? xy(4, 4) : xy(5, 4);
                    default: return xy(1, 4);
                }
        }
    }

    /** {@code both}, only {@code a}, only {@code b}, neither. */
    private static int[] pick(boolean a, boolean b, int[] both, int[] onlyA, int[] onlyB, int[] neither) {
        return a && b ? both : a ? onlyA : b ? onlyB : neither;
    }

    private static int[] xy(int x, int y) {
        return new int[]{x, y};
    }
}
