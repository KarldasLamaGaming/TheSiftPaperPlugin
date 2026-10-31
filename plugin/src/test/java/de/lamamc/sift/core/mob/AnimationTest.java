package de.lamamc.sift.core.mob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import java.util.Map;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class AnimationTest {

    private static final String FILE = """
            {"format_version": "1.8.0", "animations": {
              "walk": {"loop": true, "animation_length": 1.0, "bones": {
                "body": {"rotation": {"0.0": {"vector": [0, 0, 0]}, "0.5": {"vector": [10, 20, 30]},
                                      "1.0": {"vector": [0, 0, 0], "easing": "easeInQuad"}},
                         "position": {"0.0": [0, 0, 0], "0.5": [4, 2, 0]}}}},
              "sit": {"loop": "hold_on_last_frame", "animation_length": 0.25, "bones": {
                "body": {"rotation": {"vector": [-10, 0, 0]}, "scale": 0.5}}},
              "wave": {"animation_length": 0.5, "bones": {
                "arm": {"rotation": {"0.0": {"vector": [0, 0, 0]}, "0.5": {"vector": [0, 0, 90]}}}}},
              "look": {"loop": true, "bones": {"body": {"rotation": [0, 45, 0]}}}
            }}
            """;

    private static final String RIG = """
            {"texture_width": 16, "texture_height": 16,
             "bones": [{"name": "body", "pivot": [0, 0, 0]}, {"name": "arm", "parent": "body", "pivot": [0, 4, 0]}],
             "cubes": []}
            """;

    private static Map<String, Animation> animations() {
        return Animation.parseAll(JsonParser.parseString(FILE).getAsJsonObject());
    }

    private static Rig rig() {
        return Rig.parse(JsonParser.parseString(RIG).getAsJsonObject());
    }

    @Test
    void easingsStartAtZeroAndEndAtOne() {
        for (Easing easing : Easing.values()) {
            assertEquals(0, easing.apply(0), 1e-9, easing.name());
            assertEquals(1, easing.apply(1), 1e-9, easing.name());
        }
        assertEquals(Easing.EASE_IN_OUT_CUBIC, Easing.byName("easeInOutCubic"));
        assertEquals(Easing.LINEAR, Easing.byName("bounce?"));
        assertEquals(0.25, Easing.EASE_IN_QUAD.apply(0.5), 1e-9);
    }

    @Test
    void loopModesAndLength() {
        var all = animations();
        assertEquals(Animation.Loop.LOOP, all.get("walk").loop());
        assertEquals(Animation.Loop.HOLD, all.get("sit").loop());
        assertEquals(Animation.Loop.ONCE, all.get("wave").loop());
        assertEquals(0.0, all.get("look").length());
        assertEquals(0.25, all.get("walk").localTime(1.25), 1e-9);
        assertEquals(0.25, all.get("sit").localTime(3), 1e-9);
        assertTrue(all.get("wave").finished(0.5));
        assertFalse(all.get("sit").finished(10));
    }

    @Test
    void keyframesAreInterpolatedInJavaSpace() {
        Animation walk = animations().get("walk");
        Animation.Track rotation = walk.bones().get("body").rotation();
        // linear halfway to (10, 20, 30), x and y negated like GeckoLib
        assertVector(-5, -10, 15, rotation.sample(0.25, new Vector3f()));
        // segment to the last keyframe eases in: at its middle a quarter of the way back
        assertVector(-7.5f, -15, 22.5f, rotation.sample(0.75, new Vector3f()));
        Animation.Track position = walk.bones().get("body").position();
        assertVector(-4, 2, 0, position.sample(0.5, new Vector3f()));
        // after the last keyframe the value holds
        assertVector(-4, 2, 0, position.sample(0.9, new Vector3f()));
    }

    @Test
    void constantValues() {
        Animation sit = animations().get("sit");
        assertVector(10, 0, 0, sit.bones().get("body").rotation().sample(0.1, new Vector3f()));
        assertVector(0.5f, 0.5f, 0.5f, sit.bones().get("body").scale().sample(0, new Vector3f()));
        assertNull(sit.bones().get("body").position());
    }

    @Test
    void triggeredAnimationPlaysOnceOverTheBase() {
        Rig rig = rig();
        AnimationController controller = new AnimationController(rig, animations(), "body", "arm");
        controller.play("body", "walk", 1.0);
        controller.trigger("body", "wave");
        assertEquals("wave", controller.current("body"));
        controller.tick(0.25);
        Pose pose = controller.pose(new Pose(2));
        assertVector(0, 0, 45, pose.get(Animation.Channel.ROTATION, rig.bone("arm"), new Vector3f()));
        controller.tick(0.3);
        assertEquals("walk", controller.current("body"));
        assertFalse(controller.isTriggered("body"));
    }

    @Test
    void laterLayersOverrideAndSpeedScalesTime() {
        Rig rig = rig();
        AnimationController controller = new AnimationController(rig, animations(), "body", "head");
        controller.play("body", "walk", 2.0);
        controller.play("head", "look", 1.0);
        controller.tick(0.125);
        Pose pose = controller.pose(new Pose(2));
        // look (later layer) wins for the rotation of body; walk's position stays (at 0.25 s)
        assertVector(0, -45, 0, pose.get(Animation.Channel.ROTATION, 0, new Vector3f()));
        assertVector(-2, 1, 0, pose.get(Animation.Channel.POSITION, 0, new Vector3f()));
        controller.stop("head");
        controller.stop("body");
        assertFalse(controller.animating());
        assertEquals(new Pose(2), controller.pose(new Pose(2)));
    }

    private static void assertVector(float x, float y, float z, Vector3f actual) {
        assertEquals(x, actual.x, 1e-4, "x of " + actual);
        assertEquals(y, actual.y, 1e-4, "y of " + actual);
        assertEquals(z, actual.z, 1e-4, "z of " + actual);
    }
}
