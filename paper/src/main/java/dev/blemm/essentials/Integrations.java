package dev.blemm.essentials;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** DiscordSRV, Geyser/Floodgate and PlaceholderAPI glue. Everything here is optional. */
public class Integrations {
    private static final Pattern TEXTURE_ID = Pattern.compile("\"texture_id\"\\s*:\\s*\"([0-9a-fA-F]+)\"");

    private final BlemmEssentials plugin;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    /** Bedrock player UUID -> Mojang texture id of their (Geyser-uploaded) skin. */
    private final Map<UUID, String> bedrockTextures = new ConcurrentHashMap<>();

    public Integrations(BlemmEssentials plugin) { this.plugin = plugin; }

    public boolean has(String name) { return Bukkit.getPluginManager().getPlugin(name) != null; }

    public boolean hasGeyser() { return has("Geyser-Spigot") || has("floodgate"); }

    /** Floodgate UUIDs are (0, xuid). Returns the XUID for Bedrock players, else -1. */
    public static long xuidOf(UUID id) {
        return id.getMostSignificantBits() == 0L ? id.getLeastSignificantBits() : -1L;
    }

    public boolean isBedrock(Player p) { return xuidOf(p.getUniqueId()) >= 0; }

    /** Asks the GeyserMC skin API for the player's Bedrock skin and caches the texture id. */
    public void loadBedrockSkin(Player p) {
        long xuid = xuidOf(p.getUniqueId());
        if (xuid < 0 || bedrockTextures.containsKey(p.getUniqueId())) return;
        UUID id = p.getUniqueId();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                HttpRequest req = HttpRequest.newBuilder(URI.create("https://api.geysermc.org/v2/skin/" + xuid))
                        .timeout(Duration.ofSeconds(8)).GET().build();
                HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
                if (res.statusCode() == 200) {
                    Matcher m = TEXTURE_ID.matcher(res.body());
                    if (m.find()) bedrockTextures.put(id, m.group(1));
                }
            } catch (Exception e) {
                plugin.getLogger().fine("Geyser skin lookup failed for " + xuid + ": " + e.getMessage());
            }
        });
    }

    /** URL for DiscordSRV's AvatarUrl (via %blemm_discord_avatar%). Bedrock players use their Geyser skin. */
    public String avatarUrl(Player p) {
        var c = plugin.getConfig();
        int size = c.getInt("discord.avatar-size", 128);
        String tpl;
        String texture = "";
        if (isBedrock(p)) {
            texture = bedrockTextures.get(p.getUniqueId());
            if (texture == null) { loadBedrockSkin(p); tpl = c.getString("discord.fallback-avatar-url"); texture = ""; }
            else tpl = c.getString("discord.bedrock-avatar-url");
        } else tpl = c.getString("discord.java-avatar-url");
        if (tpl == null) tpl = "https://crafthead.net/helm/MHF_Steve/{size}";
        return tpl.replace("{uuid-nodashes}", p.getUniqueId().toString().replace("-", ""))
                .replace("{uuid}", p.getUniqueId().toString())
                .replace("{username}", p.getName())
                .replace("{texture}", texture)
                .replace("{size}", String.valueOf(size));
    }

    /** Sends text to DiscordSRV's main channel (reflection, so DiscordSRV is not a compile dependency). */
    public void announceToDiscord(String text) {
        if (!has("DiscordSRV")) return;
        try {
            Class<?> srv = Class.forName("github.scarsz.discordsrv.DiscordSRV");
            Object inst = srv.getMethod("getPlugin").invoke(null);
            Object channel = srv.getMethod("getMainTextChannel").invoke(inst);
            if (channel == null) return;
            Class<?> util = Class.forName("github.scarsz.discordsrv.util.DiscordUtil");
            for (Method m : util.getMethods()) {
                if (m.getName().equals("sendMessage") && m.getParameterCount() == 2
                        && m.getParameterTypes()[1] == String.class
                        && m.getParameterTypes()[0].isInstance(channel)) {
                    m.invoke(null, channel, text);
                    return;
                }
            }
        } catch (Throwable t) {
            plugin.getLogger().fine("DiscordSRV announce failed: " + t);
        }
    }

    /** Registers %blemm_...% placeholders if PlaceholderAPI is present. */
    public void registerPlaceholders() {
        if (!has("PlaceholderAPI")) return;
        try { new BlemmExpansion(plugin).register(); }
        catch (Throwable t) { plugin.getLogger().warning("Could not register PlaceholderAPI expansion: " + t); }
    }
}
