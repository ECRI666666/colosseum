package de.ecri.colosseum.registry;

import de.ecri.colosseum.Colosseum;
import de.ecri.colosseum.portal.ColosseumPortalBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Colosseum.MOD_ID);

    /** Der einzige eigene Block. Das Aussehen kommt vom Vanilla-Netherportal, siehe blockstates/portal.json. */
    public static final DeferredBlock<ColosseumPortalBlock> PORTAL = BLOCKS.registerBlock(
        "portal",
        ColosseumPortalBlock::new,
        p -> p.noCollision().strength(-1.0F).lightLevel(s -> 11).noLootTable().sound(SoundType.GLASS).pushReaction(PushReaction.IMMOVEABLE));

    private ModBlocks() {}
}
