package at.htl.flowstate;

/**
 * The {@code FLOWSTATE_*} testing switches (listed in the README), read from the environment once, at startup.
 * Nothing else in the game reads the environment.
 */
public final class DevFlags {
    public static final boolean AUTOSTART = flag("FLOWSTATE_AUTOSTART");
    public static final boolean FPS_LOG = flag("FLOWSTATE_FPS");
    public static final boolean ALL_ELITES = flag("FLOWSTATE_ELITES");
    public static final boolean ALL_MIMICS = flag("FLOWSTATE_MIMICS");
    public static final boolean INFINITE_SKILL_POINTS = flag("FLOWSTATE_INFINITE_SP");
    /** Comma-separated demon names that are the only ones to spawn; null or blank: normal spawning. */
    public static final String ONLY_DEMONS = System.getenv("FLOWSTATE_DEMONS");
    /** Folder for screenshots; null: none are taken. */
    public static final String SHOTS_DIR = System.getenv("FLOWSTATE_SHOTS");
    public static final double SHOTS_EVERY_S = number("FLOWSTATE_SHOTS_EVERY", 1.0);
    public static final int SHOTS_MAX = (int) number("FLOWSTATE_SHOTS_MAX", 30);

    private DevFlags() {}

    private static boolean flag(String env) {
        return "1".equals(System.getenv(env));
    }

    private static double number(String env, double fallback) {
        String v = System.getenv(env);
        if (v == null) return fallback;
        try {
            return Double.parseDouble(v);
        } catch (NumberFormatException e) {
            System.err.println(env + "=" + v + " is not a number, using " + fallback);
            return fallback;
        }
    }
}
