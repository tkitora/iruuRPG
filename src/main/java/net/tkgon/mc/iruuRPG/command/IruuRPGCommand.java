package net.tkgon.mc.iruuRPG.command;

import net.tkgon.mc.iruuRPG.gui.StatsMenu;
import net.tkgon.mc.iruuRPG.gui.SkillTreeMenu;
import net.tkgon.mc.iruuRPG.item.RpgItemDefinition;
import net.tkgon.mc.iruuRPG.item.RpgItemFactory;
import net.tkgon.mc.iruuRPG.item.RpgItemRegistry;
import net.tkgon.mc.iruuRPG.mob.DebugTargetService;
import net.tkgon.mc.iruuRPG.mob.RpgMobRegistry;
import net.tkgon.mc.iruuRPG.mob.RpgMobService;
import net.tkgon.mc.iruuRPG.player.LevelService;
import net.tkgon.mc.iruuRPG.player.PlayerProfile;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class IruuRPGCommand implements CommandExecutor, TabCompleter {

    private final ReloadAction reloadAction;
    private final RpgItemRegistry itemRegistry;
    private final RpgItemFactory itemFactory;
    private final DebugTargetService debugTargetService;
    private final RpgMobRegistry mobRegistry;
    private final RpgMobService mobService;
    private final StatsMenu statsMenu;
    private final SkillTreeMenu skillTreeMenu;
    private final LevelService levelService;

    public IruuRPGCommand(
            ReloadAction reloadAction,
            RpgItemRegistry itemRegistry,
            RpgItemFactory itemFactory,
            DebugTargetService debugTargetService,
            RpgMobRegistry mobRegistry,
            RpgMobService mobService,
            StatsMenu statsMenu,
            SkillTreeMenu skillTreeMenu,
            LevelService levelService
    ) {
        this.reloadAction = reloadAction;
        this.itemRegistry = itemRegistry;
        this.itemFactory = itemFactory;
        this.debugTargetService = debugTargetService;
        this.mobRegistry = mobRegistry;
        this.mobService = mobService;
        this.statsMenu = statsMenu;
        this.skillTreeMenu = skillTreeMenu;
        this.levelService = levelService;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            sendStatus(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase(Locale.ROOT);
        return switch (subCommand) {
            case "reload" -> reload(sender);
            case "stats", "stat", "profile" -> openStats(sender);
            case "class", "classes", "skilltree", "tree" -> openClass(sender);
            case "level", "lv", "xp" -> level(sender, args);
            case "levelup" -> levelUpShortcut(sender, args);
            case "setlevel" -> setLevelShortcut(sender, args);
            case "reset", "resetlevel" -> resetLevelShortcut(sender, args);
            case "addxp" -> addXpShortcut(sender, args);
            case "item" -> giveItem(sender, args);
            case "mob", "enemy" -> spawnMob(sender, args);
            case "dummy", "target" -> spawnDummy(sender);
            default -> {
                sender.sendMessage("Usage: /iruurpg <status|stats|class|level|reload|item|mob|dummy>");
                yield true;
            }
        };
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (args.length == 1) {
            return startsWith(List.of("status", "stats", "stat", "profile", "class", "classes", "skilltree", "tree", "level", "lv", "xp", "levelup", "setlevel", "reset", "resetlevel", "addxp", "reload", "item", "mob", "enemy", "dummy", "target"), args[0]);
        }

        if (args[0].equalsIgnoreCase("level") || args[0].equalsIgnoreCase("lv") || args[0].equalsIgnoreCase("xp")) {
            if (args.length == 2) {
                List<String> values = new ArrayList<>(List.of("show", "reset", "set", "levelup", "addxp"));
                values.addAll(onlinePlayerNames());
                return startsWith(values, args[1]);
            }
            if (args.length == 4 && (args[1].equalsIgnoreCase("set") || args[1].equalsIgnoreCase("levelup") || args[1].equalsIgnoreCase("addxp"))) {
                return startsWith(onlinePlayerNames(), args[3]);
            }
            if (args.length == 3 && args[1].equalsIgnoreCase("reset")) {
                return startsWith(onlinePlayerNames(), args[2]);
            }
        }

        if ((args[0].equalsIgnoreCase("levelup") || args[0].equalsIgnoreCase("setlevel") || args[0].equalsIgnoreCase("addxp"))
                && args.length == 3) {
            return startsWith(onlinePlayerNames(), args[2]);
        }

        if ((args[0].equalsIgnoreCase("reset") || args[0].equalsIgnoreCase("resetlevel")) && args.length == 2) {
            return startsWith(onlinePlayerNames(), args[1]);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("item")) {
            return startsWith(new ArrayList<>(itemRegistry.all().keySet()), args[1]);
        }

        if (args.length == 2 && (args[0].equalsIgnoreCase("mob") || args[0].equalsIgnoreCase("enemy"))) {
            return startsWith(new ArrayList<>(mobRegistry.all().keySet()), args[1]);
        }

        return List.of();
    }

    private void sendStatus(CommandSender sender) {
        sender.sendMessage("iruuRPG is running.");
        sender.sendMessage("Loaded items: " + itemRegistry.all().size());
        sender.sendMessage("Loaded mobs: " + mobRegistry.all().size());
        if (!itemRegistry.all().isEmpty()) {
            sender.sendMessage("Item ids: " + String.join(", ", itemRegistry.all().keySet()));
        }
        if (!mobRegistry.all().isEmpty()) {
            sender.sendMessage("Mob ids: " + String.join(", ", mobRegistry.all().keySet()));
        }
    }

    private boolean reload(CommandSender sender) {
        if (!sender.hasPermission("iruurpg.admin")) {
            sender.sendMessage("No permission.");
            return true;
        }

        reloadAction.reload();
        sender.sendMessage("iruuRPG config, skills, classes, items, and mobs were reloaded.");
        return true;
    }

    private boolean openStats(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }

        statsMenu.open(player);
        return true;
    }

    private boolean openClass(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }

        skillTreeMenu.open(player);
        return true;
    }

    private boolean level(CommandSender sender, String[] args) {
        if (args.length == 1) {
            Player target = targetOrSelf(sender, args, 1);
            if (target == null) return true;

            sendLevelStatus(sender, target);
            return true;
        }

        String action = args[1].toLowerCase(Locale.ROOT);
        return switch (action) {
            case "show", "status" -> {
                Player target = targetOrSelf(sender, args, 2);
                if (target != null) {
                    sendLevelStatus(sender, target);
                }
                yield true;
            }
            case "reset" -> {
                Player target = adminTargetOrSelf(sender, args, 2);
                if (target != null) {
                    levelService.reset(target);
                    sender.sendMessage("Reset level: " + target.getName());
                }
                yield true;
            }
            case "set" -> setLevel(sender, args, 2, 3);
            case "levelup", "up" -> levelUp(sender, args, 2, 3);
            case "addxp", "xpadd" -> addXp(sender, args, 2, 3);
            default -> {
                Player target = targetOrSelf(sender, args, 1);
                if (target != null) {
                    sendLevelStatus(sender, target);
                }
                yield true;
            }
        };
    }

    private boolean levelUpShortcut(CommandSender sender, String[] args) {
        return levelUp(sender, args, 1, 2);
    }

    private boolean setLevelShortcut(CommandSender sender, String[] args) {
        return setLevel(sender, args, 1, 2);
    }

    private boolean resetLevelShortcut(CommandSender sender, String[] args) {
        Player target = adminTargetOrSelf(sender, args, 1);
        if (target != null) {
            levelService.reset(target);
            sender.sendMessage("Reset level: " + target.getName());
        }
        return true;
    }

    private boolean addXpShortcut(CommandSender sender, String[] args) {
        return addXp(sender, args, 1, 2);
    }

    private boolean setLevel(CommandSender sender, String[] args, int levelIndex, int targetIndex) {
        if (!requireAdmin(sender)) return true;
        if (args.length <= levelIndex) {
            sender.sendMessage("Usage: /iruurpg level set <level> [player]");
            return true;
        }

        Integer level = parseInt(args[levelIndex]);
        if (level == null) {
            sender.sendMessage("Invalid level: " + args[levelIndex]);
            return true;
        }

        Player target = targetOrSelf(sender, args, targetIndex);
        if (target == null) return true;

        levelService.setLevel(target, level);
        sender.sendMessage("Set level: " + target.getName() + " -> Lv" + levelService.profile(target).level());
        return true;
    }

    private boolean levelUp(CommandSender sender, String[] args, int amountIndex, int targetIndex) {
        if (!requireAdmin(sender)) return true;

        int amount = 1;
        int resolvedTargetIndex = amountIndex;
        if (args.length > amountIndex) {
            Integer parsed = parseInt(args[amountIndex]);
            if (parsed != null) {
                amount = Math.max(1, parsed);
                resolvedTargetIndex = targetIndex;
            }
        }

        Player target = targetOrSelf(sender, args, resolvedTargetIndex);
        if (target == null) return true;

        levelService.levelUp(target, amount);
        sender.sendMessage("Level up: " + target.getName() + " -> Lv" + levelService.profile(target).level());
        return true;
    }

    private boolean addXp(CommandSender sender, String[] args, int amountIndex, int targetIndex) {
        if (!requireAdmin(sender)) return true;
        if (args.length <= amountIndex) {
            sender.sendMessage("Usage: /iruurpg level addxp <amount> [player]");
            return true;
        }

        Long amount = parseLong(args[amountIndex]);
        if (amount == null || amount <= 0L) {
            sender.sendMessage("Invalid XP amount: " + args[amountIndex]);
            return true;
        }

        Player target = targetOrSelf(sender, args, targetIndex);
        if (target == null) return true;

        levelService.addXp(target, amount);
        sender.sendMessage("Added XP: " + target.getName() + " +" + format(amount));
        return true;
    }

    private void sendLevelStatus(CommandSender sender, Player target) {
        PlayerProfile profile = levelService.profile(target);
        long required = levelService.requiredXpForNextLevel(profile.level());
        String xp = required <= 0L
                ? "MAX"
                : format(profile.xp()) + "/" + format(required) + " (" + Math.round(levelService.progress(profile) * 100.0f) + "%)";

        sender.sendMessage("[iruuRPG] " + target.getName() + " Lv" + profile.level() + " XP " + xp);
    }

    private boolean giveItem(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }

        if (!sender.hasPermission("iruurpg.admin")) {
            sender.sendMessage("No permission.");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage("Usage: /iruurpg item <itemId>");
            return true;
        }

        String itemId = args[1];
        RpgItemDefinition definition = itemRegistry.find(itemId).orElse(null);
        if (definition == null) {
            sender.sendMessage("Unknown item id: " + itemId);
            return true;
        }

        player.getInventory().addItem(itemFactory.create(definition));
        sender.sendMessage("Gave item: " + itemId);
        return true;
    }

    private boolean spawnMob(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }

        if (!sender.hasPermission("iruurpg.admin")) {
            sender.sendMessage("No permission.");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage("Usage: /iruurpg mob <mobId>");
            return true;
        }

        String mobId = args[1];
        LivingEntity mob = mobService.spawn(player, mobId).orElse(null);
        if (mob == null) {
            sender.sendMessage("Unknown mob id: " + mobId);
            return true;
        }

        sender.sendMessage("Spawned mob: " + mobId);
        return true;
    }

    private boolean spawnDummy(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }

        if (!sender.hasPermission("iruurpg.admin")) {
            sender.sendMessage("No permission.");
            return true;
        }

        LivingEntity target = debugTargetService.spawn(player);
        sender.sendMessage("Spawned debug target: " + target.getUniqueId());
        return true;
    }

    private boolean requireAdmin(CommandSender sender) {
        if (sender.hasPermission("iruurpg.admin")) {
            return true;
        }

        sender.sendMessage("No permission.");
        return false;
    }

    private Player adminTargetOrSelf(CommandSender sender, String[] args, int targetIndex) {
        if (!requireAdmin(sender)) return null;
        return targetOrSelf(sender, args, targetIndex);
    }

    private Player targetOrSelf(CommandSender sender, String[] args, int targetIndex) {
        if (args.length > targetIndex) {
            Player target = findPlayer(args[targetIndex]);
            if (target == null) {
                sender.sendMessage("Player is not online: " + args[targetIndex]);
            }
            return target;
        }

        if (sender instanceof Player player) {
            return player;
        }

        sender.sendMessage("Players only, or specify an online player.");
        return null;
    }

    private Player findPlayer(String name) {
        Player exact = Bukkit.getPlayerExact(name);
        if (exact != null) return exact;

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getName().equalsIgnoreCase(name)) {
                return player;
            }
        }
        return null;
    }

    private Integer parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String format(long value) {
        NumberFormat format = NumberFormat.getNumberInstance(Locale.US);
        format.setGroupingUsed(true);
        return format.format(value);
    }

    private List<String> onlinePlayerNames() {
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .toList();
    }

    private List<String> startsWith(List<String> values, String prefix) {
        String normalized = prefix.toLowerCase(Locale.ROOT);
        return values.stream()
                .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(normalized))
                .sorted()
                .toList();
    }

    @FunctionalInterface
    public interface ReloadAction {
        void reload();
    }
}
