package dev.blemm.essentials.auction;

import net.minecraft.server.MinecraftServer;

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

    public record Listing(long id, UUID seller, long price, String itemId, int amount, long createdAt) {}

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
        long id = DATA.stringPropertyNames().stream()
            .filter(key -> key.startsWith("listing."))
            .map(key -> key.substring("listing.".length()))
            .mapToLong(value -> {
                try { return Long.parseLong(value); } catch (NumberFormatException ignored) { return 0L; }
            }).max().orElse(0L) + 1L;
        Listing listing = new Listing(id, seller, price, itemId, amount, System.currentTimeMillis());
        DATA.setProperty(key(id), encode(listing));
        save(server);
        return listing;
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
        return listing.seller() + ";" + listing.price() + ";" + listing.itemId() + ";"
            + listing.amount() + ";" + listing.createdAt();
    }

    private static Listing decode(long id, String value) {
        try {
            String[] parts = value.split(";", -1);
            if (parts.length != 5) return null;
            UUID seller = UUID.fromString(parts[0]);
            long price = Long.parseLong(parts[1]);
            int amount = Integer.parseInt(parts[3]);
            long createdAt = Long.parseLong(parts[4]);
            if (price <= 0 || amount <= 0 || amount > 64 || parts[2].isBlank()) return null;
            return new Listing(id, seller, price, parts[2], amount, createdAt);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
