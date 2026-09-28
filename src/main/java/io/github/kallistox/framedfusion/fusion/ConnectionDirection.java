package io.github.kallistox.framedfusion.fusion;

import java.util.Locale;

/**
 * The eight neighbours of a face, as seen on the face: {@code right} and {@code up} count steps along the face's
 * right and up axes. The names match Fusion's json ({@code "top"}, {@code "top_left"}, ...).
 */
public enum ConnectionDirection {
    TOP(0, 1),
    TOP_RIGHT(1, 1),
    RIGHT(1, 0),
    BOTTOM_RIGHT(1, -1),
    BOTTOM(0, -1),
    BOTTOM_LEFT(-1, -1),
    LEFT(-1, 0),
    TOP_LEFT(-1, 1);

    private static final ConnectionDirection[] VALUES = values();

    public final int right, up;

    ConnectionDirection(int right, int up) {
        this.right = right;
        this.up = up;
    }

    /** Bit of this direction in a connection mask, see {@link Layout}. */
    public int bit() {
        return 1 << ordinal();
    }

    public static ConnectionDirection of(int right, int up) {
        for (ConnectionDirection d : VALUES) {
            if (d.right == right && d.up == up) return d;
        }
        throw new IllegalArgumentException("no neighbour at " + right + "," + up);
    }

    public static ConnectionDirection byName(String name) {
        return valueOf(name.toUpperCase(Locale.ROOT));
    }
}
