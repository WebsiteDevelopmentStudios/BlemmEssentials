package dev.blemm.essentials.ranks;

import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

public final class RankStore {
    private static final Properties DATA = new Properties();
    private static Path dataFile;
    private static final Set<String> VALID_RANKS = Set.of("member", "vip", "mvp", "admin");

    private RankStore() {}

    public static synchronized void load(MinecraftServer server) {
        dataFile = server.getServerDirectory().resolve("blemmessentials").resolve("ranks.properties");
        DATA.clear();
        if (!Files.exists(dataFile)) return;
        try (InputStream input = Files.newInputStream(dataFile)) {
            DATA.load(input);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not load BlemmEssentials ranks", exception);
        }
    }

    public static synchronized void save(MinecraftServer server) {
        Path destination = dataFile != null ? dataFile : server.getServerDirectory().resolve("blemmessentials").resolve("ranks.properties");
        try {
            Files.createDirectories(destination.getParent());
            try (OutputStream output = Files.newOutputStream(destination)) {
                DATA.store(output, "BlemmEssentials player ranks");
            }
            dataFile = destination;
        } catch (IOException exception) {
            throw new IllegalStateException("Could not save BlemmEssentials ranks", exception);
        }
    }

    public static synchronized String get(UUID player) {
        return DATA.getProperty("rank." + player, "member");
    }

    public static synchronized boolean set(UUID player, String rank, MinecraftServer server) {
        String normalized = rank.toLowerCase(Locale.ROOT);
        if (!VALID_RANKS.contains(normalized)) return false;
        if (normalized.equals("member")) DATA.remove("rank." + player);
        else DATA.setProperty("rank." + player, normalized);
        save(server);
        return true;
    }

    public static Set<String> validRanks() { return VALID_RANKS; }
}
