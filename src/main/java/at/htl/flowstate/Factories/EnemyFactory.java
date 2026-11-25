package at.htl.flowstate.Factories;

import at.htl.flowstate.Components.Enemies.*;
import at.htl.flowstate.Components.Enemies.BatBehaviour;
import at.htl.flowstate.Components.Enemies.Bosses.Hollow.HollowBehaviour;
import at.htl.flowstate.Components.Enemies.Bosses.Hydra.HydraBehaviour;
import at.htl.flowstate.Components.Enemies.Bosses.Hydra.HydraHeadBehaviour;
import at.htl.flowstate.Components.Enemies.Bosses.Hydra.HydraStumpBehaviour;
import at.htl.flowstate.Components.Enemies.Bosses.Matriarch.EggBehaviour;
import at.htl.flowstate.Components.Enemies.Bosses.Matriarch.MatriarchBehaviour;
import at.htl.flowstate.Components.Enemies.Bosses.Matriarch.MiteBehaviour;
import at.htl.flowstate.Components.Enemies.Bosses.Matriarch.ThreadBehaviour;
import at.htl.flowstate.Components.Enemies.Bosses.Matriarch.ThreadBridgeComponent;
import at.htl.flowstate.Components.Enemies.Bosses.PitLord.PitLordBehaviour;
import at.htl.flowstate.Components.Enemies.BruteBehaviour;
import at.htl.flowstate.Components.Enemies.CultistBehaviour;
import at.htl.flowstate.Components.Enemies.DemonBehaviour;
import at.htl.flowstate.Components.Enemies.DemonType;
import at.htl.flowstate.Components.Enemies.DrakeBehaviour;
import at.htl.flowstate.Components.Enemies.EliteAffix;
import at.htl.flowstate.Components.Enemies.EliteComponent;
import at.htl.flowstate.Components.Enemies.EnemyStatsComponent;
import at.htl.flowstate.Components.Enemies.GargoyleBehaviour;
import at.htl.flowstate.Components.Enemies.GatekeeperBehaviour;
import at.htl.flowstate.Components.Enemies.HoundBehaviour;
import at.htl.flowstate.Components.Enemies.HurlerBehaviour;
import at.htl.flowstate.Components.Enemies.ImpBehaviour;
import at.htl.flowstate.Components.Enemies.MimicBehaviour;
import at.htl.flowstate.Components.Enemies.PriestBehaviour;
import at.htl.flowstate.Components.Enemies.VoidCallerBehaviour;
import at.htl.flowstate.Components.Enemies.WardenBehaviour;
import at.htl.flowstate.Components.Enemies.WatcherBehaviour;
import at.htl.flowstate.Components.Identifier.EnemyIdentifierComponent;
import at.htl.flowstate.Components.SpriteComponents.DemonSpriteComponent;
import com.almasb.fxgl.dsl.FXGL;
import com.almasb.fxgl.dsl.components.HealthDoubleComponent;
import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.entity.EntityFactory;
import com.almasb.fxgl.entity.SpawnData;
import com.almasb.fxgl.entity.Spawns;
import com.almasb.fxgl.physics.BoundingShape;
import com.almasb.fxgl.physics.HitBox;
import com.almasb.fxgl.physics.PhysicsComponent;
import com.almasb.fxgl.physics.box2d.dynamics.BodyType;
import com.almasb.fxgl.physics.box2d.dynamics.FixtureDef;

/** One spawn per {@link DemonType} (spawn name = the type in lower case). */
public class EnemyFactory implements EntityFactory {

    private static final short CATEGORY_TERRAIN = 0x0001;
    private static final short CATEGORY_ENEMY = 0x0004;

    @Spawns("imp")
    public Entity imp(SpawnData data) {
        return demon(data, DemonType.IMP, new ImpBehaviour());
    }

    @Spawns("hound")
    public Entity hound(SpawnData data) {
        return demon(data, DemonType.HOUND, new HoundBehaviour());
    }

    @Spawns("brute")
    public Entity brute(SpawnData data) {
        return demon(data, DemonType.BRUTE, new BruteBehaviour());
    }

    @Spawns("hurler")
    public Entity hurler(SpawnData data) {
        return demon(data, DemonType.HURLER, new HurlerBehaviour());
    }

    @Spawns("bat")
    public Entity bat(SpawnData data) {
        return demon(data, DemonType.BAT, new BatBehaviour());
    }

    @Spawns("cultist")
    public Entity cultist(SpawnData data) {
        return demon(data, DemonType.CULTIST, new CultistBehaviour());
    }

    @Spawns("warden")
    public Entity warden(SpawnData data) {
        return demon(data, DemonType.WARDEN, new WardenBehaviour());
    }

    @Spawns("gargoyle")
    public Entity gargoyle(SpawnData data) {
        return demon(data, DemonType.GARGOYLE, new GargoyleBehaviour());
    }

    @Spawns("watcher")
    public Entity watcher(SpawnData data) {
        return demon(data, DemonType.WATCHER, new WatcherBehaviour());
    }

    @Spawns("gatekeeper")
    public Entity gatekeeper(SpawnData data) {
        return demon(data, DemonType.GATEKEEPER, new GatekeeperBehaviour());
    }

    @Spawns("priest")
    public Entity priest(SpawnData data) {
        return demon(data, DemonType.PRIEST, new PriestBehaviour());
    }

    @Spawns("drake")
    public Entity drake(SpawnData data) {
        return demon(data, DemonType.DRAKE, new DrakeBehaviour());
    }

    @Spawns("void_caller")
    public Entity voidCaller(SpawnData data) {
        return demon(data, DemonType.VOID_CALLER, new VoidCallerBehaviour());
    }

    /** Stands in for a chest (StructureGeneration). */
    @Spawns("mimic")
    public Entity mimic(SpawnData data) {
        return demon(data, DemonType.MIMIC, new MimicBehaviour());
    }

    @Spawns("lord")
    public Entity lord(SpawnData data) {
        return demon(data, DemonType.LORD, new PitLordBehaviour());
    }

    @Spawns("matriarch")
    public Entity matriarch(SpawnData data) {
        return demon(data, DemonType.MATRIARCH, new MatriarchBehaviour());
    }

    @Spawns("mite")
    public Entity mite(SpawnData data) {
        return demon(data, DemonType.MITE, new MiteBehaviour());
    }

    @Spawns("egg")
    public Entity egg(SpawnData data) {
        return demon(data, DemonType.EGG, new EggBehaviour());
    }

    /** An anchor strand: drawn standing on its knot, but it never falls (kinematic). */
    @Spawns("thread")
    public Entity thread(SpawnData data) {
        return demon(data, DemonType.THREAD, new ThreadBehaviour(), true);
    }

    /** The Tar Hydra's body: sunk in the tar, it spawns its heads and runs the fight. */
    @Spawns("hydra")
    public Entity hydra(SpawnData data) {
        return demon(data, DemonType.HYDRA, new HydraBehaviour());
    }

    @Spawns("hydra_spitter")
    public Entity hydraSpitter(SpawnData data) {
        return demon(data, DemonType.HYDRA_SPITTER, new HydraHeadBehaviour(HydraHeadBehaviour.Kind.SPITTER));
    }

    @Spawns("hydra_biter")
    public Entity hydraBiter(SpawnData data) {
        return demon(data, DemonType.HYDRA_BITER, new HydraHeadBehaviour(HydraHeadBehaviour.Kind.BITER));
    }

    @Spawns("hydra_lure")
    public Entity hydraLure(SpawnData data) {
        return demon(data, DemonType.HYDRA_LURE, new HydraHeadBehaviour(HydraHeadBehaviour.Kind.LURE));
    }

    @Spawns("hydra_crowned")
    public Entity hydraCrowned(SpawnData data) {
        return demon(data, DemonType.HYDRA_CROWNED, new HydraHeadBehaviour(HydraHeadBehaviour.Kind.CROWNED));
    }

    @Spawns("hydra_spitter_stump")
    public Entity hydraSpitterStump(SpawnData data) {
        return demon(data, DemonType.HYDRA_SPITTER_STUMP, new HydraStumpBehaviour(HydraHeadBehaviour.Kind.SPITTER));
    }

    @Spawns("hydra_biter_stump")
    public Entity hydraBiterStump(SpawnData data) {
        return demon(data, DemonType.HYDRA_BITER_STUMP, new HydraStumpBehaviour(HydraHeadBehaviour.Kind.BITER));
    }

    @Spawns("hydra_lure_stump")
    public Entity hydraLureStump(SpawnData data) {
        return demon(data, DemonType.HYDRA_LURE_STUMP, new HydraStumpBehaviour(HydraHeadBehaviour.Kind.LURE));
    }

    /** The Hollow Ember: the player's void double. */
    @Spawns("hollow")
    public Entity hollow(SpawnData data) {
        return demon(data, DemonType.HOLLOW, new HollowBehaviour());
    }

    /** SpawnData "elite" (an EliteAffix) makes it an elite; "hpScale" (a double) scales its HP (weaker early bosses). */
    private Entity demon(SpawnData data, DemonType type, DemonBehaviour behaviour) {
        return demon(data, type, behaviour, type.flying);
    }

    private Entity demon(SpawnData data, DemonType type, DemonBehaviour behaviour, boolean kinematic) {
        var builder = FXGL.entityBuilder(data)
                .bbox(new HitBox(BoundingShape.box(type.width, type.height)))
                .with(physics(kinematic))
                .with(behaviour);
        if (data.hasKey("elite")) builder.with(new EliteComponent(data.<EliteAffix>get("elite")));
        double hp = type.hp * (data.hasKey("hpScale") ? data.<Double>get("hpScale") : 1.0);
        return builder
                .with(new EnemyStatsComponent(type))
                .with(new HealthDoubleComponent(hp))
                .with(new EnemyIdentifierComponent())
                .with(new DemonSpriteComponent(type))
                .collidable()
                .build();
    }

    /** Walkers are dynamic bodies that land on the terrain; flyers are kinematic (no gravity, fly through it). */
    private PhysicsComponent physics(boolean flying) {
        FixtureDef enemyFd = new FixtureDef().friction(0).density(1.0f);
        enemyFd.getFilter().categoryBits = CATEGORY_ENEMY;
        enemyFd.getFilter().maskBits = CATEGORY_TERRAIN | ThreadBridgeComponent.CATEGORY_DEMON_FLOOR;

        PhysicsComponent physics = new PhysicsComponent();
        physics.setBodyType(flying ? BodyType.KINEMATIC : BodyType.DYNAMIC);
        physics.setFixtureDef(enemyFd);
        return physics;
    }
}
