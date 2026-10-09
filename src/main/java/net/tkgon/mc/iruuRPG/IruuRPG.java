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
    private MainMenu mainMenu;
    private MainMenuItemService mainMenuItemService;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        ItemKeys itemKeys = new ItemKeys(this);
        this.itemIdentifier = new ItemIdentifier(itemKeys);

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
        this.mainMenu = new MainMenu(equipmentService, levelService, statsMenu, skillTreeMenu, classService, classSelectMenu);
        this.mainMenuItemService = new MainMenuItemService(this);
        this.mobService.startTargetTask();
        this.levelService.startOrbTask();
        this.statusEffectService.startTask();
        this.mainMenuItemService.startTask();
        this.classService.startConditionTask(equipmentService, playerBars);

        registerCommands();
        registerListeners();
        loadOnlinePlayers();
        PlayerHudTask.start(this, profileManager, playerBars, levelService, new PlayerHud(levelService));
        PlayerResourceTask.start(this, profileManager, playerBars);
        getLogger().info("iruuRPG enabled.");
    }

    @Override
    public void onDisable() {
        if (profileManager != null) {
            profileManager.saveAll();
        }
        if (mobService != null) {
            mobService.clear();
        }
        getLogger().info("iruuRPG disabled.");
    }

    private void registerCommands() {
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

        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerProfile profile = equipmentService.recalculate(player);
            playerBars.sync(player, profile);
            levelService.syncBar(player, profile);
            mainMenuItemService.ensure(player);
        }
    }
}
