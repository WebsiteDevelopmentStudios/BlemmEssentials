package dev.blemm.essentials;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;

/** A saved location that remembers its world by name (so unloaded Multiverse worlds still work). */
public record Spot(String world, double x, double y, double z, float yaw, float pitch) {

    public static Spot of(Location l) {
        return new Spot(l.getWorld().getName(), l.getX(), l.getY(), l.getZ(), l.getYaw(), l.getPitch());
    }

    public static Spot read(ConfigurationSection s) {
        if (s == null || s.getString("world") == null) return null;
        return new Spot(s.getString("world"), s.getDouble("x"), s.getDouble("y"), s.getDouble("z"),
                (float) s.getDouble("yaw"), (float) s.getDouble("pitch"));
    }

    public void write(ConfigurationSection s) {
        s.set("world", world); s.set("x", x); s.set("y", y); s.set("z", z); s.set("yaw", yaw); s.set("pitch", pitch);
    }

    /** Null if the world is not loaded. */
    public Location toLocation() {
        var w = Bukkit.getWorld(world);
        return w == null ? null : new Location(w, x, y, z, yaw, pitch);
    }
}
