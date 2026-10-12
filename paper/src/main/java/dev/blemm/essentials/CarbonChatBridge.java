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
            Object registry = api.getClass().getMethod("channelRegistry").invoke(api);
            Set<?> keys = (Set<?>) registry.getClass().getMethod("keys").invoke(registry);
            boolean found = keys.stream().anyMatch(k -> {
                try {
                    String value = String.valueOf(k.getClass().getMethod("value").invoke(k));
                    return value.equalsIgnoreCase(channelName) || value.equalsIgnoreCase("global:" + channelName);
                } catch (ReflectiveOperationException ignored) { return false; }
            });
            Object manager = api.getClass().getMethod("userManager").invoke(api);
            Method user = findMethod(manager.getClass(), "user", UUID.class);
            if (user == null) return new Check(true, false, version, "Carbon API found, but UserManager.user(UUID) is unavailable.");
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
            Object registry = api.getClass().getMethod("channelRegistry").invoke(api);
            Object channel = null;
            for (Object key : (Set<?>) registry.getClass().getMethod("keys").invoke(registry)) {
                String value = String.valueOf(key.getClass().getMethod("value").invoke(key));
                if (value.equalsIgnoreCase(channelName) || value.equalsIgnoreCase("global:" + channelName)) {
                    channel = registry.getClass().getMethod("channel", key.getClass().getInterfaces().length > 0
                            ? key.getClass().getInterfaces()[0] : key.getClass()).invoke(registry, key);
                    if (channel != null) break;
                }
            }
            // Fall back to the registry's public channel(Key) method, using the actual key type.
            if (channel == null) {
                for (Method m : registry.getClass().getMethods()) {
                    if (m.getName().equals("channel") && m.getParameterCount() == 1) {
                        for (Object key : (Set<?>) registry.getClass().getMethod("keys").invoke(registry)) {
                            String value = String.valueOf(key.getClass().getMethod("value").invoke(key));
                            if (value.equalsIgnoreCase(channelName) || value.equalsIgnoreCase("global:" + channelName)) {
                                channel = m.invoke(registry, key); break;
                            }
                        }
                    }
                    if (channel != null) break;
                }
            }
            if (channel == null) { result.accept("Carbon channel '" + channelName + "' was not found."); return; }

            Object manager = api.getClass().getMethod("userManager").invoke(api);
            Method userMethod = findMethod(manager.getClass(), "user", UUID.class);
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
                    Method setter = findOneArgMethod(carbonPlayer.getClass(), "selectedChannel", finalChannel.getClass());
                    if (setter == null) {
                        for (Method m : carbonPlayer.getClass().getMethods()) {
                            if (m.getName().equals("selectedChannel") && m.getParameterCount() == 1
                                    && m.getParameterTypes()[0].isInstance(finalChannel)) { setter = m; break; }
                        }
                    }
                    if (setter == null) { result.accept("This Carbon version does not expose the expected channel setter."); return; }
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
