package de.lamamc.sift.core.mob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

/**
 * The shipped Blub rig in rest and in its animations: upright, no roll, pitch only where the animation sets
 * it. Angles are measured on the up vector of every cube as an item display draws it.
 */
class BlubPoseTest {

    private static final Path MOB_DATA = Path.of(System.getProperty("sift.packSrc", "../pack-src"), "mob-data");

    private static Rig rig() throws IOException {
        return Rig.parse(read("blub.rig.json"));
    }

    private static Map<String, Animation> animations() throws IOException {
        return Animation.parseAll(read("blub.animation.json"));
    }

    private static JsonObject read(String name) throws IOException {
        return JsonParser.parseString(Files.readString(MOB_DATA.resolve(name))).getAsJsonObject();
    }

    private static Matrix4f[] matrices(Rig rig, Pose pose, Matrix4f root) {
        return RigPose.cubeMatrices(rig, pose, root, new Matrix4f[rig.cubes().size()]);
    }

    /** Up vector of a cube as drawn: the display turns the item by π about y first (does not change up). */
    private static Vector3f up(Matrix4f display) {
        return new Matrix4f(display).rotateY((float) Math.PI).transformDirection(new Vector3f(0, 1, 0)).normalize();
    }

    private static double roll(Vector3f up) {
        return Math.toDegrees(Math.atan2(Math.abs(up.x), up.y));
    }

    private static double pitch(Vector3f up) {
        return Math.toDegrees(Math.atan2(Math.abs(up.z), up.y));
    }

    /** Where the display draws a corner of the cube's item model (pixels of the model). */
    private static Vector3f corner(Matrix4f display, float mx, float my, float mz) {
        Vector3f q = new Vector3f(mx / 16f - 0.5f, my / 16f - 0.5f, mz / 16f - 0.5f);
        new Matrix4f().rotateY((float) Math.PI).transformPosition(q);
        return display.transformPosition(q);
    }

    @Test
    void restPoseIsUpright() throws IOException {
        Rig rig = rig();
        Matrix4f[] m = matrices(rig, new Pose(rig.bones().size()), new Matrix4f());
        float minY = Float.MAX_VALUE;
        float minX = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        for (int i = 0; i < m.length; i++) {
            Vector3f up = up(m[i]);
            assertTrue(roll(up) < 0.1, "cube " + i + " rolled by " + roll(up));
            // the geo tilts the head by 2.5° and the ears by 12.5°; nothing else is turned at rest
            assertTrue(pitch(up) < 13, "cube " + i + " pitched by " + pitch(up));
            Rig.Cube cube = rig.cubes().get(i);
            Vector3f size = new Vector3f(cube.size()).mul(cube.modelScale());
            for (int c = 0; c < 8; c++) {
                Vector3f p = corner(m[i], (c & 1) * size.x, (c >> 1 & 1) * size.y, (c >> 2 & 1) * size.z);
                minY = Math.min(minY, p.y);
                minX = Math.min(minX, p.x);
                maxX = Math.max(maxX, p.x);
            }
        }
        Vector3f body = up(m[rig.bone("head")]); // the Blub's body is its head bone (one cube)
        assertTrue(body.y > Math.cos(Math.toRadians(3)), "body normal " + body);
        assertEquals(0, minY, 0.02, "feet on the ground");
        assertEquals(-minX, maxX, 0.02, "left and right symmetric");
    }

    @Test
    void bodyYawOnlyTurnsAboutUp() throws IOException {
        Rig rig = rig();
        for (float yaw = -180; yaw <= 180; yaw += 45) {
            Matrix4f root = RigPose.root(0.1f, -0.9f, -0.2f, yaw, 1, new Matrix4f());
            Matrix4f[] m = matrices(rig, new Pose(rig.bones().size()), root);
            Vector3f body = up(m[rig.bone("head")]);
            assertTrue(body.y > Math.cos(Math.toRadians(3)), "yaw " + yaw + ": body normal " + body);
            for (Matrix4f matrix : m) {
                assertTrue(up(matrix).y > Math.cos(Math.toRadians(13)), "yaw " + yaw);
            }
        }
    }

    /** Samples an animation over its length; returns the largest roll and pitch of any cube. */
    private static double[] extremes(Rig rig, AnimationController controller, double length, boolean sitting) {
        double maxRoll = 0;
        double maxPitch = 0;
        Pose pose = new Pose(rig.bones().size());
        for (int step = 0; step <= 40; step++) {
            controller.pose(pose);
            if (sitting) {
                BlubRules.sittingPose(rig, pose);
            }
            for (Matrix4f matrix : matrices(rig, pose, new Matrix4f())) {
                maxRoll = Math.max(maxRoll, roll(up(matrix)));
                maxPitch = Math.max(maxPitch, pitch(up(matrix)));
            }
            controller.tick(length / 40);
        }
        return new double[] {maxRoll, maxPitch};
    }

    @Test
    void hoppingOnlyPitches() throws IOException {
        Rig rig = rig();
        AnimationController controller = new AnimationController(rig, animations(), "body", "ears");
        controller.play("body", "hopping", 1.0);
        double[] e = extremes(rig, controller, animations().get("hopping").length(), false);
        assertTrue(e[0] < 0.5, "roll " + e[0]);
        // legs swing up to 35°, ears 25°, head 7.5° (+ rest tilt)
        assertTrue(e[1] < 50, "pitch " + e[1]);
    }

    @Test
    void flappingOnlyMovesTheEars() throws IOException {
        Rig rig = rig();
        AnimationController controller = new AnimationController(rig, animations(), "body", "ears");
        controller.play("ears", "flapping", 1.0);
        double[] e = extremes(rig, controller, animations().get("flapping").length(), false);
        assertTrue(e[0] < 0.5, "roll " + e[0]);
        assertTrue(e[1] < 13 + 31, "pitch " + e[1]);
    }

    @Test
    void sittingStaysUpright() throws IOException {
        Rig rig = rig();
        AnimationController controller = new AnimationController(rig, animations(), "body", "ears");
        controller.play("body", "sitting", 1.0);
        double[] e = extremes(rig, controller, animations().get("sitting").length(), true);
        assertTrue(e[0] < 0.5, "roll " + e[0]);
        // back legs fold by 68°, front legs 42°, the body tips forward by 17.5° (+ the sitting animation)
        assertTrue(e[1] < 85, "pitch " + e[1]);
        Pose pose = controller.pose(new Pose(rig.bones().size()));
        BlubRules.sittingPose(rig, pose);
        Vector3f body = up(matrices(rig, pose, new Matrix4f())[rig.bone("head")]);
        assertTrue(body.y > Math.cos(Math.toRadians(35)), "sitting body normal " + body);
    }
}
