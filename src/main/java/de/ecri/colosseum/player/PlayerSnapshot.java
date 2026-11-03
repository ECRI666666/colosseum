package de.ecri.colosseum.player;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.MapCodec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import org.slf4j.Logger;

/**
 * Sicherung von Inventar, Rüstung, Zweithand, XP, Statuseffekten und Spielmodus.
 * Sie bleibt rohes NBT und wird erst beim Zurückgeben Slot für Slot gelesen. So kostet ein
 * unlesbares Item nur diesen einen Slot, und seine Daten stehen im Log statt verloren zu gehen.
 */
public record PlayerSnapshot(CompoundTag data) {
    public static final MapCodec<PlayerSnapshot> CODEC = CompoundTag.CODEC.fieldOf("data").xmap(PlayerSnapshot::new, PlayerSnapshot::data);
    private static final Logger LOG = LogUtils.getLogger();

    public static PlayerSnapshot capture(ServerPlayer player) {
        RegistryOps<Tag> ops = player.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        Inventory inventory = player.getInventory();

        ListTag items = new ListTag();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty()) continue;
            CompoundTag entry = new CompoundTag();
            entry.putInt("slot", slot);
            entry.put("item", ItemStack.CODEC.encodeStart(ops, stack).getOrThrow());
            items.add(entry);
        }

        CompoundTag data = new CompoundTag();
        data.put("items", items);
        data.put("effects", MobEffectInstance.CODEC.listOf().encodeStart(ops, java.util.List.copyOf(player.getActiveEffects())).getOrThrow());
        data.putInt("xp_level", player.experienceLevel);
        data.putFloat("xp_progress", player.experienceProgress);
        data.putInt("xp_total", player.totalExperience);
        data.putInt("game_mode", player.gameMode().getId());
        return new PlayerSnapshot(data);
    }

    /** Ersetzt den Zustand des Spielers vollständig durch die Sicherung. */
    public void restore(ServerPlayer player) {
        RegistryOps<Tag> ops = player.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        Inventory inventory = player.getInventory();
        inventory.clearContent();

        for (Tag tag : data.getListOrEmpty("items")) {
            if (!(tag instanceof CompoundTag entry)) continue;
            int slot = entry.getIntOr("slot", -1);
            Tag raw = entry.get("item");
            ItemStack stack = raw == null ? ItemStack.EMPTY : ItemStack.CODEC.parse(ops, raw).resultOrPartial(err -> LOG.error("Could not read item of {}: {}", player.getScoreboardName(), err)).orElse(ItemStack.EMPTY);
            if (stack.isEmpty() || slot < 0 || slot >= inventory.getContainerSize()) {
                LOG.error("Could not return item of {}, slot {}: {}", player.getScoreboardName(), slot, entry);
                continue;
            }
            inventory.setItem(slot, stack);
        }
        player.inventoryMenu.broadcastChanges();

        player.removeAllEffects();
        Tag effects = data.get("effects");
        if (effects != null) {
            MobEffectInstance.CODEC.listOf().parse(ops, effects)
                .resultOrPartial(err -> LOG.error("Could not read status effects of {}: {}", player.getScoreboardName(), err))
                .ifPresent(list -> list.forEach(player::addEffect));
        }

        player.experienceLevel = data.getIntOr("xp_level", 0);
        player.experienceProgress = data.getFloatOr("xp_progress", 0F);
        player.totalExperience = data.getIntOr("xp_total", 0);
        player.giveExperienceLevels(0);

        player.setGameMode(GameType.byId(data.getIntOr("game_mode", GameType.SURVIVAL.getId())));
    }
}
