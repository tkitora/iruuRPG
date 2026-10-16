package net.tkgon.mc.iruuRPG.chat;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Pattern;

/**
 * Showing an item in chat: writing [item] (or [i] / [アイテム]) in a chat message puts the item in the main hand
 * there; hovering over it shows the item as it is (name, stats, description). /showitem sends it on its own.
 * Party chat (/p) uses the same placeholders.
 */
public final class ChatItems implements Listener, CommandExecutor {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\[(item|i|アイテム)\\]", Pattern.CASE_INSENSITIVE);

    /** The item in the main hand as "[name ×n]" that shows the item on hover, or null for an empty hand. */
    public static Component itemComponent(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType().isAir()) return null;
        ItemStack copy = item.clone();
        ItemMeta meta = copy.getItemMeta();
        Component name = meta != null && meta.hasDisplayName() && meta.displayName() != null
                ? meta.displayName()
                : Component.translatable(copy.translationKey(), NamedTextColor.WHITE);
        Component shown = Component.text("[", NamedTextColor.AQUA)
                .append(name.decoration(TextDecoration.ITALIC, false))
                .append(Component.text(copy.getAmount() > 1 ? " ×" + copy.getAmount() + "]" : "]", NamedTextColor.AQUA));
        return shown.hoverEvent(copy.asHoverEvent());
    }

    /** {@code message} with every placeholder replaced by the held item (unchanged for an empty hand). */
    public static Component withItem(Player player, Component message) {
        Component item = itemComponent(player);
        if (item == null) return message;
        return message.replaceText(TextReplacementConfig.builder().match(PLACEHOLDER).replacement(item).build());
    }

    public static boolean hasPlaceholder(String text) {
        return text != null && PLACEHOLDER.matcher(text).find();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(event.message());
        if (!hasPlaceholder(plain)) return;
        event.message(withItem(event.getPlayer(), event.message()));
    }

    /** /showitem: tells everyone online what you are holding. */
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Player only.");
            return true;
        }
        Component item = itemComponent(player);
        if (item == null) {
            player.sendMessage(Component.text("[アイテム] 見せたいアイテムを手に持ってください。", NamedTextColor.RED));
            return true;
        }
        Bukkit.broadcast(Component.text(player.getName(), NamedTextColor.WHITE)
                .append(Component.text(" がアイテムを見せている: ", NamedTextColor.GRAY))
                .append(item));
        return true;
    }
}
