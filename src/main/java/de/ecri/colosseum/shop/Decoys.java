package de.ecri.colosseum.shop;

import de.ecri.colosseum.Colosseum;
import de.ecri.colosseum.Lang;
import de.ecri.colosseum.dimension.ColosseumDimension;
import de.ecri.colosseum.wave.MobFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Der Lock-Creeper: fliegt wie ein Schneeball, setzt beim Aufprall einen kleinen Creeper ab, der alle Mobs in der
 * Nähe anlockt und am Ende explodiert. Die Explosion trifft nur Mobs der Welle, keine Spieler und keine Blöcke.
 */
public final class Decoys {
    public static final String TAG = "colosseum_decoy", PROJECTILE_TAG = "colosseum_decoy_thrown";
    private static final String ITEM_MARK = "colosseum_decoy";
    private static final double LURE_RANGE_SQUARED = 16 * 16, BLAST_RANGE_SQUARED = 5 * 5;
    private static final float BLAST_DAMAGE = 22.5F;
    /** Die letzten Ticks bläht er sich auf. Unter 30 bleiben, sonst explodiert der Creeper selbst. */
    private static final int SWELL_TICKS = 25, BELL_INTERVAL = 20, SEARCH_DOWN = 24;
    public static final double STOMP_RADIUS = 0.25;
    private static final int STOMP_PERIOD = 16;
    public static int fuseTicks = 160;

    private static final class Active {
        final Creeper creeper;
        final UUID owner;
        final Vec3 home;
        int left = fuseTicks;

        Active(Creeper creeper, UUID owner) {
            this.creeper = creeper;
            this.owner = owner;
            this.home = creeper.position();
        }

        /** Läuft im kleinen Kreis: Die Beine bewegen sich nur, wenn er sich bewegt, ohne KI läuft er sonst nicht. */
        void stomp() {
            double angle = 2 * Math.PI * left / STOMP_PERIOD;
            creeper.setPos(home.x + STOMP_RADIUS * Math.cos(angle), home.y, home.z + STOMP_RADIUS * Math.sin(angle));
            float yaw = (float) Math.toDegrees(angle);
            creeper.setYRot(yaw);
            creeper.setYBodyRot(yaw);
            creeper.setYHeadRot(yaw);
        }
    }

    /** Mobs schieben ihn nicht weg. Er tippelt nur selbst auf der Stelle, siehe stomp. */
    private static final class FrozenCreeper extends Creeper {
        FrozenCreeper(Level level) {
            super(EntityTypes.CREEPER, level);
        }

        @Override
        public boolean isPushable() {
            return false;
        }

        @Override
        public void push(Entity entity) {}

        @Override
        public void push(Vec3 impulse) {}

        @Override
        public void push(double xa, double ya, double za) {}
    }

    private final List<Active> active = new ArrayList<>();

    public static ItemStack item() {
        ItemStack stack = new ItemStack(Items.CREEPER_HEAD);
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(ITEM_MARK, true);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        stack.set(DataComponents.CUSTOM_NAME, Lang.t("offer.decoy").withStyle(s -> s.withItalic(false).withColor(ChatFormatting.GREEN)));
        stack.set(DataComponents.LORE, new ItemLore(List.<Component>of(Lang.t("offer.decoy_lore").withStyle(s -> s.withItalic(false).withColor(ChatFormatting.GRAY)))));
        return stack;
    }

    public static boolean isItem(ItemStack stack) {
        return stack.is(Items.CREEPER_HEAD) && stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().contains(ITEM_MARK);
    }

    public void throwFrom(ServerPlayer player, ItemStack stack) {
        stack.shrink(1);
        player.inventoryMenu.broadcastChanges();
        ServerLevel level = player.level();
        Snowball thrown = new Snowball(level, player, new ItemStack(Items.CREEPER_HEAD));
        thrown.addTag(PROJECTILE_TAG);
        thrown.shootFromRotation(player, player.getXRot(), player.getYRot(), 0F, 1.2F, 0.5F);
        level.addFreshEntity(thrown);
        level.playSound(null, player.blockPosition(), SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, 1F, 0.6F);
    }

    /** Setzt den Creeper auf den ersten festen Boden unter dem Aufprall. */
    public Creeper land(ServerLevel level, BlockPos hit, UUID owner) {
        BlockPos pos = hit;
        for (int i = 0; i < SEARCH_DOWN && !level.getBlockState(pos.below()).isSolid(); i++) pos = pos.below();
        Creeper creeper = new FrozenCreeper(level);
        creeper.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.getRandom().nextFloat() * 360F, 0F);
        creeper.setNoAi(true);
        creeper.setPersistenceRequired();
        creeper.addTag(TAG);
        AttributeInstance scale = creeper.getAttribute(Attributes.SCALE);
        if (scale != null) scale.addPermanentModifier(new AttributeModifier(Colosseum.id("decoy_scale"), -0.5, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        active.add(new Active(creeper, owner));
        level.addFreshEntity(creeper);
        return creeper;
    }

    /** Der nächste Lock-Creeper in Reichweite dieses Mobs. */
    public Creeper lureFor(Mob mob) {
        Creeper best = null;
        double bestDistance = LURE_RANGE_SQUARED;
        for (Active decoy : active) {
            double distance = decoy.creeper.distanceToSqr(mob);
            if (decoy.creeper.isAlive() && distance < bestDistance) {
                bestDistance = distance;
                best = decoy.creeper;
            }
        }
        return best;
    }

    public void tick(MinecraftServer server, ServerLevel level) {
        for (Active decoy : new ArrayList<>(active)) {
            decoy.left--;
            decoy.stomp();
            if (decoy.left % BELL_INTERVAL == 0) level.playSound(null, decoy.creeper.blockPosition(), SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.HOSTILE, 3F, 1.4F);
            if (decoy.left == SWELL_TICKS) decoy.creeper.setSwellDir(1);
            if (decoy.left <= 0) {
                explode(server, level, decoy);
                active.remove(decoy);
            }
        }
    }

    private void explode(MinecraftServer server, ServerLevel level, Active decoy) {
        Vec3 at = decoy.creeper.position();
        decoy.creeper.discard();
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y + 0.5, at.z, 1, 0.0, 0.0, 0.0, 0.0);
        level.playSound(null, BlockPos.containing(at), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 4F, 1F);
        ServerPlayer owner = server.getPlayerList().getPlayer(decoy.owner);
        DamageSource source = owner != null ? owner.damageSources().playerAttack(owner) : level.damageSources().generic();
        for (Mob mob : MobFactory.all(level)) {
            if (mob.distanceToSqr(at) < BLAST_RANGE_SQUARED) mob.hurtServer(level, source, BLAST_DAMAGE);
        }
    }

    /** Am Ende einer Welle oder des Durchlaufs: ohne Explosion weg. */
    public void clear() {
        active.forEach(decoy -> decoy.creeper.discard());
        active.clear();
    }

    public int count() {
        return active.size();
    }
}
