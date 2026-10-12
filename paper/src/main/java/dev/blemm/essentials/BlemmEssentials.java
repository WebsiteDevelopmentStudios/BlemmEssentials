package dev.blemm.essentials;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class BlemmEssentials extends JavaPlugin implements Listener {
    private static final List<String> COMMANDS = List.of("money", "pay", "tpa", "tpahere", "tpaccept", "tpdeny", "rtp",
            "sethome", "home", "delhome", "setwarp", "warp", "delwarp", "help-", "admin",
            "friend", "homefriends", "homevisit", "ah", "shop", "sell", "rank");

    public static final String PREFIX = ChatColor.GOLD + "[Blemm] " + ChatColor.RESET;

    private YamlConfiguration data, warps;
    private File dataFile, warpFile;
    private boolean dirty;
    private NamespacedKey compassKey;
    private Shop shop;
    private Integrations integrations;
    private Commands commands;

    // ---------------------------------------------------------------- lifecycle
    @Override
    public void onEnable() {
        saveDefaultConfig();
        compassKey = new NamespacedKey(this, "menu_compass");
        dataFile = new File(getDataFolder(), "data.yml");
        warpFile = new File(getDataFolder(), "warps.yml");
        data = YamlConfiguration.loadConfiguration(dataFile);
        warps = YamlConfiguration.loadConfiguration(warpFile);
        integrations = new Integrations(this);
        shop = new Shop(getConfig().getConfigurationSection("shop"));
        commands = new Commands(this);
        for (String name : COMMANDS) {
            PluginCommand pc = getCommand(name);
            if (pc != null) { pc.setExecutor(commands); pc.setTabCompleter(commands); }
        }
        Bukkit.getPluginManager().registerEvents(this, this);
        integrations.registerPlaceholders();
        Bukkit.getScheduler().runTaskTimer(this, this::expireAuctions, 20L * 60, 20L * 60);
        Bukkit.getScheduler().runTaskTimer(this, this::flush, 100L, 100L);
        logRecommendations();
        warnLifestealSetup();
        getLogger().info("Shop loaded with " + shop.categories().stream().mapToInt(c -> shop.items(c).size()).sum() + " items.");
    }

    @Override public void onDisable() { dirty = true; flush(); }

    public void reload() {
        reloadConfig();
        shop = new Shop(getConfig().getConfigurationSection("shop"));
    }

    private void logRecommendations() {
        for (String line : missingRecommended()) getLogger().warning("Recommended: " + line);
    }

    /** Geyser and DiscordSRV are optional but recommended. */
    public List<String> missingRecommended() {
        List<String> out = new ArrayList<>();
        if (!integrations.has("Geyser-Spigot") && !integrations.has("floodgate"))
            out.add("install Geyser + Floodgate so Bedrock players can join and have their skins resolved.");
        if (!integrations.has("DiscordSRV"))
            out.add("install DiscordSRV to link chat and auction announcements with Discord.");
        return out;
    }

    // ---------------------------------------------------------------- persistence
    public YamlConfiguration data() { return data; }
    public YamlConfiguration warps() { return warps; }
    public Shop shop() { return shop; }
    public Integrations integrations() { return integrations; }
    public void markDirty() { dirty = true; }

    private void flush() {
        if (!dirty) return;
        dirty = false;
        try { data.save(dataFile); warps.save(warpFile); }
        catch (IOException e) { getLogger().severe("Could not save data: " + e.getMessage()); }
    }

    // ---------------------------------------------------------------- messaging / permissions
    public void msg(CommandSender s, String text) { s.sendMessage(PREFIX + ChatColor.translateAlternateColorCodes('&', text)); }

    public String color(String s) { return ChatColor.translateAlternateColorCodes('&', s); }

    public boolean isAdmin(CommandSender s) {
        if (!(s instanceof Player p)) return true; // console
        return p.isOp() || p.getScoreboardTags().contains(getConfig().getString("admin-tag", "Admin"));
    }

    // ---------------------------------------------------------------- economy
    public String money(double d) { return String.format(Locale.US, "$%,.2f", d); }

    public double balance(UUID id) {
        return data.getDouble("players." + id + ".balance", getConfig().getDouble("starting-balance", 100));
    }

    public void setBalance(UUID id, double v) { data.set("players." + id + ".balance", Math.max(0, v)); markDirty(); }

    public boolean withdraw(UUID id, double amt) {
        if (balance(id) < amt) return false;
        setBalance(id, balance(id) - amt);
        return true;
    }

    public void deposit(UUID id, double amt) { setBalance(id, balance(id) + amt); }

    // ---------------------------------------------------------------- ranks
    public ConfigurationSection ranks() { return getConfig().getConfigurationSection("ranks"); }

    public String defaultRank() {
        ConfigurationSection r = ranks();
        if (r == null) return "member";
        for (String k : r.getKeys(false)) if (r.getBoolean(k + ".default")) return k;
        return r.getKeys(false).stream().findFirst().orElse("member");
    }

    public String rankId(UUID id) {
        String r = data.getString("players." + id + ".rank");
        return (r != null && ranks() != null && ranks().contains(r)) ? r : defaultRank();
    }

    public String rankPrefix(UUID id) {
        ConfigurationSection r = ranks();
        return r == null ? "" : color(r.getString(rankId(id) + ".prefix", ""));
    }

    public int maxHomes(UUID id) {
        ConfigurationSection r = ranks();
        int fallback = getConfig().getInt("max-homes", 3);
        return r == null ? fallback : r.getInt(rankId(id) + ".max-homes", fallback);
    }

    public void applyTabPrefix(Player p) {
        if (getConfig().getBoolean("tab-prefix", true))
            p.playerListName(LegacyComponentSerializer.legacySection().deserialize(rankPrefix(p.getUniqueId()) + p.getName()));
    }

    // ---------------------------------------------------------------- warps / teleport
    public Spot spotOf(String path) { return Spot.read(warps.getConfigurationSection(path)); }

    /** Teleports to a Spot, loading the world through Multiverse-Core when needed. */
    public void teleport(Player p, Spot s) {
        Location l = s.toLocation();
        if (l == null && getConfig().getBoolean("warps.multiverse-load-worlds", true) && integrations.has("Multiverse-Core")) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mv load " + s.world());
            l = s.toLocation();
        }
        if (l == null) { msg(p, "&cThat location's world (" + s.world() + ") is unavailable."); return; }
        p.teleport(l);
    }

    // ---------------------------------------------------------------- Survival world policy and Lifesteal detection
    public boolean isSurvivalWorld(World world) {
        String name = world.getName();
        return name.equalsIgnoreCase("Survival") || name.equalsIgnoreCase("world");
    }
    public boolean isSurvivalWorld(Player player) { return isSurvivalWorld(player.getWorld()); }
    public World lifestealWorld() {
        for (World world : Bukkit.getWorlds()) if (world.getName().equalsIgnoreCase("Lifesteal")) return world;
        return null;
    }
    private void warnLifestealSetup() {
        if (lifestealWorld() != null && !getConfig().getBoolean("lifesteal.setup-complete", false))
            getLogger().warning("A Lifesteal world was detected. An operator must run /setup lifesteal. Independent Lifesteal economy/auction/shop support is not yet available.");
    }
    public boolean isMenuCompass(ItemStack it) {
        return it != null && it.getType() == Material.COMPASS && it.hasItemMeta()
            && it.getItemMeta().getPersistentDataContainer().has(compassKey, PersistentDataType.BYTE);
    }
    public ItemStack makeCompass() {
        ItemStack it = new ItemStack(Material.COMPASS);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(color("&6BlemmEssentials Navigation"));
        meta.setLore(List.of(color("&7Right-click to open server navigation."), color("&8Only works in Survival.")));
        meta.getPersistentDataContainer().set(compassKey, PersistentDataType.BYTE, (byte) 1);
        meta.addEnchant(Enchantment.LUCK_OF_THE_SEA, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        it.setItemMeta(meta);
        return it;
    }
    public boolean hasMenuCompass(Player p) {
        for (ItemStack it : p.getInventory().getContents()) if (isMenuCompass(it)) return true;
        return false;
    }
    public void giveCompassIfMissing(Player p) {
        if (!isSurvivalWorld(p) || hasMenuCompass(p)) return;
        for (ItemStack leftover : p.getInventory().addItem(makeCompass()).values())
            p.getWorld().dropItemNaturally(p.getLocation(), leftover);
    }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCompassUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (!isMenuCompass(e.getItem())) return;
        e.setCancelled(true);
        if (!isSurvivalWorld(e.getPlayer())) {
            msg(e.getPlayer(), "&cThe BlemmEssentials compass only works in Survival.");
            return;
        }
        openCompass(e.getPlayer());
    }
    public void openCompass(Player p) {
        if (!isSurvivalWorld(p)) { msg(p, "&cNavigation is only available in Survival."); return; }
        Menu m = new Menu(Menu.Type.COMPASS, null, 0, 27, color("&6BlemmEssentials Navigation"));
        m.getInventory().setItem(10, icon(Material.EMERALD, "&aBalance", "&7" + money(balance(p.getUniqueId()))));
        m.getInventory().setItem(11, icon(Material.CHEST, "&eShop", "&7Browse categories"));
        m.getInventory().setItem(12, icon(Material.GOLD_INGOT, "&6Auction House", "&7Player listings"));
        m.getInventory().setItem(13, icon(Material.RED_BED, "&cHomes", "&7Your saved homes"));
        m.getInventory().setItem(14, icon(Material.ENDER_PEARL, "&5Warps", "&7Available warps"));
        m.getInventory().setItem(15, icon(Material.PLAYER_HEAD, "&bFriends", "&7Your friends"));
        p.openInventory(m.getInventory());
    }

    // ---------------------------------------------------------------- per-world command blocking
    public boolean disabledHere(Player p, String key) {
        if (isAdmin(p) || p.hasPermission("blemmessentials.bypass.worlds")) return false;
        ConfigurationSection s = getConfig().getConfigurationSection("disabled-commands");
        if (s == null) return false;
        for (String world : new String[]{"*", p.getWorld().getName()}) {
            for (String c : s.getStringList(world)) if (c.equalsIgnoreCase(key)) return true;
        }
        return false;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        String raw = e.getMessage().substring(1).split(" ")[0].toLowerCase();
        if (raw.contains(":")) raw = raw.substring(raw.indexOf(':') + 1);
        PluginCommand pc = Bukkit.getPluginCommand(raw);
        if (pc == null || pc.getPlugin() != this) return;
        if (!isSurvivalWorld(e.getPlayer()) && !raw.equalsIgnoreCase("setup")) {
            e.setCancelled(true);
            msg(e.getPlayer(), "&cBlemmEssentials commands are only available in Survival.");
            return;
        }
        if (disabledHere(e.getPlayer(), pc.getName())) {
            e.setCancelled(true);
            msg(e.getPlayer(), "&cThis command is disabled in this world.");
        }
    }

    public ItemStack icon(Material mat, String name, String... lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(color(name));
        List<String> l = new ArrayList<>();
        for (String s : lore) l.add(color(s));
        m.setLore(l);
        it.setItemMeta(m);
        return it;
    }

    // ---------------------------------------------------------------- shop menus
    public void openShopCategories(Player p) {
        Menu m = new Menu(Menu.Type.SHOP_CATEGORIES, null, 0, 27, color("&6Shop - Categories"));
        Material[] icons = {Material.BRICKS, Material.OAK_LOG, Material.DIAMOND, Material.IRON_PICKAXE, Material.IRON_SWORD,
                Material.BREAD, Material.WHEAT, Material.REDSTONE, Material.PAINTING, Material.BREWING_STAND,
                Material.MINECART, Material.CHEST};
        int i = 0;
        for (String c : shop.categories()) {
            if (shop.items(c).isEmpty() || i >= icons.length) continue;
            m.getInventory().setItem(i, icon(icons[Math.min(i, icons.length - 1)], "&e" + c, "&7" + shop.items(c).size() + " items"));
            i++;
        }
        p.openInventory(m.getInventory());
    }

    public void openShopItems(Player p, String category, int page) {
        List<Material> list = shop.items(category);
        int pages = Math.max(1, (list.size() + 44) / 45);
        page = Math.max(0, Math.min(page, pages - 1));
        Menu m = new Menu(Menu.Type.SHOP_ITEMS, category, page, 54, color("&6" + category + " &7(" + (page + 1) + "/" + pages + ")"));
        for (int i = 0; i < 45; i++) {
            int idx = page * 45 + i;
            if (idx >= list.size()) break;
            Material mat = list.get(idx);
            Shop.Price pr = shop.price(mat);
            ItemStack it = new ItemStack(mat);
            ItemMeta meta = it.getItemMeta();
            if (shop.customName(mat) != null) meta.setDisplayName(color(shop.customName(mat)));
            meta.setLore(List.of(
                    color(pr.buy() > 0 ? "&aBuy: &f" + money(pr.buy()) + " &7(left-click, shift = 64)" : "&8Not for sale"),
                    color(pr.sell() > 0 ? "&cSell: &f" + money(pr.sell()) + " &7(right-click, shift = 64)" : "&8Not purchasable")));
            it.setItemMeta(meta);
            m.getInventory().setItem(i, it);
        }
        m.getInventory().setItem(45, icon(Material.ARROW, "&ePrevious page"));
        m.getInventory().setItem(49, icon(Material.BARRIER, "&cBack to categories"));
        m.getInventory().setItem(53, icon(Material.ARROW, "&eNext page"));
        p.openInventory(m.getInventory());
    }

    public void openAuctions(Player p, int page) {
        ConfigurationSection list = data.getConfigurationSection("auctions.list");
        List<String> ids = list == null ? new ArrayList<>() : new ArrayList<>(list.getKeys(false));
        ids.sort(Comparator.comparingInt(Integer::parseInt));
        int pages = Math.max(1, (ids.size() + 44) / 45);
        page = Math.max(0, Math.min(page, pages - 1));
        Menu m = new Menu(Menu.Type.AUCTIONS, null, page, 54, color("&6Auction House &7(" + (page + 1) + "/" + pages + ")"));
        for (int i = 0; i < 45; i++) {
            int idx = page * 45 + i;
            if (idx >= ids.size()) break;
            String id = ids.get(idx);
            ItemStack it = list.getItemStack(id + ".item");
            if (it == null) continue;
            it = it.clone();
            ItemMeta meta = it.getItemMeta();
            List<String> lore = meta.hasLore() && meta.getLore() != null ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            OfflinePlayer seller = Bukkit.getOfflinePlayer(UUID.fromString(list.getString(id + ".seller")));
            lore.add(color("&8-----"));
            lore.add(color("&7ID: &f" + id + " &7Seller: &f" + seller.getName()));
            lore.add(color("&aPrice: &f" + money(list.getDouble(id + ".price")) + " &7(click to buy)"));
            meta.setLore(lore);
            it.setItemMeta(meta);
            m.getInventory().setItem(i, it);
        }
        m.getInventory().setItem(45, icon(Material.ARROW, "&ePrevious page"));
        m.getInventory().setItem(53, icon(Material.ARROW, "&eNext page"));
        p.openInventory(m.getInventory());
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Menu menu)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() == null || e.getClickedInventory() != e.getView().getTopInventory()) return;
        int slot = e.getSlot();
        switch (menu.type) {
            case SHOP_CATEGORIES -> {
                ItemStack it = e.getCurrentItem();
                if (it == null || !it.hasItemMeta()) return;
                String name = ChatColor.stripColor(it.getItemMeta().getDisplayName());
                for (String c : shop.categories()) if (c.equals(name)) { openShopItems(p, c, 0); return; }
            }
            case SHOP_ITEMS -> {
                if (slot == 45) { openShopItems(p, menu.category, menu.page - 1); return; }
                if (slot == 53) { openShopItems(p, menu.category, menu.page + 1); return; }
                if (slot == 49) { openShopCategories(p); return; }
                if (slot >= 45) return;
                int idx = menu.page * 45 + slot;
                List<Material> list = shop.items(menu.category);
                if (idx >= list.size()) return;
                Material mat = list.get(idx);
                int amt = e.isShiftClick() ? 64 : 1;
                if (e.isLeftClick()) commands.buy(p, mat, amt); else if (e.isRightClick()) commands.sellMaterial(p, mat, amt);
            }
            case AUCTIONS -> {
                if (slot == 45) { openAuctions(p, menu.page - 1); return; }
                if (slot == 53) { openAuctions(p, menu.page + 1); return; }
                if (slot >= 45 || e.getCurrentItem() == null) return;
                ItemMeta meta = e.getCurrentItem().getItemMeta();
                if (meta == null || meta.getLore() == null) return;
                for (String line : meta.getLore()) {
                    String s = ChatColor.stripColor(line);
                    if (s.startsWith("ID: ")) {
                        String id = s.substring(4).split(" ")[0];
                        p.closeInventory();
                        commands.auctionBuy(p, id);
                        return;
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- auctions
    private void expireAuctions() {
        ConfigurationSection list = data.getConfigurationSection("auctions.list");
        if (list == null) return;
        long now = System.currentTimeMillis();
        for (String id : new ArrayList<>(list.getKeys(false))) {
            if (list.getLong(id + ".expires") <= now) {
                returnAuctionItem(id);
            }
        }
    }

    public void returnAuctionItem(String id) {
        String base = "auctions.list." + id;
        ItemStack item = data.getItemStack(base + ".item");
        String seller = data.getString(base + ".seller");
        if (item != null && seller != null) {
            List<ItemStack> mail = new ArrayList<>();
            List<?> existing = data.getList("auctions.mail." + seller);
            if (existing != null) for (Object o : existing) if (o instanceof ItemStack s) mail.add(s);
            mail.add(item);
            data.set("auctions.mail." + seller, mail);
        }
        data.set(base, null);
        markDirty();
    }

    // ---------------------------------------------------------------- join / chat
    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        String base = "players." + p.getUniqueId();
        if (!data.contains(base + ".balance")) {
            data.set(base + ".balance", getConfig().getDouble("starting-balance", 100));
            data.set(base + ".name", p.getName());
            markDirty();
        }
        data.set(base + ".name", p.getName());
        applyTabPrefix(p);
        integrations.loadBedrockSkin(p);
        giveCompassIfMissing(p);
        if (isAdmin(p)) for (String line : missingRecommended()) msg(p, "&eRecommended: " + line);
        List<?> mail = data.getList("auctions.mail." + p.getUniqueId());
        if (mail != null && !mail.isEmpty()) msg(p, "&eYou have unclaimed auction items. Use &f/ah claim&e.");
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        if (isSurvivalWorld(e.getPlayer())) giveCompassIfMissing(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onChat(AsyncChatEvent e) {
        if (!getConfig().getBoolean("chat-prefix", true)) return;
        final String prefix = rankPrefix(e.getPlayer().getUniqueId());
        if (prefix.isEmpty()) return;
        e.renderer((src, name, message, viewer) -> Component.empty()
                .append(LegacyComponentSerializer.legacySection().deserialize(prefix))
                .append(name).append(Component.text(": ")).append(message));
    }
}
