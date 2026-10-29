package net.tkgon.mc.iruuRPG.bgm;

import org.bukkit.entity.Player;

import java.util.List;

/**
 * What the rest of the plugin may ask of the background music: the songs there are, a song for a scene of the game (the dungeon: it plays instead of the volumes until the
 * scene ends), and a listen for the settings menu and the editors. {@link #NONE} does nothing (the BGM is switched off, or has not started).
 */
public interface Bgm {

    /** A song as the menus show it: {@code origin} is {@code "bgm"} (the folder) or {@code "bundled"} (the jar). */
    record SongInfo(String id, String title, double seconds, String origin) {

        private static final java.util.regex.Pattern CODE = java.util.regex.Pattern.compile("\\((Field|Boss|Dungeon)(\\d{3})\\)\\s*$");

        /** The kind of the song from its name "Title (Field001)": フィールド / ボス / ダンジョン, or "" for a song named otherwise. */
        public String category() {
            java.util.regex.Matcher matcher = CODE.matcher(title);
            if (!matcher.find()) return "";
            return switch (matcher.group(1)) {
                case "Field" -> "フィールド";
                case "Boss" -> "ボス";
                default -> "ダンジョン";
            };
        }

        /** The title without the "(Field001)" at the end. */
        public String shortTitle() {
            return CODE.matcher(title).replaceFirst("").trim();
        }

        /** "フィールド001" (kind and number), or "". */
        public String code() {
            java.util.regex.Matcher matcher = CODE.matcher(title);
            return matcher.find() ? category() + matcher.group(2) : "";
        }

        /** "2:34". */
        public String length() {
            int total = (int) Math.round(seconds);
            return (total / 60) + ":" + String.format("%02d", total % 60);
        }
    }

    Bgm NONE = new Bgm() {
        @Override
        public List<SongInfo> songs() {
            return List.of();
        }

        @Override
        public SongInfo song(String id) {
            return null;
        }

        @Override
        public void playScene(Player player, String songId, boolean loop, double fadeSeconds) {
        }

        @Override
        public void endScene(Player player, double fadeSeconds) {
        }

        @Override
        public boolean preview(Player player, String songId) {
            return false;
        }

        @Override
        public void stopPreview(Player player) {
        }

        @Override
        public String previewing(Player player) {
            return null;
        }
    };

    /** Every song, sorted by id. */
    List<SongInfo> songs();

    /** The song of this id, or null. */
    SongInfo song(String id);

    /**
     * Plays a song for a scene (a dungeon run): the volumes of the world are not followed until {@link #endScene}. The song that plays now fades out, this one fades in over
     * {@code fadeSeconds}. Asking again with the song that already plays changes nothing; asking with another one goes over to it. An unknown song does nothing.
     */
    void playScene(Player player, String songId, boolean loop, double fadeSeconds);

    /** Ends the scene: its song fades out over {@code fadeSeconds} and the volumes of the world are followed again. */
    void endScene(Player player, double fadeSeconds);

    /** Plays a song for the player to listen to (looping), over everything else; false when there is no such song. {@link #stopPreview} ends it. */
    boolean preview(Player player, String songId);

    /** Ends the listen; a scene that was going on comes back. */
    void stopPreview(Player player);

    /** The id of the song the player is listening to, or null. */
    String previewing(Player player);
}
