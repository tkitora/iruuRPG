package net.tkgon.mc.iruuRPG.dungeon;

import net.tkgon.mc.iruuRPG.gui.DungeonMenu;
import net.tkgon.mc.iruuRPG.mob.RpgMobRegistry;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * /dungeon (menu), /dungeon enter &lt;id&gt;, /dungeon leave. Admins: build, entry, spawn, spawns, clearspawns, save,
 * new, reload (see dungeons/cave.yml for how a dungeon is made).
 */
public final class DungeonCommand implements CommandExecutor, TabCompleter {

    private static final List<String> PLAYER = List.of("enter", "leave", "invite", "join");
    private static final List<String> ADMIN = List.of("edit", "build", "entry", "spawn", "spawns", "removespawn", "clearspawns", "exit", "boss", "rewardroom", "chest", "addchest", "removechest", "gate", "removegate", "removegates", "save", "new", "reload");

    private final DungeonService service;
    private final DungeonMenu menu;
    private final RpgMobRegistry mobRegistry;

    private DungeonEditor editor;

    public void setEditor(DungeonEditor editor) {
        this.editor = editor;
    }

    public DungeonCommand(DungeonService service, DungeonMenu menu, RpgMobRegistry mobRegistry) {
        this.service = service;
        this.menu = menu;
        this.mobRegistry = mobRegistry;
    }

    /** /dungeon removegate (no id): the gate looked at, else the nearest one within 5 blocks, of any dungeon. */
    private void removeGateHere(Player player, DungeonRegistry registry) {
        java.util.Map<DungeonRegistry.Gate, String> all = registry.allGates();
        DungeonRegistry.Gate found = null;
        org.bukkit.block.Block target = player.getTargetBlockExact(5);
        if (target != null) {
            DungeonRegistry.Gate looked = new DungeonRegistry.Gate(target.getWorld().getName(), target.getX(), target.getY(), target.getZ());
            if (all.containsKey(looked)) found = looked;
        }
        if (found == null) {
            double best = 25.0;
            for (DungeonRegistry.Gate gate : all.keySet()) {
                if (!gate.world().equals(player.getWorld().getName())) continue;
                double distance = new org.bukkit.Location(player.getWorld(), gate.x() + 0.5, gate.y() + 0.5, gate.z() + 0.5).distanceSquared(player.getLocation());
                if (distance <= best) {
                    best = distance;
                    found = gate;
                }
            }
        }
        if (found == null) {
            player.sendMessage("[ダンジョン] 見ているブロック、または近く (5ブロック以内) に入口がありません。(入口を右クリックして「この入口を消す」でも消せます)");
            return;
        }
        String id = all.get(found);
        String error = service.removeGate(player, found);
        player.sendMessage(error != null ? "[ダンジョン] " + error
                : String.format(Locale.ROOT, "[ダンジョン] 入口を消した。(%s の %s %d, %d, %d)", id, found.world(), found.x(), found.y(), found.z()));
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Player only.");
            return true;
        }
        if (args.length == 0) {
            menu.open(player);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        DungeonRegistry registry = service.registry();
        switch (sub) {
            case "enter" -> {
                if (args.length < 2) {
                    player.sendMessage("[ダンジョン] /dungeon enter <id>");
                } else {
                    service.enter(player, args[1].toLowerCase(Locale.ROOT));
                }
                return true;
            }
            case "leave" -> {
                service.leave(player);
                return true;
            }
            case "invite" -> {
                Player target = args.length >= 2 ? org.bukkit.Bukkit.getPlayerExact(args[1]) : null;
                if (target == null) {
                    player.sendMessage("[ダンジョン] /dungeon invite <オンラインのプレイヤー名>");
                } else {
                    service.invite(player, target);
                }
                return true;
            }
            case "join" -> {
                service.join(player);
                return true;
            }
            default -> {
            }
        }

        if (!ADMIN.contains(sub)) {
            player.sendMessage("[ダンジョン] /dungeon [enter <id> | leave | invite <名前> | join]");
            return true;
        }
        if (!player.hasPermission(DungeonService.ADMIN_PERMISSION)) {
            player.sendMessage("[ダンジョン] この操作は管理者のみです。");
            return true;
        }
        if (sub.equals("edit")) {
            // editor mode: the hotbar becomes the tools (no id inside a template: switch on/off)
            if (editor != null) editor.toggle(player, args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : null);
            return true;
        }
        if (sub.equals("reload")) {
            registry.reload();
            service.refreshGates();
            player.sendMessage("[ダンジョン] 定義を読み直した。(" + registry.all().size() + " 件)");
            return true;
        }
        if (sub.equals("removegates")) {
            // every gate (out in the world) of one dungeon, or of all dungeons when no id is given. Blocks stay.
            int removed = 0;
            for (DungeonDefinition each : registry.all()) {
                if (args.length >= 2 && !each.id().equalsIgnoreCase(args[1])) continue;
                removed += registry.clearGates(each);
            }
            service.refreshGates();
            player.sendMessage("[ダンジョン] 入口を " + removed + " か所すべて消した。(登録と頭上の表示だけ。ブロックは残る)");
            return true;
        }
        if (sub.equals("removegate") && args.length < 2) {
            removeGateHere(player, registry);
            return true;
        }
        if (args.length < 2) {
            player.sendMessage("[ダンジョン] /dungeon " + sub + " <id>" + (sub.equals("spawn") ? " <mobId>" : sub.equals("new") ? " <名前>" : ""));
            return true;
        }
        String id = args[1].toLowerCase(Locale.ROOT);
        if (sub.equals("new")) {
            if (!DungeonRegistry.validId(id)) {
                player.sendMessage("[ダンジョン] id は a-z・0-9・_ の 1〜24 文字です。");
                return true;
            }
            if (registry.find(id).isPresent()) {
                player.sendMessage("[ダンジョン] その id はもうあります。");
                return true;
            }
            String name = args.length >= 3 ? String.join(" ", Arrays.copyOfRange(args, 2, args.length)) : id;
            registry.create(id, name);
            player.sendMessage("[ダンジョン] 「" + name + "」(" + id + ") を作った。/dungeon build " + id + " でひな形へ。");
            return true;
        }
        DungeonDefinition definition = registry.find(id).orElse(null);
        if (definition == null) {
            player.sendMessage("[ダンジョン] そのダンジョンはありません: " + id);
            return true;
        }
        switch (sub) {
            case "build" -> service.build(player, definition);
            case "save" -> service.saveTemplate(player, definition);
            case "entry" -> {
                if (!player.getWorld().getName().equals(definition.templateWorldName())) {
                    player.sendMessage("[ダンジョン] ひな形のワールド (" + definition.templateWorldName() + ") の中で使ってください。");
                    return true;
                }
                registry.setEntry(definition, player.getLocation());
                service.refreshMarkers(id);
                player.sendMessage("[ダンジョン] 今の位置を入口にした。");
            }
            case "spawn" -> {
                if (!player.getWorld().getName().equals(definition.templateWorldName())) {
                    player.sendMessage("[ダンジョン] ひな形のワールド (" + definition.templateWorldName() + ") の中で使ってください。");
                    return true;
                }
                if (args.length < 3 || mobRegistry.find(args[2]).isEmpty()) {
                    player.sendMessage("[ダンジョン] /dungeon spawn " + id + " <mobId> [出る確率 %] (モブの id を確認してください)");
                    return true;
                }
                int chance = 100;
                if (args.length >= 4) {
                    try {
                        chance = Integer.parseInt(args[3].replace("%", ""));
                    } catch (NumberFormatException e) {
                        chance = -1;
                    }
                    if (chance < 1 || chance > 100) {
                        player.sendMessage("[ダンジョン] 出る確率は 1〜100 (%) で書いてください。");
                        return true;
                    }
                }
                registry.addSpawn(definition, args[2], player.getLocation(), chance);
                service.refreshMarkers(id);
                player.sendMessage("[ダンジョン] #" + (definition.spawns().size() + 1) + " " + args[2] + " を置いた。" + (chance < 100 ? "(確率 " + chance + "%) " : "") + "(消すときは /dungeon removespawn " + id + " "
                        + (definition.spawns().size() + 1) + "、または近くで /dungeon removespawn " + id + ")");
            }
            case "exit" -> {
                if (!player.getWorld().getName().equals(definition.templateWorldName())) {
                    player.sendMessage("[ダンジョン] ひな形のワールド (" + definition.templateWorldName() + ") の中で使ってください。");
                    return true;
                }
                registry.setExit(definition, player.getLocation());
                service.refreshMarkers(id);
                player.sendMessage("[ダンジョン] 今の位置を脱出口にした。(ボスを倒すと通れる)");
            }
            case "rewardroom" -> {
                if (!player.getWorld().getName().equals(definition.templateWorldName())) {
                    player.sendMessage("[ダンジョン] ひな形のワールド (" + definition.templateWorldName() + ") の中で使ってください。");
                    return true;
                }
                registry.setRewardRoom(definition, player.getLocation());
                service.refreshMarkers(id);
                player.sendMessage("[ダンジョン] 今の位置を報酬部屋にした。(クリアの5秒後に全員ここへ)");
            }
            case "chest" -> {
                if (!player.getWorld().getName().equals(definition.templateWorldName())) {
                    player.sendMessage("[ダンジョン] ひな形のワールド (" + definition.templateWorldName() + ") の中で使ってください。");
                    return true;
                }
                // the block being looked at (within 5 blocks), else the block at the feet
                org.bukkit.block.Block target = player.getTargetBlockExact(5);
                org.bukkit.Location at = target != null && !target.getType().isAir() && target.getType() == org.bukkit.Material.CHEST
                        ? target.getLocation() : player.getLocation().getBlock().getLocation();
                registry.setChest(definition, at);
                at.getBlock().setType(org.bukkit.Material.CHEST);
                service.refreshMarkers(id);
                player.sendMessage(String.format(Locale.ROOT, "[ダンジョン] 報酬の宝箱を (%d, %d, %d) にした。(中身は loot/%s.yml)", at.getBlockX(), at.getBlockY(), at.getBlockZ(), id));
            }
            case "addchest" -> {
                if (!player.getWorld().getName().equals(definition.templateWorldName())) {
                    player.sendMessage("[ダンジョン] ひな形のワールド (" + definition.templateWorldName() + ") の中で使ってください。");
                    return true;
                }
                String table = args.length >= 3 ? args[2].toLowerCase(Locale.ROOT) : definition.defaultChestTable();
                if (!DungeonRegistry.validTable(table)) {
                    player.sendMessage("[ダンジョン] 表の名前は a-z・0-9・_ (1〜40文字) です。");
                    return true;
                }
                // the chest being looked at, the block on top of the one looked at, or the block at the feet
                org.bukkit.block.Block target = player.getTargetBlockExact(5);
                org.bukkit.block.Block block = target == null ? player.getLocation().getBlock()
                        : target.getType() == org.bukkit.Material.CHEST ? target : target.getRelative(org.bukkit.block.BlockFace.UP);
                boolean existed = definition.roadChestAt(block.getX(), block.getY(), block.getZ()) >= 0;
                block.setType(org.bukkit.Material.CHEST);
                registry.setRoadChest(definition, block.getLocation(), table);
                service.refreshMarkers(id);
                boolean file = service.lootFile(table).exists();
                player.sendMessage(String.format(Locale.ROOT, "[ダンジョン] %s宝箱 (%d, %d, %d)、表: %s%s", existed ? "表を変えた: " : "置いた: ",
                        block.getX(), block.getY(), block.getZ(), table, file ? "" : " (loot/" + table + ".yml がまだ無い。開けると空)"));
            }
            case "removechest" -> {
                if (!player.getWorld().getName().equals(definition.templateWorldName())) {
                    player.sendMessage("[ダンジョン] ひな形のワールド (" + definition.templateWorldName() + ") の中で使ってください。");
                    return true;
                }
                org.bukkit.block.Block target = player.getTargetBlockExact(5);
                int index = target == null ? -1 : definition.roadChestAt(target.getX(), target.getY(), target.getZ());
                if (index < 0) {
                    // nearest within 3 blocks of the feet
                    double best = 9.0;
                    for (int i = 0; i < definition.roadChests().size(); i++) {
                        double distance = definition.roadChests().get(i).at().in(player.getWorld()).add(0.5, 0.5, 0.5).distanceSquared(player.getLocation());
                        if (distance <= best) {
                            best = distance;
                            index = i;
                        }
                    }
                }
                if (index < 0) {
                    player.sendMessage("[ダンジョン] 宝箱を見ながら(または3ブロック以内で)使ってください。");
                    return true;
                }
                DungeonDefinition.RoadChest removed = definition.roadChests().get(index);
                registry.removeRoadChest(definition, index);
                removed.at().in(player.getWorld()).getBlock().setType(org.bukkit.Material.AIR);
                service.refreshMarkers(id);
                player.sendMessage("[ダンジョン] 宝箱 #" + (index + 1) + " (表: " + removed.table() + ") を消した。");
            }
            case "gate" -> {
                // a gate out in the world: the block looked at, or the one under the feet
                org.bukkit.block.Block target = player.getTargetBlockExact(5);
                org.bukkit.block.Block block = target != null ? target : player.getLocation().getBlock().getRelative(org.bukkit.block.BlockFace.DOWN);
                if (block.getWorld().getName().startsWith("dungeon_")) {
                    player.sendMessage("[ダンジョン] 入口は、ダンジョンの外 (普通のワールド) に置いてください。");
                    return true;
                }
                registry.addGate(definition, block.getLocation());
                service.refreshGates();
                player.sendMessage(String.format(Locale.ROOT, "[ダンジョン] 「%s」の入口を (%s %d, %d, %d) にした。右クリックで入れる。",
                        definition.name(), block.getWorld().getName(), block.getX(), block.getY(), block.getZ()));
            }
            case "removegate" -> {
                DungeonRegistry.Gate nearest = null;
                double best = 25.0;
                for (DungeonRegistry.Gate gate : registry.gates(id)) {
                    if (!gate.world().equals(player.getWorld().getName())) continue;
                    double distance = new org.bukkit.Location(player.getWorld(), gate.x() + 0.5, gate.y() + 0.5, gate.z() + 0.5).distanceSquared(player.getLocation());
                    if (distance <= best) {
                        best = distance;
                        nearest = gate;
                    }
                }
                if (nearest == null) {
                    player.sendMessage("[ダンジョン] 近く (5ブロック以内) にこのダンジョンの入口がありません。");
                    return true;
                }
                registry.removeGate(definition, nearest);
                service.refreshGates();
                player.sendMessage("[ダンジョン] 入口を消した。");
            }
            case "boss" -> {
                if (args.length < 3 || mobRegistry.find(args[2]).isEmpty()) {
                    player.sendMessage("[ダンジョン] /dungeon boss " + id + " <mobId> (このモブが倒れたらクリア)");
                    return true;
                }
                registry.setBoss(definition, args[2]);
                service.refreshMarkers(id);
                boolean placed = definition.spawns().stream().anyMatch(spawn -> spawn.mobId().equals(args[2]));
                player.sendMessage("[ダンジョン] ボスを " + args[2] + " にした。" + (placed ? "" : " (まだ置かれていない: /dungeon spawn " + id + " " + args[2] + ")"));
            }
            case "spawns" -> {
                player.sendMessage("[ダンジョン] ボス: " + (definition.boss() == null ? "なし (全滅でクリア)" : definition.boss())
                        + " / 脱出口: " + (definition.exit() == null ? "なし" : String.format(Locale.ROOT, "(%.1f, %.1f, %.1f)", definition.exit().x(), definition.exit().y(), definition.exit().z()))
                        + " / 報酬部屋: " + (definition.rewardRoom() == null ? "なし" : "あり")
                        + " / 宝箱: " + (definition.chest() == null ? "なし" : "あり"));
                player.sendMessage("[ダンジョン] " + definition.name() + " のモブ: " + definition.spawns().size() + " 体");
                for (int index = 0; index < definition.spawns().size(); index++) {
                    DungeonDefinition.MobSpawn spawn = definition.spawns().get(index);
                    player.sendMessage(String.format(Locale.ROOT, "  #%d %s  (%.1f, %.1f, %.1f)%s", index + 1, spawn.mobId(), spawn.x(), spawn.y(), spawn.z(),
                            spawn.always() ? "" : "  確率 " + spawn.chance() + "%"));
                }
                List<DungeonWall> walls = registry.walls(id);
                if (!walls.isEmpty()) {
                    player.sendMessage("[ダンジョン] 消える壁: " + walls.size() + " 個 (エディターの「消える壁」で編集)");
                    for (int index = 0; index < walls.size(); index++) {
                        DungeonWall wall = walls.get(index);
                        String target = wall.target() >= 0 && wall.target() < definition.spawns().size() ? definition.spawns().get(wall.target()).mobId() : "?";
                        player.sendMessage("  壁 #" + (index + 1) + " " + wall.describe(target) + " / " + wall.blocks().size() + " ブロック");
                    }
                }
                player.sendMessage("[ダンジョン] 入口 (世界): " + registry.gates(id).size() + " か所"
                        + (service.requireGate() ? " (入口からしか入れない設定)" : " (いまはどこからでも入れる設定)"));
                for (DungeonRegistry.Gate gate : registry.gates(id)) {
                    player.sendMessage(String.format(Locale.ROOT, "  %s (%d, %d, %d)", gate.world(), gate.x(), gate.y(), gate.z()));
                }
                player.sendMessage("[ダンジョン] 道中の宝箱: " + definition.roadChests().size() + " 個");
                for (int index = 0; index < definition.roadChests().size(); index++) {
                    DungeonDefinition.RoadChest chest = definition.roadChests().get(index);
                    player.sendMessage(String.format(Locale.ROOT, "  宝箱 #%d 表: %s  (%d, %d, %d)", index + 1, chest.table(),
                            (int) chest.at().x(), (int) chest.at().y(), (int) chest.at().z()));
                }
            }
            case "removespawn" -> {
                int index = -1;
                if (args.length >= 3) {
                    try {
                        index = Integer.parseInt(args[2]) - 1;
                    } catch (NumberFormatException e) {
                        player.sendMessage("[ダンジョン] /dungeon removespawn " + id + " [番号] (番号は /dungeon spawns で確認。省くと一番近いモブ)");
                        return true;
                    }
                } else if (player.getWorld().getName().equals(definition.templateWorldName())) {
                    // no number: the placed mob nearest to the player (within 4 blocks)
                    double best = 16.0;
                    for (int i = 0; i < definition.spawns().size(); i++) {
                        double distance = definition.spawns().get(i).in(player.getWorld()).distanceSquared(player.getLocation());
                        if (distance <= best) {
                            best = distance;
                            index = i;
                        }
                    }
                    if (index < 0) {
                        player.sendMessage("[ダンジョン] 近く(4ブロック以内)に置いたモブがありません。番号で指定してください。");
                        return true;
                    }
                }
                if (index < 0 || index >= definition.spawns().size()) {
                    player.sendMessage("[ダンジョン] その番号のモブはありません。(/dungeon spawns " + id + " で確認)");
                    return true;
                }
                String removed = definition.spawns().get(index).mobId();
                registry.removeSpawn(definition, index);
                service.refreshMarkers(id);
                player.sendMessage("[ダンジョン] #" + (index + 1) + " " + removed + " を消した。(番号は詰めて付け直される)");
            }
            case "clearspawns" -> {
                registry.clearSpawns(definition);
                service.refreshMarkers(id);
                player.sendMessage("[ダンジョン] モブの配置を全部消した。");
            }
            default -> {
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        List<String> options = new ArrayList<>();
        boolean admin = sender.hasPermission(DungeonService.ADMIN_PERMISSION);
        if (args.length == 1) {
            options.addAll(PLAYER);
            if (admin) options.addAll(ADMIN);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("invite")) {
            for (Player online : org.bukkit.Bukkit.getOnlinePlayers()) if (!online.equals(sender)) options.add(online.getName());
        } else if (args.length == 2 && !List.of("leave", "reload", "new", "join").contains(args[0].toLowerCase(Locale.ROOT))) {
            for (DungeonDefinition definition : service.registry().all()) options.add(definition.id());
        } else if (args.length == 3 && args[0].equalsIgnoreCase("addchest") && admin) {
            options.addAll(service.lootTables());
        } else if (args.length == 3 && args[0].equalsIgnoreCase("removespawn") && admin) {
            service.registry().find(args[1]).ifPresent(definition -> {
                for (int i = 1; i <= definition.spawns().size(); i++) options.add(String.valueOf(i));
            });
        } else if (args.length == 3 && (args[0].equalsIgnoreCase("spawn") || args[0].equalsIgnoreCase("boss")) && admin) {
            options.addAll(mobRegistry.all().keySet());
        }
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        options.removeIf(option -> !option.toLowerCase(Locale.ROOT).startsWith(prefix));
        return options;
    }
}
