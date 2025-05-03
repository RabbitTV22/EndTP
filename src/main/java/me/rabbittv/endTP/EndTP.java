package me.rabbittv.endTP;

import com.destroystokyo.paper.event.player.PlayerTeleportEndGatewayEvent;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.World;
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
import java.util.concurrent.ThreadLocalRandom;

public final class EndTP extends JavaPlugin implements Listener {

    private ConfigurationSection messages;
    private ConfigurationSection options;
    private File dataFile;
    private FileConfiguration gatewayData;
    private final MiniMessage mm = MiniMessage.miniMessage();

    @Override
    public void onEnable() {
        loadData();
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);
        options = getConfig().getConfigurationSection("options");
        messages = getConfig().getConfigurationSection("messages");
    }

    private void loadData() {
        dataFile = new File(getDataFolder(), "gateways.yml");
        if (!dataFile.exists()) {
            dataFile.getParentFile().mkdirs();
            saveResource("gateways.yml", false);
        }
        gatewayData = YamlConfiguration.loadConfiguration(dataFile);
    }

    private void saveData() {
        try {
            gatewayData.save(dataFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args){
        if (command.getName().equalsIgnoreCase("endtpreload") && sender.hasPermission("endtp.reload")) {
            reloadConfig();
            options = getConfig().getConfigurationSection("options");
            messages = getConfig().getConfigurationSection("messages");
            Audience p = (Audience) sender;
            Component Parsed = mm.deserialize(messages.getString("config_reload", "<green>Config reloaded."));
            p.sendMessage(Parsed);
        }
        return true;
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

        Location destination;

        if (gatewayData.contains(key)) {
            destination = deserializeLocation(world, gatewayData.getString(key));
        } else {
            destination = generateSafeEndLocation(world, options.getInt("min_radius", 2000), options.getInt("max_radius", 2300));
            if (destination == null) {
                Audience p = (Audience) player;
                Component Parsed = mm.deserialize(messages.getString("failed_tp", "<red>Failed to teleport you. Please try again."));
                p.sendMessage(Parsed);
                return;
            }
            gatewayData.set(key, serializeLocation(destination));
            saveData();
        }

        player.teleportAsync(destination);
        Audience p = (Audience) player;
        Component Parsed = mm.deserialize(messages.getString("teleported", "<blue>You have been teleported to a galaxy far, far away."));
        p.sendMessage(Parsed);
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

            if (world.getBlockAt(x, y + 1, z).isEmpty() &&
                    world.getBlockAt(x, y + 2, z).isEmpty()) {
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