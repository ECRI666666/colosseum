package de.ecri.colosseum.wave;

import de.ecri.colosseum.Colosseum;
import de.ecri.colosseum.run.Difficulty;
import de.ecri.colosseum.run.PlayerClass;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;

/** Baut die Mobs einer Welle: Vanilla-Mobs mit Ausrüstung und Effekten passend zu Runde und Schwierigkeit. */
public final class MobFactory {
    public static final String TAG = "colosseum_wave";
    /** Das Reittier zählt nicht als Gegner, den man erledigen muss. */
    public static final String PASSIVE_TAG = "colosseum_passive";
    public static final String BOSS_TAG = "colosseum_boss";
    private static final Identifier STATS = Colosseum.id("difficulty");

    public static Mob spawn(ServerLevel level, BlockPos pos, WaveComposer.Kind kind, int round, Difficulty difficulty, RandomSource random) {
        return switch (kind) {
            case ZOMBIE -> single(level, EntityTypes.ZOMBIE, pos, round, difficulty, random, true);
            case HUSK -> single(level, EntityTypes.HUSK, pos, round, difficulty, random, true);
            case SKELETON -> single(level, EntityTypes.SKELETON, pos, round, difficulty, random, false);
            case DROWNED -> {
                Mob m = single(level, EntityTypes.DROWNED, pos, round, difficulty, random, false);
                m.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.TRIDENT));
                yield m;
            }
            case CHICKEN_JOCKEY -> chickenJockey(level, pos, round, difficulty, random);
            case SPIDER_JOCKEY -> spiderJockey(level, pos, round, difficulty, random);
            case RAVAGER_RIDER -> ravager(level, pos, round, difficulty, random);
            case BOSS -> boss(level, pos, round, difficulty, random);
            case SPIDER -> {
                EntityType<? extends Mob> spiderType = random.nextBoolean() ? EntityTypes.SPIDER : EntityTypes.CAVE_SPIDER;
                Mob spider = base(level, spiderType, pos, round, difficulty, random);
                level.addFreshEntity(spider);
                yield spider;
            }
        };
    }

    /** Der Arenameister: großer Wither-Skelett-Krieger, wird mit jeder Boss-Runde stärker. */
    private static Mob boss(ServerLevel level, BlockPos pos, int round, Difficulty difficulty, RandomSource random) {
        Mob boss = base(level, EntityTypes.WITHER_SKELETON, pos, round, difficulty, random);
        boss.addTag(BOSS_TAG);
        modify(boss, Attributes.MAX_HEALTH, 9.0 + round / 5.0, STATS.withSuffix("_boss"));
        modify(boss, Attributes.ATTACK_DAMAGE, 0.5 + round * 0.05, STATS.withSuffix("_boss"));
        modify(boss, Attributes.SCALE, 0.5, STATS.withSuffix("_boss"));
        modify(boss, Attributes.KNOCKBACK_RESISTANCE, 0.0, STATS.withSuffix("_boss"));
        boss.setHealth(boss.getMaxHealth());
        boss.setItemSlot(EquipmentSlot.MAINHAND, PlayerClass.enchanted(level.registryAccess(), Items.NETHERITE_SWORD, Enchantments.SHARPNESS, 2));
        boss.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.NETHERITE_CHESTPLATE));
        boss.addEffect(new MobEffectInstance(MobEffects.GLOWING, MobEffectInstance.INFINITE_DURATION));
        boss.setCustomName(de.ecri.colosseum.Lang.t("mob.arena_master").withStyle(net.minecraft.ChatFormatting.DARK_RED));
        boss.setCustomNameVisible(true);
        level.addFreshEntity(boss);
        return boss;
    }

    private static <T extends Mob> T base(ServerLevel level, EntityType<T> type, BlockPos pos, int round, Difficulty difficulty, RandomSource random) {
        T mob = type.create(level, EntitySpawnReason.MOB_SUMMONED);
        mob.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, random.nextFloat() * 360F, 0F);
        mob.setPersistenceRequired();
        mob.addTag(TAG);
        for (EquipmentSlot slot : EquipmentSlot.VALUES) mob.setDropChance(slot, 0F);
        modify(mob, Attributes.FOLLOW_RANGE, 2.0, STATS.withSuffix("_range"));
        if (difficulty.mobStats != 1.0) {
            modify(mob, Attributes.MAX_HEALTH, difficulty.mobStats - 1.0, STATS);
            modify(mob, Attributes.ATTACK_DAMAGE, difficulty.mobStats - 1.0, STATS);
        }
        if (difficulty.mobSpeed != 1.0) modify(mob, Attributes.MOVEMENT_SPEED, difficulty.mobSpeed - 1.0, STATS);
        mob.setHealth(mob.getMaxHealth());
        scaleEffects(mob, round, random);
        return mob;
    }

    private static void modify(Mob mob, Holder<Attribute> attribute, double amount, Identifier id) {
        AttributeInstance instance = mob.getAttribute(attribute);
        if (instance != null) instance.addPermanentModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    /** Bis Runde 14 ist niemand schneller, danach langsam mehr Mobs, höchstens 40 %. */
    public static double speedChance(int round) {
        return round < 15 ? 0.0 : Math.min(0.4, (round - 14) * 0.03);
    }

    /** Die zweite Tempo-Stufe kommt erst ab Runde 35. */
    public static int speedLevel(int round) {
        return round >= 35 ? 1 : 0;
    }

    /** Mit jeder Runde wird Schnelligkeit und Stärke wahrscheinlicher. */
    private static void scaleEffects(Mob mob, int round, RandomSource random) {
        if (random.nextDouble() < speedChance(round)) mob.addEffect(new MobEffectInstance(MobEffects.SPEED, MobEffectInstance.INFINITE_DURATION, speedLevel(round)));
        if (round >= 10 && random.nextDouble() < Math.min(0.5, (round - 9) * 0.04)) mob.addEffect(new MobEffectInstance(MobEffects.STRENGTH, MobEffectInstance.INFINITE_DURATION, round >= 20 ? 1 : 0));
    }

    private static Mob single(ServerLevel level, EntityType<? extends Mob> type, BlockPos pos, int round, Difficulty difficulty, RandomSource random, boolean melee) {
        Mob mob = base(level, type, pos, round, difficulty, random);
        equip(mob, level.registryAccess(), round, random, melee);
        level.addFreshEntity(mob);
        return mob;
    }

    private static void equip(Mob mob, HolderLookup.Provider r, int round, RandomSource random, boolean melee) {
        double chance = Math.min(0.9, round * 0.06);
        Item[][] tiers = {
            {Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS},
            {Items.GOLDEN_HELMET, Items.GOLDEN_CHESTPLATE, Items.GOLDEN_LEGGINGS, Items.GOLDEN_BOOTS},
            {Items.CHAINMAIL_HELMET, Items.CHAINMAIL_CHESTPLATE, Items.CHAINMAIL_LEGGINGS, Items.CHAINMAIL_BOOTS},
            {Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS},
            {Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS},
        };
        int tier = round < 4 ? -1 : round < 7 ? random.nextInt(2) : round < 12 ? 1 + random.nextInt(2) : round < 18 ? 2 + random.nextInt(2) : 3 + random.nextInt(2);
        if (tier >= 0) {
            EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
            for (int i = 0; i < 4; i++) if (random.nextDouble() < chance) mob.setItemSlot(slots[i], new ItemStack(tiers[tier][i]));
        }
        if (melee) {
            if (random.nextDouble() < chance) {
                Item sword = round < 6 ? Items.STONE_SWORD : round < 14 ? Items.IRON_SWORD : Items.DIAMOND_SWORD;
                mob.setItemSlot(EquipmentSlot.MAINHAND, round >= 12 ? PlayerClass.enchanted(r, sword, Enchantments.SHARPNESS, 1 + (round - 10) / 5) : new ItemStack(sword));
            }
        } else {
            mob.setItemSlot(EquipmentSlot.MAINHAND, round >= 10 ? PlayerClass.enchanted(r, Items.BOW, Enchantments.POWER, Math.min(5, 1 + (round - 10) / 4)) : new ItemStack(Items.BOW));
        }
    }

    private static Mob chickenJockey(ServerLevel level, BlockPos pos, int round, Difficulty difficulty, RandomSource random) {
        Chicken chicken = base(level, EntityTypes.CHICKEN, pos, round, difficulty, random);
        chicken.setChickenJockey(true);
        chicken.addTag(PASSIVE_TAG);
        chicken.addEffect(new MobEffectInstance(MobEffects.SPEED, MobEffectInstance.INFINITE_DURATION, 1));
        EntityType<? extends Zombie> riderType = random.nextBoolean() ? EntityTypes.ZOMBIE : EntityTypes.HUSK;
        Zombie rider = base(level, riderType, pos, round, difficulty, random);
        rider.setBaby(true);
        equip(rider, level.registryAccess(), round, random, true);
        mount(level, chicken, rider);
        return rider;
    }

    private static Mob spiderJockey(ServerLevel level, BlockPos pos, int round, Difficulty difficulty, RandomSource random) {
        EntityType<? extends Mob> spiderType = random.nextBoolean() ? EntityTypes.SPIDER : EntityTypes.CAVE_SPIDER;
        Mob spider = base(level, spiderType, pos, round, difficulty, random);
        EntityType<? extends Mob> riderType = random.nextBoolean() ? EntityTypes.SKELETON : EntityTypes.STRAY;
        Mob rider = base(level, riderType, pos, round, difficulty, random);
        equip(rider, level.registryAccess(), round, random, false);
        mount(level, spider, rider);
        return rider;
    }

    private static Mob ravager(ServerLevel level, BlockPos pos, int round, Difficulty difficulty, RandomSource random) {
        Mob ravager = base(level, EntityTypes.RAVAGER, pos, round, difficulty, random);
        Mob rider;
        if (random.nextBoolean()) {
            Piglin piglin = base(level, EntityTypes.PIGLIN, pos, round, difficulty, random);
            piglin.setImmuneToZombification(true);
            rider = piglin;
        } else {
            rider = base(level, EntityTypes.PILLAGER, pos, round, difficulty, random);
        }
        rider.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.CROSSBOW));
        mount(level, ravager, rider);
        return rider;
    }

    private static void mount(ServerLevel level, Mob mount, Mob rider) {
        level.addFreshEntity(mount);
        level.addFreshEntity(rider);
        rider.startRiding(mount, true, false);
    }

    /** Punkte für einen Kill: stärkere Gegner und spätere Runden bringen mehr. Das Reittier-Huhn bringt nichts. */
    public static int points(Mob mob, int round) {
        if (mob.entityTags().contains(PASSIVE_TAG)) return 0;
        if (mob.entityTags().contains(BOSS_TAG)) return 1000 + 50 * round;
        int base = mob.getType() == EntityTypes.RAVAGER ? 300 : mob.getVehicle() != null ? 100 : mob.getType() == EntityTypes.DROWNED || mob.getType() == EntityTypes.SPIDER || mob.getType() == EntityTypes.CAVE_SPIDER ? 80 : 50;
        return base + 5 * round;
    }

    /** Alle Mobs einer laufenden Welle im Arena-Bereich. */
    public static List<Mob> all(ServerLevel level) {
        return level.getEntitiesOfClass(Mob.class, de.ecri.colosseum.dimension.ArenaLayout.BOUNDS, m -> m.entityTags().contains(TAG));
    }

    private MobFactory() {}
}
