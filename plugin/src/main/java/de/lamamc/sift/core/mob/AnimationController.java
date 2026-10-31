package de.lamamc.sift.core.mob;

import java.util.LinkedHashMap;
import java.util.Map;
import org.joml.Vector3f;

/**
 * Animation state of one mob, like GeckoLib's controllers: named layers, each with a looping (or held)
 * base animation and an optional triggered one that plays over it until it ends. Layers are applied in
 * the order they were created; a later layer overrides the channels it animates. Time advances per tick
 * with each layer's speed.
 */
public final class AnimationController {

    private final Rig rig;
    private final Map<String, Animation> animations;
    private final Map<String, Layer> layers = new LinkedHashMap<>();
    private final Vector3f scratch = new Vector3f();

    private static final class Layer {
        Animation base;
        double baseTime;
        double speed = 1;
        Animation triggered;
        double triggeredTime;
    }

    public AnimationController(Rig rig, Map<String, Animation> animations, String... layerNames) {
        this.rig = rig;
        this.animations = animations;
        for (String name : layerNames) {
            layers.put(name, new Layer());
        }
    }

    private Layer layer(String name) {
        Layer layer = layers.get(name);
        if (layer == null) {
            throw new IllegalArgumentException("unknown layer " + name);
        }
        return layer;
    }

    private Animation animation(String name) {
        Animation animation = animations.get(name);
        if (animation == null) {
            throw new IllegalArgumentException("unknown animation " + name);
        }
        return animation;
    }

    /** Sets the base animation of a layer; keeps its time when it is already playing. */
    public void play(String layerName, String animationName, double speed) {
        Layer layer = layer(layerName);
        Animation animation = animation(animationName);
        if (layer.base != animation) {
            layer.base = animation;
            layer.baseTime = 0;
        }
        layer.speed = speed;
    }

    /** Stops the base animation of a layer (its bones go back to rest, like GeckoLib's reset). */
    public void stop(String layerName) {
        Layer layer = layer(layerName);
        layer.base = null;
        layer.baseTime = 0;
        layer.speed = 1;
    }

    /** Plays an animation once over the base animation of a layer, from the start. */
    public void trigger(String layerName, String animationName) {
        Layer layer = layer(layerName);
        layer.triggered = animation(animationName);
        layer.triggeredTime = 0;
    }

    public boolean isTriggered(String layerName) {
        return layer(layerName).triggered != null;
    }

    public String current(String layerName) {
        Layer layer = layer(layerName);
        Animation shown = layer.triggered != null ? layer.triggered : layer.base;
        return shown == null ? null : shown.name();
    }

    /** Advances all layers; triggered animations that ended are removed. */
    public void tick(double seconds) {
        for (Layer layer : layers.values()) {
            if (layer.triggered != null) {
                layer.triggeredTime += seconds;
                if (layer.triggered.finished(layer.triggeredTime)) {
                    layer.triggered = null;
                }
            } else if (layer.base != null) {
                layer.baseTime += seconds * layer.speed;
            }
        }
    }

    /** Whether any layer shows an animation (otherwise the pose is the rest pose). */
    public boolean animating() {
        for (Layer layer : layers.values()) {
            if (layer.triggered != null || layer.base != null && !layer.base.finished(layer.baseTime)) {
                return true;
            }
        }
        return false;
    }

    /** Writes the current pose (rest + animations) into {@code pose}. */
    public Pose pose(Pose pose) {
        pose.reset();
        for (Layer layer : layers.values()) {
            if (layer.triggered != null) {
                apply(layer.triggered, layer.triggered.localTime(layer.triggeredTime), pose);
            } else if (layer.base != null && !layer.base.finished(layer.baseTime)) {
                apply(layer.base, layer.base.localTime(layer.baseTime), pose);
            }
        }
        return pose;
    }

    private void apply(Animation animation, double time, Pose pose) {
        for (var entry : animation.bones().entrySet()) {
            int bone = rig.bone(entry.getKey());
            if (bone < 0) {
                continue;
            }
            for (Animation.Channel channel : Animation.Channel.values()) {
                Animation.Track track = entry.getValue().get(channel);
                if (track != null) {
                    pose.set(channel, bone, track.sample(time, scratch));
                }
            }
        }
    }
}
