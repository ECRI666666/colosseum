package de.ecri.colosseum.shop;

import de.ecri.colosseum.dimension.ArenaLayout;
import de.ecri.colosseum.dimension.ColosseumDimension;
import de.ecri.colosseum.run.ArenaRun;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.Vec3;

/**
 * Der Amboss in der Grube: Drei Stufen pro Waffe. Auf jeder Stufe wählt man aus bis zu drei zufälligen
 * Verzauberungen. Das zuletzt Gewählte wird dabei voll gemacht, nach der letzten Stufe ist alles voll.
 * Die Auswahl hängt an der Waffe, neu öffnen würfelt also nicht neu.
 */
public final class PackAPunch {
    private static final int[] PRICES = {2500, 5000, 7500};
    public static final int STAGES = PRICES.length;
    private static final int[] SLOTS = {11, 13, 15};
    private static final String STAGE = "colosseum_pap_stage", LAST = "colosseum_pap_last", SEED = "colosseum_pap_seed";
    private static final double REACH_SQUARED = 8 * 8;

    private static final List<ResourceKey<Enchantment>> SWORD = List.of(Enchantments.SHARPNESS, Enchantments.SMITE, Enchantments.BANE_OF_ARTHROPODS, Enchantments.FIRE_ASPECT, Enchantments.KNOCKBACK, Enchantments.SWEEPING_EDGE);
    private static final List<ResourceKey<Enchantment>> AXE = List.of(Enchantments.SHARPNESS, Enchantments.SMITE, Enchantments.BANE_OF_ARTHROPODS, Enchantments.FIRE_ASPECT, Enchantments.KNOCKBACK);
    private static final List<ResourceKey<Enchantment>> BOW = List.of(Enchantments.POWER, Enchantments.PUNCH, Enchantments.FLAME, Enchantments.INFINITY);
    private static final List<ResourceKey<Enchantment>> CROSSBOW = List.of(Enchantments.MULTISHOT, Enchantments.PIERCING, Enchantments.QUICK_CHARGE);
    private static final List<ResourceKey<Enchantment>> TRIDENT = List.of(Enchantments.IMPALING, Enchantments.LOYALTY, Enchantments.CHANNELING);
    private static final List<ResourceKey<Enchantment>> MACE = List.of(Enchantments.DENSITY, Enchantments.BREACH, Enchantments.WIND_BURST, Enchantments.FIRE_ASPECT);

    public static boolean isWeapon(ItemStack stack) {
        return stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES) || stack.is(Items.BOW) || stack.is(Items.CROSSBOW) || stack.is(Items.TRIDENT) || stack.is(Items.MACE);
    }

    private static CompoundTag data(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    public static int stage(ItemStack stack) {
        return data(stack).getIntOr(STAGE, 0);
    }

    /** Preis der nächsten Stufe. */
    public static int price(ItemStack stack) {
        return PRICES[stage(stack)];
    }

    /** Die Auswahl für die nächste Stufe: bis zu drei, die zur Waffe und zu ihren Verzauberungen passen. */
    public static List<Holder<Enchantment>> choices(ItemStack stack, HolderLookup.Provider r) {
        List<ResourceKey<Enchantment>> pool = stack.is(Items.BOW) ? BOW : stack.is(Items.CROSSBOW) ? CROSSBOW : stack.is(Items.TRIDENT) ? TRIDENT : stack.is(Items.MACE) ? MACE : stack.is(ItemTags.AXES) ? AXE : SWORD;
        var lookup = r.lookupOrThrow(Registries.ENCHANTMENT);
        List<Holder<Enchantment>> open = new ArrayList<>();
        for (ResourceKey<Enchantment> key : pool) {
            Holder<Enchantment> holder = lookup.getOrThrow(key);
            boolean fits = true;
            for (Holder<Enchantment> have : stack.getEnchantments().keySet()) fits &= !have.equals(holder) && Enchantment.areCompatible(have, holder);
            if (fits) open.add(holder);
        }
        Collections.shuffle(open, new Random(data(stack).getLongOr(SEED, 0L) + stage(stack)));
        return List.copyOf(open.subList(0, Math.min(SLOTS.length, open.size())));
    }

    /** Die nächste Stufe. Ohne passende Verzauberung (choice null) wird nur das Vorhandene voll gemacht. */
    /** Neue Verzauberungen starten bei der Hälfte, aufgerundet: Schärfe III, Rückstoß I. */
    public static int startLevel(Holder<Enchantment> enchantment) {
        return (enchantment.value().getMaxLevel() + 1) / 2;
    }

    public static void apply(ItemStack stack, Holder<Enchantment> choice, HolderLookup.Provider r) {
        CompoundTag tag = data(stack);
        int stage = tag.getIntOr(STAGE, 0) + 1;
        var lookup = r.lookupOrThrow(Registries.ENCHANTMENT);
        tag.getString(LAST).flatMap(id -> lookup.listElements().filter(h -> h.key().identifier().toString().equals(id)).findFirst())
            .ifPresent(last -> stack.enchant(last, last.value().getMaxLevel()));
        if (choice != null) {
            stack.enchant(choice, stage == STAGES ? choice.value().getMaxLevel() : startLevel(choice));
            choice.unwrapKey().ifPresent(key -> tag.putString(LAST, key.identifier().toString()));
        }
        if (stage == STAGES) {
            // Zum Schluss ist alles voll, auch was die Waffe schon mitgebracht hat
            for (Holder<Enchantment> have : List.copyOf(stack.getEnchantments().keySet())) stack.enchant(have, have.value().getMaxLevel());
        }
        tag.putInt(STAGE, stage);
        CustomData.set(DataComponents.CUSTOM_DATA, stack, tag);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("★".repeat(stage) + " ").append(new ItemStack(stack.getItem()).getHoverName()).withStyle(s -> s.withItalic(false).withColor(ChatFormatting.AQUA)));
    }

    public static void open(ServerPlayer player, ItemStack weapon) {
        CompoundTag tag = data(weapon);
        if (!tag.contains(SEED)) {
            tag.putLong(SEED, player.level().getRandom().nextLong());
            CustomData.set(DataComponents.CUSTOM_DATA, weapon, tag);
        }
        Component title = de.ecri.colosseum.Lang.t("anvil.title", stage(weapon) + 1, STAGES);
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new PunchMenu(id, inventory, weapon), title));
    }

    public static final class PunchMenu extends ChestMenu {
        private final ItemStack weapon;
        private final List<Holder<Enchantment>> choices;

        PunchMenu(int id, Inventory inventory, ItemStack weapon) {
            this(id, inventory, weapon, new SimpleContainer(27));
        }

        private PunchMenu(int id, Inventory inventory, ItemStack weapon, SimpleContainer shelf) {
            super(MenuType.GENERIC_9x3, id, inventory, shelf, 3);
            this.weapon = weapon;
            this.choices = PackAPunch.choices(weapon, inventory.player.registryAccess());
            int stage = stage(weapon);
            Component price = de.ecri.colosseum.Lang.t("sign.points", PRICES[stage]);
            Component note = de.ecri.colosseum.Lang.t(stage == STAGES - 1 ? "anvil.note_last" : stage > 0 ? "anvil.note_mid" : "anvil.note_first");
            for (int i = 0; i < choices.size(); i++) {
                Holder<Enchantment> holder = choices.get(i);
                int level = stage == STAGES - 1 ? holder.value().getMaxLevel() : startLevel(holder);
                shelf.setItem(SLOTS[i], icon(Items.ENCHANTED_BOOK.getDefaultInstance(), Enchantment.getFullname(holder, level), price, note));
            }
            if (choices.isEmpty()) shelf.setItem(SLOTS[1], icon(Items.ANVIL.getDefaultInstance(), de.ecri.colosseum.Lang.t("anvil.reinforce"), price, note));
        }

        public List<Holder<Enchantment>> choices() {
            return choices;
        }

        private static ItemStack icon(ItemStack stack, Component name, Component price, Component note) {
            stack.set(DataComponents.CUSTOM_NAME, name.copy().withStyle(s -> s.withItalic(false).withColor(ChatFormatting.AQUA)));
            stack.set(DataComponents.LORE, new ItemLore(List.of(line(price, ChatFormatting.GOLD), line(note, ChatFormatting.GRAY))));
            return stack;
        }

        private static Component line(Component text, ChatFormatting color) {
            return text.copy().withStyle(s -> s.withItalic(false).withColor(color));
        }

        /** Hier wandert nie ein Item. Ein Klick auf ein Angebot kauft es und schließt das Menü. */
        @Override
        public void clicked(int slot, int button, ContainerInput input, Player player) {
            if (player instanceof ServerPlayer p && input == ContainerInput.PICKUP && button == 0) {
                for (int i = 0; i < SLOTS.length; i++) {
                    if (slot != SLOTS[i]) continue;
                    if (i < choices.size()) ArenaRun.INSTANCE.punch(p, weapon, choices.get(i));
                    else if (choices.isEmpty() && i == 1) ArenaRun.INSTANCE.punch(p, weapon, null);
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
            return player.isAlive() && ColosseumDimension.isArena(player.level()) && ArenaRun.INSTANCE.isAlive(player.getUUID())
                && player.getMainHandItem() == weapon && player.distanceToSqr(Vec3.atCenterOf(ArenaLayout.ANVIL)) < REACH_SQUARED;
        }
    }

    private PackAPunch() {}
}
