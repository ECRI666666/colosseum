package de.ecri.colosseum.shop;

import de.ecri.colosseum.run.PlayerClass;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;

/**
 * Die Kaufstellen an den Wänden, wie Wall-Buys: Knopf mit Schild und Preis. Billiges hängt am Mittelteil,
 * das beste Zeug liegt tiefer in der Karte, in den Häusern und in der Grube.
 */
public final class Shop {
    public record Offer(String line1, String line2, int price, BlockPos button, Direction facing, Consumer<ServerPlayer> give) {
        public BlockPos sign() { return button.above(); }
        public BlockPos backing() { return button.relative(facing.getOpposite()); }
        public net.minecraft.network.chat.Component name() { return line2.isEmpty() ? de.ecri.colosseum.Lang.t(line1) : de.ecri.colosseum.Lang.t("offer.pair", de.ecri.colosseum.Lang.t(line1), de.ecri.colosseum.Lang.t(line2)); }
    }

    private static void give(ServerPlayer p, ItemStack stack) {
        p.getInventory().placeItemBackInInventory(PlayerClass.unbreakable(stack), Prediction.SERVER_ONLY);
    }

    private static Consumer<ServerPlayer> item(Item item, int count) {
        return p -> give(p, new ItemStack(item, count));
    }

    private static Consumer<ServerPlayer> potion(Item bottle, Holder<Potion> potion) {
        return p -> give(p, PotionContents.createItemStack(bottle, potion));
    }

    private static Consumer<ServerPlayer> weapon(Item item, Object... enchants) {
        return p -> give(p, PlayerClass.enchanted(p.registryAccess(), item, enchants));
    }

    /** Eine Rüstung ersetzt die alte komplett. */
    private static Consumer<ServerPlayer> armor(Item head, Item chest, Item legs, Item feet, ResourceKey<Enchantment> enchant, int level) {
        return p -> {
            HolderLookup.Provider r = p.registryAccess();
            Object[] e = enchant == null ? new Object[0] : new Object[] {enchant, level};
            p.setItemSlot(EquipmentSlot.HEAD, PlayerClass.enchanted(r, head, e));
            p.setItemSlot(EquipmentSlot.CHEST, PlayerClass.enchanted(r, chest, e));
            p.setItemSlot(EquipmentSlot.LEGS, PlayerClass.enchanted(r, legs, e));
            p.setItemSlot(EquipmentSlot.FEET, PlayerClass.enchanted(r, feet, e));
        };
    }

    private static BlockPos south(int x) { return new BlockPos(x, 66, 5); }
    private static BlockPos north(int x) { return new BlockPos(x, 66, -5); }

    public static final List<Offer> OFFERS = List.of(
        // Mittelteil, Südseite: das Nötigste
        new Offer("offer.steak", "offer.four", 100, south(-18), Direction.SOUTH, item(Items.COOKED_BEEF, 4)),
        new Offer("offer.decoy", "offer.decoy_info", 1500, south(12), Direction.SOUTH, p -> give(p, Decoys.item())),
        new Offer("offer.arrows", "offer.n32", 100, south(-16), Direction.SOUTH, item(Items.ARROW, 32)),
        new Offer("offer.stone_sword", "", 150, south(-9), Direction.SOUTH, weapon(Items.STONE_SWORD)),
        new Offer("offer.leather_armor", "", 150, south(-7), Direction.SOUTH, armor(Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS, null, 0)),
        new Offer("offer.bow", "offer.plus16", 200, south(-5), Direction.SOUTH, p -> { give(p, new ItemStack(Items.BOW)); give(p, new ItemStack(Items.ARROW, 16)); }),
        new Offer("offer.shield", "", 300, south(5), Direction.SOUTH, item(Items.SHIELD, 1)),
        new Offer("offer.splash", "offer.healing", 250, south(7), Direction.SOUTH, potion(Items.SPLASH_POTION, Potions.HEALING)),
        new Offer("offer.splash", "offer.harming", 250, south(9), Direction.SOUTH, potion(Items.SPLASH_POTION, Potions.HARMING)),

        // Mittelteil, Nordseite: die Mittelklasse
        new Offer("offer.iron_sword", "", 500, north(-9), Direction.NORTH, weapon(Items.IRON_SWORD)),
        new Offer("offer.chain_armor", "", 500, north(-7), Direction.NORTH, armor(Items.CHAINMAIL_HELMET, Items.CHAINMAIL_CHESTPLATE, Items.CHAINMAIL_LEGGINGS, Items.CHAINMAIL_BOOTS, null, 0)),
        new Offer("offer.crossbow", "", 500, north(-5), Direction.NORTH, weapon(Items.CROSSBOW)),
        new Offer("offer.bow", "offer.power2", 800, north(5), Direction.NORTH, weapon(Items.BOW, Enchantments.POWER, 2)),
        new Offer("offer.iron_armor", "", 1200, north(7), Direction.NORTH, armor(Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS, null, 0)),
        new Offer("offer.splash", "offer.regeneration", 400, north(16), Direction.NORTH, potion(Items.SPLASH_POTION, Potions.REGENERATION)),
        new Offer("offer.golden_apple", "", 800, north(18), Direction.NORTH, item(Items.GOLDEN_APPLE, 1)),

        // Säulenhalle im Westen
        new Offer("offer.strength", "", 900, new BlockPos(-43, 66, -7), Direction.SOUTH, potion(Items.POTION, Potions.STRONG_STRENGTH)),
        new Offer("offer.bow", "offer.power4", 2500, new BlockPos(-32, 66, -7), Direction.SOUTH, weapon(Items.BOW, Enchantments.POWER, 4)),
        new Offer("offer.totem", "offer.undying", 3000, new BlockPos(-27, 66, 7), Direction.NORTH, item(Items.TOTEM_OF_UNDYING, 1)),

        // In den Häusern
        new Offer("offer.diamond_sword", "offer.sharp2", 2000, new BlockPos(-30, 66, -19), Direction.SOUTH, weapon(Items.DIAMOND_SWORD, Enchantments.SHARPNESS, 2)),
        new Offer("offer.diamond_armor", "offer.prot2", 3000, new BlockPos(-27, 66, -19), Direction.SOUTH, armor(Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS, Enchantments.PROTECTION, 2)),
        new Offer("offer.turtle", "offer.potion", 1500, new BlockPos(25, 66, 19), Direction.NORTH, potion(Items.POTION, Potions.TURTLE_MASTER)),
        new Offer("offer.enchanted", "offer.gapple2", 3500, new BlockPos(28, 66, 19), Direction.NORTH, item(Items.ENCHANTED_GOLDEN_APPLE, 1)),

        // Unten in der Grube: das Beste
        new Offer("offer.netherite_sword", "offer.sharp5", 4500, new BlockPos(33, 64, -7), Direction.SOUTH, weapon(Items.NETHERITE_SWORD, Enchantments.SHARPNESS, 5)),
        new Offer("offer.netherite_armor", "offer.prot4", 6000, new BlockPos(37, 64, -7), Direction.SOUTH, armor(Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS, Enchantments.PROTECTION, 4)));

    public static Offer at(BlockPos pos) {
        for (Offer offer : OFFERS) if (offer.button().equals(pos)) return offer;
        return null;
    }

    private Shop() {}
}
