package dev.blemm.essentials;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Optional Carbon Chat bridge. Uses Carbon's public API reflectively so Carbon remains optional
 * and its channel configuration files remain authoritative.
 */
public final class CarbonChatBridge {
    public record Check(boolean installed, boolean compatible, String version, String detail) {}

    private final BlemmEssentials plugin;

    public CarbonChatBridge(BlemmEssentials plugin) { this.plugin = plugin; }

    private Plugin carbonPlugin() {
        Plugin p = Bukkit.getPluginManager().getPlugin("Carbon");
        if (p == null) p = Bukkit.getPluginManager().getPlugin("CarbonChat");
        return p != null && p.isEnabled() ? p : null;
    }

    private Class<?> carbonClass(String name, Plugin carbon) throws ClassNotFoundException {
        return Class.forName(name, true, carbon.getClass().getClassLoader());
    }

    public Check check(String channelName) {
        Plugin carbon = carbonPlugin();
        if (carbon == null) return new Check(false, false, "not installed", "Carbon Chat is not installed or enabled.");
        String version = carbon.getDescription().getVersion();
        try {
            Object api = carbonClass("net.draycia.carbon.api.CarbonChatProvider", carbon)
                    .getMethod("carbonChat").invoke(null);
            Class<?> carbonChatType = carbonClass("net.draycia.carbon.api.CarbonChat", carbon);
            Object registry = carbonChatType.getMethod("channelRegistry").invoke(api);
            Class<?> registryType = carbonClass("net.draycia.carbon.api.channels.ChannelRegistry", carbon);
            Set<?> keys = (Set<?>) registryType.getMethod("keys").invoke(registry);
            boolean found = keys.stream().anyMatch(k -> {
                try {
                    String value = String.valueOf(carbonClass("net.kyori.adventure.key.Key", carbon).getMethod("value").invoke(k));
                    return value.equalsIgnoreCase(channelName) || value.equalsIgnoreCase("global:" + channelName);
                } catch (ReflectiveOperationException ignored) { return false; }
            });
            Object manager = carbonChatType.getMethod("userManager").invoke(api);
            Class<?> userManagerType = carbonClass("net.draycia.carbon.api.users.UserManager", carbon);
            Method user = findMethod(userManagerType, "user", UUID.class);
            if (user == null) return new Check(true, false, version, "Carbon API found, but UserManager.user(UUID) is unavailable.");
            Class<?> carbonPlayer = carbonClass("net.draycia.carbon.api.users.CarbonPlayer", carbon);
            boolean setter = false;
            for (Method method : carbonPlayer.getMethods()) if (method.getName().equals("selectedChannel") && method.getParameterCount() == 1) setter = true;
            if (!setter) return new Check(true, false, version, "CarbonPlayer.selectedChannel(ChatChannel) is unavailable in this API.");
            if (!found) return new Check(true, false, version, "Carbon API loaded, but channel '" + channelName + "' is not registered. Registered keys: " + keys);
            return new Check(true, true, version, "Carbon API and channel '" + channelName + "' detected.");
        } catch (Throwable t) {
            return new Check(true, false, version, "Could not safely access Carbon's public API (" + rootMessage(t) + "). No channel settings were changed.");
        }
    }

    public void switchChannel(Player target, String channelName, java.util.function.Consumer<String> result) {
        Plugin carbon = carbonPlugin();
        if (carbon == null) { result.accept("Carbon Chat is not installed or enabled."); return; }
        try {
            Object api = carbonClass("net.draycia.carbon.api.CarbonChatProvider", carbon)
                    .getMethod("carbonChat").invoke(null);
            Class<?> carbonChatType = carbonClass("net.draycia.carbon.api.CarbonChat", carbon);
            Object registry = carbonChatType.getMethod("channelRegistry").invoke(api);
            Object channel = null;
            Class<?> registryType = carbonClass("net.draycia.carbon.api.channels.ChannelRegistry", carbon);
            Set<?> keys = (Set<?>) registryType.getMethod("keys").invoke(registry);
            for (Object key : keys) {
                String value = String.valueOf(carbonClass("net.kyori.adventure.key.Key", carbon).getMethod("value").invoke(key));
                if (!value.equalsIgnoreCase(channelName) && !value.equalsIgnoreCase("global:" + channelName)) continue;
                for (Method m : registryType.getMethods()) {
                    if (m.getName().equals("channel") && m.getParameterCount() == 1
                            && m.getParameterTypes()[0].isInstance(key)) {
                        channel = m.invoke(registry, key);
                        break;
                    }
                }
                if (channel != null) break;
            }
            if (channel == null) { result.accept("Carbon channel '" + channelName + "' was not found."); return; }

            Object manager = carbonChatType.getMethod("userManager").invoke(api);
            Method userMethod = findMethod(carbonClass("net.draycia.carbon.api.users.UserManager", carbon), "user", UUID.class);
            if (userMethod == null) { result.accept("This Carbon version does not expose UserManager.user(UUID)."); return; }
            Object futureObject = userMethod.invoke(manager, target.getUniqueId());
            if (!(futureObject instanceof CompletableFuture<?> future)) {
                result.accept("Carbon returned an unexpected user lookup result; channel unchanged."); return;
            }
            Object finalChannel = channel;
            future.whenComplete((carbonPlayer, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (error != null || carbonPlayer == null) {
                    result.accept("Carbon could not load the player's profile; channel unchanged."); return;
                }
                try {
                    Method setter = carbonClass("net.draycia.carbon.api.users.CarbonPlayer", carbon).getMethod("selectedChannel", carbonClass("net.draycia.carbon.api.channels.ChatChannel", carbon));
                    setter.invoke(carbonPlayer, finalChannel);
                    result.accept("Switched " + target.getName() + "'s Carbon channel to '" + channelName + "'.");
                } catch (Throwable t) {
                    result.accept("Carbon channel switch failed safely: " + rootMessage(t)); 
                }
            }));
        } catch (Throwable t) {
            result.accept("Carbon API integration failed safely: " + rootMessage(t));
        }
    }

    private static Method findMethod(Class<?> type, String name, Class<?> parameter) {
        for (Method m : type.getMethods()) if (m.getName().equals(name) && m.getParameterCount() == 1
                && m.getParameterTypes()[0].isAssignableFrom(parameter)) return m;
        return null;
    }

    private static Method findOneArgMethod(Class<?> type, String name, Class<?> argumentType) {
        for (Method m : type.getMethods()) if (m.getName().equals(name) && m.getParameterCount() == 1
                && m.getParameterTypes()[0].isAssignableFrom(argumentType)) return m;
        return null;
    }

    private static String rootMessage(Throwable t) {
        Throwable x = t;
        while (x.getCause() != null && x.getCause() != x) x = x.getCause();
        return x.getMessage() == null ? x.getClass().getSimpleName() : x.getMessage();
    }
}
