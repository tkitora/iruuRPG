package net.tkgon.mc.iruuRPG;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/** Which commit this jar was built from and when (written into the jar by the build as build-info.properties). */
public final class BuildInfo {

    private static final String TEXT = read();

    private BuildInfo() {
    }

    /** For example "commit 4c22159abc, built 2026-10-25 10:30"; "unknown build" when the jar has no stamp. */
    public static String text() {
        return TEXT;
    }

    private static String read() {
        try (InputStream in = BuildInfo.class.getResourceAsStream("/build-info.properties")) {
            if (in == null) return "unknown build";
            Properties props = new Properties();
            props.load(in);
            return "commit " + props.getProperty("commit", "unknown") + ", built " + props.getProperty("built", "unknown");
        } catch (IOException ex) {
            return "unknown build";
        }
    }
}
