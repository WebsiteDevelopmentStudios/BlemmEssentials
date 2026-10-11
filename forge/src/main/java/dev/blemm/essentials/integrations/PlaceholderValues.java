package dev.blemm.essentials.integrations;

import dev.blemm.essentials.ranks.RankStore;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;

/**
 * Small dependency-free placeholder API for other mods and server-side integrations.
 * Supported tokens use the %blemm_name% format.
 */
public final class PlaceholderValues {
    private PlaceholderValues() {}

    public static String resolve(ServerPlayer player, String name) {
        if (player == null || name == null) return "";
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "balance" -> Long.toString(dev.blemm.essentials.forge.BlemmEssentialsForge.balanceFor(player.getUUID()));
            case "rank" -> RankStore.get(player.getUUID());
            case "rank_prefix" -> prefix(RankStore.get(player.getUUID()));
            case "world" -> player.level().dimension().identifier().toString();
            case "is_bedrock" -> Boolean.toString(BedrockAvatarLookup.isBedrock(player.getUUID()));
            case "discord_avatar" -> BedrockAvatarLookup.avatarUrl(player);
            case "player" -> player.getGameProfile().name();
            case "uuid" -> player.getUUID().toString();
            default -> "";
        };
    }

    public static String apply(ServerPlayer player, String input) {
        if (input == null || input.isEmpty() || player == null) return input == null ? "" : input;
        String result = input;
        for (String name : new String[] {"balance", "rank", "rank_prefix", "world", "is_bedrock", "discord_avatar", "player", "uuid"}) {
            result = result.replace("%blemm_" + name + "%", resolve(player, name));
        }
        return result;
    }

    private static String prefix(String rank) {
        return switch (rank) {
            case "vip" -> "[VIP] ";
            case "mvp" -> "[MVP] ";
            case "admin" -> "[ADMIN] ";
            default -> "[Member] ";
        };
    }
}
