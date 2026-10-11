package dev.blemm.essentials.fabric;

import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import dev.blemm.essentials.locations.LocationCommands;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class BlemmEssentialsFabric implements ModInitializer {
    private static final EconomyStore ECONOMY = new EconomyStore();

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> { ECONOMY.load(server); LocationCommands.load(server); });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> { ECONOMY.save(server); LocationCommands.save(server); });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(literal("balance")
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    long balance = ECONOMY.balance(player.getUUID());
                    context.getSource().sendSuccess(() -> Component.literal("Balance: $" + balance), false);
                    return 1;
                }));

            dispatcher.register(literal("money")
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    context.getSource().sendSuccess(() -> Component.literal("Balance: $" + ECONOMY.balance(player.getUUID())), false);
                    return 1;
                })
                .then(argument("player", EntityArgument.player())
                    .requires(source -> source.getEntity() == null || (source.getEntity() instanceof ServerPlayer player && source.getServer().getPlayerList().isOp(player.nameAndId())))
                    .then(argument("amount", LongArgumentType.longArg(0))
                        .executes(context -> {
                            ServerPlayer target = EntityArgument.getPlayer(context, "player");
                            long amount = LongArgumentType.getLong(context, "amount");
                            ECONOMY.setBalance(target.getUUID(), amount);
                            ECONOMY.save(context.getSource().getServer());
                            context.getSource().sendSuccess(() -> Component.literal("Set " + target.getGameProfile().name() + "'s balance to $" + amount + "."), true);
                            target.sendSystemMessage(Component.literal("Your balance is now $" + amount + "."));
                            return 1;
                        }))));

            dispatcher.register(literal("pay")
                .then(argument("player", EntityArgument.player())
                    .then(argument("amount", LongArgumentType.longArg(1))
                        .executes(context -> {
                            ServerPlayer sender = context.getSource().getPlayerOrException();
                            ServerPlayer target = EntityArgument.getPlayer(context, "player");
                            long amount = LongArgumentType.getLong(context, "amount");
                            if (sender.getUUID().equals(target.getUUID())) {
                                context.getSource().sendFailure(Component.literal("You cannot pay yourself."));
                                return 0;
                            }
                            if (!ECONOMY.transfer(sender.getUUID(), target.getUUID(), amount)) {
                                context.getSource().sendFailure(Component.literal("Insufficient funds."));
                                return 0;
                            }
                            ECONOMY.save(context.getSource().getServer());
                            sender.sendSystemMessage(Component.literal("You paid " + target.getGameProfile().name() + " $" + amount + "."));
                            target.sendSystemMessage(Component.literal(sender.getGameProfile().name() + " paid you $" + amount + "."));
                            return 1;
                        }))));

            dispatcher.register(literal("blemm")
                .then(literal("balance").executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    context.getSource().sendSuccess(() -> Component.literal("Balance: $" + ECONOMY.balance(player.getUUID())), false);
                    return 1;
                })));
            LocationCommands.register(dispatcher);
        });
    }

    private static final class EconomyStore {
        private final Map<UUID, Long> balances = new HashMap<>();
        private Path file;

        long balance(UUID player) { return balances.getOrDefault(player, 100L); }
        void setBalance(UUID player, long amount) { balances.put(player, amount); }

        boolean transfer(UUID from, UUID to, long amount) {
            long current = balance(from);
            if (amount <= 0 || current < amount || Long.MAX_VALUE - balance(to) < amount) return false;
            balances.put(from, current - amount);
            balances.put(to, balance(to) + amount);
            return true;
        }

        void load(MinecraftServer server) {
            file = server.getServerDirectory().resolve("blemmessentials").resolve("balances.tsv");
            if (!Files.exists(file)) return;
            try {
                for (String line : Files.readAllLines(file)) {
                    String[] parts = line.split("\\t", 2);
                    if (parts.length != 2) continue;
                    try {
                        UUID id = UUID.fromString(parts[0]);
                        long value = Long.parseLong(parts[1]);
                        if (value >= 0) balances.put(id, value);
                    } catch (IllegalArgumentException ignored) { }
                }
            } catch (IOException exception) {
                throw new IllegalStateException("Could not load BlemmEssentials balances", exception);
            }
        }

        void save(MinecraftServer server) {
            Path destination = file != null ? file : server.getServerDirectory().resolve("blemmessentials").resolve("balances.tsv");
            try {
                Files.createDirectories(destination.getParent());
                Path temporary = destination.resolveSibling(destination.getFileName() + ".tmp");
                StringBuilder content = new StringBuilder();
                balances.forEach((uuid, amount) -> content.append(uuid).append('\t').append(amount).append('\n'));
                Files.writeString(temporary, content);
                try {
                    Files.move(temporary, destination, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
                } catch (IOException ignored) {
                    Files.move(temporary, destination, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                file = destination;
            } catch (IOException exception) {
                throw new IllegalStateException("Could not save BlemmEssentials balances", exception);
            }
        }
    }
}
