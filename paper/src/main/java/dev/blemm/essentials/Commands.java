package dev.blemm.essentials;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.stream.Collectors;

public class Commands implements CommandExecutor, TabCompleter {
    private record Request(UUID from, boolean here, long expires) {}

    private final BlemmEssentials pl;
    /** target -> pending requests */
    private final Map<UUID, List<Request>> requests = new HashMap<>();
    private final Random rnd = new Random();

    public Commands(BlemmEssentials pl) { this.pl = pl; }

    private boolean playerOnly(CommandSender s) {
        if (s instanceof Player) return false;
        pl.msg(s, "&cThis command can only be used by players.");
        return true;
    }

    private Double parseAmount(CommandSender s, String in) {
        try {
            double d = Double.parseDouble(in);
            if (d <= 0 || Double.isNaN(d) || Double.isInfinite(d)) throw new NumberFormatException();
            return Math.floor(d * 100) / 100;
        } catch (NumberFormatException e) { pl.msg(s, "&cInvalid amount."); return null; }
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        switch (cmd.getName().toLowerCase()) {
            case "money" -> money(s, a);
            case "pay" -> pay(s, a);
            case "tpa" -> tpa(s, a, false);
            case "tpahere" -> tpa(s, a, true);
            case "tpaccept" -> tpRespond(s, a, true);
            case "tpdeny" -> tpRespond(s, a, false);
            case "rtp" -> rtp(s);
            case "sethome" -> setHome(s, a);
            case "home" -> home(s, a);
            case "delhome" -> delHome(s, a);
            case "setwarp" -> setWarp(s, a);
            case "warp" -> warp(s, a);
            case "delwarp" -> delWarp(s, a);
            case "help-" -> help(s);
            case "admin" -> admin(s, a);
            case "friend" -> friend(s, a);
            case "homefriends" -> homeFriends(s, a);
            case "homevisit" -> homeVisit(s, a);
            case "ah" -> ah(s, a);
            case "shop" -> shopCommand(s, a);
            case "sell" -> sell(s, a);
            case "rank" -> rank(s, a);
            case "setup" -> setup(s, a);
            case "lobby" -> lobby(s);
            default -> { return false; }
        }
        return true;
    }

    private void lobby(CommandSender sender) {
        if (playerOnly(sender)) return;
        pl.selectCarbonLobby((Player) sender);
        pl.msg(sender, "&aLobby chat channel selected.");
    }

    private void setup(CommandSender sender, String[] args) {
        if (!pl.isAdmin(sender)) { pl.msg(sender, "&cOnly an owner/operator can run setup."); return; }
        if (args.length != 1 || !args[0].equalsIgnoreCase("lifesteal")) {
            pl.msg(sender, "&cUsage: /setup lifesteal"); return;
        }
        World world = pl.lifestealWorld();
        if (world == null) {
            pl.msg(sender, "&cNo world named Lifesteal is loaded. Create/load that world, then run /setup lifesteal again."); return;
        }
        pl.msg(sender, "&eLifesteal world detected: &f" + world.getName());
        pl.msg(sender, "&cSetup is not complete: this build does not yet isolate Lifesteal balances, auctions, or shop data.");
        pl.msg(sender, "&7No existing player data was changed.");
    }

    private void shopCommand(CommandSender sender, String[] args) {
        if (args.length == 0) {
            if (!playerOnly(sender)) pl.openShopCategories((Player) sender);
            return;
        }
        if (!args[0].equalsIgnoreCase("preset")) {
            pl.msg(sender, "&cUsage: /shop [preset [name]]");
            return;
        }
        if (!pl.isAdmin(sender)) {
            pl.msg(sender, "&cOnly an owner/operator can change the active shop preset.");
            return;
        }
        if (args.length == 1) {
            pl.msg(sender, "&eAvailable shop presets: &f" + String.join(", ", Shop.listPresetNames(pl.pricesFolder())));
            pl.msg(sender, "&7Active preset: &f" + pl.activeShopPreset());
            pl.msg(sender, "&7Use /shop preset <name> to switch presets.");
            return;
        }
        String presetName = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        if (!pl.activateShopPreset(presetName)) {
            pl.msg(sender, "&cCould not load that preset. Check its name and make sure prices/" + presetName + ".txt exists.");
            return;
        }
        pl.msg(sender, "&aShop preset changed to &f" + pl.activeShopPreset() + "&a.");
    }

    // ------------------------------------------------------------ economy
    private void money(CommandSender s, String[] a) {
        if (a.length == 0) {
            if (playerOnly(s)) return;
            pl.msg(s, "Balance: &a" + pl.money(pl.balance(((Player) s).getUniqueId())));
            return;
        }
        OfflinePlayer t = Bukkit.getOfflinePlayerIfCached(a[0]);
        if (t == null) { pl.msg(s, "&cUnknown player."); return; }
        pl.msg(s, t.getName() + "'s balance: &a" + pl.money(pl.balance(t.getUniqueId())));
    }

    private void pay(CommandSender s, String[] a) {
        if (playerOnly(s)) return;
        Player p = (Player) s;
        if (a.length < 2) { pl.msg(s, "&cUsage: /pay <player> <amount>"); return; }
        Player t = Bukkit.getPlayerExact(a[0]);
        if (t == null || t.equals(p)) { pl.msg(s, "&cThat player is not online."); return; }
        Double amt = parseAmount(s, a[1]);
        if (amt == null) return;
        if (!pl.withdraw(p.getUniqueId(), amt)) { pl.msg(s, "&cNot enough money."); return; }
        pl.deposit(t.getUniqueId(), amt);
        pl.msg(p, "Sent &a" + pl.money(amt) + "&r to " + t.getName());
        pl.msg(t, p.getName() + " sent you &a" + pl.money(amt));
    }

    // ------------------------------------------------------------ teleport requests
    private void tpa(CommandSender s, String[] a, boolean here) {
        if (playerOnly(s)) return;
        Player p = (Player) s;
        if (a.length < 1) { pl.msg(s, "&cUsage: /" + (here ? "tpahere" : "tpa") + " <player>"); return; }
        Player t = Bukkit.getPlayerExact(a[0]);
        if (t == null || t.equals(p)) { pl.msg(s, "&cThat player is not online."); return; }
        long exp = System.currentTimeMillis() + pl.getConfig().getInt("tpa-timeout-seconds", 60) * 1000L;
        List<Request> l = requests.computeIfAbsent(t.getUniqueId(), k -> new ArrayList<>());
        l.removeIf(r -> r.from().equals(p.getUniqueId()));
        l.add(new Request(p.getUniqueId(), here, exp));
        pl.msg(p, "Request sent to " + t.getName() + ".");
        pl.msg(t, p.getName() + (here ? " wants you to teleport to them." : " wants to teleport to you.")
                + " &a/tpaccept &7or &c/tpdeny");
    }

    private void tpRespond(CommandSender s, String[] a, boolean accept) {
        if (playerOnly(s)) return;
        Player p = (Player) s;
        List<Request> l = requests.getOrDefault(p.getUniqueId(), new ArrayList<>());
        l.removeIf(r -> r.expires() < System.currentTimeMillis());
        Request pick = null;
        for (Request r : l) {
            Player from = Bukkit.getPlayer(r.from());
            if (from == null) continue;
            if (a.length == 0 || from.getName().equalsIgnoreCase(a[0])) { pick = r; break; }
        }
        if (pick == null) { pl.msg(p, "&cNo pending request."); return; }
        l.remove(pick);
        Player from = Bukkit.getPlayer(pick.from());
        if (!accept) { pl.msg(p, "Request denied."); pl.msg(from, p.getName() + " denied your request."); return; }
        if (pick.here()) p.teleport(from.getLocation()); else from.teleport(p.getLocation());
        pl.msg(p, "Teleport request accepted."); pl.msg(from, p.getName() + " accepted your request.");
    }

    // ------------------------------------------------------------ rtp
    private void rtp(CommandSender s) {
        if (playerOnly(s)) return;
        Player p = (Player) s;
        if (!p.hasPermission("blemmessentials.rtp")) { pl.msg(p, "&cNo permission."); return; }
        World w = p.getWorld();
        int min = pl.getConfig().getInt("rtp.minimum-radius", 100), max = pl.getConfig().getInt("rtp.maximum-radius", 5000);
        int tries = pl.getConfig().getInt("rtp.attempts", 32);
        Location c = w.getSpawnLocation();
        for (int i = 0; i < tries; i++) {
            double ang = rnd.nextDouble() * Math.PI * 2, r = min + rnd.nextDouble() * Math.max(1, max - min);
            int x = (int) (c.getX() + Math.cos(ang) * r), z = (int) (c.getZ() + Math.sin(ang) * r);
            Location l = safeSpot(w, x, z);
            if (l != null) { p.teleport(l); pl.msg(p, "Teleported to &f" + x + ", " + l.getBlockY() + ", " + z); return; }
        }
        pl.msg(p, "&cCould not find a safe spot. Try again.");
    }

    private Location safeSpot(World w, int x, int z) {
        int y;
        if (w.getEnvironment() == World.Environment.NETHER) {
            y = -1;
            for (int yy = 100; yy > 32; yy--) {
                if (w.getBlockAt(x, yy, z).getType().isAir() && w.getBlockAt(x, yy + 1, z).getType().isAir()
                        && w.getBlockAt(x, yy - 1, z).getType().isSolid()) { y = yy; break; }
            }
            if (y < 0) return null;
        } else y = w.getHighestBlockYAt(x, z) + 1;
        Material below = w.getBlockAt(x, y - 1, z).getType();
        if (!below.isSolid() || below == Material.LAVA || below == Material.MAGMA_BLOCK || below == Material.CACTUS
                || below == Material.FIRE) return null;
        if (!w.getBlockAt(x, y, z).getType().isAir() && w.getBlockAt(x, y, z).isLiquid()) return null;
        return new Location(w, x + 0.5, y, z + 0.5);
    }

    // ------------------------------------------------------------ homes
    private String homePath(UUID id, String name) { return "players." + id + ".homes." + name.toLowerCase(); }

    private void setHome(CommandSender s, String[] a) {
        if (playerOnly(s)) return;
        Player p = (Player) s;
        String name = a.length > 0 ? a[0] : "home";
        ConfigurationSection homes = pl.data().getConfigurationSection("players." + p.getUniqueId() + ".homes");
        int count = homes == null ? 0 : homes.getKeys(false).size();
        boolean exists = homes != null && homes.contains(name.toLowerCase());
        int max = pl.maxHomes(p.getUniqueId());
        if (!exists && count >= max) { pl.msg(p, "&cYou reached your home limit (" + max + ") for rank " + pl.rankId(p.getUniqueId()) + "."); return; }
        Spot.of(p.getLocation()).write(pl.data().createSection(homePath(p.getUniqueId(), name)));
        pl.markDirty();
        pl.msg(p, "Home &f" + name + "&r saved.");
    }

    private void home(CommandSender s, String[] a) {
        if (playerOnly(s)) return;
        Player p = (Player) s;
        ConfigurationSection homes = pl.data().getConfigurationSection("players." + p.getUniqueId() + ".homes");
        if (homes == null || homes.getKeys(false).isEmpty()) { pl.msg(p, "&cYou have no homes. Use /sethome."); return; }
        if (a.length == 0 && homes.getKeys(false).size() > 1) {
            pl.msg(p, "Homes: &f" + String.join(", ", homes.getKeys(false))); return;
        }
        String name = a.length > 0 ? a[0].toLowerCase() : homes.getKeys(false).iterator().next();
        Spot sp = Spot.read(homes.getConfigurationSection(name));
        if (sp == null) { pl.msg(p, "&cNo such home."); return; }
        pl.teleport(p, sp);
    }

    private void delHome(CommandSender s, String[] a) {
        if (playerOnly(s)) return;
        Player p = (Player) s;
        if (a.length < 1) { pl.msg(p, "&cUsage: /delhome <name>"); return; }
        if (!pl.data().contains(homePath(p.getUniqueId(), a[0]))) { pl.msg(p, "&cNo such home."); return; }
        pl.data().set(homePath(p.getUniqueId(), a[0]), null);
        pl.markDirty();
        pl.msg(p, "Home deleted.");
    }

    // ------------------------------------------------------------ warps
    private boolean adminOnly(CommandSender s) {
        if (pl.isAdmin(s)) return false;
        pl.msg(s, "&cAdmins only."); return true;
    }

    private void setWarp(CommandSender s, String[] a) {
        if (playerOnly(s) || adminOnly(s)) return;
        Player p = (Player) s;
        if (a.length < 1) { pl.msg(p, "&cUsage: /setwarp <name>"); return; }
        String name = a[0].toLowerCase();
        // The warp remembers the world this command was run in.
        Spot.of(p.getLocation()).write(pl.warps().createSection("warps." + name));
        pl.markDirty();
        pl.msg(p, "Warp &f" + name + "&r set for world &f" + p.getWorld().getName() + "&r. It can only be used in that world.");
    }

    private void warp(CommandSender s, String[] a) {
        if (playerOnly(s)) return;
        Player p = (Player) s;
        ConfigurationSection ws = pl.warps().getConfigurationSection("warps");
        if (a.length == 0 || a[0].equalsIgnoreCase("list")) {
            if (ws == null || ws.getKeys(false).isEmpty()) { pl.msg(p, "No warps set."); return; }
            StringBuilder sb = new StringBuilder();
            for (String k : ws.getKeys(false)) {
                Spot sp = Spot.read(ws.getConfigurationSection(k));
                boolean here = sp != null && sp.world().equals(p.getWorld().getName());
                sb.append(here ? "&a" : "&8").append(k).append("&7(").append(sp == null ? "?" : sp.world()).append(") ");
            }
            pl.msg(p, "Warps (&agreen&r = usable here): " + sb);
            return;
        }
        Spot sp = Spot.read(pl.warps().getConfigurationSection("warps." + a[0].toLowerCase()));
        if (sp == null) { pl.msg(p, "&cNo such warp."); return; }
        if (pl.getConfig().getBoolean("warps.require-same-world", true) && !sp.world().equals(p.getWorld().getName())) {
            pl.msg(p, "&cThis warp can only be used in world &f" + sp.world() + "&c."); return;
        }
        pl.teleport(p, sp);
    }

    private void delWarp(CommandSender s, String[] a) {
        if (adminOnly(s)) return;
        if (a.length < 1) { pl.msg(s, "&cUsage: /delwarp <name>"); return; }
        if (!pl.warps().contains("warps." + a[0].toLowerCase())) { pl.msg(s, "&cNo such warp."); return; }
        pl.warps().set("warps." + a[0].toLowerCase(), null);
        pl.markDirty();
        pl.msg(s, "Warp deleted.");
    }

    // ------------------------------------------------------------ help / admin / ranks
    private void help(CommandSender s) {
        pl.msg(s, "&6Commands: &f/money /pay /tpa /tpahere /tpaccept /tpdeny /rtp /sethome /home /delhome /warp "
                + "/friend /homefriends /homevisit /shop /sell /ah /rank");
    }

    private void admin(CommandSender s, String[] a) {
        if (adminOnly(s)) return;
        if (a.length == 0 || a[0].equalsIgnoreCase("help")) {
            pl.msg(s, "&6/admin balance <set|add> <player> <amount> | tag <add|remove> <player> | reload");
            return;
        }
        switch (a[0].toLowerCase()) {
            case "reload" -> { pl.reload(); pl.msg(s, "Reloaded."); }
            case "tag" -> {
                Player t = a.length > 2 ? Bukkit.getPlayerExact(a[2]) : null;
                if (t == null) { pl.msg(s, "&cUsage: /admin tag <add|remove> <online-player>"); return; }
                String tag = pl.getConfig().getString("admin-tag", "Admin");
                if (a[1].equalsIgnoreCase("add")) t.addScoreboardTag(tag); else t.removeScoreboardTag(tag);
                pl.msg(s, "Updated tag for " + t.getName());
            }
            case "balance" -> {
                if (a.length < 4) { pl.msg(s, "&cUsage: /admin balance <set|add> <player> <amount>"); return; }
                OfflinePlayer t = Bukkit.getOfflinePlayerIfCached(a[2]);
                Double amt;
                try { amt = Double.parseDouble(a[3]); } catch (NumberFormatException e) { pl.msg(s, "&cInvalid amount."); return; }
                if (t == null) { pl.msg(s, "&cUnknown player."); return; }
                if (a[1].equalsIgnoreCase("set")) pl.setBalance(t.getUniqueId(), amt); else pl.deposit(t.getUniqueId(), amt);
                pl.msg(s, t.getName() + " now has &a" + pl.money(pl.balance(t.getUniqueId())));
            }
            default -> pl.msg(s, "&cUnknown admin command.");
        }
    }

    private void rank(CommandSender s, String[] a) {
        ConfigurationSection ranks = pl.ranks();
        if (ranks == null) { pl.msg(s, "&cNo ranks configured."); return; }
        if (a.length == 0 || a[0].equalsIgnoreCase("info")) {
            OfflinePlayer t = a.length > 1 ? Bukkit.getOfflinePlayerIfCached(a[1]) : (s instanceof Player p ? p : null);
            if (t == null) { pl.msg(s, "&cSpecify a player."); return; }
            pl.msg(s, t.getName() + " is rank &f" + pl.rankId(t.getUniqueId()) + "&r (max homes " + pl.maxHomes(t.getUniqueId()) + ")");
        } else if (a[0].equalsIgnoreCase("list")) {
            pl.msg(s, "Ranks: &f" + String.join(", ", ranks.getKeys(false)));
        } else if (a[0].equalsIgnoreCase("set")) {
            if (adminOnly(s)) return;
            if (a.length < 3) { pl.msg(s, "&cUsage: /rank set <player> <rank>"); return; }
            OfflinePlayer t = Bukkit.getOfflinePlayerIfCached(a[1]);
            String r = a[2].toLowerCase();
            if (t == null || !ranks.contains(r)) { pl.msg(s, "&cUnknown player or rank."); return; }
            pl.data().set("players." + t.getUniqueId() + ".rank", r);
            pl.markDirty();
            if (t instanceof Player online) pl.applyTabPrefix(online);
            pl.msg(s, t.getName() + " is now rank &f" + r);
        } else pl.msg(s, "&cUsage: /rank [list|info [player]|set <player> <rank>]");
    }

    // ------------------------------------------------------------ friends
    private List<String> friendsOf(UUID id) { return pl.data().getStringList("players." + id + ".friends"); }

    private void friend(CommandSender s, String[] a) {
        if (playerOnly(s)) return;
        Player p = (Player) s;
        if (a.length == 0) { pl.msg(p, "&cUsage: /friend <add|remove|list> [player]"); return; }
        List<String> f = new ArrayList<>(friendsOf(p.getUniqueId()));
        switch (a[0].toLowerCase()) {
            case "list" -> pl.msg(p, f.isEmpty() ? "You have no friends yet. Use /friend add <player>."
                    : "Friends: &f" + f.stream().map(u -> String.valueOf(Bukkit.getOfflinePlayer(UUID.fromString(u)).getName()))
                    .collect(Collectors.joining(", ")));
            case "add", "remove" -> {
                OfflinePlayer t = a.length > 1 ? Bukkit.getOfflinePlayerIfCached(a[1]) : null;
                if (t == null || t.getUniqueId().equals(p.getUniqueId())) { pl.msg(p, "&cUnknown player."); return; }
                if (a[0].equalsIgnoreCase("add")) { if (!f.contains(t.getUniqueId().toString())) f.add(t.getUniqueId().toString()); }
                else f.remove(t.getUniqueId().toString());
                pl.data().set("players." + p.getUniqueId() + ".friends", f);
                pl.markDirty();
                pl.msg(p, "Friends updated.");
            }
            default -> pl.msg(p, "&cUsage: /friend <add|remove|list> [player]");
        }
    }

    private void homeFriends(CommandSender s, String[] a) {
        if (playerOnly(s)) return;
        Player p = (Player) s;
        String path = "players." + p.getUniqueId() + ".homefriends";
        boolean now = a.length == 0 || a[0].equalsIgnoreCase("toggle") ? !pl.data().getBoolean(path) : a[0].equalsIgnoreCase("on");
        pl.data().set(path, now);
        pl.markDirty();
        pl.msg(p, "Friends can " + (now ? "&anow" : "&cno longer") + "&r visit your homes.");
    }

    private void homeVisit(CommandSender s, String[] a) {
        if (playerOnly(s)) return;
        Player p = (Player) s;
        if (a.length < 1) { pl.msg(p, "&cUsage: /homevisit <friend> [home]"); return; }
        OfflinePlayer t = Bukkit.getOfflinePlayerIfCached(a[0]);
        if (t == null) { pl.msg(p, "&cUnknown player."); return; }
        if (!friendsOf(t.getUniqueId()).contains(p.getUniqueId().toString()) || !pl.data().getBoolean("players." + t.getUniqueId() + ".homefriends")) {
            pl.msg(p, "&cThey are not your friend or do not allow visits."); return;
        }
        ConfigurationSection homes = pl.data().getConfigurationSection("players." + t.getUniqueId() + ".homes");
        if (homes == null || homes.getKeys(false).isEmpty()) { pl.msg(p, "&cThey have no homes."); return; }
        String name = a.length > 1 ? a[1].toLowerCase() : homes.getKeys(false).iterator().next();
        Spot sp = Spot.read(homes.getConfigurationSection(name));
        if (sp == null) { pl.msg(p, "&cNo such home."); return; }
        pl.teleport(p, sp);
    }

    // ------------------------------------------------------------ shop / sell
    public void buy(Player p, Material m, int amt) {
        Shop.Price pr = pl.shop().price(m);
        if (pr == null || pr.buy() <= 0) { pl.msg(p, "&cThat item is not for sale."); return; }
        double cost = pr.buy() * amt;
        if (!pl.withdraw(p.getUniqueId(), cost)) { pl.msg(p, "&cYou need " + pl.money(cost) + "."); return; }
        Map<Integer, ItemStack> left = p.getInventory().addItem(new ItemStack(m, amt));
        left.values().forEach(it -> p.getWorld().dropItemNaturally(p.getLocation(), it));
        pl.msg(p, "Bought " + amt + "x " + m.name().toLowerCase() + " for &a" + pl.money(cost));
    }

    private boolean plain(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return true;
        var m = it.getItemMeta();
        return !m.hasDisplayName() && !m.hasLore() && !m.hasEnchants();
    }

    public void sellMaterial(Player p, Material m, int amt) {
        Shop.Price pr = pl.shop().price(m);
        if (pr == null || pr.sell() <= 0) { pl.msg(p, "&cThat item cannot be sold."); return; }
        int removed = 0;
        for (ItemStack it : p.getInventory().getStorageContents()) {
            if (removed >= amt) break;
            if (it == null || it.getType() != m || !plain(it)) continue;
            int take = Math.min(it.getAmount(), amt - removed);
            it.setAmount(it.getAmount() - take);
            removed += take;
        }
        if (removed == 0) { pl.msg(p, "&cYou do not have that item."); return; }
        double earn = pr.sell() * removed;
        pl.deposit(p.getUniqueId(), earn);
        pl.msg(p, "Sold " + removed + "x " + m.name().toLowerCase() + " for &a" + pl.money(earn));
    }

    private void sell(CommandSender s, String[] a) {
        if (playerOnly(s)) return;
        Player p = (Player) s;
        if (a.length > 0 && a[0].equalsIgnoreCase("all")) {
            double total = 0; int count = 0;
            for (ItemStack it : p.getInventory().getStorageContents()) {
                if (it == null || pl.isMenuCompass(it) || !pl.shop().sellable(it.getType()) || !plain(it)) continue;
                total += pl.shop().price(it.getType()).sell() * it.getAmount();
                count += it.getAmount();
                it.setAmount(0);
            }
            if (count == 0) { pl.msg(p, "&cNothing sellable in your inventory."); return; }
            pl.deposit(p.getUniqueId(), total);
            pl.msg(p, "Sold " + count + " items for &a" + pl.money(total));
            return;
        }
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (pl.isMenuCompass(hand) || hand.getType().isAir() || !pl.shop().sellable(hand.getType()) || !plain(hand)) { pl.msg(p, "&cThat item cannot be sold."); return; }
        sellMaterial(p, hand.getType(), hand.getAmount());
    }

    // ------------------------------------------------------------ auction house
    private void ah(CommandSender s, String[] a) {
        if (playerOnly(s)) return;
        Player p = (Player) s;
        if (a.length == 0) { pl.openAuctions(p, 0); return; }
        switch (a[0].toLowerCase()) {
            case "sell" -> {
                if (a.length < 2) { pl.msg(p, "&cUsage: /ah sell <price> [amount]"); return; }
                Double price = parseAmount(p, a[1]);
                if (price == null) return;
                ItemStack hand = p.getInventory().getItemInMainHand();
                if (pl.isMenuCompass(hand)) { pl.msg(p, "&cThe navigation compass cannot be auctioned."); return; }
                if (hand.getType().isAir()) { pl.msg(p, "&cHold an item."); return; }
                int amt = hand.getAmount();
                if (a.length > 2) { try { amt = Math.min(amt, Math.max(1, Integer.parseInt(a[2]))); } catch (NumberFormatException e) { pl.msg(p, "&cInvalid amount."); return; } }
                ItemStack listed = hand.clone();
                listed.setAmount(amt);
                hand.setAmount(hand.getAmount() - amt);
                int id = pl.data().getInt("auctions.next-id", 1);
                pl.data().set("auctions.next-id", id + 1);
                String base = "auctions.list." + id;
                pl.data().set(base + ".seller", p.getUniqueId().toString());
                pl.data().set(base + ".price", price);
                pl.data().set(base + ".item", listed);
                pl.data().set(base + ".expires", System.currentTimeMillis() + pl.getConfig().getLong("auction-expiration-hours", 168) * 3_600_000L);
                pl.markDirty();
                pl.msg(p, "Listed as &f#" + id + "&r for &a" + pl.money(price));
                if (pl.getConfig().getBoolean("discord.announce-auctions"))
                    pl.integrations().announceToDiscord(p.getName() + " listed " + amt + "x " + listed.getType().name().toLowerCase() + " for " + pl.money(price) + " on /ah (#" + id + ")");
            }
            case "buy" -> { if (a.length < 2) pl.msg(p, "&cUsage: /ah buy <id>"); else auctionBuy(p, a[1]); }
            case "cancel" -> {
                if (a.length < 2) { pl.msg(p, "&cUsage: /ah cancel <id>"); return; }
                String base = "auctions.list." + a[1];
                if (!p.getUniqueId().toString().equals(pl.data().getString(base + ".seller"))) { pl.msg(p, "&cNot your listing."); return; }
                pl.returnAuctionItem(a[1]);
                pl.msg(p, "Listing cancelled. Use &f/ah claim&r to get the item back.");
            }
            case "claim" -> {
                List<?> mail = pl.data().getList("auctions.mail." + p.getUniqueId());
                if (mail == null || mail.isEmpty()) { pl.msg(p, "Nothing to claim."); return; }
                for (Object o : mail) if (o instanceof ItemStack it)
                    p.getInventory().addItem(it).values().forEach(r -> p.getWorld().dropItemNaturally(p.getLocation(), r));
                pl.data().set("auctions.mail." + p.getUniqueId(), null);
                pl.markDirty();
                pl.msg(p, "Items claimed.");
            }
            default -> pl.msg(p, "&cUsage: /ah [sell <price> [amount] | buy <id> | cancel <id> | claim]");
        }
    }

    public void auctionBuy(Player p, String id) {
        String base = "auctions.list." + id;
        ItemStack item = pl.data().getItemStack(base + ".item");
        if (item == null) { pl.msg(p, "&cThat listing no longer exists."); return; }
        UUID seller = UUID.fromString(pl.data().getString(base + ".seller"));
        double price = pl.data().getDouble(base + ".price");
        if (seller.equals(p.getUniqueId())) { pl.msg(p, "&cYou cannot buy your own listing."); return; }
        if (!pl.withdraw(p.getUniqueId(), price)) { pl.msg(p, "&cYou need " + pl.money(price) + "."); return; }
        pl.deposit(seller, price);
        pl.data().set(base, null);
        pl.markDirty();
        p.getInventory().addItem(item).values().forEach(r -> p.getWorld().dropItemNaturally(p.getLocation(), r));
        pl.msg(p, "Bought listing #" + id + " for &a" + pl.money(price));
        Player sp = Bukkit.getPlayer(seller);
        if (sp != null) pl.msg(sp, "Your listing #" + id + " sold for &a" + pl.money(price));
    }

    // ------------------------------------------------------------ tab completion
    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        List<String> out = new ArrayList<>();
        String n = cmd.getName().toLowerCase();
        if (a.length == 1) {
            switch (n) {
                case "warp" -> { out.add("list"); ConfigurationSection w = pl.warps().getConfigurationSection("warps"); if (w != null) out.addAll(w.getKeys(false)); }
                case "delwarp" -> { ConfigurationSection w = pl.warps().getConfigurationSection("warps"); if (w != null) out.addAll(w.getKeys(false)); }
                case "friend" -> out.addAll(List.of("add", "remove", "list"));
                case "admin" -> out.addAll(List.of("help", "balance", "tag", "reload"));
                case "ah" -> out.addAll(List.of("sell", "buy", "cancel", "claim"));
                case "rank" -> out.addAll(List.of("list", "info", "set"));
                case "sell" -> out.add("all");
                case "shop" -> out.add("preset");
                case "money", "pay", "tpa", "tpahere", "tpaccept", "tpdeny", "homevisit" -> Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
                default -> { }
            }
        } else if (a.length == 2 && n.equals("shop") && a[0].equalsIgnoreCase("preset")) {
            out.addAll(Shop.listPresetNames(pl.pricesFolder()));
        } else if (a.length == 2 && n.equals("rank") && a[0].equalsIgnoreCase("set")) {
            Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
        } else if (a.length == 3 && n.equals("rank") && a[0].equalsIgnoreCase("set") && pl.ranks() != null) {
            out.addAll(pl.ranks().getKeys(false));
        }
        String cur = a.length == 0 ? "" : a[a.length - 1].toLowerCase();
        return out.stream().filter(x -> x.toLowerCase().startsWith(cur)).collect(Collectors.toList());
    }
}
