package dev.blemm.essentials.integrations;

import net.minecraft.server.level.ServerPlayer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Optional Floodgate/Geyser skin lookup. Floodgate UUIDs encode the XUID in the low bits. */
public final class BedrockAvatarLookup {
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();
    private static final Map<UUID, String> TEXTURES = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> REQUESTED = new ConcurrentHashMap<>();
    private BedrockAvatarLookup() {}

    public static boolean isBedrock(UUID id) {
        return id.getMostSignificantBits() == 0L && id.getLeastSignificantBits() >= 0L;
    }

    public static void load(ServerPlayer player) {
        UUID id = player.getUUID();
        if (!isBedrock(id) || REQUESTED.putIfAbsent(id, true) != null) return;
        long xuid = id.getLeastSignificantBits();
        Thread.startVirtualThread(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.geysermc.org/v2/skin/" + xuid))
                    .timeout(Duration.ofSeconds(7)).GET().build();
                HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) return;
                java.util.regex.Matcher matcher = java.util.regex.Pattern
                    .compile("\"texture_id\"\\s*:\\s*\"([0-9a-fA-F]+)\"")
                    .matcher(response.body());
                if (matcher.find()) TEXTURES.put(id, matcher.group(1));
            } catch (Exception ignored) {
                // External skin service is optional; use the fallback avatar.
            }
        });
    }

    public static String avatarUrl(ServerPlayer player) {
        String texture = TEXTURES.get(player.getUUID());
        if (texture == null) {
            load(player);
            return "https://crafthead.net/helm/MHF_Steve/128";
        }
        return "https://crafthead.net/helm/" + texture + "/128";
    }
}
