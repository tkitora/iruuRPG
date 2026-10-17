package net.tkgon.mc.iruuRPG;

import net.tkgon.mc.iruuRPG.command.IruuRPGCommand;
import net.tkgon.mc.iruuRPG.command.ClassCommand;
import net.tkgon.mc.iruuRPG.classsystem.ClassRegistry;
import net.tkgon.mc.iruuRPG.classsystem.ClassService;
import net.tkgon.mc.iruuRPG.classsystem.ClassSkillRegistry;
import net.tkgon.mc.iruuRPG.command.StatsCommand;
import net.tkgon.mc.iruuRPG.combat.AttackCooldowns;
import net.tkgon.mc.iruuRPG.combat.AttackEffects;
import net.tkgon.mc.iruuRPG.combat.SoundSettings;
import net.tkgon.mc.iruuRPG.gui.SettingsMenu;
import net.tkgon.mc.iruuRPG.combat.AttackService;
import net.tkgon.mc.iruuRPG.combat.ClassEffectService;
import net.tkgon.mc.iruuRPG.combat.ClassSkillService;
import net.tkgon.mc.iruuRPG.combat.DamageCalculator;
import net.tkgon.mc.iruuRPG.combat.DeployService;
import net.tkgon.mc.iruuRPG.combat.ItemSkillService;
import net.tkgon.mc.iruuRPG.combat.StatusEffectService;
import net.tkgon.mc.iruuRPG.equipment.EquipmentService;
import net.tkgon.mc.iruuRPG.gui.ClassSelectMenu;
import net.tkgon.mc.iruuRPG.gui.MainMenu;
import net.tkgon.mc.iruuRPG.gui.MainMenuItemService;
import net.tkgon.mc.iruuRPG.gui.SkillTreeMenu;
import net.tkgon.mc.iruuRPG.gui.StatsMenu;
import net.tkgon.mc.iruuRPG.listener.ClassSkillListener;
import net.tkgon.mc.iruuRPG.hud.PlayerBars;
import net.tkgon.mc.iruuRPG.hud.PlayerHud;
import net.tkgon.mc.iruuRPG.hud.PlayerHudTask;
import net.tkgon.mc.iruuRPG.item.ItemIdentifier;
import net.tkgon.mc.iruuRPG.item.ItemKeys;
import net.tkgon.mc.iruuRPG.item.ItemSkillRegistry;
import net.tkgon.mc.iruuRPG.item.RpgItemFactory;
import net.tkgon.mc.iruuRPG.dungeon.DungeonCommand;
import net.tkgon.mc.iruuRPG.dungeon.DungeonEditor;
import net.tkgon.mc.iruuRPG.dungeon.DungeonRegistry;
import net.tkgon.mc.iruuRPG.dungeon.DungeonService;
import net.tkgon.mc.iruuRPG.gui.DungeonMenu;
import net.tkgon.mc.iruuRPG.gui.RewardChestMenu;
import net.tkgon.mc.iruuRPG.hud.SidebarService;
import net.tkgon.mc.iruuRPG.gui.PartyMenu;
import net.tkgon.mc.iruuRPG.party.PartyCommand;
import net.tkgon.mc.iruuRPG.party.PartyService;
import net.tkgon.mc.iruuRPG.gui.StorageMenu;
import net.tkgon.mc.iruuRPG.storage.StorageService;
import net.tkgon.mc.iruuRPG.gui.TravelMenu;
import net.tkgon.mc.iruuRPG.travel.TravelCommand;
import net.tkgon.mc.iruuRPG.travel.TravelService;
import net.tkgon.mc.iruuRPG.sync.ReloadReport;
import net.tkgon.mc.iruuRPG.sync.YmlSyncService;
import net.tkgon.mc.iruuRPG.gui.MarketMenu;
import net.tkgon.mc.iruuRPG.item.ResourceSync;
import net.tkgon.mc.iruuRPG.market.MarketCommand;
import net.tkgon.mc.iruuRPG.market.MarketService;
import net.tkgon.mc.iruuRPG.trade.TradeCommand;
import net.tkgon.mc.iruuRPG.trade.TradeService;
import net.tkgon.mc.iruuRPG.item.RpgItemRegistry;
import net.tkgon.mc.iruuRPG.listener.CombatListener;
import net.tkgon.mc.iruuRPG.listener.EntityPhysicsListener;
import net.tkgon.mc.iruuRPG.listener.DebugTargetListener;
import net.tkgon.mc.iruuRPG.listener.EquipmentChangeListener;
import net.tkgon.mc.iruuRPG.listener.ExperienceListener;
import net.tkgon.mc.iruuRPG.listener.MainMenuItemListener;
import net.tkgon.mc.iruuRPG.listener.PlayerLifecycleListener;
import net.tkgon.mc.iruuRPG.listener.RightClickGuard;
import net.tkgon.mc.iruuRPG.listener.PlayerResourceListener;
import net.tkgon.mc.iruuRPG.listener.RpgMobListener;
import net.tkgon.mc.iruuRPG.mob.DebugTargetService;
import net.tkgon.mc.iruuRPG.mob.RpgMobRegistry;
import net.tkgon.mc.iruuRPG.mob.RpgMobService;
import net.tkgon.mc.iruuRPG.player.LevelService;
import net.tkgon.mc.iruuRPG.player.PlayerProfile;
import net.tkgon.mc.iruuRPG.player.PlayerProfileManager;
import net.tkgon.mc.iruuRPG.player.PlayerProfileStorage;
import net.tkgon.mc.iruuRPG.player.PlayerResourceTask;
import net.tkgon.mc.iruuRPG.tutorial.TutorialCommand;
import net.tkgon.mc.iruuRPG.tutorial.TutorialListener;
import net.tkgon.mc.iruuRPG.tutorial.TutorialService;
import net.tkgon.mc.iruuRPG.crafting.CraftCommand;
import net.tkgon.mc.iruuRPG.crafting.CraftRegistry;
import net.tkgon.mc.iruuRPG.crafting.CraftService;
import net.tkgon.mc.iruuRPG.gui.CraftMenu;
import net.tkgon.mc.iruuRPG.gui.QuestBoardMenu;
import net.tkgon.mc.iruuRPG.quest.QuestCommand;
import net.tkgon.mc.iruuRPG.quest.QuestRegistry;
import net.tkgon.mc.iruuRPG.quest.QuestService;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class IruuRPG extends JavaPlugin {

    private RpgItemRegistry itemRegistry;
    private RpgItemFactory itemFactory;
    private ItemSkillRegistry itemSkillRegistry;
    private ClassRegistry classRegistry;
    private ClassSkillRegistry classSkillRegistry;
    private ClassService classService;
    private PlayerProfileManager profileManager;
    private EquipmentService equipmentService;
    private PlayerBars playerBars;
    private ItemIdentifier itemIdentifier;
    private DebugTargetService debugTargetService;
    private RpgMobRegistry mobRegistry;
    private RpgMobService mobService;
    private AttackService attackService;
    private DeployService deployService;
    private ClassSkillService classSkillService;
    private ClassEffectService classEffectService;
    private ItemSkillService itemSkillService;
    private StatusEffectService statusEffectService;
    private LevelService levelService;
    private StatsMenu statsMenu;
    private SkillTreeMenu skillTreeMenu;
    private ClassSelectMenu classSelectMenu;
    private TutorialService tutorialService;
    private QuestRegistry questRegistry;
    private QuestService questService;
    private QuestBoardMenu questBoardMenu;
    private CraftRegistry craftRegistry;
    private CraftService craftService;
    private CraftMenu craftMenu;
    private MainMenu mainMenu;
    private MainMenuItemService mainMenuItemService;
    private MarketService marketService;
    private MarketMenu marketMenu;
    private TradeService tradeService;
    private DungeonRegistry dungeonRegistry;
    private DungeonService dungeonService;
    private DungeonMenu dungeonMenu;
    private DungeonEditor dungeonEditor;
    private RewardChestMenu rewardChestMenu;
    private SidebarService sidebarService;
    private PartyService partyService;
    private PartyMenu partyMenu;
    private SoundSettings soundSettings;
    private AttackEffects attackEffectsRef;
    private SettingsMenu settingsMenu;
    private TravelService travelService;
    private TravelMenu travelMenu;
    private StorageService storageService;
    private StorageMenu storageMenu;
    private YmlSyncService ymlSyncService;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        ItemKeys itemKeys = new ItemKeys(this);
        this.itemIdentifier = new ItemIdentifier(itemKeys);

        // plugin-owned data files: bring older copies on the server up to date (the old copy is kept as a backup)
        ResourceSync.sync(this, "items/craft.yml");
        ResourceSync.sync(this, "crafting/recipes.yml");
        ResourceSync.sync(this, "items/dungeon.yml");
        ResourceSync.sync(this, "mobs/dungeon.yml");
        ResourceSync.sync(this, "mobs/quest.yml");
        ResourceSync.sync(this, "loot/cave.yml");
        ResourceSync.sync(this, "loot/cave_road.yml");
        this.itemRegistry = new RpgItemRegistry(this);
        this.itemSkillRegistry = new ItemSkillRegistry(this);
        this.itemSkillRegistry.reload();
        this.itemRegistry.reload();
        this.classRegistry = new ClassRegistry(this);
        this.classRegistry.reload();
        this.classSkillRegistry = new ClassSkillRegistry(this);
        this.classSkillRegistry.reload();
        this.itemFactory = new RpgItemFactory(this, itemIdentifier, itemSkillRegistry);
        this.mobRegistry = new RpgMobRegistry(this);
        this.mobRegistry.reload();
        this.profileManager = new PlayerProfileManager(new PlayerProfileStorage(this));
        this.equipmentService = new EquipmentService(profileManager, itemIdentifier, itemRegistry);
        this.classService = new ClassService(this, classRegistry);
        this.equipmentService.setClassService(classService);
        this.playerBars = new PlayerBars(this);
        this.levelService = new LevelService(this, profileManager, equipmentService, playerBars);
        this.statusEffectService = new StatusEffectService(this, equipmentService, playerBars);
        this.debugTargetService = new DebugTargetService(this);

        this.soundSettings = new SoundSettings(this);
        AttackEffects attackEffects = new AttackEffects(this, soundSettings);
        this.statusEffectService.setAttackEffects(attackEffects);
        this.attackService = new AttackService(
                this,
                profileManager,
                equipmentService,
                itemIdentifier,
                itemRegistry,
                new DamageCalculator(),
                new AttackCooldowns(this),
                attackEffects,
                playerBars,
                statusEffectService
        );
        this.mobService = new RpgMobService(this, mobRegistry, itemRegistry, itemFactory, levelService, statusEffectService);
        this.attackEffectsRef = attackEffects;
        this.mobService.setAttackEffects(attackEffects);
        this.statusEffectService.setMobService(mobService);
        this.attackService.setMobService(mobService);
        this.attackService.setDebugTargetService(debugTargetService);
        this.attackService.setClassService(classService);
        this.classSkillService = new ClassSkillService(this, attackService, classService, profileManager);
        this.classSkillService.setStatusEffectService(statusEffectService);
        this.classEffectService = new ClassEffectService(this, classService, profileManager, statusEffectService);
        this.attackService.setClassEffectService(classEffectService);
        this.classSkillService.setClassEffectService(classEffectService);
        this.classEffectService.setAttackService(attackService);
        this.deployService = new DeployService(this, attackService, attackEffects, itemSkillRegistry);
        this.itemSkillService = new ItemSkillService(this, attackService, attackEffects, itemSkillRegistry);
        this.statsMenu = new StatsMenu(equipmentService, levelService);
        this.skillTreeMenu = new SkillTreeMenu(equipmentService, classService, classSkillRegistry, profileManager, playerBars);
        this.classSelectMenu = new ClassSelectMenu(equipmentService, classService, profileManager, playerBars);
        this.tutorialService = new TutorialService(this, profileManager, equipmentService, classService, classSelectMenu, itemRegistry, itemFactory, playerBars);
        this.attackService.setTutorialHook(tutorialService);
        this.statusEffectService.setTutorialHook(tutorialService);
        this.questRegistry = new QuestRegistry(this);
        this.questRegistry.reload();
        this.questService = new QuestService(this, questRegistry, profileManager, itemIdentifier);
        this.questBoardMenu = new QuestBoardMenu(questService);
        this.mobService.setKillHook(questService::onMobKilled);
        this.craftRegistry = new CraftRegistry(this);
        this.craftRegistry.reload();
        this.craftService = new CraftService(this, craftRegistry, profileManager, itemIdentifier, itemRegistry, itemFactory);
        this.craftMenu = new CraftMenu(this, craftService, itemRegistry, itemFactory);
        this.mainMenu = new MainMenu(equipmentService, levelService, statsMenu, skillTreeMenu, classService, classSelectMenu);
        this.mainMenu.setCraftMenu(craftMenu);
        this.mainMenu.setQuestBoardMenu(questBoardMenu);
        this.statsMenu.setMainMenu(mainMenu);
        this.skillTreeMenu.setMainMenu(mainMenu);
        this.classSelectMenu.setMainMenu(mainMenu);
        this.mainMenuItemService = new MainMenuItemService(this);
        this.settingsMenu = new SettingsMenu(soundSettings, attackEffectsRef.sounds(), mainMenuItemService);
        this.settingsMenu.setMainMenu(mainMenu);
        this.mainMenu.setSettingsMenu(settingsMenu);
        this.marketService = new MarketService(this, profileManager);
        this.marketService.load();
        this.marketMenu = new MarketMenu(this, marketService, item -> !mainMenuItemService.isMenuItem(item));
        this.marketMenu.setMainMenu(mainMenu);
        this.dungeonRegistry = new DungeonRegistry(this);
        this.dungeonRegistry.reload();
        this.dungeonService = new DungeonService(this, dungeonRegistry, mobService, mobRegistry, levelService, profileManager);
        this.dungeonMenu = new DungeonMenu(dungeonService);
        this.dungeonEditor = new DungeonEditor(this, dungeonService, mobRegistry);
        this.rewardChestMenu = new RewardChestMenu(this);
        this.dungeonService.setRewardTools(itemRegistry, itemFactory, rewardChestMenu);
        this.sidebarService = new SidebarService(this, profileManager);
        this.sidebarService.setDungeonService(dungeonService);
        this.sidebarService.setClassService(classService);
        this.partyService = new PartyService(this);
        this.partyService.load();
        this.partyMenu = new PartyMenu(partyService);
        this.partyMenu.setMainMenu(mainMenu);
        this.mainMenu.setPartyMenu(partyMenu);
        this.sidebarService.setPartyService(partyService);
        this.dungeonService.setPartyService(partyService);
        this.mobService.setPartyService(partyService);
        this.travelService = new TravelService(this, profileManager);
        this.travelMenu = new TravelMenu(travelService);
        this.travelMenu.setMainMenu(mainMenu);
        this.mainMenu.setTravelMenu(travelMenu);
        this.travelService.start();
        this.storageService = new StorageService(this, profileManager);
        this.storageMenu = new StorageMenu(this, storageService, item -> !mainMenuItemService.isMenuItem(item));
        this.storageMenu.start();
        this.dungeonMenu.setMainMenu(mainMenu);
        this.dungeonService.setGateHandler(dungeonMenu::openConfirm);
        this.mainMenu.setDungeonMenu(dungeonMenu);
        this.tradeService = new TradeService(this, profileManager, item -> !mainMenuItemService.isMenuItem(item));
        this.mainMenu.setMarketMenu(marketMenu);
        this.mobService.startTargetTask();
        this.levelService.startOrbTask();
        this.statusEffectService.startTask();
        this.mainMenuItemService.startTask();
        this.marketService.startTask();
        this.dungeonService.start();
        this.sidebarService.start();
        this.ymlSyncService = new YmlSyncService(this, this::reloadWithReport);
        this.ymlSyncService.start();
        this.classService.startConditionTask(equipmentService, playerBars);

        registerCommands();
        registerListeners();
        loadOnlinePlayers();
        Bukkit.getScheduler().runTask(this, tutorialService::sweepEntities);
        PlayerHudTask.start(this, profileManager, playerBars, levelService, new PlayerHud(levelService));
        PlayerResourceTask.start(this, profileManager, playerBars);
        getLogger().info("iruuRPG enabled.");
    }

    @Override
    public void onDisable() {
        if (dungeonEditor != null) {
            dungeonEditor.shutdown();
        }
        if (travelService != null) {
            travelService.shutdown();
        }
        if (storageMenu != null) {
            storageMenu.shutdown();
        }
        if (dungeonService != null) {
            dungeonService.shutdown();
        }
        if (sidebarService != null) {
            sidebarService.shutdown();
        }
        if (tutorialService != null) {
            tutorialService.shutdown();
        }
        if (profileManager != null) {
            profileManager.saveAll();
        }
        if (marketService != null) {
            marketService.save();
        }
        if (mobService != null) {
            mobService.clear();
        }
        getLogger().info("iruuRPG disabled.");
    }

    private void registerCommands() {
        PluginCommand craftCommand = getCommand("craft");
        if (craftCommand != null) {
            CraftCommand craft = new CraftCommand(craftService, craftMenu);
            craftCommand.setExecutor(craft);
            craftCommand.setTabCompleter(craft);
        }

        PluginCommand menuCommand = getCommand("menu");
        if (menuCommand != null) {
            menuCommand.setExecutor((sender, command, label, args) -> {
                if (!(sender instanceof Player player)) return true;
                String sub = args.length == 0 ? "" : args[0].toLowerCase(java.util.Locale.ROOT);
                switch (sub) {
                    case "hide", "off" -> setMenuItemHidden(player, true);
                    case "show", "on" -> setMenuItemHidden(player, false);
                    case "toggle", "item" -> setMenuItemHidden(player, !mainMenuItemService.isHidden(player));
                    case "" -> mainMenu.open(player);
                    default -> player.sendMessage("[メニュー] /menu = 開く / /menu toggle = ホットバーのメニューアイテムの表示切替 (hide / show)");
                }
                return true;
            });
            menuCommand.setTabCompleter((sender, command, alias, args) ->
                    args.length == 1 ? java.util.List.of("toggle", "hide", "show").stream()
                            .filter(name -> name.startsWith(args[0].toLowerCase(java.util.Locale.ROOT))).toList() : java.util.List.of());
        }

        PluginCommand storageCommand = getCommand("storage");
        if (storageCommand != null) {
            storageCommand.setExecutor((sender, command, label, args) -> {
                if (!(sender instanceof Player player)) return true;
                if (!player.hasPermission("iruurpg.admin")) {
                    player.sendMessage("[ストレージ] 各地のエンダーチェストから開けます。");
                    return true;
                }
                storageMenu.open(player, 0); // admins: anywhere, for testing
                return true;
            });
        }

        PluginCommand travelCommand = getCommand("travel");
        if (travelCommand != null) {
            TravelCommand travel = new TravelCommand(travelService, travelMenu);
            travelCommand.setExecutor(travel);
            travelCommand.setTabCompleter(travel);
        }

        PartyCommand party = new PartyCommand(partyService, partyMenu);
        for (String name : new String[]{"party", "pc", "p"}) {
            PluginCommand partyCommand = getCommand(name);
            if (partyCommand != null) {
                partyCommand.setExecutor(party);
                partyCommand.setTabCompleter(party);
            }
        }

        net.tkgon.mc.iruuRPG.chat.ChatItems chatItems = new net.tkgon.mc.iruuRPG.chat.ChatItems();
        getServer().getPluginManager().registerEvents(chatItems, this);
        PluginCommand showItem = getCommand("showitem");
        if (showItem != null) showItem.setExecutor(chatItems);

        PluginCommand dungeonCommand = getCommand("dungeon");
        if (dungeonCommand != null) {
            DungeonCommand dungeon = new DungeonCommand(dungeonService, dungeonMenu, mobRegistry);
            dungeon.setEditor(dungeonEditor);
            dungeonCommand.setExecutor(dungeon);
            dungeonCommand.setTabCompleter(dungeon);
        }

        PluginCommand tradeCommand = getCommand("trade");
        if (tradeCommand != null) {
            TradeCommand trade = new TradeCommand(tradeService);
            tradeCommand.setExecutor(trade);
            tradeCommand.setTabCompleter(trade);
        }

        PluginCommand marketCommand = getCommand("market");
        if (marketCommand != null) {
            marketCommand.setExecutor(new MarketCommand(marketMenu));
        }

        PluginCommand questCommand = getCommand("quest");
        if (questCommand != null) {
            QuestCommand quest = new QuestCommand(questService, questBoardMenu);
            questCommand.setExecutor(quest);
            questCommand.setTabCompleter(quest);
        }

        TutorialCommand tutorialCommand = new TutorialCommand(tutorialService);
        for (String name : new String[]{"tutorialstart", "tutorialstop"}) {
            PluginCommand tutorial = getCommand(name);
            if (tutorial != null) tutorial.setExecutor(tutorialCommand);
        }

        PluginCommand command = getCommand("iruurpg");
        if (command == null) {
            getLogger().severe("Command iruurpg is missing from plugin.yml.");
            return;
        }

        IruuRPGCommand executor = new IruuRPGCommand(() -> {
            ReloadReport report = reloadWithReport(null, null, "/iruurpg reload");
            return "警告 " + report.warningCount() + " 件 (plugins/iruuRPG/reload-report.txt)";
        }, itemRegistry, itemFactory, debugTargetService, mobRegistry, mobService, statsMenu, skillTreeMenu, levelService);
        executor.setSyncAction(ymlSyncService::sync);
        command.setExecutor(executor);
        command.setTabCompleter(executor);

        PluginCommand statsCommand = getCommand("stats");
        if (statsCommand == null) {
            getLogger().severe("Command stats is missing from plugin.yml.");
            return;
        }

        StatsCommand statsExecutor = new StatsCommand(statsMenu);
        statsCommand.setExecutor(statsExecutor);
        statsCommand.setTabCompleter(statsExecutor);

        PluginCommand classCommand = getCommand("class");
        if (classCommand == null) {
            getLogger().severe("Command class is missing from plugin.yml.");
            return;
        }

        ClassCommand classExecutor = new ClassCommand(skillTreeMenu, classSelectMenu);
        classCommand.setExecutor(classExecutor);
        classCommand.setTabCompleter(classExecutor);
    }

    private void registerListeners() {
        RightClickGuard rightClickGuard = new RightClickGuard();
        EntityPhysicsListener entityPhysics = new EntityPhysicsListener(this);
        getServer().getPluginManager().registerEvents(entityPhysics, this);
        entityPhysics.applyToLoaded();
        // registered first so the tutorial locks cancel events before the combat listeners act on them
        getServer().getPluginManager().registerEvents(new TutorialListener(tutorialService), this);
        getServer().getPluginManager().registerEvents(questBoardMenu, this);
        getServer().getPluginManager().registerEvents(craftMenu, this);
        getServer().getPluginManager().registerEvents(marketMenu, this);
        getServer().getPluginManager().registerEvents(marketService, this);
        getServer().getPluginManager().registerEvents(tradeService, this);
        getServer().getPluginManager().registerEvents(dungeonService, this);
        getServer().getPluginManager().registerEvents(dungeonMenu, this);
        getServer().getPluginManager().registerEvents(dungeonEditor, this);
        getServer().getPluginManager().registerEvents(rewardChestMenu, this);
        getServer().getPluginManager().registerEvents(sidebarService, this);
        getServer().getPluginManager().registerEvents(partyService, this);
        getServer().getPluginManager().registerEvents(partyMenu, this);
        getServer().getPluginManager().registerEvents(soundSettings, this);
        getServer().getPluginManager().registerEvents(settingsMenu, this);
        getServer().getPluginManager().registerEvents(travelService, this);
        getServer().getPluginManager().registerEvents(travelMenu, this);
        getServer().getPluginManager().registerEvents(storageMenu, this);
        getServer().getPluginManager().registerEvents(craftService, this);
        getServer().getPluginManager().registerEvents(
                new PlayerLifecycleListener(this, profileManager, equipmentService, playerBars, levelService, classService, classSelectMenu),
                this
        );
        getServer().getPluginManager().registerEvents(
                new EquipmentChangeListener(this, equipmentService, playerBars),
                this
        );
        getServer().getPluginManager().registerEvents(
                new CombatListener(attackService, deployService, itemSkillService, rightClickGuard),
                this
        );
        getServer().getPluginManager().registerEvents(
                new PlayerResourceListener(profileManager, playerBars),
                this
        );
        getServer().getPluginManager().registerEvents(
                new DebugTargetListener(this, debugTargetService),
                this
        );
        getServer().getPluginManager().registerEvents(
                new RpgMobListener(this, mobService),
                this
        );
        getServer().getPluginManager().registerEvents(
                new ExperienceListener(levelService),
                this
        );
        getServer().getPluginManager().registerEvents(statsMenu, this);
        getServer().getPluginManager().registerEvents(skillTreeMenu, this);
        getServer().getPluginManager().registerEvents(classSelectMenu, this);
        getServer().getPluginManager().registerEvents(mainMenu, this);
        getServer().getPluginManager().registerEvents(
                new MainMenuItemListener(this, mainMenu, mainMenuItemService),
                this
        );
        getServer().getPluginManager().registerEvents(
                new ClassSkillListener(classService, classSkillRegistry, profileManager, attackService, mainMenuItemService, classSkillService, rightClickGuard),
                this
        );
    }

    private void setMenuItemHidden(Player player, boolean hidden) {
        mainMenuItemService.setHidden(player, hidden);
        player.sendMessage(hidden
                ? "[メニュー] ホットバーのメニューアイテムを消しました。/menu でメニューを開けます。戻すには /menu show"
                : "[メニュー] ホットバーのメニューアイテムを戻しました。");
    }

    private void loadOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            profileManager.load(player);
            PlayerProfile profile = equipmentService.recalculate(player);
            playerBars.sync(player, profile);
            levelService.syncBar(player, profile);
            mainMenuItemService.ensure(player);
        }
    }

    /**
     * Reloads the data (items, mobs, recipes, item skills, ...) and writes plugins/iruuRPG/reload-report.txt with the
     * counts and every problem found. A failure while loading never leaves things half empty (see the registries).
     */
    private ReloadReport reloadWithReport(String before, String after, String trigger) {
        ReloadReport report = new ReloadReport();
        report.capture(getLogger(), this::reloadPluginData);
        report.check(getDataFolder(), itemRegistry);
        report.header(before, after, trigger, itemRegistry.all().size(), mobRegistry.all().size(),
                craftRegistry.all().size(), itemSkillRegistry.size());
        try {
            report.write(new java.io.File(getDataFolder(), "reload-report.txt"));
        } catch (java.io.IOException e) {
            getLogger().warning("could not write reload-report.txt: " + e.getMessage());
        }
        return report;
    }

    private void reloadPluginData() {
        profileManager.saveAll();
        reloadConfig();
        itemSkillRegistry.reload();
        classRegistry.reload();
        classSkillRegistry.reload();
        itemRegistry.reload();
        mobRegistry.reload();
        questRegistry.reload();
        craftRegistry.reload();
        questService.rerollNow();

        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerProfile profile = equipmentService.recalculate(player);
            playerBars.sync(player, profile);
            levelService.syncBar(player, profile);
            mainMenuItemService.ensure(player);
        }
    }
}
