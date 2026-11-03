package de.ecri.colosseum.dimension;

import de.ecri.colosseum.Colosseum;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;

/** Die Arena-Dimension. Definiert ist sie in data/colosseum/dimension/arena.json. */
public final class ColosseumDimension {
    public static final ResourceKey<Level> ARENA = ResourceKey.create(Registries.DIMENSION, Colosseum.id("arena"));

    public static boolean isArena(LevelAccessor level) {
        return level instanceof Level l && l.dimension().equals(ARENA);
    }

    private ColosseumDimension() {}
}
