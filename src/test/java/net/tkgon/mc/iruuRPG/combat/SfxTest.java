package net.tkgon.mc.iruuRPG.combat;

import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** t166: the sounds of the blades are sharp (no vanilla sweep, which sounded blunt), and the config can give a role its own layers. */
class SfxTest {

    @Test
    void noRoleUsesTheVanillaSweep() {
        for (Sfx role : Sfx.values()) {
            for (Sfx.Layer layer : role.layers()) assertNotEquals(Sound.ENTITY_PLAYER_ATTACK_SWEEP, layer.sound(), role.name());
        }
    }

    @Test
    void aLineIsASoundItsVolumeAndItsPitch() {
        assertEquals(new Sfx.Layer(Sound.ITEM_TRIDENT_THROW, 0.5f, 1.7f), Sfx.layer("ITEM_TRIDENT_THROW 0.5 1.7"));
        // the game's own name of it too
        assertEquals(new Sfx.Layer(Sound.ITEM_TRIDENT_THROW, 0.5f, 1.7f), Sfx.layer("minecraft:item.trident.throw 0.5 1.7"));
        assertNull(Sfx.layer("ITEM_TRIDENT_THROW 0.5"));
        assertNull(Sfx.layer("NO_SUCH_SOUND 0.5 1.0"));
        assertNull(Sfx.layer("ITEM_TRIDENT_THROW loud 1.0"));
        assertNull(Sfx.layer(null));
    }

    @Test
    void theConfigGivesARoleItsOwnLayers() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("sound-design.blade", List.of("ITEM_TRIDENT_THROW 0.4 1.9", "nonsense"));
        config.set("sound-design.NOT_A_ROLE", List.of("ITEM_TRIDENT_THROW 0.4 1.9"));
        List<String> problems = new ArrayList<>();
        try {
            Sfx.configure(config.getConfigurationSection("sound-design"), problems::add);
            assertEquals(1, Sfx.BLADE.layers().length);
            assertEquals(new Sfx.Layer(Sound.ITEM_TRIDENT_THROW, 0.4f, 1.9f), Sfx.BLADE.layers()[0]);
            // the others keep their own
            assertEquals(3, Sfx.HIT_CUT.layers().length);
            assertEquals(2, problems.size(), problems.toString());
            assertTrue(problems.stream().anyMatch(problem -> problem.contains("NOT_A_ROLE")));
        } finally {
            Sfx.configure(null, problem -> { });
        }
        assertEquals(3, Sfx.BLADE.layers().length);
    }
}
