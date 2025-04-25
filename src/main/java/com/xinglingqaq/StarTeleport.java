package com.xinglingqaq;

import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.scheduler.BukkitTask;
import java.util.Map;
import java.util.logging.Level;
import java.util.concurrent.ConcurrentHashMap;

public class StarTeleport extends JavaPlugin implements Listener, CommandExecutor {
    private boolean debug;
    private int teleportDelay;
    // 使用 ConcurrentHashMap 来避免并发问题
    private final Map<Player, BukkitTask> taskMap = new ConcurrentHashMap<>();
    private final Map<String, TeleportRule> teleportRules = new ConcurrentHashMap<>();
    private final Map<Player, Long> lastProcessedTime = new ConcurrentHashMap<>();
    
    // 配置键常量
    private static final String CONFIG_DEBUG = "debug";
    private static final String CONFIG_DELAY = "delay_seconds";
    private static final String CONFIG_THRESHOLD = "threshold_y";
    private static final String CONFIG_WORLDS = "worlds";

    
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!cmd.getName().equalsIgnoreCase("stp")) {
            return false;
        }
        
        if (!sender.hasPermission("starteleport.command.reload")) {
            sender.sendMessage("§c你没有权限执行此命令！");
            return true;
        }
        
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            reloadPluginConfig();
            sender.sendMessage("§a配置已成功重载！");
            return true;
        }
        
        return false;
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfig();
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("stp").setExecutor(this);
        getLogger().info("[XingLingQAQ]StarTeleport——已成功启动！");
    }

    @Override
    public void onDisable() {
        // 取消所有待处理的传送任务
        taskMap.values().forEach(BukkitTask::cancel);
        taskMap.clear();
        teleportRules.clear();
        getLogger().info("[XingLingQAQ]StarTeleport——已成功卸载！");
    }
    
    /**
     * 重新加载插件配置
     */
    private void reloadPluginConfig() {
        reloadConfig();
        loadConfig();
    }
    
    /**
     * 加载配置文件
     */
    private void loadConfig() {
        debug = getConfig().getBoolean(CONFIG_DEBUG, false);
        teleportDelay = getConfig().getInt(CONFIG_DELAY, 5);

        // 读取世界传送配置
        teleportRules.clear();
        ConfigurationSection rules = getConfig().getConfigurationSection(CONFIG_WORLDS);
        if (rules != null) {
            for (String key : rules.getKeys(false)) {
                ConfigurationSection rule = rules.getConfigurationSection(key);
                if (rule != null) {
                    teleportRules.put(
                            rule.getString("world_from"),
                            new TeleportRule(rule.getString("world_to"),
                                    rule.getInt(CONFIG_THRESHOLD, -62),
                                    rule.getBoolean("above", false)));
                }
            }
        }
        if (debug) {
            getLogger().info("Debug 模式已启用");
            getLogger().info("传送延迟设置为: " + teleportDelay + "秒");
        }
    }
    
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        if (event.getTo() == null) {
            return;
        }

        // 未配置该世界规则 返回
        TeleportRule rule = findTeleportRule(player.getWorld().getName());
        if (rule == null) {
            return;
        }

        // 未进行高度变化 返回 只检测方块移动 降低性能损耗
        if (event.getFrom().getBlockY() == event.getTo().getBlockY()) {
            return;
        }

        long currentTime = System.currentTimeMillis();
        // 限制处理频率 每 100 毫秒处理一次
        if (lastProcessedTime.containsKey(player) && currentTime - lastProcessedTime.get(player) < 100) {
            return;
        }
        lastProcessedTime.put(player, currentTime);

        // 检查当前高度是否在阈值范围内
        boolean shouldTeleport = rule.shouldTeleport(event.getTo().getBlockY());
        // 检查是否在传送倒计时中
        if (taskMap.containsKey(player)) {
            // 只有当玩家移动了指定格数时才取消传送 改为高度不在阈值时取消传送
            if (!shouldTeleport) {
                cancelExistingTask(player, true);
                if (debug) {
                    getLogger().log(Level.INFO, "[调试] 玩家离开传送阈值区域，取消传送", player.getName());
                }
                return;
            }
            if (debug) {
                getLogger().log(Level.INFO, "[调试] 玩家 {0} 还在传送阈值区域，继续传送", player.getName());
            }
            // 如果只是微小移动，继续保持传送状态
            return;
        }

        if (!shouldTeleport) {
            return;
        }

        startTeleport(player, rule);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        cancelExistingTask(event.getPlayer(), false);
    }
    
    /**
     * 取消玩家现有的传送任务
     * @param showTitle 是否显示取消传送的Title
     */
    private void cancelExistingTask(Player player, boolean showTitle) {
        BukkitTask existingTask = taskMap.remove(player);
        if (existingTask != null) {
            existingTask.cancel();
            if (showTitle) {
                player.sendTitle("§c传送取消!", "", 10, 20, 10);
            }
        }
    }
    
    /**
     * 查找适用的传送规则
     */
    private TeleportRule findTeleportRule(String currentWorldName) {
        return teleportRules.get(currentWorldName);
    }
    
    /**
     * 开始传送流程
     */
    private void startTeleport(Player player, TeleportRule rule) {
        World targetWorld = getServer().getWorld(rule.targetWorldName);
        if (targetWorld == null) {
            getLogger().log(Level.WARNING, "目标世界 {0} 未正确加载！", rule.targetWorldName);
            return;
        }

        scheduleTeleport(player, targetWorld);
        
        if (debug) {
            getLogger().log(Level.INFO, "[调试] 玩家 {0} 触发传送，目标世界：{1}，初始位置：({2}, {3}, {4})", 
                new Object[]{player.getName(), rule.targetWorldName, 
                    String.format("%.2f", player.getLocation().getX()),
                    String.format("%.2f", player.getLocation().getY()),
                    String.format("%.2f", player.getLocation().getZ())});
        }
    }
    
    /**
     * 调度传送任务
     */
    private void scheduleTeleport(Player player, World targetWorld) {
        BukkitTask task = getServer().getScheduler().runTaskTimer(this, new Runnable() {
            private int remaining = teleportDelay;
            
            @Override
            public void run() {
                if (remaining > 0) {
                    player.sendTitle("§e即将传送", "剩余 " + remaining + " 秒", 0, 20, 0);
                    remaining--;
                } else {
                    executeTeleport(player, targetWorld);
                }
            }
        }, 0L, 20L);
        
        taskMap.put(player, task);
    }
    
    /**
     * 执行传送
     */
    private void executeTeleport(Player player, World targetWorld) {
        BukkitTask task = taskMap.remove(player);
        if (task != null) {
            task.cancel();
        }
        
        player.teleport(targetWorld.getSpawnLocation());
        player.sendMessage("§a传送完成！");
    }
    
    /**
     * 传送规则数据类
     */
    private static class TeleportRule {
        final String targetWorldName;
        final int threshold;
        final boolean above;
        
        TeleportRule(String targetWorldName, int threshold, boolean above) {
            this.targetWorldName = targetWorldName;
            this.threshold = threshold;
            this.above = above;
        }

        /**
         * 判断此高度是否在阈值内
         */
        public boolean shouldTeleport(Integer yPosition) {
            if (yPosition == null) {
                return false;
            }
            if (above) {
                return yPosition >= threshold;
            } else {
                return yPosition <= threshold;
            }
        }
    }
}