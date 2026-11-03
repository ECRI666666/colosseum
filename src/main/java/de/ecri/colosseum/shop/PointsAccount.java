package de.ecri.colosseum.shop;

import de.ecri.colosseum.registry.ModAttachments;
import de.ecri.colosseum.run.Difficulty;
import net.minecraft.server.level.ServerPlayer;

/**
 * Das feste Punktekonto eines Spielers für den Shop in der Halle. Jede geschaffte Runde bringt etwas mehr
 * als die davor. Es hat nichts mit den Punkten zu tun, die man im Durchlauf an den Wänden ausgibt.
 * Es liegt in der Spielerdatei und übersteht Tod und Neustart.
 */
public final class PointsAccount {
    /**
     * Gesamtertrag nach so vielen geschafften Runden: 100 * n * (n + 5).
     * Damit bringt der Tod in Runde 11 genau 15.000 (Preis von 30 Level, 16 Diamanten oder einem Netherit-Barren)
     * und der Tod in Runde 16 genau das Doppelte, und jede Runde bringt mehr als die davor.
     */
    public static int totalFor(int rounds) {
        return 100 * rounds * (rounds + 5);
    }

    /** Was genau diese Runde dazugibt, mit dem Faktor der Schwierigkeit. */
    public static int roundGain(int round, Difficulty difficulty) {
        return (int) Math.round((totalFor(round) - totalFor(round - 1)) * difficulty.xp);
    }

    /** Ein geschaffter Exfil zählt 1,5-fach. */
    public static int withExfil(int amount) {
        return amount + amount / 2;
    }

    public static int balance(ServerPlayer player) {
        return player.getData(ModAttachments.POINTS_ACCOUNT);
    }

    public static void add(ServerPlayer player, int amount) {
        if (amount > 0) player.setData(ModAttachments.POINTS_ACCOUNT, balance(player) + amount);
    }

    /** Bucht ab, wenn genug da ist. */
    public static boolean take(ServerPlayer player, int amount) {
        int have = balance(player);
        if (have < amount) return false;
        player.setData(ModAttachments.POINTS_ACCOUNT, have - amount);
        return true;
    }

    /** Der Ertrag des laufenden Durchlaufs liegt in der Spielerdatei: Auch ein Absturz oder Abmelden nimmt ihn nicht weg. */
    public static void addPending(ServerPlayer player, int amount) {
        player.setData(ModAttachments.RUN_POINTS, pending(player) + amount);
    }

    public static int pending(ServerPlayer player) {
        return player.getData(ModAttachments.RUN_POINTS);
    }

    public static int settlePending(ServerPlayer player) {
        int value = player.getData(ModAttachments.RUN_POINTS);
        player.removeData(ModAttachments.RUN_POINTS);
        add(player, value);
        return value;
    }

    private PointsAccount() {}
}
