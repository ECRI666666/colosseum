package de.ecri.colosseum.selftest;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import de.ecri.colosseum.Colosseum;
import de.ecri.colosseum.dimension.ArenaLayout;
import de.ecri.colosseum.dimension.ColosseumDimension;
import de.ecri.colosseum.player.PlayerSnapshot;
import de.ecri.colosseum.portal.PortalFrame;
import de.ecri.colosseum.registry.ModAttachments;
import de.ecri.colosseum.registry.ModBlocks;
import de.ecri.colosseum.reward.XpLedger;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.NameAndId;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

/**
 * Nur mit -Dcolosseum.selftest=true. Prüft Meilenstein 1 mit echten ServerPlayer-Objekten auf einem
 * echten Server und beendet ihn. Fehlt in der fertigen Jar. Das Hineinlaufen selbst simuliert der Test,
 * indem er jeden Tick dieselbe Block-Methode aufruft wie die Spiel-Physik.
 */
@EventBusSubscriber(modid = Colosseum.MOD_ID)
public final class ColosseumSelfTest {
    private static final Logger LOG = LogUtils.getLogger();
    private static final boolean ON = Boolean.getBoolean("colosseum.selftest");
    private static final Deque<Runnable> STEPS = new ArrayDeque<>();
    static final List<ServerPlayer> PLAYERS = new ArrayList<>();
    static final java.util.Map<ServerPlayer, BlockPos> HOLD = new java.util.HashMap<>();
    private static int wait = 40, errors = 0, checks = 0;
    private static boolean started = false;
    private static MinecraftServer server;
    private static ServerLevel world;

    static void check(String name, boolean ok) {
        checks++;
        if (ok) LOG.info("COLTEST ok      {}", name);
        else { errors++; LOG.error("COLTEST FEHLER  {}", name); }
    }

    static void then(Runnable step) { STEPS.add(step); }
    /** Für Warteschleifen: Schritt nach ticks Ticks wiederholen, vor allen anderen. */
    static void pushFront(Runnable step, int ticks) { STEPS.addFirst(step); wait = ticks; }
    static void sleep(int ticks) { STEPS.add(() -> wait = ticks); }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post e) {
        if (!ON) return;
        server = e.getServer();
        for (ServerPlayer p : new ArrayList<>(PLAYERS)) {
            if (p.isRemoved() && !HOLD.containsKey(p)) continue;
            BlockPos hold = HOLD.get(p);
            if (hold != null && p.level().getBlockState(hold).is(ModBlocks.PORTAL.get())) {
                p.level().getBlockState(hold).entityInside(p.level(), hold, p, InsideBlockEffectApplier.NOOP, true);
            }
            // Ein echter Client bestätigt den Dimensionswechsel per Paket, erst dann läuft die Pendel-Sperre ab.
            if (p.isChangingDimension()) p.hasChangedDimension();
            if (!p.isRemoved() && p.isAlive()) p.doTick();
        }
        if (wait > 0) { wait--; return; }
        if (!started) { started = true; world = server.overworld(); plan(); }
        try {
            while (wait == 0 && !STEPS.isEmpty()) STEPS.poll().run();
        } catch (Exception ex) {
            errors++;
            LOG.error("COLTEST FEHLER  Abbruch", ex);
            STEPS.clear();
        }
        if (STEPS.isEmpty() && wait == 0) {
            LOG.info("COLTEST FERTIG Prüfungen={} Fehler={}", checks, errors);
            server.halt(false);
        }
    }

    static ServerPlayer join(String name, UUID id) {
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(id, name), false);
        // Gleicher Ablauf wie PrepareSpawnTask beim echten Login: Spielerdatei lesen, Level wählen, laden, platzieren.
        try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(LOG)) {
            var input = server.getPlayerList().loadPlayerData(new NameAndId(cookie.gameProfile()))
                .map(tag -> (ValueInput) TagValueInput.create(reporter, server.registryAccess(), tag));
            var saved = input.flatMap(i -> i.read(ServerPlayer.SavedPosition.MAP_CODEC));
            ServerLevel level = saved.flatMap(ServerPlayer.SavedPosition::dimension).map(server::getLevel).orElse(world);
            Vec3 pos = saved.flatMap(ServerPlayer.SavedPosition::position).orElse(new Vec3(3.5, 200, 3.5));
            ServerPlayer p = new ServerPlayer(server, level, cookie.gameProfile(), cookie.clientInformation());
            input.ifPresent(p::load);
            p.snapTo(pos.x, pos.y, pos.z, 0F, 0F);
            Connection c = new Connection(PacketFlow.SERVERBOUND);
            new EmbeddedChannel(c);
            server.getPlayerList().placeNewPlayer(c, p, cookie);
            PLAYERS.add(p);
            return p;
        }
    }

    private static void frame(BlockPos origin, Direction.Axis axis, int w, int h, Block block, boolean corners) {
        Direction r = axis == Direction.Axis.X ? Direction.EAST : Direction.SOUTH;
        for (int i = -1; i <= w; i++) for (int j = -1; j <= h; j++) {
            boolean sideI = i == -1 || i == w, sideJ = j == -1 || j == h;
            BlockPos pos = origin.relative(r, i).above(j);
            if (sideI && sideJ && !corners) world.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            else world.setBlockAndUpdate(pos, sideI || sideJ ? block.defaultBlockState() : Blocks.AIR.defaultBlockState());
        }
    }

    private static void ignite(ServerPlayer p, BlockPos floor) {
        ItemStack flint = new ItemStack(Items.FLINT_AND_STEEL);
        p.setItemInHand(InteractionHand.MAIN_HAND, flint);
        p.gameMode.useItemOn(p, p.level(), flint, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(floor), Direction.UP, floor, false));
    }

    private static int portals(ServerLevel l, BlockPos a, BlockPos b) {
        int n = 0;
        for (BlockPos pos : BlockPos.betweenClosed(a, b)) if (l.getBlockState(pos).is(ModBlocks.PORTAL.get())) n++;
        return n;
    }

    private static ItemStack[] copyInventory(ServerPlayer p) {
        ItemStack[] out = new ItemStack[p.getInventory().getContainerSize()];
        for (int i = 0; i < out.length; i++) out[i] = p.getInventory().getItem(i).copy();
        return out;
    }

    private static boolean same(ItemStack[] want, ServerPlayer p, boolean ignoreExtra) {
        for (int i = 0; i < want.length; i++) {
            if (ignoreExtra && want[i].isEmpty()) continue;
            if (!ItemStack.matches(want[i], p.getInventory().getItem(i))) { LOG.error("COLTEST Slot {}: erwartet {} ist {}", i, want[i], p.getInventory().getItem(i)); return false; }
        }
        return true;
    }

    static int count(ServerPlayer p, net.minecraft.world.item.Item item) {
        int n = 0;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) if (p.getInventory().getItem(i).is(item)) n += p.getInventory().getItem(i).getCount();
        return n;
    }

    private static void fillRich(ServerPlayer p) {
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        sword.enchant(p.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SHARPNESS), 5);
        sword.set(DataComponents.CUSTOM_NAME, Component.literal("Testklinge"));
        sword.setDamageValue(17);
        p.getInventory().setItem(0, sword);
        p.getInventory().setItem(8, new ItemStack(Items.DIRT, 64));
        p.getInventory().setItem(35, new ItemStack(Items.ENDER_PEARL, 7));
        p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.NETHERITE_CHESTPLATE));
        p.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.CHAINMAIL_LEGGINGS));
        p.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.GOLDEN_BOOTS));
        p.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
        p.addEffect(new MobEffectInstance(MobEffects.SPEED, 20000, 1));
        p.experienceLevel = 17;
        p.experienceProgress = 0.5F;
        p.totalExperience = 400;
    }

    private static boolean emptyArenaState(ServerPlayer p) {
        return p.getInventory().isEmpty() && p.getActiveEffects().isEmpty() && p.experienceLevel == 0 && p.totalExperience == 0;
    }

    private static boolean inArena(ServerPlayer p) { return ColosseumDimension.isArena(p.level()); }

    private static void plan() {
        BlockPos base = new BlockPos(0, 200, 0);
        for (int x = -3; x <= 9; x++) for (int z = -3; z <= 9; z++) world.setChunkForced(x, z, true);
        for (BlockPos pos : BlockPos.betweenClosed(base.offset(-5, -2, -5), base.offset(60, -2, 40))) world.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());

        final ServerPlayer[] a = new ServerPlayer[1];
        final ItemStack[][] want = new ItemStack[1][];
        final UUID aId = UUID.nameUUIDFromBytes("a".getBytes());

        // --- Dimension
        then(() -> {
            ServerLevel arena = server.getLevel(ColosseumDimension.ARENA);
            check("Arena-Dimension existiert", arena != null);
            check("Arena hat feste Zeit", arena.dimensionType().hasFixedTime());
            check("Arena: Betten setzen keinen Spawn und Anker aus", !arena.environmentAttributes().getValue(net.minecraft.world.attribute.EnvironmentAttributes.RESPAWN_ANCHOR_WORKS, BlockPos.ZERO));
            check("Kolosseum-Sand liegt", arena.getBlockState(new BlockPos(0, ArenaLayout.FLOOR_Y, 0)).is(Blocks.SAND));
            check("Rückkehr-Portal brennt", arena.getBlockState(ArenaLayout.EXIT_PORTAL).is(ModBlocks.PORTAL.get()));
            a[0] = join("Anna", aId);
            check("Testspieler ist im Überlebensmodus", a[0].gameMode() == GameType.SURVIVAL);
        });

        // --- Rahmen-Erkennung
        then(() -> {
            BlockPos o = base.offset(0, 0, 0);
            frame(o, Direction.Axis.X, 1, 3, Blocks.SANDSTONE, true);
            check("Innen 1 breit wird abgelehnt", PortalFrame.findEither(world, o).isEmpty());
            frame(o, Direction.Axis.X, 2, 2, Blocks.SANDSTONE, true);
            check("Innen 2 hoch wird abgelehnt", PortalFrame.findEither(world, o).isEmpty());
            frame(o, Direction.Axis.X, 2, 3, Blocks.CUT_SANDSTONE, true);
            check("Geschnittener Sandstein wird abgelehnt", PortalFrame.findEither(world, o).isEmpty());
            frame(o, Direction.Axis.X, 2, 3, Blocks.SANDSTONE, true);
            world.setBlockAndUpdate(o.above(3), Blocks.AIR.defaultBlockState());
            check("Loch im Rahmen wird abgelehnt", PortalFrame.findEither(world, o).isEmpty());
            frame(o, Direction.Axis.X, 2, 3, Blocks.SANDSTONE, true);
            world.setBlockAndUpdate(o.above(1), Blocks.DIRT.defaultBlockState());
            check("Block im Innenraum wird abgelehnt", PortalFrame.findEither(world, o).isEmpty());
        });

        // --- Zünden mit echtem Rechtsklick über den Spielmodus
        then(() -> {
            BlockPos o = base.offset(10, 0, 0);
            frame(o, Direction.Axis.X, 2, 3, Blocks.SANDSTONE, true);
            a[0].setPos(o.getX() + 0.5, o.getY(), o.getZ() + 3.5);
            ignite(a[0], o.below());
            check("4x5 Ost-West zündet komplett", portals(world, o, o.offset(1, 2, 0)) == 6);
            check("Feuerzeug verliert 1 Haltbarkeit", a[0].getMainHandItem().getDamageValue() == 1);
            BlockPos z = base.offset(20, 0, 0);
            frame(z, Direction.Axis.Z, 3, 4, Blocks.SANDSTONE, false);
            ignite(a[0], z.below());
            check("Nord-Süd ohne Ecken zündet", portals(world, z, z.offset(0, 3, 2)) == 12);
            BlockPos big = base.offset(30, 0, 0);
            frame(big, Direction.Axis.X, 21, 21, Blocks.SANDSTONE, true);
            ignite(a[0], big.below());
            check("Größtes Portal 23x23 zündet", portals(world, big, big.offset(20, 20, 0)) == 441);
            frame(base.offset(30, 0, 20), Direction.Axis.X, 22, 3, Blocks.SANDSTONE, true);
            check("Zu breites Portal wird abgelehnt", PortalFrame.findEither(world, base.offset(30, 0, 20)).isEmpty());
        });

        // --- Rahmen abbauen löscht das Portal
        then(() -> {
            BlockPos z = base.offset(20, 0, 0);
            world.setBlockAndUpdate(z.above(4), Blocks.AIR.defaultBlockState());
            check("Rahmen abgebaut: Portal erlischt ganz", portals(world, z, z.offset(0, 3, 2)) == 0);
        });

        // --- Items reisen nicht
        final ItemEntity[] thrown = new ItemEntity[1];
        final BlockPos entry = base.offset(10, 0, 0);
        then(() -> {
            thrown[0] = new ItemEntity(world, entry.getX() + 0.5, entry.getY() + 0.5, entry.getZ() + 0.5, new ItemStack(Items.DIAMOND));
            thrown[0].setNoGravity(true);
            world.addFreshEntity(thrown[0]);
        });
        sleep(50);
        then(() -> check("Geworfenes Item bleibt in der Oberwelt", !thrown[0].isRemoved() && thrown[0].level() == world));

        // --- Hinweg mit vollem Inventar, Cursor-Item und Crafting-Feld
        then(() -> {
            ServerPlayer p = a[0];
            p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            fillRich(p);
            want[0] = copyInventory(p);
            p.containerMenu.setCarried(new ItemStack(Items.STONE, 3));
            p.inventoryMenu.getSlot(1).set(new ItemStack(Items.APPLE, 2));
            p.setPos(entry.getX() + 0.5, entry.getY(), entry.getZ() + 0.5);
            HOLD.put(p, entry);
        });
        sleep(10);
        then(() -> check("Portal braucht Wartezeit, noch nicht gereist", !inArena(a[0])));
        sleep(45);
        then(() -> {
            HOLD.remove(a[0]);
            ServerPlayer p = a[0];
            check("Spieler ist in der Arena", inArena(p));
            check("In der Arena: Inventar, Effekte und XP leer", emptyArenaState(p));
            check("In der Arena: Abenteuermodus", p.gameMode() == GameType.ADVENTURE);
            check("Sicherung ist vorhanden", p.hasData(ModAttachments.SNAPSHOT));
            check("Rückkehrpunkt ist der Eingang", p.getData(ModAttachments.RETURN_POINT).pos().distanceTo(new Vec3(entry.getX() + 0.5, entry.getY(), entry.getZ() + 0.5)) < 0.01);
            check("Cursor leer", p.containerMenu.getCarried().isEmpty());
            check("Crafting-Feld leer", p.inventoryMenu.getSlot(1).getItem().isEmpty());
            p.getInventory().setItem(0, new ItemStack(Items.IRON_SWORD));
        });

        // --- Arena-Schutz
        then(() -> {
            ServerPlayer p = a[0];
            ServerLevel arena = p.level();
            BlockPos floor = new BlockPos(5, ArenaLayout.FLOOR_Y, 5);
            check("Abbauen abgelehnt", !p.gameMode.destroyBlock(floor) && arena.getBlockState(floor).is(Blocks.SAND));
            arena.explode(null, 5.5, ArenaLayout.FLOOR_Y + 1, 5.5, 5F, Level.ExplosionInteraction.TNT);
            check("Explosion zerstört nichts", arena.getBlockState(floor).is(Blocks.SAND));
            var creeper = net.minecraft.world.entity.EntityTypes.CREEPER.create(arena, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
            check("Mobs dürfen nichts zerstören", !net.neoforged.neoforge.event.EventHooks.canEntityGrief(arena, creeper));
            ItemStack dirt = new ItemStack(Items.DIRT);
            p.setItemInHand(InteractionHand.MAIN_HAND, dirt);
            p.gameMode.useItemOn(p, arena, dirt, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(floor), Direction.UP, floor, false));
            check("Platzieren abgelehnt", arena.getBlockState(floor.above()).isAir());
            p.getInventory().setItem(0, new ItemStack(Items.IRON_SWORD));
        });

        // --- Rückweg: erst die Pendel-Sperre von der Ankunft ablaufen lassen, wie beim Weglaufen im Spiel
        sleep(30);
        then(() -> {
            ServerPlayer p = a[0];
            BlockPos exit = ArenaLayout.EXIT_PORTAL;
            p.setPos(exit.getX() + 0.5, exit.getY(), exit.getZ() + 0.5);
            HOLD.put(p, exit);
        });
        sleep(50);
        then(() -> {
            ServerPlayer p = a[0];
            HOLD.remove(p);
            check("Spieler ist zurück in der Oberwelt", p.level() == world);
            check("Alle Slots, Rüstung und Zweithand exakt zurück", same(want[0], p, true));
            check("Cursor-Item ist zurück", count(p, Items.STONE) == 3);
            check("Crafting-Item ist zurück", count(p, Items.APPLE) == 2);
            check("Arena-Item ist weg", count(p, Items.IRON_SWORD) == 0);
            check("XP exakt zurück", p.experienceLevel == 17 && p.experienceProgress == 0.5F && p.totalExperience == 400);
            var speed = p.getEffect(MobEffects.SPEED);
            check("Statuseffekt zurück", speed != null && speed.getAmplifier() == 1 && speed.getDuration() > 19700);
            check("Überlebensmodus zurück", p.gameMode() == GameType.SURVIVAL);
            check("Sicherung gelöscht", !p.hasData(ModAttachments.SNAPSHOT));
            check("Zurück am Eingang", p.position().distanceTo(new Vec3(entry.getX() + 0.5, entry.getY(), entry.getZ() + 0.5)) < 0.01);
            HOLD.put(p, entry);
        });
        sleep(70);
        then(() -> {
            check("Kein Pendeln: Stehen im Eingang reist nicht sofort wieder", !inArena(a[0]));
            HOLD.remove(a[0]);
            a[0].setPos(entry.getX() + 3.5, entry.getY(), entry.getZ() + 3.5);
        });
        sleep(30);

        // --- Neuanmeldung in der Arena: echte Spielerdatei schreiben und wieder laden
        then(() -> { HOLD.put(a[0], entry); a[0].setPos(entry.getX() + 0.5, entry.getY(), entry.getZ() + 0.5); });
        sleep(50);
        then(() -> {
            HOLD.remove(a[0]);
            check("Zweite Reise in die Arena klappt", inArena(a[0]));
            want[0] = null;
            PLAYERS.remove(a[0]);
            server.getPlayerList().remove(a[0]);
            a[0] = join("Anna", aId);
            check("Nach Neuanmeldung noch in der Arena", inArena(a[0]));
            check("Sicherung hat die Datei überlebt", a[0].hasData(ModAttachments.SNAPSHOT));
            check("Inventar weiter leer", a[0].getInventory().isEmpty());
            BlockPos exit = ArenaLayout.EXIT_PORTAL;
            a[0].setPos(exit.getX() + 0.5, exit.getY(), exit.getZ() + 0.5);
            a[0].setPortalCooldown(0);
            HOLD.put(a[0], exit);
        });
        sleep(50);
        then(() -> {
            HOLD.remove(a[0]);
            ServerPlayer p = a[0];
            check("Nach Neuanmeldung: zurück in der Oberwelt", p.level() == world);
            check("Nach Neuanmeldung: Schwert mit Name und Verzauberung zurück", p.getInventory().getItem(0).is(Items.DIAMOND_SWORD) && p.getInventory().getItem(0).getDamageValue() == 17 && "Testklinge".equals(p.getInventory().getItem(0).getHoverName().getString()) && p.getInventory().getItem(0).isEnchanted());
            check("Nach Neuanmeldung: Rüstung zurück", p.getItemBySlot(EquipmentSlot.CHEST).is(Items.NETHERITE_CHESTPLATE));
            check("Nach Neuanmeldung: XP zurück", p.experienceLevel == 17);
            check("Nach Neuanmeldung: Überlebensmodus", p.gameMode() == GameType.SURVIVAL);
            p.setPos(entry.getX() + 3.5, entry.getY(), entry.getZ() + 3.5);
        });

        // --- Tod in der Arena, mit und ohne keepInventory
        for (boolean keep : new boolean[] {false, true}) {
            String tag = keep ? "keepInventory an: " : "keepInventory aus: ";
            then(() -> {
                world.getGameRules().set(GameRules.KEEP_INVENTORY, keep, server);
                a[0].setPortalCooldown(0);
                tp(a[0], true);
                check(tag + "in der Arena", inArena(a[0]));
                a[0].getInventory().setItem(3, new ItemStack(Items.GOLD_INGOT, 5));
                a[0].experienceLevel = 3;
                a[0].totalExperience = 30;
                ServerLevel arena = a[0].level();
                a[0].setHealth(0F);
                a[0].die(arena.damageSources().generic());
            });
            sleep(5);
            then(() -> {
                ServerLevel arena = server.getLevel(ColosseumDimension.ARENA);
                AABB box = new AABB(-40, 0, -40, 40, 255, 40);
                check(tag + "keine Items in der Arena", arena.getEntitiesOfClass(ItemEntity.class, box).isEmpty());
                check(tag + "keine XP-Kugeln in der Arena", arena.getEntitiesOfClass(ExperienceOrb.class, box).isEmpty());
                ServerPlayer old = a[0];
                PLAYERS.remove(old);
                var connection = old.connection;
                connection.handleClientCommand(new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
                a[0] = connection.player;
                connection.markClientLoaded();
                PLAYERS.add(a[0]);
                ServerPlayer p = a[0];
                check(tag + "Respawn in der Halle", inArena(p) && ArenaLayout.isLobbySide(p.getZ()));
                check(tag + "In der Halle ohne Arena-Sachen", p.getInventory().isEmpty());
                check(tag + "Sicherung bleibt bis zum Portal", p.hasData(ModAttachments.SNAPSHOT));
                tp(p, false);
                check(tag + "Beim Verlassen in der Oberwelt", !inArena(p));
                check(tag + "Sachen zurück", p.getInventory().getItem(0).is(Items.DIAMOND_SWORD) && p.getItemBySlot(EquipmentSlot.CHEST).is(Items.NETHERITE_CHESTPLATE));
                check(tag + "Arena-Gold ist weg", count(p, Items.GOLD_INGOT) == 0);
                check(tag + "XP zurück", p.experienceLevel == 17 && p.totalExperience == 400);
                check(tag + "Überlebensmodus", p.gameMode() == GameType.SURVIVAL);
                check(tag + "Sicherung gelöscht", !p.hasData(ModAttachments.SNAPSHOT));
            });
        }
        then(() -> world.getGameRules().set(GameRules.KEEP_INVENTORY, false, server));

        // --- /tp rein und raus
        then(() -> {
            ServerPlayer p = a[0];
            want[0] = copyInventory(p);
            tp(p, true);
            check("/tp rein: gesichert und leer", inArena(p) && p.hasData(ModAttachments.SNAPSHOT) && p.getInventory().isEmpty());
            tp(p, false);
            check("/tp raus: alles zurück", !inArena(p) && !p.hasData(ModAttachments.SNAPSHOT) && same(want[0], p, false));
        });

        // --- Zwei Spieler zur selben Zeit vertauschen nichts
        final ServerPlayer[] b = new ServerPlayer[1];
        then(() -> {
            b[0] = join("Ben", UUID.nameUUIDFromBytes("b".getBytes()));
            b[0].getInventory().setItem(4, new ItemStack(Items.EMERALD, 11));
            a[0].getInventory().setItem(4, new ItemStack(Items.REDSTONE, 22));
            tp(a[0], true);
            tp(b[0], true);
            check("Zwei Spieler: beide in der Arena und leer", inArena(a[0]) && inArena(b[0]) && a[0].getInventory().isEmpty() && b[0].getInventory().isEmpty());
            tp(b[0], false);
            tp(a[0], false);
            check("Zwei Spieler: jeder hat nur seine Sachen", count(b[0], Items.EMERALD) == 11 && count(b[0], Items.REDSTONE) == 0 && count(a[0], Items.REDSTONE) == 22 && count(a[0], Items.EMERALD) == 0);
        });

        // --- Kaputtes Item kostet nur diesen Slot
        then(() -> {
            ServerPlayer p = b[0];
            tp(p, true);
            CompoundTag bad = new CompoundTag();
            CompoundTag item = new CompoundTag();
            item.putString("id", "gibtsnicht:item");
            item.putInt("count", 1);
            bad.putInt("slot", 20);
            bad.put("item", item);
            PlayerSnapshot snapshot = p.getData(ModAttachments.SNAPSHOT);
            ListTag items = snapshot.data().getListOrEmpty("items");
            items.add(bad);
            snapshot.data().put("items", items);
            tp(p, false);
            check("Kaputtes Item: Rest kommt trotzdem zurück", count(p, Items.EMERALD) == 11 && !p.hasData(ModAttachments.SNAPSHOT));
        });

        // --- Regel: Tod führt in die Oberwelt, nur die gewonnenen Flaschen kommen mit
        then(() -> {
            ServerPlayer p = b[0];
            p.getInventory().clearContent();
            p.getInventory().setItem(2, new ItemStack(Items.EMERALD, 4));
            XpLedger.credit(p, 9);
            check("Flaschen außerhalb der Arena werden nicht gutgeschrieben", XpLedger.balance(p) == 0);
            tp(p, true);
            p.getInventory().setItem(0, new ItemStack(Items.NETHERITE_SWORD));
            p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
            XpLedger.credit(p, 5);
            ServerPlayer n = dieAndRespawn(p);
            b[0] = n;
            check("Tod: Spieler steht in der Halle", ColosseumDimension.isArena(n.level()) && ArenaLayout.isLobbySide(n.getZ()));
            check("Tod: Flaschen bleiben auf dem Konto", XpLedger.balance(n) == 5 && count(n, Items.EXPERIENCE_BOTTLE) == 0);
            tp(n, false);
            check("Verlassen: Spieler ist in der Oberwelt", n.level() == world);
            check("Verlassen: alle gewonnenen Flaschen kommen mit", count(n, Items.EXPERIENCE_BOTTLE) == 5);
            check("Tod: eigenes Inventar ist zurück", count(n, Items.EMERALD) == 4);
            check("Tod: Arena-Waffe bleibt zurück", count(n, Items.NETHERITE_SWORD) == 0);
            check("Tod: Arena-Rüstung bleibt zurück", count(n, Items.DIAMOND_CHESTPLATE) == 0);
            check("Tod: Konto ist leer", XpLedger.balance(n) == 0);
        });
        then(() -> {
            ServerPlayer p = b[0];
            tp(p, true);
            XpLedger.credit(p, 3);
            PLAYERS.remove(p);
            server.getPlayerList().remove(p);
            b[0] = join("Ben", UUID.nameUUIDFromBytes("b".getBytes()));
            check("Konto überlebt die Spielerdatei", XpLedger.balance(b[0]) == 3);
            tp(b[0], false);
            check("Verlassen zahlt aus", count(b[0], Items.EXPERIENCE_BOTTLE) == 8);
            tp(b[0], true);
            tp(b[0], false);
            check("Keine doppelte Auszahlung", count(b[0], Items.EXPERIENCE_BOTTLE) == 8);
        });
        then(() -> {
            ServerPlayer p = b[0];
            p.getInventory().clearContent();
            for (int i = 0; i < 36; i++) p.getInventory().setItem(i, new ItemStack(Items.DIRT, 64));
            tp(p, true);
            XpLedger.credit(p, 130);
            tp(p, false);
            int dropped = 0;
            for (ItemEntity item : world.getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(4))) if (item.getItem().is(Items.EXPERIENCE_BOTTLE)) dropped += item.getItem().getCount();
            check("Volles Inventar: 130 Flaschen liegen vor den Füßen", dropped == 130 && count(p, Items.EXPERIENCE_BOTTLE) == 0);
            p.getInventory().clearContent();
        });
        then(() -> {
            ServerPlayer p = b[0];
            tp(p, true);
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "execute in colosseum:arena run spawnpoint Ben 0 65 0");
            ServerPlayer n = dieAndRespawn(p);
            b[0] = n;
            check("Eigener Spawnpunkt: Tod führt trotzdem in die Halle", ColosseumDimension.isArena(n.level()) && ArenaLayout.isLobbySide(n.getZ()));
            tp(n, false);
        });

        // --- Der ganze Durchlauf
        RunSelfTest.plan(server, a, b);

        // --- Rückkehrort zugebaut: Spawn statt im Block stecken
        then(() -> {
            ServerPlayer p = b[0];
            BlockPos spot = base.offset(0, 0, 30);
            p.setPos(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
            tp(p, true);
            world.setBlockAndUpdate(spot, Blocks.STONE.defaultBlockState());
            world.setBlockAndUpdate(spot.above(), Blocks.STONE.defaultBlockState());
            var t = de.ecri.colosseum.player.ArenaTransfer.exitTransition(p);
            check("Zugebauter Rückkehrort: Ziel ist ein anderer Ort", t.position().distanceTo(new Vec3(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5)) > 1);
            tp(p, false);
        });
    }

    static ServerPlayer dieAndRespawn(ServerPlayer p) {
        p.setHealth(0F);
        p.die(p.level().damageSources().generic());
        PLAYERS.remove(p);
        // Dasselbe Paket, das ein echter Client beim Klick auf „Respawn“ schickt.
        var connection = p.connection;
        connection.handleClientCommand(new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
        ServerPlayer n = connection.player;
        connection.markClientLoaded();
        PLAYERS.add(n);
        return n;
    }

    static void tp(ServerPlayer p, boolean toArena) {
        String dim = toArena ? ColosseumDimension.ARENA.identifier().toString() : "minecraft:overworld";
        String pos = toArena ? "0.5 65 26.5" : "3.5 200 3.5";
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "execute in " + dim + " run tp " + p.getScoreboardName() + " " + pos);
    }

    private ColosseumSelfTest() {}
}
