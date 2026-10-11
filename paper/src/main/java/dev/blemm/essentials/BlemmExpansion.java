package dev.blemm.essentials;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class BlemmExpansion extends PlaceholderExpansion {
    private final BlemmEssentials plugin;
    public BlemmExpansion(BlemmEssentials plugin) { this.plugin = plugin; }

    @Override public @NotNull String getIdentifier() { return "blemm"; }
    @Override public @NotNull String getAuthor() { return "Blemm"; }
    @Override public @NotNull String getVersion() { return plugin.getPluginMeta().getVersion(); }
    @Override public boolean persist() { return true; }

    @Override
    public String onRequest(OfflinePlayer off, @NotNull String params) {
        if (off == null) return "";
        switch (params.toLowerCase()) {
            case "discord_avatar":
                return off instanceof Player p ? plugin.integrations().avatarUrl(p)
                        : plugin.getConfig().getString("discord.fallback-avatar-url", "").replace("{size}", "128");
            case "balance": return plugin.money(plugin.balance(off.getUniqueId()));
            case "rank": return plugin.rankId(off.getUniqueId());
            case "rank_prefix": return plugin.rankPrefix(off.getUniqueId());
            default: return null;
        }
    }
}
