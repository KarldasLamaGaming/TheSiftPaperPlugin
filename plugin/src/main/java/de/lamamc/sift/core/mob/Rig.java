package de.lamamc.sift.core.mob;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.joml.Vector3f;

/**
 * A mob model as packgen writes it (mob-data/&lt;mob&gt;.rig.json): bones with parent, pivot and rest rotation,
 * and the cubes, each shown by one item display. Everything is in Java model space (pixels, degrees), x
 * already mirrored like GeckoLib does it.
 */
public record Rig(int textureWidth, int textureHeight, List<Bone> bones, List<Cube> cubes, Map<String, Integer> boneIndex) {

    /** {@code parent} is the index of the parent bone or -1; parents come before their children. */
    public record Bone(String name, int parent, Vector3f pivot, Vector3f rotation) {
    }

    /** {@code from} is the cube's minimum corner, {@code modelScale} how much smaller its item model is. */
    public record Cube(int bone, Vector3f from, Vector3f size, float modelScale, Vector3f pivot, Vector3f rotation) {
    }

    public int bone(String name) {
        return boneIndex.getOrDefault(name, -1);
    }

    public static Rig parse(JsonObject json) {
        List<Bone> bones = new ArrayList<>();
        Map<String, Integer> index = new HashMap<>();
        List<JsonObject> pending = new ArrayList<>();
        for (JsonElement element : json.getAsJsonArray("bones")) {
            pending.add(element.getAsJsonObject());
        }
        // order parents before children (geo files usually are, but do not rely on it)
        while (!pending.isEmpty()) {
            boolean progress = false;
            for (var it = pending.iterator(); it.hasNext(); ) {
                JsonObject bone = it.next();
                String parent = bone.has("parent") ? bone.get("parent").getAsString() : null;
                if (parent != null && !index.containsKey(parent)) {
                    continue;
                }
                String name = bone.get("name").getAsString();
                index.put(name, bones.size());
                bones.add(new Bone(name, parent == null ? -1 : index.get(parent), vector(bone, "pivot"), vector(bone, "rotation")));
                it.remove();
                progress = true;
            }
            if (!progress) {
                throw new IllegalArgumentException("bones with missing parents: " + pending);
            }
        }
        List<Cube> cubes = new ArrayList<>();
        for (JsonElement element : json.getAsJsonArray("cubes")) {
            JsonObject cube = element.getAsJsonObject();
            Integer bone = index.get(cube.get("bone").getAsString());
            if (bone == null) {
                throw new IllegalArgumentException("cube of unknown bone " + cube.get("bone"));
            }
            cubes.add(new Cube(bone, vector(cube, "from"), vector(cube, "size"), cube.get("model_scale").getAsFloat(),
                    vector(cube, "pivot"), vector(cube, "rotation")));
        }
        return new Rig(json.get("texture_width").getAsInt(), json.get("texture_height").getAsInt(), List.copyOf(bones),
                List.copyOf(cubes), Map.copyOf(index));
    }

    private static Vector3f vector(JsonObject object, String key) {
        if (!object.has(key)) {
            return new Vector3f();
        }
        JsonArray array = object.getAsJsonArray(key);
        return new Vector3f(array.get(0).getAsFloat(), array.get(1).getAsFloat(), array.get(2).getAsFloat());
    }
}
