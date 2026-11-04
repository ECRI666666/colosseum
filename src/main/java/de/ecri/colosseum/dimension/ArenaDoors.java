package de.ecri.colosseum.dimension;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
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
        if (open) for (BlockPos pos : door.cells()) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 18);
        else placeBars(level, door.cells());
        setSkyWall(level, door, open);
    }

    /**
     * Die unsichtbare Wand über einer Tür: Sie hält nur auf, solange die Tür zu ist. Danach darf man auch
     * über die Grenze fliegen und von oben hinunter.
     */
    public static List<BlockPos> skyWall(Door door) {
        List<BlockPos> out = new ArrayList<>();
        if (door == NORD) {
            // Vom Vorhof nicht aufs Dach des Mittelteils. Dessen Brücke nach Osten führt bei x = 20 durch den Vorhof und bleibt frei.
            for (int x = -ArenaLayout.ZONE_WALL_X + 1; x <= 19; x++) column(out, x, 5, 68);
            for (int z = 6; z <= 10; z++) column(out, 19, z, 69);
            column(out, 20, 10, 69);
        }
        return out;
    }

    /** Über den Trennmauern bleibt die Wand immer stehen, sonst könnte man oben auf der Mauerkante stehen. */
    public static List<BlockPos> dividerWalls() {
        List<BlockPos> out = new ArrayList<>();
        for (int x : new int[] {-ArenaLayout.ZONE_WALL_X, ArenaLayout.ZONE_WALL_X}) {
            for (int z = -ArenaLayout.B; z <= ArenaLayout.B; z++) if (ArenaLayout.ring(x, z) == 0) column(out, x, z, 73);
        }
        return out;
    }

    private static void column(List<BlockPos> out, int x, int z, int from) {
        for (int y = from; y <= ArenaLayout.SKY_LIMIT; y++) out.add(new BlockPos(x, y, z));
    }

    private static void setSkyWall(ServerLevel level, Door door, boolean open) {
        for (BlockPos pos : skyWall(door)) {
            if (open && level.getBlockState(pos).is(Blocks.BARRIER)) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 18);
            else if (!open && level.getBlockState(pos).isAir()) level.setBlock(pos, Blocks.BARRIER.defaultBlockState(), 18);
        }
    }

    /**
     * Setzt ein geschlossenes Gitter. Erst alle Felder, dann die Verbindungen: Ohne sie steht jedes Feld als
     * dünner Stab für sich, und zwischen zwei Stäben passt ein Spieler hindurch.
     */
    public static void placeBars(ServerLevel level, List<BlockPos> cells) {
        for (BlockPos pos : cells) level.setBlock(pos, Blocks.IRON_BARS.defaultBlockState(), 18);
        for (BlockPos pos : cells) level.setBlock(pos, Block.updateFromNeighbourShapes(Blocks.IRON_BARS.defaultBlockState(), level, pos), 18);
    }

    public static void closeAll(ServerLevel level) {
        for (Door door : ALL) set(level, door, false);
    }

    private ArenaDoors() {}
}
