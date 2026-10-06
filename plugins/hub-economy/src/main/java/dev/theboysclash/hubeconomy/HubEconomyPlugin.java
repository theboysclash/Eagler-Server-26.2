package dev.theboysclash.hubeconomy;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class HubEconomyPlugin extends JavaPlugin {

    private PluginConfig pluginConfig;
    private EconomyStore economyStore;
    private WorthCatalog worthCatalog;
    private WorldService worldService;
    private HubNpc hubNpc;
    private SellMenuService sellMenuService;
    private CoinSidebar coinSidebar;
    private ShopMenu shopMenu;
    private AuctionHouse auctionHouse;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        pluginConfig = new PluginConfig(this);
        pluginConfig.ensureDefaults();

        economyStore = new EconomyStore(this);
        economyStore.load();

        worthCatalog = new WorthCatalog(this);
        worthCatalog.loadOrCreate();

        coinSidebar = new CoinSidebar(this, economyStore);
        economyStore.setBalanceChangeListener(coinSidebar::refresh);

        auctionHouse = new AuctionHouse(this, economyStore);
        auctionHouse.load();

        worldService = new WorldService(this, pluginConfig);
        worldService.setupWorlds();

        hubNpc = new HubNpc(this, pluginConfig, worldService);
        hubNpc.init();

        sellMenuService = new SellMenuService(this, economyStore, worthCatalog);
        shopMenu = new ShopMenu(this, economyStore, worthCatalog);

        HubEconomyCommands commands = new HubEconomyCommands(
                this, pluginConfig, worldService, hubNpc, economyStore, worthCatalog,
                sellMenuService, shopMenu, auctionHouse);
        register("sell", commands);
        register("bal", commands);
        register("balance", commands);
        register("worth", commands);
        register("hub", commands);
        register("survival", commands);
        register("sethub", commands);
        register("setsurvival", commands);
        register("hubeconomy", commands);
        register("shop", commands);
        register("ah", commands);

        getServer().getPluginManager().registerEvents(
                new HubListener(this, pluginConfig, worldService, hubNpc, coinSidebar), this);
        getServer().getPluginManager().registerEvents(sellMenuService, this);
        getServer().getPluginManager().registerEvents(shopMenu, this);
        getServer().getPluginManager().registerEvents(auctionHouse, this);

        getLogger().info("HubEconomy enabled.");
    }

    @Override
    public void onDisable() {
        if (auctionHouse != null) {
            auctionHouse.save();
        }
        if (economyStore != null) {
            economyStore.save();
        }
    }

    public void reloadPluginConfig() {
        reloadConfig();
        pluginConfig.ensureDefaults();
    }

    private void register(String name, HubEconomyCommands commands) {
        PluginCommand command = getCommand(name);
        if (command != null) {
            command.setExecutor(commands);
            if ("hubeconomy".equals(name)) {
                command.setTabCompleter(commands);
            }
        }
    }
}
