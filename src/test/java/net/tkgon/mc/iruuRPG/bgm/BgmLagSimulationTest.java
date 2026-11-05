package net.tkgon.mc.iruuRPG.bgm;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Plays a song through the planning-ahead channel against a made-up server clock and measures what reaches the player. */
class BgmLagSimulationTest {

    private static final long MS = 1_000_000L;

    private static final class Kept implements BgmChannel.Later {
        final List<long[]> notes = new ArrayList<>();   // due, planned at
        final List<Runnable> tasks = new ArrayList<>();
        long now;

        @Override
        public void at(long dueNanos, Runnable task) {
            notes.add(new long[]{dueNanos, now});
            tasks.add(task);
        }
    }

    static final class Result {
        int planned;
        int sent;
        long maxGap;          // longest silence between two sent notes (ms)
        long lateMax;         // the latest a sent note was (ms after its time)
        int resyncs;
    }

    private static NbsSong load(String id) throws IOException {
        try (InputStream in = BgmLagSimulationTest.class.getResourceAsStream("/bgm/" + id + ".nbs")) {
            return NbsSong.read(id, in.readAllBytes());
        }
    }

    /** {@code ticks}: the wall-clock time (ms) of every server tick. */
    static Result run(String id, long[] ticks, int lead) throws IOException {
        Kept kept = new Kept();
        BgmChannel channel = new BgmChannel();
        long[] clock = {0L};
        channel.planAhead(kept, lead, () -> clock[0]);
        channel.playManual(load(id), true, 0.0);
        Player player = mock(Player.class);
        when(player.getEyeLocation()).thenReturn(new Location(null, 0, 64, 0));
        List<Long> sentAt = new ArrayList<>();
        doAnswer(inv -> { sentAt.add(clock[0]); return null; }).when(player).playSound(any(Location.class), any(Sound.class), any(SoundCategory.class), anyFloat(), anyFloat());
        Result result = new Result();
        int next = 0;   // tasks not yet run
        for (long ms : ticks) {
            // run what falls due before this server tick (the sender thread does not wait for the main thread)
            while (next < kept.tasks.size() && kept.notes.get(next)[0] <= ms * MS) {
                clock[0] = Math.max(kept.notes.get(next)[0], kept.notes.get(next)[1]);
                kept.tasks.get(next).run();
                result.lateMax = Math.max(result.lateMax, (clock[0] - kept.notes.get(next)[0]) / MS);
                next++;
            }
            clock[0] = ms * MS;
            kept.now = clock[0];
            channel.tick(player, 1.0f);
        }
        result.planned = kept.notes.size();
        result.sent = sentAt.size();
        for (int i = 1; i < sentAt.size(); i++) result.maxGap = Math.max(result.maxGap, (sentAt.get(i) - sentAt.get(i - 1)) / MS);
        return result;
    }

    static long[] profile(String kind, int seconds) {
        List<Long> out = new ArrayList<>();
        Random random = new Random(7);
        long t = 0;
        while (t < seconds * 1000L) {
            out.add(t);
            long step = 50;
            switch (kind) {
                case "jitter" -> step = 50 + random.nextInt(41) - 20;
                case "spike300" -> step = (t / 50) % 200 == 199 ? 350 : 50;      // 300 ms extra, every 10 s
                case "spike600" -> step = (t / 50) % 100 == 99 ? 650 : 50;       // 600 ms extra, every 5 s
                case "spike1000" -> step = (t / 50) % 200 == 199 ? 1050 : 50;    // 1 s extra, every 10 s
                case "slow" -> step = 100;                                        // 10 TPS all the time
                case "veryslow" -> step = 200;                                    // 5 TPS
                case "freeze2s" -> step = t >= 20000 && t < 20050 ? 2050 : 50;
                default -> step = 50;
            }
            t += Math.max(1, step);
        }
        return out.stream().mapToLong(Long::longValue).toArray();
    }

    @Test
    void aServerThatStopsForLessThanTheLeadMakesNoGapAndNoLostNote() throws IOException {
        for (String id : new String[]{"colossus_phase1", "mirror_phase2", "broodmother_phase1", "atlas_vindicator_2"}) {
            Result perfect = run(id, profile("perfect", 60), 10);
            for (String kind : new String[]{"jitter", "spike300", "slow", "veryslow"}) {
                Result result = run(id, profile(kind, 60), 10);
                assertEquals(perfect.sent, result.sent, 40, id + " " + kind + ": the same notes reach the player (within the notes still waiting at the end)");
                assertEquals(perfect.maxGap, result.maxGap, 100, id + " " + kind + ": and no new silence");
                assertTrue(result.lateMax <= 50, id + " " + kind + ": and none late (" + result.lateMax + " ms)");
            }
            Result spike = run(id, profile("spike600", 60), 10);
            assertEquals(perfect.sent, spike.sent, id + " spike600: a stop of 600 ms (lead 500) loses no note");
        }
    }

    @Test
    void aLongerStopPausesTheMusicAndDoesNotJumpOrBurst() throws IOException {
        Result perfect = run("colossus_phase1", profile("perfect", 60), 10);
        Result frozen = run("colossus_phase1", profile("freeze2s", 60), 10);
        assertTrue(frozen.maxGap >= perfect.maxGap + 1000 && frozen.maxGap <= perfect.maxGap + 1600, "the 2 s stop is a pause of about 1.5 s: " + frozen.maxGap);
        assertTrue(frozen.lateMax <= 300, "and the notes after it are not hurried out in a burst");
    }
}
