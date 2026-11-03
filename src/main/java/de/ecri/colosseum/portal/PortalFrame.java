package de.ecri.colosseum.portal;

import de.ecri.colosseum.registry.ModBlocks;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.tags.BlockTags;

/**
 * Ein Sandstein-Rahmen wie beim Netherportal: Innenraum 2x3 bis 21x21, die Ecken dürfen fehlen.
 * origin ist die untere linke Ecke des Innenraums.
 */
public record PortalFrame(BlockPos origin, Direction.Axis axis, int width, int height) {
    public static final int MIN_WIDTH = 2, MIN_HEIGHT = 3, MAX_SIZE = 21;

    private static boolean isFrame(BlockState state) {
        return state.is(Blocks.SANDSTONE);
    }

    private static boolean isInterior(BlockState state) {
        return state.isAir() || state.is(BlockTags.FIRE) || state.is(ModBlocks.PORTAL.get());
    }

    /** Sucht den Rahmen um eine Position im Innenraum. */
    public static Optional<PortalFrame> find(BlockGetter level, BlockPos inside, Direction.Axis axis) {
        if (!isInterior(level.getBlockState(inside))) return Optional.empty();
        Direction right = axis == Direction.Axis.X ? Direction.EAST : Direction.SOUTH;

        BlockPos pos = inside;
        for (int i = 0; i < MAX_SIZE && isInterior(level.getBlockState(pos.below())); i++) pos = pos.below();
        if (!isFrame(level.getBlockState(pos.below()))) return Optional.empty();

        for (int i = 0; i < MAX_SIZE && isInterior(level.getBlockState(pos.relative(right.getOpposite()))); i++) pos = pos.relative(right.getOpposite());
        if (!isFrame(level.getBlockState(pos.relative(right.getOpposite())))) return Optional.empty();
        BlockPos origin = pos;

        int width = 0;
        while (width <= MAX_SIZE && isInterior(level.getBlockState(origin.relative(right, width)))) {
            if (!isFrame(level.getBlockState(origin.relative(right, width).below()))) return Optional.empty();
            width++;
        }
        if (width < MIN_WIDTH || width > MAX_SIZE || !isFrame(level.getBlockState(origin.relative(right, width)))) return Optional.empty();

        int height = 0;
        rows:
        while (height <= MAX_SIZE) {
            BlockPos row = origin.above(height);
            for (int i = 0; i < width; i++) {
                if (!isInterior(level.getBlockState(row.relative(right, i)))) break rows;
            }
            if (!isFrame(level.getBlockState(row.relative(right.getOpposite()))) || !isFrame(level.getBlockState(row.relative(right, width)))) return Optional.empty();
            height++;
        }
        if (height < MIN_HEIGHT || height > MAX_SIZE) return Optional.empty();
        BlockPos top = origin.above(height);
        for (int i = 0; i < width; i++) {
            if (!isFrame(level.getBlockState(top.relative(right, i)))) return Optional.empty();
        }
        return Optional.of(new PortalFrame(origin, axis, width, height));
    }

    /** Sucht einen zündbaren Rahmen in beiden Ausrichtungen. */
    public static Optional<PortalFrame> findEither(BlockGetter level, BlockPos inside) {
        Optional<PortalFrame> x = find(level, inside, Direction.Axis.X);
        return x.isPresent() ? x : find(level, inside, Direction.Axis.Z);
    }

    private BlockPos cell(int i, int j) {
        return origin.relative(axis == Direction.Axis.X ? Direction.EAST : Direction.SOUTH, i).above(j);
    }

    /** Ist der Innenraum vollständig mit Portalblöcken gefüllt? */
    public boolean isComplete(BlockGetter level) {
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                if (!level.getBlockState(cell(i, j)).is(ModBlocks.PORTAL.get())) return false;
            }
        }
        return true;
    }

    public void fill(Level level) {
        BlockState state = ModBlocks.PORTAL.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_AXIS, axis);
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                level.setBlock(cell(i, j), state, 18);
            }
        }
    }
}
