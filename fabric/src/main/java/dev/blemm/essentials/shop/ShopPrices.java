package dev.blemm.essentials.shop;

import net.minecraft.server.MinecraftServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;

/** Editable survival-shop prices. Values are stored in the server's blemmessentials directory. */
public final class ShopPrices {
    private static final Properties PRICES = new Properties();
    private static Path file;
    private ShopPrices() {}

    public static synchronized void load(MinecraftServer server) {
        file = server.getServerDirectory().resolve("blemmessentials").resolve("shop-prices.properties");
        PRICES.clear();
        if (Files.exists(file)) {
            try (InputStream in = Files.newInputStream(file)) {
                PRICES.load(in);
            } catch (IOException e) {
                throw new IllegalStateException("Could not load BlemmEssentials shop prices", e);
            }
        } else {
            defaults();
            save();
        }
        if (!PRICES.containsKey("default.building_blocks.buy")) {
            defaults();
            save();
        }
    }

    private static void defaults() {
        PRICES.setProperty("default.building_blocks.buy", "10");
        PRICES.setProperty("default.building_blocks.sell", "2");
        PRICES.setProperty("default.natural.buy", "10");
        PRICES.setProperty("default.natural.sell", "2");
        PRICES.setProperty("default.ores_minerals.buy", "10");
        PRICES.setProperty("default.ores_minerals.sell", "2");
        PRICES.setProperty("default.tools.buy", "10");
        PRICES.setProperty("default.tools.sell", "2");
        PRICES.setProperty("default.combat.buy", "10");
        PRICES.setProperty("default.combat.sell", "2");
        PRICES.setProperty("default.food.buy", "10");
        PRICES.setProperty("default.food.sell", "2");
        PRICES.setProperty("default.farming.buy", "10");
        PRICES.setProperty("default.farming.sell", "2");
        PRICES.setProperty("default.redstone.buy", "10");
        PRICES.setProperty("default.redstone.sell", "2");
        PRICES.setProperty("default.decoration.buy", "10");
        PRICES.setProperty("default.decoration.sell", "2");
        PRICES.setProperty("default.brewing.buy", "10");
        PRICES.setProperty("default.brewing.sell", "2");
        PRICES.setProperty("default.transport.buy", "10");
        PRICES.setProperty("default.transport.sell", "2");
        PRICES.setProperty("default.miscellaneous.buy", "10");
        PRICES.setProperty("default.miscellaneous.sell", "2");
        PRICES.setProperty("item.minecraft:diamond.buy", "250");
        PRICES.setProperty("item.minecraft:diamond.sell", "50");
        PRICES.setProperty("item.minecraft:iron_ingot.buy", "40");
        PRICES.setProperty("item.minecraft:iron_ingot.sell", "8");
        PRICES.setProperty("item.minecraft:gold_ingot.buy", "55");
        PRICES.setProperty("item.minecraft:gold_ingot.sell", "11");
        PRICES.setProperty("item.minecraft:emerald.buy", "150");
        PRICES.setProperty("item.minecraft:emerald.sell", "30");
        PRICES.setProperty("item.minecraft:bread.buy", "8");
        PRICES.setProperty("item.minecraft:bread.sell", "2");
        PRICES.setProperty("item.minecraft:cooked_beef.buy", "18");
        PRICES.setProperty("item.minecraft:cooked_beef.sell", "4");
        PRICES.setProperty("item.minecraft:obsidian.buy", "90");
        PRICES.setProperty("item.minecraft:obsidian.sell", "18");
        PRICES.setProperty("item.minecraft:ender_pearl.buy", "45");
        PRICES.setProperty("item.minecraft:ender_pearl.sell", "9");
    }

    public static synchronized void save() {
        if (file == null) return;
        try {
            Files.createDirectories(file.getParent());
            try (OutputStream out = Files.newOutputStream(file)) {
                PRICES.store(out, "BlemmEssentials prices. Edit default.<category>.buy/sell or item.<namespace:id>.buy/sell");
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not save BlemmEssentials shop prices", e);
        }
    }

    public static synchronized long buy(String itemId) { return price(itemId, "buy"); }
    public static synchronized long sell(String itemId) { return price(itemId, "sell"); }

    private static long price(String itemId, String side) {
        String normalized = itemId.toLowerCase(Locale.ROOT);
        String specific = PRICES.getProperty("item." + normalized + "." + side);
        if (specific != null) return parse(specific, side.equals("buy") ? 10 : 2);
        String category = category(normalized);
        return parse(PRICES.getProperty("default." + category + "." + side), side.equals("buy") ? 10 : 2);
    }

    private static long parse(String raw, long fallback) {
        try { return Math.max(0L, Long.parseLong(raw)); }
        catch (RuntimeException ignored) { return fallback; }
    }

    private static String category(String id) {
        String n = id.substring(id.indexOf(':') + 1);
        if (contains(n, "_sword", "bow", "arrow", "trident", "shield", "_helmet", "_chestplate", "_leggings", "_boots", "mace", "totem", "elytra", "horse_armor", "wolf_armor", "wind_charge", "firework_rocket")) return "combat";
        if (contains(n, "potion", "blaze_powder", "fermented_spider_eye", "ghast_tear", "brewing_stand", "cauldron", "glass_bottle", "magma_cream", "dragon_breath", "phantom_membrane", "rabbit_foot")) return "brewing";
        if (contains(n, "_pickaxe", "_axe", "_shovel", "_hoe", "shears", "fishing_rod", "flint_and_steel", "brush", "spyglass", "compass", "clock", "bucket", "lead", "name_tag", "ender_eye", "ender_pearl", "map")) return "tools";
        if (contains(n, "_boat", "_raft", "minecart", "_rail", "saddle", "on_a_stick")) return "transport";
        if (contains(n, "seeds", "sapling", "bone_meal", "propagule", "sugar_cane", "bamboo", "cactus", "kelp", "pumpkin", "melon", "hay_block", "cocoa", "nether_wart", "composter", "wheat", "vine", "lily_pad", "azalea", "fern", "moss")) return "farming";
        if (contains(n, "redstone", "repeater", "comparator", "piston", "observer", "dispenser", "dropper", "hopper", "lever", "_button", "pressure_plate", "tnt", "target", "daylight", "lightning_rod", "tripwire", "note_block", "slime_block", "honey_block", "crafter")) return "redstone";
        if (contains(n, "ore", "ingot", "nugget", "raw_", "diamond", "emerald", "coal", "lapis", "quartz", "amethyst", "netherite", "copper", "glowstone_dust", "prismarine_shard") && !contains(n, "stairs", "slab", "wall", "bricks", "pillar")) return "ores_minerals";
        if (contains(n, "wool", "carpet", "banner", "_bed", "candle", "_head", "_skull", "painting", "item_frame", "flower_pot", "lantern", "torch", "_sign", "glass", "_dye", "terracotta", "concrete", "music_disc", "pottery", "template", "bookshelf", "tulip", "orchid", "dandelion", "poppy", "lilac", "peony", "armor_stand", "flower", "coral", "book", "chain", "bell")) return "decoration";
        if (contains(n, "_log", "_wood", "leaves", "dirt", "sand", "gravel", "grass", "mycelium", "podzol", "clay", "snow", "ice", "netherrack", "end_stone", "cobblestone", "stone", "deepslate", "tuff", "obsidian", "basalt", "blackstone", "mud", "soul_", "crimson_stem", "warped_stem", "sponge", "calcite", "dripstone", "cobweb", "roots", "magma_block") && !contains(n, "stairs", "slab", "wall", "brick", "button", "pressure", "sword", "_axe")) return "natural";
        if (contains(n, "bread", "beef", "porkchop", "chicken", "mutton", "rabbit", "cod", "salmon", "potato", "carrot", "apple", "stew", "cookie", "pie", "melon_slice", "berries", "chorus_fruit", "egg")) return "food";
        if (contains(n, "stone", "planks", "brick", "stairs", "slab", "wall", "block")) return "building_blocks";
        return "miscellaneous";
    }

    private static boolean contains(String value, String... needles) {
        for (String needle : needles) if (value.contains(needle)) return true;
        return false;
    }
}
