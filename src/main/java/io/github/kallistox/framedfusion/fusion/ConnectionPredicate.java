package io.github.kallistox.framedfusion.fusion;

import de.bluecolored.bluemap.core.world.BlockState;

/**
 * Whether a face connects to one of its neighbours. Built from Fusion's {@code "connections"} json by
 * {@link Predicates}.
 */
@FunctionalInterface
public interface ConnectionPredicate {

    ConnectionPredicate NEVER = c -> false;
    ConnectionPredicate ALWAYS = c -> true;
    /** What Fusion uses when neither the model nor the texture says anything. */
    ConnectionPredicate SAME_STATE = c -> c.self().equals(c.other());

    boolean test(Context context);

    /**
     * @param self      the block the face belongs to (its appearance, e.g. the camouflage of a Framed Block)
     * @param other     the neighbour (its appearance)
     * @param inFront   the block in front of the neighbour, seen from the face
     * @param direction where the neighbour sits, relative to the face's default orientation
     */
    record Context(BlockState self, BlockState other, BlockState inFront, ConnectionDirection direction) {}
}
