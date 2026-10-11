package dev.blemm.essentials.integrations;

import dev.blemm.essentials.fabric.BlemmEssentialsFabric;
import dev.blemm.essentials.ranks.RankStore;
import io.github.miniplaceholders.api.Expansion;
import io.github.miniplaceholders.api.MiniPlaceholders;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.minecraft.server.level.ServerPlayer;

public final class FabricPlaceholderIntegration {
    private FabricPlaceholderIntegration() {}
    public static void register() {
        Expansion.builder("blemm")
            .author("WebsiteDevelopmentStudios")
            .version("1.2.0")
            .audiencePlaceholder("balance", (audience, queue, context) -> Tag.selfClosingInserting(Component.text(audience instanceof ServerPlayer player ? BlemmEssentialsFabric.balanceFor(player.getUUID()) : 0)))
            .audiencePlaceholder("rank", (audience, queue, context) -> Tag.selfClosingInserting(Component.text(audience instanceof ServerPlayer player ? RankStore.get(player.getUUID()) : "member")))
            .audiencePlaceholder("rank_prefix", (audience, queue, context) -> Tag.selfClosingInserting(Component.text(audience instanceof ServerPlayer player ? prefix(RankStore.get(player.getUUID())) : "[Member] ")))
            .audiencePlaceholder("world", (audience, queue, context) -> Tag.selfClosingInserting(Component.text(audience instanceof ServerPlayer player ? player.level().dimension().identifier().toString() : "")))
            .audiencePlaceholder("is_bedrock", (audience, queue, context) -> Tag.selfClosingInserting(Component.text(audience instanceof ServerPlayer player && BedrockAvatarLookup.isBedrock(player.getUUID()) ? "true" : "false")))
            .audiencePlaceholder("discord_avatar", (audience, queue, context) -> Tag.selfClosingInserting(Component.text(audience instanceof ServerPlayer player ? BedrockAvatarLookup.avatarUrl(player) : "https://crafthead.net/helm/MHF_Steve/128")))
            .build().register();
    }
    private static String prefix(String rank) {
        return switch (rank) { case "vip" -> "[VIP] "; case "mvp" -> "[MVP] "; case "admin" -> "[ADMIN] "; default -> "[Member] "; };
    }
}
