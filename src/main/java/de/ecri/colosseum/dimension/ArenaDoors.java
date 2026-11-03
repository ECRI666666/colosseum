package de.ecri.colosseum.dimension;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

/**
 * Die Türen wie bei CoD Zombies. Man startet im Vorhof, jede Tür kostet Punkte und öffnet den nächsten Bereich.
 * Die beiden Abkürzungen verbinden nur Bereiche, die schon offen sind, und schaffen Rundwege.
 */
public final class ArenaDoors {
    public record DoorButton(BlockPos pos, Direction facing) {}
    public record Door(String name, int zoneA, int zoneB, int price, boolean shortcut, List<BlockPos> cells, List<DoorButton> buttons) {
        public net.minecraft.network.chat.Component label() { return de.ecri.colosseum.Lang.t(name); }
    }

    private static List<BlockPos> cells(int x1, int y1, int z1, int x2, int y2, int z2) {
        List<BlockPos> out = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(x1, y1, z1, x2, y2, z2)) out.add(pos.immutable());
        return out;
    }

    /** Boden-Durchgang durch die Trennwand, dazu der Durchgang auf Dachhöhe, wo eine Brücke die Wand kreuzt. */
    private static List<BlockPos> withBridge(int wallX, int groundZ1, int groundZ2, int bridgeZ1, int bridgeZ2) {
        List<BlockPos> out = cells(wallX, 65, groundZ1, wallX, 67, groundZ2);
        out.addAll(cells(wallX, 69, bridgeZ1, wallX, 71, bridgeZ2));
        return out;
    }

    public static final Door NORD = new Door("door.north_court", ArenaLayout.ZONE_START, ArenaLayout.ZONE_NORTH, 750, false,
        cells(-3, 65, 4, 3, 67, 4),
        List.of(new DoorButton(new BlockPos(-4, 66, 5), Direction.SOUTH), new DoorButton(new BlockPos(-4, 66, -5), Direction.NORTH)));
    public static final Door WEST = new Door("door.pillar_hall", ArenaLayout.ZONE_NORTH, ArenaLayout.ZONE_WEST, 1500, false,
        withBridge(-21, -8, -6, -9, -5),
        List.of(new DoorButton(new BlockPos(-20, 66, -9), Direction.EAST), new DoorButton(new BlockPos(-22, 66, -9), Direction.WEST)));
    public static final Door EAST = new Door("door.the_pit", ArenaLayout.ZONE_NORTH, ArenaLayout.ZONE_EAST, 3000, false,
        withBridge(21, -8, -6, 5, 9),
        List.of(new DoorButton(new BlockPos(20, 66, -9), Direction.WEST), new DoorButton(new BlockPos(22, 66, -9), Direction.EAST)));
    public static final Door WEST_SHORTCUT = new Door("door.shortcut", ArenaLayout.ZONE_START, ArenaLayout.ZONE_WEST, 500, true,
        cells(-21, 65, 6, -21, 67, 8),
        List.of(new DoorButton(new BlockPos(-20, 66, 9), Direction.EAST), new DoorButton(new BlockPos(-22, 66, 9), Direction.WEST)));
    public static final Door EAST_SHORTCUT = new Door("door.shortcut", ArenaLayout.ZONE_START, ArenaLayout.ZONE_EAST, 500, true,
        cells(21, 65, 6, 21, 67, 8),
        List.of(new DoorButton(new BlockPos(20, 66, 9), Direction.WEST), new DoorButton(new BlockPos(22, 66, 9), Direction.EAST)));

    public static final List<Door> ALL = List.of(NORD, WEST, EAST, WEST_SHORTCUT, EAST_SHORTCUT);

    public static Door at(BlockPos pos) {
        for (Door door : ALL) for (DoorButton button : door.buttons()) if (button.pos().equals(pos)) return door;
        return null;
    }

    public static void set(ServerLevel level, Door door, boolean open) {
        for (BlockPos pos : door.cells()) level.setBlock(pos, open ? Blocks.AIR.defaultBlockState() : Blocks.IRON_BARS.defaultBlockState(), 18);
    }

    public static void closeAll(ServerLevel level) {
        for (Door door : ALL) set(level, door, false);
    }

    private ArenaDoors() {}
}
