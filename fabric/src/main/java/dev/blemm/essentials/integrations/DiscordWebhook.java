package dev.blemm.essentials.integrations;

import net.minecraft.server.MinecraftServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Properties;

/** Optional Discord webhook replacement for Paper's DiscordSRV auction announcements. */
public final class DiscordWebhook {
    private static final Properties CONFIG = new Properties();
    private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private static Path file;
    private DiscordWebhook() {}

    public static synchronized void load(MinecraftServer server) {
        file = server.getServerDirectory().resolve("blemmessentials").resolve("discord.properties");
        CONFIG.clear();
        if (Files.exists(file)) {
            try (InputStream in = Files.newInputStream(file)) { CONFIG.load(in); }
            catch (IOException e) { throw new IllegalStateException("Could not load BlemmEssentials Discord settings", e); }
        } else {
            CONFIG.setProperty("webhook-url", "");
            save();
        }
    }

    private static synchronized void save() {
        try {
            Files.createDirectories(file.getParent());
            try (OutputStream out = Files.newOutputStream(file)) {
                CONFIG.store(out, "Optional Discord webhook for new auction announcements. Leave webhook-url empty to disable.");
            }
        } catch (IOException e) { throw new IllegalStateException("Could not save BlemmEssentials Discord settings", e); }
    }

    public static void announceAuction(String seller, String itemId, int amount, long price) {
        String url;
        synchronized (DiscordWebhook.class) { url = CONFIG.getProperty("webhook-url", "").trim(); }
        if (!url.startsWith("https://discord.com/api/webhooks/") && !url.startsWith("https://discordapp.com/api/webhooks/")) return;
        String description = "**Seller:** " + escape(seller) + "\n**Item:** " + amount + "x " + escape(itemId) + "\n**Price:** $" + price;
        String payload = "{\"embeds\":[{\"title\":\"New BlemmEssentials auction\",\"description\":\"" + description + "\",\"color\":5793266}]}";
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(payload)).build();
            CLIENT.sendAsync(request, HttpResponse.BodyHandlers.discarding()).exceptionally(error -> null);
        } catch (IllegalArgumentException ignored) { }
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\n", "\\n").replace("\r", "\\r");
    }
}
