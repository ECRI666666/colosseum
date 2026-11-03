package de.ecri.colosseum.registry;

import com.mojang.serialization.Codec;
import de.ecri.colosseum.Colosseum;
import de.ecri.colosseum.player.PlayerSnapshot;
import de.ecri.colosseum.player.ReturnPoint;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Daten am Spieler. Sie liegen in derselben Spielerdatei wie das Inventar. Geleertes Inventar und
 * Sicherung werden deshalb immer gemeinsam gespeichert, auch ein Absturz kann sie nicht trennen.
 */
public final class ModAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Colosseum.MOD_ID);

    public static final Supplier<AttachmentType<PlayerSnapshot>> SNAPSHOT = ATTACHMENTS.register("snapshot",
        () -> AttachmentType.builder(() -> new PlayerSnapshot(new net.minecraft.nbt.CompoundTag())).serialize(PlayerSnapshot.CODEC).copyOnDeath().build());

    public static final Supplier<AttachmentType<ReturnPoint>> RETURN_POINT = ATTACHMENTS.register("return_point",
        () -> AttachmentType.builder(() -> new ReturnPoint(Level.OVERWORLD, Vec3.ZERO, 0F, 0F)).serialize(ReturnPoint.CODEC).copyOnDeath().build());

    public static final Supplier<AttachmentType<Integer>> EARNED_BOTTLES = ATTACHMENTS.register("earned_bottles",
        () -> AttachmentType.builder(() -> 0).serialize(Codec.INT.fieldOf("bottles")).copyOnDeath().build());

    /** Das feste Punktekonto für den Shop in der Halle. */
    public static final Supplier<AttachmentType<Integer>> POINTS_ACCOUNT = ATTACHMENTS.register("points_account",
        () -> AttachmentType.builder(() -> 0).serialize(Codec.INT.fieldOf("points")).copyOnDeath().build());

    /** Sicherung der Punkte des laufenden Durchlaufs, schon mit dem Faktor der Schwierigkeit. */
    public static final Supplier<AttachmentType<Integer>> RUN_POINTS = ATTACHMENTS.register("run_points",
        () -> AttachmentType.builder(() -> 0).serialize(Codec.INT.fieldOf("points")).copyOnDeath().build());

    /** Gekaufte Waren, die beim Verlassen der Arena ausgegeben werden. */
    public static final Supplier<AttachmentType<List<ItemStack>>> PENDING_GOODS = ATTACHMENTS.register("pending_goods",
        () -> AttachmentType.<List<ItemStack>>builder(() -> List.of()).serialize(ItemStack.CODEC.listOf().fieldOf("items")).copyOnDeath().build());

    private ModAttachments() {}
}
