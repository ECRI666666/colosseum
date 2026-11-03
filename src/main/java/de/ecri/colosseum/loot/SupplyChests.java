package de.ecri.colosseum.loot;

import de.ecri.colosseum.dimension.ArenaLayout;
import de.ecri.colosseum.run.PlayerClass;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

/** Zwei Versorgungskisten pro Pause, der Inhalt hängt von der Runde ab. */
public final class SupplyChests {
    private record Entry(int weight, Function<RollContext, ItemStack> make) {}
    private record RollContext(HolderLookup.Provider registries, RandomSource random) {}

    private static Entry item(int weight, Item item, int min, int max) {
        return new Entry(weight, c -> new ItemStack(item, min + c.random().nextInt(max - min + 1)));
    }

    private static Entry potion(int weight, Item item, net.minecraft.core.Holder<net.minecraft.world.item.alchemy.Potion> potion) {
        return new Entry(weight, c -> PotionContents.createItemStack(item, potion));
    }

    private static Entry enchanted(int weight, Item item, Object... enchants) {
        return new Entry(weight, c -> PlayerClass.enchanted(c.registries(), item, enchants));
    }

    private static final List<Entry> LOW = List.of(
        item(10, Items.COOKED_BEEF, 2, 5), item(10, Items.BREAD, 3, 6), item(6, Items.COOKED_CHICKEN, 2, 4), item(8, Items.ARROW, 8, 20),
        potion(8, Items.POTION, Potions.HEALING),
        item(1, Items.LEATHER_HELMET, 1, 1), item(1, Items.LEATHER_CHESTPLATE, 1, 1), item(1, Items.CHAINMAIL_LEGGINGS, 1, 1), item(1, Items.CHAINMAIL_BOOTS, 1, 1));

    private static final List<Entry> MID = List.of(
        item(10, Items.GOLDEN_CARROT, 3, 8), item(6, Items.COOKED_BEEF, 4, 8), item(6, Items.ARROW, 16, 32), item(5, Items.SHIELD, 1, 1), item(4, Items.BOW, 1, 1),
        potion(8, Items.SPLASH_POTION, Potions.STRONG_HEALING), potion(6, Items.SPLASH_POTION, Potions.REGENERATION), potion(6, Items.SPLASH_POTION, Potions.SWIFTNESS),
        enchanted(4, Items.IRON_SWORD, Enchantments.SHARPNESS, 2), enchanted(3, Items.IRON_SWORD, Enchantments.SMITE, 3), enchanted(2, Items.DIAMOND_SWORD, Enchantments.SHARPNESS, 1), enchanted(1, Items.DIAMOND_SWORD, Enchantments.SMITE, 3),
        enchanted(3, Items.BOW, Enchantments.POWER, 2),
        item(1, Items.IRON_HELMET, 1, 1), item(1, Items.IRON_CHESTPLATE, 1, 1), item(1, Items.IRON_LEGGINGS, 1, 1), item(1, Items.IRON_BOOTS, 1, 1));

    private static final List<Entry> HIGH = List.of(
        item(10, Items.GOLDEN_APPLE, 1, 3), item(1, Items.ENCHANTED_GOLDEN_APPLE, 1, 1), item(1, Items.TOTEM_OF_UNDYING, 1, 1), item(4, Items.GOLDEN_CARROT, 6, 12), item(4, Items.ARROW, 32, 64),
        potion(6, Items.SPLASH_POTION, Potions.STRONG_HEALING), potion(5, Items.POTION, Potions.STRONG_STRENGTH), potion(4, Items.POTION, Potions.TURTLE_MASTER),
        enchanted(5, Items.DIAMOND_SWORD, Enchantments.SHARPNESS, 4, Enchantments.UNBREAKING, 3), enchanted(2, Items.NETHERITE_SWORD, Enchantments.SHARPNESS, 5), enchanted(4, Items.BOW, Enchantments.POWER, 5, Enchantments.UNBREAKING, 3),
        enchanted(1, Items.DIAMOND_HELMET, Enchantments.PROTECTION, 3), enchanted(1, Items.DIAMOND_CHESTPLATE, Enchantments.PROTECTION, 3), enchanted(1, Items.DIAMOND_LEGGINGS, Enchantments.PROTECTION, 3), enchanted(1, Items.DIAMOND_BOOTS, Enchantments.PROTECTION, 3),
        enchanted(1, Items.NETHERITE_CHESTPLATE, Enchantments.PROTECTION, 4));

    private final List<BlockPos> placed = new ArrayList<>();

    /** Würfelt den Inhalt einer Kiste für diese Runde aus. */
    public static List<ItemStack> roll(int round, HolderLookup.Provider registries, RandomSource random) {
        List<Entry> table = round <= 5 ? LOW : round <= 14 ? MID : HIGH;
        int total = table.stream().mapToInt(Entry::weight).sum();
        RollContext context = new RollContext(registries, random);
        int rolls = 4 + random.nextInt(3);
        List<ItemStack> out = new ArrayList<>();
        for (int i = 0; i < rolls; i++) {
            int pick = random.nextInt(total);
            for (Entry entry : table) {
                pick -= entry.weight();
                if (pick < 0) {
                    out.add(PlayerClass.unbreakable(entry.make().apply(context)));
                    break;
                }
            }
        }
        return out;
    }

    public void place(ServerLevel level, int round, RandomSource random, List<BlockPos> allowed) {
        List<BlockPos> spots = new ArrayList<>(allowed);
        Collections.shuffle(spots, new java.util.Random(random.nextLong()));
        for (BlockPos pos : spots.subList(0, 2)) {
            level.setBlock(pos, Blocks.CHEST.defaultBlockState(), 3);
            if (level.getBlockEntity(pos) instanceof ChestBlockEntity chest) {
                List<Integer> slots = new ArrayList<>();
                for (int i = 0; i < chest.getContainerSize(); i++) slots.add(i);
                Collections.shuffle(slots, new java.util.Random(random.nextLong()));
                List<ItemStack> items = roll(round, level.registryAccess(), random);
                for (int i = 0; i < items.size(); i++) chest.setItem(slots.get(i), items.get(i));
            }
            placed.add(pos);
        }
    }

    public List<BlockPos> placed() {
        return placed;
    }

    public void particles(ServerLevel level) {
        for (BlockPos pos : placed) level.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 0.5, 6, 0.1, 1.2, 0.1, 0.01);
    }

    /** Entfernt die Kisten samt Restinhalt, es fällt nichts heraus. */
    public void remove(ServerLevel level) {
        for (BlockPos pos : placed) clearSpot(level, pos);
        placed.clear();
    }

    public static void clearAll(ServerLevel level) {
        for (BlockPos pos : ArenaLayout.CHEST_SPOTS) clearSpot(level, pos);
    }

    private static void clearSpot(ServerLevel level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof ChestBlockEntity chest) chest.clearContent();
        if (level.getBlockState(pos).is(Blocks.CHEST)) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
    }
}
