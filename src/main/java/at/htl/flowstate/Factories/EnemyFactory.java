package at.htl.flowstate.Factories;

import at.htl.flowstate.Components.Enemies.BatBehaviour;
import at.htl.flowstate.Components.Enemies.BruteBehaviour;
import at.htl.flowstate.Components.Enemies.CultistBehaviour;
import at.htl.flowstate.Components.Enemies.DemonBehaviour;
import at.htl.flowstate.Components.Enemies.DemonType;
import at.htl.flowstate.Components.Enemies.EnemyStatsComponent;
import at.htl.flowstate.Components.Enemies.HoundBehaviour;
import at.htl.flowstate.Components.Enemies.HurlerBehaviour;
import at.htl.flowstate.Components.Enemies.ImpBehaviour;
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

    private Entity demon(SpawnData data, DemonType type, DemonBehaviour behaviour) {
        return FXGL.entityBuilder(data)
                .bbox(new HitBox(BoundingShape.box(type.width, type.height)))
                .with(physics(type.flying))
                .with(behaviour)
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
