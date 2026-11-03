package de.ecri.colosseum.lobby;

import de.ecri.colosseum.dimension.ArenaBuilder;
import de.ecri.colosseum.dimension.ArenaDoors;
import de.ecri.colosseum.dimension.ArenaLayout;
import de.ecri.colosseum.dimension.ColosseumDimension;
import de.ecri.colosseum.run.ArenaRun;
import de.ecri.colosseum.run.Difficulty;
import de.ecri.colosseum.run.PlayerClass;
import de.ecri.colosseum.shop.Perk;
import de.ecri.colosseum.shop.PointsShop;
import de.ecri.colosseum.shop.Shop;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Knöpfe und Hebel in der Lobby. Die Rüstungsständer sind nur zum Anschauen. */
public final class LobbyController {
    public static void register() {
        NeoForge.EVENT_BUS.addListener(LobbyController::onRightClick);
        NeoForge.EVENT_BUS.addListener(LobbyController::onEntityInteract);
        PointsShop.register();
        de.ecri.colosseum.shop.DebugMenu.register();
    }

    private static void onRightClick(PlayerInteractEvent.RightClickBlock e) {
        if (!ColosseumDimension.isArena(e.getLevel()) || e.getHand() != InteractionHand.MAIN_HAND) return;
        BlockPos pos = e.getPos();
        boolean lever = pos.equals(ArenaLayout.LEVER_LEFT) || pos.equals(ArenaLayout.LEVER_RIGHT);
        boolean exfil = pos.equals(ArenaLayout.EXFIL_BUTTON);
        boolean power = pos.equals(ArenaLayout.POWER_LEVER);
        boolean anvil = pos.equals(ArenaLayout.ANVIL);
        boolean box = pos.equals(ArenaRun.INSTANCE.boxPos());
        if (lever || exfil || power || anvil || box) {
            // Den Hebel setzt der Durchlauf selbst, er zeigt, wie viele bereit sind.
            // Der Exfil-Knopf verschwindet beim Drücken, das normale Drücken würde ihn wieder hinstellen.
            e.setCancellationResult(InteractionResult.SUCCESS);
            e.setCanceled(true);
        }
        if (!(e.getEntity() instanceof ServerPlayer player)) return;
        if (lever) { ArenaRun.INSTANCE.toggleReady(player); return; }
        if (exfil) { ArenaRun.INSTANCE.callExfil(player); return; }
        if (power) { ArenaRun.INSTANCE.switchPower(player); return; }
        if (anvil) { ArenaRun.INSTANCE.upgradeWeapon(player); return; }
        if (box) { ArenaRun.INSTANCE.useBox(player); return; }
        if (pos.equals(PointsShop.BUTTON)) { PointsShop.open(player); return; }
        if (pos.equals(ArenaLayout.SHARE_BUTTON)) { ArenaRun.INSTANCE.sharePoints(player); return; }
        Perk perk = Perk.at(pos);
        if (perk != null) { ArenaRun.INSTANCE.buyPerk(player, perk); return; }
        ArenaDoors.Door door = ArenaDoors.at(pos);
        if (door != null) { ArenaRun.INSTANCE.openDoor(player, door); return; }
        Shop.Offer offer = Shop.at(pos);
        if (offer != null) { ArenaRun.INSTANCE.buy(player, offer); return; }
        for (PlayerClass cls : PlayerClass.values()) if (cls.button().equals(pos)) ArenaRun.INSTANCE.chooseClass(player, cls);
        for (Difficulty d : Difficulty.values()) if (d.button().equals(pos)) ArenaRun.INSTANCE.chooseDifficulty(player, d);
    }

    private static void onEntityInteract(PlayerInteractEvent.EntityInteract e) {
        if (e.getTarget().entityTags().contains(ArenaBuilder.DISPLAY_TAG)) {
            e.setCancellationResult(InteractionResult.SUCCESS);
            e.setCanceled(true);
        }
    }

    private LobbyController() {}
}
