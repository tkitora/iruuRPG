package net.tkgon.mc.iruuRPG.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.tkgon.mc.iruuRPG.combat.AttackSounds;
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
 * The settings screens: the hub (sound, particles, hotbar menu item), the sound screen (how loud every kind of attack
 * sound is, and whether other players' attack sounds are heard) and the particle screen (how much of the particles is
 * shown, and whether other players' particles are).
 */
public final class SettingsMenu implements Listener {

    private static final int SIZE = 27;
    private static final int BACK = 18;
    private static final int SOUND_BUTTON = 11;
    private static final int PARTICLE_BUTTON = 13;
    private static final int MENU_ITEM_BUTTON = 15;
    private static final int PARTICLE_AMOUNT = 11;
    private static final int PARTICLE_OTHERS = 15;
    private static final int[] KIND_SLOTS = {10, 11, 12, 13, 14, 15, 16};
    private static final int RESET = 20;
    private static final int OTHERS = 24;

    private final SoundSettings soundSettings;
    private final AttackSounds attackSounds;
    private final MainMenuItemService menuItemService;
    private final ParticleSettings particleSettings;
    private MainMenu mainMenu;

    public SettingsMenu(SoundSettings soundSettings, AttackSounds attackSounds, MainMenuItemService menuItemService, ParticleSettings particleSettings) {
        this.particleSettings = particleSettings;
        this.soundSettings = soundSettings;
        this.attackSounds = attackSounds;
        this.menuItemService = menuItemService;
    }

    public void setMainMenu(MainMenu mainMenu) {
        this.mainMenu = mainMenu;
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
            boolean hidden = menuItemService.isHidden(player);
            inventory.setItem(MENU_ITEM_BUTTON, item(Material.NETHER_STAR, "ホットバーのメニューアイテム", NamedTextColor.GOLD, List.of(
                    text("現在: " + (hidden ? "表示しない" : "表示する"), hidden ? NamedTextColor.RED : NamedTextColor.GREEN),
                    text("建築の邪魔なときは消せます (/menu でメニューを開けます)", NamedTextColor.GRAY),
                    text("クリックで切り替え", NamedTextColor.YELLOW))));
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
        if (slot == BACK) {
            if (holder.screen == Screen.SOUND || holder.screen == Screen.PARTICLE) {
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
            } else if (slot == MENU_ITEM_BUTTON) {
                menuItemService.setHidden(player, !menuItemService.isHidden(player));
                render(player, holder);
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
        if (screen == Screen.SOUND || screen == Screen.PARTICLE) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.lore(List.of(text("設定へ戻る", NamedTextColor.GRAY)));
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
        PARTICLE
    }

    private static final class Holder implements MenuHolder {
        private final UUID owner;
        private final Screen screen;
        private Inventory inventory;

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
