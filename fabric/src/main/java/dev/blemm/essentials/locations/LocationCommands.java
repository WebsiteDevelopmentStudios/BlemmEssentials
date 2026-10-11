package dev.blemm.essentials.locations;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import dev.blemm.essentials.friends.FriendStore;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class LocationCommands {
    private static final Properties DATA = new Properties();
    private static final Map<UUID, Request> REQUESTS = new HashMap<>();
    private static Path dataFile;

    private LocationCommands() {}

    public static synchronized void load(MinecraftServer server) {
        dataFile = server.getServerDirectory().resolve("blemmessentials").resolve("locations.properties");
        DATA.clear();
        if (!Files.exists(dataFile)) return;
        try (InputStream input = Files.newInputStream(dataFile)) {
            DATA.load(input);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not load BlemmEssentials locations", exception);
        }
    }

    public static synchronized void save(MinecraftServer server) {
        Path destination = dataFile != null ? dataFile : server.getServerDirectory().resolve("blemmessentials").resolve("locations.properties");
        try {
            Files.createDirectories(destination.getParent());
            try (OutputStream output = Files.newOutputStream(destination)) {
                DATA.store(output, "BlemmEssentials homes, warps, and lobby");
            }
            dataFile = destination;
        } catch (IOException exception) {
            throw new IllegalStateException("Could not save BlemmEssentials locations", exception);
        }
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("sethome")
            .executes(context -> setHome(context.getSource().getPlayerOrException(), "home", context.getSource().getServer()))
            .then(Commands.argument("name", StringArgumentType.word()).executes(context ->
                setHome(context.getSource().getPlayerOrException(), StringArgumentType.getString(context, "name"), context.getSource().getServer()))));

        dispatcher.register(Commands.literal("home")
            .executes(context -> goHome(context.getSource().getPlayerOrException(), "home", context.getSource().getServer()))
            .then(Commands.argument("name", StringArgumentType.word()).executes(context ->
                goHome(context.getSource().getPlayerOrException(), StringArgumentType.getString(context, "name"), context.getSource().getServer()))));

        dispatcher.register(Commands.literal("delhome")
            .then(Commands.argument("name", StringArgumentType.word()).executes(context -> {
                ServerPlayer player = context.getSource().getPlayerOrException();
                String key = homeKey(player.getUUID(), StringArgumentType.getString(context, "name"));
                synchronized (LocationCommands.class) {
                    if (DATA.remove(key) == null) {
                        context.getSource().sendFailure(Component.literal("That home does not exist."));
                        return 0;
                    }
                    save(context.getSource().getServer());
                }
                context.getSource().sendSuccess(() -> Component.literal("Home deleted."), false);
                return 1;
            })));

        dispatcher.register(Commands.literal("setwarp").requires(LocationCommands::isOperator)
            .then(Commands.argument("name", StringArgumentType.word()).executes(context -> {
                ServerPlayer player = context.getSource().getPlayerOrException();
                String name = StringArgumentType.getString(context, "name").toLowerCase(java.util.Locale.ROOT);
                synchronized (LocationCommands.class) {
                    DATA.setProperty("warp." + name, encode(player));
                    save(context.getSource().getServer());
                }
                context.getSource().sendSuccess(() -> Component.literal("Warp '" + name + "' saved."), true);
                return 1;
            })));

        dispatcher.register(Commands.literal("warp")
            .then(Commands.argument("name", StringArgumentType.word()).executes(context -> {
                String name = StringArgumentType.getString(context, "name").toLowerCase(java.util.Locale.ROOT);
                String encoded;
                synchronized (LocationCommands.class) { encoded = DATA.getProperty("warp." + name); }
                return teleport(context.getSource(), encoded, "Warp not found.");
            })));

        dispatcher.register(Commands.literal("delwarp").requires(LocationCommands::isOperator)
            .then(Commands.argument("name", StringArgumentType.word()).executes(context -> {
                String name = StringArgumentType.getString(context, "name").toLowerCase(java.util.Locale.ROOT);
                synchronized (LocationCommands.class) {
                    if (DATA.remove("warp." + name) == null) {
                        context.getSource().sendFailure(Component.literal("Warp not found."));
                        return 0;
                    }
                    save(context.getSource().getServer());
                }
                context.getSource().sendSuccess(() -> Component.literal("Warp deleted."), true);
                return 1;
            })));

        dispatcher.register(Commands.literal("setlobby").requires(LocationCommands::isOperator).executes(context -> {
            ServerPlayer player = context.getSource().getPlayerOrException();
            synchronized (LocationCommands.class) {
                DATA.setProperty("lobby", encode(player));
                save(context.getSource().getServer());
            }
            context.getSource().sendSuccess(() -> Component.literal("Lobby location saved."), true);
            return 1;
        }));

        dispatcher.register(Commands.literal("lobby").executes(context -> {
            String encoded;
            synchronized (LocationCommands.class) { encoded = DATA.getProperty("lobby"); }
            return teleport(context.getSource(), encoded, "Lobby location has not been set.");
        }));

        dispatcher.register(Commands.literal("friend")
            .then(Commands.literal("add").then(Commands.argument("player", EntityArgument.player()).executes(context -> {
                ServerPlayer player = context.getSource().getPlayerOrException();
                ServerPlayer target = EntityArgument.getPlayer(context, "player");
                if (player.getUUID().equals(target.getUUID())) {
                    context.getSource().sendFailure(Component.literal("You cannot add yourself as a friend."));
                    return 0;
                }
                FriendStore.add(player.getUUID(), target.getUUID(), context.getSource().getServer());
                player.sendSystemMessage(Component.literal(target.getGameProfile().name() + " added as a friend."));
                target.sendSystemMessage(Component.literal(player.getGameProfile().name() + " added you as a friend."));
                return 1;
            })))
            .then(Commands.literal("remove").then(Commands.argument("player", EntityArgument.player()).executes(context -> {
                ServerPlayer player = context.getSource().getPlayerOrException();
                ServerPlayer target = EntityArgument.getPlayer(context, "player");
                FriendStore.remove(player.getUUID(), target.getUUID(), context.getSource().getServer());
                context.getSource().sendSuccess(() -> Component.literal("Friend relationship removed."), false);
                return 1;
            })))
            .then(Commands.literal("list").executes(context -> {
                ServerPlayer player = context.getSource().getPlayerOrException();
                var friends = FriendStore.friends(player.getUUID());
                if (friends.isEmpty()) {
                    context.getSource().sendSuccess(() -> Component.literal("Your friends list is empty."), false);
                    return 1;
                }
                for (UUID friendId : friends) {
                    ServerPlayer online = context.getSource().getServer().getPlayerList().getPlayer(friendId);
                    String name = online == null ? friendId.toString() : online.getGameProfile().name();
                    context.getSource().sendSuccess(() -> Component.literal("Friend: " + name), false);
                }
                return 1;
            })));
        dispatcher.register(Commands.literal("homefriends").executes(context -> {
            ServerPlayer player = context.getSource().getPlayerOrException();
            boolean enabled = FriendStore.setHomeVisits(player.getUUID(),
                !FriendStore.homeVisitsEnabled(player.getUUID()), context.getSource().getServer());
            player.sendSystemMessage(Component.literal("Friends may now " + (enabled ? "visit" : "not visit") + " your homes."));
            return 1;
        }));
        dispatcher.register(Commands.literal("homevisit")
            .then(Commands.argument("player", EntityArgument.player())
                .then(Commands.argument("home", StringArgumentType.word()).executes(context -> {
                    ServerPlayer visitor = context.getSource().getPlayerOrException();
                    ServerPlayer owner = EntityArgument.getPlayer(context, "player");
                    String homeName = StringArgumentType.getString(context, "home");
                    if (!FriendStore.areFriends(visitor.getUUID(), owner.getUUID())
                        || !FriendStore.homeVisitsEnabled(owner.getUUID())) {
                        context.getSource().sendFailure(Component.literal("That player has not enabled home visits for you."));
                        return 0;
                    }
                    String encoded;
                    synchronized (LocationCommands.class) {
                        encoded = DATA.getProperty(homeKey(owner.getUUID(), homeName));
                    }
                    return teleport(context.getSource(), encoded, "That home does not exist.");
                }))));
        dispatcher.register(Commands.literal("rtp").executes(context -> {
            ServerPlayer player = context.getSource().getPlayerOrException();
            if (!(player.level() instanceof ServerLevel level)) {
                context.getSource().sendFailure(Component.literal("Random teleport is unavailable in this world."));
                return 0;
            }
            ThreadLocalRandom random = ThreadLocalRandom.current();
            BlockPos spawn = level.getSharedSpawnPos();
            for (int attempt = 0; attempt < 32; attempt++) {
                int radius = random.nextInt(100, 5001);
                double angle = random.nextDouble(0, Math.PI * 2);
                int x = spawn.getX() + (int) Math.round(Math.cos(angle) * radius);
                int z = spawn.getZ() + (int) Math.round(Math.sin(angle) * radius);
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                if (y <= level.getMinY() || y >= level.getMaxY() - 1) continue;
                BlockPos feet = new BlockPos(x, y, z);
                if (!level.getBlockState(feet).isAir() || !level.getBlockState(feet.above()).isAir()
                    || level.getBlockState(feet.below()).isAir()) continue;
                player.teleportTo(x + 0.5, y, z + 0.5);
                player.sendSystemMessage(Component.literal("Randomly teleported."));
                return 1;
            }
            context.getSource().sendFailure(Component.literal("Could not find a safe location. Try again."));
            return 0;
        }));

        dispatcher.register(Commands.literal("tpa")
            .then(Commands.argument("player", EntityArgument.player()).executes(context ->
                requestTeleport(context.getSource().getPlayerOrException(), EntityArgument.getPlayer(context, "player"), false, context.getSource()))));

        dispatcher.register(Commands.literal("tpahere")
            .then(Commands.argument("player", EntityArgument.player()).executes(context ->
                requestTeleport(context.getSource().getPlayerOrException(), EntityArgument.getPlayer(context, "player"), true, context.getSource()))));

        dispatcher.register(Commands.literal("tpaccept").executes(context -> respond(context.getSource().getPlayerOrException(), true, context.getSource())));
        dispatcher.register(Commands.literal("tpdeny").executes(context -> respond(context.getSource().getPlayerOrException(), false, context.getSource())));
    }

    private static boolean isOperator(CommandSourceStack source) {
        if (source.getEntity() == null) return true;
        return source.getEntity() instanceof ServerPlayer player
            && source.getServer().getPlayerList().isOp(player.nameAndId());
    }

    private static int setHome(ServerPlayer player, String name, MinecraftServer server) {
        synchronized (LocationCommands.class) {
            String key = homeKey(player.getUUID(), name);
            String prefix = "home." + player.getUUID() + ".";
            long homeCount = DATA.stringPropertyNames().stream().filter(existing -> existing.startsWith(prefix)).count();
            if (!DATA.containsKey(key) && homeCount >= 3) {
                player.sendSystemMessage(Component.literal("You have reached the default limit of 3 homes."));
                return 0;
            }
            DATA.setProperty(key, encode(player));
            save(server);
        }
        player.sendSystemMessage(Component.literal("Home '" + name + "' saved."));
        return 1;
    }

    private static int goHome(ServerPlayer player, String name, MinecraftServer server) {
        String encoded;
        synchronized (LocationCommands.class) { encoded = DATA.getProperty(homeKey(player.getUUID(), name)); }
        return teleport(player.createCommandSourceStack(), encoded, "Home not found.");
    }

    private static String homeKey(UUID uuid, String name) {
        return "home." + uuid + "." + name.toLowerCase(java.util.Locale.ROOT);
    }

    private static String encode(ServerPlayer player) {
        return player.level().dimension().identifier() + ";"
            + player.getX() + ";" + player.getY() + ";" + player.getZ() + ";"
            + player.getYRot() + ";" + player.getXRot();
    }

    private static int teleport(CommandSourceStack source, String encoded, String missingMessage) {
        if (encoded == null || encoded.isBlank()) {
            source.sendFailure(Component.literal(missingMessage));
            return 0;
        }
        try {
            String[] fields = encoded.split(";", -1);
            if (fields.length != 6) throw new IllegalArgumentException("Invalid saved location");
            ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, Identifier.parse(fields[0]));
            ServerLevel level = source.getServer().getLevel(dimension);
            if (level == null) {
                source.sendFailure(Component.literal("That location's world is not loaded."));
                return 0;
            }
            double x = Double.parseDouble(fields[1]);
            double y = Double.parseDouble(fields[2]);
            double z = Double.parseDouble(fields[3]);
            float yaw = Float.parseFloat(fields[4]);
            float pitch = Float.parseFloat(fields[5]);
            if (!(source.getEntity() instanceof ServerPlayer player)) {
                source.sendFailure(Component.literal("This command can only be used by a player."));
                return 0;
            }
            player.teleportTo(level, x, y, z, Set.of(), yaw, pitch, false);
            player.sendSystemMessage(Component.literal("Teleported."));
            return 1;
        } catch (RuntimeException exception) {
            source.sendFailure(Component.literal("That saved location is invalid."));
            return 0;
        }
    }

    private static int requestTeleport(ServerPlayer sender, ServerPlayer target, boolean here, CommandSourceStack source) {
        if (sender.getUUID().equals(target.getUUID())) {
            source.sendFailure(Component.literal("You cannot request a teleport to yourself."));
            return 0;
        }
        REQUESTS.put(target.getUUID(), new Request(sender.getUUID(), here, System.currentTimeMillis() + 60_000L));
        target.sendSystemMessage(Component.literal(sender.getGameProfile().name()
            + (here ? " asks you to teleport to them." : " wants to teleport to you.")
            + " Use /tpaccept or /tpdeny within 60 seconds."));
        sender.sendSystemMessage(Component.literal("Teleport request sent to " + target.getGameProfile().name() + "."));
        return 1;
    }

    private static int respond(ServerPlayer target, boolean accept, CommandSourceStack source) {
        Request request = REQUESTS.remove(target.getUUID());
        if (request == null || request.expiresAt() < System.currentTimeMillis()) {
            source.sendFailure(Component.literal("You have no active teleport requests."));
            return 0;
        }
        ServerPlayer requester = source.getServer().getPlayerList().getPlayer(request.requester());
        if (requester == null) {
            source.sendFailure(Component.literal("That player is no longer online."));
            return 0;
        }
        if (!accept) {
            requester.sendSystemMessage(Component.literal(target.getGameProfile().name() + " denied your teleport request."));
            target.sendSystemMessage(Component.literal("Teleport request denied."));
            return 1;
        }
        ServerPlayer moving = request.teleportHere() ? requester : target;
        ServerPlayer destination = request.teleportHere() ? target : requester;
        if (!(destination.level() instanceof ServerLevel destinationLevel)) {
            source.sendFailure(Component.literal("The destination world is unavailable."));
            return 0;
        }
        moving.teleportTo(destinationLevel, destination.getX(), destination.getY(), destination.getZ(),
            Set.of(), destination.getYRot(), destination.getXRot(), false);
        moving.sendSystemMessage(Component.literal("Teleport request accepted."));
        return 1;
    }

    private record Request(UUID requester, boolean teleportHere, long expiresAt) {}
}
