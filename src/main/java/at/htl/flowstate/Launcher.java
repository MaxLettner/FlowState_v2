package at.htl.flowstate;

/**
 * Entry point of the runnable fat jar ({@code mvn -Pdist package}).
 * The main class of a jar must not extend javafx.application.Application when JavaFX is on the
 * classpath, otherwise the JVM refuses to start with "JavaFX runtime components are missing".
 */
public final class Launcher {
    private Launcher() {}

    public static void main(String[] args) {
        Game.main(args);
    }
}
