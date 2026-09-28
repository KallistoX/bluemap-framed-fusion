package io.github.kallistox.framedfusion.framed;

import de.bluecolored.bluemap.core.world.BlockState;
import de.bluecolored.bluemap.core.world.mca.blockentity.MCABlockEntity;
import de.bluecolored.bluenbt.NBTName;

import java.util.Map;

/**
 * Block entity of a Framed Block: its camouflage ({@code camo}) and, for double blocks, the camouflage of the second
 * part ({@code camo_two}), each as {@code {type: "framedblocks:block", state: {Name, Properties}}}.
 */
@SuppressWarnings({"unused", "FieldMayBeFinal"})
public class FramedBlockEntity extends MCABlockEntity {

    private static final String BLOCK_CAMO = "framedblocks:block";

    @NBTName("camo") private Camo camo;
    @NBTName("camo_two") private Camo camoTwo;

    private transient BlockState[] states;

    public FramedBlockEntity() {}

    /** Camouflage of part 0 (first) or 1 (second); {@code null} if the part has no block camouflage. */
    public BlockState camo(int part) {
        if (states == null) states = new BlockState[]{state(camo), state(camoTwo)};
        return states[part];
    }

    private static BlockState state(Camo camo) {
        if (camo == null || camo.state == null || camo.state.name == null) return null;
        if (camo.type != null && !camo.type.equals(BLOCK_CAMO)) return null;
        return new BlockState(camo.state.name, camo.state.properties != null ? camo.state.properties : Map.of());
    }

    public static class Camo {
        @NBTName("type") private String type;
        @NBTName("state") private State state;
    }

    public static class State {
        @NBTName("Name") private String name;
        @NBTName("Properties") private Map<String, String> properties;
    }
}
