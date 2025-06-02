package me.rabbittv.endTP;

import com.destroystokyo.paper.event.player.PlayerTeleportEndGatewayEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class EndTP extends JavaPlugin implements Listener {

    private ConfigurationSection options;
    private File dataFile;
    private FileConfiguration gatewayData;

    private final MiniMessage mm = MiniMessage.miniMessage();

    private Component msgTeleportSuccess;
    private Component msgTeleportFail;
    private Component msgConfigReload;

    private final Map<String, Location> locationCache = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfigMessages();
        loadData();

        getServer().getPluginManager().registerEvents(this, this);
    }

    private void loadConfigMessages() {
        reloadConfig();
        ConfigurationSection messages = getConfig().getConfigurationSection("messages");
        options = getConfig().getConfigurationSection("options");

        msgTeleportSuccess = mm.deserialize(messages.getString("teleported", "<blue>You have been teleported to a galaxy far, far away."));
        msgTeleportFail = mm.deserialize(messages.getString("failed_tp", "<red>Failed to teleport you. Please try again."));
        msgConfigReload = mm.deserialize(messages.getString("config_reload", "<green>Config reloaded."));
    }

    private void loadData() {
        dataFile = new File(getDataFolder(), "gateways.yml");
        if (!dataFile.exists()) {
            dataFile.getParentFile().mkdirs();
            saveResource("gateways.yml", false);
        }
        gatewayData = YamlConfiguration.loadConfiguration(dataFile);

        // Preload into cache
        for (String key : gatewayData.getKeys(false)) {
            String val = gatewayData.getString(key);
            String[] split = key.split("_");
            if (split.length < 4) continue;
            World w = Bukkit.getWorld(split[0]);
            if (w != null) {
                locationCache.put(key, deserializeLocation(w, val));
            }
        }
    }

    private void saveDataAsync() {
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                gatewayData.save(dataFile);
            } catch (IOException e) {
                getLogger().severe("Failed to save gateway data:");
                e.printStackTrace();
            }
        });
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args){
        if (command.getName().equalsIgnoreCase("endtpreload") && sender.hasPermission("endtp.reload")) {
            loadConfigMessages();
            sender.sendMessage(msgConfigReload);
            return true;
        }
        return false;
    }

    @EventHandler
    public void onGatewayUse(PlayerTeleportEndGatewayEvent event) {
        Player player = event.getPlayer();
        Location from = event.getFrom();
        World world = from.getWorld();

        if (world == null || world.getEnvironment() != World.Environment.THE_END) return;

        Block block = event.getGateway().getBlock();
        Location blockLoc = block.getLocation().toBlockLocation();
        String key = world.getName() + "_" + blockLoc.getBlockX() + "_" + blockLoc.getBlockY() + "_" + blockLoc.getBlockZ();

        event.setCancelled(true);

        Location destination = locationCache.get(key);

        if (destination == null) {
            destination = generateSafeEndLocation(world, options.getInt("min_radius", 5000), options.getInt("max_radius", 5300));
            if (destination == null) {
                player.sendMessage(msgTeleportFail);
                return;
            }

            gatewayData.set(key, serializeLocation(destination));
            locationCache.put(key, destination);
            saveDataAsync();
        }

        player.teleportAsync(destination).thenAccept(success -> {
            if (success) player.sendMessage(msgTeleportSuccess);
            else player.sendMessage(msgTeleportFail);
        });
    }

    private Location generateSafeEndLocation(World world, int minRadius, int maxRadius) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int maxAttempts = options.getInt("max_attempts", 30);

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            double angle = random.nextDouble(0, Math.PI * 2);
            double radius = minRadius + random.nextDouble() * (maxRadius - minRadius);
            int x = (int) Math.round(Math.cos(angle) * radius);
            int z = (int) Math.round(Math.sin(angle) * radius);

            int y = findSafeY(world, x, z);
            if (y != -1) {
                return new Location(world, x + 0.5, y + 1, z + 0.5);
            }
        }
        return null;
    }

    private int findSafeY(World world, int x, int z) {
        int minY = options.getInt("min_y", 30);
        int maxY = options.getInt("max_y", 80);

        for (int y = maxY; y >= minY; y--) {
            Block ground = world.getBlockAt(x, y, z);
            if (!ground.getType().isSolid()) continue;
            if (world.getBlockAt(x, y + 1, z).isEmpty() && world.getBlockAt(x, y + 2, z).isEmpty()) {
                return y;
            }
        }
        return -1;
    }

    private String serializeLocation(Location loc) {
        return loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }

    private Location deserializeLocation(World world, String s) {
        String[] parts = s.split(",");
        int x = Integer.parseInt(parts[0]);
        int y = Integer.parseInt(parts[1]);
        int z = Integer.parseInt(parts[2]);
        return new Location(world, x + 0.5, y + 1, z + 0.5);
    }
}
