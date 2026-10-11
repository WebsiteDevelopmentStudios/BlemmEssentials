package dev.blemm.essentials.shop;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/** Server-side vanilla chest GUI for browsing shop categories and buying items. */
public final class ShopMenu extends ChestMenu {
    @FunctionalInterface
    public interface PurchaseHandler { void purchase(ServerPlayer player, ItemStack stack); }

    private static final List<Category> CATEGORIES = List.of(
        new Category("building_blocks", "Building Blocks", Items.OAK_PLANKS),
        new Category("natural", "Natural", Items.GRASS_BLOCK),
        new Category("ores_minerals", "Ores & Minerals", Items.DIAMOND),
        new Category("tools", "Tools", Items.IRON_PICKAXE),
        new Category("combat", "Combat", Items.IRON_SWORD),
        new Category("food", "Food", Items.BREAD),
        new Category("farming", "Farming", Items.WHEAT),
        new Category("redstone", "Redstone", Items.REDSTONE),
        new Category("decoration", "Decoration", Items.LANTERN),
        new Category("brewing", "Brewing", Items.POTION),
        new Category("transport", "Transport", Items.MINECART),
        new Category("miscellaneous", "Miscellaneous", Items.CHEST)
    );

    private final String category;
    private final int page;
    private final PurchaseHandler purchaseHandler;
    private final List<Item> pageItems;

    private ShopMenu(int containerId, Inventory inventory, String category, int page, PurchaseHandler handler) {
        super(MenuType.GENERIC_9x6, containerId, inventory, new net.minecraft.world.SimpleContainer(54), 6);
        this.category = category;
        this.page = Math.max(0, page);
        this.purchaseHandler = handler;
        this.pageItems = category == null ? List.of() : itemsFor(category);
        fill();
    }

    public static void openCategories(ServerPlayer player, PurchaseHandler handler) {
        player.openMenu(new SimpleMenuProvider((id, inventory, p) ->
            new ShopMenu(id, inventory, null, 0, handler), Component.literal("BlemmEssentials Shop")));
    }

    private static void openItems(ServerPlayer player, String category, int page, PurchaseHandler handler) {
        player.openMenu(new SimpleMenuProvider((id, inventory, p) ->
            new ShopMenu(id, inventory, category, page, handler),
            Component.literal("Shop: " + displayName(category) + " (" + (page + 1) + ")")));
    }

    private void fill() {
        if (category == null) {
            for (int i = 0; i < CATEGORIES.size(); i++) {
                Category entry = CATEGORIES.get(i);
                ItemStack icon = named(new ItemStack(entry.icon()), entry.title() + " — click to browse");
                getSlot(i + 9).set(icon);
            }
            getSlot(49).set(named(new ItemStack(Items.BARRIER), "Close shop"));
            return;
        }

        int start = page * 45;
        for (int i = 0; i < 45 && start + i < pageItems.size(); i++) {
            Item item = pageItems.get(start + i);
            String id = BuiltInRegistries.ITEM.getKey(item).toString();
            long buy = ShopPrices.buy(id);
            long sell = ShopPrices.sell(id);
            ItemStack icon = named(new ItemStack(item), item.getName(new ItemStack(item)).getString()
                + " — Buy $" + buy + " / Sell $" + sell + " (left-click 1, right-click stack)");
            getSlot(i).set(icon);
        }
        getSlot(45).set(named(new ItemStack(Items.ARROW), "Previous page"));
        getSlot(49).set(named(new ItemStack(Items.BARRIER), "Back to categories"));
        getSlot(53).set(named(new ItemStack(Items.ARROW), "Next page"));
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (!(player instanceof ServerPlayer serverPlayer) || clickType == ClickType.QUICK_MOVE
            || clickType == ClickType.THROW || clickType == ClickType.SWAP || clickType == ClickType.CLONE) return;
        if (slotId < 0 || slotId >= 54) return;
        if (category == null) {
            int index = slotId - 9;
            if (index >= 0 && index < CATEGORIES.size()) {
                openItems(serverPlayer, CATEGORIES.get(index).id(), 0, purchaseHandler);
            } else if (slotId == 49) {
                serverPlayer.closeContainer();
            }
            return;
        }
        if (slotId == 49) {
            openCategories(serverPlayer, purchaseHandler);
            return;
        }
        if (slotId == 45) {
            if (page > 0) openItems(serverPlayer, category, page - 1, purchaseHandler);
            return;
        }
        if (slotId == 53) {
            if ((page + 1) * 45 < pageItems.size()) openItems(serverPlayer, category, page + 1, purchaseHandler);
            return;
        }
        if (slotId >= 0 && slotId < 45 && slotId < pageItems.size() - page * 45) {
            Item item = pageItems.get(page * 45 + slotId);
            long unitPrice = ShopPrices.buy(BuiltInRegistries.ITEM.getKey(item).toString());
            if (unitPrice <= 0) {
                serverPlayer.sendSystemMessage(Component.literal("This item is not available for purchase."));
                return;
            }
            int amount = button == 1 ? new ItemStack(item).getMaxStackSize() : 1;
            purchaseHandler.purchase(serverPlayer, new ItemStack(item, amount));
        }
    }

    private static List<Item> itemsFor(String category) {
        List<Item> items = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (item == Items.AIR) continue;
            String id = BuiltInRegistries.ITEM.getKey(item).toString();
            String path = id.substring(id.indexOf(':') + 1);
            if (path.endsWith("_spawn_egg") || path.equals("barrier") || path.equals("bedrock")
                || path.contains("command_block") || path.equals("structure_block") || path.equals("structure_void")
                || path.equals("jigsaw") || path.equals("debug_stick") || path.equals("spawner")
                || path.equals("end_portal_frame") || path.equals("knowledge_book")) continue;
            if (ShopPrices.buy(id) > 0 && ShopPrices.categoryFor(id).equals(category)) items.add(item);
        }
        items.sort((a, b) -> BuiltInRegistries.ITEM.getKey(a).toString().compareTo(BuiltInRegistries.ITEM.getKey(b).toString()));
        return items;
    }

    private static String displayName(String category) {
        for (Category entry : CATEGORIES) if (entry.id().equals(category)) return entry.title();
        return category;
    }

    private static ItemStack named(ItemStack stack, String name) {
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal(name));
        return stack;
    }

    private record Category(String id, String title, Item icon) {}
}
