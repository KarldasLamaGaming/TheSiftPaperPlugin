package de.lamamc.sift.core.mob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class RigPoseTest {

    private static final float EPS = 1e-4f;

    private static Rig rig(String json) {
        return Rig.parse(JsonParser.parseString(json).getAsJsonObject());
    }

    /** One bone "body" (pivot 0,4,0) with a 4×2×6 cube at (-2,0,-3), a child "head" at pivot (0,6,-3). */
    private static final String RIG = """
            {"texture_width": 64, "texture_height": 64,
             "bones": [{"name": "head", "parent": "body", "pivot": [0, 6, -3], "rotation": [0, 0, 0]},
                       {"name": "body", "pivot": [0, 4, 0], "rotation": [0, 0, 0]}],
             "cubes": [{"bone": "body", "from": [-2, 0, -3], "size": [4, 2, 6], "model_scale": 1.0, "uv": [0, 0],
                        "pivot": [0, 0, 0], "rotation": [0, 0, 0]},
                       {"bone": "head", "from": [-1, 6, -5], "size": [2, 2, 2], "model_scale": 0.5, "uv": [0, 0],
                        "pivot": [0, 0, 0], "rotation": [0, 0, 0]}]}
            """;

    /** Where a display draws a point of the item model (pixels 0..16): matrix · rotY(π) · (m/16 − 0.5). */
    private static Vector3f rendered(Matrix4f display, float mx, float my, float mz) {
        Vector3f q = new Vector3f(mx / 16f - 0.5f, my / 16f - 0.5f, mz / 16f - 0.5f);
        new Matrix4f().rotateY((float) Math.PI).transformPosition(q);
        return display.transformPosition(q);
    }

    private static void assertVector(float x, float y, float z, Vector3f actual) {
        assertEquals(x, actual.x, EPS, "x of " + actual);
        assertEquals(y, actual.y, EPS, "y of " + actual);
        assertEquals(z, actual.z, EPS, "z of " + actual);
    }

    private static Matrix4f[] matrices(Rig rig, Pose pose) {
        return RigPose.cubeMatrices(rig, pose, new Matrix4f(), new Matrix4f[rig.cubes().size()]);
    }

    @Test
    void parentsComeFirst() {
        Rig rig = rig(RIG);
        assertEquals("body", rig.bones().get(0).name());
        assertEquals(0, rig.bones().get(1).parent());
        assertEquals(0, rig.cubes().get(0).bone());
        assertEquals(1, rig.cubes().get(1).bone());
    }

    @Test
    void restPoseMapsModelOntoCube() {
        Rig rig = rig(RIG);
        Matrix4f[] m = matrices(rig, new Pose(2));
        assertVector(-2 / 16f, 0, -3 / 16f, rendered(m[0], 0, 0, 0));
        assertVector(2 / 16f, 2 / 16f, 3 / 16f, rendered(m[0], 4, 2, 6));
        // modelled at half size: the model spans 0..1 pixels per axis
        assertVector(-1 / 16f, 6 / 16f, -5 / 16f, rendered(m[1], 0, 0, 0));
        assertVector(1 / 16f, 8 / 16f, -3 / 16f, rendered(m[1], 1, 1, 1));
    }

    @Test
    void rotationTurnsAboutThePivotAndCarriesChildren() {
        Rig rig = rig(RIG);
        Pose pose = new Pose(2);
        pose.rotate(rig.bone("body"), 0, 90, 0);
        Matrix4f[] m = matrices(rig, pose);
        // body corner (-2, 0, -3): rotY(90°) about pivot x/z = 0 -> (-3, 0, 2)
        assertVector(-3 / 16f, 0, 2 / 16f, rendered(m[0], 0, 0, 0));
        // head corner (-1, 6, -5) follows its parent -> (-5, 6, 1)
        assertVector(-5 / 16f, 6 / 16f, 1 / 16f, rendered(m[1], 0, 0, 0));
    }

    @Test
    void positionAndScale() {
        Rig rig = rig(RIG);
        Pose pose = new Pose(2);
        pose.translate(rig.bone("body"), 0, 3, 0);
        pose.set(Animation.Channel.SCALE, rig.bone("head"), new Vector3f(2, 2, 2));
        Matrix4f[] m = matrices(rig, pose);
        assertVector(-2 / 16f, 3 / 16f, -3 / 16f, rendered(m[0], 0, 0, 0));
        // head scaled ×2 about its pivot (0, 6, -3), then moved up with the body
        assertVector(-2 / 16f, 9 / 16f, -7 / 16f, rendered(m[1], 0, 0, 0));
    }

    @Test
    void rootRotatesLikeGeckoLib() {
        Rig rig = rig(RIG);
        Matrix4f root = RigPose.root(0, 0, 0, 0, 1, new Matrix4f());
        Matrix4f[] m = RigPose.cubeMatrices(rig, new Pose(2), root, new Matrix4f[2]);
        // yaw 0 faces +z in Minecraft; GeckoLib turns the model by 180° - yaw, so model -z ends up at +z
        assertVector(2 / 16f, 0, 3 / 16f, rendered(m[0], 0, 0, 0));
    }

    @Test
    void zeroScaleStaysDecomposable() {
        Rig rig = rig(RIG);
        Pose pose = new Pose(2);
        pose.set(Animation.Channel.SCALE, rig.bone("head"), new Vector3f(0, 0, 0));
        Matrix4f[] m = matrices(rig, pose);
        assertTrue(Math.abs(m[1].determinant()) > 0);
    }

    @Test
    void shippedRigsAndAnimationsLoad() throws IOException {
        Path mobData = Path.of(System.getProperty("sift.packSrc", "../pack-src"), "mob-data");
        for (String mob : List.of("blub", "sifter", "singer", "echo_golem")) {
            Rig rig = Rig.parse(read(mobData.resolve(mob + ".rig.json")));
            var animations = Animation.parseAll(read(mobData.resolve(mob + ".animation.json")));
            assertTrue(!animations.isEmpty(), mob);
            for (Animation animation : animations.values()) {
                for (String bone : animation.bones().keySet()) {
                    assertTrue(rig.bone(bone) >= 0, mob + ": " + animation.name() + " animates unknown bone " + bone);
                }
            }
            Matrix4f[] m = matrices(rig, new Pose(rig.bones().size()));
            for (Matrix4f matrix : m) {
                assertTrue(Float.isFinite(matrix.determinant()) && matrix.determinant() > 0, mob);
            }
        }
    }

    private static JsonObject read(Path file) throws IOException {
        return JsonParser.parseString(Files.readString(file)).getAsJsonObject();
    }
}
