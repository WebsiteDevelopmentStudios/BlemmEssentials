package dev.blemm.essentials.ranks;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

/** Applies rank prefixes through vanilla scoreboard teams, so they show in chat and tab without another mod. */
public final class RankPresentation {
    private RankPresentation() {}

    public static void apply(ServerPlayer player) {
        MinecraftServer server = player.serverLevel().getServer();
        if (server == null) return;
        Scoreboard scoreboard = server.getScoreboard();
        String rank = RankStore.get(player.getUUID());
        PlayerTeam current = scoreboard.getPlayersTeam(player.getScoreboardName());
        if (current != null && current.getName().startsWith("be_")) {
            scoreboard.removePlayerFromTeam(player.getScoreboardName(), current);
        }
        if (rank.equals("member")) return;
        String teamName = "be_" + rank;
        PlayerTeam team = scoreboard.getPlayerTeam(teamName);
        if (team == null) team = scoreboard.addPlayerTeam(teamName);
        ChatFormatting color = switch (rank) {
            case "vip" -> ChatFormatting.GREEN;
            case "mvp" -> ChatFormatting.LIGHT_PURPLE;
            case "admin" -> ChatFormatting.RED;
            default -> ChatFormatting.GRAY;
        };
        team.setPlayerPrefix(Component.literal("[" + rank.toUpperCase(java.util.Locale.ROOT) + "] ").withStyle(color));
        scoreboard.addPlayerToTeam(player.getScoreboardName(), team);
    }
}
