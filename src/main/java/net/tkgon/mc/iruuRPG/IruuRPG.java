package net.tkgon.mc.iruuRPG;

import net.tkgon.mc.iruuRPG.command.IruuRPGCommand;
import net.tkgon.mc.iruuRPG.command.ClassCommand;
import net.tkgon.mc.iruuRPG.classsystem.ClassRegistry;
import net.tkgon.mc.iruuRPG.classsystem.ClassService;
import net.tkgon.mc.iruuRPG.classsystem.ClassSkillRegistry;
import net.tkgon.mc.iruuRPG.command.StatsCommand;
import net.tkgon.mc.iruuRPG.combat.AttackCooldowns;
import net.tkgon.mc.iruuRPG.combat.AttackEffects;
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
import net.tkgon.mc.iruuRPG.dungeon.DungeonRegistry;
import net.tkgon.mc.iruuRPG.dungeon.DungeonService;
import net.tkgon.mc.iruuRPG.gui.DungeonMenu;
import net.tkgon.mc.iruuRPG.gui.MarketMenu;
import net.tkgon.mc.iruuRPG.item.ResourceSync;
import net.tkgon.mc.iruuRPG.market.MarketCommand;
import net.tkgon.mc.iruuRPG.market.MarketService;
import net.tkgon.mc.iruuRPG.trade.TradeCommand;
import net.tkgon.mc.iruuRPG.trade.TradeService;
import net.tkgon.mc.iruuRPG.item.RpgItemRegistry;
import net.tkgon.mc.iruuRPG.listener.CombatListener;
import net.tkgon.mc.iruuRPG.listener.DebugTargetListener;
import net.tkgon.mc.iruuRPG.listener.EquipmentChangeListener;
import net.tkgon.mc.iruuRPG.listener.ExperienceListener;
import net.tkgon.mc.iruuRPG.listener.MainMenuItemListener;
import net.tkgon.mc.iruuRPG.listener.PlayerLifecycleListener;
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

    @Override
    public void onEnable() {
        saveDefaultConfig();

        ItemKeys itemKeys = new ItemKeys(this);
        this.itemIdentifier = new ItemIdentifier(itemKeys);

        // plugin-owned data files: bring older copies on the server up to date (the old copy is kept as a backup)
        ResourceSync.sync(this, "items/craft.yml");
        ResourceSync.sync(this, "crafting/recipes.yml");
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
        this.playerBars = new PlayerBars();
        this.levelService = new LevelService(this, profileManager, equipmentService, playerBars);
        this.statusEffectService = new StatusEffectService(this, equipmentService, playerBars);
        this.debugTargetService = new DebugTargetService(this);

        AttackEffects attackEffects = new AttackEffects(this);
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
        this.marketService = new MarketService(this, profileManager);
        this.marketService.load();
        this.marketMenu = new MarketMenu(marketService, item -> !mainMenuItemService.isMenuItem(item));
        this.marketMenu.setMainMenu(mainMenu);
        this.dungeonRegistry = new DungeonRegistry(this);
        this.dungeonRegistry.reload();
        this.dungeonService = new DungeonService(this, dungeonRegistry, mobService, levelService, profileManager);
        this.dungeonMenu = new DungeonMenu(dungeonService);
        this.dungeonMenu.setMainMenu(mainMenu);
        this.mainMenu.setDungeonMenu(dungeonMenu);
        this.tradeService = new TradeService(this, profileManager, item -> !mainMenuItemService.isMenuItem(item));
        this.mainMenu.setMarketMenu(marketMenu);
        this.mobService.startTargetTask();
        this.levelService.startOrbTask();
        this.statusEffectService.startTask();
        this.mainMenuItemService.startTask();
        this.marketService.startTask();
        this.dungeonService.start();
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
        if (dungeonService != null) {
            dungeonService.shutdown();
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

        PluginCommand dungeonCommand = getCommand("dungeon");
        if (dungeonCommand != null) {
            DungeonCommand dungeon = new DungeonCommand(dungeonService, dungeonMenu, mobRegistry);
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

        IruuRPGCommand executor = new IruuRPGCommand(this::reloadPluginData, itemRegistry, itemFactory, debugTargetService, mobRegistry, mobService, statsMenu, skillTreeMenu, levelService);
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
        // registered first so the tutorial locks cancel events before the combat listeners act on them
        getServer().getPluginManager().registerEvents(new TutorialListener(tutorialService), this);
        getServer().getPluginManager().registerEvents(questBoardMenu, this);
        getServer().getPluginManager().registerEvents(craftMenu, this);
        getServer().getPluginManager().registerEvents(marketMenu, this);
        getServer().getPluginManager().registerEvents(marketService, this);
        getServer().getPluginManager().registerEvents(tradeService, this);
        getServer().getPluginManager().registerEvents(dungeonService, this);
        getServer().getPluginManager().registerEvents(dungeonMenu, this);
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
                new CombatListener(attackService, deployService, itemSkillService),
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
                new ClassSkillListener(classService, classSkillRegistry, profileManager, attackService, mainMenuItemService, classSkillService),
                this
        );
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
