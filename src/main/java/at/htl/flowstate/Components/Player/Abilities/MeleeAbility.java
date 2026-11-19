package at.htl.flowstate.Components.Player.Abilities;

import at.htl.flowstate.Combat.Hit;
import at.htl.flowstate.Components.Player.Attacks.MeleeSwingComponent;
import at.htl.flowstate.Components.Player.LoadoutComponent;
import at.htl.flowstate.Skills.Branch;
import at.htl.flowstate.Visuals.Particles;
import com.almasb.fxgl.entity.Entity;
import javafx.geometry.Point2D;
import javafx.scene.paint.Color;

/**
 * Shared melee feel for every melee weapon:
 * <ul>
 *   <li>Forward slashes in the facing direction; S + attack in the air is a down-slash that pogos off what it hits.</li>
 *   <li>Input buffer: a press shortly before the weapon is ready is queued and fires on the first ready frame.</li>
 *   <li>Flow: attacking on the beat (buffered, or within a short grace after ready) right after a hit that
 *       connected raises the flow chain, up to {@link #FLOW_MAX}. Each level adds damage and impact.
 *       Mashing too early or attacking late drops it. A glint at the hand marks the moment the window opens.</li>
 *   <li>Commitment: short lunge on grounded horizontal attacks, slightly slower movement mid-swing.</li>
 * </ul>
 * Weapon stats (damage, interval, reach, swing time) are untouched; flow is a bonus on top.
 */
public abstract class MeleeAbility extends Ability implements MeleeSwingComponent.Listener {
    protected static final double BUFFER = 0.2;
    protected static final double GRACE = 0.14;
    protected static final int FLOW_MAX = 5;
    private static final double FLOW_DAMAGE = 0.08;
    private static final double FLOW_IMPACT = 0.12;
    private static final double LUNGE_SPEED = 280;
    private static final double LUNGE_TIME = 0.07;
    private static final double SWING_MOVE_MULT = 0.75;
    // screen angles (0 = right, 90 = down) for a right-facing player; mirrored when facing left
    private static final double SLASH_FROM = -130, SLASH_TO = 45;   // overhead, behind -> front-down
    private static final double DOWN_FROM = 20, DOWN_TO = 160;      // under the feet, front -> back
    private static final Color[] FLOW_COLORS = {
            Color.WHITE, Color.web("#fff3a0"), Color.web("#ffd84d"), Color.web("#ffa93d"), Color.web("#ff6b3d"), Color.web("#62e0ff")
    };

    private Runnable queued;
    private double sinceReady = 99;
    private boolean wasReady = true;
    private boolean connected = false;
    private boolean cueShown = true;
    private double swingLeft = 0;
    protected int flow = 0;

    protected MeleeAbility(Entity player, LoadoutComponent loadout) {
        super(player, loadout, Branch.MELEE);
    }

    //-----Timing-----

    /** Routes a tap attack through the buffer and the flow timing. */
    protected void press(Runnable attack) {
        if (ready()) {
            fire(attack, sinceReady <= GRACE);
        } else if (loadout.getCooldown(branch) <= BUFFER) {
            queued = attack;
        } else {
            // mashing: way too early
            queued = null;
            breakFlow();
        }
    }

    /** Runs an attack now; {@code onBeat} says whether it was timed into the window. */
    protected void fire(Runnable attack, boolean onBeat) {
        if (onBeat && connected) {
            flow = Math.min(FLOW_MAX, flow + 1);
            flowCue();
        } else {
            flow = 0;
        }
        connected = false;
        cueShown = false;
        attack.run();
    }

    protected void breakFlow() {
        flow = 0;
        connected = false;
    }

    @Override
    public void update(double tpf) {
        boolean r = ready();
        if (r && !wasReady) sinceReady = 0;
        else if (r) sinceReady += tpf;
        wasReady = r;

        // rhythm cue: the window for the next on-beat attack just opened
        if (!cueShown && connected && (r || loadout.getCooldown(branch) <= BUFFER)) {
            cueShown = true;
            Particles.glint(hand(), new Color[]{flowColor(Math.min(FLOW_MAX, flow + 1))}, 5);
        }

        if (queued != null && r) {
            Runnable q = queued;
            queued = null;
            fire(q, true);
        }
        if (r && sinceReady > GRACE && flow > 0 && queued == null) flow = 0;
        if (swingLeft > 0) swingLeft -= tpf;
    }

    @Override
    public void onDeselect() {
        queued = null;
        flow = 0;
    }

    //-----Swing helpers-----

    /** A swing ready to spawn: its parameters plus what the ability needs to know about it. */
    protected record Slash(MeleeSwingComponent.Params params, double duration, boolean down) {}

    /** S held in the air (not levitating): the next slash goes down. */
    protected boolean wantsDownSlash() {
        return !movement().isGrounded() && !loadout.isLevitating() && movement().isDownHeld();
    }

    /**
     * A slash in the facing direction (overhead, or rising when {@code reverse}), or a down-slash when
     * S is held in the air. Applies flow. Tune it via {@code params()}, then {@link #launch} it.
     */
    protected Slash slash(Hit hit, double duration, boolean reverse) {
        boolean down = wantsDownSlash();
        double from = down ? DOWN_FROM : SLASH_FROM;
        double to = down ? DOWN_TO : SLASH_TO;
        if (reverse && !down) {
            double t = from;
            from = to;
            to = t;
        }
        if (facing() < 0) {
            from = 180 - from;
            to = 180 - to;
        }
        MeleeSwingComponent.Params params = new MeleeSwingComponent.Params(flowed(hit), duration, from, to)
                .color(flowColor(flow))
                .listener(this);
        if (down) params.down();
        return new Slash(params, duration, down);
    }

    /** Spawns the slash: keeps the player facing it and lunges a little on grounded forward slashes. */
    protected Entity launch(MeleeSpec spec, Slash slash, double size, double lungeMult) {
        movement().faceAttack(facing(), slash.duration() * 0.8);
        if (!slash.down() && lungeMult > 0) movement().lunge(facing(), LUNGE_SPEED * lungeMult, LUNGE_TIME);
        swingLeft = slash.duration() * 0.5;
        return spec.swing(slash.params(), size);
    }

    protected Entity launch(MeleeSpec spec, Slash slash) {
        return launch(spec, slash, 1, 1);
    }

    /** Applies the flow bonus to a hit. */
    protected Hit flowed(Hit hit) {
        return hit.withDamage(hit.getDamage() * (1 + FLOW_DAMAGE * flow)).impact(hit.getImpact() * (1 + FLOW_IMPACT * flow));
    }

    protected Point2D hand() {
        return new Point2D(player.getCenter().getX() + facing() * 14, player.getY() + player.getHeight() * 0.35);
    }

    protected static Color flowColor(int level) {
        return FLOW_COLORS[Math.max(0, Math.min(FLOW_COLORS.length - 1, level))];
    }

    private void flowCue() {
        Color c = flowColor(flow);
        Particles.glint(hand(), new Color[]{Color.WHITE, c}, 6 + flow * 2);
        Particles.text(new Point2D(player.getCenter().getX(), player.getY() - 10),
                flow == FLOW_MAX ? "FLOW!" : "x" + (flow + 1), c, 16 + flow * 2);
    }

    //-----Listener-----

    @Override
    public void onConnect(boolean enemy) {
        connected = true;
    }

    /** Marks the current attack as connected (attacks that don't use a swing, e.g. punches). */
    protected void markConnected() {
        connected = true;
    }

    //-----State-----

    @Override
    public double getMoveSpeedMult() {
        return swingLeft > 0 ? SWING_MOVE_MULT : 1;
    }
}
