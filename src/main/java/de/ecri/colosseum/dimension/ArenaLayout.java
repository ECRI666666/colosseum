package de.ecri.colosseum.dimension;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Alle Maße des Bauwerks an einer Stelle. Das Oval liegt um (0,0), die Lobby schließt im Süden an. */
public final class ArenaLayout {
    public static final int FLOOR_Y = 64, STAND_Y = 65;
    public static final int A = 48, B = 34, TRIBUNE = 14;
    /** Höchste begehbare Stelle ist das Dach (Oberkante 69). Die Mauer bleibt deutlich darüber. */
    public static final int WALL_TOP = FLOOR_Y + 14, MOB_CEILING = FLOOR_Y + 10, MOB_FLOOR = FLOOR_Y - 4;

    public static final int GATE_Z = B + 1, GATE_HEIGHT = 5;
    public static final int LOBBY_Z0 = B + 15, LOBBY_Z1 = LOBBY_Z0 + 26, LOBBY_X = 7, LOBBY_TOP = 70;
    public static final Vec3 CENTER = new Vec3(0.5, STAND_Y, 0.5);
    /** Dort kommen Gefallene zurück und landen Ausreißer: im Vorhof direkt hinter dem Eingangstor, der ist immer offen. */
    public static final Vec3 START = new Vec3(0.5, STAND_Y, 22.5);

    /** Die Bereiche, die man mit Türen freischaltet. */
    public static final int ZONE_START = 1, ZONE_NORTH = 2, ZONE_WEST = 3, ZONE_EAST = 4;
    public static final int ZONE_WALL_X = 21;

    public static int zone(int x, int z) {
        if (x <= -ZONE_WALL_X) return ZONE_WEST;
        if (x >= ZONE_WALL_X) return ZONE_EAST;
        return z >= 5 ? ZONE_START : ZONE_NORTH;
    }
    /**
     * Bereich einer genauen Position. Die Grenze liegt mitten in der Wand und im Gitter: Wer direkt vor
     * einem Gitter steht, ragt schon in dessen Blockfeld hinein, gehört aber noch zu seiner Seite.
     */
    public static int zoneAt(double x, double z) {
        if (x <= -ZONE_WALL_X + 0.5) return ZONE_WEST;
        if (x >= ZONE_WALL_X + 0.5) return ZONE_EAST;
        return z >= 4.5 ? ZONE_START : ZONE_NORTH;
    }

    public static final Vec3 LOBBY_SPAWN = new Vec3(0.5, STAND_Y, LOBBY_Z1 - 5 + 0.5);
    /** Untere linke Ecke des Innenraums vom Portal in der Lobby. */
    public static final BlockPos EXIT_PORTAL = new BlockPos(-1, STAND_Y + 1, LOBBY_Z1 - 1);
    /** Untere linke Ecke des Innenraums vom Exfil-Portal. Es steht im Vorhof und ist damit immer erreichbar. */
    public static final BlockPos EXTRACTION_PORTAL = new BlockPos(-1, 66, 12);
    /** Knopf zum Rufen des Exfil, er steht nur in der Pause nach jeder 5. Runde. */
    public static final BlockPos EXFIL_BUTTON = new BlockPos(3, 65, 12);

    public static final AABB BOUNDS = new AABB(-A - TRIBUNE - 4, 55, -B - TRIBUNE - 4, A + TRIBUNE + 4, 95, LOBBY_Z1 + 10);
    public static final int[] NICHE_Z = {LOBBY_Z0 + 5, LOBBY_Z0 + 12, LOBBY_Z0 + 19};

    public static final BlockPos LEVER_LEFT = new BlockPos(-2, STAND_Y + 1, GATE_Z + 2);
    public static final BlockPos LEVER_RIGHT = new BlockPos(2, STAND_Y + 1, GATE_Z + 2);

    /** Mögliche Plätze der Mystery Box, einer pro Bereich. */
    public static final List<BlockPos> BOX_SPOTS = List.of(new BlockPos(-10, STAND_Y, 28), new BlockPos(9, STAND_Y, -25), new BlockPos(-30, STAND_Y, -25), new BlockPos(40, STAND_Y, 14));
    /** Stromschalter unten in der Grube. */
    public static final BlockPos POWER_LEVER = new BlockPos(35, 64, 7);
    /** Amboss zum Verbessern von Waffen, ebenfalls in der Grube. */
    public static final BlockPos ANVIL = new BlockPos(35, 63, -7);
    /** Knopf zum Teilen von Punkten im Vorhof. */
    public static final BlockPos SHARE_BUTTON = new BlockPos(16, 66, 5);

    /** Mögliche Plätze für die Versorgungskisten. */
    public static final List<BlockPos> CHEST_SPOTS = List.of(
        new BlockPos(8, STAND_Y, 14), new BlockPos(-8, STAND_Y, 14), new BlockPos(0, STAND_Y, -14), new BlockPos(0, STAND_Y, 26), new BlockPos(0, STAND_Y, -28), new BlockPos(-12, STAND_Y, -26),
        new BlockPos(-28, STAND_Y, 15), new BlockPos(28, STAND_Y, -15),
        new BlockPos(28, 69, 15), new BlockPos(-28, 69, -15), new BlockPos(14, 69, 2), new BlockPos(-14, 69, -2),
        new BlockPos(35, 63, 3), new BlockPos(-35, 66, 0));

    /** mouth ist das Sandfeld direkt vor dem Tor. */
    public record MobGate(List<BlockPos> bars, List<BlockPos> tunnel, BlockPos spawn, BlockPos mouth) {
        public int zone() { return ArenaLayout.zone(mouth.getX(), mouth.getZ()); }
    }
    public static final List<MobGate> MOB_GATES = List.of(gate(70), gate(110), gate(-75), gate(-105), gate(0), gate(180), gate(-60), gate(-120), gate(55), gate(125));

    /** 0 ist Sand, 1 und 2 die Mauer, ab 3 die Tribüne, darüber hinaus nichts. */
    public static int ring(int x, int z) {
        for (int t = 0; t <= TRIBUNE; t++) {
            double ax = A + t, bz = B + t;
            if ((x * x) / (ax * ax) + (z * z) / (bz * bz) <= 1.0) return t;
        }
        return TRIBUNE + 1;
    }

    public static boolean isSand(double x, double z) {
        return ring((int) Math.floor(x), (int) Math.floor(z)) == 0;
    }

    /** Hinter dem Tor auf der Lobby-Seite? */
    public static boolean isLobbySide(double z) {
        return z >= GATE_Z;
    }

    private static MobGate gate(double degrees) {
        double rad = Math.toRadians(degrees), cos = Math.cos(rad), sin = Math.sin(rad);
        Direction out = Math.abs(cos / A) >= Math.abs(sin / B) ? (cos > 0 ? Direction.EAST : Direction.WEST) : (sin > 0 ? Direction.SOUTH : Direction.NORTH);
        Direction side = out.getClockWise();
        BlockPos mid = new BlockPos((int) Math.round((A - 2) * cos), STAND_Y, (int) Math.round((B - 2) * sin));
        List<BlockPos> bars = new ArrayList<>(), tunnel = new ArrayList<>();
        BlockPos spawn = null, mouth = null, previous = null;
        for (int p = -1; p <= 1; p++) {
            BlockPos cell = mid.relative(side, p);
            while (ring(cell.getX(), cell.getZ()) == 0) cell = cell.relative(out);
            if (p == 0) mouth = cell.relative(out.getOpposite());
            for (int y = 0; y < 4; y++) bars.add(cell.above(y));
            // Das Oval ist gebogen: Liegen zwei Spalten versetzt, bliebe zwischen ihnen eine schräge Lücke.
            if (previous != null) {
                int depth = cell.getX() * out.getStepX() + cell.getZ() * out.getStepZ(), before = previous.getX() * out.getStepX() + previous.getZ() * out.getStepZ();
                BlockPos shallow = depth < before ? cell : previous;
                for (int i = 1; i <= Math.abs(depth - before); i++) for (int y = 0; y < 4; y++) bars.add(shallow.relative(out, i).above(y));
            }
            previous = cell;
            for (int depth = 1; depth <= 6; depth++) {
                BlockPos t = cell.relative(out, depth);
                for (int y = 0; y < 4; y++) tunnel.add(t.above(y));
                if (p == 0 && depth == 4) spawn = t;
            }
        }
        return new MobGate(List.copyOf(bars), List.copyOf(tunnel), spawn, mouth);
    }

    private ArenaLayout() {}
}
