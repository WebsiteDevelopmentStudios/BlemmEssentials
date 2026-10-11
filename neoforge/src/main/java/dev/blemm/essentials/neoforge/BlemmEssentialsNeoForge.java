package dev.blemm.essentials.neoforge;

import com.mojang.brigadier.arguments.LongArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import dev.blemm.essentials.locations.LocationCommands;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mod(BlemmEssentialsNeoForge.MOD_ID)
public final class BlemmEssentialsNeoForge {
    public static final String MOD_ID = "blemmessentials";
    private static final EconomyStore ECONOMY = new EconomyStore();

    public BlemmEssentialsNeoForge(IEventBus modEventBus) {
        NeoForge.EVENT_BUS.addListener(this::registerCommands);
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) -> { ECONOMY.load(event.getServer()); LocationCommands.load(event.getServer()); });
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent event) -> { ECONOMY.save(event.getServer()); LocationCommands.save(event.getServer()); });
    }

    private void registerCommands(RegisterCommandsEvent event) {
        var dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("balance").executes(context -> {
            ServerPlayer player = context.getSource().getPlayerOrException();
            context.getSource().sendSuccess(() -> Component.literal("Balance: $" + ECONOMY.balance(player.getUUID())), false);
            return 1;
        }));
        dispatcher.register(Commands.literal("money")
            .executes(context -> {
                ServerPlayer player = context.getSource().getPlayerOrException();
                context.getSource().sendSuccess(() -> Component.literal("Balance: $" + ECONOMY.balance(player.getUUID())), false);
                return 1;
            })
            .then(Commands.argument("player", EntityArgument.player()).requires(source -> source.getEntity() == null || (source.getEntity() instanceof ServerPlayer player && source.getServer().getPlayerList().isOp(player.nameAndId())))
                .then(Commands.argument("amount", LongArgumentType.longArg(0)).executes(context -> {
                    ServerPlayer target = EntityArgument.getPlayer(context, "player");
                    long amount = LongArgumentType.getLong(context, "amount");
                    ECONOMY.setBalance(target.getUUID(), amount);
                    ECONOMY.save(context.getSource().getServer());
                    context.getSource().sendSuccess(() -> Component.literal("Set " + target.getGameProfile().name() + "'s balance to $" + amount + "."), true);
                    target.sendSystemMessage(Component.literal("Your balance is now $" + amount + "."));
                    return 1;
                }))));
        dispatcher.register(Commands.literal("pay")
            .then(Commands.argument("player", EntityArgument.player())
                .then(Commands.argument("amount", LongArgumentType.longArg(1)).executes(context -> {
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
        LocationCommands.register(dispatcher);
    }

    private static final class EconomyStore {
        private final Map<UUID, Long> balances = new HashMap<>();
        private Path file;

        long balance(UUID id) { return balances.getOrDefault(id, 100L); }
        void setBalance(UUID id, long amount) { balances.put(id, amount); }

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
                        long value = Long.parseLong(parts[1]);
                        if (value >= 0) balances.put(UUID.fromString(parts[0]), value);
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
                StringBuilder data = new StringBuilder();
                balances.forEach((id, amount) -> data.append(id).append('\t').append(amount).append('\n'));
                Files.writeString(temporary, data);
                try {
                    Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (IOException ignored) {
                    Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
                }
                file = destination;
            } catch (IOException exception) {
                throw new IllegalStateException("Could not save BlemmEssentials balances", exception);
            }
        }
    }
}
