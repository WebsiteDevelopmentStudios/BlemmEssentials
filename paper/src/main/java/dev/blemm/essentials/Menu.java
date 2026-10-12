package dev.blemm.essentials;

import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** Inventory holder that tags every BlemmEssentials GUI. */
public class Menu implements InventoryHolder {
    public enum Type { SHOP_CATEGORIES, SHOP_ITEMS, AUCTIONS }

    public final Type type;
    public final String category;
    public final int page;
    private Inventory inv;

    public Menu(Type type, String category, int page, int size, String title) {
        this.type = type; this.category = category; this.page = page;
        this.inv = Bukkit.createInventory(this, size, title);
    }

    @Override public Inventory getInventory() { return inv; }
}
