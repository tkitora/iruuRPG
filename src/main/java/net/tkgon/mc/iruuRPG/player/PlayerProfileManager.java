package net.tkgon.mc.iruuRPG.player;

import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class PlayerProfileManager {

    private final PlayerProfileStorage storage;
    private final Map<UUID, PlayerProfile> profiles = new HashMap<>();

    public PlayerProfileManager(PlayerProfileStorage storage) {
        this.storage = storage;
    }

    public PlayerProfile load(Player player) {
        PlayerProfile profile = storage.load(player.getUniqueId());
        profiles.put(player.getUniqueId(), profile);
        return profile;
    }

    public PlayerProfile getOrCreate(Player player) {
        return profiles.computeIfAbsent(player.getUniqueId(), storage::load);
    }

    public Optional<PlayerProfile> get(Player player) {
        return Optional.ofNullable(profiles.get(player.getUniqueId()));
    }

    public void unload(Player player) {
        PlayerProfile profile = profiles.remove(player.getUniqueId());
        if (profile != null) {
            storage.save(profile);
        }
    }

    public void save(Player player) {
        get(player).ifPresent(storage::save);
    }

    public void saveAll() {
        for (PlayerProfile profile : profiles.values()) {
            storage.save(profile);
        }
    }

    public Collection<PlayerProfile> allLoaded() {
        return profiles.values();
    }
}
