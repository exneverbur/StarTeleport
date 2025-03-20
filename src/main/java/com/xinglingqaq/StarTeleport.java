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
import java.util.HashMap;
import java.util.Map;

public class StarTeleport extends JavaPlugin implements Listener, CommandExecutor {
    private boolean debug;
    private int teleportDelay;
    private final Map<Player, Integer> taskMap = new HashMap<>();
    private final Map<Player, Boolean> playerStatusMap = new HashMap<>();
    
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!cmd.getName().equalsIgnoreCase("stp")) return false;
        
        if (!sender.hasPermission("starteleport.command.reload")) {
            sender.sendMessage("§c你没有权限执行此命令！");
            return true;
        }
        
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            debug = getConfig().getBoolean("debug", false);
            teleportDelay = getConfig().getInt("delay_seconds", 5);
            sender.sendMessage("§a配置已成功重载！");
            return true;
        }
        return false;
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        debug = getConfig().getBoolean("debug", false);
        teleportDelay = getConfig().getInt("delay_seconds", 5);
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("stp").setExecutor(this);
        getLogger().info("[XingLingQAQ]StarTeleport——已成功启动！");
    }

    @Override
    public void onDisable() {
        getLogger().info("[XingLingQAQ]StarTeleport——已成功卸载！");
    }
    
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        
        // 新增权限检查
        if (!player.hasPermission("starteleport.pass")) {
            return;
        }

        // 检测三维坐标变化
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() &&
            event.getFrom().getBlockY() == event.getTo().getBlockY() &&
            event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        // 取消现有任务并发送提示
        if (taskMap.containsKey(player)) {
            getServer().getScheduler().cancelTask(taskMap.get(player));
            taskMap.remove(player);
            player.sendTitle("§c传送取消!", "", 10, 20, 10);
            return;
        }

        World currentWorld = player.getWorld();
        String targetWorldName = null;
        
        // 调试：记录玩家当前坐标
        if (debug) {
            getLogger().info("[调试] 玩家 " + player.getName() + " 当前位置 Y 坐标：" + player.getLocation().getBlockY());
        }
        
        ConfigurationSection rules = getConfig().getConfigurationSection("worlds");
        int worldThresholdY = getConfig().getInt("threshold_y", -62); // 默认阈值
        
        if (rules != null) {
            for (String key : rules.getKeys(false)) {
                ConfigurationSection rule = rules.getConfigurationSection(key);
                if (currentWorld.getName().equals(rule.getString("world_from"))) {
                    targetWorldName = rule.getString("world_to");
                    // 从世界配置中获取阈值，如果没有则使用默认值
                    worldThresholdY = rule.getInt("threshold_y", worldThresholdY);
                    break;
                }
            }
        }
        
        // 取消现有任务
        if (taskMap.containsKey(player)) {
            getServer().getScheduler().cancelTask(taskMap.get(player));
            taskMap.remove(player);
            playerStatusMap.put(player, true);
        }
        // 更新玩家状态当离开阈值区域
        int currentY = player.getLocation().getBlockY();
        if ((worldThresholdY < 0 && currentY > worldThresholdY) || (worldThresholdY > 0 && currentY < worldThresholdY)) {
            playerStatusMap.put(player, true);
        }

        // 精确层数状态追踪
        Boolean status = playerStatusMap.get(player);
        
        // 根据阈值正负值动态调整触发条件
        boolean triggerCondition = (worldThresholdY < 0) ? (currentY <= worldThresholdY) : (currentY >= worldThresholdY);
        if (triggerCondition && (status == null || status)) {
            playerStatusMap.put(player, false);
            
            if (targetWorldName != null) {
            World targetWorld = getServer().getWorld(targetWorldName);
            if (targetWorld == null) {
                getLogger().warning("目标世界 " + targetWorldName + " 未正确加载！");
                return;
            }
            final World finalTargetWorld = targetWorld;
            java.util.concurrent.atomic.AtomicInteger remaining = new java.util.concurrent.atomic.AtomicInteger(teleportDelay);
            
            int taskId = getServer().getScheduler().scheduleSyncRepeatingTask(this, () -> {
                int current = remaining.getAndDecrement();
                if (current > 0) {
                    player.sendTitle("§e即将传送", "剩余 " + current + " 秒", 0, 20, 0);
                } else {
                    player.sendTitle("§e即将传送", "剩余 0 秒", 0, 20, 0);
                    getServer().getScheduler().scheduleSyncDelayedTask(this, () -> {
                        getServer().getScheduler().cancelTask(taskMap.get(player));
                        taskMap.remove(player);
                        player.teleport(finalTargetWorld.getSpawnLocation());
                        playerStatusMap.put(player, true);
                        player.sendMessage("§a传送完成！");
                        // 重置层数状态标记
                        playerStatusMap.remove(player);
                    }, 20L);
                }
            }, 0L, 20L);
            
            taskMap.put(player, taskId);
            // 调试：记录匹配到的传送规则
            if (debug) {
                getLogger().info("[调试] 触发传送条件，目标世界：" + targetWorldName);
            }
          }
      }
   }
}