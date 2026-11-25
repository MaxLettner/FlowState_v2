package at.htl.flowstate.Components.Enemies.Bosses;

/** Something in a boss arena that can be stood on: a ledge, bridge or pillar top, from fromX to toX (world px). */
public record Surface(double fromX, double toX, double top) {
    public boolean spans(double x) {
        return x >= fromX && x <= toX;
    }
}
