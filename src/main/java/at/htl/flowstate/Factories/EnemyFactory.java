package at.htl.flowstate.Factories;

import at.htl.flowstate.Components.Enemies.*;
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

    @Spawns("lord")
    public Entity lord(SpawnData data) {
        return demon(data, DemonType.LORD, new PitLordBehaviour());
    }

    /** SpawnData "elite" (an EliteAffix) makes it an elite. */
    private Entity demon(SpawnData data, DemonType type, DemonBehaviour behaviour) {
        var builder = FXGL.entityBuilder(data)
                .bbox(new HitBox(BoundingShape.box(type.width, type.height)))
                .with(physics(type.flying))
                .with(behaviour);
        if (data.hasKey("elite")) builder.with(new EliteComponent(data.<EliteAffix>get("elite")));
        return builder
                .with(new EnemyStatsComponent(type))
                .with(new HealthDoubleComponent(type.hp))
                .with(new EnemyIdentifierComponent())
                .with(new DemonSpriteComponent(type))
                .collidable()
                .build();
    }

    /** Walkers are dynamic bodies that land on the terrain; flyers are kinematic (no gravity, fly through it). */
    private PhysicsComponent physics(boolean flying) {
        FixtureDef enemyFd = new FixtureDef().friction(0).density(1.0f);
        enemyFd.getFilter().categoryBits = CATEGORY_ENEMY;
        enemyFd.getFilter().maskBits = CATEGORY_TERRAIN;

        PhysicsComponent physics = new PhysicsComponent();
        physics.setBodyType(flying ? BodyType.KINEMATIC : BodyType.DYNAMIC);
        physics.setFixtureDef(enemyFd);
        return physics;
    }
}
