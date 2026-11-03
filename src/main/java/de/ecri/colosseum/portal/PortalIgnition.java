package de.ecri.colosseum.portal;

import de.ecri.colosseum.dimension.ColosseumDimension;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Feuerzeug in einem gültigen Sandstein-Rahmen benutzen: Das Portal zündet statt eines normalen Feuers. */
public final class PortalIgnition {
    public static void register() {
        NeoForge.EVENT_BUS.addListener(PortalIgnition::onRightClick);
    }

    private static void onRightClick(PlayerInteractEvent.RightClickBlock e) {
        ItemStack stack = e.getItemStack();
        Direction face = e.getFace();
        Level level = e.getLevel();
        Player player = e.getEntity();
        if (!stack.is(Items.FLINT_AND_STEEL) || face == null || ColosseumDimension.isArena(level)) return;

        BlockPos inside = e.getPos().relative(face);
        if (!level.mayInteract(player, e.getPos()) || !player.mayUseItemAt(inside, face, stack)) return;

        var frame = PortalFrame.findEither(level, inside);
        if (frame.isEmpty() || level.getBlockState(inside).is(de.ecri.colosseum.registry.ModBlocks.PORTAL.get())) return;

        // Der Client macht dasselbe ohne Änderung, sonst zeigt er kurz ein falsches Feuer.
        if (level instanceof ServerLevel) {
            frame.get().fill(level);
            stack.hurtAndBreak(1, player, e.getHand());
            level.playSound(null, inside, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
        e.setCancellationResult(InteractionResult.SUCCESS);
        e.setCanceled(true);
    }

    private PortalIgnition() {}
}
