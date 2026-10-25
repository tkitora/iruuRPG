package net.tkgon.mc.iruuRPG.mob.behavior;

import org.bukkit.Location;
import org.bukkit.entity.Player;

/** The services: keeping the target, and running a query of the environment into a blackboard key. */
final class Services {

    private Services() {
    }

    /** Looks for a target: keeps the one it has while it is valid and within 1.4 times the range, else takes the nearest player in range. */
    static final class UpdateTarget extends Service {
        private final double range;

        UpdateTarget(double range, int interval) {
            this.range = range;
            this.interval = interval;
        }

        @Override
        void run(Ctx c, Node node) {
            if (c.now < (int) c.bb.number("no-target-until", 0)) return;
            Player current = c.bb.target;
            if (current != null && c.host.valid(current, range * 1.4)) return;
            c.bb.target = c.host.findTarget(range);
        }

        @Override
        String label() {
            return "update-target " + range;
        }
    }

    /** Runs the query {@code query} and writes the place it chose to {@code key} (cleared when nothing fits). */
    static final class RunEqs extends Service {
        private final String query;
        private final String key;

        RunEqs(String query, String key, int interval) {
            this.query = query;
            this.key = key;
            this.interval = interval;
        }

        String query() {
            return query;
        }

        @Override
        void run(Ctx c, Node node) {
            EqsQuery eqs = c.queries.get(query);
            Location place = eqs == null ? null : eqs.run(c);
            c.bb.set(key, place);
        }

        @Override
        String label() {
            return "eqs " + query + "→" + key;
        }
    }
}
