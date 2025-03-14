package com.xinglingqaq;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.HashMap;
import org.bukkit.configuration.ConfigurationSection;

public class DeepslateReplacementPlugin extends JavaPlugin implements Listener {
    private double replacementChance;
    private String worldToReplace;
    private String worldToTeleport;
    private Map<String, String> worldTransferMap;
    @Override
    public void onEnable() {
        saveDefaultConfig();
        FileConfiguration config = getConfig();
        replacementChance = config.getDouble("replacement_chance");
        worldToReplace = config.getString("world_to_replace");
        worldToTeleport = config.getString("world_to_teleport");
        worldTransferMap = new HashMap<>();
        ConfigurationSection worldTransferSection = config.getConfigurationSection("world_transfer");
        if (worldTransferSection != null) {
            for (String key : worldTransferSection.getKeys(false)) {
                ConfigurationSection transferConfig = worldTransferSection.getConfigurationSection(key);
                if (transferConfig != null) {
                    String fromWorld = transferConfig.getString("world_to_replace");
                    String toWorld = transferConfig.getString("world_to_teleport");
                    if (fromWorld != null && toWorld != null) {
                        worldTransferMap.put(fromWorld, toWorld);
                    }
                }
            }
        }
        getServer().getPluginManager().registerEvents((Listener)this, this);
        getLogger().info("[XingLingQAQ&AI]洞穴适配——已成功启动!");
    }

    @Override
    public void onDisable() {
        getLogger().info("[XingLingQAQ&AI]洞穴适配——已成功卸载!");
    }

    @Override
    public ChunkGenerator getDefaultWorldGenerator(String worldName, String id) {
        if (worldName.equals(worldToReplace)) {
            return new DeepslateChunkGenerator(worldToReplace, replacementChance);
        }
        return super.getDefaultWorldGenerator(worldName, id);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location location = player.getLocation();
        String currentWorld = location.getWorld().getName();
        if (location.getBlockY() < 0 && worldTransferMap.containsKey(currentWorld)) {
            String targetWorldName = worldTransferMap.get(currentWorld);
            World targetWorld = Bukkit.getWorld(targetWorldName);
            if (targetWorld != null) {
                Location targetSpawn = targetWorld.getSpawnLocation();
                player.teleport(targetSpawn);
            }
        }
    }

    private static class DeepslateChunkGenerator extends ChunkGenerator {
        private final String worldToReplace;
        private final double replacementChance;

        public DeepslateChunkGenerator(String worldToReplace, double replacementChance) {
            this.worldToReplace = worldToReplace;
            this.replacementChance = replacementChance;
        }

        @Override
        public List<BlockPopulator> getDefaultPopulators(World world) {
            List<BlockPopulator> populators = new ArrayList<>();
            populators.add(new DeepslatePopulator(this.worldToReplace, this.replacementChance));
            return populators;
        }
    }

    private static class DeepslatePopulator extends BlockPopulator {
        private final String worldToReplace;
        private final double replacementChance;

        public DeepslatePopulator(String worldToReplace, double replacementChance) {
            this.worldToReplace = worldToReplace;
            this.replacementChance = replacementChance;
        }

        @Override
        public void populate(World world, Random random, Chunk chunk) {
            if (world.getName().equals(this.worldToReplace)) {
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        Block block = chunk.getBlock(x, 0, z);
                        if (block.getType() == Material.BEDROCK && random.nextDouble() < this.replacementChance) {
                            block.setType(Material.DEEPSLATE);
                        }
                    }
                }
            }
        }
    }
}