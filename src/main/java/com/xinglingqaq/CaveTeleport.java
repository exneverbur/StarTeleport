package com.xinglingqaq;

import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class CaveTeleport extends JavaPlugin implements Listener, CommandExecutor {
    private boolean debug;
    
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!cmd.getName().equalsIgnoreCase("ctp")) return false;
        
        if (!sender.hasPermission("caveteleport.reload")) {
            sender.sendMessage("§c你没有权限执行此命令！");
            return true;
        }
        
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            debug = getConfig().getBoolean("debug", false);
            sender.sendMessage("§a配置已成功重载！");
            return true;
        }
        return false;
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        debug = getConfig().getBoolean("debug", false);
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("ctp").setExecutor(this);
        getLogger().info("[XingLingQAQ&AI]洞穴适配——已成功启动！");
    }

    @Override
    public void onDisable() {
        getLogger().info("[XingLingQAQ&AI]洞穴适配——已成功卸载！");
    }
    
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        
        // 新增权限检查
        if (!player.hasPermission("caveteleport.pass")) {
            return;
        }
        
        World currentWorld = player.getWorld();
        String targetWorldName = null;
        
        // 调试：记录玩家当前坐标
        if (debug) {
            getLogger().info("[调试] 玩家 " + player.getName() + " 当前位置 Y 坐标：" + player.getLocation().getBlockY());
        }
        
        ConfigurationSection rules = getConfig().getConfigurationSection("worlds");
        if (rules != null) {
            for (String key : rules.getKeys(false)) {
                ConfigurationSection rule = rules.getConfigurationSection(key);
                if (currentWorld.getName().equals(rule.getString("world_from"))) {
                    targetWorldName = rule.getString("world_to");
                    break;
                }
            }
        }
        
        // 检查Y坐标是否低于等于-62
        if (player.getLocation().getBlockY() <= -62 && targetWorldName != null) {
            // 调试：记录匹配到的传送规则
            if (debug) {
                getLogger().info("[调试] 触发传送条件，目标世界：" + targetWorldName);
            }
            
            World targetWorld = getServer().getWorld(targetWorldName);
            if (targetWorld != null) {
                player.teleport(targetWorld.getSpawnLocation());
                player.sendMessage("你已到达地底，正在传送...");
            } else {
                // 调试：记录世界加载失败错误
                getLogger().warning("[错误] 目标世界 " + targetWorldName + " 未正确加载！");
            }
        }
    }
}