package de.ecri.colosseum.shop;

import de.ecri.colosseum.run.PlayerClass;
import java.util.List;
import java.util.function.Function;
import net.minecraft.core.HolderLookup;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;

/** Die Mystery Box: zufälliger Inhalt, vom Scherz bis zum Volltreffer. */
public final class MysteryBox {
    public static final int PRICE = 950, USES_BEFORE_MOVE = 6;

    private record Prize(int weight, Function<HolderLookup.Provider, ItemStack> make) {}

    private static Prize plain(int weight, Item item, int count) {
        return new Prize(weight, r -> new ItemStack(item, count));
    }

    private static Prize magic(int weight, Item item, Object... enchants) {
        return new Prize(weight, r -> PlayerClass.enchanted(r, item, enchants));
    }

    private static final List<Prize> PRIZES = List.of(
        plain(8, Items.WOODEN_SWORD, 1), plain(6, Items.COOKED_BEEF, 16),
        magic(8, Items.STONE_AXE, Enchantments.SHARPNESS, 3),
        magic(8, Items.IRON_SWORD, Enchantments.SHARPNESS, 3),
        magic(8, Items.IRON_AXE, Enchantments.SHARPNESS, 2),
        magic(6, Items.BOW, Enchantments.POWER, 3, Enchantments.FLAME, 1),
        magic(6, Items.CROSSBOW, Enchantments.MULTISHOT, 1, Enchantments.QUICK_CHARGE, 2),
        magic(5, Items.TRIDENT, Enchantments.LOYALTY, 3),
        magic(5, Items.DIAMOND_SWORD, Enchantments.SHARPNESS, 4, Enchantments.FIRE_ASPECT, 1),
        magic(4, Items.DIAMOND_AXE, Enchantments.SHARPNESS, 3),
        magic(3, Items.MACE, Enchantments.DENSITY, 3),
        magic(2, Items.NETHERITE_AXE, Enchantments.SHARPNESS, 5),
        magic(2, Items.BOW, Enchantments.POWER, 5, Enchantments.FLAME, 1, Enchantments.PUNCH, 2),
        plain(3, Items.GOLDEN_APPLE, 3), plain(1, Items.ENCHANTED_GOLDEN_APPLE, 1), plain(1, Items.TOTEM_OF_UNDYING, 1));

    public static ItemStack roll(HolderLookup.Provider registries, RandomSource random) {
        int pick = random.nextInt(PRIZES.stream().mapToInt(Prize::weight).sum());
        for (Prize prize : PRIZES) {
            pick -= prize.weight();
            if (pick < 0) return PlayerClass.unbreakable(prize.make().apply(registries));
        }
        throw new IllegalStateException();
    }

    private MysteryBox() {}
}
