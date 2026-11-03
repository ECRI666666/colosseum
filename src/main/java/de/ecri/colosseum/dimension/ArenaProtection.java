package de.ecri.colosseum.dimension;

import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityMobGriefingEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;

/** Die Arena bleibt, wie sie ist. Nur Spieler im Kreativmodus dürfen dort bauen. */
public final class ArenaProtection {
    public static void register() {
        NeoForge.EVENT_BUS.addListener(ArenaProtection::onDamage);
        NeoForge.EVENT_BUS.addListener(ArenaProtection::onBreak);
        NeoForge.EVENT_BUS.addListener(ArenaProtection::onPlace);
        NeoForge.EVENT_BUS.addListener(ArenaProtection::onExplosion);
        NeoForge.EVENT_BUS.addListener(ArenaProtection::onMobGriefing);
    }

    /** Die Rüstungsständer in der Lobby kann niemand kaputt schlagen. */
    private static void onDamage(LivingIncomingDamageEvent e) {
        if (e.getEntity().entityTags().contains(ArenaBuilder.DISPLAY_TAG)) e.setCanceled(true);
    }

    private static void onBreak(BreakBlockEvent e) {
        if (ColosseumDimension.isArena(e.getLevel()) && !e.getPlayer().isCreative()) e.setCanceled(true);
    }

    private static void onPlace(BlockEvent.EntityPlaceEvent e) {
        if (ColosseumDimension.isArena(e.getLevel()) && !(e.getEntity() instanceof Player p && p.isCreative())) e.setCanceled(true);
    }

    private static void onExplosion(ExplosionEvent.Detonate e) {
        if (ColosseumDimension.isArena(e.getLevel())) e.getAffectedBlocks().clear();
    }

    private static void onMobGriefing(EntityMobGriefingEvent e) {
        if (ColosseumDimension.isArena(e.getEntity().level())) e.setCanGrief(false);
    }

    private ArenaProtection() {}
}
