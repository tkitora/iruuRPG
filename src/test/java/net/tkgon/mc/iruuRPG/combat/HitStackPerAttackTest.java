package net.tkgon.mc.iruuRPG.combat;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** A weapon's "hit" stacks grow once for each attack, however many enemies or times it hits. */
class HitStackPerAttackTest {

    @Test
    void oneAttackGivesOneHitStack() {
        AttackService service = new AttackService(null, null, null, null, null, null, null, null, null, null);
        List<String> triggers = new ArrayList<>();
        service.setStackHook((player, trigger) -> triggers.add(trigger));
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());

        // a hit with no attack started (nothing open): nothing
        service.normalAttackHit(player);
        assertEquals(0, triggers.size());

        // an attack that hits three enemies (an area, a combo step's several hits): once
        service.beginAttack(player);
        service.normalAttackHit(player);
        service.normalAttackHit(player);
        service.normalAttackHit(player);
        assertEquals(List.of("hit"), triggers);

        // the next attack: once more
        service.beginAttack(player);
        service.normalAttackHit(player);
        assertEquals(2, triggers.size());

        // an attack that hits nothing gives nothing, and does not carry over a stack to a later one
        service.beginAttack(player);
        service.beginAttack(player);
        service.normalAttackHit(player);
        assertEquals(3, triggers.size());
    }
}
