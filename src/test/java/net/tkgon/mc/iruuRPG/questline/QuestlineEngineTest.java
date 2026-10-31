package net.tkgon.mc.iruuRPG.questline;

import net.tkgon.mc.iruuRPG.questline.QuestlineData.Status;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestlineEngineTest {

    /** The example of the design (3-1), the format the tool site writes. */
    private static final String BOAR_HUNT = """
            format: 1
            id: boar_hunt
            name: "畑を荒らすイノシシ"
            category: main
            description: ["アトラス村の畑にイノシシが出る。"]
            repeatable: false
            start:
              npc: atlas_farmer
              when:
                - { type: quest, quest: tutorial_intro, status: completed }
                - { type: level, min: 3 }
              offer:
                - { speaker: atlas_farmer, text: "イノシシが畑を荒らして困ってるんだ…" }
            flags:
              boar_kills: { type: number, min: 0, max: 5 }
              talked_wife: { type: bool }
            initial: talk
            steps:
              talk:
                name: "農夫と話す"
                talk:
                  atlas_farmer:
                    - { speaker: atlas_farmer, text: "東の森のほうから来るんだ。" }
                    - "イノシシを5匹倒そう。"
                transitions:
                  - to: kill
                    when: [ { type: event, event: npc-talk, npc: atlas_farmer } ]
              kill:
                name: "イノシシを倒す"
                progress: { flag: boar_kills, max: 5 }
                spawns:
                  - { type: mob, mob: wild_boar, at: { world: world, x: 210.5, y: 70, z: -40.5 }, count: 3 }
                on-event:
                  - event: { type: event, event: mob-kill, mob: wild_boar }
                    do: [ { type: add-flag, flag: boar_kills, value: 1 } ]
                transitions:
                  - to: report
                    when: [ { type: flag, flag: boar_kills, op: ">=", value: 5 } ]
              report:
                name: "農夫に報告する"
                talk:
                  atlas_farmer:
                    - { speaker: atlas_farmer, text: "本当にやってくれたのか！" }
                transitions:
                  - to: done
                    when: [ { type: event, event: npc-talk, npc: atlas_farmer } ]
              done:
                end: complete
                enter:
                  - { type: reward, money: 120, exp: 80, items: [ { item: potion_small, amount: 2 } ] }
                  - { type: set-flag, flag: atlas_trusted, value: true }
            """;

    private static final String FLAGS = """
            format: 1
            flags:
              atlas_trusted: { type: bool, description: "アトラス村に信用された" }
              wolves_total: { type: number, min: 0, max: 9999 }
            rules:
              - event: { type: event, event: mob-kill, mob: forest_wolf }
                do: [ { type: add-flag, flag: wolves_total, value: 1 } ]
            """;

    /** A line that starts by itself after boar_hunt and finishes on its first event. */
    private static final String AFTER = """
            id: after
            initial: wait
            start:
              when: [ { type: quest, quest: boar_hunt, status: completed } ]
            steps:
              wait:
                transitions:
                  - { to: end, when: { type: event, event: dungeon-clear, dungeon: cave } }
              end: { end: complete }
            """;

    private static final class FakeGame implements QuestlineEngine.Game {
        long now = 1_000L;
        int level = 5;
        final List<String> performed = new ArrayList<>();
        final List<String> log = new ArrayList<>();

        @Override public long now() { return now; }
        @Override public int level() { return level; }
        @Override public String classId() { return "warrior"; }
        @Override public int itemCount(String item) { return 0; }
        @Override public void takeItem(String item, int amount) { log.add("take " + item); }
        @Override public void perform(Questline line, Questline.Act act) { performed.add(act.type()); }
        @Override public void started(Questline line) { log.add("start " + line.id()); }
        @Override public void stepChanged(Questline line, Questline.Step step) { log.add(line.id() + ">" + step.id()); }
        @Override public void finished(Questline line, Questline.End end) { log.add("end " + line.id() + " " + end); }
        @Override public void warn(String message) { log.add("warn " + message); }
    }

    private static Map<?, ?> yaml(String text) {
        return new Yaml().load(text);
    }

    private static QuestlineEngine engine(List<String> warnings, String... files) {
        QuestlineParser.FlagBook book = QuestlineParser.parseFlags(yaml(FLAGS), warnings);
        Map<String, Questline> lines = new HashMap<>();
        for (String file : files) {
            Map<?, ?> raw = yaml(file);
            Questline line = QuestlineParser.parse(raw.get("id").toString(), raw, book.flags().keySet(), warnings);
            assertNotNull(line);
            lines.put(line.id(), line);
        }
        return new QuestlineEngine(lines, book);
    }

    @Test
    void theExampleOfTheDesignReadsWithoutWarnings() {
        List<String> warnings = new ArrayList<>();
        QuestlineEngine engine = engine(warnings, BOAR_HUNT, AFTER);
        assertEquals(List.of(), warnings);
        Questline line = engine.line("boar_hunt");
        assertEquals("atlas_farmer", line.start().npc());
        assertEquals(2, line.step("talk").talk().get("atlas_farmer").size());
        assertEquals(Questline.End.COMPLETE, line.step("done").end());
        assertEquals(1, line.step("kill").spawns().size());
    }

    @Test
    void wrongPartsAreWarnedAndLeftOut() {
        List<String> warnings = new ArrayList<>();
        Map<?, ?> raw = yaml("""
                id: broken
                initial: a
                steps:
                  a:
                    on:
                      - event: { type: event, event: flew-away }
                        do: [ { type: explode }, { type: set-flag, flag: nowhere } ]
                    transitions:
                      - { to: missing, when: { type: flag, flag: x } }
                      - { to: b, when: { type: teleport-to-moon } }
                  b: { end: maybe }
                """);
        Questline line = QuestlineParser.parse("broken", raw, Set.of(), warnings);
        assertNotNull(line, "a line with bad parts still loads");
        String all = String.join("\n", warnings);
        assertTrue(all.contains("flew-away"), all);
        assertTrue(all.contains("unknown action 'explode'"), all);
        assertTrue(all.contains("no step 'missing'"), all);
        assertTrue(all.contains("unknown condition 'teleport-to-moon'"), all);
        assertTrue(all.contains("'maybe' is not complete / fail"), all);
        assertTrue(all.contains("flag 'nowhere' is not declared"), all);
        // the unknown condition never holds: the transition to b never fires
        QuestlineEngine engine = new QuestlineEngine(Map.of("broken", line), QuestlineParser.FlagBook.EMPTY);
        QuestlineData data = new QuestlineData();
        FakeGame game = new FakeGame();
        engine.start(data, game, "broken", true);
        assertEquals("a", data.line("broken").step);

        assertNull(QuestlineParser.parse("x", yaml("id: x\ninitial: nope\nsteps: { a: {} }"), Set.of(), new ArrayList<>()));

        // a dot would split the key in the player's file: such a flag is left out, such a file is not loaded
        List<String> idWarnings = new ArrayList<>();
        Questline dotted = QuestlineParser.parse("ok", yaml("initial: a\nflags: { \"bad.name\": { type: bool }, good: { type: bool } }\nsteps: { a: {} }"), Set.of(), idWarnings);
        assertEquals(Set.of("good"), dotted.flags().keySet());
        assertTrue(String.join("\n", idWarnings).contains("bad.name"));
        assertNull(QuestlineParser.parse("bad.line", yaml("initial: a\nsteps: { a: {} }"), Set.of(), new ArrayList<>()));
    }

    @Test
    void aLineGoesFromTalkToKillsToReportToItsEnd() {
        QuestlineEngine engine = engine(new ArrayList<>(), BOAR_HUNT, AFTER);
        QuestlineData data = new QuestlineData();
        FakeGame game = new FakeGame();

        // its condition (the tutorial done) does not hold: it does not start
        assertFalse(engine.start(data, game, "boar_hunt", false));
        assertTrue(engine.start(data, game, "boar_hunt", true));
        assertEquals("talk", data.line("boar_hunt").step);
        assertEquals(2, engine.talk(data, "atlas_farmer").size());

        engine.fire(data, game, QuestEvent.of("npc-talk", "npc", "someone_else"));
        assertEquals("talk", data.line("boar_hunt").step);
        engine.fire(data, game, QuestEvent.of("npc-talk", "npc", "atlas_farmer"));
        assertEquals("kill", data.line("boar_hunt").step);

        for (int kill = 0; kill < 4; kill++) engine.fire(data, game, QuestEvent.of("mob-kill", "mob", "wild_boar"));
        engine.fire(data, game, QuestEvent.of("mob-kill", "mob", "forest_wolf"));
        assertEquals("4 / 5", engine.progress(data, "boar_hunt"));
        assertEquals(1L, data.flags().get("wolves_total"), "the shared rule counts outside the quest");
        engine.fire(data, game, QuestEvent.of("mob-kill", "mob", "wild_boar"));
        assertEquals("report", data.line("boar_hunt").step);

        // the same talk event that moved talk → kill does not also skip report: the next one does
        engine.fire(data, game, QuestEvent.of("npc-talk", "npc", "atlas_farmer"));
        assertEquals(Status.COMPLETED, data.line("boar_hunt").status);
        assertEquals(List.of("reward"), game.performed);
        assertEquals(true, data.flags().get("atlas_trusted"));
        assertEquals(1, data.line("boar_hunt").runs);
        // the completed line starts the one that waits for it, by itself
        assertEquals(Status.ACTIVE, data.line("after").status);
        engine.fire(data, game, QuestEvent.of("dungeon-clear", "dungeon", "cave"));
        assertEquals(Status.COMPLETED, data.line("after").status);
        // not repeatable: no second start
        assertFalse(engine.start(data, game, "boar_hunt", false));
    }

    @Test
    void numberFlagsStayInTheirRangeAndWritingOneSettlesTheLine() {
        QuestlineEngine engine = engine(new ArrayList<>(), BOAR_HUNT);
        QuestlineData data = new QuestlineData();
        FakeGame game = new FakeGame();
        engine.setStep(data, game, "boar_hunt", "kill");
        engine.writeFlag(data, game, "boar_hunt", "boar_kills", 99);
        assertEquals(5L, data.line("boar_hunt").flags.get("boar_kills") instanceof Long ? 5L : -1L);
        assertEquals("report", data.line("boar_hunt").step);
    }

    @Test
    void aPausedLineIgnoresEventsUntilResumed() {
        QuestlineEngine engine = engine(new ArrayList<>(), BOAR_HUNT);
        QuestlineData data = new QuestlineData();
        FakeGame game = new FakeGame();
        engine.setStep(data, game, "boar_hunt", "kill");
        assertTrue(engine.pause(data, game, "boar_hunt"));
        engine.fire(data, game, QuestEvent.of("mob-kill", "mob", "wild_boar"));
        assertNull(data.line("boar_hunt").flags.get("boar_kills"));
        assertTrue(engine.resume(data, game, "boar_hunt"));
        engine.fire(data, game, QuestEvent.of("mob-kill", "mob", "wild_boar"));
        assertEquals(1L, data.line("boar_hunt").flags.get("boar_kills"));
    }

    @Test
    void aLoopOfStepsStopsOnItsBudget() {
        Map<?, ?> raw = yaml("""
                id: loop
                initial: a
                steps:
                  a: { transitions: [ { to: b } ] }
                  b: { transitions: [ { to: a } ] }
                """);
        Questline line = QuestlineParser.parse("loop", raw, Set.of(), new ArrayList<>());
        QuestlineEngine engine = new QuestlineEngine(Map.of("loop", line), QuestlineParser.FlagBook.EMPTY);
        FakeGame game = new FakeGame();
        engine.start(new QuestlineData(), game, "loop", true);
        assertTrue(game.log.stream().anyMatch(entry -> entry.startsWith("warn")), game.log.toString());
    }

    @Test
    void dependentsAreTheLinesThatNeedIt() {
        QuestlineEngine engine = engine(new ArrayList<>(), BOAR_HUNT, AFTER);
        assertEquals(Set.of("after"), engine.dependents("boar_hunt"));
        assertEquals(Set.of("boar_hunt", "after"), engine.dependents("tutorial_intro"));
    }

    @Test
    void anNpcOffersAndWaitsAndTakes() {
        String give = """
                id: deliver
                initial: bring
                start: { npc: atlas_smith }
                steps:
                  bring:
                    transitions:
                      - { to: done, when: { type: event, event: item-give, npc: atlas_smith, item: iron_ore, amount: 3 } }
                  done: { end: complete }
                """;
        QuestlineEngine engine = engine(new ArrayList<>(), BOAR_HUNT, give);
        QuestlineData data = new QuestlineData();
        FakeGame game = new FakeGame();
        // boar_hunt needs the tutorial: not offered; deliver has no conditions: offered
        assertEquals(List.of("deliver"), engine.offers(data, game, "atlas_smith").stream().map(Questline::id).toList());
        assertTrue(engine.offers(data, game, "atlas_farmer").isEmpty());
        data.lines().put("tutorial_intro", new QuestlineData.LineState(Status.COMPLETED, "x", 0));
        assertEquals(List.of("boar_hunt"), engine.offers(data, game, "atlas_farmer").stream().map(Questline::id).toList());

        engine.start(data, game, "deliver", false);
        assertTrue(engine.offers(data, game, "atlas_smith").isEmpty(), "a running line is not offered again");
        assertTrue(engine.waitsOn(data, "atlas_smith"));
        assertEquals("iron_ore", engine.gives(data, "atlas_smith").get(0).string("item"));
        assertEquals(List.of("deliver 開始", "deliver › bring (渡す)"), engine.usesOf("atlas_smith"));
        engine.fire(data, game, QuestEvent.of("item-give", "npc", "atlas_smith", "item", "iron_ore", "amount", 2));
        assertEquals("bring", data.line("deliver").step, "too few");
        engine.fire(data, game, QuestEvent.of("item-give", "npc", "atlas_smith", "item", "iron_ore", "amount", 3));
        assertEquals(Status.COMPLETED, data.line("deliver").status);
        assertFalse(engine.waitsOn(data, "atlas_smith"));
    }

    private static Questline line(String yamlText) {
        Map<?, ?> raw = yaml(yamlText);
        return QuestlineParser.parse(raw.get("id").toString(), raw, Set.of(), new ArrayList<>());
    }

    @Test
    void oneMainAtATimeAndAnExclusiveLineHoldsTheOthersUntilItEnds() {
        String steps = "start: { npc: giver }\ninitial: a\nsteps:\n  a: { transitions: [ { to: z, when: { type: event, event: npc-talk, npc: n } } ] }\n  z: { end: complete }\n";
        Questline main1 = line("id: main1\ncategory: main\n" + steps);
        Questline main2 = line("id: main2\ncategory: main\n" + steps);
        Questline side = line("id: side\ncategory: sub\n" + steps);
        Questline scene = line("id: scene\ncategory: sub\nstart: { npc: giver }\ninitial: a\nsteps:\n  a:\n    enter: [ { type: cutscene, id: intro, wait: false } ]\n"
                + "    transitions: [ { to: z, when: { type: event, event: npc-talk, npc: m } } ]\n  z: { end: complete }\n");
        assertTrue(scene.exclusive(), "a cutscene makes a line exclusive (priority auto)");
        assertFalse(side.exclusive());
        assertTrue(line("id: p\npriority: parallel\n" + steps.replace("initial", "category: sub\ninitial")).exclusive() == false);
        QuestlineEngine engine = new QuestlineEngine(Map.of("main1", main1, "main2", main2, "side", side, "scene", scene), QuestlineParser.FlagBook.EMPTY);
        QuestlineData data = new QuestlineData();
        FakeGame game = new FakeGame();

        engine.start(data, game, "main1", false);
        engine.start(data, game, "side", false);
        engine.start(data, game, "main2", false);
        assertEquals(Status.PAUSED, data.line("main1").status, "a new main line holds the old one");
        assertEquals(Status.ACTIVE, data.line("side").status, "a sub line runs beside it ");

        engine.start(data, game, "scene", false);
        assertEquals(Status.PAUSED, data.line("main2").status);
        assertEquals(Status.PAUSED, data.line("side").status);
        assertFalse(engine.resume(data, game, "main1"), "nothing comes back while the exclusive line runs");
        engine.fire(data, game, QuestEvent.of("npc-talk", "npc", "m"));
        assertEquals(Status.COMPLETED, data.line("scene").status);
        assertEquals(Status.ACTIVE, data.line("main2").status, "what it held goes on again");
        assertEquals(Status.ACTIVE, data.line("side").status);
        assertEquals(Status.PAUSED, data.line("main1").status, "the main line held by a newer main stays held");
    }

    @Test
    void anNpcIsReadyWhenTalkingToItFinishesALine() {
        QuestlineEngine engine = engine(new ArrayList<>(), BOAR_HUNT);
        QuestlineData data = new QuestlineData();
        FakeGame game = new FakeGame();
        engine.setStep(data, game, "boar_hunt", "kill");
        assertFalse(engine.ready(data, game, "atlas_farmer"));
        assertTrue(engine.waitsOn(data, "atlas_farmer") == false);
        engine.setStep(data, game, "boar_hunt", "report");
        assertTrue(engine.ready(data, game, "atlas_farmer"));
        engine.setAssignedNpcs(line -> line.equals("boar_hunt") ? Set.of("someone") : Set.of());
        assertTrue(engine.offeredBy(engine.line("boar_hunt"), "someone"));
    }

    @Test
    void anAreaEventMatchesTheSameArea() {
        Questline.Cond filter = new Questline.Cond("event", Map.of("event", "area-enter", "world", "world", "x", 10, "y", 64, "z", -5, "radius", 4), List.of());
        QuestEvent inside = QuestEvent.of("area-enter", "area", QuestEvent.areaKey(filter.args()));
        assertTrue(inside.matches(filter));
        assertFalse(QuestEvent.of("area-enter", "area", "world@0.0,0.0,0.0~4.0").matches(filter));
    }
}
