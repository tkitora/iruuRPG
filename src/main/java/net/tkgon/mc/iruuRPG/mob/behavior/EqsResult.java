package net.tkgon.mc.iruuRPG.mob.behavior;

import org.bukkit.Location;

import java.util.List;

/** What the last run of a query of the environment found, kept for the debug view: every candidate with its score (and whether a test threw it out), and the place chosen. */
public record EqsResult(String query, int tick, List<Candidate> candidates, Location chosen) {

    /** One candidate place: {@code score} 0 (worst) to 1 (best) among the kept ones; {@code kept} false when a test threw it out. */
    public record Candidate(Location place, double score, boolean kept) {
    }
}
