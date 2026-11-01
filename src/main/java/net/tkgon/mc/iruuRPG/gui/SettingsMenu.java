package net.tkgon.mc.iruuRPG.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.tkgon.mc.iruuRPG.bgm.Bgm;
import net.tkgon.mc.iruuRPG.bgm.BgmMenus;
import net.tkgon.mc.iruuRPG.bgm.BgmSettings;
import net.tkgon.mc.iruuRPG.combat.AttackSounds;
import net.tkgon.mc.iruuRPG.combat.HeavyInput;
import net.tkgon.mc.iruuRPG.combat.KeyBindings;
import net.tkgon.mc.iruuRPG.combat.ParticleSettings;
import net.tkgon.mc.iruuRPG.combat.SoundKind;
import net.tkgon.mc.iruuRPG.combat.SoundSettings;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The settings screens: the hub (sound, particles, BGM, hotbar menu item, keys), the sound screen (how loud every kind of attack
 * sound is, and whether other players' attack sounds are heard), the particle screen (how much of the particles is
 * shown, and whether other players' particles are), the BGM screen (how loud the background music is), the screen to listen to the songs,
 * and the keys (t138): what each of the 8 actions is done by, one of the 8 patterns of two keys ({@link KeyBindings}); choosing a pattern for an
 * action gives the action that had it the action's old one.
 */
public final class SettingsMenu implements Listener {

    private static final int SIZE = 27;
    private static final int BACK = 18;
    private static final int SOUND_BUTTON = 10;
    private static final int PARTICLE_BUTTON = 12;
    private static final int BGM_BUTTON = 14;
    private static final int MENU_ITEM_BUTTON = 16;
    private static final int BGM_VOLUME = 11;
    private static final int BGM_LISTEN = 15;
    private static final int LISTEN_PREVIOUS = 45;
    private static final int LISTEN_STOP = 47;
    private static final int LISTEN_BACK = 49;
    private static final int LISTEN_NEXT = 53;
    private static final int PARTICLE_AMOUNT = 11;
    private static final int PARTICLE_OTHERS = 15;
    private static final int[] KIND_SLOTS = {10, 11, 12, 13, 14, 15, 16};
    private static final int RESET = 20;
    private static final int OTHERS = 24;
    private static final int KEYS_BUTTON = 22;
    /** The keys' screens: the 8 actions (or the 8 patterns) in the middle row, the reset and the explanation in the bottom row. */
    private static final int KEY_FIRST = 10;
    private static final int KEY_RESET = 20;
    private static final int KEY_HELP = 22;

    private final SoundSettings soundSettings;
    private final AttackSounds attackSounds;
    private final MainMenuItemService menuItemService;
    private final ParticleSettings particleSettings;
    private final BgmSettings bgmSettings;
    private MainMenu mainMenu;
    private KeyBindings keyBindings;

    public SettingsMenu(SoundSettings soundSettings, AttackSounds attackSounds, MainMenuItemService menuItemService, ParticleSettings particleSettings, BgmSettings bgmSettings) {
        this.particleSettings = particleSettings;
        this.bgmSettings = bgmSettings;
        this.soundSettings = soundSettings;
        this.attackSounds = attackSounds;
        this.menuItemService = menuItemService;
    }

    public void setMainMenu(MainMenu mainMenu) {
        this.mainMenu = mainMenu;
    }

    public void setKeyBindings(KeyBindings keyBindings) {
        this.keyBindings = keyBindings;
    }

    /** The keys: the 8 actions and their two keys. */
    public void openKeys(Player player) {
        Holder holder = new Holder(player.getUniqueId(), Screen.KEYS);
        holder.inventory = Bukkit.createInventory(holder, SIZE, Component.text("設定 - キー操作"));
        render(player, holder);
        player.openInventory(holder.inventory);
    }

    /** The 8 patterns to choose from for {@code action}. */
    public void openKeyChoice(Player player, KeyBindings.Action action) {
        Holder holder = new Holder(player.getUniqueId(), Screen.KEY_CHOICE);
        holder.action = action;
        holder.inventory = Bukkit.createInventory(holder, SIZE, Component.text("キー操作 - " + action.label()));
        render(player, holder);
        player.openInventory(holder.inventory);
    }

    /** The keys of a pattern: one label when they have no button, else as with a melee weapon and as with a ranged one. */
    static String patternText(KeyBindings.Pattern pattern) {
        String melee = pattern.label(true), ranged = pattern.label(false);
        return melee.equals(ranged) ? melee : melee + " (近接) / " + ranged + " (遠距離)";
    }

    private static Material actionIcon(KeyBindings.Action action) {
        return switch (action) {
            case ULTIMATE -> Material.NETHER_STAR;
            case ITEM -> Material.ENCHANTED_BOOK;
            default -> Material.IRON_SWORD;
        };
    }

    private void renderKeys(Player player, Holder holder) {
        Inventory inventory = holder.inventory;
        java.util.Map<KeyBindings.Action, KeyBindings.Pattern> bindings = keyBindings.of(player);
        KeyBindings.Action[] actions = KeyBindings.Action.values();
        if (holder.screen == Screen.KEYS) {
            for (int index = 0; index < actions.length; index++) {
                KeyBindings.Action action = actions[index];
                ItemStack item = item(actionIcon(action), action.label(), NamedTextColor.GOLD, List.of(
                        text(patternText(bindings.get(action)), NamedTextColor.WHITE),
                        text(switch (action) {
                            case ULTIMATE -> "クラスの必殺 (クラススキル)";
                            case ITEM -> "持っている武器のスキル";
                            default -> "コンボの" + action.label() + (action.heavyIndex() < 5 ? " (以前の強" + (action.heavyIndex() + 1) + ")" : "");
                        }, NamedTextColor.GRAY),
                        text("クリックで、キーを選ぶ", NamedTextColor.YELLOW)));
                if (action.heavyIndex() >= 0) item.setAmount(action.heavyIndex() + 1);
                inventory.setItem(KEY_FIRST + index, item);
            }
            inventory.setItem(KEY_RESET, item(Material.BARRIER, "初期の割り当てに戻す", NamedTextColor.RED, List.of(
                    text("F→F スキル1 … Q→Q アイテムスキル", NamedTextColor.GRAY))));
        } else {
            KeyBindings.Pattern current = bindings.get(holder.action);
            for (int index = 0; index < KeyBindings.Pattern.ALL.size(); index++) {
                KeyBindings.Pattern pattern = KeyBindings.Pattern.ALL.get(index);
                KeyBindings.Action holderOf = KeyBindings.actionOf(bindings, pattern);
                boolean mine = pattern.equals(current);
                List<Component> lore = new ArrayList<>();
                lore.add(text(mine ? "いまの " + holder.action.label() + " のキー" : "いま: " + holderOf.label(), mine ? NamedTextColor.GREEN : NamedTextColor.GRAY));
                if (!mine) lore.add(text("選ぶと、" + holderOf.label() + " は " + patternText(current) + " になる", NamedTextColor.GRAY));
                if (!mine) lore.add(text("クリックで、これにする", NamedTextColor.YELLOW));
                inventory.setItem(KEY_FIRST + index, item(mine ? Material.LIME_DYE : Material.TRIPWIRE_HOOK, patternText(pattern), mine ? NamedTextColor.GREEN : NamedTextColor.AQUA, lore));
            }
        }
        List<Component> help = new ArrayList<>();
        help.add(text("F か Q を押してから、すぐに 2 つ目のキーを押す", NamedTextColor.GRAY));
        help.add(text("2 つ目: F・Q・武器のボタン (近接は左が通常、右が別)", NamedTextColor.GRAY));
        help.add(text("通常のボタンだけなら、ふつうの攻撃", NamedTextColor.GRAY));
        if (!HeavyInput.keyStyle()) help.add(text("いまのサーバーの設定では、この操作は使われていません", NamedTextColor.RED));
        inventory.setItem(KEY_HELP, item(Material.PAPER, "キー操作", NamedTextColor.WHITE, help));
    }

    public void open(Player player) {
        Holder holder = new Holder(player.getUniqueId(), Screen.HUB);
        Inventory inventory = Bukkit.createInventory(holder, SIZE, Component.text("設定"));
        holder.inventory = inventory;
        render(player, holder);
        player.openInventory(inventory);
    }

    public void openSound(Player player) {
        Holder holder = new Holder(player.getUniqueId(), Screen.SOUND);
        Inventory inventory = Bukkit.createInventory(holder, SIZE, Component.text("設定 - サウンド"));
        holder.inventory = inventory;
        render(player, holder);
        player.openInventory(inventory);
    }

    public void openParticles(Player player) {
        Holder holder = new Holder(player.getUniqueId(), Screen.PARTICLE);
        Inventory inventory = Bukkit.createInventory(holder, SIZE, Component.text("設定 - パーティクル"));
        holder.inventory = inventory;
        render(player, holder);
        player.openInventory(inventory);
    }

    /** The screen to listen to the songs of the BGM (a page of 45 songs; click one to play it, the same again to stop). */
    public void openBgmListen(Player player, int page) {
        Holder holder = new Holder(player.getUniqueId(), Screen.BGM_LISTEN);
        holder.songs = bgmSettings.bgm().songs();
        holder.page = Math.max(0, Math.min(page, BgmMenus.pages(holder.songs.size()) - 1));
        holder.inventory = Bukkit.createInventory(holder, 54, Component.text("設定 - BGMを聴く"));
        renderListen(player, holder);
        player.openInventory(holder.inventory);
    }

    /** Puts the songs and the buttons in the screen; again, in place, when the song changes (opening a new screen would end the listen). */
    private void renderListen(Player player, Holder holder) {
        Inventory inventory = holder.inventory;
        inventory.clear();
        Bgm bgm = bgmSettings.bgm();
        String playing = bgm.previewing(player);
        List<Bgm.SongInfo> list = BgmMenus.page(holder.songs, holder.page);
        for (int index = 0; index < list.size(); index++) {
            Bgm.SongInfo song = list.get(index);
            boolean on = song.id().equals(playing);
            inventory.setItem(index, BgmMenus.item(song, on, List.of(BgmMenus.line(on ? "クリックで止める" : "クリックで聴く (くり返し)", NamedTextColor.YELLOW))));
        }
        ItemStack frame = item(Material.GRAY_STAINED_GLASS_PANE, " ", NamedTextColor.DARK_GRAY, List.of());
        for (int slot = 45; slot < 54; slot++) inventory.setItem(slot, frame);
        int pages = BgmMenus.pages(holder.songs.size());
        if (holder.page > 0) inventory.setItem(LISTEN_PREVIOUS, item(Material.ARROW, "前のページ", NamedTextColor.WHITE, List.of(text(holder.page + " / " + pages, NamedTextColor.GRAY))));
        if (holder.page < pages - 1) inventory.setItem(LISTEN_NEXT, item(Material.ARROW, "次のページ", NamedTextColor.WHITE, List.of(text((holder.page + 2) + " / " + pages, NamedTextColor.GRAY))));
        List<Component> status = new ArrayList<>();
        Bgm.SongInfo now = playing == null ? null : bgm.song(playing);
        status.add(text(now == null ? "いまは聴いていません" : "聴いている曲: " + (now.category().isEmpty() ? now.title() : now.shortTitle()), now == null ? NamedTextColor.GRAY : NamedTextColor.GREEN));
        if (bgmSettings.percent(player) == 0) status.add(text("BGMの音量が0%です (BGMの設定で上げてください)", NamedTextColor.RED));
        status.add(text("聴く間、いまのBGMは止まります。閉じると元に戻ります", NamedTextColor.GRAY));
        inventory.setItem(51, item(Material.PAPER, "試聴", NamedTextColor.YELLOW, status));
        inventory.setItem(LISTEN_STOP, item(Material.BARRIER, "止める", NamedTextColor.RED, List.of(text("聴いている曲を止める", NamedTextColor.GRAY))));
        inventory.setItem(LISTEN_BACK, item(Material.ARROW, "戻る", NamedTextColor.GRAY, List.of(text("BGMの設定へ", NamedTextColor.GRAY))));
    }

    private void onListenClick(Player player, Holder holder, int slot) {
        Bgm bgm = bgmSettings.bgm();
        if (slot == LISTEN_BACK) {
            bgm.stopPreview(player);
            openBgm(player);
        } else if (slot == LISTEN_STOP) {
            bgm.stopPreview(player);
            renderListen(player, holder);
        } else if (slot == LISTEN_PREVIOUS && holder.page > 0) {
            openBgmListen(player, holder.page - 1);
        } else if (slot == LISTEN_NEXT && holder.page < BgmMenus.pages(holder.songs.size()) - 1) {
            openBgmListen(player, holder.page + 1);
        } else if (slot >= 0 && slot < BgmMenus.PER_PAGE) {
            List<Bgm.SongInfo> list = BgmMenus.page(holder.songs, holder.page);
            if (slot >= list.size()) return;
            String id = list.get(slot).id();
            if (id.equals(bgm.previewing(player))) bgm.stopPreview(player); else bgm.preview(player, id);
            renderListen(player, holder);
        }
    }

    /** Closing the listen screen ends the listen (the BGM that was going on comes back). */
    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player && event.getInventory().getHolder() instanceof Holder holder && holder.screen == Screen.BGM_LISTEN) {
            bgmSettings.bgm().stopPreview(player);
        }
    }

    public void openBgm(Player player) {
        Holder holder = new Holder(player.getUniqueId(), Screen.BGM);
        Inventory inventory = Bukkit.createInventory(holder, SIZE, Component.text("設定 - BGM"));
        holder.inventory = inventory;
        render(player, holder);
        player.openInventory(inventory);
    }

    private void render(Player player, Holder holder) {
        Inventory inventory = holder.inventory;
        inventory.clear();
        ItemStack frame = item(Material.GRAY_STAINED_GLASS_PANE, " ", NamedTextColor.DARK_GRAY, List.of());
        for (int slot = 0; slot < SIZE; slot++) {
            if (slot < 9 || slot >= 18) inventory.setItem(slot, frame);
        }
        inventory.setItem(BACK, back(holder.screen));

        if (holder.screen == Screen.HUB) {
            inventory.setItem(SOUND_BUTTON, item(Material.NOTE_BLOCK, "サウンド", NamedTextColor.AQUA, List.of(
                    text("攻撃の音の大きさを、種類ごとに調整します", NamedTextColor.GRAY),
                    text("他のプレイヤーの攻撃音を消すこともできます", NamedTextColor.GRAY))));
            inventory.setItem(PARTICLE_BUTTON, item(Material.FIREWORK_STAR, "パーティクル", NamedTextColor.LIGHT_PURPLE, List.of(
                    text("表示するパーティクルの量を調整します", NamedTextColor.GRAY),
                    text("他のプレイヤーのパーティクルを消すこともできます", NamedTextColor.GRAY))));
            inventory.setItem(BGM_BUTTON, item(Material.JUKEBOX, "BGM", NamedTextColor.GREEN, List.of(
                    text("フィールドで流れる音楽の大きさを調整します", NamedTextColor.GRAY),
                    text("現在: " + bgmSettings.percent(player) + "%", NamedTextColor.WHITE))));
            boolean hidden = menuItemService.isHidden(player);
            inventory.setItem(MENU_ITEM_BUTTON, item(Material.NETHER_STAR, "ホットバーのメニューアイテム", NamedTextColor.GOLD, List.of(
                    text("現在: " + (hidden ? "表示しない" : "表示する"), hidden ? NamedTextColor.RED : NamedTextColor.GREEN),
                    text("建築の邪魔なときは消せます (/menu でメニューを開けます)", NamedTextColor.GRAY),
                    text("クリックで切り替え", NamedTextColor.YELLOW))));
            if (keyBindings != null) {
                inventory.setItem(KEYS_BUTTON, item(Material.TRIPWIRE_HOOK, "キー操作", NamedTextColor.YELLOW, List.of(
                        text("スキル1〜6・必殺・アイテムスキルを出すキーを、", NamedTextColor.GRAY),
                        text("8 通り (F か Q と、2 つ目のキー) から選びます", NamedTextColor.GRAY),
                        text("クリックで開く", NamedTextColor.YELLOW))));
            }
            return;
        }

        if (holder.screen == Screen.KEYS || holder.screen == Screen.KEY_CHOICE) {
            renderKeys(player, holder);
            return;
        }

        if (holder.screen == Screen.BGM_LISTEN) {
            renderListen(player, holder);
            return;
        }
        if (holder.screen == Screen.BGM) {
            int percent = bgmSettings.percent(player);
            String playing = bgmSettings.nowPlaying(player);
            List<Component> lore = new ArrayList<>();
            lore.add(text("フィールドの音楽の大きさ。0%で聞こえなくなります", NamedTextColor.GRAY));
            lore.add(text(playing == null ? "いまは流れていません" : "いまの曲: " + playing, NamedTextColor.GRAY));
            lore.add(text(" ", NamedTextColor.GRAY));
            lore.add(Component.text(bar(percent) + " ", percent == 0 ? NamedTextColor.RED : NamedTextColor.GREEN)
                    .append(Component.text(percent + "%", NamedTextColor.WHITE))
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(text("左クリック: +" + BgmSettings.STEP + "%", NamedTextColor.YELLOW));
            lore.add(text("右クリック: -" + BgmSettings.STEP + "%", NamedTextColor.YELLOW));
            lore.add(text("Shift+左: 100% / Shift+右: 消す", NamedTextColor.YELLOW));
            inventory.setItem(BGM_VOLUME, item(Material.JUKEBOX, "BGMの音量", NamedTextColor.GREEN, lore));
            inventory.setItem(BGM_LISTEN, item(Material.MUSIC_DISC_MELLOHI, "曲を聴く", NamedTextColor.AQUA, List.of(
                    text("BGMの曲を、1曲ずつ聴いてみます", NamedTextColor.GRAY),
                    text(bgmSettings.bgm().songs().size() + " 曲", NamedTextColor.GRAY),
                    text("クリックで、曲の一覧へ", NamedTextColor.YELLOW))));
            return;
        }

        if (holder.screen == Screen.PARTICLE) {
            int percent = particleSettings.percent(player);
            List<Component> amount = new ArrayList<>();
            amount.add(text("見えるパーティクルの量。減らすと、重いときに軽くなります", NamedTextColor.GRAY));
            amount.add(text(" ", NamedTextColor.GRAY));
            amount.add(Component.text(bar(percent) + " ", percent == 0 ? NamedTextColor.RED : NamedTextColor.GREEN)
                    .append(Component.text(percent + "%", NamedTextColor.WHITE))
                    .decoration(TextDecoration.ITALIC, false));
            amount.add(text("左クリック: +" + ParticleSettings.STEP + "%", NamedTextColor.YELLOW));
            amount.add(text("右クリック: -" + ParticleSettings.STEP + "%", NamedTextColor.YELLOW));
            amount.add(text("Shift+左: 100% / Shift+右: 消す", NamedTextColor.YELLOW));
            inventory.setItem(PARTICLE_AMOUNT, item(Material.FIREWORK_STAR, "パーティクルの量", NamedTextColor.AQUA, amount));
            boolean hideOthers = particleSettings.hideOthers(player);
            inventory.setItem(PARTICLE_OTHERS, item(hideOthers ? Material.SKELETON_SKULL : Material.PLAYER_HEAD, "他のプレイヤーのパーティクル", NamedTextColor.LIGHT_PURPLE, List.of(
                    text("現在: " + (hideOthers ? "消している" : "見える"), hideOthers ? NamedTextColor.RED : NamedTextColor.GREEN),
                    text("消すと、自分の攻撃のパーティクルと敵のパーティクルだけが見えます", NamedTextColor.GRAY),
                    text("クリックで切り替え", NamedTextColor.YELLOW))));
            return;
        }

        SoundKind[] kinds = SoundKind.values();
        for (int index = 0; index < kinds.length && index < KIND_SLOTS.length; index++) {
            inventory.setItem(KIND_SLOTS[index], kindItem(player, kinds[index]));
        }
        inventory.setItem(RESET, item(Material.BARRIER, "すべて100%に戻す", NamedTextColor.RED, List.of(
                text("音の大きさを、初期の状態に戻します", NamedTextColor.GRAY))));
        boolean hear = soundSettings.hearOthers(player);
        inventory.setItem(OTHERS, item(hear ? Material.PLAYER_HEAD : Material.SKELETON_SKULL, "他のプレイヤーの攻撃音", NamedTextColor.LIGHT_PURPLE, List.of(
                text("現在: " + (hear ? "聞こえる" : "消している"), hear ? NamedTextColor.GREEN : NamedTextColor.RED),
                text("消すと、自分の攻撃音と敵の攻撃音だけが聞こえます", NamedTextColor.GRAY),
                text("クリックで切り替え", NamedTextColor.YELLOW))));
    }

    private ItemStack kindItem(Player player, SoundKind kind) {
        int percent = soundSettings.volume(player, kind);
        List<Component> lore = new ArrayList<>();
        lore.add(text(kind.description(), NamedTextColor.GRAY));
        lore.add(text(" ", NamedTextColor.GRAY));
        lore.add(Component.text(bar(percent) + " ", percent == 0 ? NamedTextColor.RED : NamedTextColor.GREEN)
                .append(Component.text(percent + "%", NamedTextColor.WHITE))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(text("左クリック: +" + SoundSettings.STEP + "%", NamedTextColor.YELLOW));
        lore.add(text("右クリック: -" + SoundSettings.STEP + "%", NamedTextColor.YELLOW));
        lore.add(text("Shift+左: 100% / Shift+右: 消す", NamedTextColor.YELLOW));
        return item(kind.icon(), kind.label(), NamedTextColor.AQUA, lore);
    }

    private String bar(int percent) {
        int filled = Math.round(percent / 10.0f);
        return "▮".repeat(filled) + "▯".repeat(10 - filled);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!holder.owner.equals(player.getUniqueId())) return;
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;

        int slot = event.getRawSlot();
        if (holder.screen == Screen.BGM_LISTEN) {
            onListenClick(player, holder, slot);
            return;
        }
        if (slot == BACK) {
            if (holder.screen == Screen.KEY_CHOICE) {
                openKeys(player);
            } else if (holder.screen == Screen.SOUND || holder.screen == Screen.PARTICLE || holder.screen == Screen.BGM || holder.screen == Screen.KEYS) {
                open(player);
            } else if (mainMenu != null) {
                mainMenu.open(player);
            }
            return;
        }

        if (holder.screen == Screen.HUB) {
            if (slot == SOUND_BUTTON) {
                openSound(player);
            } else if (slot == PARTICLE_BUTTON) {
                openParticles(player);
            } else if (slot == BGM_BUTTON) {
                openBgm(player);
            } else if (slot == MENU_ITEM_BUTTON) {
                menuItemService.setHidden(player, !menuItemService.isHidden(player));
                render(player, holder);
            } else if (slot == KEYS_BUTTON && keyBindings != null) {
                openKeys(player);
            }
            return;
        }

        if (holder.screen == Screen.KEYS) {
            int index = slot - KEY_FIRST;
            if (index >= 0 && index < KeyBindings.Action.values().length) {
                openKeyChoice(player, KeyBindings.Action.values()[index]);
            } else if (slot == KEY_RESET) {
                keyBindings.reset(player);
                render(player, holder);
            }
            return;
        }
        if (holder.screen == Screen.KEY_CHOICE) {
            int index = slot - KEY_FIRST;
            if (index >= 0 && index < KeyBindings.Pattern.ALL.size()) {
                keyBindings.set(player, holder.action, KeyBindings.Pattern.ALL.get(index));
                openKeys(player);
            }
            return;
        }

        if (holder.screen == Screen.BGM) {
            if (slot == BGM_LISTEN) {
                openBgmListen(player, 0);
                return;
            }
            if (slot == BGM_VOLUME) {
                int current = bgmSettings.percent(player);
                int next = switch (event.getClick()) {
                    case LEFT -> current + BgmSettings.STEP;
                    case RIGHT -> current - BgmSettings.STEP;
                    case SHIFT_LEFT -> 100;
                    case SHIFT_RIGHT -> 0;
                    default -> current;
                };
                if (next != current) {
                    bgmSettings.setPercent(player, next);
                    render(player, holder);
                }
            }
            return;
        }

        if (holder.screen == Screen.PARTICLE) {
            if (slot == PARTICLE_AMOUNT) {
                int current = particleSettings.percent(player);
                int next = switch (event.getClick()) {
                    case LEFT -> current + ParticleSettings.STEP;
                    case RIGHT -> current - ParticleSettings.STEP;
                    case SHIFT_LEFT -> 100;
                    case SHIFT_RIGHT -> 0;
                    default -> current;
                };
                if (next != current) {
                    particleSettings.setPercent(player, next);
                    render(player, holder);
                }
            } else if (slot == PARTICLE_OTHERS) {
                particleSettings.setHideOthers(player, !particleSettings.hideOthers(player));
                render(player, holder);
            }
            return;
        }

        if (slot == RESET) {
            soundSettings.resetVolumes(player);
            render(player, holder);
            return;
        }
        if (slot == OTHERS) {
            soundSettings.setHearOthers(player, !soundSettings.hearOthers(player));
            render(player, holder);
            return;
        }
        SoundKind[] kinds = SoundKind.values();
        for (int index = 0; index < kinds.length && index < KIND_SLOTS.length; index++) {
            if (KIND_SLOTS[index] != slot) continue;
            SoundKind kind = kinds[index];
            ClickType click = event.getClick();
            int current = soundSettings.volume(player, kind);
            int next = switch (click) {
                case LEFT -> current + SoundSettings.STEP;
                case RIGHT -> current - SoundSettings.STEP;
                case SHIFT_LEFT -> 100;
                case SHIFT_RIGHT -> 0;
                default -> current;
            };
            if (next == current && click != ClickType.LEFT && click != ClickType.RIGHT) return;
            soundSettings.setVolume(player, kind, next);
            render(player, holder);
            attackSounds.preview(player, kind);
            return;
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) event.setCancelled(true);
    }

    private ItemStack back(Screen screen) {
        ItemStack item = MenuButtons.back();
        if (screen == Screen.SOUND || screen == Screen.PARTICLE || screen == Screen.BGM || screen == Screen.KEYS || screen == Screen.KEY_CHOICE) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.lore(List.of(text(screen == Screen.KEY_CHOICE ? "キー操作へ戻る" : "設定へ戻る", NamedTextColor.GRAY)));
                item.setItemMeta(meta);
            }
        }
        return item;
    }

    private Component text(String value, NamedTextColor color) {
        return Component.text(value, color).decoration(TextDecoration.ITALIC, false);
    }

    private ItemStack item(Material material, String name, NamedTextColor color, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name, color, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_UNBREAKABLE);
            item.setItemMeta(meta);
        }
        return item;
    }

    private enum Screen {
        HUB,
        SOUND,
        PARTICLE,
        BGM,
        BGM_LISTEN,
        KEYS,
        KEY_CHOICE
    }

    private static final class Holder implements MenuHolder {
        private final UUID owner;
        private final Screen screen;
        private Inventory inventory;
        /** The listen screen: the songs and the page shown. */
        private List<Bgm.SongInfo> songs = List.of();
        private int page;
        /** The choice of keys: the action they are chosen for. */
        private KeyBindings.Action action;

        private Holder(UUID owner, Screen screen) {
            this.owner = owner;
            this.screen = screen;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }
}
