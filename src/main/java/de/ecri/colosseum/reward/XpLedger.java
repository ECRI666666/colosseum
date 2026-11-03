package de.ecri.colosseum.reward;

import de.ecri.colosseum.dimension.ColosseumDimension;
import de.ecri.colosseum.registry.ModAttachments;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Das Abholkonto: Was im Punkte-Shop gekauft wurde, liegt hier, bis der Spieler die Arena verlässt.
 * XP-Flaschen sind nur noch gekaufte Ware (oder alte Guthaben aus Versionen vor 0.8) und keine Belohnung mehr.
 */
public final class XpLedger {
    /** Schreibt Flaschen gut. Außerhalb der Arena passiert nichts. */
    public static void credit(ServerPlayer player, int bottles) {
        if (bottles <= 0 || !ColosseumDimension.isArena(player.level())) return;
        player.setData(ModAttachments.EARNED_BOTTLES, balance(player) + bottles);
    }

    public static int balance(ServerPlayer player) {
        return player.getData(ModAttachments.EARNED_BOTTLES);
    }

    public static void addGoods(ServerPlayer player, ItemStack stack) {
        List<ItemStack> list = new ArrayList<>(goods(player));
        list.add(stack.copy());
        player.setData(ModAttachments.PENDING_GOODS, List.copyOf(list));
    }

    public static List<ItemStack> goods(ServerPlayer player) {
        return player.getData(ModAttachments.PENDING_GOODS);
    }

    /** Legt alles ins Inventar. Was nicht mehr hineinpasst, fällt vor die Füße. */
    public static void payOut(ServerPlayer player) {
        int left = balance(player);
        List<ItemStack> goods = goods(player);
        player.removeData(ModAttachments.EARNED_BOTTLES);
        player.removeData(ModAttachments.PENDING_GOODS);
        int stackSize = Items.EXPERIENCE_BOTTLE.getDefaultMaxStackSize();
        while (left > 0) {
            int count = Math.min(left, stackSize);
            player.getInventory().placeItemBackInInventory(new ItemStack(Items.EXPERIENCE_BOTTLE, count), Prediction.SERVER_ONLY);
            left -= count;
        }
        for (ItemStack stack : goods) player.getInventory().placeItemBackInInventory(stack.copy(), Prediction.SERVER_ONLY);
    }

    private XpLedger() {}
}
