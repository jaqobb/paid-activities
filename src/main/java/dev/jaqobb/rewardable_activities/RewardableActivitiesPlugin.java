package dev.jaqobb.rewardable_activities;

import dev.jaqobb.rewardable_activities.command.RewardableActivitiesCommand;
import dev.jaqobb.rewardable_activities.command.RewardableActivitiesCommandTabCompleter;
import dev.jaqobb.rewardable_activities.data.RewardLimiterData;
import dev.jaqobb.rewardable_activities.data.RewardableActivityRepository;
import dev.jaqobb.rewardable_activities.listener.block.BlockBreakListener;
import dev.jaqobb.rewardable_activities.listener.block.BlockExplodeListener;
import dev.jaqobb.rewardable_activities.listener.block.BlockPistonExtendListener;
import dev.jaqobb.rewardable_activities.listener.block.BlockPistonRetractListener;
import dev.jaqobb.rewardable_activities.listener.block.BlockPlaceListener;
import dev.jaqobb.rewardable_activities.listener.entity.EntityBreedListener;
import dev.jaqobb.rewardable_activities.listener.entity.EntityDamageByEntityListener;
import dev.jaqobb.rewardable_activities.listener.entity.EntityExplodeListener;
import dev.jaqobb.rewardable_activities.listener.entity.SpawnerSpawnListener;
import dev.jaqobb.rewardable_activities.listener.player.PlayerFishListener;
import dev.jaqobb.rewardable_activities.listener.player.PlayerJoinListener;
import dev.jaqobb.rewardable_activities.listener.plugin.PluginDisableListener;
import dev.jaqobb.rewardable_activities.listener.plugin.PluginEnableListener;
import dev.jaqobb.rewardable_activities.updater.Updater;
import dev.jaqobb.rewardable_activities.util.TimeUtils;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.ChatColor;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.Metadatable;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class RewardableActivitiesPlugin extends JavaPlugin {
    
    private boolean rewardLimiterEnabled;
    private int rewardLimiterLimit;
    private String rewardLimiterLimitReachedMessage;
    private Instant rewardLimiterCooldown;
    private Map<UUID, RewardLimiterData> rewardLimiterData;
    private boolean blockBreakOwnershipCheckEnabled;
    private boolean blockPlaceOwnershipCheckEnabled;
    private boolean entityBreedOwnershipCheckEnabled;
    private boolean entitySpawnerOwnershipCheckEnabled;
    private RewardableActivityRepository repository;
    private boolean placeholderApiPresent;
    private Updater updater;
    private Economy economy;
    
    @Override
    public void onLoad() {
        this.saveDefaultConfig();
        this.loadConfig(false);
        PluginManager pluginManager = this.getServer().getPluginManager();
        this.placeholderApiPresent = pluginManager.getPlugin("PlaceholderAPI") != null;
        this.getLogger().log(Level.INFO, "PlaceholderAPI: " + (this.placeholderApiPresent ? "present" : "not present") + ".");
    }
    
    @Override
    public void onEnable() {
        this.getLogger().log(Level.INFO, "Starting updater...");
        this.updater = new Updater(this, 86090);
        this.getServer().getScheduler().runTaskTimerAsynchronously(this, this.updater, 0L, 20L * 60L * 60L);
        this.economy = this.setupEconomy();
        if (this.economy != null) {
            this.getLogger().log(Level.INFO, "Economy has been successfully setup.");
            this.getLogger().log(Level.INFO, "Economy provider: " + this.economy.getName());
        } else {
            this.getLogger().log(Level.INFO, "Could not find Vault or economy plugin, economy rewards will not be supported.");
        }
        this.getLogger().log(Level.INFO, "Registering command...");
        this.getCommand("rewardable-activities").setExecutor(new RewardableActivitiesCommand(this));
        this.getCommand("rewardable-activities").setTabCompleter(new RewardableActivitiesCommandTabCompleter());
        this.getLogger().log(Level.INFO, "Registering listeners...");
        PluginManager pluginManager = this.getServer().getPluginManager();
        pluginManager.registerEvents(new BlockBreakListener(this), this);
        pluginManager.registerEvents(new BlockExplodeListener(this), this);
        pluginManager.registerEvents(new BlockPistonExtendListener(this), this);
        pluginManager.registerEvents(new BlockPistonRetractListener(this), this);
        pluginManager.registerEvents(new BlockPlaceListener(this), this);
        pluginManager.registerEvents(new EntityBreedListener(this), this);
        pluginManager.registerEvents(new EntityDamageByEntityListener(this), this);
        pluginManager.registerEvents(new EntityExplodeListener(this), this);
        pluginManager.registerEvents(new SpawnerSpawnListener(this), this);
        pluginManager.registerEvents(new PlayerFishListener(this), this);
        pluginManager.registerEvents(new PlayerJoinListener(this), this);
        pluginManager.registerEvents(new PluginDisableListener(this), this);
        pluginManager.registerEvents(new PluginEnableListener(this), this);
    }
    
    public void loadConfig(boolean reload) {
        this.getLogger().log(Level.INFO, (reload ? "Rel" : "L") + "oading configuration...");
        this.rewardLimiterEnabled = this.getConfig().getBoolean("general.reward-limiter.enabled", false);
        this.rewardLimiterLimitReachedMessage = ChatColor.translateAlternateColorCodes('&', this.getConfig().getString("general.reward-limiter.limit-reached-message", "&cYou have reached the reward limit. You have to wait for a while before you can receive more rewards."));
        this.rewardLimiterLimit = this.getConfig().getInt("general.reward-limiter.limit", 10);
        String rewardLimiterCooldown = this.getConfig().getString("general.reward-limiter.cooldown", "10m");
        this.rewardLimiterCooldown = TimeUtils.parse(rewardLimiterCooldown);
        if (this.rewardLimiterLimit < 1 || this.rewardLimiterCooldown == null) {
            this.getLogger().log(Level.WARNING, "Reward limiter was not properly configured. As such, it will be disabled.");
            this.rewardLimiterEnabled = false;
        }
        this.getLogger().log(Level.INFO, "Reward limiter:");
        this.getLogger().log(Level.INFO, " * Enabled: " + (this.rewardLimiterEnabled ? "yes" : "no"));
        if (this.rewardLimiterEnabled) {
            this.getLogger().log(Level.INFO, " * Limit: " + this.rewardLimiterLimit);
            this.getLogger().log(Level.INFO, " * Cooldown: " + this.rewardLimiterCooldown.toEpochMilli() + " ms (" + rewardLimiterCooldown + ")");
            this.rewardLimiterData = new HashMap<>();
        }
        this.blockBreakOwnershipCheckEnabled = this.getConfig().getBoolean("block.ownership-check.break", this.getConfig().getBoolean("block.ownership-check.place", this.getConfig().getBoolean("block.ownership-check")));
        this.blockPlaceOwnershipCheckEnabled = this.getConfig().getBoolean("block.ownership-check.place", true);
        this.entityBreedOwnershipCheckEnabled = this.getConfig().getBoolean("entity.ownership-check.breed", this.getConfig().getBoolean("entity.ownership-check", true));
        this.entitySpawnerOwnershipCheckEnabled = this.getConfig().getBoolean("entity.ownership-check.spawner", true);
        if (this.repository == null) {
            this.repository = new RewardableActivityRepository(this);
        }
        this.repository.loadAllActivities(reload);
        this.getLogger().log(Level.INFO, "Rewardable activities:");
        this.getLogger().log(Level.INFO, " * Block break: " + this.repository.getBlockBreakActivities().size());
        this.getLogger().log(Level.INFO, " * Block place: " + this.repository.getBlockPlaceActivities().size());
        this.getLogger().log(Level.INFO, " * Entity kill: " + this.repository.getEntityKillActivities().size());
        this.getLogger().log(Level.INFO, " * Entity breed: " + this.repository.getEntityBreedActivities().size());
        this.getLogger().log(Level.INFO, " * Item fish: " + this.repository.getItemFishActivities().size());
    }
    
    public boolean isRewardLimiterEnabled() {
        return this.rewardLimiterEnabled;
    }
    
    public int getRewardLimiterLimit() {
        return this.rewardLimiterLimit;
    }
    
    public String getRewardLimiterLimitReachedMessage() {
        return this.rewardLimiterLimitReachedMessage;
    }
    
    public Instant getRewardLimiterCooldown() {
        return this.rewardLimiterCooldown;
    }
    
    public Map<UUID, RewardLimiterData> getRewardLimiterData() {
        return Collections.unmodifiableMap(this.rewardLimiterData);
    }
    
    public RewardLimiterData getRewardLimiterData(UUID uniqueId) {
        return this.rewardLimiterData.get(uniqueId);
    }
    
    public void setRewardLimiterData(UUID uniqueId, RewardLimiterData data) {
        this.rewardLimiterData.put(uniqueId, data);
    }
    
    public boolean isBlockBreakOwnershipCheckEnabled() {
        return this.blockBreakOwnershipCheckEnabled;
    }
    
    public boolean isBlockPlaceOwnershipCheckEnabled() {
        return this.blockPlaceOwnershipCheckEnabled;
    }
    
    public boolean isEntityBreedOwnershipCheckEnabled() {
        return this.entityBreedOwnershipCheckEnabled;
    }
    
    public boolean isEntitySpawnerOwnershipCheckEnabled() {
        return this.entitySpawnerOwnershipCheckEnabled;
    }
    
    public RewardableActivityRepository getRepository() {
        return this.repository;
    }
    
    public boolean isPlaceholderApiPresent() {
        return this.placeholderApiPresent;
    }
    
    public void setPlaceholderApiPresent(boolean present) {
        this.placeholderApiPresent = present;
    }
    
    public Updater getUpdater() {
        return this.updater;
    }
    
    public Economy getEconomy() {
        return this.economy;
    }
    
    public boolean hasMetadata(Metadatable metadatable, String key) {
        return metadatable.hasMetadata(key);
    }
    
    public void setMetadata(Metadatable metadatable, String key, Object value) {
        if (!metadatable.hasMetadata(key)) {
            metadatable.setMetadata(key, new FixedMetadataValue(this, value));
        }
    }
    
    public void unsetMetadata(Metadatable metadatable, String key) {
        if (metadatable.hasMetadata(key)) {
            metadatable.removeMetadata(key, this);
        }
    }
    
    public void updatePistonBlocks(BlockFace direction, List<Block> blocks) {
        List<Block> blocksPlacedByPlayer = new ArrayList<>(blocks.size());
        List<Block> blocksSoonToBePlacedByPlayer = new ArrayList<>(blocks.size());
        for (Block block : blocks) {
            if (!blocksPlacedByPlayer.contains(block) && this.hasMetadata(block, RewardableActivitiesConstants.BLOCK_PLACED_BY_PLAYER_KEY)) {
                blocksPlacedByPlayer.add(block);
            }
        }
        for (Block block : blocks) {
            if (blocksPlacedByPlayer.contains(block)) {
                if (!blocksSoonToBePlacedByPlayer.contains(block)) {
                    this.unsetMetadata(block, RewardableActivitiesConstants.BLOCK_PLACED_BY_PLAYER_KEY);
                }
                Block blockSoonToBePlacedByPlayer = block.getRelative(direction);
                blocksSoonToBePlacedByPlayer.add(blockSoonToBePlacedByPlayer);
                this.setMetadata(blockSoonToBePlacedByPlayer, RewardableActivitiesConstants.BLOCK_PLACED_BY_PLAYER_KEY, true);
            }
        }
    }
    
    private Economy setupEconomy() {
        if (!this.getServer().getPluginManager().isPluginEnabled("Vault")) {
            return null;
        }
        RegisteredServiceProvider<Economy> economyProvider = this.getServer().getServicesManager().getRegistration(Economy.class);
        if (economyProvider == null) {
            return null;
        }
        this.economy = economyProvider.getProvider();
        return this.economy;
    }
}
