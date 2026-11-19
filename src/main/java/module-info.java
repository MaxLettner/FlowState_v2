module at.htl.flowstate {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.media;

    requires com.almasb.fxgl.all;
    requires java.desktop;
    requires com.fasterxml.jackson.databind;
    requires annotations;
    requires jdk.security.jgss;

    // FXGL reflects into entity factories and components, so every package is opened to it.
    exports at.htl.flowstate;
    opens at.htl.flowstate to com.almasb.fxgl.core;
    exports at.htl.flowstate.Visuals;
    opens at.htl.flowstate.Visuals to com.almasb.fxgl.core;
    exports at.htl.flowstate.Combat;
    opens at.htl.flowstate.Combat to com.almasb.fxgl.core;
    exports at.htl.flowstate.Skills;
    opens at.htl.flowstate.Skills to com.almasb.fxgl.core;
    exports at.htl.flowstate.Factories;
    opens at.htl.flowstate.Factories to com.almasb.fxgl.core;
    exports at.htl.flowstate.Generation;
    opens at.htl.flowstate.Generation to com.almasb.fxgl.core;
    exports at.htl.flowstate.Menu;
    opens at.htl.flowstate.Menu to com.almasb.fxgl.core;
    exports at.htl.flowstate.Menu.SkillTree;
    opens at.htl.flowstate.Menu.SkillTree to com.almasb.fxgl.core;
    exports at.htl.flowstate.Components.Chests;
    opens at.htl.flowstate.Components.Chests to com.almasb.fxgl.core;
    exports at.htl.flowstate.Components.Enemies;
    opens at.htl.flowstate.Components.Enemies to com.almasb.fxgl.core;
    exports at.htl.flowstate.Components.Identifier;
    opens at.htl.flowstate.Components.Identifier to com.almasb.fxgl.core;
    exports at.htl.flowstate.Components.SpriteComponents;
    opens at.htl.flowstate.Components.SpriteComponents to com.almasb.fxgl.core;
    exports at.htl.flowstate.Components.Player;
    opens at.htl.flowstate.Components.Player to com.almasb.fxgl.core;
    exports at.htl.flowstate.Components.Player.Abilities;
    opens at.htl.flowstate.Components.Player.Abilities to com.almasb.fxgl.core;
    exports at.htl.flowstate.Components.Player.Attacks;
    opens at.htl.flowstate.Components.Player.Attacks to com.almasb.fxgl.core;
    exports at.htl.flowstate.Components.Player.Helpers;
    opens at.htl.flowstate.Components.Player.Helpers to com.almasb.fxgl.core;
}
