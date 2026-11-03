package de.ecri.colosseum.shop;

import de.ecri.colosseum.Lang;
import de.ecri.colosseum.dimension.ColosseumDimension;
import de.ecri.colosseum.run.ArenaRun;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
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
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Das Cheat-Menü zum Testen: /colosseum debug, nur für Operatoren. Durchläufe, in denen es benutzt wurde,
 * kommen nicht auf die Bestenliste.
 */
public final class DebugMenu {
    public enum Action {
        POINTS_1K(10, Items.GOLD_NUGGET, "debug.points_1k"),
        POINTS_10K(11, Items.GOLD_INGOT, "debug.points_10k"),
        SHOP_15K(12, Items.EMERALD, "debug.shop_15k"),
        POWER(13, Items.LEVER, "debug.power"),
        DOORS(14, Items.IRON_DOOR, "debug.doors"),
        FINISH_WAVE(15, Items.DIAMOND_SWORD, "debug.finish_wave"),
        SKIP_ROUNDS(16, Items.CLOCK, "debug.skip_rounds"),
        BOSS(19, Items.WITHER_SKELETON_SKULL, "debug.boss"),
        DECOYS(20, Items.CREEPER_HEAD, "debug.decoys"),
        HEAL(21, Items.GOLDEN_APPLE, "debug.heal"),
        GOD(22, Items.TOTEM_OF_UNDYING, "debug.god");

        public final int slot;
        final Item icon;
        final String name;

        Action(int slot, Item icon, String name) {
            this.slot = slot;
            this.icon = icon;
            this.name = name;
        }
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> e.getDispatcher().register(
            Commands.literal("colosseum").then(Commands.literal("debug").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).executes(c -> {
                ServerPlayer p = c.getSource().getPlayerOrException();
                if (!ColosseumDimension.isArena(p.level())) {
                    c.getSource().sendFailure(Lang.t("debug.only_arena"));
                    return 0;
                }
                open(p);
                return 1;
            }))));
    }

    public static void open(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new Menu(id, inventory), Lang.t("debug.title")));
    }

    public static final class Menu extends ChestMenu {
        Menu(int id, Inventory inventory) {
            this(id, inventory, new SimpleContainer(27));
        }

        private Menu(int id, Inventory inventory, SimpleContainer shelf) {
            super(MenuType.GENERIC_9x3, id, inventory, shelf, 3);
            for (Action action : Action.values()) {
                ItemStack icon = new ItemStack(action.icon);
                icon.set(DataComponents.CUSTOM_NAME, Lang.t(action.name).withStyle(s -> s.withItalic(false).withColor(ChatFormatting.YELLOW)));
                icon.set(DataComponents.LORE, new ItemLore(List.<Component>of(Lang.t("debug.note").withStyle(s -> s.withItalic(false).withColor(ChatFormatting.GRAY)))));
                shelf.setItem(action.slot, icon);
            }
        }

        /** Hier wandert nie ein Item, jeder Klick löst nur die Aktion aus. */
        @Override
        public void clicked(int slot, int button, ContainerInput input, Player player) {
            if (player instanceof ServerPlayer p && input == ContainerInput.PICKUP && button == 0) {
                for (Action action : Action.values()) if (action.slot == slot) ArenaRun.INSTANCE.debug(p, action);
            }
            sendAllDataToRemote();
        }

        @Override
        public ItemStack quickMoveStack(Player player, int slot) {
            return ItemStack.EMPTY;
        }

        @Override
        public boolean stillValid(Player player) {
            return player.isAlive() && ColosseumDimension.isArena(player.level());
        }
    }

    private DebugMenu() {}
}
