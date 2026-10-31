package de.lamamc.sift.core.mob;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.joml.Vector3f;

/**
 * One Blockbench/GeckoLib animation (mob-data/&lt;mob&gt;.animation.json): keyframes per bone for rotation
 * (degrees), position (pixels) and scale. Values are converted to Java model space on load like GeckoLib
 * does it (rotation x and y negated, position x negated).
 */
public record Animation(String name, double length, Loop loop, Map<String, BoneTrack> bones) {

    public enum Loop {
        /** Plays once; afterwards the bones are back at rest. */
        ONCE,
        LOOP,
        /** Plays once and keeps the last frame. */
        HOLD
    }

    public enum Channel {
        ROTATION, POSITION, SCALE
    }

    public record Keyframe(double time, Vector3f value, Easing easing) {
    }

    /** Keyframes sorted by time; one keyframe means a constant value. */
    public record Track(List<Keyframe> keyframes) {

        public Vector3f sample(double time, Vector3f out) {
            List<Keyframe> frames = keyframes;
            Keyframe first = frames.getFirst();
            if (frames.size() == 1 || time <= first.time()) {
                return out.set(first.value());
            }
            for (int i = 1; i < frames.size(); i++) {
                Keyframe next = frames.get(i);
                if (time <= next.time()) {
                    Keyframe previous = frames.get(i - 1);
                    double span = next.time() - previous.time();
                    double t = span <= 0 ? 1 : next.easing().apply((time - previous.time()) / span);
                    return previous.value().lerp(next.value(), (float) t, out);
                }
            }
            return out.set(frames.getLast().value());
        }

        double lastTime() {
            return keyframes.getLast().time();
        }
    }

    /** The tracks of one bone; missing channels are null. */
    public record BoneTrack(Track rotation, Track position, Track scale) {

        public Track get(Channel channel) {
            return switch (channel) {
                case ROTATION -> rotation;
                case POSITION -> position;
                case SCALE -> scale;
            };
        }
    }

    /** Animation time for the time since start: wrapped for loops, clamped otherwise. */
    public double localTime(double elapsed) {
        if (loop == Loop.LOOP) {
            return length <= 0 ? 0 : elapsed % length;
        }
        return Math.min(elapsed, length);
    }

    public boolean finished(double elapsed) {
        return loop == Loop.ONCE && elapsed >= length;
    }

    public static Map<String, Animation> parseAll(JsonObject file) {
        Map<String, Animation> animations = new LinkedHashMap<>();
        for (var entry : file.getAsJsonObject("animations").entrySet()) {
            animations.put(entry.getKey(), parse(entry.getKey(), entry.getValue().getAsJsonObject()));
        }
        return animations;
    }

    static Animation parse(String name, JsonObject json) {
        Loop loop = Loop.ONCE;
        if (json.has("loop")) {
            JsonPrimitive value = json.getAsJsonPrimitive("loop");
            loop = value.isBoolean() ? (value.getAsBoolean() ? Loop.LOOP : Loop.ONCE)
                    : "hold_on_last_frame".equals(value.getAsString()) ? Loop.HOLD
                    : "true".equals(value.getAsString()) ? Loop.LOOP : Loop.ONCE;
        }
        Map<String, BoneTrack> bones = new HashMap<>();
        double last = 0;
        if (json.has("bones")) {
            for (var bone : json.getAsJsonObject("bones").entrySet()) {
                JsonObject channels = bone.getValue().getAsJsonObject();
                BoneTrack track = new BoneTrack(track(channels.get("rotation"), Channel.ROTATION),
                        track(channels.get("position"), Channel.POSITION), track(channels.get("scale"), Channel.SCALE));
                for (Channel channel : Channel.values()) {
                    if (track.get(channel) != null) {
                        last = Math.max(last, track.get(channel).lastTime());
                    }
                }
                bones.put(bone.getKey(), track);
            }
        }
        double length = json.has("animation_length") ? json.get("animation_length").getAsDouble() : last;
        return new Animation(name, length, loop, Map.copyOf(bones));
    }

    private static Track track(JsonElement json, Channel channel) {
        if (json == null || json.isJsonNull()) {
            return null;
        }
        List<Keyframe> frames = new ArrayList<>();
        if (json.isJsonObject() && !isValue(json.getAsJsonObject())) {
            for (var entry : json.getAsJsonObject().entrySet()) {
                JsonElement frame = entry.getValue();
                String easing = frame.isJsonObject() && frame.getAsJsonObject().has("easing")
                        ? frame.getAsJsonObject().get("easing").getAsString() : null;
                frames.add(new Keyframe(Double.parseDouble(entry.getKey()), value(frame, channel), Easing.byName(easing)));
            }
            frames.sort((a, b) -> Double.compare(a.time(), b.time()));
        } else {
            frames.add(new Keyframe(0, value(json, channel), Easing.LINEAR));
        }
        return frames.isEmpty() ? null : new Track(List.copyOf(frames));
    }

    private static boolean isValue(JsonObject object) {
        return object.has("vector") || object.has("post") || object.has("pre");
    }

    /** A keyframe value: number, [x, y, z], {vector}, or {pre, post} (post wins); in Java model space. */
    private static Vector3f value(JsonElement json, Channel channel) {
        if (json.isJsonObject()) {
            JsonObject object = json.getAsJsonObject();
            JsonElement inner = object.has("vector") ? object.get("vector") : object.has("post") ? object.get("post") : object.get("pre");
            if (inner.isJsonObject() && inner.getAsJsonObject().has("vector")) {
                inner = inner.getAsJsonObject().get("vector");
            }
            return value(inner, channel);
        }
        Vector3f v;
        if (json.isJsonArray()) {
            JsonArray array = json.getAsJsonArray();
            v = new Vector3f(number(array.get(0)), number(array.get(1)), number(array.get(2)));
        } else {
            float s = number(json);
            v = new Vector3f(s, s, s);
        }
        return switch (channel) {
            case ROTATION -> new Vector3f(-v.x, -v.y, v.z);
            case POSITION -> new Vector3f(-v.x, v.y, v.z);
            case SCALE -> v;
        };
    }

    /** Numbers or numeric strings; molang expressions are not supported and count as 0. */
    private static float number(JsonElement json) {
        JsonPrimitive primitive = json.getAsJsonPrimitive();
        if (primitive.isNumber()) {
            return primitive.getAsFloat();
        }
        try {
            return Float.parseFloat(primitive.getAsString().trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
