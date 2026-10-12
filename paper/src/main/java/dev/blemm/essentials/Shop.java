package dev.blemm.essentials;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/**
 * Survival-obtainable shop catalogue with explicit per-item prices loaded from
 * plugins/BlemmEssentials/prices/*.txt. Each line is MATERIAL=buy,sell.
 */
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
    private final File pricesFolder;
    private final String presetName;

    public Shop(ConfigurationSection cfg, File pricesFolder, String presetName) {
        this.pricesFolder = pricesFolder;
        this.presetName = presetName;
        for (String c : CATEGORIES) byCategory.put(c, new ArrayList<>());
        Set<String> blocked = new HashSet<>();
        if (cfg != null) for (String s : cfg.getStringList("blocked-materials")) blocked.add(s.toUpperCase(Locale.ROOT));
        ConfigurationSection items = cfg == null ? null : cfg.getConfigurationSection("items");
        Map<String, Price> presetPrices = readPreset(new File(pricesFolder, safePresetName(presetName) + ".txt"));

        for (Material m : Material.values()) {
            if (!obtainable(m) || blocked.contains(m.name())) continue;
            String cat = categorize(m);
            ConfigurationSection ov = items == null ? null : items.getConfigurationSection(m.name());
            if (ov != null && ov.getString("category") != null) cat = ov.getString("category");
            Price price = presetPrices.getOrDefault(m.name(), generatedPrice(m));
            if (ov != null && ov.getString("name") != null) names.put(m, ov.getString("name"));
            byCategory.computeIfAbsent(cat, k -> new ArrayList<>()).add(m);
            prices.put(m, price);
        }
        byCategory.values().forEach(l -> l.sort(Comparator.comparing(Enum::name)));
    }

    public static String safePresetName(String name) {
        String cleaned = name == null ? "" : name.trim();
        if (!cleaned.matches("[A-Za-z0-9 _.-]{1,64}") || cleaned.equals(".") || cleaned.equals("..") || cleaned.startsWith(".")) throw new IllegalArgumentException("Preset names may contain letters, numbers, spaces, dots, underscores, and hyphens, but cannot start with a dot.");
        return cleaned;
    }

    public static void ensurePresetFiles(File folder) throws IOException {
        if (!folder.exists() && !folder.mkdirs()) throw new IOException("Could not create prices folder.");
        File defaultFile = new File(folder, "shop.txt");
        if (!defaultFile.exists()) Files.writeString(defaultFile.toPath(), generatedPresetText(), StandardCharsets.UTF_8);
        File activeFile = new File(folder, "active.txt");
        if (!activeFile.exists()) Files.writeString(activeFile.toPath(), "shop\n", StandardCharsets.UTF_8);
    }

    public static String readActivePreset(File folder) {
        File active = new File(folder, "active.txt");
        try {
            String value = Files.readString(active.toPath(), StandardCharsets.UTF_8).trim();
            return safePresetName(value.isEmpty() ? "shop" : value);
        } catch (Exception ignored) { return "shop"; }
    }

    public static List<String> listPresetNames(File folder) {
        File[] files = folder.listFiles((dir, filename) -> filename.toLowerCase(Locale.ROOT).endsWith(".txt")
                && !filename.equalsIgnoreCase("active.txt"));
        if (files == null) return List.of();
        return Arrays.stream(files).map(f -> f.getName().substring(0, f.getName().length() - 4))
                .sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    public static boolean presetExists(File folder, String name) {
        try { return new File(folder, safePresetName(name) + ".txt").isFile(); }
        catch (IllegalArgumentException ex) { return false; }
    }

    public static void activatePreset(File folder, String name) throws IOException {
        String safe = safePresetName(name);
        if (!presetExists(folder, safe)) throw new IOException("No preset named '" + safe + "' exists in the prices folder.");
        Files.writeString(new File(folder, "active.txt").toPath(), safe + "\n", StandardCharsets.UTF_8);
    }

    private static Map<String, Price> readPreset(File file) {
        Map<String, Price> out = new HashMap<>();
        if (!file.isFile()) return out;
        try {
            for (String raw : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
                String line = raw.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                int eq = line.indexOf('=');
                if (eq < 1) continue;
                String material = line.substring(0, eq).trim().toUpperCase(Locale.ROOT);
                String[] values = line.substring(eq + 1).split(",");
                if (values.length != 2) continue;
                try {
                    Material.matchMaterial(material);
                    double buy = Double.parseDouble(values[0].trim());
                    double sell = Double.parseDouble(values[1].trim());
                    if (Material.matchMaterial(material) != null && Double.isFinite(buy) && Double.isFinite(sell) && buy >= 0 && sell >= 0)
                        out.put(material, new Price(buy, sell));
                } catch (NumberFormatException ignored) { }
            }
        } catch (IOException ignored) { }
        return out;
    }

    /** Generated shop preset has a separate buy/sell value for every supported item. */
    private static String generatedPresetText() {
        StringBuilder b = new StringBuilder("# BlemmEssentials shop price preset\n")
                .append("# Format: MATERIAL=buy-price,sell-price\n")
                .append("# Prices are per item. Set either value to 0 to disable that action.\n")
                .append("# Edit this file, then run /admin reload to apply it; switch with /shop preset <name>.\n\n");
        Arrays.stream(Material.values()).filter(Shop::obtainable).sorted(Comparator.comparing(Enum::name)).forEach(m -> {
            Price p = generatedPrice(m);
            b.append(m.name()).append('=').append(format(p.buy())).append(',').append(format(p.sell())).append('\n');
        });
        return b.toString();
    }

    private static String format(double n) {
        if (n == Math.rint(n)) return Long.toString((long)n);
        return String.format(Locale.US, "%.2f", n);
    }

    private static Price generatedPrice(Material m) {
        String n = m.name();
        String cat = categorize(m);
        double base = switch (cat) {
            case "Building Blocks" -> 5;
            case "Natural" -> 3;
            case "Ores & Minerals" -> 35;
            case "Tools" -> 45;
            case "Combat" -> 55;
            case "Food" -> 5;
            case "Farming" -> 4;
            case "Redstone" -> 12;
            case "Decoration" -> 6;
            case "Brewing" -> 18;
            case "Transport" -> 30;
            default -> 10;
        };
        if (n.contains("NETHERITE")) {
            base = n.contains("BLOCK") ? 8500 : n.contains("INGOT") ? 6500 : n.contains("SCRAP") ? 4500 : n.contains("SMITHING_TEMPLATE") ? 4500 :
                    (n.contains("SWORD") || n.contains("PICKAXE") || n.contains("AXE") || n.contains("SHOVEL")
                    || n.contains("HOE") || n.contains("HELMET") || n.contains("CHESTPLATE") || n.contains("LEGGINGS")
                    || n.contains("BOOTS")) ? 8500 : 1200;
        } else if (n.contains("ELYTRA")) base = 9000;
        else if (n.contains("NETHER_STAR")) base = 12000;
        else if (n.contains("DRAGON_EGG")) base = 25000;
        else if (n.contains("TOTEM_OF_UNDYING")) base = 5000;
        else if (n.contains("HEAVY_CORE")) base = 7000;
        else if (n.contains("TRIAL_KEY") || n.contains("OMINOUS_TRIAL_KEY")) base = 500;
        else if (n.contains("DIAMOND")) base = n.contains("BLOCK") ? 2300 : n.contains("SWORD") || n.contains("PICKAXE")
                || n.contains("AXE") || n.contains("SHOVEL") || n.contains("HOE") || n.contains("HELMET")
                || n.contains("CHESTPLATE") || n.contains("LEGGINGS") || n.contains("BOOTS") ? 700 : 250;
        else if (n.contains("EMERALD")) base = n.contains("BLOCK") ? 1300 : 150;
        else if (n.contains("ANCIENT_DEBRIS")) base = 2400;
        else if (n.contains("GOLD_INGOT")) base = 55;
        else if (n.contains("IRON_INGOT")) base = 35;
        else if (n.contains("RAW_IRON")) base = 22;
        else if (n.contains("RAW_GOLD")) base = 30;
        else if (n.contains("BEACON")) base = 6000;
        else if (n.contains("ENCHANTED_GOLDEN_APPLE")) base = 4000;
        else if (n.contains("GOLDEN_APPLE")) base = 250;
        else if (n.contains("SHULKER_BOX")) base = 1800;
        else if (n.contains("SHULKER_SHELL")) base = 900;
        else if (n.contains("ENDER_PEARL")) base = 45;
        else if (n.contains("BLAZE_ROD")) base = 80;
        else if (n.contains("GHAST_TEAR")) base = 180;
        else if (n.contains("WITHER_SKELETON_SKULL")) base = 1200;
        else if (n.contains("PRISMARINE") || n.contains("SPONGE")) base = 70;
        else if (n.contains("COAL")) base = 8;
        else if (n.contains("LAPIS")) base = 18;
        else if (n.contains("COPPER")) base = 12;
        else if (n.contains("WHEAT") || n.contains("CARROT") || n.contains("POTATO")) base = 4;
        else if (n.contains("DIRT") || n.contains("COBBLESTONE") || n.contains("NETHERRACK")) base = 1;
        double sell = Math.max(0, Math.floor(base * 0.25 * 100) / 100);
        return new Price(base, sell);
    }

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
    public String presetName() { return presetName; }
}
