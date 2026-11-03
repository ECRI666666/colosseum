package de.ecri.colosseum.selftest;

import static de.ecri.colosseum.selftest.ColosseumSelfTest.check;
import static de.ecri.colosseum.selftest.ColosseumSelfTest.count;
import static de.ecri.colosseum.selftest.ColosseumSelfTest.dieAndRespawn;
import static de.ecri.colosseum.selftest.ColosseumSelfTest.sleep;
import static de.ecri.colosseum.selftest.ColosseumSelfTest.then;
import static de.ecri.colosseum.selftest.ColosseumSelfTest.tp;

import de.ecri.colosseum.board.Leaderboard;
import de.ecri.colosseum.dimension.ArenaBuilder;
import de.ecri.colosseum.dimension.ArenaDoors;
import de.ecri.colosseum.dimension.ArenaLayout;
import de.ecri.colosseum.dimension.ColosseumDimension;
import de.ecri.colosseum.loot.SupplyChests;
import de.ecri.colosseum.portal.PortalFrame;
import de.ecri.colosseum.registry.ModBlocks;
import de.ecri.colosseum.reward.XpLedger;
import de.ecri.colosseum.shop.PerkHud;
import de.ecri.colosseum.shop.PointsAccount;
import de.ecri.colosseum.shop.PointsShop;
import de.ecri.colosseum.run.ArenaRun;
import de.ecri.colosseum.run.Difficulty;
import de.ecri.colosseum.run.PlayerClass;
import de.ecri.colosseum.shop.MysteryBox;
import de.ecri.colosseum.shop.PackAPunch;
import de.ecri.colosseum.shop.Perk;
import de.ecri.colosseum.shop.Shop;
import de.ecri.colosseum.wave.MobFactory;
import de.ecri.colosseum.wave.WaveComposer;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Prüft Bauwerk, Lobby, Wellen, Jockeys, Wiederbelebung, Kisten und Auszahlung. */
final class RunSelfTest {
    private static final ArenaRun RUN = ArenaRun.INSTANCE;
    private static MinecraftServer server;
    private static ServerLevel arena;

    private static void press(ServerPlayer p, BlockPos pos) {
        p.gameMode.useItemOn(p, p.level(), ItemStack.EMPTY, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
    }

    private static void toSand(ServerPlayer p, double x, double z) {
        p.teleportTo(x, ArenaLayout.STAND_Y, z);
    }

    private static boolean free(BlockPos pos, boolean barsBlock) {
        if (barsBlock && arena.getBlockState(pos).is(Blocks.IRON_BARS)) return false;
        return passable(pos);
    }

    private static boolean passable(BlockPos pos) {
        var st = arena.getBlockState(pos);
        return st.isAir() || st.is(Blocks.LANTERN) || st.is(Blocks.CARPET.pick(net.minecraft.world.item.DyeColor.RED)) || st.is(Blocks.WALL_TORCH) || st.is(Blocks.WALL_BANNER.pick(net.minecraft.world.item.DyeColor.RED)) || st.is(Blocks.IRON_BARS) || st.is(Blocks.LEVER) || st.is(Blocks.DARK_OAK_WALL_SIGN) || st.is(Blocks.POLISHED_BLACKSTONE_BUTTON);
    }

    /** Erreichbare Felder ab start, mit denselben Regeln wie zu Fuß. */
    private static java.util.Set<Long> seenFrom(BlockPos start) {
        return walk(start, false);
    }

    /** Wie seenFrom, aber Gitter sind Wände. */
    private static java.util.Set<Long> sealed(BlockPos start) {
        return walk(start, true);
    }

    private static java.util.Set<Long> walk(BlockPos start, boolean barsBlock) {
        java.util.Set<Long> seen = new java.util.HashSet<>();
        java.util.ArrayDeque<BlockPos> open = new java.util.ArrayDeque<>();
        open.add(start);
        seen.add(start.asLong());
        while (!open.isEmpty()) {
            BlockPos at = open.poll();
            for (Direction d : Direction.Plane.HORIZONTAL) {
                BlockPos n = at.relative(d);
                for (int dy : new int[] {1, 0, -1, -2, -3, -4}) {
                    BlockPos m = n.above(dy);
                    boolean head = dy <= 0 || free(at.above(2), barsBlock);
                    boolean drop = true;
                    for (int k = 0; k > dy; k--) drop &= free(n.above(k), barsBlock);
                    if (head && drop && free(m, barsBlock) && free(m.above(), barsBlock) && arena.getBlockState(m.below()).isSolid()) {
                        if (seen.add(m.asLong())) open.add(m);
                        break;
                    }
                }
            }
        }
        return seen;
    }

    private static String signText(BlockPos pos) {
        if (!(arena.getBlockEntity(pos) instanceof net.minecraft.world.level.block.entity.SignBlockEntity sign)) return "";
        StringBuilder out = new StringBuilder();
        for (var line : sign.getText(net.minecraft.world.level.block.entity.SignTextSlot.FRONT).getMessages(false)) out.append(line.getString()).append(' ');
        return out.toString();
    }

    private static boolean gateClosed() {
        return arena.getBlockState(new BlockPos(0, ArenaLayout.STAND_Y, ArenaLayout.GATE_Z)).is(Blocks.IRON_BARS);
    }

    private static boolean mobGateClosed() {
        return arena.getBlockState(ArenaLayout.MOB_GATES.get(0).bars().get(0)).is(Blocks.IRON_BARS);
    }

    private static List<Mob> mobs() { return MobFactory.all(arena); }

    private static void killAll() { for (Mob m : mobs()) m.discard(); }

    private static int enchant(ItemStack stack, net.minecraft.resources.ResourceKey<net.minecraft.world.item.enchantment.Enchantment> key) {
        return EnchantmentHelper.getItemEnchantmentLevel(arena.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key), stack);
    }

    /** Wartet über Schleifen im Test-Takt, bis die Phase erreicht ist. Gibt nach 2000 Ticks auf. */
    private static void waitFor(java.util.function.BooleanSupplier done, String what) {
        then(new Runnable() {
            int left = 400;
            @Override public void run() {
                if (done.getAsBoolean()) return;
                if (--left <= 0) { check("Warten auf: " + what, false); return; }
                ColosseumSelfTest.pushFront(this, 1);
            }
        });
    }

    /** Beide im Kampf: Runde jump, Mobs fertig gespawnt. */
    private static void waitSpawned(int round) {
        waitFor(() -> RUN.phase() == ArenaRun.Phase.WAVE && RUN.round() == round && RUN.queued() == 0, "Runde " + round + " gespawnt");
    }

    private static void finishWave(int round) {
        then(RunSelfTest::killAll);
        waitFor(() -> RUN.phase() == ArenaRun.Phase.PAUSE && RUN.round() == round, "Pause nach Runde " + round);
    }

    static void plan(MinecraftServer s, ServerPlayer[] a, ServerPlayer[] b) {
        server = s;
        ArenaRun.pauseTicks = 30;
        ArenaRun.countdownTicks = 10;
        ArenaRun.spawnInterval = 1;

        // --- Bauwerk
        then(() -> {
            arena = server.getLevel(ColosseumDimension.ARENA);
            ArenaBuilder.forceChunks(arena, true);
            check("Sand in der Mitte, Sandstein darunter", arena.getBlockState(new BlockPos(0, 64, 0)).is(Blocks.SAND) && arena.getBlockState(new BlockPos(0, 63, 0)).is(Blocks.SANDSTONE));
            check("Mauer im Osten", arena.getBlockState(new BlockPos(ArenaLayout.A + 1, 66, 5)).is(Blocks.STONE_BRICKS));
            check("Mauer ist höher als die Dächer", arena.getBlockState(new BlockPos(ArenaLayout.A + 1, 76, 5)).isSolid());
            check("Mittelteil mit Durchgang", arena.getBlockState(new BlockPos(0, 68, 0)).isSolid() && arena.getBlockState(new BlockPos(10, 66, 0)).isSolid() && arena.getBlockState(new BlockPos(0, 66, 2)).isAir());
            check("Dach und Brücke", arena.getBlockState(new BlockPos(28, 68, 15)).isSolid() && arena.getBlockState(new BlockPos(21, 68, 7)).is(Blocks.OAK_PLANKS) && arena.getBlockState(new BlockPos(20, 66, 7)).isAir());
            check("Grube liegt tiefer", arena.getBlockState(new BlockPos(35, 62, 0)).is(Blocks.SAND) && arena.getBlockState(new BlockPos(35, 64, 0)).isAir());
            check("Tribüne steigt an", arena.getBlockState(new BlockPos(-ArenaLayout.A - 12, 74, 4)).isSolid());
            check("Eingangstor ist zu", gateClosed());
            check("Zehn Mob-Tore sind zu", ArenaLayout.MOB_GATES.size() == 10 && ArenaLayout.MOB_GATES.stream().allMatch(g -> g.bars().stream().allMatch(x -> arena.getBlockState(x).is(Blocks.IRON_BARS))));
            boolean chambers = true;
            for (ArenaLayout.MobGate g : ArenaLayout.MOB_GATES) {
                BlockPos sp = g.spawn();
                chambers &= ArenaLayout.ring(sp.getX(), sp.getZ()) <= ArenaLayout.TRIBUNE && arena.getBlockState(sp).isAir() && arena.getBlockState(sp.above(3)).isAir() && arena.getBlockState(sp.above(4)).isSolid() && arena.getBlockState(sp.below()).isSolid();
            }
            check("Mob-Kammern sind frei und geschlossen", chambers);
            boolean spots = true;
            for (BlockPos sp : ArenaLayout.CHEST_SPOTS) {
                boolean ok = ArenaLayout.isSand(sp.getX(), sp.getZ()) && arena.getBlockState(sp).isAir() && arena.getBlockState(sp.above()).isAir() && arena.getBlockState(sp.below()).isSolid();
                if (!ok) org.slf4j.LoggerFactory.getLogger("COLTEST").error("COLTEST Kistenplatz belegt: {} ist {} auf {}", sp, arena.getBlockState(sp), arena.getBlockState(sp.below()));
                spots &= ok;
            }
            check("Alle Kistenplätze sind frei", spots);
            check("Exfil-Rahmen steht, Portal ist aus", PortalFrame.find(arena, ArenaLayout.EXTRACTION_PORTAL, Direction.Axis.X).isPresent() && arena.getBlockState(ArenaLayout.EXTRACTION_PORTAL).isAir());
            check("Exfil-Knopf ist noch nicht da", arena.getBlockState(ArenaLayout.EXFIL_BUTTON).isAir());
            BlockPos spawn = BlockPos.containing(ArenaLayout.LOBBY_SPAWN);
            check("Lobby-Spawn ist frei", passable(spawn) && arena.getBlockState(spawn.above()).isAir() && arena.getBlockState(spawn.below()).isSolid());
            check("Portal in der Lobby brennt", arena.getBlockState(ArenaLayout.EXIT_PORTAL).is(ModBlocks.PORTAL.get()));
            for (PlayerClass c : PlayerClass.values()) check("Knopf " + c.title(), arena.getBlockState(c.button()).is(Blocks.POLISHED_BLACKSTONE_BUTTON));
            for (Difficulty d : Difficulty.values()) check("Knopf " + d.title(), arena.getBlockState(d.button()).is(Blocks.POLISHED_BLACKSTONE_BUTTON));
            check("Hebel links und rechts", arena.getBlockState(ArenaLayout.LEVER_LEFT).is(Blocks.LEVER) && arena.getBlockState(ArenaLayout.LEVER_RIGHT).is(Blocks.LEVER));
        });
        // --- Kann man überall hinlaufen? Gitter gelten dabei als offen.
        then(() -> {
            java.util.Set<Long> seen = new java.util.HashSet<>();
            java.util.ArrayDeque<BlockPos> open = new java.util.ArrayDeque<>();
            BlockPos start = BlockPos.containing(ArenaLayout.LOBBY_SPAWN);
            open.add(start);
            seen.add(start.asLong());
            while (!open.isEmpty()) {
                BlockPos at = open.poll();
                for (Direction d : Direction.Plane.HORIZONTAL) {
                    BlockPos n = at.relative(d);
                    // Eine Stufe hoch, gerade aus oder bis zu 4 Blöcke hinunter
                    for (int dy : new int[] {1, 0, -1, -2, -3, -4}) {
                        BlockPos m = n.above(dy);
                        boolean head = dy <= 0 || passable(at.above(2));
                        boolean drop = true;
                        for (int k = 0; k > dy; k--) drop &= passable(n.above(k));
                        boolean free = head && drop && passable(m) && passable(m.above()) && arena.getBlockState(m.below()).isSolid();
                        if (free) {
                            if (seen.add(m.asLong())) open.add(m);
                            break;
                        }
                    }
                }
            }
            check("Von der Lobby durchs Tor in die Mitte laufen (" + seen.size() + " Felder)", seen.contains(BlockPos.containing(ArenaLayout.CENTER).asLong()));
            boolean gates = true;
            for (ArenaLayout.MobGate g : ArenaLayout.MOB_GATES) gates &= seen.contains(g.spawn().asLong());
            check("Aus allen sechs Kammern kommt man in den Sand", gates);
            boolean spots = true;
            for (BlockPos c : ArenaLayout.CHEST_SPOTS) spots &= seen.contains(c.asLong());
            check("Alle Kistenplätze sind erreichbar", spots);
            check("Dächer, Mittelteil und Grube sind erreichbar", seen.contains(new BlockPos(28, 69, 15).asLong()) && seen.contains(new BlockPos(-28, 69, -15).asLong()) && seen.contains(new BlockPos(28, 69, -15).asLong()) && seen.contains(new BlockPos(-28, 69, 15).asLong()) && seen.contains(new BlockPos(14, 69, 2).asLong()) && seen.contains(new BlockPos(35, 63, 3).asLong()));
            check("Exfil-Tor ist erreichbar", seen.contains(ArenaLayout.EXFIL_BUTTON.asLong()) && seen.contains(new BlockPos(0, 65, 11).asLong()));
            check("Exfil-Tor steht im ersten Bereich", ArenaLayout.zone(ArenaLayout.EXTRACTION_PORTAL.getX(), ArenaLayout.EXTRACTION_PORTAL.getZ()) == ArenaLayout.ZONE_START);
            for (int zone = 1; zone <= 4; zone++) {
                final int z0 = zone;
                check("Bereich " + zone + " hat Mob-Tore und Kistenplätze", ArenaLayout.MOB_GATES.stream().anyMatch(g -> g.zone() == z0) && ArenaLayout.CHEST_SPOTS.stream().anyMatch(c -> ArenaLayout.zone(c.getX(), c.getZ()) == z0));
            }
            check("Erster Bereich hat Platz für zwei Kisten", ArenaLayout.CHEST_SPOTS.stream().filter(c -> ArenaLayout.zone(c.getX(), c.getZ()) == ArenaLayout.ZONE_START).count() >= 2);
            // Dicht? Mit geschlossenen Türen und Gittern als Wand kommt man nicht weiter als bis zur nächsten Tür.
            BlockPos startCell = BlockPos.containing(ArenaLayout.START);
            BlockPos northCell = new BlockPos(0, 65, -14), topCell = new BlockPos(14, 69, 2), westCell = new BlockPos(-28, 65, 15), westRoof = new BlockPos(-28, 69, -15), eastCell = new BlockPos(28, 65, -15), eastRoof = new BlockPos(28, 69, 15), pitCell = new BlockPos(35, 63, 3);
            java.util.Set<Long> s1 = sealed(startCell);
            // Wer direkt am Gitter steht, ist noch auf seiner Seite
            check("Am Gitter im Vorhof: noch im Vorhof", ArenaLayout.zoneAt(0.5, 4.87) == ArenaLayout.ZONE_START && ArenaLayout.zoneAt(-20.14, 7.5) == ArenaLayout.ZONE_START && ArenaLayout.zoneAt(21.14, 7.5) == ArenaLayout.ZONE_START);
            check("Am Gitter von der anderen Seite: dort geblieben", ArenaLayout.zoneAt(0.5, 4.14) == ArenaLayout.ZONE_NORTH && ArenaLayout.zoneAt(-20.86, -7.5) == ArenaLayout.ZONE_WEST && ArenaLayout.zoneAt(21.86, -7.5) == ArenaLayout.ZONE_EAST);
            boolean closedDoors = true;
            for (ArenaDoors.Door d : ArenaDoors.ALL) for (BlockPos cell : d.cells()) {
                // Spielerbreite mitten im Gitter: Dort darf niemand durchpassen
                closedDoors &= !arena.noCollision(new AABB(cell.getX() + 0.2, cell.getY(), cell.getZ() + 0.2, cell.getX() + 0.8, cell.getY() + 1.8, cell.getZ() + 0.8));
            }
            check("Geschlossene Türen lassen niemanden durch", closedDoors);
            check("Türen zu: im Vorhof eingeschlossen", s1.contains(new BlockPos(0, 65, 26).asLong()) && !s1.contains(northCell.asLong()) && !s1.contains(topCell.asLong()) && !s1.contains(westCell.asLong()) && !s1.contains(eastCell.asLong()));
            ArenaDoors.set(arena, ArenaDoors.NORD, true);
            java.util.Set<Long> s2 = sealed(startCell);
            check("Tür Nordhof: Norden und Mittelteil offen, Seiten zu", s2.contains(northCell.asLong()) && s2.contains(topCell.asLong()) && !s2.contains(westCell.asLong()) && !s2.contains(westRoof.asLong()) && !s2.contains(eastCell.asLong()) && !s2.contains(eastRoof.asLong()));
            ArenaDoors.set(arena, ArenaDoors.WEST, true);
            java.util.Set<Long> s3 = sealed(startCell);
            check("Tür Säulenhalle: Westen offen, Dach über die Brücke, Osten zu", s3.contains(westCell.asLong()) && s3.contains(westRoof.asLong()) && !s3.contains(eastCell.asLong()) && !s3.contains(pitCell.asLong()));
            ArenaDoors.set(arena, ArenaDoors.EAST, true);
            java.util.Set<Long> s4 = sealed(startCell);
            check("Tür Grube: Osten, Grube und Dach offen", s4.contains(eastCell.asLong()) && s4.contains(eastRoof.asLong()) && s4.contains(pitCell.asLong()));
            ArenaDoors.closeAll(arena);
            boolean doors = true;
            for (ArenaDoors.Door d : ArenaDoors.ALL) for (ArenaDoors.DoorButton db : d.buttons()) {
                doors &= arena.getBlockState(db.pos()).is(Blocks.POLISHED_BLACKSTONE_BUTTON) && arena.getBlockState(db.pos().relative(db.facing().getOpposite())).is(Blocks.SEA_LANTERN) && seen.contains(db.pos().below().asLong());
            }
            check("Alle Tür-Knöpfe stehen auf beiden Seiten, leuchten und sind erreichbar", doors);
            boolean stations = true;
            for (Perk perk : Perk.values()) {
                boolean ok = arena.getBlockState(perk.button).is(Blocks.POLISHED_BLACKSTONE_BUTTON) && arena.getBlockState(perk.button.relative(perk.facing.getOpposite())).is(Blocks.SEA_LANTERN) && seen.contains(perk.button.below().asLong());
                if (!ok) org.slf4j.LoggerFactory.getLogger("COLTEST").error("COLTEST Perk-Station kaputt: {}", perk);
                stations &= ok;
            }
            check("Alle Perk-Stationen stehen und sind erreichbar", stations);
            check("Stromschalter in der Grube ist erreichbar", arena.getBlockState(ArenaLayout.POWER_LEVER).is(Blocks.LEVER) && seen.contains(ArenaLayout.POWER_LEVER.below().asLong()));
            check("Amboss in der Grube ist erreichbar", arena.getBlockState(ArenaLayout.ANVIL).is(Blocks.ANVIL) && seen.contains(ArenaLayout.ANVIL.west().asLong()));
            check("Knopf zum Teilen im Vorhof", arena.getBlockState(ArenaLayout.SHARE_BUTTON).is(Blocks.POLISHED_BLACKSTONE_BUTTON) && s1.contains(ArenaLayout.SHARE_BUTTON.below().asLong()));
            boolean boxes = true;
            for (int zone = 1; zone <= 4; zone++) {
                final int z0 = zone;
                boxes &= ArenaLayout.BOX_SPOTS.stream().filter(c -> ArenaLayout.zone(c.getX(), c.getZ()) == z0).count() == 1;
            }
            for (BlockPos spot : ArenaLayout.BOX_SPOTS) {
                boolean ok = arena.getBlockState(spot).isAir() && arena.getBlockState(spot.below()).isSolid() && seen.contains(spot.asLong());
                if (!ok) org.slf4j.LoggerFactory.getLogger("COLTEST").error("COLTEST Box-Platz belegt: {}", spot);
                boxes &= ok;
            }
            check("Mystery-Box-Plätze: einer pro Bereich, frei und erreichbar", boxes);
            check("Aus der Grube kommt man wieder raus", seenFrom(new BlockPos(35, 63, 3)).contains(BlockPos.containing(ArenaLayout.CENTER).asLong()));
            boolean plates = arena.getBlockState(ArenaLayout.LEVER_LEFT.west()).is(Blocks.SEA_LANTERN) && arena.getBlockState(ArenaLayout.LEVER_RIGHT.east()).is(Blocks.SEA_LANTERN) && arena.getBlockState(ArenaLayout.EXFIL_BUTTON.below()).is(Blocks.SEA_LANTERN);
            for (PlayerClass c : PlayerClass.values()) plates &= arena.getBlockState(c.button().west()).is(Blocks.SEA_LANTERN);
            for (Difficulty d : Difficulty.values()) plates &= arena.getBlockState(d.button().east()).is(Blocks.SEA_LANTERN);
            for (Shop.Offer o : Shop.OFFERS) plates &= arena.getBlockState(o.backing()).is(Blocks.SEA_LANTERN);
            check("Hinter jedem Knopf und Hebel leuchtet eine Platte", plates);
            check("Schilder leuchten", arena.getBlockEntity(Shop.OFFERS.get(0).sign()) instanceof net.minecraft.world.level.block.entity.SignBlockEntity sb && sb.getText(net.minecraft.world.level.block.entity.SignTextSlot.FRONT).hasGlowingText());
            check("Lobby-Portal ist erreichbar", seen.contains(new BlockPos(0, ArenaLayout.STAND_Y, ArenaLayout.EXIT_PORTAL.getZ() - 1).asLong()));
            boolean buttons = true;
            for (PlayerClass c : PlayerClass.values()) buttons &= seen.contains(c.button().below().east().asLong());
            for (Difficulty d : Difficulty.values()) buttons &= seen.contains(d.button().below().west().asLong());
            check("Alle Knöpfe sind erreichbar", buttons);
            boolean shops = true;
            for (Shop.Offer o : Shop.OFFERS) {
                boolean ok = seen.contains(o.button().below().asLong()) && arena.getBlockState(o.button()).is(Blocks.POLISHED_BLACKSTONE_BUTTON) && arena.getBlockState(o.sign()).is(Blocks.DARK_OAK_WALL_SIGN);
                if (!ok) org.slf4j.LoggerFactory.getLogger("COLTEST").error("COLTEST Kaufstelle kaputt: {} {}", o.name(), o.button());
                shops &= ok;
            }
            check(Shop.OFFERS.size() + " Kaufstellen stehen und sind erreichbar", shops);
            check("Hebel sind erreichbar", seen.contains(ArenaLayout.LEVER_LEFT.below().east().asLong()) && seen.contains(ArenaLayout.LEVER_RIGHT.below().west().asLong()));
            StringBuilder map = new StringBuilder("COLMAP\n");
            for (int z = -ArenaLayout.B - 2; z <= ArenaLayout.LOBBY_Z1 + 2; z += 2) {
                for (int x = -ArenaLayout.A - 2; x <= ArenaLayout.A + 2; x++) {
                    BlockPos pos = new BlockPos(x, ArenaLayout.STAND_Y, z);
                    var st = arena.getBlockState(pos);
                    map.append(st.isAir() ? (arena.getBlockState(pos.below()).isAir() ? ' ' : '.') : st.is(Blocks.IRON_BARS) ? '#' : st.is(Blocks.SANDSTONE) ? 'S' : st.is(ModBlocks.PORTAL.get()) ? 'P' : st.is(Blocks.LEVER) ? 'L' : st.is(Blocks.POLISHED_BLACKSTONE_BUTTON) ? 'K' : st.is(Blocks.QUARTZ_PILLAR) || st.is(Blocks.BARREL) || st.is(Blocks.OAK_PLANKS) || st.is(Blocks.CRACKED_STONE_BRICKS) || st.is(Blocks.STRIPPED_OAK_LOG) ? 'o' : 'X');
                }
                map.append('\n');
            }
            org.slf4j.LoggerFactory.getLogger("COLMAP").info(map.toString());
        });
        sleep(40);
        then(() -> {
            // Hell genug? Jedes Bodenfeld im Vorbereitungsraum und im Tunnel, auf Kopfhöhe gemessen
            int darkest = 15, cells = 0;
            for (int x = -ArenaLayout.LOBBY_X; x <= ArenaLayout.LOBBY_X; x++) for (int z = ArenaLayout.LOBBY_Z0; z < ArenaLayout.LOBBY_Z1; z++) {
                BlockPos pos = new BlockPos(x, ArenaLayout.STAND_Y + 1, z);
                if (arena.getBlockState(pos).isSolid()) continue;
                int light = arena.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, pos);
                if (light < 10) org.slf4j.LoggerFactory.getLogger("COLTEST").error("COLTEST dunkle Stelle {} Licht {}", pos, light);
                darkest = Math.min(darkest, light);
                cells++;
            }
            check("Vorbereitungsraum: dunkelste Stelle hat Licht " + darkest + " (" + cells + " Felder)", darkest >= 10);
            int tunnel = 15;
            for (int z = ArenaLayout.GATE_Z + 1; z < ArenaLayout.LOBBY_Z0; z++) tunnel = Math.min(tunnel, arena.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, new BlockPos(0, ArenaLayout.STAND_Y + 1, z)));
            check("Tunnel zum Tor: dunkelste Stelle hat Licht " + tunnel, tunnel >= 8);
            int niche = 15;
            for (int z : ArenaLayout.NICHE_Z) niche = Math.min(niche, arena.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, new BlockPos(-ArenaLayout.LOBBY_X - 2, ArenaLayout.STAND_Y + 1, z)));
            check("Nischen mit den Rüstungsständern: Licht " + niche, niche >= 10);
        });
        then(() -> {
            List<ArmorStand> stands = arena.getEntitiesOfClass(ArmorStand.class, ArenaLayout.BOUNDS, x -> x.entityTags().contains(ArenaBuilder.DISPLAY_TAG));
            check("Drei Rüstungsständer (gefunden: " + stands.size() + ")", stands.size() == 3);
            if (!stands.isEmpty()) {
                ArmorStand st = stands.get(0);
                st.hurtServer(arena, arena.damageSources().generic(), 100F);
                check("Rüstungsständer lässt sich nicht zerstören", st.isAlive());
            }
        });

        // --- Lobby
        then(() -> {
            a[0].getInventory().clearContent();
            b[0].getInventory().clearContent();
            a[0].connection.markClientLoaded();
            b[0].connection.markClientLoaded();
            tp(a[0], true);
            tp(b[0], true);
        });
        sleep(3);
        then(() -> {
            check("Ohne Durchlauf: vom Sand in die Lobby", ArenaLayout.isLobbySide(a[0].getZ()) && ArenaLayout.isLobbySide(b[0].getZ()));
            press(a[0], ArenaLayout.LEVER_LEFT);
            check("Hebel ohne Klasse tut nichts", !RUN.isReady(a[0].getUUID()) && gateClosed());
            press(a[0], PlayerClass.ARCHER.button());
            check("Schütze hat Eisenaxt und 4 Steaks", count(a[0], Items.IRON_AXE) == 1 && count(a[0], Items.COOKED_BEEF) == 4);
            check("Schütze startet klein: Bogen, 16 Pfeile, Lederhelm", a[0].getInventory().getItem(0).is(Items.BOW) && count(a[0], Items.ARROW) == 16 && count(a[0], Items.CROSSBOW) == 0 && a[0].getItemBySlot(EquipmentSlot.HEAD).is(Items.LEATHER_HELMET) && a[0].getItemBySlot(EquipmentSlot.CHEST).isEmpty());
            press(a[0], PlayerClass.GLADIATOR.button());
            check("Klassen-Ausrüstung ist unzerstörbar", !a[0].getInventory().getItem(0).isDamageableItem() && !a[0].getItemBySlot(EquipmentSlot.CHEST).isDamageableItem() && !a[0].getItemBySlot(EquipmentSlot.OFFHAND).isDamageableItem());
            check("Gladiator startet klein: Steinschwert, Schild, Lederbrust", a[0].getItemBySlot(EquipmentSlot.CHEST).is(Items.LEATHER_CHESTPLATE) && a[0].getItemBySlot(EquipmentSlot.LEGS).isEmpty() && a[0].getItemBySlot(EquipmentSlot.OFFHAND).is(Items.SHIELD) && a[0].getInventory().getItem(0).is(Items.STONE_SWORD));
            check("Klassenwechsel nimmt die alte Ausrüstung weg", count(a[0], Items.ARROW) == 0 && count(a[0], Items.BOW) == 0);
            press(b[0], PlayerClass.PLAGUE_DOCTOR.button());
            check("Holzschwert ist unzerstörbar", !b[0].getInventory().getItem(0).isDamageableItem() && !b[0].getItemBySlot(EquipmentSlot.FEET).isDamageableItem());
            check("Doktor startet klein: Holzschwert, je 1 Trank, Federfall", enchant(b[0].getInventory().getItem(0), Enchantments.KNOCKBACK) == 1 && PlayerClass.countSplash(b[0], Potions.HEALING) == 1 && PlayerClass.countSplash(b[0], Potions.HARMING) == 1 && enchant(b[0].getItemBySlot(EquipmentSlot.FEET), Enchantments.FEATHER_FALLING) >= 1);
            press(a[0], Difficulty.HARD.button());
            check("Schwierigkeit Schwer", RUN.difficulty() == Difficulty.HARD);
            press(a[0], Difficulty.NORMAL.button());
            check("Schwierigkeit Normal", RUN.difficulty() == Difficulty.NORMAL);
            press(a[0], ArenaLayout.LEVER_LEFT);
            check("Ein Spieler bereit: Hebel an", arena.getBlockState(ArenaLayout.LEVER_LEFT).getValue(BlockStateProperties.POWERED));
        });
        sleep(3);
        then(() -> {
            check("Einer bereit, einer nicht: Tor bleibt zu", gateClosed() && RUN.phase() == ArenaRun.Phase.LOBBY);
            press(b[0], ArenaLayout.LEVER_RIGHT);
        });
        sleep(3);
        then(() -> {
            check("Beide bereit: Tor offen", !gateClosed() && RUN.phase() == ArenaRun.Phase.GATE_OPEN);
            press(b[0], PlayerClass.GLADIATOR.button());
            check("Klassenwechsel bei offenem Tor geht nicht", PlayerClass.countSplash(b[0], Potions.HEALING) == 1);
            toSand(a[0], 5.5, 20.5);
        });
        sleep(3);
        then(() -> {
            check("Einer im Sand: Tor bleibt offen", !gateClosed());
            toSand(b[0], -5.5, 20.5);
        });
        sleep(3);
        then(() -> {
            check("Beide im Sand: Tor schließt", gateClosed());
        });

        // --- Runde 1
        waitSpawned(1);
        final double[] start = new double[1];
        then(() -> {
            int want = (int) Math.round((4 + 2) * 1.5);
            check("Runde 1: " + want + " Gegner für zwei Spieler (" + mobs().size() + ")", mobs().size() == want);
            check("Mob-Tore sind offen", !mobGateClosed());
            check("Mobs kommen aus den Toren", mobs().stream().allMatch(m -> !ArenaLayout.isSand(m.getX(), m.getZ())));
            check("Mobs kommen nur im offenen Bereich", mobs().stream().allMatch(m -> ArenaLayout.zone((int) Math.floor(m.getX()), (int) Math.floor(m.getZ())) == ArenaLayout.ZONE_START));
            check("Tore geschlossener Bereiche bleiben zu", ArenaLayout.MOB_GATES.stream().filter(g -> g.zone() != ArenaLayout.ZONE_START).allMatch(g -> arena.getBlockState(g.bars().get(0)).is(Blocks.IRON_BARS)));
            check("Runde 1 ohne Rüstung", mobs().stream().allMatch(m -> m.getItemBySlot(EquipmentSlot.CHEST).isEmpty()));
            check("Normal: Zombie hat normales Leben", mobs().stream().filter(m -> m.getType() == EntityTypes.ZOMBIE).allMatch(m -> m.getMaxHealth() == 20F));
            start[0] = mobs().stream().filter(m -> m instanceof Zombie).mapToDouble(m -> m.distanceTo(a[0])).average().orElse(0);
        });
        sleep(120);
        then(() -> {
            double now = mobs().stream().filter(m -> m instanceof Zombie).mapToDouble(m -> m.distanceTo(a[0])).average().orElse(999);
            check("Zombies kommen näher: " + (int) start[0] + " auf " + (int) now + " Blöcke", now < start[0] - 3);
            check("In der Arena brennt kein Mob", mobs().stream().noneMatch(Entity::isOnFire));
            long hunting = mobs().stream().filter(m -> m.getTarget() != null).count();
            check("Mobs haben ein Ziel: " + hunting + " von " + mobs().size(), hunting * 10 >= mobs().size() * 7);
        });
        then(() -> {
            a[0].teleportTo(0.5, ArenaLayout.STAND_Y, 4.87);
            // Ausreißer auf der Tribüne wird zurückgeholt
            mobs().stream().findFirst().ifPresent(m -> m.teleportTo(-ArenaLayout.A - 8.5, 80, 0.5));
        });
        sleep(25);
        then(() -> {
            check("Mob auf der Tribüne wird zurückgeholt", mobs().stream().allMatch(m -> m.getY() < 72));
            check("Direkt am geschlossenen Gitter: keine Teleportation", Math.abs(a[0].getZ() - 4.87) < 0.5 && Math.abs(a[0].getX() - 0.5) < 0.5);
        });
        then(() -> a[0].teleportTo(ArenaLayout.A + 5.5, 85, 0.5));
        sleep(2);
        final BlockPos chamber = ArenaLayout.MOB_GATES.get(0).spawn();
        then(() -> {
            check("Über der Mauer: zurück in den Vorhof", a[0].getY() < 70 && ArenaLayout.zone((int) a[0].getX(), (int) a[0].getZ()) == ArenaLayout.ZONE_START);
            a[0].teleportTo(chamber.getX() + 0.5, chamber.getY(), chamber.getZ() + 0.5);
        });
        sleep(3);
        then(() -> check("Im Gang: keine Teleportation", a[0].distanceToSqr(Vec3.atBottomCenterOf(chamber)) < 9));
        finishWave(1);
        then(() -> {
            BlockPos mouth = ArenaLayout.MOB_GATES.get(0).mouth();
            check("Tor schließt: Spieler steht direkt davor im Sand, nicht in der Mitte", ArenaLayout.isSand(a[0].getX(), a[0].getZ()) && a[0].distanceToSqr(Vec3.atBottomCenterOf(mouth)) < 2);
            check("Vor dem Tor ist frei", arena.getBlockState(mouth).isAir() && arena.getBlockState(mouth.above()).isAir());
            toSand(a[0], 5.5, 20.5);
            check("Pause: keine XP-Flaschen mehr als Belohnung", XpLedger.balance(a[0]) == 0 && XpLedger.balance(b[0]) == 0);
            check("Pause: Mob-Tore zu", mobGateClosed());
            check("Runde 1: noch keine Kisten", RUN.chestPositions().isEmpty());
            check("Doktor bekommt nach", PlayerClass.countSplash(b[0], Potions.HEALING) == 2 && PlayerClass.countSplash(b[0], Potions.HARMING) == 2);
            check("Runde geschafft bringt Punkte", RUN.points(b[0].getUUID()) == 60);
        });

        // --- Runde 2: Kisten weg, einer fällt und kommt ohne Gear zurück
        waitSpawned(2);
        then(() -> {
            ServerPlayer p = b[0];
            RUN.setPointsForTest(p.getUUID(), 777);
            ArenaRun.bleedOutTicks = 400;
            ArenaRun.rescueTicks = 10;
            p.getInventory().setItem(5, new ItemStack(Items.GOLD_NUGGET));
            toSand(p, 10.5, 25.5);
            toSand(a[0], -15.5, 25.5);
            a[0].setShiftKeyDown(false);
            p.hurtServer(arena, arena.damageSources().fellOutOfWorld(), 1000F);
            check("Am Boden: Partner lebt, also kein echter Tod", p.isAlive() && p.getHealth() > 0);
            check("Am Boden: kein Zuschauer, Gear behalten", RUN.isDowned(p.getUUID()) && p.gameMode() == GameType.ADVENTURE && count(p, Items.GOLD_NUGGET) == 1);
            check("Am Boden: langsam und zählt nicht als kämpfend", p.hasEffect(MobEffects.SLOWNESS) && !RUN.isAlive(p.getUUID()));
            float hp = p.getHealth();
            p.hurtServer(arena, arena.damageSources().generic(), 5F);
            check("Am Boden: kein weiterer Schaden", p.getHealth() == hp);
            press(p, Shop.OFFERS.get(0).button());
            check("Am Boden: kann nichts kaufen", RUN.points(p.getUUID()) == 777);
        });
        sleep(15);
        then(() -> {
            check("Partner weit weg: keine Rettung", RUN.isDowned(b[0].getUUID()));
            toSand(a[0], b[0].getX() + 1, b[0].getZ());
            a[0].setShiftKeyDown(true);
            RUN.setPointsForTest(a[0].getUUID(), 0);
        });
        sleep(15);
        then(() -> {
            ServerPlayer p = b[0];
            a[0].setShiftKeyDown(false);
            // Zwischen Rettung und Prüfung liegen ein paar Ticks, in denen Mobs treffen können: mehr als 1 Leben, höchstens die Hälfte
            check("Gerettet: steht wieder, kämpft mit bis zu halbem Leben (" + p.getHealth() + ")", !RUN.isDowned(p.getUUID()) && RUN.isAlive(p.getUUID()) && p.getHealth() > 1F && p.getHealth() <= p.getMaxHealth() / 2F + 0.5F);
            check("Gerettet: behält sein Gear, Effekte weg", count(p, Items.GOLD_NUGGET) == 1 && !p.hasEffect(MobEffects.SLOWNESS));
            check("Retter bekommt 200 Punkte", RUN.points(a[0].getUUID()) == 200);
            // Niemand rettet: Zeit läuft ab, die alte Regel greift
            toSand(a[0], -15.5, 25.5);
            ArenaRun.bleedOutTicks = 5;
            p.hurtServer(arena, arena.damageSources().fellOutOfWorld(), 1000F);
        });
        sleep(12);
        then(() -> {
            ServerPlayer p = b[0];
            check("Ohne Rettung: Zuschauer", p.gameMode() == GameType.SPECTATOR && !RUN.isDowned(p.getUUID()));
            check("Ohne Rettung: Ausrüstung ist weg", p.getInventory().isEmpty());
            check("Gefallen: Partner kämpft weiter", RUN.isAlive(a[0].getUUID()) && !RUN.isAlive(b[0].getUUID()));
            check("Gefallen: Punkte bleiben", RUN.points(p.getUUID()) == 777);
            ArenaRun.bleedOutTicks = 900;
            press(p, Shop.OFFERS.get(0).button());
            check("Gefallen: Zuschauer kann nichts kaufen", RUN.points(p.getUUID()) == 777 && p.getInventory().isEmpty());
            check("Gefallen: Sicherung bleibt", p.hasData(de.ecri.colosseum.registry.ModAttachments.SNAPSHOT));
        });
        finishWave(2);
        then(() -> {
            check("Gefallener bekommt für Runde 2 keinen Rundenbonus", RUN.points(b[0].getUUID()) == 777);
            check("In der Pause bleibt er Zuschauer", b[0].gameMode() == GameType.SPECTATOR);
        });
        waitFor(() -> RUN.phase() == ArenaRun.Phase.WAVE && RUN.round() == 3, "Runde 3");
        then(() -> {
            ServerPlayer p = b[0];
            check("Nächste Runde: wieder dabei", p.gameMode() == GameType.ADVENTURE && RUN.isAlive(p.getUUID()));
            check("Nächste Runde: halbes Leben", Math.abs(p.getHealth() - p.getMaxHealth() / 2F) < 0.01F);
            check("Nächste Runde: ohne Ausrüstung", p.getInventory().isEmpty());
            check("Nächste Runde: im Vorhof hinter dem Tor", p.distanceToSqr(ArenaLayout.START) < 2);
        });
        waitSpawned(3);
        then(() -> {
            // Punkte: echter Kill durch einen Spieler
            Mob zombie = mobs().stream().filter(m -> m.getVehicle() == null).findFirst().orElseThrow();
            int before = RUN.points(a[0].getUUID());
            zombie.hurtServer(arena, arena.damageSources().playerAttack(a[0]), 1000F);
            check("Kill bringt dem Spieler Punkte: +" + (RUN.points(a[0].getUUID()) - before), RUN.points(a[0].getUUID()) == before + MobFactory.points(zombie, 3));
            Mob other = mobs().stream().filter(m -> m != zombie && m.isAlive() && m.getVehicle() == null).findFirst().orElseThrow();
            int beforeB = RUN.points(b[0].getUUID());
            before = RUN.points(a[0].getUUID());
            other.hurtServer(arena, arena.damageSources().generic(), 1000F);
            check("Kill ohne Spieler bringt niemandem Punkte", RUN.points(a[0].getUUID()) == before && RUN.points(b[0].getUUID()) == beforeB);

            // Kaufen
            ServerPlayer p = a[0];
            RUN.setPointsForTest(p.getUUID(), 100);
            Shop.Offer iron = Shop.OFFERS.stream().filter(o -> o.line1().equals("offer.iron_armor")).findFirst().orElseThrow();
            press(p, iron.button());
            check("Zu wenig Punkte: nichts gekauft", RUN.points(p.getUUID()) == 100 && !p.getItemBySlot(EquipmentSlot.CHEST).is(Items.IRON_CHESTPLATE));
            RUN.setPointsForTest(p.getUUID(), iron.price() + 5);
            press(p, iron.button());
            check("Eisenrüstung gekauft: angezogen, Punkte abgezogen", RUN.points(p.getUUID()) == 5 && p.getItemBySlot(EquipmentSlot.HEAD).is(Items.IRON_HELMET) && p.getItemBySlot(EquipmentSlot.CHEST).is(Items.IRON_CHESTPLATE) && p.getItemBySlot(EquipmentSlot.LEGS).is(Items.IRON_LEGGINGS) && p.getItemBySlot(EquipmentSlot.FEET).is(Items.IRON_BOOTS));
            check("Alte Rüstung ist ersetzt", count(p, Items.LEATHER_CHESTPLATE) == 0);
            boolean all = true;
            for (Shop.Offer o : Shop.OFFERS) {
                RUN.setPointsForTest(p.getUUID(), o.price());
                p.getInventory().clearContent();
                press(p, o.button());
                boolean got = RUN.points(p.getUUID()) == 0 && !p.getInventory().isEmpty();
                boolean strong = true;
                for (int i = 0; i < p.getInventory().getContainerSize(); i++) strong &= !p.getInventory().getItem(i).isDamageableItem();
                if (!got || !strong) org.slf4j.LoggerFactory.getLogger("COLTEST").error("COLTEST Kauf falsch: {}", o.name());
                all &= got && strong;
            }
            check("Alle " + Shop.OFFERS.size() + " Angebote liefern unzerstörbare Ware", all);
            p.getInventory().clearContent();
            RUN.setPointsForTest(p.getUUID(), 0);
        });
        then(() -> {
            // Stationen brauchen Strom
            ServerPlayer p = a[0];
            RUN.setPointsForTest(p.getUUID(), 100000);
            press(p, Perk.TOUGH.button);
            check("Perk ohne Strom geht nicht", !RUN.hasPerk(p.getUUID(), Perk.TOUGH) && RUN.points(p.getUUID()) == 100000);
            press(p, ArenaLayout.ANVIL);
            check("Amboss ohne Strom geht nicht", RUN.points(p.getUUID()) == 100000);
            press(p, ArenaLayout.POWER_LEVER);
            check("Schalter macht Strom an", RUN.isPowerOn() && arena.getBlockState(ArenaLayout.POWER_LEVER).getValue(BlockStateProperties.POWERED));
            press(p, Perk.TOUGH.button);
            check("Zähigkeit: +5 Herzen", RUN.hasPerk(p.getUUID(), Perk.TOUGH) && p.getMaxHealth() == 30F && RUN.points(p.getUUID()) == 100000 - Perk.TOUGH.price);
            press(p, Perk.TOUGH.button);
            check("Perk zweimal kostet nichts", RUN.points(p.getUUID()) == 100000 - Perk.TOUGH.price);
            double speed = p.getAttributeValue(Attributes.MOVEMENT_SPEED);
            press(p, Perk.SWIFT.button);
            check("Tempo: schneller", p.getAttributeValue(Attributes.MOVEMENT_SPEED) > speed * 1.15);
            press(p, Perk.MIGHT.button);
            press(p, Perk.MARKSMAN.button);
            check("Alle vier Perks aktiv", RUN.hasPerk(p.getUUID(), Perk.MIGHT) && RUN.hasPerk(p.getUUID(), Perk.MARKSMAN));
            int full = RUN.points(p.getUUID());
            press(p, Perk.VAMPIRE.button);
            check("Perk-Limit: der fünfte geht nicht und kostet nichts", !RUN.hasPerk(p.getUUID(), Perk.VAMPIRE) && RUN.perksOf(p.getUUID()).size() == 4 && RUN.points(p.getUUID()) == full);
            check("Anzeige: Perks stehen in der Seitenleiste", RUN.isHudShown(p.getUUID()) && PerkHud.lines(RUN.perksOf(p.getUUID())).size() == 4 && PerkHud.lines(RUN.perksOf(p.getUUID())).get(0).getString().startsWith("Toughness"));
            RUN.clearPerksForTest(p);
            check("Anzeige: ohne Perks steht „Noch keine“ da", PerkHud.lines(RUN.perksOf(p.getUUID())).get(0).getString().equals("None yet") && p.getMaxHealth() == 20F);

            // Die vier neuen Perks
            int plainRescue = RUN.rescueTicksFor(p.getUUID());
            press(p, Perk.FAST_REVIVE.button);
            check("Schnell-Retten: geht schneller oder bleibt beim Testwert", RUN.hasPerk(p.getUUID(), Perk.FAST_REVIVE) && RUN.rescueTicksFor(p.getUUID()) == Math.min(plainRescue, 20));
            double swing = p.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED);
            press(p, Perk.DOUBLE_TAP.button);
            check("Doppelschlag: 25 % schneller schlagen", Math.abs(p.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED) - swing * 1.25) < 0.001);
            press(p, Perk.VAMPIRE.button);
            p.setHealth(10F);
            // Ein frischer Mob: Die aus der Welle sind in diesem Testschritt schon tot und liegen nur noch da.
            Mob prey = MobFactory.spawn(arena, BlockPos.containing(p.position()).north(2), WaveComposer.Kind.ZOMBIE, RUN.round(), Difficulty.NORMAL, RandomSource.create(5));
            int pointsBefore = RUN.points(p.getUUID());
            double chance = ArenaRun.powerUpChance;
            ArenaRun.powerUpChance = 0; // Ein zufälliges Power-up würde die Zählung danach stören
            prey.hurtServer(arena, p.damageSources().playerAttack(p), 100000F);
            ArenaRun.powerUpChance = chance;
            check("Vampir: Kill heilt 1 Herz (" + p.getHealth() + ", Punkte +" + (RUN.points(p.getUUID()) - pointsBefore) + ")", p.getHealth() == 12F && RUN.points(p.getUUID()) > pointsBefore);
            press(p, Perk.SECOND_CHANCE.button);
            check("Zweite Chance: gekauft, 4 Perks voll", RUN.hasPerk(p.getUUID(), Perk.SECOND_CHANCE) && RUN.perksOf(p.getUUID()).size() == 4);
            p.hurtServer(arena, p.damageSources().fellOutOfWorld(), 100000F);
            check("Zweite Chance: Tod abgefangen, steht mit halbem Leben", !p.isDeadOrDying() && p.getHealth() == p.getMaxHealth() / 2F && RUN.isAlive(p.getUUID()) && !RUN.isDowned(p.getUUID()));
            check("Zweite Chance: Perk verbraucht, Platz frei", !RUN.hasPerk(p.getUUID(), Perk.SECOND_CHANCE) && RUN.perksOf(p.getUUID()).size() == 3);
            int afterChance = RUN.points(p.getUUID());
            press(p, Perk.SECOND_CHANCE.button);
            check("Zweite Chance: nur einmal pro Durchlauf", !RUN.hasPerk(p.getUUID(), Perk.SECOND_CHANCE) && RUN.points(p.getUUID()) == afterChance);
            RUN.clearPerksForTest(p);
            p.removeAllEffects();
            p.setHealth(p.getMaxHealth());
            press(p, Perk.TOUGH.button);
            press(p, Perk.SWIFT.button);
            press(p, Perk.MIGHT.button);
            press(p, Perk.MARKSMAN.button);
            check("Standard: 45 Sekunden Zeit zum Retten", ArenaRun.bleedOutTicks == 900);

            // Waffe verbessern
            p.getInventory().setItem(p.getInventory().getSelectedSlot(), new ItemStack(Items.IRON_SWORD));
            int before = RUN.points(p.getUUID());
            press(p, ArenaLayout.ANVIL);
            ItemStack up = p.getMainHandItem();
            check("Amboss: Menü öffnet, noch nichts bezahlt", p.containerMenu instanceof PackAPunch.PunchMenu && RUN.points(p.getUUID()) == before);
            PackAPunch.PunchMenu menu = (PackAPunch.PunchMenu) p.containerMenu;
            check("Amboss: drei verschiedene Angebote", menu.choices().size() == 3 && new java.util.HashSet<>(menu.choices()).size() == 3);
            p.closeContainer();
            press(p, ArenaLayout.ANVIL);
            check("Amboss: Neu öffnen würfelt nicht neu", ((PackAPunch.PunchMenu) p.containerMenu).choices().equals(menu.choices()));
            var first = menu.choices().get(0);
            p.containerMenu.clicked(11, 0, net.minecraft.world.inventory.ContainerInput.PICKUP, p);
            check("Amboss: Schärfe startet auf III, Rückstoß auf I", PackAPunch.startLevel(arena.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SHARPNESS)) == 3 && PackAPunch.startLevel(arena.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.KNOCKBACK)) == 1);
            check("Amboss Stufe 1: 2500 Punkte, gewählte Verzauberung auf halber Stufe, Menü zu", RUN.points(p.getUUID()) == before - 2500 && EnchantmentHelper.getItemEnchantmentLevel(first, up) == PackAPunch.startLevel(first) && PackAPunch.stage(up) == 1 && p.containerMenu == p.inventoryMenu);
            check("Amboss: ein Stern im Namen", up.getHoverName().getString().startsWith("★ "));
            before = RUN.points(p.getUUID());
            press(p, ArenaLayout.ANVIL);
            var second = ((PackAPunch.PunchMenu) p.containerMenu).choices().get(0);
            p.containerMenu.clicked(11, 0, net.minecraft.world.inventory.ContainerInput.PICKUP, p);
            check("Amboss Stufe 2: 5000 Punkte, erste voll, neue dazu", RUN.points(p.getUUID()) == before - 5000 && EnchantmentHelper.getItemEnchantmentLevel(first, up) == first.value().getMaxLevel() && EnchantmentHelper.getItemEnchantmentLevel(second, up) == PackAPunch.startLevel(second));
            before = RUN.points(p.getUUID());
            press(p, ArenaLayout.ANVIL);
            boolean any = !((PackAPunch.PunchMenu) p.containerMenu).choices().isEmpty();
            p.containerMenu.clicked(any ? 11 : 13, 0, net.minecraft.world.inventory.ContainerInput.PICKUP, p);
            check("Amboss Stufe 3: 7500 Punkte, alles voll", RUN.points(p.getUUID()) == before - 7500 && PackAPunch.stage(up) == 3 && up.getEnchantments().keySet().stream().allMatch(h -> EnchantmentHelper.getItemEnchantmentLevel(h, up) == h.value().getMaxLevel()));
            before = RUN.points(p.getUUID());
            press(p, ArenaLayout.ANVIL);
            check("Amboss: Nach Stufe 3 ist Schluss", p.containerMenu == p.inventoryMenu && RUN.points(p.getUUID()) == before);
            p.getInventory().setItem(p.getInventory().getSelectedSlot(), new ItemStack(Items.COOKED_BEEF));
            press(p, ArenaLayout.ANVIL);
            check("Amboss: nur Waffen", p.containerMenu == p.inventoryMenu && RUN.points(p.getUUID()) == before);
            p.getInventory().setItem(p.getInventory().getSelectedSlot(), new ItemStack(Items.BOW));
            press(p, ArenaLayout.ANVIL);
            check("Amboss: Bogen bekommt Bogen-Verzauberungen", ((PackAPunch.PunchMenu) p.containerMenu).choices().stream().allMatch(h -> h.is(Enchantments.POWER) || h.is(Enchantments.PUNCH) || h.is(Enchantments.FLAME) || h.is(Enchantments.INFINITY)));
            ItemStack broke = p.getMainHandItem();
            int noSwap = RUN.points(p.getUUID());
            p.getInventory().setItem(p.getInventory().getSelectedSlot(), new ItemStack(Items.WOODEN_SWORD));
            p.containerMenu.clicked(11, 0, net.minecraft.world.inventory.ContainerInput.PICKUP, p);
            check("Amboss: Waffe getauscht, kein Kauf", RUN.points(p.getUUID()) == noSwap && PackAPunch.stage(broke) == 0 && p.containerMenu == p.inventoryMenu);
            RUN.setPointsForTest(p.getUUID(), 100);
            p.getInventory().setItem(p.getInventory().getSelectedSlot(), new ItemStack(Items.BOW));
            press(p, ArenaLayout.ANVIL);
            check("Amboss: zu wenig Punkte, Menü bleibt zu", p.containerMenu == p.inventoryMenu);

            // Mystery Box
            check("Mystery Box steht im Vorhof", arena.getBlockState(RUN.boxPos()).is(Blocks.ENDER_CHEST) && ArenaLayout.zone(RUN.boxPos().getX(), RUN.boxPos().getZ()) == ArenaLayout.ZONE_START);
            RUN.setPointsForTest(p.getUUID(), 100);
            p.getInventory().clearContent();
            press(p, RUN.boxPos());
            check("Mystery Box: zu wenig Punkte", p.getInventory().isEmpty() && RUN.points(p.getUUID()) == 100);
            RUN.setPointsForTest(p.getUUID(), MysteryBox.PRICE);
            press(p, RUN.boxPos());
            check("Mystery Box: liefert etwas", !p.getInventory().isEmpty() && RUN.points(p.getUUID()) == 0);
            check("Mystery Box: Meldung nennt die Beute, nicht Air (" + RUN.lastPrizeName() + ")", !RUN.lastPrizeName().isEmpty() && !RUN.lastPrizeName().toLowerCase().contains("air"));
            RandomSource random = RandomSource.create(9);
            boolean strong = true;
            java.util.Set<net.minecraft.world.item.Item> kinds = new java.util.HashSet<>();
            for (int i = 0; i < 2000; i++) { ItemStack st = MysteryBox.roll(arena.registryAccess(), random); strong &= !st.isDamageableItem() && !st.isEmpty(); kinds.add(st.getItem()); }
            check("Mystery Box: viele verschiedene Preise (" + kinds.size() + "), nichts geht kaputt", strong && kinds.size() >= 12);

            // Punkte teilen
            RUN.setPointsForTest(p.getUUID(), 600);
            RUN.setPointsForTest(b[0].getUUID(), 0);
            press(p, ArenaLayout.SHARE_BUTTON);
            check("Punkte teilen: 500 wechseln den Besitzer", RUN.points(p.getUUID()) == 100 && RUN.points(b[0].getUUID()) == 500);
            press(p, ArenaLayout.SHARE_BUTTON);
            check("Punkte teilen: nicht mit zu wenig", RUN.points(p.getUUID()) == 100);

            // Power-ups
            RUN.setPointsForTest(p.getUUID(), 0);
            toSand(p, 5.5, 25.5);
            int groundBefore = RUN.powerUpsOnGround();
            RUN.dropPowerUp(arena, new Vec3(30.5, 66, 0.5), ArenaRun.PowerUp.DOUBLE);
            check("Power-up liegt da (vorher " + groundBefore + ", jetzt " + RUN.powerUpsOnGround() + ")", RUN.powerUpsOnGround() == groundBefore + 1);
            RUN.dropPowerUp(arena, new Vec3(5.5, 65, 25.5), ArenaRun.PowerUp.DOUBLE);
        });
        sleep(3);
        then(() -> {
            check("Power-up wird beim Drüberlaufen aktiv", RUN.isDoubleActive());
            Mob m1 = mobs().stream().filter(x -> x.getVehicle() == null && x.isAlive()).findFirst().orElseThrow();
            int before = RUN.points(a[0].getUUID());
            m1.hurtServer(arena, arena.damageSources().playerAttack(a[0]), 1000F);
            check("Doppelte Punkte wirken: +" + (RUN.points(a[0].getUUID()) - before), RUN.points(a[0].getUUID()) - before == 2 * MobFactory.points(m1, 3));
            RUN.dropPowerUp(arena, a[0].position(), ArenaRun.PowerUp.INSTA);
        });
        sleep(3);
        then(() -> {
            check("Ein-Treffer-Power-up aktiv", RUN.isInstaActive());
            Mob m2 = mobs().stream().filter(x -> x.getVehicle() == null && x.isAlive() && x.getType() == EntityTypes.ZOMBIE || x.getVehicle() == null && x.isAlive()).findFirst().orElseThrow();
            m2.hurtServer(arena, arena.damageSources().playerAttack(a[0]), 1F);
            check("Ein Treffer, ein Kill", !m2.isAlive());
            a[0].getInventory().clearContent();
            RUN.dropPowerUp(arena, a[0].position(), ArenaRun.PowerUp.AMMO);
        });
        sleep(3);
        then(() -> {
            check("Munition: Pfeile im Inventar", count(a[0], Items.ARROW) == 32);
            a[0].getInventory().clearContent();
            RUN.setPointsForTest(a[0].getUUID(), 0);
            RUN.dropPowerUp(arena, a[0].position(), ArenaRun.PowerUp.NUKE);
        });
        sleep(3);
        then(() -> {
            check("Atombombe: alle Mobs weg", mobs().isEmpty() || RUN.phase() == ArenaRun.Phase.PAUSE);
            check("Atombombe: Punkte", RUN.points(a[0].getUUID()) > 0);
        });
        waitFor(() -> RUN.phase() == ArenaRun.Phase.PAUSE && RUN.round() == 3, "Pause nach der Atombombe");
        then(() -> {
            ArenaRun.powerUpChance = 0.0;
            check("Runde 3: genau zwei Kisten", RUN.chestPositions().size() == 2 && RUN.chestPositions().stream().allMatch(c -> arena.getBlockState(c).is(Blocks.CHEST)));
            check("Kisten haben Inhalt", RUN.chestPositions().stream().allMatch(c -> arena.getBlockEntity(c) instanceof ChestBlockEntity ch && !ch.isEmpty()));
        });

        // --- Runde 4: Hühner-Jockeys
        waitSpawned(4);
        then(() -> {
            boolean gone = true;
            for (BlockPos c : ArenaLayout.CHEST_SPOTS) gone &= !arena.getBlockState(c).is(Blocks.CHEST);
            check("Neue Runde: Kisten sind weg", gone);
            check("Kein Kisteninhalt liegt herum", arena.getEntitiesOfClass(ItemEntity.class, ArenaLayout.BOUNDS).stream().noneMatch(i -> i.getItem().is(Items.BREAD) || i.getItem().is(Items.COOKED_BEEF)));
            List<Mob> all = mobs();
            List<Mob> riders = all.stream().filter(m -> m instanceof Zombie && m.getVehicle() instanceof Chicken).toList();
            check("Runde 4: Baby-Zombies/-Husks reiten Hühner", !riders.isEmpty() && riders.stream().allMatch(m -> m.isBaby() && m.getVehicle() instanceof Chicken ch && ch.isChickenJockey()));
            check("Runde 4: nur noch 3 Hühner-Jockeys zu zweit (" + riders.size() + "), dazu Zombies", riders.size() == 3 && all.stream().filter(m -> m instanceof Zombie && m.getVehicle() == null).count() == 6);
            check("Runde 4: Hühner mit Tempo", all.stream().filter(m -> m instanceof Chicken).allMatch(m -> m.hasEffect(MobEffects.SPEED)));
        });
        sleep(40);
        then(() -> {
            // Nur die Reiter zählen: Sind sie weg, endet die Welle trotz Hühnern
            for (Mob m : mobs()) if (m instanceof Zombie) m.discard();
        });
        waitFor(() -> RUN.phase() == ArenaRun.Phase.PAUSE && RUN.round() == 4, "Pause nach Runde 4");
        then(() -> check("Ohne Reiter verschwinden auch die Hühner", mobs().isEmpty()));

        // --- Türen: kosten Punkte, Reihenfolge stimmt, neue Tore gehen auf
        waitSpawned(5);
        then(() -> {
            ServerPlayer p = a[0];
            ArenaDoors.DoorButton nord = ArenaDoors.NORD.buttons().get(0);
            check("Am Anfang ist nur der Vorhof offen", RUN.isZoneOpen(ArenaLayout.ZONE_START) && !RUN.isZoneOpen(ArenaLayout.ZONE_NORTH));
            RUN.setPointsForTest(p.getUUID(), 100);
            press(p, nord.pos());
            check("Tür: zu wenig Punkte, bleibt zu", !RUN.isDoorOpen(ArenaDoors.NORD) && RUN.points(p.getUUID()) == 100 && arena.getBlockState(new BlockPos(0, 65, 4)).is(Blocks.IRON_BARS));
            RUN.setPointsForTest(p.getUUID(), 100000);
            press(p, ArenaDoors.WEST_SHORTCUT.buttons().get(0).pos());
            check("Abkürzung geht erst, wenn beide Seiten offen sind", !RUN.isDoorOpen(ArenaDoors.WEST_SHORTCUT) && RUN.points(p.getUUID()) == 100000);
            RUN.setPointsForTest(p.getUUID(), ArenaDoors.NORD.price() + 7);
            press(p, nord.pos());
            check("Tür gekauft: offen, Punkte abgezogen", RUN.isDoorOpen(ArenaDoors.NORD) && RUN.isZoneOpen(ArenaLayout.ZONE_NORTH) && RUN.points(p.getUUID()) == 7 && arena.getBlockState(new BlockPos(0, 65, 4)).isAir());
            press(p, nord.pos());
            check("Zweimal drücken kostet nichts", RUN.points(p.getUUID()) == 7);
            check("Während der Welle gehen die Mob-Tore im neuen Bereich auf", ArenaLayout.MOB_GATES.stream().filter(g -> g.zone() == ArenaLayout.ZONE_NORTH).allMatch(g -> arena.getBlockState(g.bars().get(0)).isAir()));
            check("Mob-Tore im Westen bleiben zu", ArenaLayout.MOB_GATES.stream().filter(g -> g.zone() == ArenaLayout.ZONE_WEST).allMatch(g -> arena.getBlockState(g.bars().get(0)).is(Blocks.IRON_BARS)));
            BlockPos boxBefore = RUN.boxPos();
            RUN.setPointsForTest(p.getUUID(), MysteryBox.PRICE * MysteryBox.USES_BEFORE_MOVE + ArenaDoors.WEST.price() + ArenaDoors.WEST_SHORTCUT.price());
            press(p, ArenaDoors.WEST.buttons().get(0).pos());
            press(p, ArenaDoors.WEST_SHORTCUT.buttons().get(0).pos());
            for (int i = 0; i < MysteryBox.USES_BEFORE_MOVE; i++) press(p, RUN.boxPos());
            check("Mystery Box zieht nach 6 Mal um, in einen offenen Bereich", !RUN.boxPos().equals(boxBefore) && arena.getBlockState(RUN.boxPos()).is(Blocks.ENDER_CHEST) && !arena.getBlockState(boxBefore).is(Blocks.ENDER_CHEST) && RUN.isZoneOpen(ArenaLayout.zone(RUN.boxPos().getX(), RUN.boxPos().getZ())));
            check("Danach gehen Säulenhalle und Abkürzung auf", RUN.isDoorOpen(ArenaDoors.WEST) && RUN.isDoorOpen(ArenaDoors.WEST_SHORTCUT) && RUN.points(p.getUUID()) == 0);
        });

        // --- Runde 5: Exfil rufen, schwere Runde, Bonus, raus
        then(() -> { ArenaRun.pauseTicks = 200; ArenaRun.exfilCallTicks = 20; ArenaRun.exfilPortalTicks = 200; });
        finishWave(5);
        final int[] paid = new int[2];
        then(() -> {
            check("Runde 5: Exfil-Knopf steht", arena.getBlockState(ArenaLayout.EXFIL_BUTTON).is(Blocks.POLISHED_BLACKSTONE_BUTTON));
            check("Runde 5: Portal bleibt aus, kein freier Ausgang", !arena.getBlockState(ArenaLayout.EXTRACTION_PORTAL).is(ModBlocks.PORTAL.get()));
            press(a[0], ArenaLayout.EXFIL_BUTTON);
            check("Knopf ruft den Exfil", RUN.isExfilCalled());
            check("Knopf ist danach weg", arena.getBlockState(ArenaLayout.EXFIL_BUTTON).isAir());
        });
        waitFor(() -> RUN.isExfilWave() && RUN.queued() == 0, "Exfil-Runde gespawnt");
        then(() -> {
            int want = WaveComposer.composeExfil(5, 2, RandomSource.create(1)).size();
            long enemies = mobs().stream().filter(m -> !m.entityTags().contains(MobFactory.PASSIVE_TAG)).count();
            check("Exfil-Runde ist schwer: " + mobs().size() + " Mobs, normal wären es 21", enemies >= (want - 1) && enemies > WaveComposer.compose(5, 2, RandomSource.create(1)).size());
            int chickens = 0, rolled = 0;
            RandomSource rnd = RandomSource.create(7);
            for (int i = 0; i < 200; i++) for (WaveComposer.Kind k : WaveComposer.composeExfil(5, 2, rnd)) { rolled++; if (k == WaveComposer.Kind.CHICKEN_JOCKEY) chickens++; }
            check("Exfil: wenige Hühner-Jockeys, " + (100 * chickens / rolled) + " %", 100 * chickens / rolled <= 5 && chickens > 0);
            check("Exfil-Runde hat einen Verwüster", mobs().stream().anyMatch(m -> m.getType() == EntityTypes.RAVAGER));
            check("Portal bleibt während der Exfil-Runde aus", !RUN.isExfilOpen());
            paid[1] = PointsAccount.balance(a[0]);
        });
        then(RunSelfTest::killAll);
        waitFor(() -> RUN.isExfilOpen(), "Exfil-Portal offen");
        then(() -> {
            check("Exfil geschafft: Portal brennt", arena.getBlockState(ArenaLayout.EXTRACTION_PORTAL).is(ModBlocks.PORTAL.get()) && PortalFrame.find(arena, ArenaLayout.EXTRACTION_PORTAL, Direction.Axis.X).filter(f -> f.isComplete(arena)).isPresent());
            check("Exfil geschafft: noch nichts auf dem Konto", PointsAccount.balance(a[0]) == paid[1]);
            paid[0] = PointsAccount.withExfil(PointsAccount.pending(a[0]));
            ServerPlayer p = a[0];
            p.getInventory().setItem(10, new ItemStack(Items.DIAMOND_BLOCK));
            p.teleport(new net.minecraft.world.level.portal.TeleportTransition(arena, new Vec3(ArenaLayout.EXTRACTION_PORTAL.getX() + 0.5, ArenaLayout.EXTRACTION_PORTAL.getY(), ArenaLayout.EXTRACTION_PORTAL.getZ() + 0.5), Vec3.ZERO, 0F, 0F, net.minecraft.world.level.portal.TeleportTransition.DO_NOTHING));
            p.setPortalCooldown(0);
            ColosseumSelfTest.HOLD.put(p, ArenaLayout.EXTRACTION_PORTAL);
        });
        waitFor(() -> !ColosseumDimension.isArena(a[0].level()), "Ausstieg über das Exfil-Portal");
        then(() -> {
            ColosseumSelfTest.HOLD.remove(a[0]);
            ServerPlayer p = a[0];
            check("Ausstieg: Ertrag von 5 Runden mal 1,5 aufs Konto (+" + paid[0] + ")", PointsAccount.balance(p) == paid[1] + paid[0] && paid[0] == 7500);
            check("Ausstieg: keine XP-Flaschen als Belohnung", count(p, Items.EXPERIENCE_BOTTLE) == 0);
            check("Ausstieg: Perks sind weg", p.getMaxHealth() == 20F && !RUN.hasPerk(p.getUUID(), Perk.TOUGH));
            check("Ausstieg: Arena-Gear bleibt zurück", count(p, Items.DIAMOND_BLOCK) == 0 && count(p, Items.IRON_SWORD) == 0 && !p.getItemBySlot(EquipmentSlot.OFFHAND).is(Items.SHIELD));
            check("Ausstieg: Durchlauf geht für den Partner weiter", RUN.isAlive(b[0].getUUID()) && RUN.active());
            ArenaRun.pauseTicks = 30;
        });
        waitFor(() -> RUN.phase() == ArenaRun.Phase.WAVE && RUN.round() == 6, "Runde 6 nach dem Exfil");
        then(() -> check("Wer nicht rausgeht, spielt weiter: Portal wieder aus", !arena.getBlockState(ArenaLayout.EXTRACTION_PORTAL).is(ModBlocks.PORTAL.get()) && !RUN.isExfilWave()));

        // --- Runden 8, 12, 16, 20 prüfen wir direkt
        final int[][] jumps = {{8}, {12}, {16}, {20}, {24}};
        for (int[] jump : jumps) {
            int r = jump[0];
            waitFor(() -> RUN.queued() == 0, "Spawnen vor Runde " + r);
            then(RunSelfTest::killAll);
            waitFor(() -> RUN.phase() == ArenaRun.Phase.PAUSE, "Pause vor Runde " + r);
            then(() -> RUN.setRoundForTest(r));
            waitSpawned(r);
            then(() -> {
                List<Mob> all = mobs();
                switch (WaveComposer.type(r)) {
                    case SPIDER -> {
                        long skeletons = all.stream().filter(m -> m.getType() == EntityTypes.SKELETON || m.getType() == EntityTypes.STRAY).count();
                        check("Runde " + r + ": höchstens " + (r / 2) + " Skelette, gefunden " + skeletons, skeletons <= r / 2);
                        check("Runde " + r + ": Spinnen da, Skelette nur als Reiter", all.stream().anyMatch(m -> m.getType() == EntityTypes.SPIDER || m.getType() == EntityTypes.CAVE_SPIDER) && all.stream().filter(m -> m.getType() == EntityTypes.SKELETON || m.getType() == EntityTypes.STRAY).allMatch(m -> m.getVehicle() != null && (m.getVehicle().getType() == EntityTypes.SPIDER || m.getVehicle().getType() == EntityTypes.CAVE_SPIDER)));
                    }
                    case CHICKEN -> check("Runde " + r + ": wieder Hühner-Jockeys", all.stream().anyMatch(m -> m.getVehicle() instanceof Chicken));
                    case HEAVY -> {
                        check("Runde " + r + ": Verwüster mit Armbrust-Reiter", all.stream().anyMatch(m -> m.getVehicle() != null && m.getVehicle().getType() == EntityTypes.RAVAGER && m.getMainHandItem().is(Items.CROSSBOW) && (m.getType() == EntityTypes.PIGLIN || m.getType() == EntityTypes.PILLAGER)));
                        check("Runde " + r + ": Ertrunkene mit Dreizack", all.stream().filter(m -> m.getType() == EntityTypes.DROWNED).allMatch(m -> m.getMainHandItem().is(Items.TRIDENT)) && all.stream().anyMatch(m -> m.getType() == EntityTypes.DROWNED));
                    }
                    default -> check("Runde " + r + " ist eine Spezialrunde", false);
                }
                Mob victim = all.stream().filter(m -> m.getVehicle() == null && m.getPassengers().isEmpty()).findFirst().orElse(all.get(0));
                victim.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
                victim.setDropChance(EquipmentSlot.CHEST, 0F);
                victim.kill(arena);
                check("Runde " + r + ": Mobs lassen keine Rüstung fallen", arena.getEntitiesOfClass(ItemEntity.class, ArenaLayout.BOUNDS).stream().noneMatch(i -> i.getItem().is(Items.DIAMOND_CHESTPLATE)));
                if (r >= 12) check("Runde " + r + ": Mobs tragen Rüstung oder haben Effekte", all.stream().anyMatch(m -> !m.getItemBySlot(EquipmentSlot.CHEST).isEmpty() || !m.getActiveEffects().isEmpty()));
            });
            if (r == 20) {
                // Hier geht es nur um die Mobs: Der reglose Testspieler soll nicht vorher sterben.
                then(() -> {
                    b[0].addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.RESISTANCE, 400, 4, false, false));
                    // Keine Drops: viele Mobs vom Spieler getötet, Zombies und Skelette geben sonst oft Items und immer XP
                    for (int i = 0; i < 12; i++) {
                        Mob prey = MobFactory.spawn(arena, new BlockPos(i - 6, 65, 24), i % 2 == 0 ? WaveComposer.Kind.ZOMBIE : WaveComposer.Kind.SKELETON, 20, Difficulty.NORMAL, RandomSource.create(i));
                        prey.hurtServer(arena, b[0].damageSources().playerAttack(b[0]), 100000F);
                    }
                });
                sleep(30);
                then(() -> {
                    check("Mobs lassen keine Items fallen", arena.getEntitiesOfClass(ItemEntity.class, ArenaLayout.BOUNDS).stream().filter(i -> !i.hasCustomName()).noneMatch(i -> i.getItem().is(Items.ROTTEN_FLESH) || i.getItem().is(Items.BONE) || i.getItem().is(Items.ARROW)));
                    check("Mobs lassen keine XP fallen", arena.getEntitiesOfClass(net.minecraft.world.entity.ExperienceOrb.class, ArenaLayout.BOUNDS).isEmpty());
                    check("Boss hat eine Lebensleiste", RUN.bossBarProgress().size() == 1 && RUN.bossBarProgress().get(0) == 1F);
                    mobs().stream().filter(m -> m.entityTags().contains(MobFactory.BOSS_TAG)).forEach(m -> m.setHealth(m.getMaxHealth() / 2F));
                });
                sleep(10);
                then(() -> {
                    check("Boss-Leiste zeigt das Leben (" + RUN.bossBarProgress() + ")", RUN.bossBarProgress().size() == 1 && Math.abs(RUN.bossBarProgress().get(0) - 0.5F) < 0.01F);
                    mobs().stream().filter(m -> m.entityTags().contains(MobFactory.BOSS_TAG)).forEach(Mob::discard);
                });
                sleep(10);
                then(() -> check("Boss weg: Leiste weg", RUN.bossBarProgress().isEmpty()));
                // Der Lock-Creeper
                final Mob[] luredMob = new Mob[1];
                final net.minecraft.world.entity.monster.Creeper[] lure = new net.minecraft.world.entity.monster.Creeper[1];
                final int[] decoyPoints = new int[1];
                final Vec3[] lurePos = new Vec3[1];
                then(() -> {
                    ServerPlayer p = b[0];
                    Shop.Offer offer = Shop.OFFERS.stream().filter(o -> o.line1().equals("offer.decoy")).findFirst().orElseThrow();
                    check("Lock-Creeper: an der Wand im Vorhof", offer.price() == 1500 && ArenaLayout.zone(offer.button().getX(), offer.button().getZ()) == ArenaLayout.ZONE_START);
                    p.getInventory().setItem(p.getInventory().getSelectedSlot(), new ItemStack(Items.ACACIA_BOAT));
                    p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, de.ecri.colosseum.shop.Decoys.item());
                    check("Lock-Creeper: im Inventar als Creeper-Kopf", p.getMainHandItem().is(Items.CREEPER_HEAD) && de.ecri.colosseum.shop.Decoys.isItem(p.getMainHandItem()));
                    check("Lock-Creeper: normaler Kopf zählt nicht", !de.ecri.colosseum.shop.Decoys.isItem(new ItemStack(Items.CREEPER_HEAD)));
                    net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickItem(p, net.minecraft.world.InteractionHand.MAIN_HAND));
                    check("Lock-Creeper: Wurf verbraucht ihn und erzeugt ein Geschoss", p.getMainHandItem().isEmpty() && !arena.getEntitiesOfClass(net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball.class, ArenaLayout.BOUNDS, ball -> ball.entityTags().contains(de.ecri.colosseum.shop.Decoys.PROJECTILE_TAG)).isEmpty());
                    // Den Aufprall hängt von der Flugbahn ab, deshalb hier direkt absetzen
                    de.ecri.colosseum.shop.Decoys.fuseTicks = 40;
                    BlockPos spot = new BlockPos(0, 65, 26);
                    lure[0] = RUN.decoys().land(arena, spot, p.getUUID());
                    check("Lock-Creeper: klein, leuchtet nicht, ohne KI und ohne Namen", lure[0] != null && lure[0].isNoAi() && lure[0].getScale() < 0.6F && !lure[0].hasEffect(MobEffects.GLOWING) && lure[0].getCustomName() == null);
                    lurePos[0] = lure[0].position();
                    luredMob[0] = MobFactory.spawn(arena, spot, WaveComposer.Kind.ZOMBIE, 1, Difficulty.NORMAL, RandomSource.create(9));
                    lure[0].push(5.0, 0.0, 5.0);
                    lure[0].hurtServer(arena, p.damageSources().playerAttack(p), 100000F);
                    check("Lock-Creeper: lässt sich nicht töten", lure[0].isAlive());
                    decoyPoints[0] = RUN.points(p.getUUID());
                });
                sleep(10);
                then(() -> {
                    check("Lock-Creeper: lockt Mobs an", luredMob[0].getTarget() == lure[0]);
                    double away = Math.hypot(lure[0].getX() - lurePos[0].x, lure[0].getZ() - lurePos[0].z);
                    check("Lock-Creeper: tippelt auf der Stelle, Mob und Stoß schieben ihn nicht weg (" + away + ")", away > 0.01 && away <= de.ecri.colosseum.shop.Decoys.STOMP_RADIUS + 0.01);
                    check("Lock-Creeper: etwa halb so groß wie ein normaler Creeper (" + lure[0].getScale() + ")", Math.abs(lure[0].getScale() - 0.5F) < 0.01F);
                });
                sleep(40);
                then(() -> {
                    check("Lock-Creeper: explodiert und ist weg", lure[0].isRemoved() && RUN.decoys().count() == 0);
                    check("Lock-Creeper: Explosion tötet Mobs in der Nähe und bringt Punkte", luredMob[0].isDeadOrDying() && RUN.points(b[0].getUUID()) > decoyPoints[0]);
                    de.ecri.colosseum.shop.Decoys.fuseTicks = 160;
                });
                // Leere Flaschen und Cheat-Menü
                then(() -> {
                    ServerPlayer p = b[0];
                    ItemStack rest = net.neoforged.neoforge.event.EventHooks.onItemUseFinish(p, new ItemStack(Items.POTION), 0, new ItemStack(Items.GLASS_BOTTLE));
                    check("Getrunkener Trank: keine leere Flasche in der Arena", rest.isEmpty());
                    ItemStack other = net.neoforged.neoforge.event.EventHooks.onItemUseFinish(p, new ItemStack(Items.COOKED_BEEF), 0, new ItemStack(Items.COOKED_BEEF, 3));
                    check("Andere Reste bleiben", other.getCount() == 3);

                    server.getCommands().performPrefixedCommand(p.createCommandSourceStack(), "colosseum debug");
                    check("Cheat-Menü: ohne Operator-Rechte geht es nicht", !(p.containerMenu instanceof de.ecri.colosseum.shop.DebugMenu.Menu));
                    server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withEntity(p), "colosseum debug");
                    check("Cheat-Menü: mit Rechten öffnet es", p.containerMenu instanceof de.ecri.colosseum.shop.DebugMenu.Menu);
                    int before = RUN.points(p.getUUID()), shop = PointsAccount.balance(p);
                    java.util.function.Consumer<de.ecri.colosseum.shop.DebugMenu.Action> click = x -> p.containerMenu.clicked(x.slot, 0, net.minecraft.world.inventory.ContainerInput.PICKUP, p);
                    click.accept(de.ecri.colosseum.shop.DebugMenu.Action.POINTS_10K);
                    check("Cheat-Menü: Punkte kommen an und der Lauf zählt nicht mehr", RUN.points(p.getUUID()) == before + 10000 && RUN.isDebugRun());
                    click.accept(de.ecri.colosseum.shop.DebugMenu.Action.SHOP_15K);
                    check("Cheat-Menü: Shop-Punkte kommen an", PointsAccount.balance(p) == shop + 15000);
                    click.accept(de.ecri.colosseum.shop.DebugMenu.Action.POWER);
                    check("Cheat-Menü: Strom", RUN.isPowerOn());
                    click.accept(de.ecri.colosseum.shop.DebugMenu.Action.DOORS);
                    boolean open = true;
                    for (ArenaDoors.Door door : ArenaDoors.ALL) open &= RUN.isDoorOpen(door);
                    check("Cheat-Menü: alle Türen offen", open);
                    p.setHealth(5F);
                    click.accept(de.ecri.colosseum.shop.DebugMenu.Action.HEAL);
                    check("Cheat-Menü: Heilen", p.getHealth() == p.getMaxHealth());
                    click.accept(de.ecri.colosseum.shop.DebugMenu.Action.DECOYS);
                    check("Cheat-Menü: 4 Lock-Creeper", count(p, Items.CREEPER_HEAD) == 4);
                    click.accept(de.ecri.colosseum.shop.DebugMenu.Action.BOSS);
                    check("Cheat-Menü: Boss kommt", mobs().stream().anyMatch(m -> m.entityTags().contains(MobFactory.BOSS_TAG)));
                    mobs().stream().filter(m -> m.entityTags().contains(MobFactory.BOSS_TAG)).forEach(Mob::discard);
                    p.getInventory().clearContent();
                    p.closeContainer();
                    RUN.clearDebugRunForTest();
                });
                sleep(60);
                then(() -> {
                    check("Runde 20: nach 5 Sekunden lebt alles noch und niemand brennt", !mobs().isEmpty() && mobs().stream().noneMatch(Entity::isOnFire));
                    check("Piglins werden nicht zu Zombies", mobs().stream().noneMatch(m -> m.getType() == EntityTypes.ZOMBIFIED_PIGLIN));
                    long drowned = mobs().stream().filter(m -> m.getType() == EntityTypes.DROWNED).count(), drownedHunting = mobs().stream().filter(m -> m.getType() == EntityTypes.DROWNED && m.getTarget() != null).count();
                    check("Ertrunkene haben ein Ziel: " + drownedHunting + " von " + drowned, drownedHunting * 10 >= drowned * 7);
                });
            }
            finishWave(r);
        }

        // --- Exfil nicht gerufen: Knopf verschwindet, es geht normal weiter
        waitFor(() -> RUN.queued() == 0, "Spawnen vor Runde 10");
        then(RunSelfTest::killAll);
        waitFor(() -> RUN.phase() == ArenaRun.Phase.PAUSE, "Pause vor Runde 10");
        then(() -> RUN.setRoundForTest(10));
        waitSpawned(10);
        then(() -> {
            List<Mob> bosses = mobs().stream().filter(m -> m.entityTags().contains(MobFactory.BOSS_TAG)).toList();
            check("Runde 10: genau ein Boss", bosses.size() == 1);
            Mob boss = bosses.get(0);
            check("Boss ist stark und groß: " + (int) boss.getMaxHealth() + " Leben", boss.getMaxHealth() >= 200F && boss.getScale() > 1.4F && boss.getType() == EntityTypes.WITHER_SKELETON);
            check("Boss passt durch das Tor", boss.getBbHeight() < 4F);
            ServerPlayer p = b[0];
            int before = RUN.points(p.getUUID());
            int ground = RUN.powerUpsOnGround();
            boss.setHealth(1F);
            boss.hurtServer(arena, arena.damageSources().playerAttack(p), 100F);
            check("Boss bringt viele Punkte: +" + (RUN.points(p.getUUID()) - before), RUN.points(p.getUUID()) - before == 1500);
            check("Boss lässt ein Power-up fallen", RUN.powerUpsOnGround() == ground + 1);
        });
        finishWave(10);
        then(() -> {
            check("Runde 10: Kisten auch ohne 3er-Runde", RUN.chestPositions().size() == 2);
        });
        then(() -> check("Runde 10: Exfil-Knopf steht wieder", arena.getBlockState(ArenaLayout.EXFIL_BUTTON).is(Blocks.POLISHED_BLACKSTONE_BUTTON)));
        waitFor(() -> RUN.phase() == ArenaRun.Phase.WAVE && RUN.round() == 11, "Runde 11");
        then(() -> check("Exfil nicht gerufen: normale Runde, Knopf weg", !RUN.isExfilWave() && arena.getBlockState(ArenaLayout.EXFIL_BUTTON).isAir()));

        // --- Loot-Stufen
        then(() -> {
            RandomSource random = RandomSource.create(42);
            boolean low = true, mid = true, high = true, midStrong = false, highGap = false, noPearls = true;
            int armor = 0, total = 0;
            boolean noDurability = true;
            for (int i = 0; i < 300; i++) {
                for (int round : new int[] {3, 10, 20}) for (ItemStack st : SupplyChests.roll(round, arena.registryAccess(), random)) {
                    total++;
                    if (st.get(DataComponents.EQUIPPABLE) != null && !st.is(Items.SHIELD)) armor++;
                    noDurability &= !st.isDamageableItem();
                }
                for (ItemStack st : SupplyChests.roll(3, arena.registryAccess(), random)) low &= !st.isEmpty() && !st.is(Items.GOLDEN_APPLE) && !st.is(Items.DIAMOND_SWORD);
                for (ItemStack st : SupplyChests.roll(10, arena.registryAccess(), random)) { mid &= !st.isEmpty() && !st.is(Items.TOTEM_OF_UNDYING); midStrong |= st.is(Items.GOLDEN_CARROT); noPearls &= !st.is(Items.ENDER_PEARL); }
                for (ItemStack st : SupplyChests.roll(20, arena.registryAccess(), random)) { high &= !st.isEmpty() && !st.is(Items.BREAD); highGap |= st.is(Items.GOLDEN_APPLE); noPearls &= !st.is(Items.ENDER_PEARL); }
            }
            boolean limit = true, zero = true, top = true;
            for (int round = 1; round <= 40; round++) for (int players = 1; players <= 3; players++) {
                boolean sawZero = false, sawMax = false;
                for (int i = 0; i < 300; i++) {
                    int regular = WaveComposer.skeletonCount(WaveComposer.compose(round, players, random));
                    int exfil = WaveComposer.skeletonCount(WaveComposer.composeExfil(round, players, random));
                    limit &= regular <= round / 2 && exfil <= round / 2;
                    sawZero |= regular == 0;
                    sawMax |= regular == round / 2;
                }
                WaveComposer.Type type = WaveComposer.type(round);
                if (type == WaveComposer.Type.REGULAR || type == WaveComposer.Type.SPIDER) { zero &= sawZero; top &= sawMax; }
            }
            check("Skelette: nie mehr als die halbe Rundenzahl, Runden 1 bis 40, 1 bis 3 Spieler", limit);
            check("Skelette: zufällig von null bis zum Maximum", zero && top);
            check("Runde 1 hat keine Skelette", WaveComposer.skeletonCount(WaveComposer.compose(1, 2, random)) == 0);
            check("Runde 5 hat höchstens 2 Skelette, Gegnerzahl bleibt", WaveComposer.skeletonCount(WaveComposer.compose(5, 2, random)) <= 2 && WaveComposer.compose(5, 2, random).size() == 21);
            check("Loot geht nie kaputt", noDurability);
            check("Wenig Rüstung im Loot: " + (100 * armor / total) + " %", armor * 100 / total <= 12);
            check("Loot Runde 1 bis 5 ist einfach", low);
            check("Loot Runde 6 bis 14 ist mittel", mid && midStrong);
            check("Keine Enderperlen im Loot und im Shop", noPearls && Shop.OFFERS.stream().noneMatch(o -> o.line1().contains("ender")));
            check("Loot ab Runde 15 ist stark, mit Goldäpfeln", high && highGap);
            check("Konto: Tod in Runde 11 bringt 15.000", PointsAccount.totalFor(10) == 15000);
            check("Konto: Runde 15 geschafft ist das Doppelte von Runde 10", PointsAccount.totalFor(15) == 2 * PointsAccount.totalFor(10));
            boolean rising = true;
            for (int r = 2; r <= 40; r++) rising &= PointsAccount.roundGain(r, Difficulty.NORMAL) > PointsAccount.roundGain(r - 1, Difficulty.NORMAL);
            check("Konto: Jede Runde bringt mehr als die davor", rising);
            check("Konto: Exfil zählt 1,5-fach", PointsAccount.withExfil(2000) == 3000);
            check("Konto: Faktor der Schwierigkeit", PointsAccount.roundGain(1, Difficulty.HARD) == 900 && PointsAccount.roundGain(1, Difficulty.BLOOD_GOD) == 1200);
            java.util.function.ToIntFunction<String> priceOf = name -> PointsShop.OFFERS.stream().filter(o -> o.name().startsWith(name)).findFirst().orElseThrow().price();
            check("Shop: 30 Level, 16 Diamanten und ein Netherit-Barren kosten genau den Ertrag bis Runde 10", priceOf.applyAsInt("points.30_levels") == 15000 && priceOf.applyAsInt("points.16_diamonds") == 15000 && priceOf.applyAsInt("points.netherite_ingot") == 15000);
            check("Shop: viele Waren, jeder Platz nur einmal (" + PointsShop.OFFERS.size() + ")", PointsShop.OFFERS.size() == 44 && PointsShop.OFFERS.stream().map(PointsShop.Offer::slot).distinct().count() == 44);
            check("Shop: 200 Flaschen reichen im Schnitt für 30 Level", PointsShop.BOTTLES_FOR_30_LEVELS * 7 >= 1395);
            check("Steak an der Wand: 4 Stück für 100", Shop.OFFERS.get(0).price() == 100);
        });

        // --- Letzter stirbt: echter Tod, Oberwelt, Flaschen, Arena räumt auf
        waitFor(() -> RUN.phase() == ArenaRun.Phase.WAVE && RUN.queued() == 0, "letzte Welle");
        then(() -> {
            ServerPlayer n = dieAndRespawn(b[0]);
            b[0] = n;
            check("Letzter stirbt: steht in der Halle", ColosseumDimension.isArena(n.level()) && ArenaLayout.isLobbySide(n.getZ()));
            check("Letzter stirbt: Punkte auf dem Konto", PointsAccount.balance(n) > 0 || RUN.points(n.getUUID()) > 0);
            check("Letzter stirbt: Gear ist weg", n.getInventory().isEmpty());
        });
        sleep(3);
        then(() -> {
            check("Bestenliste: Ben steht drin", Leaderboard.byRounds().stream().anyMatch(x -> x.name().equals("Ben") && x.rounds() >= 10));
            check("Bestenliste: Anna steht mit 5 Runden drin", Leaderboard.byRounds().stream().anyMatch(x -> x.name().equals("Anna") && x.rounds() == 5 && x.points() > 0));
            check("Bestenliste: sortiert nach Runden", Leaderboard.byRounds().get(0).name().equals("Ben"));
            String board = signText(new BlockPos(-5, 68, ArenaLayout.LOBBY_Z0));
            check("Tafel in der Halle zeigt den Ersten: " + board, board.contains("1. Ben") && board.contains("R"));
            check("Tafel zeigt auch die Punkte", signText(new BlockPos(3, 68, ArenaLayout.LOBBY_Z0)).contains("1. "));
            int before = Leaderboard.byRounds().size();
            Leaderboard.load(server);
            check("Bestenliste überlebt das Neuladen aus der Datei (" + before + " Einträge)", Leaderboard.byRounds().size() == before && before >= 2);
            arena.getBlockEntity(new BlockPos(-5, 68, ArenaLayout.LOBBY_Z0));
            check("Türen sind wieder zu", !RUN.isDoorOpen(ArenaDoors.NORD) && arena.getBlockState(new BlockPos(0, 65, 4)).is(Blocks.IRON_BARS) && !RUN.isZoneOpen(ArenaLayout.ZONE_NORTH));
            tp(b[0], false);
            check("Durch das Hallen-Portal: Konto bleibt, keine Flaschen", PointsAccount.balance(b[0]) > 0 && count(b[0], Items.EXPERIENCE_BOTTLE) == 0);
        });
        then(() -> {
            check("Durchlauf beendet", RUN.phase() == ArenaRun.Phase.LOBBY && !RUN.active());
            check("Punkte gelten nur im Durchlauf", RUN.points(b[0].getUUID()) == 0);
            check("Arena aufgeräumt: keine Mobs", mobs().isEmpty());
            boolean none = true;
            for (BlockPos c : ArenaLayout.CHEST_SPOTS) none &= !arena.getBlockState(c).is(Blocks.CHEST);
            check("Arena aufgeräumt: keine Kisten", none);
            check("Arena aufgeräumt: Tore zu", gateClosed() && mobGateClosed());
            check("Arena aufgeräumt: Exfil-Portal und Knopf weg", !arena.getBlockState(ArenaLayout.EXTRACTION_PORTAL).is(ModBlocks.PORTAL.get()) && arena.getBlockState(ArenaLayout.EXFIL_BUTTON).isAir());
            check("Hebel wieder aus", !arena.getBlockState(ArenaLayout.LEVER_LEFT).getValue(BlockStateProperties.POWERED));
        });

        // --- Solo auf Blutgott
        then(() -> {
            ColosseumSelfTest.tp(a[0], true);
        });
        sleep(3);
        then(() -> {
            press(a[0], PlayerClass.GLADIATOR.button());
            press(a[0], Difficulty.BLOOD_GOD.button());
            press(a[0], ArenaLayout.LEVER_LEFT);
        });
        sleep(3);
        then(() -> {
            check("Solo: Hebel reicht, Tor offen", RUN.phase() == ArenaRun.Phase.GATE_OPEN);
            int before = count(a[0], Items.COOKED_BEEF);
            press(a[0], Shop.OFFERS.get(0).button());
            check("In der Lobby-Phase kann man nichts kaufen", count(a[0], Items.COOKED_BEEF) == before);
            toSand(a[0], 3.5, 18.5);
            a[0].getFoodData().setFoodLevel(20);
        });
        waitSpawned(1);
        then(() -> {
            check("Blutgott: Hunger bleibt unter der Regenerationsgrenze", a[0].getFoodData().getFoodLevel() <= 17 && a[0].getFoodData().getSaturationLevel() == 0F);
            Mob z = mobs().stream().filter(m -> m.getType() == EntityTypes.ZOMBIE || m.getType() == EntityTypes.HUSK).findFirst().orElse(null);
            check("Blutgott: Mob +30 % Leben", z != null && Math.abs(z.getMaxHealth() - 26F) < 0.01F);
            check("Blutgott: Mob schneller", z != null && z.getAttributeValue(Attributes.MOVEMENT_SPEED) > 0.23 * 1.15);
            check("Solo: weniger Gegner", mobs().size() == 6);
            a[0].setHealth(10F);
        });
        sleep(120);
        then(() -> check("Blutgott: Keine Regeneration (Leben " + a[0].getHealth() + ")", a[0].getHealth() <= 10F));
        finishWave(1);
        final int[] blood = new int[2];
        then(() -> {
            blood[0] = PointsAccount.balance(a[0]);
            blood[1] = RUN.points(a[0].getUUID());
            check("Blutgott: Runde 1 bringt 1200 für den Shop", PointsAccount.pending(a[0]) == 1200);
        });

        // --- Anmeldung während des Durchlaufs und danach
        final ServerPlayer[] c = new ServerPlayer[1];
        then(() -> {
            c[0] = ColosseumSelfTest.join("Cara", UUID.nameUUIDFromBytes("c".getBytes()));
            c[0].connection.markClientLoaded();
            tp(c[0], true);
        });
        sleep(3);
        then(() -> {
            check("Dritter während des Durchlaufs bleibt in der Lobby", ArenaLayout.isLobbySide(c[0].getZ()) && !RUN.isParticipant(c[0].getUUID()));
            press(c[0], PlayerClass.GLADIATOR.button());
            check("Dritter kann keine Klasse wählen", c[0].getInventory().isEmpty());
            // --- Punkte-Shop in der Halle
            check("Shop: Knopf und Schild stehen", arena.getBlockState(PointsShop.BUTTON).getBlock() instanceof net.minecraft.world.level.block.ButtonBlock && signText(PointsShop.BUTTON.above()).contains("POINTS SHOP"));
            press(c[0], PointsShop.BUTTON);
            check("Shop: Knopf öffnet das Menü", c[0].containerMenu instanceof PointsShop.ShopMenu);
            int slot = PointsShop.OFFERS.stream().filter(o -> o.name().equals("points.1_diamond")).findFirst().orElseThrow().slot();
            c[0].containerMenu.clicked(slot, 0, net.minecraft.world.inventory.ContainerInput.PICKUP, c[0]);
            check("Shop: ohne Punkte kein Kauf", XpLedger.goods(c[0]).isEmpty() && PointsAccount.balance(c[0]) == 0);
            PointsAccount.add(c[0], 100000);
            ColosseumSelfTest.PLAYERS.remove(a[0]);
            server.getPlayerList().remove(a[0]);
        });
        sleep(30);
        then(() -> {
            check("Einziger Kämpfer meldet sich ab: Durchlauf endet", RUN.phase() == ArenaRun.Phase.LOBBY);
            a[0] = ColosseumSelfTest.join("Anna", UUID.nameUUIDFromBytes("a".getBytes()));
            a[0].connection.markClientLoaded();
            check("Danach neu angemeldet: in der Lobby", ColosseumDimension.isArena(a[0].level()) && ArenaLayout.isLobbySide(a[0].getZ()));
            check("Danach neu angemeldet: Gear weg, Ertrag auf dem Konto", a[0].getInventory().isEmpty() && PointsAccount.balance(a[0]) == blood[0] + 1200);
            check("Danach neu angemeldet: Sicherung leer", a[0].getData(de.ecri.colosseum.registry.ModAttachments.RUN_POINTS) == 0);
            tp(a[0], false);
            check("Danach raus: Konto bleibt", PointsAccount.balance(a[0]) == blood[0] + 1200);
            int diamond = PointsShop.OFFERS.stream().filter(o -> o.name().equals("points.1_diamond")).findFirst().orElseThrow().slot();
            c[0].containerMenu.clicked(diamond, 0, net.minecraft.world.inventory.ContainerInput.PICKUP, c[0]);
            check("Shop: Diamant gekauft", PointsAccount.balance(c[0]) == 100000 - PointsShop.DIAMOND_PRICE && XpLedger.goods(c[0]).size() == 1);
            c[0].containerMenu.clicked(diamond, 0, net.minecraft.world.inventory.ContainerInput.PICKUP, c[0]);
            check("Shop: Doppelklick kauft nur einmal", XpLedger.goods(c[0]).size() == 1);
            check("Shop: Das Menü gibt keine Items heraus", c[0].containerMenu.getCarried().isEmpty() && count(c[0], Items.DIAMOND) == 0);
        });
        sleep(10);
        then(() -> {
            int bottles = PointsShop.OFFERS.stream().filter(o -> o.bottles() == 16).findFirst().orElseThrow().slot();
            c[0].containerMenu.clicked(bottles, 0, net.minecraft.world.inventory.ContainerInput.PICKUP, c[0]);
            check("Shop: 16 Flaschen liegen bereit", XpLedger.balance(c[0]) == 16 && PointsAccount.balance(c[0]) == 100000 - PointsShop.DIAMOND_PRICE - 16 * PointsShop.BOTTLE_PRICE);
            check("Shop: Gekauftes ist noch nicht im Arena-Inventar", count(c[0], Items.DIAMOND) == 0 && count(c[0], Items.EXPERIENCE_BOTTLE) == 0);
            ColosseumSelfTest.PLAYERS.remove(c[0]);
            server.getPlayerList().remove(c[0]);
            c[0] = ColosseumSelfTest.join("Cara", UUID.nameUUIDFromBytes("c".getBytes()));
            c[0].connection.markClientLoaded();
            check("Shop: Waren und Konto überleben die Spielerdatei", XpLedger.goods(c[0]).size() == 1 && XpLedger.balance(c[0]) == 16 && PointsAccount.balance(c[0]) == 100000 - PointsShop.DIAMOND_PRICE - 16 * PointsShop.BOTTLE_PRICE);
            tp(c[0], false);
            check("Shop: Beim Verlassen kommt alles an, ohne Shop-Beschriftung", count(c[0], Items.EXPERIENCE_BOTTLE) == 16 && count(c[0], Items.DIAMOND) == 1 && XpLedger.goods(c[0]).isEmpty()
                && c[0].getInventory().getItem(c[0].getInventory().findSlotMatchingItem(new ItemStack(Items.DIAMOND))).get(net.minecraft.core.component.DataComponents.CUSTOM_NAME) == null);
            ArenaBuilder.forceChunks(arena, false);
        });
    }

    private RunSelfTest() {}
}
