package net.tkgon.mc.iruuRPG.tools.procedural.plot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** (t313) What the walls leave for the parts put on afterwards: the doors (where one stands, its lower half's row, the way out) and the posts of each side. */
final class Marks {

    /** A door of storey {@code storey} of lot {@code lot}: its lower half at {@code (x, y, z)} (in the wall, one in from the frame), {@code out} the way out. */
    record Door(int lot, int storey, int side, int x, int y, int z, int out, int width) {
    }

    final List<Door> doors = new ArrayList<>();
    /** By lot, storey and side: where along the side (as {@link Area#along} counts) the posts stand. */
    private final Map<String, List<Integer>> posts = new HashMap<>();

    void post(int lot, int storey, int side, int t) {
        List<Integer> list = posts.computeIfAbsent(lot + ":" + storey + ":" + side, unused -> new ArrayList<>());
        if (!list.contains(t)) list.add(t);
    }

    /** Everything another lot's marks hold, added to these. */
    void addAll(Marks other) {
        doors.addAll(other.doors);
        for (Map.Entry<String, List<Integer>> entry : other.posts.entrySet()) posts.computeIfAbsent(entry.getKey(), unused -> new ArrayList<>()).addAll(entry.getValue());
    }

    List<Integer> posts(int lot, int storey, int side) {
        List<Integer> list = new ArrayList<>(posts.getOrDefault(lot + ":" + storey + ":" + side, List.of()));
        list.sort(Integer::compare);
        return list;
    }
}
