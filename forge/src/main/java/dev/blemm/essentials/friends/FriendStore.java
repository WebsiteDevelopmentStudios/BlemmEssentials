package dev.blemm.essentials.friends;

import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

public final class FriendStore {
    private static final Properties DATA = new Properties();
    private static Path dataFile;

    private FriendStore() {}

    public static synchronized void load(MinecraftServer server) {
        dataFile = server.getServerDirectory().resolve("blemmessentials").resolve("friends.properties");
        DATA.clear();
        if (!Files.exists(dataFile)) return;
        try (InputStream input = Files.newInputStream(dataFile)) {
            DATA.load(input);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not load BlemmEssentials friends", exception);
        }
    }

    public static synchronized void save(MinecraftServer server) {
        Path destination = dataFile != null ? dataFile : server.getServerDirectory().resolve("blemmessentials").resolve("friends.properties");
        try {
            Files.createDirectories(destination.getParent());
            try (OutputStream output = Files.newOutputStream(destination)) {
                DATA.store(output, "BlemmEssentials friends and home-visit preferences");
            }
            dataFile = destination;
        } catch (IOException exception) {
            throw new IllegalStateException("Could not save BlemmEssentials friends", exception);
        }
    }

    public static synchronized boolean areFriends(UUID first, UUID second) {
        return DATA.getProperty(friendKey(first, second), "false").equals("true");
    }

    public static synchronized void add(UUID first, UUID second, MinecraftServer server) {
        DATA.setProperty(friendKey(first, second), "true");
        DATA.setProperty(friendKey(second, first), "true");
        save(server);
    }

    public static synchronized void remove(UUID first, UUID second, MinecraftServer server) {
        DATA.remove(friendKey(first, second));
        DATA.remove(friendKey(second, first));
        save(server);
    }

    public static synchronized List<UUID> friends(UUID player) {
        String prefix = "friend." + player + ".";
        List<UUID> result = new ArrayList<>();
        for (String key : DATA.stringPropertyNames()) {
            if (!key.startsWith(prefix) || !DATA.getProperty(key).equals("true")) continue;
            try { result.add(UUID.fromString(key.substring(prefix.length()))); }
            catch (IllegalArgumentException ignored) { }
        }
        return result;
    }

    public static synchronized boolean homeVisitsEnabled(UUID player) {
        return DATA.getProperty("homevisits." + player, "false").equals("true");
    }

    public static synchronized boolean setHomeVisits(UUID player, boolean enabled, MinecraftServer server) {
        DATA.setProperty("homevisits." + player, Boolean.toString(enabled));
        save(server);
        return enabled;
    }

    private static String friendKey(UUID player, UUID friend) { return "friend." + player + "." + friend; }
}
