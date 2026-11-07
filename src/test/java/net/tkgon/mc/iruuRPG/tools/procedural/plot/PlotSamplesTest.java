package net.tkgon.mc.iruuRPG.tools.procedural.plot;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * (t313) Pictures of buildings of lots, to look at what the maker makes: {@code ./gradlew test --tests '*PlotSamplesTest' -PplotSamples=<folder>} draws the
 * sample plans and every pattern (or those in {@code -PplotSamplePatterns=1,3}) for a few seeds ({@code -PplotSampleSeeds=3}) as PNGs. Without the folder it
 * does nothing.
 */
class PlotSamplesTest {

    @Test
    void samples() throws IOException {
        String folder = System.getProperty("plot.samples", "");
        if (folder.isBlank()) return;
        File out = new File(folder);
        out.mkdirs();
        Kit kit = Kit.builtin();
        List<Plan> plans = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (int index = 0; index < SamplePlans.count(); index++) {
            plans.add(SamplePlans.plan(index));
            names.add("plan-" + SamplePlans.name(index));
        }
        String only = System.getProperty("plot.samples.patterns", "");
        int seeds = Integer.getInteger("plot.samples.seeds", 3);
        for (Patterns.Pattern pattern : Patterns.Pattern.values()) {
            if (!only.isBlank() && !java.util.Arrays.asList(only.split(",")).contains(Integer.toString(pattern.ordinal() + 1))) continue;
            for (long seed = 1; seed <= seeds; seed++) {
                Patterns.Design design = Patterns.design(pattern, new Patterns.Rules(), kit, seed, 4, 3, Ground.FLAT);
                PlotGenerator.Result result = design.result();
                String name = "pattern-" + (pattern.ordinal() + 1) + "-seed" + seed;
                ImageIO.write(PlotPictures.picture(result.grid(), name + " " + pattern.label + " (score " + String.format(java.util.Locale.ROOT, "%.2f", result.score())
                        + ", tries " + result.tries() + ") " + String.join(" / ", result.notes()), result.problems()), "png", new File(out, name + ".png"));
            }
        }
        for (int i = 0; i < plans.size(); i++) {
            PlotGenerator.Result result = PlotGenerator.build(plans.get(i), kit, 1L, Ground.FLAT);
            ImageIO.write(PlotPictures.picture(result.grid(), names.get(i) + " (" + result.grid().size() + " blocks)", result.problems()), "png",
                    new File(out, names.get(i) + ".png"));
        }
    }
}
