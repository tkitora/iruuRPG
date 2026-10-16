package net.tkgon.mc.iruuRPG.party;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Parties: a leader invites players (up to party.max-size, default 4). Members stay in the party while offline
 * (until they leave, are removed, or the server restarts). Parties go into dungeons together, share kill XP with
 * members nearby, and have their own chat.
 */
public final class PartyService implements Listener {

    /** One party. The first member is the leader. */
    public static final class Party {
        private final UUID id = UUID.randomUUID();
        private final LinkedHashSet<UUID> members = new LinkedHashSet<>();
        private UUID leader;

        public UUID leader() {
            return leader;
        }

        public List<UUID> members() {
            return new ArrayList<>(members);
        }

        /** Members online now, leader first. */
        public List<Player> online() {
            List<Player> players = new ArrayList<>();
            for (UUID id : members) {
                Player player = Bukkit.getPlayer(id);
                if (player != null) players.add(player);
            }
            return players;
        }

        public int size() {
            return members.size();
        }
    }

    private record Invite(UUID party, UUID inviter, long until) {
    }

    private static final long INVITE_MILLIS = 60_000L;

    private final JavaPlugin plugin;
    private final Map<UUID, Party> byMember = new HashMap<>();
    /** invited player -> the invite */
    private final Map<UUID, Invite> invites = new HashMap<>();
    /** party id -> party (invites point at the party, not at a leader who may change) */
    private final Map<UUID, Party> byId = new HashMap<>();

    public PartyService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public int maxSize() {
        return Math.max(2, plugin.getConfig().getInt("party.max-size", 4));
    }

    /** The party this player is in, or null. */
    public Party partyOf(UUID player) {
        return byMember.get(player);
    }

    public boolean isLeader(UUID player) {
        Party party = byMember.get(player);
        return party != null && player.equals(party.leader);
    }

    /** Both are in the same party. */
    public boolean together(UUID a, UUID b) {
        Party party = byMember.get(a);
        return party != null && party == byMember.get(b);
    }

    // ---- invites -------------------------------------------------------------------------------------------------

    public void invite(Player inviter, Player target) {
        if (inviter.equals(target)) {
            inviter.sendMessage(Component.text("[パーティー] 自分は招待できません。", NamedTextColor.RED));
            return;
        }
        Party party = byMember.get(inviter.getUniqueId());
        if (party != null && !inviter.getUniqueId().equals(party.leader)) {
            inviter.sendMessage(Component.text("[パーティー] 招待できるのはリーダーだけです。", NamedTextColor.RED));
            return;
        }
        if (byMember.containsKey(target.getUniqueId())) {
            inviter.sendMessage(Component.text("[パーティー] " + target.getName() + " はもう別のパーティーにいます。", NamedTextColor.RED));
            return;
        }
        if (party != null && party.size() >= maxSize()) {
            inviter.sendMessage(Component.text("[パーティー] これ以上は入れません。(最大 " + maxSize() + " 人)", NamedTextColor.RED));
            return;
        }
        if (party == null) {
            // inviting someone starts a party with the inviter as the leader
            party = new Party();
            party.leader = inviter.getUniqueId();
            party.members.add(inviter.getUniqueId());
            byMember.put(inviter.getUniqueId(), party);
            byId.put(party.id, party);
            inviter.sendMessage(Component.text("[パーティー] パーティーを作った。(リーダー: あなた)", NamedTextColor.GREEN));
        }
        invites.put(target.getUniqueId(), new Invite(party.id, inviter.getUniqueId(), System.currentTimeMillis() + INVITE_MILLIS));
        inviter.sendMessage(Component.text("[パーティー] " + target.getName() + " を招待した。(60秒)", NamedTextColor.YELLOW));
        target.sendMessage(Component.text("[パーティー] " + inviter.getName() + " からパーティーへの招待 ", NamedTextColor.YELLOW)
                .append(Component.text("[参加する]", NamedTextColor.GREEN, TextDecoration.BOLD)
                        .clickEvent(ClickEvent.runCommand("/party accept"))
                        .hoverEvent(HoverEvent.showText(Component.text("クリックでパーティーに入る"))))
                .append(Component.text(" "))
                .append(Component.text("[断る]", NamedTextColor.RED, TextDecoration.BOLD)
                        .clickEvent(ClickEvent.runCommand("/party deny"))));
        target.playSound(target.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.6f, 1.4f);
    }

    public void accept(Player player) {
        Invite invite = invites.remove(player.getUniqueId());
        Party party = invite == null ? null : byId.get(invite.party());
        if (invite == null || invite.until() < System.currentTimeMillis() || party == null || party.members.isEmpty()) {
            player.sendMessage(Component.text("[パーティー] 参加できる招待がありません。(期限切れかもしれません)", NamedTextColor.RED));
            return;
        }
        if (byMember.containsKey(player.getUniqueId())) {
            player.sendMessage(Component.text("[パーティー] もう別のパーティーにいます。(/party leave で抜けられる)", NamedTextColor.RED));
            return;
        }
        if (party.size() >= maxSize()) {
            player.sendMessage(Component.text("[パーティー] もう満員です。", NamedTextColor.RED));
            return;
        }
        party.members.add(player.getUniqueId());
        byMember.put(player.getUniqueId(), party);
        tell(party, Component.text(player.getName() + " がパーティーに入った。(" + party.size() + " 人)", NamedTextColor.GREEN));
    }

    public void deny(Player player) {
        Invite invite = invites.remove(player.getUniqueId());
        if (invite == null) {
            player.sendMessage(Component.text("[パーティー] 断れる招待がありません。", NamedTextColor.RED));
            return;
        }
        player.sendMessage(Component.text("[パーティー] 招待を断った。", NamedTextColor.GRAY));
        Player inviter = Bukkit.getPlayer(invite.inviter());
        if (inviter != null) inviter.sendMessage(Component.text("[パーティー] " + player.getName() + " に断られた。", NamedTextColor.GRAY));
    }

    // ---- leaving -------------------------------------------------------------------------------------------------

    public void leave(Player player) {
        Party party = byMember.get(player.getUniqueId());
        if (party == null) {
            player.sendMessage(Component.text("[パーティー] パーティーに入っていません。", NamedTextColor.RED));
            return;
        }
        player.sendMessage(Component.text("[パーティー] パーティーを抜けた。", NamedTextColor.GRAY));
        remove(party, player.getUniqueId(), player.getName() + " がパーティーを抜けた。");
    }

    public void kick(Player leader, String name) {
        Party party = byMember.get(leader.getUniqueId());
        if (party == null || !leader.getUniqueId().equals(party.leader)) {
            leader.sendMessage(Component.text("[パーティー] 外せるのはリーダーだけです。", NamedTextColor.RED));
            return;
        }
        UUID target = memberByName(party, name);
        if (target == null || target.equals(leader.getUniqueId())) {
            leader.sendMessage(Component.text("[パーティー] その人はパーティーにいません。", NamedTextColor.RED));
            return;
        }
        Player kicked = Bukkit.getPlayer(target);
        if (kicked != null) kicked.sendMessage(Component.text("[パーティー] パーティーから外された。", NamedTextColor.GRAY));
        remove(party, target, nameOf(target) + " をパーティーから外した。");
    }

    public void promote(Player leader, String name) {
        Party party = byMember.get(leader.getUniqueId());
        if (party == null || !leader.getUniqueId().equals(party.leader)) {
            leader.sendMessage(Component.text("[パーティー] リーダーを渡せるのはリーダーだけです。", NamedTextColor.RED));
            return;
        }
        UUID target = memberByName(party, name);
        if (target == null) {
            leader.sendMessage(Component.text("[パーティー] その人はパーティーにいません。", NamedTextColor.RED));
            return;
        }
        party.leader = target;
        tell(party, Component.text(nameOf(target) + " が新しいリーダーになった。", NamedTextColor.GOLD));
    }

    public void disband(Player leader) {
        Party party = byMember.get(leader.getUniqueId());
        if (party == null || !leader.getUniqueId().equals(party.leader)) {
            leader.sendMessage(Component.text("[パーティー] 解散できるのはリーダーだけです。", NamedTextColor.RED));
            return;
        }
        tell(party, Component.text("パーティーは解散した。", NamedTextColor.GRAY));
        for (UUID id : party.members()) byMember.remove(id);
        party.members.clear();
        byId.remove(party.id);
    }

    private void remove(Party party, UUID member, String message) {
        party.members.remove(member);
        byMember.remove(member);
        if (member.equals(party.leader) && !party.members.isEmpty()) {
            // the leader left: the next member leads
            party.leader = party.members.iterator().next();
            message += " " + nameOf(party.leader) + " が新しいリーダー。";
        }
        tell(party, Component.text(message, NamedTextColor.GRAY));
        if (party.members.size() <= 1) {
            // one person is not a party
            for (UUID id : party.members()) {
                byMember.remove(id);
                Player last = Bukkit.getPlayer(id);
                if (last != null) last.sendMessage(Component.text("[パーティー] 1人になったので、パーティーは解散した。", NamedTextColor.GRAY));
            }
            party.members.clear();
            byId.remove(party.id);
        }
    }

    private UUID memberByName(Party party, String name) {
        for (UUID id : party.members) {
            if (nameOf(id).equalsIgnoreCase(name)) return id;
        }
        return null;
    }

    public static String nameOf(UUID id) {
        String name = Bukkit.getOfflinePlayer(id).getName();
        return name == null ? "?" : name;
    }

    // ---- chat and info -------------------------------------------------------------------------------------------

    public void chat(Player player, String message) {
        Party party = byMember.get(player.getUniqueId());
        if (party == null) {
            player.sendMessage(Component.text("[パーティー] パーティーに入っていません。", NamedTextColor.RED));
            return;
        }
        Component line = Component.text("[パーティー] ", NamedTextColor.AQUA)
                .append(Component.text(player.getName(), NamedTextColor.WHITE))
                .append(Component.text(": " + message, NamedTextColor.AQUA));
        for (Player member : party.online()) member.sendMessage(line);
    }

    public void list(Player player) {
        Party party = byMember.get(player.getUniqueId());
        if (party == null) {
            player.sendMessage(Component.text("[パーティー] パーティーに入っていません。/party invite <名前> で作れる。", NamedTextColor.GRAY));
            return;
        }
        player.sendMessage(Component.text("[パーティー] " + party.size() + " / " + maxSize() + " 人", NamedTextColor.AQUA));
        for (UUID id : party.members) {
            boolean online = Bukkit.getPlayer(id) != null;
            player.sendMessage(Component.text("  " + (id.equals(party.leader) ? "★ " : "・ ") + nameOf(id) + (online ? "" : " (オフライン)"),
                    online ? NamedTextColor.WHITE : NamedTextColor.GRAY));
        }
    }

    private void tell(Party party, Component message) {
        Component line = Component.text("[パーティー] ", NamedTextColor.AQUA).append(message);
        for (Player member : party.online()) member.sendMessage(line);
    }

    /** Pending invite for this player (for tab completion / menus): the inviter's name or null. */
    public String invitedBy(UUID player) {
        Invite invite = invites.get(player);
        if (invite == null || invite.until() < System.currentTimeMillis()) return null;
        return nameOf(invite.inviter());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Party party = byMember.get(event.getPlayer().getUniqueId());
        if (party != null) tell(party, Component.text(event.getPlayer().getName() + " がログインした。", NamedTextColor.GRAY));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        invites.remove(event.getPlayer().getUniqueId());
        Party party = byMember.get(event.getPlayer().getUniqueId());
        if (party == null) return;
        Bukkit.getScheduler().runTask(plugin, () ->
                tell(party, Component.text(event.getPlayer().getName() + " がログアウトした。(パーティーには残っている)", NamedTextColor.GRAY)));
    }

    /** Online members near this player (same world, within range), the player excluded. */
    public List<Player> nearbyMembers(Player player, double range) {
        List<Player> near = new ArrayList<>();
        Party party = byMember.get(player.getUniqueId());
        if (party == null) return near;
        for (Player member : party.online()) {
            if (member.equals(player) || !member.getWorld().equals(player.getWorld())) continue;
            if (member.getLocation().distanceSquared(player.getLocation()) <= range * range) near.add(member);
        }
        return near;
    }

    /** Everyone in this player's party (online), the player first; just the player when not in a party. */
    public List<Player> withParty(Player player) {
        List<Player> players = new ArrayList<>();
        players.add(player);
        Party party = byMember.get(player.getUniqueId());
        if (party == null) return players;
        for (Player member : party.online()) {
            if (!member.equals(player)) players.add(member);
        }
        return players;
    }

    public Set<UUID> memberIds(UUID player) {
        Party party = byMember.get(player);
        return party == null ? Set.of(player) : Set.copyOf(party.members);
    }
}
