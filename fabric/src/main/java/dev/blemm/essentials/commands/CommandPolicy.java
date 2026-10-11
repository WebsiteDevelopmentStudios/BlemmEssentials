package dev.blemm.essentials.commands;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

/** Optional per-dimension command disabling for mod-loader servers. */
public final class CommandPolicy {
    private static final Properties CONFIG = new Properties();
    private static Path file;
    private CommandPolicy() {}

    public static synchronized void load(MinecraftServer server) {
        file = server.getServerDirectory().resolve("blemmessentials").resolve("disabled-commands.properties");
        CONFIG.clear();
        if (Files.exists(file)) {
            try (InputStream in = Files.newInputStream(file)) { CONFIG.load(in); }
            catch (IOException e) { throw new IllegalStateException("Could not load BlemmEssentials command policy", e); }
        } else {
            CONFIG.setProperty("minecraft:the_end", "");
            save();
        }
    }

    private static synchronized void save() {
        try {
            Files.createDirectories(file.getParent());
            try (OutputStream out = Files.newOutputStream(file)) {
                CONFIG.store(out, "Disable command names per dimension ID, e.g. minecraft:the_end=rtp,home,sethome. Use *=... for every dimension.");
            }
        } catch (IOException e) { throw new IllegalStateException("Could not save BlemmEssentials command policy", e); }
    }

    public static synchronized boolean disabled(CommandSourceStack source, String command) {
        if (source.getEntity() == null) return false;
        String dimension = source.getLevel().dimension().location().toString().toLowerCase(Locale.ROOT);
        String target = command.toLowerCase(Locale.ROOT);
        return contains(CONFIG.getProperty("*", ""), target)
            || contains(CONFIG.getProperty(dimension, ""), target)
            || contains(CONFIG.getProperty("world." + dimension, ""), target);
    }

    private static boolean contains(String value, String command) {
        if (value == null || value.isBlank()) return false;
        Set<String> commands = Arrays.stream(value.split(","))
            .map(s -> s.trim().toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        return commands.contains("*") || commands.contains(command);
    }
}
