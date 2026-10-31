package de.lamamc.sift.core.mob;

import java.util.Arrays;
import org.joml.Vector3f;

/**
 * Animated offsets of every bone of a rig in Java model space: rotation in degrees (added to the rest
 * rotation), position in pixels, scale as factor. Mob code may adjust it after the animations (like the
 * renderer hooks of the original).
 */
public final class Pose {

    private final float[] rotation;
    private final float[] position;
    private final float[] scale;

    public Pose(int bones) {
        rotation = new float[bones * 3];
        position = new float[bones * 3];
        scale = new float[bones * 3];
        reset();
    }

    public int bones() {
        return rotation.length / 3;
    }

    public void reset() {
        Arrays.fill(rotation, 0);
        Arrays.fill(position, 0);
        Arrays.fill(scale, 1);
    }

    public void set(Animation.Channel channel, int bone, Vector3f value) {
        float[] target = array(channel);
        target[bone * 3] = value.x;
        target[bone * 3 + 1] = value.y;
        target[bone * 3 + 2] = value.z;
    }

    public Vector3f get(Animation.Channel channel, int bone, Vector3f out) {
        float[] source = array(channel);
        return out.set(source[bone * 3], source[bone * 3 + 1], source[bone * 3 + 2]);
    }

    /** Adds to the rotation (degrees) of a bone; ignored for bones the rig does not have (index -1). */
    public void rotate(int bone, float x, float y, float z) {
        add(rotation, bone, x, y, z);
    }

    /** Adds to the position (pixels) of a bone; ignored for index -1. */
    public void translate(int bone, float x, float y, float z) {
        add(position, bone, x, y, z);
    }

    private static void add(float[] target, int bone, float x, float y, float z) {
        if (bone < 0) {
            return;
        }
        target[bone * 3] += x;
        target[bone * 3 + 1] += y;
        target[bone * 3 + 2] += z;
    }

    private float[] array(Animation.Channel channel) {
        return switch (channel) {
            case ROTATION -> rotation;
            case POSITION -> position;
            case SCALE -> scale;
        };
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Pose other && Arrays.equals(rotation, other.rotation) && Arrays.equals(position, other.position)
                && Arrays.equals(scale, other.scale);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(rotation) * 31 + Arrays.hashCode(position) * 7 + Arrays.hashCode(scale);
    }

    public Pose copyInto(Pose target) {
        System.arraycopy(rotation, 0, target.rotation, 0, rotation.length);
        System.arraycopy(position, 0, target.position, 0, position.length);
        System.arraycopy(scale, 0, target.scale, 0, scale.length);
        return target;
    }
}
