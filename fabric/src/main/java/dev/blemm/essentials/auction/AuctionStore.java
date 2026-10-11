package dev.blemm.essentials.auction;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.RegistryOps;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

public final class AuctionStore {
    private static final Properties DATA = new Properties();
    private static Path dataFile;

    private AuctionStore() {}

    public record Listing(long id, UUID seller, long price, String itemId, int amount, long createdAt, String itemData) {}

    public static synchronized void load(MinecraftServer server) {
        dataFile = server.getServerDirectory().resolve("blemmessentials").resolve("auctions.properties");
        DATA.clear();
        if (!Files.exists(dataFile)) return;
        try (InputStream input = Files.newInputStream(dataFile)) {
            DATA.load(input);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not load BlemmEssentials auction listings", exception);
        }
    }

    public static synchronized void save(MinecraftServer server) {
        Path destination = dataFile != null ? dataFile : server.getServerDirectory().resolve("blemmessentials").resolve("auctions.properties");
        try {
            Files.createDirectories(destination.getParent());
            try (OutputStream output = Files.newOutputStream(destination)) {
                DATA.store(output, "BlemmEssentials auction listings");
            }
            dataFile = destination;
        } catch (IOException exception) {
            throw new IllegalStateException("Could not save BlemmEssentials auction listings", exception);
        }
    }

    public static synchronized Listing create(UUID seller, long price, String itemId, int amount, MinecraftServer server) {
        return create(seller, price, itemId, amount, "", server);
    }

    public static synchronized Listing create(UUID seller, long price, ItemStack stack, MinecraftServer server) {
        String itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        String itemData = encodeStack(stack, server);
        return create(seller, price, itemId, stack.getCount(), itemData, server);
    }

    public static synchronized Listing create(Listing previous, MinecraftServer server) {
        return create(previous.seller(), previous.price(), previous.itemId(), previous.amount(), previous.itemData(), server);
    }

    private static synchronized Listing create(UUID seller, long price, String itemId, int amount, String itemData, MinecraftServer server) {
        long id = DATA.stringPropertyNames().stream()
            .filter(key -> key.startsWith("listing."))
            .map(key -> key.substring("listing.".length()))
            .mapToLong(value -> {
                try { return Long.parseLong(value); } catch (NumberFormatException ignored) { return 0L; }
            }).max().orElse(0L) + 1L;
        Listing listing = new Listing(id, seller, price, itemId, amount, System.currentTimeMillis(), itemData);
        DATA.setProperty(key(id), encode(listing));
        save(server);
        return listing;
    }

    private static String encodeStack(ItemStack stack, MinecraftServer server) {
        try {
            var ops = RegistryOps.create(JsonOps.INSTANCE, server.registryAccess());
            return ItemStack.CODEC.encodeStart(ops, stack.copy()).result().map(Object::toString).orElse("");
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    public static ItemStack restoreStack(Listing listing, MinecraftServer server) {
        if (listing.itemData() != null && !listing.itemData().isBlank()) {
            try {
                var ops = RegistryOps.create(JsonOps.INSTANCE, server.registryAccess());
                ItemStack decoded = ItemStack.CODEC.parse(ops, JsonParser.parseString(listing.itemData())).result().orElse(ItemStack.EMPTY);
                if (!decoded.isEmpty()) return decoded;
            } catch (RuntimeException ignored) { }
        }
        var id = net.minecraft.resources.Identifier.tryParse(listing.itemId());
        var item = id == null ? null : net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(id);
        return item == null || item == net.minecraft.world.item.Items.AIR
            ? ItemStack.EMPTY : new ItemStack(item, listing.amount());
    }

    public static synchronized Listing get(long id) {
        String encoded = DATA.getProperty(key(id));
        return encoded == null ? null : decode(id, encoded);
    }

    public static synchronized List<Listing> listings() {
        List<Listing> listings = new ArrayList<>();
        for (String property : DATA.stringPropertyNames()) {
            if (!property.startsWith("listing.")) continue;
            try {
                long id = Long.parseLong(property.substring("listing.".length()));
                Listing listing = decode(id, DATA.getProperty(property));
                if (listing != null) listings.add(listing);
            } catch (NumberFormatException ignored) { }
        }
        listings.sort(Comparator.comparingLong(Listing::id));
        return listings;
    }

    public static synchronized void remove(long id, MinecraftServer server) {
        DATA.remove(key(id));
        save(server);
    }

    private static String key(long id) { return "listing." + id; }

    private static String encode(Listing listing) {
        String encodedData = listing.itemData() == null || listing.itemData().isBlank() ? "" :
            Base64.getUrlEncoder().withoutPadding().encodeToString(listing.itemData().getBytes(StandardCharsets.UTF_8));
        return listing.seller() + ";" + listing.price() + ";" + listing.itemId() + ";"
            + listing.amount() + ";" + listing.createdAt() + ";" + encodedData;
    }

    private static Listing decode(long id, String value) {
        try {
            String[] parts = value.split(";", -1);
            if (parts.length != 5 && parts.length != 6) return null;
            UUID seller = UUID.fromString(parts[0]);
            long price = Long.parseLong(parts[1]);
            int amount = Integer.parseInt(parts[3]);
            long createdAt = Long.parseLong(parts[4]);
            if (price <= 0 || amount <= 0 || amount > 64 || parts[2].isBlank()) return null;
            String itemData = parts.length == 6 && !parts[5].isBlank()
                ? new String(Base64.getUrlDecoder().decode(parts[5]), StandardCharsets.UTF_8) : "";
            return new Listing(id, seller, price, parts[2], amount, createdAt, itemData);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
