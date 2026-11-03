package de.ecri.colosseum.player;

import de.ecri.colosseum.dimension.ArenaLayout;
import de.ecri.colosseum.dimension.ColosseumDimension;
import de.ecri.colosseum.registry.ModAttachments;
import de.ecri.colosseum.reward.XpLedger;
import de.ecri.colosseum.run.ArenaRun;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerRespawnPositionEvent;

/**
 * Regel für alle Wege, ob Portal, /tp, Tod oder Neuanmeldung:
 * In der Arena hat der Spieler eine Sicherung, außerhalb keine. Weicht das ab, wird es hier korrigiert.
 */
public final class ArenaTransfer {
    public static void register() {
        NeoForge.EVENT_BUS.addListener(ArenaTransfer::beforeTravel);
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent e) -> reconcile(e.getEntity()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerRespawnEvent e) -> {
            reconcile(e.getEntity());
            if (e.getEntity() instanceof ServerPlayer p && ColosseumDimension.isArena(p.level())) ArenaRun.INSTANCE.onRespawnInHall(p);
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            reconcile(e.getEntity());
            if (e.getEntity() instanceof ServerPlayer p && ColosseumDimension.isArena(p.level()) && !p.isDeadOrDying()) ArenaRun.INSTANCE.onLoginInArena(p);
        });
        NeoForge.EVENT_BUS.addListener(ArenaTransfer::onRespawnPosition);
        NeoForge.EVENT_BUS.addListener(ArenaTransfer::onDrops);
        NeoForge.EVENT_BUS.addListener(ArenaTransfer::onXpDrop);
    }

    /** Läuft vor dem Wechsel: Cursor und Crafting-Feld wandern ins Inventar und werden so mitgesichert. */
    private static void beforeTravel(EntityTravelToDimensionEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer player) || !e.getDimension().equals(ColosseumDimension.ARENA)) return;
        if (ColosseumDimension.isArena(player.level())) return;
        player.closeContainer();
        player.setData(ModAttachments.RETURN_POINT, new ReturnPoint(player.level().dimension(), player.position(), player.getYRot(), player.getXRot()));
    }

    private static void reconcile(net.minecraft.world.entity.player.Player entity) {
        if (!(entity instanceof ServerPlayer player) || player.isDeadOrDying()) return;
        boolean inArena = ColosseumDimension.isArena(player.level());
        boolean hasSnapshot = player.hasData(ModAttachments.SNAPSHOT);
        if (inArena && !hasSnapshot) enter(player);
        else if (!inArena && hasSnapshot) leave(player);
    }

    private static void enter(ServerPlayer player) {
        // Erst sichern, dann leeren. Schlägt das Sichern fehl, behält der Spieler seine Sachen.
        player.setData(ModAttachments.SNAPSHOT, PlayerSnapshot.capture(player));
        player.getInventory().clearContent();
        player.inventoryMenu.broadcastChanges();
        player.removeAllEffects();
        player.experienceLevel = 0;
        player.experienceProgress = 0F;
        player.totalExperience = 0;
        player.giveExperienceLevels(0);
        if (player.gameMode() == GameType.SURVIVAL) player.setGameMode(GameType.ADVENTURE);
    }

    private static void leave(ServerPlayer player) {
        player.getData(ModAttachments.SNAPSHOT).restore(player);
        player.removeData(ModAttachments.SNAPSHOT);
        player.removeData(ModAttachments.RETURN_POINT);
        XpLedger.payOut(player);
        ArenaRun.INSTANCE.onLeftArena(player);
    }

    /** Ziel des Rückkehr-Portals. Ist der alte Platz weg oder zugebaut, geht es zum normalen Spawn. */
    public static TeleportTransition exitTransition(ServerPlayer player) {
        if (player.hasData(ModAttachments.RETURN_POINT)) {
            ReturnPoint point = player.getData(ModAttachments.RETURN_POINT);
            ServerLevel level = player.level().getServer().getLevel(point.dimension());
            if (level != null) {
                Vec3 pos = point.pos();
                level.getChunk(net.minecraft.util.Mth.floor(pos.x) >> 4, net.minecraft.util.Mth.floor(pos.z) >> 4);
                if (level.noCollision(player.getDimensions(player.getPose()).makeBoundingBox(pos))) {
                    return new TeleportTransition(level, pos, Vec3.ZERO, point.yRot(), point.xRot(), TeleportTransition.PLAY_PORTAL_SOUND);
                }
            }
        }
        return TeleportTransition.createDefault(player, TeleportTransition.PLAY_PORTAL_SOUND);
    }

    /** Wer in der Arena stirbt, erscheint in der Halle. Inventar und Gekauftes gibt es beim Gang durchs Portal dort. */
    private static void onRespawnPosition(PlayerRespawnPositionEvent e) {
        if (!ColosseumDimension.isArena(e.getEntity().level())) return;
        ServerLevel arena = (ServerLevel) e.getEntity().level();
        e.setTeleportTransition(new TeleportTransition(arena, ArenaLayout.LOBBY_SPAWN, Vec3.ZERO, 180F, 0F, TeleportTransition.DO_NOTHING));
    }

    /** Arena-Sachen bleiben in der Arena: Sie verschwinden beim Tod, das eigene Inventar liegt in der Sicherung. */
    private static void onDrops(LivingDropsEvent e) {
        if (e.getEntity() instanceof ServerPlayer && ColosseumDimension.isArena(e.getEntity().level())) e.setCanceled(true);
    }

    private static void onXpDrop(LivingExperienceDropEvent e) {
        if (e.getEntity() instanceof ServerPlayer && ColosseumDimension.isArena(e.getEntity().level())) e.setCanceled(true);
    }

    private ArenaTransfer() {}
}
