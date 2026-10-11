package dev.blemm.essentials;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.*;

/** Builds the full, categorised shop catalogue from every survival-obtainable item. */
public class Shop {
    public record Price(double buy, double sell) {}

    public static final List<String> CATEGORIES = List.of("Building Blocks", "Natural", "Ores & Minerals", "Tools",
            "Combat", "Food", "Farming", "Redstone", "Decoration", "Brewing", "Transport", "Miscellaneous");

    private static final Set<String> CREATIVE_ONLY = Set.of(
            "COMMAND_BLOCK", "CHAIN_COMMAND_BLOCK", "REPEATING_COMMAND_BLOCK", "COMMAND_BLOCK_MINECART",
            "STRUCTURE_BLOCK", "STRUCTURE_VOID", "JIGSAW", "BARRIER", "LIGHT", "BEDROCK", "SPAWNER",
            "TRIAL_SPAWNER", "VAULT", "END_PORTAL_FRAME", "END_PORTAL", "NETHER_PORTAL", "END_GATEWAY",
            "REINFORCED_DEEPSLATE", "DEBUG_STICK", "KNOWLEDGE_BOOK", "PETRIFIED_OAK_SLAB", "BUDDING_AMETHYST",
            "CHORUS_PLANT", "FARMLAND", "FROGSPAWN", "SCULK_SENSOR_PLACEHOLDER", "TEST_BLOCK", "TEST_INSTANCE_BLOCK",
            "ALLAY_SPAWN_EGG_PLACEHOLDER", "COPPER_GOLEM_STATUE_PLACEHOLDER", "BUNDLE_PLACEHOLDER");

    private final Map<String, List<Material>> byCategory = new LinkedHashMap<>();
    private final Map<Material, Price> prices = new EnumMap<>(Material.class);
    private final Map<Material, String> names = new EnumMap<>(Material.class);

    public Shop(ConfigurationSection cfg) {
        for (String c : CATEGORIES) byCategory.put(c, new ArrayList<>());
        Set<String> blocked = new HashSet<>();
        for (String s : cfg.getStringList("blocked-materials")) blocked.add(s.toUpperCase(Locale.ROOT));
        ConfigurationSection items = cfg.getConfigurationSection("items");
        ConfigurationSection defaults = cfg.getConfigurationSection("default-prices");

        for (Material m : Material.values()) {
            if (!obtainable(m) || blocked.contains(m.name())) continue;
            String cat = categorize(m);
            double buy = 10, sell = 2;
            ConfigurationSection ov = items == null ? null : items.getConfigurationSection(m.name());
            if (ov != null && ov.getString("category") != null) cat = ov.getString("category");
            if (defaults != null && defaults.getConfigurationSection(cat) != null) {
                buy = defaults.getDouble(cat + ".buy", buy);
                sell = defaults.getDouble(cat + ".sell", sell);
            }
            if (ov != null) {
                buy = ov.getDouble("buy", buy);
                sell = ov.getDouble("sell", sell);
                if (ov.getString("name") != null) names.put(m, ov.getString("name"));
            }
            byCategory.computeIfAbsent(cat, k -> new ArrayList<>()).add(m);
            prices.put(m, new Price(buy, sell));
        }
        byCategory.values().forEach(l -> l.sort(Comparator.comparing(Enum::name)));
    }

    /** Anything a survival player can hold: no air, legacy, creative-only blocks or spawn eggs. */
    public static boolean obtainable(Material m) {
        if (m.isLegacy() || m.isAir() || !m.isItem()) return false;
        String n = m.name();
        if (CREATIVE_ONLY.contains(n)) return false;
        if (n.endsWith("_SPAWN_EGG") || n.startsWith("INFESTED_")) return false;
        return true;
    }

    private static boolean has(String n, String... parts) {
        for (String p : parts) if (n.contains(p)) return true;
        return false;
    }

    static String categorize(Material m) {
        String n = m.name();
        if (has(n, "_SWORD", "BOW", "ARROW", "TRIDENT", "SHIELD", "_HELMET", "_CHESTPLATE", "_LEGGINGS", "_BOOTS",
                "MACE", "TOTEM", "ELYTRA", "HORSE_ARMOR", "WOLF_ARMOR", "WIND_CHARGE", "FIREWORK", "SPEAR")) return "Combat";
        if (has(n, "POTION", "BLAZE_POWDER", "FERMENTED_SPIDER_EYE", "GHAST_TEAR", "BREWING_STAND", "CAULDRON",
                "GLASS_BOTTLE", "MAGMA_CREAM", "DRAGON_BREATH", "PHANTOM_MEMBRANE", "RABBIT_FOOT")) return "Brewing";
        if (has(n, "_PICKAXE", "_AXE", "_SHOVEL", "_HOE", "SHEARS", "FISHING_ROD", "FLINT_AND_STEEL", "BRUSH",
                "SPYGLASS", "COMPASS", "CLOCK", "BUCKET", "LEAD", "NAME_TAG", "ENDER_EYE", "ENDER_PEARL", "MAP")) return "Tools";
        if (has(n, "_BOAT", "_RAFT", "MINECART", "_RAIL", "SADDLE", "ON_A_STICK")) return "Transport";
        if (m.isEdible()) return "Food";
        if (has(n, "ORE", "INGOT", "NUGGET", "RAW_", "DIAMOND", "EMERALD", "COAL", "LAPIS", "QUARTZ", "AMETHYST",
                "NETHERITE", "COPPER", "GLOWSTONE_DUST", "PRISMARINE_SHARD")
                && !has(n, "STAIRS", "SLAB", "WALL", "BRICKS", "PILLAR", "TOOL")) return "Ores & Minerals";
        if (has(n, "REDSTONE", "REPEATER", "COMPARATOR", "PISTON", "OBSERVER", "DISPENSER", "DROPPER", "HOPPER",
                "LEVER", "_BUTTON", "PRESSURE_PLATE", "TNT", "TARGET", "DAYLIGHT", "LIGHTNING_ROD", "TRIPWIRE",
                "NOTE_BLOCK", "SCULK_SENSOR", "SCULK_SHRIEKER", "SLIME", "HONEY", "CRAFTER")) return "Redstone";
        if (has(n, "SEEDS", "SAPLING", "BONE_MEAL", "PROPAGULE", "SUGAR_CANE", "BAMBOO", "CACTUS", "KELP", "PUMPKIN",
                "MELON", "HAY_BLOCK", "COCOA", "NETHER_WART", "COMPOSTER", "WHEAT", "VINE", "LILY_PAD", "SPORE_BLOSSOM",
                "AZALEA", "FERN", "MOSS", "TORCHFLOWER", "PITCHER")) return "Farming";
        if (has(n, "WOOL", "CARPET", "BANNER", "_BED", "CANDLE", "_HEAD", "_SKULL", "PAINTING", "ITEM_FRAME",
                "FLOWER_POT", "LANTERN", "TORCH", "_SIGN", "GLASS", "_DYE", "TERRACOTTA", "CONCRETE", "MUSIC_DISC",
                "POTTERY", "_TEMPLATE", "BOOKSHELF", "TULIP", "ORCHID", "DANDELION", "POPPY", "ROSE", "LILAC",
                "PEONY", "ARMOR_STAND", "FLOWER", "CORAL", "BOOK", "SHELF", "CHAIN", "BELL")) return "Decoration";
        if (has(n, "_LOG", "_WOOD", "LEAVES", "DIRT", "SAND", "GRAVEL", "GRASS", "MYCELIUM", "PODZOL", "CLAY",
                "SNOW", "ICE", "NETHERRACK", "END_STONE", "COBBLESTONE", "STONE", "DEEPSLATE", "TUFF", "OBSIDIAN",
                "BASALT", "BLACKSTONE", "MUD", "SOUL_", "CRIMSON_STEM", "WARPED_STEM", "STEM", "SPONGE", "CALCITE",
                "DRIPSTONE", "SCULK", "COBWEB", "ROOTS", "MAGMA_BLOCK")
                && !has(n, "STAIRS", "SLAB", "WALL", "BRICK", "BUTTON", "PRESSURE", "SWORD", "_AXE")) return "Natural";
        if (m.isBlock()) return "Building Blocks";
        return "Miscellaneous";
    }

    public List<Material> items(String category) { return byCategory.getOrDefault(category, List.of()); }
    public Collection<String> categories() { return byCategory.keySet(); }
    public Price price(Material m) { return prices.get(m); }
    public String customName(Material m) { return names.get(m); }
    public boolean sellable(Material m) { Price p = prices.get(m); return p != null && p.sell() > 0; }
}
