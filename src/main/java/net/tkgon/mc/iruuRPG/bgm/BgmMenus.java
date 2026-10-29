package net.tkgon.mc.iruuRPG.bgm;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** What the menus that list songs share (the song picker of the dungeon editor, the listen screen of the settings): the item of a song and the pages. */
public final class BgmMenus {

    /** How many songs one screen shows (the 5 rows above the row of buttons). */
    public static final int PER_PAGE = 45;

    private BgmMenus() {
    }

    public static int pages(int songs) {
        return Math.max(1, (songs + PER_PAGE - 1) / PER_PAGE);
    }

    /** The songs of a page (0-based), as they are in {@code songs}. */
    public static List<Bgm.SongInfo> page(List<Bgm.SongInfo> songs, int page) {
        int from = Math.max(0, page) * PER_PAGE;
        if (from >= songs.size()) return List.of();
        return songs.subList(from, Math.min(songs.size(), from + PER_PAGE));
    }

    /** A disc of the kind of the song; {@code playing} puts a mark on it. {@code lines} are added under the song's own lines. */
    public static ItemStack item(Bgm.SongInfo song, boolean playing, List<Component> lines) {
        Material disc = switch (song.category()) {
            case "フィールド" -> Material.MUSIC_DISC_MELLOHI;
            case "ボス" -> Material.MUSIC_DISC_PIGSTEP;
            case "ダンジョン" -> Material.MUSIC_DISC_OTHERSIDE;
            default -> Material.MUSIC_DISC_13;
        };
        ItemStack stack = new ItemStack(disc);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;
        String shown = song.category().isEmpty() ? song.title() : song.shortTitle();
        meta.displayName(Component.text((playing ? "▶ " : "") + shown, playing ? NamedTextColor.GREEN : NamedTextColor.AQUA, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        if (!song.category().isEmpty()) lore.add(line(song.code() + " (" + song.category() + ")", NamedTextColor.YELLOW));
        lore.add(line(song.length() + "  /  id: " + song.id(), NamedTextColor.GRAY));
        lore.addAll(lines);
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_POTION_EFFECTS, ItemFlag.HIDE_ATTRIBUTES);
        stack.setItemMeta(meta);
        return stack;
    }

    public static Component line(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }
}
