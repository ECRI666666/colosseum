package de.ecri.colosseum.wave;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.RandomSource;

/** Entscheidet, was in welcher Runde kommt. */
public final class WaveComposer {
    public enum Kind { ZOMBIE, HUSK, SKELETON, CHICKEN_JOCKEY, SPIDER_JOCKEY, RAVAGER_RIDER, DROWNED, BOSS, SPIDER }
    public enum Type { REGULAR, CHICKEN, SPIDER, HEAVY }

    public static final int MAX_MOBS = 40, EXFIL_MAX_MOBS = 32;

    /** Jede 4. Runde ist besonders: 4 und 12 Hühner, 8 und 16 Spinnen, ab 20 schwere Jockeys. */
    public static Type type(int round) {
        if (round % 4 != 0) return Type.REGULAR;
        if (round >= 20) return Type.HEAVY;
        return round % 8 == 4 ? Type.CHICKEN : Type.SPIDER;
    }

    public static List<Kind> compose(int round, int players, RandomSource random) {
        double party = 1.0 + 0.5 * Math.max(0, players - 1);
        List<Kind> list = new ArrayList<>();
        switch (type(round)) {
            case REGULAR -> {
                int n = Math.min(MAX_MOBS, (int) Math.round((4 + 2 * round) * party));
                int skeletons = Math.min(n, skeletonBudget(round, random));
                add(list, Kind.SKELETON, skeletons);
                for (int i = skeletons; i < n; i++) list.add(random.nextInt(100) < 64 ? Kind.ZOMBIE : Kind.HUSK);
            }
            case CHICKEN -> {
                // Nur noch wenige Jockeys, sie sind sehr schwer zu treffen. Der Rest der Welle sind normale Zombies.
                int jockeys = Math.max(1, (int) Math.round((5 + round) * party / 4));
                add(list, Kind.CHICKEN_JOCKEY, jockeys);
                add(list, Kind.ZOMBIE, 2 * jockeys);
            }
            case SPIDER -> {
                // Die Reiter sind Skelette: Was über dem Limit liegt, kommt als Spinne ohne Reiter.
                int n = Math.min(MAX_MOBS, (int) Math.round((3 + round / 2) * party));
                int riders = Math.min(n, skeletonBudget(round, random));
                add(list, Kind.SPIDER_JOCKEY, riders);
                add(list, Kind.SPIDER, n - riders);
            }
            case HEAVY -> {
                add(list, Kind.RAVAGER_RIDER, Math.min(8, (int) Math.round((1 + (round - 20) / 8) * party)));
                add(list, Kind.DROWNED, Math.min(MAX_MOBS, (int) Math.round((4 + round / 4) * party)));
            }
        }
        // Alle 10 Runden kommt zusätzlich ein Boss
        if (round % 10 == 0) list.add(Kind.BOSS);
        return list;
    }

    /** Die Exfil-Runde: alles gemischt und deutlich mehr als eine normale Runde dieser Stufe. */
    public static List<Kind> composeExfil(int round, int players, RandomSource random) {
        double party = 1.0 + 0.5 * Math.max(0, players - 1);
        int n = Math.min(EXFIL_MAX_MOBS, (int) Math.round((8 + 2.5 * round) * party));
        List<Kind> list = new ArrayList<>();
        int skeletons = skeletonBudget(round, random);
        for (int i = 0; i < n; i++) {
            int roll = random.nextInt(100);
            if (roll < 78 && roll >= 58) {
                list.add(skeletons-- > 0 ? Kind.SKELETON : Kind.ZOMBIE);
            } else if (roll >= 85) {
                list.add(skeletons-- > 0 ? Kind.SPIDER_JOCKEY : Kind.SPIDER);
            } else {
                list.add(roll < 38 ? Kind.ZOMBIE : roll < 58 ? Kind.HUSK : roll < 82 ? Kind.ZOMBIE : Kind.CHICKEN_JOCKEY);
            }
        }
        add(list, Kind.RAVAGER_RIDER, Math.max(1, round / 10));
        return list;
    }

    /** Skelette pro Runde: zufällig von null bis zur halben Rundenzahl, unabhängig von der Spielerzahl. */
    public static int skeletonBudget(int round, RandomSource random) {
        return random.nextInt(round / 2 + 1);
    }

    /** Wie viele Skelette eine Liste enthält, auch die Reiter auf den Spinnen. */
    public static int skeletonCount(List<Kind> list) {
        return (int) list.stream().filter(k -> k == Kind.SKELETON || k == Kind.SPIDER_JOCKEY).count();
    }

    private static void add(List<Kind> list, Kind kind, int n) {
        for (int i = 0; i < n; i++) list.add(kind);
    }

    private WaveComposer() {}
}
