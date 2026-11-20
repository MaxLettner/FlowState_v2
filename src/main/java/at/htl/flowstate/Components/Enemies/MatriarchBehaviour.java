package at.htl.flowstate.Components.Enemies;

import at.htl.flowstate.Combat.Hit;
import at.htl.flowstate.Components.Player.PlayerMovementComponent;
import at.htl.flowstate.Components.Player.PlayerStatsComponent;
import at.htl.flowstate.Sfx;
import at.htl.flowstate.Visuals.Darkness;
import at.htl.flowstate.Visuals.Impact;
import at.htl.flowstate.Visuals.Particles;
import com.almasb.fxgl.core.math.FXGLMath;
import com.almasb.fxgl.dsl.components.HealthDoubleComponent;
import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.entity.SpawnData;
import com.almasb.fxgl.physics.PhysicsComponent;
import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.StrokeLineCap;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;

import static com.almasb.fxgl.dsl.FXGL.entityBuilder;
import static com.almasb.fxgl.dsl.FXGL.getGameWorld;
import static com.almasb.fxgl.dsl.FXGL.runOnce;
import static com.almasb.fxgl.dsl.FXGL.spawn;

/**
 * The Void Matriarch, the second boss: a void spider queen hanging over a chasm (MatriarchArena) on three anchor
 * strands (ThreadBehaviour). Up there she takes half damage. Cut all three and she falls: downed for a few
 * seconds and taking half again as much; then she leaps up out of sight and comes down on fresh strands somewhere
 * else. Her bite (Rappel Strike) is the other time she is in reach: she hangs at the bottom of her line for a moment.
 * <p>
 * Phase 1: Rappel Strike, Weave (re-spins a cut bridge, lays tripwires), Brood Drop (egg sacs that hatch mites).
 * Phase 2 (2/3 HP): also Sever (snaps the bridge the player stands on), Reel In (hooks the player and drags them
 * under her, then bites; a dodge or a hit on her breaks it) and Thread Curtain (strands whip down everywhere but a
 * gap). Phase 3 (1/3 HP): lights out (Darkness), faster, and she moves to a new spot every two attacks.
 * <p>
 * Seen from the front, so the sprite never flips. She is kinematic: everything here moves her by hand.
 */
public class MatriarchBehaviour extends DemonBehaviour {
    // where she hangs: arena x of her centre, and how high above the floor
    static final double[] SPOTS = {250, 620, 820, 1020, 1660};
    static final double INTRO_SPOT = 820;
    static final double HANG_ABOVE = 560;
    // her strands: x offsets from her centre; their knots hang this far below her centre
    private static final double[] STRAND_DX = {-163, 0, 163};
    private static final double KNOT_BELOW = 20;
    private static final double SPINNERET = 95;            // the top of her abdomen, above her centre

    private static final double INTRO_SPEED = 330, CLIMB_SPEED = 520, DROP_SPEED = 1500, LEAVE_SPEED = 1300, ARRIVE_SPEED = 650;
    private static final double GRAVITY = 1800;
    private static final double HALF_H = 85;               // her centre to the bottom of her hitbox
    private static final double HANGING_GUARD = 0.5, DOWNED_GUARD = 1.5;

    private static final double BITE_DAMAGE = 25, BITE_REACH_X = 130, BITE_REACH_Y = 120, BITE_AT = 0.2;
    private static final double RAPPEL_TELL = 0.75, RAPPEL_TELL_DARK = 0.55, HOLD = 1.0, HOLD_DARK = 0.8;
    private static final double CAST_AT = 0.35, CAST_END = 0.9;
    private static final double DOWNED = 4.0, DOWNED_DARK = 3.2;
    private static final double REEL_SHOT = 0.3, REEL_PULL = 1.6, REEL_SPEED = 150, REEL_BREAK_DAMAGE = 30;
    private static final double SCREECH = 1.3;
    private static final double TRIP_W = 120, TRIP_H = 30;
    private static final double CURTAIN_STEP = 210, CURTAIN_GAP = 270;
    private static final int REWARD_SP = 3;

    private static final double HANG_FPS = 12, CAST_FPS = 12, DIVE_FPS = 16, BITE_FPS = 16, CLIMB_FPS = 14,
            FALL_FPS = 14, DOWNED_FPS = 10, RISE_FPS = 12, SCREECH_FPS = 14;
    private static final Color TELL = Color.rgb(185, 140, 255, 0.95);
    private static final Color LINE_GLOW = Color.rgb(140, 77, 255, 0.45), LINE_CORE = Color.rgb(226, 210, 255);
    private static final Color EYE_LIGHT = Color.rgb(170, 120, 255);
    // death scene (s)
    private static final double D_SNAP = 0.25, D_CURL_END = 2.4, D_FLARE = 2.6, D_BURST = 3.0;

    private static Entity current;

    private enum State { INTRO, HANG, CAST, RAPPEL_TELL, DROP, BITE, HOLD, CLIMB_BACK, REEL, FALL, DOWNED, RISE, LEAVE, ARRIVE, SCREECH }

    private enum Attack { RAPPEL, WEAVE, BROOD, SEVER, REEL, CURTAIN }

    private State state = State.INTRO;
    private Attack casting, lastAttack;
    private double timer, cooldown = 1.2;
    private int phase = 1, shownPhase = 1;
    private int attacksHere;                                 // attacks since she came down on this spot (phase 3 moves)
    private double hx, hy;                                   // where she hangs (her centre)
    private final List<Entity> anchors = new ArrayList<>();
    private Point2D target;                                  // rappel: where her centre goes
    private boolean bitten, castDone;
    private double vy;
    private double[] landing;
    private double reelStartHp;
    private boolean introDone;
    private Entity silk;                                     // her drag line / reel line
    private Line silkGlow, silkCore;
    private boolean dying;
    private double deathT;
    private int deathCue;
    private double deathVy;
    private boolean deathLanded;

    /** The living Matriarch, if any (the HUD shows its health bar). */
    public static Entity current() {
        return current != null && current.isActive() ? current : null;
    }

    static int mites() {
        return getGameWorld().getEntitiesByComponent(MiteBehaviour.class).size();
    }

    @Override
    protected void onReady() {
        current = entity;
        hx = MatriarchArena.lockX() + INTRO_SPOT;
        hy = MatriarchArena.groundY() - HANG_ABOVE;
        place(new Point2D(hx, entity.getCenter().getY()));
        // placed at the arena, not at 0: Game's cleanup removes whatever is far behind the camera
        silk = entityBuilder().at(MatriarchArena.lockX(), 0).zIndex(-1).buildAndAttach();
        silkGlow = line(LINE_GLOW, 6);
        silkCore = line(LINE_CORE, 2.2);
        silk.getViewComponent().addChild(new Group(silkGlow, silkCore));
        silk.getViewComponent().setVisible(false);
        spinAnchors();
        Sfx.SILK_SPIN.at(entity.getCenter(), 1, 0.7);
    }

    private static Line line(Color c, double w) {
        Line l = new Line();
        l.setStroke(c);
        l.setStrokeWidth(w);
        l.setStrokeLineCap(StrokeLineCap.ROUND);
        return l;
    }

    //-----the brain-----
    @Override
    protected void think(double tpf) {
        double t = dt(tpf);
        updatePhase();
        switch (state) {
            case INTRO -> intro(tpf);
            case HANG -> hang(t);
            case CAST -> cast(t);
            case RAPPEL_TELL -> rappelTell(t);
            case DROP -> drop(tpf);
            case BITE -> bite(t);
            case HOLD -> {
                sprite.loop("idle", HANG_FPS);
                drawSilk(dragAnchor(), top());
                if ((timer -= t) <= 0) state = State.CLIMB_BACK;
            }
            case CLIMB_BACK -> climbBack(tpf);
            case REEL -> reel(tpf, t);
            case FALL -> fall(tpf);
            case DOWNED -> {
                sprite.loop("downed", DOWNED_FPS);
                if ((timer -= t) <= 0) {
                    state = State.RISE;
                    timer = 0;
                    sprite.once("rise", RISE_FPS, null);
                    Sfx.MATRIARCH_SCREECH.at(entity.getCenter(), 0.6, 1.3);
                }
            }
            case RISE -> {
                timer += t;
                if (timer >= 6 / RISE_FPS) startLeave();
            }
            case LEAVE -> leave(tpf);
            case ARRIVE -> arrive(tpf);
            case SCREECH -> screech(t);
        }
    }

    @Override
    protected void whileDisabled(double tpf) {
        // frozen or stunned in place; while hanging the strands still hold her
        if (state == State.FALL) fall(tpf);
    }

    @Override
    protected void interrupt() {
        hideSilk();
        switch (state) {
            case RAPPEL_TELL, CAST -> state = State.HANG;
            case DROP, BITE, HOLD, REEL -> state = State.CLIMB_BACK;
            default -> { }
        }
        cooldown = Math.max(cooldown, 0.8);
    }

    private void updatePhase() {
        HealthDoubleComponent hp = entity.getComponent(HealthDoubleComponent.class);
        double f = hp.getValue() / hp.getMaxValue();
        phase = f <= 1 / 3.0 ? 3 : f <= 2 / 3.0 ? 2 : 1;
    }

    private boolean dark() {
        return phase == 3;
    }

    //-----entrance-----
    private void intro(double tpf) {
        sprite.loop("idle", HANG_FPS);
        Point2D at = entity.getCenter();
        if (moveTo(new Point2D(hx, hy), INTRO_SPEED, tpf)) {
            introDone = true;
            startScreech();
            BossArena.landed(entity);
        }
        if (at.getY() < 0 && Math.random() < tpf * 6) Sfx.THREAD_TWANG.at(at, 0.4, 0.7);
    }

    //-----hanging and choosing-----
    private void hang(double t) {
        sprite.loop("idle", HANG_FPS);
        hideSilk();
        if (anchors.isEmpty()) {
            startFall();
            return;
        }
        if (BossArena.inCutscene()) return;          // the intro screech is over but the player can't move yet
        if (phase > shownPhase) {
            startScreech();
            return;
        }
        cooldown -= t;
        if (cooldown > 0) return;
        if (phase == 3 && attacksHere >= 2) {
            startLeave();
            return;
        }
        Attack a = choose();
        lastAttack = a;
        attacksHere++;
        switch (a) {
            case RAPPEL -> startRappel(dark() ? RAPPEL_TELL_DARK : RAPPEL_TELL);
            case REEL -> startReel();
            default -> startCast(a);
        }
    }

    private Attack choose() {
        boolean onBridge = MatriarchArena.bridgeUnder(player) != null;
        double[] w = new double[Attack.values().length];
        w[Attack.RAPPEL.ordinal()] = dark() ? 4.5 : 3;
        if (mites() + getGameWorld().getEntitiesByComponent(EggBehaviour.class).size() < 4) w[Attack.BROOD.ordinal()] = 1.5;
        if (MatriarchArena.missingBridges() > 0 || getGameWorld().getEntitiesByComponent(SilkHazardComponent.class).size() < 2) {
            w[Attack.WEAVE.ordinal()] = MatriarchArena.missingBridges() > 1 ? 3 : 1.5;
        }
        if (phase >= 2) {
            if (onBridge && MatriarchArena.solidBridges().size() > 1) w[Attack.SEVER.ordinal()] = 1.6;
            w[Attack.REEL.ordinal()] = 1.4;
            w[Attack.CURTAIN.ordinal()] = 1.4;
        }
        if (lastAttack != null && lastAttack != Attack.RAPPEL) w[lastAttack.ordinal()] = 0;
        double total = 0;
        for (double v : w) total += v;
        double roll = Math.random() * total;
        for (Attack a : Attack.values()) {
            roll -= w[a.ordinal()];
            if (roll <= 0 && w[a.ordinal()] > 0) return a;
        }
        return Attack.RAPPEL;
    }

    private void rest() {
        state = State.HANG;
        cooldown = FXGLMath.random(1.6, 2.4) * (dark() ? 0.75 : 1);
    }

    //-----rappel strike-----
    private void startRappel(double tell) {
        state = State.RAPPEL_TELL;
        timer = tell;
        Point2D feet = new Point2D(player.getCenter().getX(), player.getBottomY());
        double x = Math.max(MatriarchArena.lockX() + 120, Math.min(MatriarchArena.lockX() + BossArena.WIDTH - 120, feet.getX()));
        target = new Point2D(x, Math.min(feet.getY(), MatriarchArena.groundY()) - HALF_H);
        sprite.setTelegraph(TELL);
        Sfx.THREAD_TWANG.at(entity.getCenter(), 0.9, 0.6);
    }

    private void rappelTell(double t) {
        sprite.loop("idle", HANG_FPS);
        double k = 0.35 + 0.35 * Math.sin(timer * 40);
        drawSilk(top(), target, k);
        if ((timer -= t) <= 0) {
            sprite.setTelegraph(null);
            state = State.DROP;
            Sfx.HOUND_LUNGE.at(entity.getCenter(), 1, 0.5);
        }
    }

    private void drop(double tpf) {
        sprite.loop("dive", DIVE_FPS);
        drawSilk(dragAnchor(), top());
        if (moveTo(target, DROP_SPEED, tpf)) {
            state = State.BITE;
            timer = 0;
            bitten = false;
            sprite.once("bite", BITE_FPS, null);
            Impact.shake(10);
            Particles.burst(new Point2D(target.getX(), target.getY() + HALF_H), Particles.VOID, 14, -90, 70, 80, 280, 0.6, 5, 500);
        }
    }

    private void bite(double t) {
        timer += t;
        drawSilk(dragAnchor(), top());
        if (!bitten && timer >= BITE_AT) {
            bitten = true;
            Sfx.MIMIC_CHOMP.at(entity.getCenter(), 1, 0.6);
            if (Math.abs(dxToPlayer()) < BITE_REACH_X && Math.abs(dyToPlayer()) < BITE_REACH_Y) hurtPlayer(BITE_DAMAGE);
        }
        if (timer >= 8 / BITE_FPS) {
            state = State.HOLD;
            timer = dark() ? HOLD_DARK : HOLD;
        }
    }

    private void climbBack(double tpf) {
        sprite.loop("climb", CLIMB_FPS);
        drawSilk(dragAnchor(), top());
        if (moveTo(new Point2D(hx, hy), CLIMB_SPEED, tpf)) {
            hideSilk();
            if (anchors.isEmpty()) startFall();
            else rest();
        }
    }

    /** Where her drag line is fastened: up where she hangs. */
    private Point2D dragAnchor() {
        return new Point2D(hx, hy - SPINNERET);
    }

    private Point2D top() {
        return entity.getCenter().subtract(0, SPINNERET);
    }

    //-----weave, brood, sever, curtain: she plucks her web-----
    private void startCast(Attack a) {
        if (a == Attack.SEVER && MatriarchArena.bridgeUnder(player) == null) {
            startRappel(RAPPEL_TELL_DARK);
            return;
        }
        state = State.CAST;
        casting = a;
        timer = 0;
        castDone = false;
        sprite.once("weave", CAST_FPS, null);
        sprite.setTelegraph(TELL);
        Sfx.SILK_SPIN.at(entity.getCenter(), 0.9, a == Attack.BROOD ? 0.7 : 1.0);
    }

    private void cast(double t) {
        timer += t;
        if (!castDone && timer >= CAST_AT) {
            castDone = true;
            sprite.setTelegraph(null);
            switch (casting) {
                case WEAVE -> weave();
                case BROOD -> brood();
                case SEVER -> {
                    ThreadBridgeComponent b = MatriarchArena.bridgeUnder(player);
                    if (b != null) b.sever();
                }
                case CURTAIN -> curtain();
                default -> { }
            }
        }
        if (timer >= CAST_END) rest();
    }

    private void weave() {
        MatriarchArena.weaveOne(player.getCenter().getX(), 0.8);
        // a tripwire on the player's own footing, a little way off, and one somewhere else
        List<double[]> surfaces = MatriarchArena.surfaces();
        double[] mine = null;
        for (double[] s : surfaces) {
            if (player.getRightX() > s[0] && player.getX() < s[1] && Math.abs(player.getBottomY() - s[2]) < 30) mine = s;
        }
        if (mine != null) tripwireOn(mine, player.getCenter().getX() + (Math.random() < 0.5 ? -1 : 1) * FXGLMath.random(120, 220));
        double[] other = surfaces.get(FXGLMath.random(0, surfaces.size() - 1));
        if (other != mine) tripwireOn(other, FXGLMath.random(other[0], other[1]));
    }

    private void tripwireOn(double[] s, double x) {
        if (s[1] - s[0] < TRIP_W + 20) return;
        double x1 = Math.max(s[0] + 10, Math.min(s[1] - 10 - TRIP_W, x - TRIP_W / 2));
        SilkHazardComponent.tripwire(x1, x1 + TRIP_W, s[2], TRIP_H, 0.7);
    }

    private void brood() {
        int n = dark() ? 3 : 2;
        Point2D from = entity.getCenter().subtract(0, 30);
        for (int i = 0; i < n; i++) {
            DemonType egg = DemonType.EGG;
            Entity e = spawn(egg.spawnName(), new SpawnData(from.getX() - egg.width / 2, from.getY()));
            double vx = (i - (n - 1) / 2.0) * 220 + FXGLMath.random(-60, 60);
            PhysicsComponent p = e.getComponent(PhysicsComponent.class);
            Runnable push = () -> {
                p.setVelocityX(vx);
                p.setVelocityY(-260);
            };
            try {
                p.getBody();
                push.run();
            } catch (IllegalStateException notYet) {
                p.setOnPhysicsInitialized(push);
            }
        }
        Sfx.EGG_HATCH.at(from, 0.5, 0.6);
        Particles.burst(from, Particles.VOID, 12, -90, 100, 60, 200, 0.6, 5, 300);
    }

    private void curtain() {
        double left = MatriarchArena.lockX() + 90, right = MatriarchArena.lockX() + BossArena.WIDTH - 90;
        double gap = Math.max(left + CURTAIN_GAP / 2, Math.min(right - CURTAIN_GAP / 2,
                player.getCenter().getX() + FXGLMath.random(-350, 350)));
        double tell = dark() ? 0.7 : 0.85;
        for (double x = left + FXGLMath.random(0, 60); x <= right; x += CURTAIN_STEP) {
            if (Math.abs(x - gap) < CURTAIN_GAP / 2) continue;
            SilkHazardComponent.curtain(x, -60, MatriarchArena.groundY() + 300, tell);
        }
    }

    //-----reel in-----
    private void startReel() {
        state = State.REEL;
        timer = 0;
        reelStartHp = entity.getComponent(HealthDoubleComponent.class).getValue();
        sprite.once("weave", CAST_FPS, null);
        Sfx.SILK_SPIN.at(entity.getCenter(), 1, 1.3);
    }

    private void reel(double tpf, double t) {
        timer += t;
        Point2D from = entity.getCenter().add(0, 30);
        Point2D to = player.getCenter();
        if (timer < REEL_SHOT) {                       // the line shoots out
            drawSilk(from, from.add(to.subtract(from).multiply(timer / REEL_SHOT)));
            return;
        }
        drawSilk(from, to);
        PlayerMovementComponent movement = player.getComponent(PlayerMovementComponent.class);
        double hp = entity.getComponent(HealthDoubleComponent.class).getValue();
        if (movement.isDodging() || reelStartHp - hp >= REEL_BREAK_DAMAGE) {     // broken free
            Sfx.THREAD_SNAP.at(to);
            Particles.burst(to, Particles.VOID, 14, -90, 180, 60, 240, 0.5, 4, 300);
            hideSilk();
            rest();
            cooldown = 1.0;
            return;
        }
        if (Math.random() < tpf * 5) Sfx.THREAD_TWANG.at(to, 0.4, 1.5);
        double dx = hx - to.getX();
        double step = Math.signum(dx) * Math.min(Math.abs(dx), REEL_SPEED * tpf);
        // standing, the player is dragged to the edge of their footing, not over it
        if (movement.isGrounded()) {
            for (double[] s : MatriarchArena.surfaces()) {
                if (player.getRightX() > s[0] && player.getX() < s[1] && Math.abs(player.getBottomY() - s[2]) < 8) {
                    double room = step > 0 ? s[1] - player.getRightX() : player.getX() - s[0];
                    step = Math.signum(step) * Math.max(0, Math.min(Math.abs(step), room));
                }
            }
        }
        if (Math.abs(dx) > 10 && step != 0) movement.pull(step);
        if (timer >= REEL_SHOT + REEL_PULL) {
            hideSilk();
            startRappel(0.35);
        }
    }

    //-----cut down-----
    /** One of her strands was cut. */
    void anchorCut(Entity strand) {
        anchors.remove(strand);
        if (dying) return;
        Sfx.THREAD_TWANG.at(entity.getCenter(), 0.8, 0.5);
        if (anchors.isEmpty() && (state == State.HANG || state == State.CAST || state == State.RAPPEL_TELL
                || state == State.REEL || state == State.SCREECH)) {
            startFall();
        }
    }

    private void startFall() {
        hideSilk();
        sprite.setTelegraph(null);
        state = State.FALL;
        vy = 0;
        landing = MatriarchArena.landing(entity.getCenter().getX(), entity.getBottomY(), entity.getWidth() / 2);
        Sfx.MATRIARCH_SCREECH.at(entity.getCenter(), 0.8, 1.5);
    }

    private void fall(double tpf) {
        sprite.loop("fall", FALL_FPS);
        vy += GRAVITY * tpf;
        Point2D c = entity.getCenter();
        double x = c.getX();
        if (landing != null) x += Math.signum(landing[0] - x) * Math.min(Math.abs(landing[0] - x), 500 * tpf);
        double y = c.getY() + vy * tpf;
        if (landing != null && y + HALF_H >= landing[1]) {
            place(new Point2D(x, landing[1] - HALF_H));
            state = State.DOWNED;
            timer = dark() ? DOWNED_DARK : DOWNED;
            Impact.shake(22);
            Sfx.BRUTE_SLAM.at(entity.getCenter(), 1, 0.7);
            Point2D feet = new Point2D(x, landing[1]);
            Particles.burst(feet, Particles.DUST, 30, -90, 85, 120, 420, 0.8, 8, 900);
            Particles.burst(feet, Particles.VOID, 20, -90, 80, 100, 360, 0.8, 5, 400);
            return;
        }
        place(new Point2D(x, y));
        if (landing == null && y > MatriarchArena.groundY() + 200) {   // nothing to land on (never happens): back up
            startLeave();
        }
    }

    /** Up and out of sight: the old strands are pulled up after her. */
    private void startLeave() {
        state = State.LEAVE;
        hideSilk();
        sprite.setTelegraph(null);
        Sfx.SILK_SPIN.at(entity.getCenter(), 1, 0.8);
        Sfx.MATRIARCH_SCREECH.at(entity.getCenter(), 0.5, 1.6);
    }

    private void leave(double tpf) {
        sprite.loop("climb", CLIMB_FPS);
        Point2D c = entity.getCenter();
        place(new Point2D(c.getX(), c.getY() - LEAVE_SPEED * tpf));
        if (entity.getBottomY() < -40) {
            for (Entity a : new ArrayList<>(anchors)) {
                ThreadBehaviour tb = (ThreadBehaviour) DemonBehaviour.of(a);
                if (tb != null) tb.release();
            }
            anchors.clear();
            double old = hx;
            do {
                hx = MatriarchArena.lockX() + SPOTS[FXGLMath.random(0, SPOTS.length - 1)];
            } while (Math.abs(hx - old) < 1);
            place(new Point2D(hx, -HALF_H - 60));
            spinAnchors();
            state = State.ARRIVE;
            attacksHere = 0;
        }
    }

    private void arrive(double tpf) {
        sprite.loop("idle", HANG_FPS);
        if (moveTo(new Point2D(hx, hy), ARRIVE_SPEED, tpf)) {
            rest();
            cooldown = 0.9;
        }
    }

    /** Three fresh strands from the ceiling to just below where she hangs. */
    private void spinAnchors() {
        DemonType t = DemonType.THREAD;
        for (double dx : STRAND_DX) {
            double knotY = hy + KNOT_BELOW;
            Entity a = spawn(t.spawnName(), new SpawnData(hx + dx - t.width / 2, knotY - t.height));
            ThreadBehaviour tb = (ThreadBehaviour) DemonBehaviour.of(a);
            if (tb != null) tb.setOwner(this);
            anchors.add(a);
            if (Darkness.isOn()) Darkness.light(() -> a.isActive() ? new Point2D(a.getCenter().getX(), a.getBottomY()) : null, 80, EYE_LIGHT);
        }
        Sfx.THREAD_TWANG.at(new Point2D(hx, Math.max(0, hy - 200)), 0.8, 0.9);
    }

    //-----screech (intro, phase changes)-----
    private void startScreech() {
        state = State.SCREECH;
        timer = SCREECH;
        shownPhase = Math.max(shownPhase, phase);
        sprite.loop("screech", SCREECH_FPS);
        Sfx.MATRIARCH_SCREECH.at(entity.getCenter());
        Impact.shake(16);
        Particles.burst(entity.getCenter().add(0, 40), Particles.VOID, 30, 90, 80, 120, 420, 0.8, 5, 200);
        if (introDone && phase == 3 && !Darkness.isOn()) lightsOut();
    }

    private void screech(double t) {
        sprite.loop("screech", SCREECH_FPS);
        if (Math.random() < t * 8) Impact.shake(4);
        if ((timer -= t) <= 0) rest();
    }

    /** Phase 3: the arena goes dark; her eyes, her silk and the mites keep a faint light. */
    private void lightsOut() {
        Darkness.setOn(true);
        Sfx.LIGHTS_OUT.play();
        Darkness.light(() -> entity.isActive() ? entity.getCenter().add(0, 30) : null, 170, EYE_LIGHT);
        MatriarchArena.lightBridges();
        for (Entity a : anchors) {
            Darkness.light(() -> a.isActive() ? new Point2D(a.getCenter().getX(), a.getBottomY()) : null, 80, EYE_LIGHT);
        }
        for (Entity m : getGameWorld().getEntitiesByComponent(MiteBehaviour.class)) Darkness.light(m, 60, EYE_LIGHT);
    }

    //-----damage-----
    @Override
    public double guard(Hit h, Entity p) {
        return switch (state) {
            case DOWNED, RISE -> DOWNED_GUARD;
            case INTRO, HANG, CAST, RAPPEL_TELL, REEL, SCREECH, ARRIVE, LEAVE -> HANGING_GUARD;
            default -> 1;
        };
    }

    //-----death-----
    @Override
    public boolean diesSlowly() {
        return true;
    }

    @Override
    public void onDeath() {
        if (current == entity) current = null;
        dying = true;
        deathT = 0;
        deathCue = 0;
        deathVy = 0;
        hideSilk();
        sprite.setTelegraph(null);
        Darkness.setOn(false);
        Sfx.MATRIARCH_SCREECH.at(entity.getCenter(), 1, 0.8);
        Impact.shake(18);
        BossArena.bossDying(entity);
    }

    @Override
    protected void dying(double tpf) {
        deathT += tpf;
        Point2D c = entity.getCenter();
        if (deathCue == 0 && deathT >= D_SNAP) {           // her strands snap one after another
            deathCue++;
            for (int i = 0; i < anchors.size(); i++) {
                Entity a = anchors.get(i);
                runOnce(() -> {
                    if (!a.isActive()) return;
                    Sfx.THREAD_SNAP.at(a.getCenter());
                    Particles.burst(new Point2D(a.getCenter().getX(), a.getBottomY()), Particles.VOID, 10, 90, 60, 40, 200, 0.7, 4, 400);
                    a.removeFromWorld();
                }, Duration.seconds(i * 0.12));
            }
            anchors.clear();
        }
        if (deathT >= D_SNAP && !deathLanded) {            // and she falls, curling up
            deathVy += GRAVITY * tpf;
            double y = c.getY() + deathVy * tpf;
            double[] land = MatriarchArena.landing(c.getX(), entity.getBottomY(), 0);
            if (land != null && Math.abs(land[0] - c.getX()) < 1 && y + HALF_H >= land[1]) {
                y = land[1] - HALF_H;
                deathLanded = true;
                Impact.shake(18);
                Sfx.BRUTE_SLAM.at(c, 1, 0.6);
                Particles.burst(new Point2D(c.getX(), land[1]), Particles.DUST, 26, -90, 85, 120, 400, 0.8, 8, 900);
            }
            place(new Point2D(c.getX(), y));
        }
        int frame = (int) Math.min(11, Math.max(0, (deathT - D_SNAP) / (D_CURL_END - D_SNAP) * 11));
        sprite.show("death", frame);
        if (deathT >= D_SNAP && Math.random() < tpf * 30) {
            Particles.burst(c.add(FXGLMath.random(-60, 60), FXGLMath.random(-80, 40)), Particles.VOID, 2, -90, 50, 40, 160, 0.7, 5, -80);
        }
        if (deathT >= D_FLARE && Math.random() < tpf * 16) sprite.flash();
        if (deathT >= D_BURST || c.getY() > MatriarchArena.groundY() + 160) burst();
    }

    /** The void core implodes: she's gone, the floor comes back, the arena opens. */
    private void burst() {
        Point2D c = entity.getCenter();
        Sfx.EXPLOSION.at(c, 1, 0.5);
        Sfx.VOID_CAST.at(c, 1, 1.6);
        Impact.shake(30);
        Particles.burst(c, Particles.VOID, 90, -90, 180, 150, 900, 1.4, 8, 200);
        Particles.burst(c, Particles.SPARK, 30, -90, 180, 200, 700, 0.8, 5, 200);
        PlayerStatsComponent stats = player.getComponent(PlayerStatsComponent.class);
        for (int i = 0; i < REWARD_SP; i++) stats.addSkillPoint();
        runOnce(Sfx.LEVEL_UP::play, Duration.seconds(0.9));
        BossArena.bossExploded();
        entity.removeFromWorld();
    }

    @Override
    public void onRemoved() {
        if (!dying) BossArena.end();
        if (current == entity) current = null;
        if (silk != null && silk.isActive()) silk.removeFromWorld();
        for (Entity a : anchors) if (a.isActive()) a.removeFromWorld();
        anchors.clear();
        Darkness.setOn(false);
    }

    //-----helpers-----
    /** Moves her centre toward p at speed. @return true once there */
    private boolean moveTo(Point2D p, double speed, double tpf) {
        Point2D c = entity.getCenter();
        Point2D d = p.subtract(c);
        double step = speed * tpf;
        if (d.magnitude() <= step) {
            place(p);
            return true;
        }
        place(c.add(d.normalize().multiply(step)));
        return false;
    }

    private void place(Point2D centre) {
        physics.setVelocityX(0);
        physics.setVelocityY(0);
        physics.overwritePosition(centre.subtract(entity.getWidth() / 2, entity.getHeight() / 2));
    }

    private void drawSilk(Point2D a, Point2D b) {
        drawSilk(a, b, 1);
    }

    private void drawSilk(Point2D a, Point2D b, double opacity) {
        silk.getViewComponent().setVisible(true);
        silk.getViewComponent().setOpacity(opacity);
        double ox = silk.getX();
        for (Line l : new Line[]{silkGlow, silkCore}) {
            l.setStartX(a.getX() - ox);
            l.setStartY(a.getY());
            l.setEndX(b.getX() - ox);
            l.setEndY(b.getY());
        }
    }

    private void hideSilk() {
        if (silk != null) silk.getViewComponent().setVisible(false);
    }
}
