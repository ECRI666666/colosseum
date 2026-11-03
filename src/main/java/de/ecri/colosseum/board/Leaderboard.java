package de.ecri.colosseum.board;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.logging.LogUtils;
import de.ecri.colosseum.dimension.ArenaBuilder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;

/**
 * Die Bestenliste in der Halle: links die meisten Runden, rechts die meisten Punkte.
 * Sie liegt als kleine Datei im Weltordner und übersteht damit jeden Neustart.
 */
public final class Leaderboard {
    public record Entry(String name, int rounds, int points) {}

    private static final Logger LOG = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int KEEP = 100;
    private static final List<Entry> ENTRIES = new ArrayList<>();

    private static Path file(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("colosseum_bestenliste.json");
    }

    public static void load(MinecraftServer server) {
        ENTRIES.clear();
        Path path = file(server);
        if (!Files.exists(path)) return;
        try {
            List<Entry> read = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), new TypeToken<List<Entry>>() {}.getType());
            if (read != null) ENTRIES.addAll(read);
        } catch (Exception e) {
            LOG.error("Could not read the leaderboard, keeping the file: {}", path, e);
        }
    }

    public static void add(MinecraftServer server, ServerLevel level, String name, int rounds, int points) {
        if (rounds <= 0 && points <= 0) return;
        ENTRIES.add(new Entry(name, rounds, points));
        ENTRIES.sort(Comparator.comparingInt(Entry::rounds).thenComparingInt(Entry::points).reversed());
        while (ENTRIES.size() > KEEP) ENTRIES.remove(ENTRIES.size() - 1);
        save(server);
        refresh(level);
    }

    private static void save(MinecraftServer server) {
        Path path = file(server);
        try {
            Path tmp = path.resolveSibling(path.getFileName() + ".neu");
            Files.writeString(tmp, GSON.toJson(ENTRIES), StandardCharsets.UTF_8);
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            LOG.error("Could not save the leaderboard: {}", path, e);
        }
    }

    public static List<Entry> byRounds() {
        return ENTRIES.stream().sorted(Comparator.comparingInt(Entry::rounds).thenComparingInt(Entry::points).reversed()).toList();
    }

    public static List<Entry> byPoints() {
        return ENTRIES.stream().sorted(Comparator.comparingInt(Entry::points).thenComparingInt(Entry::rounds).reversed()).toList();
    }

    public static void refresh(ServerLevel level) {
        ArenaBuilder.showBoard(level, byRounds(), byPoints());
    }

    private Leaderboard() {}
}
