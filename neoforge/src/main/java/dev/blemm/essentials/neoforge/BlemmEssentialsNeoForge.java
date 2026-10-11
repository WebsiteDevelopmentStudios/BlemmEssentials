package dev.blemm.essentials.neoforge;

import com.mojang.brigadier.arguments.LongArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import dev.blemm.essentials.locations.LocationCommands;
import dev.blemm.essentials.auction.AuctionStore;
import dev.blemm.essentials.ranks.RankStore;
import dev.blemm.essentials.friends.FriendStore;
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
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) dev.blemm.essentials.ranks.RankPresentation.apply(player);
        });
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) -> { ECONOMY.load(event.getServer()); LocationCommands.load(event.getServer()); AuctionStore.load(event.getServer()); RankStore.load(event.getServer()); FriendStore.load(event.getServer()); dev.blemm.essentials.shop.ShopPrices.load(event.getServer()); });
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent event) -> { ECONOMY.save(event.getServer()); LocationCommands.save(event.getServer()); AuctionStore.save(event.getServer()); RankStore.save(event.getServer()); FriendStore.save(event.getServer()); });
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
            dispatcher.register(Commands.literal("shopprice")
                .then(Commands.argument("item", com.mojang.brigadier.arguments.StringArgumentType.word()).executes(context -> {
                    String rawItemId = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "item").toLowerCase(java.util.Locale.ROOT);
                    final String itemId = rawItemId.contains(":") ? rawItemId : "minecraft:" + rawItemId;
                    context.getSource().sendSuccess(() -> Component.literal(itemId + " — buy: $" +
                        dev.blemm.essentials.shop.ShopPrices.buy(itemId) + ", sell: $" +
                        dev.blemm.essentials.shop.ShopPrices.sell(itemId) +
                        " (edit blemmessentials/shop-prices.properties to customize prices)"), false);
                    return 1;
                })));
            dispatcher.register(Commands.literal("shopprices").executes(context -> {
                context.getSource().sendSuccess(() -> Component.literal("Shop prices are editable in blemmessentials/shop-prices.properties. " +
                    "Keys use default.<category>.buy/sell or item.<namespace:id>.buy/sell. Categories: building_blocks, natural, ores_minerals, tools, combat, food, farming, redstone, decoration, brewing, transport, miscellaneous."), false);
                return 1;
            }));
        dispatcher.register(Commands.literal("shop")
            .then(Commands.argument("item", com.mojang.brigadier.arguments.StringArgumentType.word())
                .then(Commands.argument("amount", LongArgumentType.longArg(1, 64)).executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    String rawId = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "item").toLowerCase(java.util.Locale.ROOT);
                    if (!rawId.contains(":")) rawId = "minecraft:" + rawId;
                    net.minecraft.resources.Identifier itemId = net.minecraft.resources.Identifier.tryParse(rawId);
                    if (itemId == null) {
                        context.getSource().sendFailure(Component.literal("Invalid item ID."));
                        return 0;
                    }
                    net.minecraft.world.item.Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(itemId);
                    if (item == null || item == net.minecraft.world.item.Items.AIR) {
                        context.getSource().sendFailure(Component.literal("Unknown item."));
                        return 0;
                    }
                    int amount = (int) LongArgumentType.getLong(context, "amount");
                    int stackLimit = new net.minecraft.world.item.ItemStack(item).getMaxStackSize();
                    if (amount > stackLimit) {
                        context.getSource().sendFailure(Component.literal("That item stacks to a maximum of " + stackLimit + "."));
                        return 0;
                    }
                    long price = priceFor(itemId.toString()) * amount;
                    if (player.getInventory().getFreeSlot() < 0) {
                        context.getSource().sendFailure(Component.literal("Free an inventory slot before buying."));
                        return 0;
                    }
                    if (!ECONOMY.withdraw(player.getUUID(), price)) {
                        context.getSource().sendFailure(Component.literal("Insufficient funds. Item costs $" + price + "."));
                        return 0;
                    }
                    net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(item, amount);
                    if (!player.getInventory().add(stack)) {
                        ECONOMY.deposit(player.getUUID(), price);
                        context.getSource().sendFailure(Component.literal("Could not add the item to your inventory."));
                        return 0;
                    }
                    ECONOMY.save(context.getSource().getServer());
                    context.getSource().sendSuccess(() -> Component.literal("Bought " + amount + " " + itemId + " for $" + price + "."), false);
                    return 1;
                }))));
        dispatcher.register(Commands.literal("sell")
            .executes(context -> {
                ServerPlayer player = context.getSource().getPlayerOrException();
                net.minecraft.world.item.ItemStack stack = player.getMainHandItem();
                if (stack.isEmpty()) {
                    context.getSource().sendFailure(Component.literal("Hold an item to sell."));
                    return 0;
                }
                String itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                int amount = stack.getCount();
                long unitSellPrice = dev.blemm.essentials.shop.ShopPrices.sell(itemId);
                if (unitSellPrice <= 0) {
                    context.getSource().sendFailure(Component.literal("This item cannot be sold."));
                    return 0;
                }
                long payout = unitSellPrice * amount;
                stack.shrink(amount);
                player.getInventory().setItem(player.getInventory().getSelectedSlot(), stack.isEmpty() ? net.minecraft.world.item.ItemStack.EMPTY : stack);
                ECONOMY.deposit(player.getUUID(), payout);
                ECONOMY.save(context.getSource().getServer());
                context.getSource().sendSuccess(() -> Component.literal("Sold " + amount + " " + itemId + " for $" + payout + "."), false);
                return 1;
            }));
        dispatcher.register(Commands.literal("rank")
            .executes(context -> {
                ServerPlayer player = context.getSource().getPlayerOrException();
                context.getSource().sendSuccess(() -> Component.literal("Your rank: " + RankStore.get(player.getUUID())), false);
                return 1;
            })
            .then(Commands.literal("set")
                .requires(source -> source.getEntity() == null || (source.getEntity() instanceof ServerPlayer operator
                    && source.getServer().getPlayerList().isOp(operator.nameAndId())))
                .then(Commands.argument("player", EntityArgument.player())
                    .then(Commands.argument("rank", com.mojang.brigadier.arguments.StringArgumentType.word()).executes(context -> {
                        ServerPlayer target = EntityArgument.getPlayer(context, "player");
                        String rank = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "rank");
                        if (!RankStore.set(target.getUUID(), rank, context.getSource().getServer())) {
                            context.getSource().sendFailure(Component.literal("Valid ranks: member, vip, mvp, admin."));
                            return 0;
                        }
                        dev.blemm.essentials.ranks.RankPresentation.apply(target);
                        context.getSource().sendSuccess(() -> Component.literal("Set " + target.getGameProfile().name() + "'s rank to " + rank.toLowerCase(java.util.Locale.ROOT) + "."), true);
                        target.sendSystemMessage(Component.literal("Your rank is now " + RankStore.get(target.getUUID()) + "."));
                        return 1;
                    }))))
            .then(Commands.argument("player", EntityArgument.player()).executes(context -> {
                ServerPlayer target = EntityArgument.getPlayer(context, "player");
                context.getSource().sendSuccess(() -> Component.literal(target.getGameProfile().name() + "'s rank: " + RankStore.get(target.getUUID())), false);
                return 1;
            })));
        dispatcher.register(Commands.literal("help-").executes(context -> {
            context.getSource().sendSuccess(() -> Component.literal("BlemmEssentials: /balance, /pay, /shop, /sell, /ah, /sethome, /home, /delhome, /setwarp, /warp, /delwarp, /lobby, /setlobby, /rtp, /tpa, /tpahere, /tpaccept, /tpdeny, /rank"), false);
            return 1;
        }));
        dispatcher.register(Commands.literal("ah")
            .then(Commands.literal("list").executes(context -> {
                var listings = AuctionStore.listings();
                if (listings.isEmpty()) {
                    context.getSource().sendSuccess(() -> Component.literal("The auction house is empty."), false);
                    return 1;
                }
                for (AuctionStore.Listing listing : listings) {
                    context.getSource().sendSuccess(() -> Component.literal("#" + listing.id() + " " + listing.amount()
                        + "x " + listing.itemId() + " — $" + listing.price() + " (seller " + listing.seller() + ")"), false);
                }
                return 1;
            }))
            .then(Commands.literal("sell")
                .then(Commands.argument("price", LongArgumentType.longArg(1)).executes(context -> {
                    ServerPlayer seller = context.getSource().getPlayerOrException();
                    long price = LongArgumentType.getLong(context, "price");
                    net.minecraft.world.item.ItemStack held = seller.getMainHandItem();
                    if (held.isEmpty()) {
                        context.getSource().sendFailure(Component.literal("Hold an item to list."));
                        return 0;
                    }
                    String itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(held.getItem()).toString();
                    int amount = held.getCount();
                    AuctionStore.Listing listing = AuctionStore.create(seller.getUUID(), price, itemId, amount, context.getSource().getServer());
                    seller.getInventory().setItem(seller.getInventory().getSelectedSlot(), net.minecraft.world.item.ItemStack.EMPTY);
                    seller.sendSystemMessage(Component.literal("Listed " + amount + "x " + itemId + " as auction #" + listing.id() + " for $" + price + "."));
                    return 1;
                })))
            .then(Commands.literal("buy")
                .then(Commands.argument("id", LongArgumentType.longArg(1)).executes(context -> {
                    ServerPlayer buyer = context.getSource().getPlayerOrException();
                    long id = LongArgumentType.getLong(context, "id");
                    AuctionStore.Listing listing = AuctionStore.get(id);
                    if (listing == null) {
                        context.getSource().sendFailure(Component.literal("Auction listing not found."));
                        return 0;
                    }
                    if (listing.seller().equals(buyer.getUUID())) {
                        context.getSource().sendFailure(Component.literal("You cannot buy your own listing."));
                        return 0;
                    }
                    if (buyer.getInventory().getFreeSlot() < 0) {
                        context.getSource().sendFailure(Component.literal("Free an inventory slot before buying."));
                        return 0;
                    }
                    net.minecraft.resources.Identifier itemId = net.minecraft.resources.Identifier.tryParse(listing.itemId());
                    net.minecraft.world.item.Item item = itemId == null ? null : net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(itemId);
                    if (item == null || item == net.minecraft.world.item.Items.AIR) {
                        context.getSource().sendFailure(Component.literal("The listed item is no longer available."));
                        return 0;
                    }
                    if (!ECONOMY.withdraw(buyer.getUUID(), listing.price())) {
                        context.getSource().sendFailure(Component.literal("Insufficient funds. This listing costs $" + listing.price() + "."));
                        return 0;
                    }
                    AuctionStore.remove(id, context.getSource().getServer());
                    net.minecraft.world.item.ItemStack bought = new net.minecraft.world.item.ItemStack(item, listing.amount());
                    if (!buyer.getInventory().add(bought)) {
                        ECONOMY.deposit(buyer.getUUID(), listing.price());
                        AuctionStore.create(listing.seller(), listing.price(), listing.itemId(), listing.amount(), context.getSource().getServer());
                        ECONOMY.save(context.getSource().getServer());
                        context.getSource().sendFailure(Component.literal("Could not add the item to your inventory; purchase refunded."));
                        return 0;
                    }
                    ECONOMY.deposit(listing.seller(), listing.price());
                    ECONOMY.save(context.getSource().getServer());
                    ServerPlayer seller = context.getSource().getServer().getPlayerList().getPlayer(listing.seller());
                    if (seller != null) seller.sendSystemMessage(Component.literal("Your auction #" + id + " sold for $" + listing.price() + "."));
                    context.getSource().sendSuccess(() -> Component.literal("Bought auction #" + id + " for $" + listing.price() + "."), false);
                    return 1;
                })))
            .then(Commands.literal("cancel")
                .then(Commands.argument("id", LongArgumentType.longArg(1)).executes(context -> {
                    ServerPlayer actor = context.getSource().getPlayerOrException();
                    long id = LongArgumentType.getLong(context, "id");
                    AuctionStore.Listing listing = AuctionStore.get(id);
                    if (listing == null || (!listing.seller().equals(actor.getUUID())
                        && !context.getSource().getServer().getPlayerList().isOp(actor.nameAndId()))) {
                        context.getSource().sendFailure(Component.literal("Listing not found or you do not own it."));
                        return 0;
                    }
                    ServerPlayer seller = context.getSource().getServer().getPlayerList().getPlayer(listing.seller());
                    if (seller == null || seller.getInventory().getFreeSlot() < 0) {
                        context.getSource().sendFailure(Component.literal("The seller must be online with a free inventory slot to cancel this listing."));
                        return 0;
                    }
                    net.minecraft.resources.Identifier itemId = net.minecraft.resources.Identifier.tryParse(listing.itemId());
                    net.minecraft.world.item.Item item = itemId == null ? null : net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(itemId);
                    if (item == null || item == net.minecraft.world.item.Items.AIR) {
                        context.getSource().sendFailure(Component.literal("The listed item is no longer available."));
                        return 0;
                    }
                    AuctionStore.remove(id, context.getSource().getServer());
                    seller.getInventory().add(new net.minecraft.world.item.ItemStack(item, listing.amount()));
                    seller.sendSystemMessage(Component.literal("Auction #" + id + " was cancelled and returned."));
                    return 1;
                }))));
        LocationCommands.register(dispatcher);
    }

    private static long priceFor(String itemId) {
        return dev.blemm.essentials.shop.ShopPrices.buy(itemId);
    }

    private static final class EconomyStore {
        private final Map<UUID, Long> balances = new HashMap<>();
        private Path file;

        long balance(UUID id) { return balances.getOrDefault(id, 100L); }
        void setBalance(UUID id, long amount) { balances.put(id, amount); }
        boolean withdraw(UUID player, long amount) {
            long current = balance(player);
            if (amount < 0 || current < amount) return false;
            balances.put(player, current - amount);
            return true;
        }

        void deposit(UUID player, long amount) {
            if (amount < 0 || Long.MAX_VALUE - balance(player) < amount) return;
            balances.put(player, balance(player) + amount);
        }

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
