package de.ecri.colosseum.shop;

import de.ecri.colosseum.Lang;
import de.ecri.colosseum.dimension.ArenaLayout;
import de.ecri.colosseum.dimension.ColosseumDimension;
import de.ecri.colosseum.reward.XpLedger;
import de.ecri.colosseum.run.ArenaRun;
import de.ecri.colosseum.run.PlayerClass;
import java.util.List;
import java.util.function.Function;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Der Punkte-Shop in der Halle: ein Kistenmenü, ein Klick kauft. Gekauftes liegt bereit und kommt mit dem
 * Gang durchs Portal ins normale Inventar, denn in der Arena gilt ein eigenes Inventar.
 *
 * Der Maßstab ist der Tod in Runde 11: Er bringt genau 15.000 Punkte (PointsAccount.totalFor) und das reicht
 * für 30 Level, 16 Diamanten oder einen Netherit-Barren. Alles andere ist daran ausgerichtet.
 */
public final class PointsShop {
    public static final BlockPos BUTTON = new BlockPos(-4, ArenaLayout.STAND_Y + 1, ArenaLayout.LOBBY_Z1);
    public static final int BOTTLE_PRICE = 75, DIAMOND_PRICE = 1000, ROUND_TEN = 15000;
    /** 1395 XP für 30 Level, eine Flasche gibt im Schnitt 7 XP: 200 Flaschen reichen im Schnitt knapp aus. */
    public static final int BOTTLES_FOR_30_LEVELS = 200;
    private static final int SIZE = 54, INFO_SLOT = 4, CLICK_PAUSE_TICKS = 6;
    private static final double REACH_SQUARED = 8 * 8;

    public record Offer(int slot, String name, int price, int bottles, Function<HolderLookup.Provider, ItemStack> make) {}

    private static Offer goods(int slot, String name, int price, Item item, int count) {
        return new Offer(slot, name, price, 0, r -> new ItemStack(item, count));
    }

    private static Offer xp(int slot, String name, int bottles, int iconCount) {
        return new Offer(slot, name, bottles * BOTTLE_PRICE, bottles, r -> new ItemStack(Items.EXPERIENCE_BOTTLE, iconCount));
    }

    private static Offer book(int slot, String name, int price, ResourceKey<Enchantment> enchantment, int level) {
        return new Offer(slot, name, price, 0, r -> PlayerClass.enchanted(r, Items.ENCHANTED_BOOK, enchantment, level));
    }

    public static final List<Offer> OFFERS = List.of(
        // XP und Essen
        xp(9, "points.16_xp_bottles", 16, 16),
        xp(10, "points.64_xp_bottles", 64, 64),
        xp(11, "points.30_levels", BOTTLES_FOR_30_LEVELS, 64),
        goods(13, "points.16_steak", 400, Items.COOKED_BEEF, 16),
        goods(14, "points.16_golden_carrots", 1200, Items.GOLDEN_CARROT, 16),
        goods(15, "points.2_golden_apples", 2500, Items.GOLDEN_APPLE, 2),
        goods(16, "points.enchanted_golden_apple", ROUND_TEN, Items.ENCHANTED_GOLDEN_APPLE, 1),
        goods(17, "points.totem_of_undying", 7500, Items.TOTEM_OF_UNDYING, 1),

        // Rohstoffe
        goods(18, "points.32_coal", 400, Items.COAL, 32),
        goods(19, "points.16_iron_ingots", 2000, Items.IRON_INGOT, 16),
        goods(20, "points.8_gold_ingots", 1000, Items.GOLD_INGOT, 8),
        goods(21, "points.32_redstone", 800, Items.REDSTONE, 32),
        goods(22, "points.32_lapis_lazuli", 800, Items.LAPIS_LAZULI, 32),
        goods(23, "points.8_emeralds", 1500, Items.EMERALD, 8),
        goods(24, "points.1_diamond", DIAMOND_PRICE, Items.DIAMOND, 1),
        goods(25, "points.4_diamonds", 3750, Items.DIAMOND, 4),
        goods(26, "points.16_diamonds", ROUND_TEN, Items.DIAMOND, 16),

        // Selteneres
        goods(27, "points.netherite_ingot", ROUND_TEN, Items.NETHERITE_INGOT, 1),
        goods(28, "points.netherite_upgrade_template", 12000, Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE, 1),
        goods(29, "points.ancient_debris", 3000, Items.ANCIENT_DEBRIS, 1),
        goods(30, "points.8_obsidian", 800, Items.OBSIDIAN, 8),
        goods(31, "points.8_ender_pearls", 1500, Items.ENDER_PEARL, 8),
        goods(32, "points.4_blaze_rods", 1000, Items.BLAZE_ROD, 4),
        goods(33, "points.16_slimeballs", 800, Items.SLIME_BALL, 16),
        goods(34, "points.16_gunpowder", 800, Items.GUNPOWDER, 16),
        goods(35, "points.nether_star", 20000, Items.NETHER_STAR, 1),

        // Verzauberte Bücher
        book(36, "points.book_mending", 10000, Enchantments.MENDING, 1),
        book(37, "points.book_unbreaking_iii", 5000, Enchantments.UNBREAKING, 3),
        book(38, "points.book_efficiency_v", 7500, Enchantments.EFFICIENCY, 5),
        book(39, "points.book_fortune_iii", 10000, Enchantments.FORTUNE, 3),
        book(40, "points.book_silk_touch", 7500, Enchantments.SILK_TOUCH, 1),
        book(41, "points.book_sharpness_v", 7500, Enchantments.SHARPNESS, 5),
        book(42, "points.book_protection_iv", 7500, Enchantments.PROTECTION, 4),
        book(43, "points.book_looting_iii", 7500, Enchantments.LOOTING, 3),
        book(44, "points.book_feather_falling_iv", 5000, Enchantments.FEATHER_FALLING, 4),

        // Besonderes
        goods(45, "points.shulker_box", 10000, Items.SHULKER_BOX, 1),
        goods(46, "points.ender_chest", 3000, Items.ENDER_CHEST, 1),
        goods(47, "points.saddle", 1500, Items.SADDLE, 1),
        goods(48, "points.name_tag", 1000, Items.NAME_TAG, 1),
        goods(49, "points.32_firework_rockets", 1500, Items.FIREWORK_ROCKET, 32),
        goods(50, "points.trident", ROUND_TEN, Items.TRIDENT, 1),
        goods(51, "points.wither_skeleton_skull", 7500, Items.WITHER_SKELETON_SKULL, 1),
        goods(52, "points.beacon", 25000, Items.BEACON, 1),
        goods(53, "points.elytra", 2 * ROUND_TEN, Items.ELYTRA, 1));

    public static Offer at(int slot) {
        for (Offer offer : OFFERS) if (offer.slot() == slot) return offer;
        return null;
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> {
            if (e.getServer().getTickCount() % 60 != 0) return;
            ServerLevel arena = e.getServer().getLevel(ColosseumDimension.ARENA);
            if (arena == null) return;
            for (ServerPlayer p : arena.players()) {
                if (ArenaLayout.isLobbySide(p.getZ()) && !ArenaRun.INSTANCE.isParticipant(p.getUUID())) {
                    p.sendOverlayMessage(Lang.t("shop.account", PointsAccount.balance(p)).withStyle(ChatFormatting.GOLD));
                }
            }
        });
    }

    public static void open(ServerPlayer player) {
        if (ArenaRun.INSTANCE.isParticipant(player.getUUID())) {
            player.sendOverlayMessage(Lang.t("shop.after_run").withStyle(ChatFormatting.RED));
            return;
        }
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new ShopMenu(id, inventory), Lang.t("shop.title")));
    }

    /** Kauft ein Angebot. Mit dem Geld gehen die Waren auf das Abholkonto, beides liegt in der Spielerdatei. */
    public static boolean buy(ServerPlayer player, Offer offer) {
        if (!PointsAccount.take(player, offer.price())) {
            player.level().playSound(null, player.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 1F, 1F);
            return false;
        }
        if (offer.bottles() > 0) XpLedger.credit(player, offer.bottles());
        else XpLedger.addGoods(player, offer.make().apply(player.registryAccess()));
        player.level().playSound(null, player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 1F, 1.2F);
        player.sendSystemMessage(Lang.t("shop.bought", Lang.t(offer.name())).withStyle(ChatFormatting.GREEN));
        return true;
    }

    public static final class ShopMenu extends ChestMenu {
        private final SimpleContainer shelf;
        private final Player owner;
        private long lastClick = Long.MIN_VALUE / 2;

        ShopMenu(int id, Inventory inventory) {
            this(id, inventory, new SimpleContainer(SIZE));
        }

        private ShopMenu(int id, Inventory inventory, SimpleContainer shelf) {
            super(MenuType.GENERIC_9x6, id, inventory, shelf, 6);
            this.shelf = shelf;
            this.owner = inventory.player;
            refresh();
        }

        private void refresh() {
            int have = owner instanceof ServerPlayer p ? PointsAccount.balance(p) : 0;
            shelf.setItem(INFO_SLOT, label(new ItemStack(Items.SUNFLOWER), Lang.t("shop.your_account", have), ChatFormatting.GOLD, Lang.t("shop.info")));
            for (Offer offer : OFFERS) {
                boolean afford = have >= offer.price();
                ItemStack icon = offer.make().apply(owner.registryAccess());
                shelf.setItem(offer.slot(), label(icon, Lang.t(offer.name()), afford ? ChatFormatting.GREEN : ChatFormatting.RED, Lang.t("sign.points", offer.price())));
            }
        }

        private static ItemStack label(ItemStack stack, Component name, ChatFormatting color, Component line) {
            stack.set(DataComponents.CUSTOM_NAME, name.copy().withStyle(s -> s.withItalic(false).withColor(color)));
            stack.set(DataComponents.LORE, new ItemLore(List.of(line.copy().withStyle(s -> s.withItalic(false).withColor(ChatFormatting.GRAY)))));
            return stack;
        }

        /** Hier wandert nie ein Item: Jeder Klick ist höchstens ein Kauf. Doppelklicks kaufen nur einmal. */
        @Override
        public void clicked(int slot, int button, ContainerInput input, Player player) {
            if (player instanceof ServerPlayer p && input == ContainerInput.PICKUP && button == 0 && slot >= 0 && slot < SIZE) {
                Offer offer = at(slot);
                long now = p.level().getGameTime();
                if (offer != null && now - lastClick >= CLICK_PAUSE_TICKS) {
                    lastClick = now;
                    buy(p, offer);
                    refresh();
                }
            }
            sendAllDataToRemote();
        }

        @Override
        public ItemStack quickMoveStack(Player player, int slot) {
            return ItemStack.EMPTY;
        }

        @Override
        public boolean stillValid(Player player) {
            return player.isAlive() && ColosseumDimension.isArena(player.level()) && player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(BUTTON)) < REACH_SQUARED;
        }
    }

    private PointsShop() {}
}
