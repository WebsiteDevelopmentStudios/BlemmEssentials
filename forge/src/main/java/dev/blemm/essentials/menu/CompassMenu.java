package dev.blemm.essentials.menu;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

/** Vanilla compass item and utility menu, replacing the Paper-only compass GUI. */
public final class CompassMenu {
    private static final String MARKER = "blemmessentials_menu_compass";
    private CompassMenu() {}

    public static ItemStack makeCompass() {
        ItemStack stack = new ItemStack(Items.COMPASS);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("BlemmEssentials Navigation"));
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(MARKER, true);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    public static boolean isMenuCompass(ItemStack stack) {
        if (stack == null || !stack.is(Items.COMPASS)) return false;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().contains(MARKER);
    }

    /** Ensures players have a menu compass; if it is lost, it is replaced on their next join. */
    public static void onJoin(ServerPlayer player) {
        if (!isAllowedWorld(player)) return;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (isMenuCompass(player.getInventory().getItem(i))) return;
        }
        ItemStack compass = makeCompass();
        player.getInventory().add(compass);
    }

    public static boolean isAllowedWorld(ServerPlayer player) {
        // Mod loaders use dimensions, not Bukkit/Multiverse world names. Only the normal Overworld is allowed.
        // Nether, End, and custom dimensions (including a modded Lifesteal dimension) are rejected.
        return player.level().dimension().identifier().toString().equals("minecraft:overworld");
    }

    public static void open(ServerPlayer player) {
        if (!isAllowedWorld(player) || dev.blemm.essentials.commands.CommandPolicy.disabled(player.createCommandSourceStack(), "compass")) {
            player.sendSystemMessage(Component.literal("The BlemmEssentials compass is disabled in this dimension."));
            return;
        }
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new UtilityMenu(id, inventory),
            Component.literal("BlemmEssentials")));
    }

    private static final class UtilityMenu extends ChestMenu {
        private UtilityMenu(int id, Inventory inventory) {
            super(MenuType.GENERIC_9x3, id, inventory, new net.minecraft.world.SimpleContainer(27), 3);
            set(10, Items.EMERALD, "Balance", "balance");
            set(11, Items.CHEST, "Shop", "shop");
            set(12, Items.GOLD_INGOT, "Auction House", "ah list");
            set(13, Items.CHEST, "Homes", "home");
            set(14, Items.ENDER_PEARL, "Warps", "warp list");
            set(15, Items.PLAYER_HEAD, "Friends", "friend list");

            getSlot(22).set(named(new ItemStack(Items.BARRIER), "Close"));
        }

        private void set(int slot, net.minecraft.world.item.Item item, String label, String command) {
            getSlot(slot).set(named(new ItemStack(item), label));
        }

        @Override
        public void clicked(int slotId, int button, ContainerInput input, Player player) {
            if (!(player instanceof ServerPlayer serverPlayer) || slotId < 0 || slotId >= 27) return;
            if (!isAllowedWorld(serverPlayer)) {
                serverPlayer.closeContainer();
                serverPlayer.sendSystemMessage(Component.literal("The BlemmEssentials compass is only available in the Overworld."));
                return;
            }
            if (slotId == 22) {
                serverPlayer.closeContainer();
                return;
            }
            String command = switch (slotId) {
                case 10 -> "balance";
                case 11 -> "shop";
                case 12 -> "ah list";
                case 13 -> "home";
                case 14 -> "warp list";
                case 15 -> "friend list";
                default -> null;
            };
            if (command == null) return;
            if (!(serverPlayer.level() instanceof ServerLevel level)) return;
            MinecraftServer server = level.getServer();
            serverPlayer.closeContainer();
            server.getCommands().performPrefixedCommand(serverPlayer.createCommandSourceStack(), command);
        }

        private static ItemStack named(ItemStack stack, String label) {
            stack.set(DataComponents.CUSTOM_NAME, Component.literal(label));
            return stack;
        }
    }
}
