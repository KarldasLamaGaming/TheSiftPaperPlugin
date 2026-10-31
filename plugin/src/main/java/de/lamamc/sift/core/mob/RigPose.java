package de.lamamc.sift.core.mob;

import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Turns a rig and a pose into one transformation matrix per cube for its item display, composed like
 * GeckoLib renders: per bone translate(position) · translate(pivot) · rotZ · rotY · rotX · scale ·
 * translate(-pivot), the parent chain first, then the cube rotation about its own pivot.
 *
 * <p>The cube's item model spans 0 … size·modelScale pixels; an item display draws it rotated by π about y
 * and shifted by -0.5 block, so the matrix maps it back onto the cube's place in the rig. {@code root}
 * goes from model space in blocks (origin between the feet, +z = the mob's front after the yaw rotation
 * GeckoLib applies, see {@link #root}) to the display's local space.
 */
public final class RigPose {

    /** Scales of zero cannot be sent to a display (its matrix is decomposed); they become this small. */
    static final float MIN_SCALE = 1.0e-4f;
    private static final float DEG = (float) (Math.PI / 180.0);

    private RigPose() {
    }

    /**
     * Root transformation like GeckoLib's entity renderer: offset from the display to the mob's feet (blocks),
     * then rotate by 180° − body yaw and scale the model.
     */
    public static Matrix4f root(float offsetX, float offsetY, float offsetZ, float bodyYaw, float scale, Matrix4f out) {
        return out.identity().translate(offsetX, offsetY, offsetZ).rotateY((180f - bodyYaw) * DEG).scale(scale);
    }

    /** Fills {@code out} (one matrix per cube of the rig). */
    public static Matrix4f[] cubeMatrices(Rig rig, Pose pose, Matrix4f root, Matrix4f[] out) {
        int boneCount = rig.bones().size();
        Matrix4f[] bones = new Matrix4f[boneCount];
        Vector3f rotation = new Vector3f();
        Vector3f position = new Vector3f();
        Vector3f scale = new Vector3f();
        for (int i = 0; i < boneCount; i++) {
            Rig.Bone bone = rig.bones().get(i);
            Matrix4f m = bone.parent() < 0 ? new Matrix4f(root).scale(1f / 16f) : new Matrix4f(bones[bone.parent()]);
            pose.get(Animation.Channel.ROTATION, i, rotation).add(bone.rotation());
            pose.get(Animation.Channel.POSITION, i, position);
            pose.get(Animation.Channel.SCALE, i, scale);
            Vector3f pivot = bone.pivot();
            m.translate(position).translate(pivot);
            rotateZyx(m, rotation);
            m.scale(nonZero(scale.x), nonZero(scale.y), nonZero(scale.z)).translate(-pivot.x, -pivot.y, -pivot.z);
            bones[i] = m;
        }
        for (int c = 0; c < rig.cubes().size(); c++) {
            Rig.Cube cube = rig.cubes().get(c);
            Matrix4f m = out[c] == null ? (out[c] = new Matrix4f()) : out[c];
            m.set(bones[cube.bone()]);
            Vector3f pivot = cube.pivot();
            m.translate(pivot);
            rotateZyx(m, cube.rotation());
            m.translate(-pivot.x, -pivot.y, -pivot.z);
            float k = cube.modelScale();
            m.translate(cube.from().x + 8f / k, cube.from().y + 8f / k, cube.from().z + 8f / k)
                    .scale(16f / k)
                    .rotateY((float) Math.PI);
        }
        return out;
    }

    private static void rotateZyx(Matrix4f m, Vector3f degrees) {
        if (degrees.z != 0) {
            m.rotateZ(degrees.z * DEG);
        }
        if (degrees.y != 0) {
            m.rotateY(degrees.y * DEG);
        }
        if (degrees.x != 0) {
            m.rotateX(degrees.x * DEG);
        }
    }

    private static float nonZero(float value) {
        return Math.abs(value) < MIN_SCALE ? MIN_SCALE : value;
    }
}
