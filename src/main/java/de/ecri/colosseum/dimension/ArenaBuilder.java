package de.ecri.colosseum.dimension;

import de.ecri.colosseum.board.Leaderboard;
import de.ecri.colosseum.portal.PortalFrame;
import de.ecri.colosseum.run.Difficulty;
import de.ecri.colosseum.run.PlayerClass;
import de.ecri.colosseum.shop.PackAPunch;
import de.ecri.colosseum.shop.Perk;
import de.ecri.colosseum.shop.Shop;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.WallTorchBlock;

import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Baut Kolosseum und Lobby aus Vanilla-Blöcken. Einmal pro Welt, und neu, wenn BUILD_VERSION steigt. */
public final class ArenaBuilder {
    private static final int BUILD_VERSION = 16;
    private static final String DISPLAY_VERSION_TAG = "colosseum_display_v" + BUILD_VERSION;
    public static final String DISPLAY_TAG = "colosseum_display";
    private static int unforceAt = -1;

    public static void register() {
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent e) -> {
            ServerLevel arena = e.getServer().getLevel(ColosseumDimension.ARENA);
            if (arena != null) build(arena);
        });
        // Rüstungsständer eines älteren Baus tauchen erst beim Laden ihres Chunks auf und bleiben weg.
        NeoForge.EVENT_BUS.addListener((EntityJoinLevelEvent e) -> {
            var tags = e.getEntity().entityTags();
            if (tags.contains(DISPLAY_TAG) && !tags.contains(DISPLAY_VERSION_TAG)) e.setCanceled(true);
        });
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> {
            if (unforceAt >= 0 && e.getServer().getTickCount() >= unforceAt) {
                unforceAt = -1;
                ServerLevel arena = e.getServer().getLevel(ColosseumDimension.ARENA);
                if (arena != null) forceChunks(arena, false);
            }
        });
    }

    public static void forceChunks(ServerLevel level, boolean force) {
        ChunkPos min = ChunkPos.containing(BlockPos.containing(ArenaLayout.BOUNDS.minX, 64, ArenaLayout.BOUNDS.minZ));
        ChunkPos max = ChunkPos.containing(BlockPos.containing(ArenaLayout.BOUNDS.maxX, 64, ArenaLayout.BOUNDS.maxZ));
        for (int x = min.x(); x <= max.x(); x++) for (int z = min.z(); z <= max.z(); z++) level.setChunkForced(x, z, force);
    }

    private static void build(ServerLevel level) {
        BlockPos marker = new BlockPos(0, 2 + BUILD_VERSION, 0);
        if (level.getBlockState(marker).is(Blocks.LODESTONE)) return;
        // Die Armorstände müssen gespeichert werden, deshalb bleiben die Chunks kurz geladen.
        forceChunks(level, true);
        unforceAt = level.getServer().getTickCount() + 100;
        level.getEntitiesOfClass(ArmorStand.class, ArenaLayout.BOUNDS, s -> s.entityTags().contains(DISPLAY_TAG)).forEach(ArmorStand::discard);

        clear(level);
        buildBowl(level);
        buildMobGates(level);
        buildEntryTunnel(level);
        buildHall(level);
        buildLobbyDecor(level);
        buildDecor(level);
        closeAllGates(level);
        closeEntryGate(level);
        level.setBlock(marker, Blocks.LODESTONE.defaultBlockState(), 2);
    }

    private static void set(ServerLevel level, int x, int y, int z, Block block) {
        level.setBlock(new BlockPos(x, y, z), block.defaultBlockState(), 18);
    }

    private static void clear(ServerLevel level) {
        for (BlockPos pos : BlockPos.betweenClosed((int) ArenaLayout.BOUNDS.minX, 55, (int) ArenaLayout.BOUNDS.minZ, (int) ArenaLayout.BOUNDS.maxX, 95, (int) ArenaLayout.BOUNDS.maxZ)) {
            if (!level.getBlockState(pos).isAir()) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 18);
        }
    }

    private static void buildBowl(ServerLevel level) {
        int f = ArenaLayout.FLOOR_Y, reach = ArenaLayout.TRIBUNE + 1;
        for (int x = -ArenaLayout.A - reach; x <= ArenaLayout.A + reach; x++) {
            for (int z = -ArenaLayout.B - reach; z <= ArenaLayout.B + reach; z++) {
                int t = ArenaLayout.ring(x, z);
                if (t > ArenaLayout.TRIBUNE) continue;
                for (int y = f - 4; y < f; y++) set(level, x, y, z, Blocks.SANDSTONE);
                if (t == 0) {
                    set(level, x, f, z, Blocks.SAND);
                    continue;
                }
                int top = t <= 2 ? ArenaLayout.WALL_TOP : ArenaLayout.WALL_TOP + (t - 1) / 2;
                for (int y = f; y <= top; y++) {
                    Block b = Blocks.STONE_BRICKS;
                    if (y == f + 5 || y == f + 10) b = Blocks.POLISHED_GRANITE;
                    if (y == top) b = t <= 2 ? Blocks.SANDSTONE : t % 4 < 2 ? Blocks.SMOOTH_SANDSTONE : Blocks.POLISHED_GRANITE;
                    set(level, x, y, z, b);
                }
                if (t == ArenaLayout.TRIBUNE && Math.floorMod(x + z, 3) != 0) {
                    for (int y = top + 1; y <= top + 3; y++) set(level, x, y, z, Blocks.STONE_BRICKS);
                }
                if (t == 2 && Math.floorMod(x * 3 + z * 5, 37) == 0) {
                    level.setBlock(new BlockPos(x, top + 1, z), Blocks.LANTERN.defaultBlockState(), 18);
                }
            }
        }
    }

    private static void buildMobGates(ServerLevel level) {
        for (ArenaLayout.MobGate gate : ArenaLayout.MOB_GATES) {
            for (BlockPos pos : gate.tunnel()) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 18);
            for (BlockPos pos : gate.bars()) set(level, pos.getX(), ArenaLayout.FLOOR_Y, pos.getZ(), Blocks.STONE_BRICKS);
            for (BlockPos pos : gate.tunnel()) set(level, pos.getX(), ArenaLayout.FLOOR_Y, pos.getZ(), Blocks.STONE_BRICKS);
        }
    }

    private static void buildEntryTunnel(ServerLevel level) {
        int f = ArenaLayout.FLOOR_Y;
        for (int z = ArenaLayout.B - 1; z < ArenaLayout.LOBBY_Z0; z++) {
            for (int x = -3; x <= 3; x++) {
                boolean wall = Math.abs(x) == 3;
                if (ArenaLayout.ring(x, z) == 0) continue;
                set(level, x, f, z, Blocks.POLISHED_DEEPSLATE);
                for (int y = f + 1; y <= f + ArenaLayout.GATE_HEIGHT; y++) set(level, x, y, z, wall ? Blocks.POLISHED_BLACKSTONE_BRICKS : Blocks.AIR);
                set(level, x, f + ArenaLayout.GATE_HEIGHT + 1, z, Blocks.POLISHED_BLACKSTONE);
            }
        }
        lever(level, ArenaLayout.LEVER_LEFT, Direction.EAST);
        lever(level, ArenaLayout.LEVER_RIGHT, Direction.WEST);
        sign(level, ArenaLayout.LEVER_LEFT.above(), Direction.EAST, de.ecri.colosseum.Lang.t("sign.lever1"), de.ecri.colosseum.Lang.t("sign.lever2"), de.ecri.colosseum.Lang.t("sign.lever3"));
        sign(level, ArenaLayout.LEVER_RIGHT.above(), Direction.WEST, de.ecri.colosseum.Lang.t("sign.lever1"), de.ecri.colosseum.Lang.t("sign.lever2"), de.ecri.colosseum.Lang.t("sign.lever3"));
    }

    /** Alles zum Drücken sitzt auf einer leuchtenden Platte und ist so vor jedem Hintergrund gut zu sehen. */
    private static void plate(ServerLevel level, BlockPos pos, Direction facing) {
        level.setBlock(pos.relative(facing.getOpposite()), Blocks.SEA_LANTERN.defaultBlockState(), 18);
    }

    private static void lever(ServerLevel level, BlockPos pos, Direction facing) {
        plate(level, pos, facing);
        level.setBlock(pos, Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE, AttachFace.WALL).setValue(LeverBlock.FACING, facing), 18);
    }

    private static void button(ServerLevel level, BlockPos pos, Direction facing) {
        plate(level, pos, facing);
        level.setBlock(pos, Blocks.POLISHED_BLACKSTONE_BUTTON.defaultBlockState().setValue(ButtonBlock.FACE, AttachFace.WALL).setValue(ButtonBlock.FACING, facing), 18);
    }

    private static void sign(ServerLevel level, BlockPos pos, Direction facing, String... lines) {
        Component[] parts = new Component[lines.length];
        for (int i = 0; i < lines.length; i++) parts[i] = Component.literal(lines[i] == null ? "" : lines[i]);
        sign(level, pos, facing, parts);
    }

    private static void sign(ServerLevel level, BlockPos pos, Direction facing, Component... lines) {
        level.setBlock(pos, Blocks.DARK_OAK_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING, facing), 18);
        if (level.getBlockEntity(pos) instanceof SignBlockEntity sign) {
            SignText.Mutable text = sign.getText(SignTextSlot.FRONT).asMutable();
            for (int i = 0; i < lines.length && i < 4; i++) text.setLine(i, lines[i]);
            // Weiß und leuchtend, damit man das Schild auch im Dunkeln lesen kann
            sign.setText(text.asImmutable().withColor(DyeColor.WHITE).withGlowingText(true), SignTextSlot.FRONT);
            sign.setWaxed(true);
        }
    }

    private static void buildHall(ServerLevel level) {
        int f = ArenaLayout.FLOOR_Y, top = ArenaLayout.LOBBY_TOP, z0 = ArenaLayout.LOBBY_Z0, z1 = ArenaLayout.LOBBY_Z1, w = ArenaLayout.LOBBY_X;
        for (int x = -w - 1; x <= w + 1; x++) {
            for (int z = z0 - 1; z <= z1 + 1; z++) {
                boolean edge = Math.abs(x) == w + 1 || z == z0 - 1 || z == z1 + 1;
                boolean door = z == z0 - 1 && Math.abs(x) <= 2;
                set(level, x, f, z, Math.floorMod(x + z, 2) == 0 ? Blocks.DEEPSLATE_TILES : Blocks.POLISHED_DEEPSLATE);
                set(level, x, top + 1, z, Blocks.POLISHED_BLACKSTONE);
                for (int y = f + 1; y <= top; y++) {
                    if (door && y <= f + ArenaLayout.GATE_HEIGHT) { set(level, x, y, z, Blocks.AIR); continue; }
                    Block wall = y == f + 4 ? Blocks.CUT_SANDSTONE : Blocks.POLISHED_BLACKSTONE_BRICKS;
                    set(level, x, y, z, edge ? wall : Blocks.AIR);
                }
            }
        }
        for (int i = 0; i < PlayerClass.values().length; i++) buildClassNiche(level, PlayerClass.values()[i], ArenaLayout.NICHE_Z[i]);
        for (int i = 0; i < Difficulty.values().length; i++) {
            Difficulty d = Difficulty.values()[i];
            BlockPos at = d.button();
            button(level, at, Direction.WEST);
            sign(level, at.above(), Direction.WEST, d.title(), d.line2(), d.line3());
            set(level, w + 1, at.getY() + 3, at.getZ(), d.accent());
        }
        BlockPos exit = ArenaLayout.EXIT_PORTAL;
        for (int i = -1; i <= 2; i++) for (int j = -1; j <= 3; j++) {
            if (i == -1 || i == 2 || j == -1 || j == 3) set(level, exit.getX() + i, exit.getY() + j, exit.getZ(), Blocks.SANDSTONE);
        }
        new PortalFrame(exit, Direction.Axis.X, 2, 3).fill(level);
        sign(level, new BlockPos(3, f + 2, exit.getZ() + 1), Direction.NORTH, de.ecri.colosseum.Lang.t("sign.back1"), de.ecri.colosseum.Lang.t("sign.back2"), de.ecri.colosseum.Lang.t("sign.back3"));
        BlockPos shop = de.ecri.colosseum.shop.PointsShop.BUTTON;
        button(level, shop, Direction.NORTH);
        sign(level, shop.above(), Direction.NORTH, de.ecri.colosseum.Lang.t("sign.shop1"), de.ecri.colosseum.Lang.t("sign.shop2"), de.ecri.colosseum.Lang.t("sign.shop3"));
        set(level, shop.getX(), shop.getY() + 3, shop.getZ() + 1, Blocks.EMERALD_BLOCK);
    }

    /** Der Vorbereitungsraum: helle Decke, Fackeln, Säulen, Banner, Feuerschalen und ein roter Teppich zum Tor. */
    private static void buildLobbyDecor(ServerLevel level) {
        int y = ArenaLayout.STAND_Y, z0 = ArenaLayout.LOBBY_Z0, z1 = ArenaLayout.LOBBY_Z1, w = ArenaLayout.LOBBY_X, top = ArenaLayout.LOBBY_TOP;
        for (int z = z0 + 1; z < z1; z += 3) {
            for (int x : new int[] {-4, 0, 4}) set(level, x, top + 1, z, Blocks.SHROOMLIGHT);
        }
        // Leuchtende Bodenplatten, die Decke allein ist zu weit weg
        for (int z = z0; z < z1; z += 3) for (int x : new int[] {-5, -2, 2, 5}) set(level, x, ArenaLayout.FLOOR_Y, z, Blocks.SHROOMLIGHT);
        // Lichtband im Sandsteinstreifen der Wände, damit auch die Ränder und Ecken hell sind
        for (int z = z0; z <= z1; z += 3) for (int x : new int[] {-w - 1, w + 1}) {
            if (level.getBlockState(new BlockPos(x, y + 3, z)).isSolid()) set(level, x, y + 3, z, Blocks.SHROOMLIGHT);
        }
        for (int x : new int[] {-5, 5}) set(level, x, y + 3, z0 - 1, Blocks.SHROOMLIGHT);
        for (int x : new int[] {-5, -2, 2, 5}) set(level, x, y + 3, z1 + 1, Blocks.SHROOMLIGHT);
        for (int z = z0 + 2; z < z1; z += 6) {
            for (int x : new int[] {-4, 4}) level.setBlock(new BlockPos(x, top, z), Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true), 18);
        }
        for (int z : new int[] {z0 + 2, z0 + 9, z0 + 16, z0 + 23}) {
            level.setBlock(new BlockPos(-w, y + 3, z), Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.EAST), 18);
            level.setBlock(new BlockPos(w, y + 3, z), Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.WEST), 18);
        }
        for (int z : new int[] {z0 + 8, z0 + 15, z0 + 22}) {
            level.setBlock(new BlockPos(-w, y + 4, z), Blocks.WALL_BANNER.pick(DyeColor.RED).defaultBlockState().setValue(WallBannerBlock.FACING, Direction.EAST), 18);
            level.setBlock(new BlockPos(w, y + 4, z), Blocks.WALL_BANNER.pick(DyeColor.RED).defaultBlockState().setValue(WallBannerBlock.FACING, Direction.WEST), 18);
        }
        for (int z : new int[] {z0 + 9, z0 + 16}) for (int x : new int[] {-5, 5}) {
            set(level, x, y, z, Blocks.CHISELED_SANDSTONE);
            for (int h = y + 1; h < top; h++) set(level, x, h, z, Blocks.CUT_SANDSTONE);
            set(level, x, top, z, Blocks.CHISELED_SANDSTONE);
        }
        for (int[] c : new int[][] {{-6, z0 + 1}, {6, z0 + 1}, {-6, z1 - 2}, {6, z1 - 2}}) {
            set(level, c[0], y, c[1], Blocks.CHISELED_POLISHED_BLACKSTONE);
            level.setBlock(new BlockPos(c[0], y + 1, c[1]), Blocks.CAMPFIRE.defaultBlockState(), 18);
        }
        set(level, 6, y, z0 + 3, Blocks.BARREL);
        set(level, 5, y, z0 + 3, Blocks.BARREL);
        set(level, 6, y + 1, z0 + 3, Blocks.BARREL);
        for (int z = ArenaLayout.GATE_Z + 1; z < z1 - 1; z++) for (int x = -1; x <= 1; x++) set(level, x, y, z, Blocks.CARPET.pick(DyeColor.RED));
        for (int z = ArenaLayout.GATE_Z + 5; z < z0; z += 4) {
            level.setBlock(new BlockPos(-2, y + 3, z), Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.EAST), 18);
            level.setBlock(new BlockPos(2, y + 3, z), Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.WEST), 18);
        }
    }

    private static void buildClassNiche(ServerLevel level, PlayerClass cls, int z) {
        int f = ArenaLayout.FLOOR_Y, w = ArenaLayout.LOBBY_X + 1;
        for (int x = -w - 3; x <= -w; x++) {
            for (int dz = -2; dz <= 2; dz++) {
                boolean shell = x == -w - 3 || Math.abs(dz) == 2;
                set(level, x, f, z + dz, Blocks.DEEPSLATE_TILES);
                set(level, x, f + 5, z + dz, Blocks.POLISHED_BLACKSTONE);
                for (int y = f + 1; y <= f + 4; y++) set(level, x, y, z + dz, shell ? Blocks.POLISHED_BLACKSTONE_BRICKS : Blocks.AIR);
            }
        }
        level.setBlock(new BlockPos(-w - 1, f + 4, z), Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true), 18);
        for (int dz = -1; dz <= 1; dz++) set(level, -w - 3, f + 2, z + dz, Blocks.SHROOMLIGHT);
        BlockPos at = cls.button();
        button(level, at, Direction.EAST);
        sign(level, at.above(), Direction.EAST, cls.title(), cls.line2(), cls.line3());
        ArmorStand stand = EntityTypes.ARMOR_STAND.create(level, EntitySpawnReason.COMMAND);
        stand.snapTo(-w - 1 + 0.5, f + 1, z + 0.5, -90F, 0F);
        stand.setNoGravity(true);
        stand.setShowArms(true);
        stand.setNoBasePlate(true);
        stand.addTag(DISPLAY_TAG);
        stand.addTag(DISPLAY_VERSION_TAG);
        cls.dress(stand, level.registryAccess());
        level.addFreshEntity(stand);
    }

    // --- Die Karte: Mittelteil, Häuser mit Dächern, Brücken, Ruinengänge, Grube, Säulenhalle

    /** Setzt nur auf dem Sand, die Außenmauer bleibt unberührt. */
    private static void d(ServerLevel level, int x, int y, int z, BlockState state) {
        if (ArenaLayout.ring(x, z) == 0) level.setBlock(new BlockPos(x, y, z), state, 18);
    }

    private static void d(ServerLevel level, int x, int y, int z, Block block) {
        d(level, x, y, z, block.defaultBlockState());
    }

    private static void box(ServerLevel level, int x1, int y1, int z1, int x2, int y2, int z2, Block block) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) for (int y = y1; y <= y2; y++) for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) d(level, x, y, z, block);
    }

    /** Treppe bis auf die Dächer: Die oberste Stufe liegt bündig mit dem Dach (Oberkante 69), springen muss man nicht. high zeigt zur hohen Seite, (x,z) ist die oberste Stufe, width geht quer dazu. */
    private static void stairs(ServerLevel level, int x, int z, Direction high, int steps, int topY) {
        Direction side = high.getClockWise();
        for (int s = 0; s < steps; s++) {
            for (int w = -1; w <= 1; w++) {
                int px = x + high.getOpposite().getStepX() * s + side.getStepX() * w, pz = z + high.getOpposite().getStepZ() * s + side.getStepZ() * w;
                int y = topY - s;
                for (int below = ArenaLayout.STAND_Y; below < y; below++) d(level, px, below, pz, Blocks.STONE_BRICKS);
                d(level, px, y, pz, Blocks.SANDSTONE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, high));
            }
        }
    }

    private static void lantern(ServerLevel level, int x, int y, int z, boolean hanging) {
        d(level, x, y, z, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, hanging));
    }

    private static void buildDecor(ServerLevel level) {
        buildSpina(level);
        house(level, -34, -20, 1, 1);
        house(level, 22, 10, -1, -1);
        house(level, -34, 10, 1, -1);
        house(level, 22, -20, -1, 1);
        buildZoneWalls(level);
        bridges(level);
        buildExfilGate(level);
        ruins(level);
        buildPit(level);
        buildColonnade(level);
        buildShops(level);
        buildDoors(level);
        buildBoard(level);
        buildStations(level);
    }

    /** Die Kaufstellen: fester Block dahinter, Knopf davor, darüber das Schild mit dem Preis. */
    private static void buildShops(ServerLevel level) {
        for (Shop.Offer offer : Shop.OFFERS) {
            BlockPos back = offer.backing();
            level.setBlock(back.above(), Blocks.CHISELED_STONE_BRICKS.defaultBlockState(), 18);
            button(level, offer.button(), offer.facing());
            sign(level, offer.sign(), offer.facing(), de.ecri.colosseum.Lang.t(offer.line1()), de.ecri.colosseum.Lang.t(offer.line2()), de.ecri.colosseum.Lang.t("sign.points", offer.price()));
        }
    }

    /** Der Mittelteil: 4 hoch, mit Durchgang in der Mitte, Rampen an den Enden und Seiten, oben das Exfil-Tor. */
    private static void buildSpina(ServerLevel level) {
        int top = 68;
        for (int x = -22; x <= 22; x++) for (int z = -4; z <= 4; z++) {
            boolean tunnel = Math.abs(x) <= 3;
            for (int y = ArenaLayout.STAND_Y; y < top; y++) d(level, x, y, z, tunnel ? Blocks.AIR : Math.floorMod(x, 6) == 0 ? Blocks.CHISELED_STONE_BRICKS : Blocks.STONE_BRICKS);
            d(level, x, top, z, Blocks.SMOOTH_SANDSTONE);
        }
        for (int z : new int[] {-4, 4}) for (int x = -22; x <= 22; x++) {
            boolean ramp = Math.abs(Math.abs(x) - 12) <= 2 || Math.abs(x) >= 19;
            if (!ramp && Math.floorMod(x, 7) < 5) d(level, x, top + 1, z, Blocks.STONE_BRICK_WALL);
        }
        // Nach oben geht es nur von Norden: Das Dach gehört zum zweiten Bereich.
        for (int x : new int[] {-12, 12}) stairs(level, x, -5, Direction.SOUTH, 4, 68);
        lantern(level, -7, top + 1, 2, false);
        lantern(level, 7, top + 1, -2, false);
        lantern(level, 0, 67, 0, true);
    }

    /** Das Exfil-Tor im Vorhof. Der Knopf steht auf der Platte, sie markiert die Stelle auch ohne Knopf. */
    private static void buildExfilGate(ServerLevel level) {
        BlockPos o = ArenaLayout.EXTRACTION_PORTAL;
        for (int i = -1; i <= 2; i++) for (int j = -1; j <= 3; j++) {
            if (i == -1 || i == 2 || j == -1 || j == 3) d(level, o.getX() + i, o.getY() + j, o.getZ(), Blocks.SANDSTONE);
        }
        sign(level, new BlockPos(o.getX() + 3, o.getY() + 1, o.getZ()), Direction.EAST, de.ecri.colosseum.Lang.t("sign.exfil1"), de.ecri.colosseum.Lang.t("sign.exfil2"), de.ecri.colosseum.Lang.t("sign.exfil3"));
        d(level, ArenaLayout.EXFIL_BUTTON.getX(), ArenaLayout.EXFIL_BUTTON.getY() - 1, ArenaLayout.EXFIL_BUTTON.getZ(), Blocks.SEA_LANTERN);
    }

    /** Hohe Trennwände zwischen den Bereichen, auch über den Mittelteil hinweg. Geöffnet wird nur über die Türen. */
    private static void buildZoneWalls(ServerLevel level) {
        for (int x : new int[] {-ArenaLayout.ZONE_WALL_X, ArenaLayout.ZONE_WALL_X}) {
            for (int z = -ArenaLayout.B; z <= ArenaLayout.B; z++) {
                int from = Math.abs(z) <= 4 ? 69 : ArenaLayout.STAND_Y;
                for (int y = from; y <= 72; y++) d(level, x, y, z, y == 69 ? Blocks.POLISHED_GRANITE : y == 72 ? Blocks.SANDSTONE : Blocks.STONE_BRICKS);
            }
        }
    }

    /** Perks, Stromschalter, Amboss und der Knopf zum Punkteteilen. */
    private static void buildStations(ServerLevel level) {
        for (Perk perk : Perk.values()) {
            BlockPos back = perk.button.relative(perk.facing.getOpposite());
            level.setBlock(back.above(), Blocks.CHISELED_STONE_BRICKS.defaultBlockState(), 18);
            button(level, perk.button, perk.facing);
            sign(level, perk.button.above(), perk.facing, de.ecri.colosseum.Lang.t("sign.perk"), perk.title, perk.line2, de.ecri.colosseum.Lang.t("sign.points", perk.price));
        }
        BlockPos power = ArenaLayout.POWER_LEVER;
        level.setBlock(power.south().above(), Blocks.CHISELED_STONE_BRICKS.defaultBlockState(), 18);
        lever(level, power, Direction.NORTH);
        sign(level, power.above(), Direction.NORTH, de.ecri.colosseum.Lang.t("sign.power1"), de.ecri.colosseum.Lang.t("sign.power2"), de.ecri.colosseum.Lang.t("sign.power3"));
        BlockPos anvil = ArenaLayout.ANVIL;
        level.setBlock(anvil, Blocks.ANVIL.defaultBlockState(), 18);
        level.setBlock(anvil.above().north(), Blocks.CHISELED_STONE_BRICKS.defaultBlockState(), 18);
        sign(level, anvil.above(), Direction.SOUTH, de.ecri.colosseum.Lang.t("sign.anvil1"), de.ecri.colosseum.Lang.t("sign.anvil2"), de.ecri.colosseum.Lang.t("sign.anvil3"), de.ecri.colosseum.Lang.t("sign.anvil4"));
        BlockPos share = ArenaLayout.SHARE_BUTTON;
        level.setBlock(share.north().above(), Blocks.CHISELED_STONE_BRICKS.defaultBlockState(), 18);
        button(level, share, Direction.SOUTH);
        sign(level, share.above(), Direction.SOUTH, de.ecri.colosseum.Lang.t("sign.share1"), de.ecri.colosseum.Lang.t("sign.share2"), de.ecri.colosseum.Lang.t("sign.share3"));
    }

    /** Tür: Gitter im Durchgang, daneben auf jeder Seite ein Knopf mit Schild. */
    private static void buildDoors(ServerLevel level) {
        for (ArenaDoors.Door door : ArenaDoors.ALL) {
            ArenaDoors.set(level, door, false);
            for (ArenaDoors.DoorButton b : door.buttons()) {
                button(level, b.pos(), b.facing());
                sign(level, b.pos().above(), b.facing(), de.ecri.colosseum.Lang.t(door.shortcut() ? "sign.shortcut" : "sign.door"), door.shortcut() ? net.minecraft.network.chat.Component.empty() : door.label(), de.ecri.colosseum.Lang.t("sign.points", door.price()));
            }
        }
    }

    /** Die Bestenliste an der Hallenwand neben dem Tunnel, links Runden, rechts Punkte. */
    private static final int[][] BOARD_COLUMNS = {{-5, -4, -3}, {3, 4, 5}};

    private static void buildBoard(ServerLevel level) {
        int z = ArenaLayout.LOBBY_Z0;
        sign(level, new BlockPos(-4, 69, z), Direction.SOUTH, de.ecri.colosseum.Lang.t("sign.board"), de.ecri.colosseum.Lang.t("sign.board_rounds"));
        sign(level, new BlockPos(4, 69, z), Direction.SOUTH, de.ecri.colosseum.Lang.t("sign.board"), de.ecri.colosseum.Lang.t("sign.board_points"));
        showBoard(level, List.of(), List.of());
    }

    /** Zwei Einträge pro Schild, sechs Schilder pro Seite. */
    public static void showBoard(ServerLevel level, List<Leaderboard.Entry> byRounds, List<Leaderboard.Entry> byPoints) {
        for (int side = 0; side < 2; side++) {
            List<Leaderboard.Entry> list = side == 0 ? byRounds : byPoints;
            int index = 0;
            for (int y : new int[] {68, 67}) for (int x : BOARD_COLUMNS[side]) {
                String[] lines = new String[4];
                for (int line = 0; line < 4; line += 2) {
                    if (index < list.size()) {
                        Leaderboard.Entry e = list.get(index);
                        String name = e.name().length() > 12 ? e.name().substring(0, 12) : e.name();
                        lines[line] = (index + 1) + ". " + name;
                        lines[line + 1] = "R" + e.rounds() + "  " + e.points() + " P";
                    } else {
                        lines[line] = (index + 1) + ". -";
                        lines[line + 1] = "";
                    }
                    index++;
                }
                sign(level, new BlockPos(x, y, ArenaLayout.LOBBY_Z0), Direction.SOUTH, lines);
            }
        }
    }

    /** Haus 13 x 11 mit Türen, Fensterschlitzen und begehbarem Dach. dx und dz geben an, zur Mitte hin zeigende Seiten. */
    private static void house(ServerLevel level, int x0, int z0, int dx, int dz) {
        int x1 = x0 + 12, z1 = z0 + 10, y = ArenaLayout.STAND_Y;
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
            boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
            if (edge) for (int h = 0; h < 3; h++) d(level, x, y + h, z, h == 1 && Math.floorMod(x + z, 4) == 0 ? Blocks.AIR : Math.floorMod(x * 3 + z + h, 5) == 0 ? Blocks.CRACKED_STONE_BRICKS : Blocks.STONE_BRICKS);
            d(level, x, 68, z, Math.floorMod(x + z, 2) == 0 ? Blocks.SMOOTH_SANDSTONE : Blocks.POLISHED_GRANITE);
        }
        // Zwei Türen, jeweils 3 breit: eine auf der Längs-, eine auf der Querseite
        int cx = x0 + 6, cz = z0 + 5;
        int doorZ = dz > 0 ? z1 : z0, doorX = dx > 0 ? x1 : x0;
        box(level, cx - 1, y, doorZ, cx + 1, y + 2, doorZ, Blocks.AIR);
        box(level, doorX, y, cz - 1, doorX, y + 2, cz + 1, Blocks.AIR);
        // Dritte Tür nach außen, damit man nicht in der Falle sitzt
        int backZ = dz > 0 ? z0 : z1;
        box(level, cx + 3, y, backZ, cx + 5, y + 2, backZ, Blocks.AIR);
        box(level, cx - 4, y, cz - 3, cx - 4, y + 2, cz - 3, Blocks.QUARTZ_PILLAR);
        box(level, cx + 4, y, cz + 3, cx + 4, y + 2, cz + 3, Blocks.QUARTZ_PILLAR);
        lantern(level, cx, 67, cz, true);
        for (int x = x0; x <= x1; x++) for (int z : new int[] {z0, z1}) if (Math.floorMod(x, 5) < 2) d(level, x, 69, z, Blocks.STONE_BRICK_WALL);
        lantern(level, x0, 69, z0 + 2, false);
        lantern(level, x1, 69, z0 + 2, false);
    }

    /** Zwei Häuser hängen über Brücken am Mittelteil, die anderen beiden haben Außentreppen. */
    private static void bridges(ServerLevel level) {
        box(level, 20, 68, 5, 22, 68, 9, Blocks.OAK_PLANKS);
        box(level, -22, 68, -9, -20, 68, -5, Blocks.OAK_PLANKS);
        stairs(level, 35, 15, Direction.WEST, 4, 68);
        stairs(level, -35, -15, Direction.EAST, 4, 68);
        stairs(level, -35, 15, Direction.EAST, 4, 68);
        stairs(level, 35, -15, Direction.WEST, 4, 68);
    }

    private static void ruinWall(ServerLevel level, int x1, int z1, int x2, int z2) {
        int y = ArenaLayout.STAND_Y;
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
            int height = 3 + Math.floorMod(x * 5 + z * 7, 2);
            for (int h = 0; h < height; h++) d(level, x, y + h, z, h == height - 1 && height == 4 ? Blocks.CRACKED_STONE_BRICKS : Blocks.STONE_BRICKS);
        }
    }

    /** Winkel und Engstellen zwischen den Häusern, damit man nie über die ganze Fläche sieht. */
    private static void ruins(ServerLevel level) {
        ruinWall(level, -14, -18, -3, -18);
        ruinWall(level, 3, -18, 14, -18);
        ruinWall(level, -14, -18, -14, -10);
        ruinWall(level, 14, -18, 14, -10);
        ruinWall(level, -14, 18, -5, 18);
        ruinWall(level, 5, 18, 14, 18);
        ruinWall(level, -14, 10, -14, 18);
        ruinWall(level, 14, 10, 14, 18);
        ruinWall(level, -6, -26, -6, -22);
        ruinWall(level, 6, -26, 6, -22);
        ruinWall(level, -22, -30, -16, -30);
        ruinWall(level, 16, 26, 22, 26);
        ruinWall(level, -22, 26, -16, 26);
        for (int[] c : new int[][] {{-10, 24}, {10, 24}, {18, -26}, {-3, -9}, {4, 9}, {-18, 7}, {18, -7}}) {
            box(level, c[0], ArenaLayout.STAND_Y, c[1], c[0] + 1, ArenaLayout.STAND_Y, c[1] + 1, Blocks.BARREL);
            d(level, c[0], ArenaLayout.STAND_Y + 1, c[1], Blocks.BARREL);
        }
    }

    /** Abgesenkte Grube im Osten, je eine Treppe auf der West- und Ostseite. */
    private static void buildPit(ServerLevel level) {
        for (int x = 30; x <= 40; x++) for (int z = -7; z <= 7; z++) {
            d(level, x, 64, z, Blocks.AIR);
            d(level, x, 63, z, Blocks.AIR);
            d(level, x, 62, z, Blocks.SAND);
        }
        for (int s = 0; s < 2; s++) for (int w = -1; w <= 1; w++) {
            d(level, 30 + s, 64 - s, w, Blocks.SANDSTONE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.WEST));
            d(level, 40 - s, 64 - s, w, Blocks.SANDSTONE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST));
            if (s == 1) {
                d(level, 30, 63, w, Blocks.SANDSTONE);
                d(level, 40, 63, w, Blocks.SANDSTONE);
            }
        }
        lantern(level, 31, 63, -6, false);
        lantern(level, 31, 63, 6, false);
    }

    /** Säulenhalle im Westen um ein niedriges Podest. */
    private static void buildColonnade(ServerLevel level) {
        int y = ArenaLayout.STAND_Y;
        for (int x : new int[] {-43, -38, -32, -27}) for (int z : new int[] {-8, 8}) {
            int height = x == -38 ? 2 : 6;
            box(level, x, y, z, x, y + height - 1, z, Blocks.QUARTZ_PILLAR);
            if (height == 6) lantern(level, x, y + height, z, false);
        }
        box(level, -40, y, -3, -30, y, 3, Blocks.POLISHED_GRANITE);
        for (int s : new int[] {-41, -29}) box(level, s, y, -3, s, y, 3, Blocks.SMOOTH_SANDSTONE_SLAB);
        for (int z : new int[] {-4, 4}) box(level, -40, y, z, -30, y, z, Blocks.SMOOTH_SANDSTONE_SLAB);
    }

    // --- Tore

    public static void setMobGates(ServerLevel level, boolean open) {
        for (ArenaLayout.MobGate gate : ArenaLayout.MOB_GATES) {
            if (open) for (BlockPos pos : gate.bars()) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
            else ArenaDoors.placeBars(level, gate.bars());
        }
    }

    /** Nur die Tore der offenen Bereiche gehen auf. */
    public static void openMobGates(ServerLevel level, java.util.Set<Integer> zones) {
        for (ArenaLayout.MobGate gate : ArenaLayout.MOB_GATES) {
            if (!zones.contains(gate.zone())) continue;
            for (BlockPos pos : gate.bars()) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
        }
    }

    private static void closeAllGates(ServerLevel level) {
        setMobGates(level, false);
    }

    public static List<BlockPos> entryGateCells() {
        List<BlockPos> cells = new ArrayList<>();
        for (int x = -2; x <= 2; x++) for (int y = 0; y < ArenaLayout.GATE_HEIGHT; y++) cells.add(new BlockPos(x, ArenaLayout.STAND_Y + y, ArenaLayout.GATE_Z));
        return cells;
    }

    public static void openEntryGate(ServerLevel level) {
        for (BlockPos pos : entryGateCells()) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
    }

    public static void closeEntryGate(ServerLevel level) {
        ArenaDoors.placeBars(level, entryGateCells());
    }

    public static void setLever(ServerLevel level, BlockPos pos, boolean on) {
        BlockState state = level.getBlockState(pos);
        if (state.is(Blocks.LEVER)) level.setBlock(pos, state.setValue(BlockStateProperties.POWERED, on), 2);
    }

    // --- Exfil: Der Rahmen steht fest, Knopf und Portal kommen und gehen

    public static void placeExfilButton(ServerLevel level) {
        level.setBlock(ArenaLayout.EXFIL_BUTTON, Blocks.POLISHED_BLACKSTONE_BUTTON.defaultBlockState().setValue(ButtonBlock.FACE, AttachFace.FLOOR).setValue(ButtonBlock.FACING, Direction.WEST), 18);
    }

    public static void removeExfilButton(ServerLevel level) {
        level.setBlock(ArenaLayout.EXFIL_BUTTON, Blocks.AIR.defaultBlockState(), 18);
    }

    public static void placeExtraction(ServerLevel level) {
        new PortalFrame(ArenaLayout.EXTRACTION_PORTAL, Direction.Axis.X, 2, 3).fill(level);
    }

    public static void removeExtraction(ServerLevel level) {
        BlockPos o = ArenaLayout.EXTRACTION_PORTAL;
        for (int i = 0; i < 2; i++) for (int j = 0; j < 3; j++) set(level, o.getX() + i, o.getY() + j, o.getZ(), Blocks.AIR);
    }

    private ArenaBuilder() {}
}
