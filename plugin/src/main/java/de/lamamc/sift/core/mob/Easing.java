package de.lamamc.sift.core.mob;

import java.util.Locale;
import java.util.function.DoubleUnaryOperator;

/** GeckoLib easings by their Blockbench names; an easing on a keyframe shapes the segment that ends there. */
public enum Easing {
    LINEAR(t -> t),
    STEP(t -> t >= 1 ? 1 : Math.floor(t * 2) / 2),
    EASE_IN_SINE(t -> 1 - Math.cos(t * Math.PI / 2)),
    EASE_OUT_SINE(t -> Math.sin(t * Math.PI / 2)),
    EASE_IN_OUT_SINE(t -> -(Math.cos(Math.PI * t) - 1) / 2),
    EASE_IN_QUAD(t -> in(t, 2)),
    EASE_OUT_QUAD(t -> out(t, 2)),
    EASE_IN_OUT_QUAD(t -> inOut(t, 2)),
    EASE_IN_CUBIC(t -> in(t, 3)),
    EASE_OUT_CUBIC(t -> out(t, 3)),
    EASE_IN_OUT_CUBIC(t -> inOut(t, 3)),
    EASE_IN_QUART(t -> in(t, 4)),
    EASE_OUT_QUART(t -> out(t, 4)),
    EASE_IN_OUT_QUART(t -> inOut(t, 4)),
    EASE_IN_QUINT(t -> in(t, 5)),
    EASE_OUT_QUINT(t -> out(t, 5)),
    EASE_IN_OUT_QUINT(t -> inOut(t, 5)),
    EASE_IN_EXPO(t -> t <= 0 ? 0 : Math.pow(2, 10 * t - 10)),
    EASE_OUT_EXPO(t -> t >= 1 ? 1 : 1 - Math.pow(2, -10 * t)),
    EASE_IN_OUT_EXPO(t -> t <= 0 ? 0 : t >= 1 ? 1
            : t < 0.5 ? Math.pow(2, 20 * t - 10) / 2 : (2 - Math.pow(2, -20 * t + 10)) / 2),
    EASE_IN_CIRC(t -> 1 - Math.sqrt(1 - t * t)),
    EASE_OUT_CIRC(t -> Math.sqrt(1 - (t - 1) * (t - 1))),
    EASE_IN_OUT_CIRC(t -> t < 0.5 ? (1 - Math.sqrt(1 - 4 * t * t)) / 2 : (Math.sqrt(1 - Math.pow(-2 * t + 2, 2)) + 1) / 2);

    private final DoubleUnaryOperator function;

    Easing(DoubleUnaryOperator function) {
        this.function = function;
    }

    public double apply(double t) {
        return function.applyAsDouble(Math.max(0, Math.min(1, t)));
    }

    /** "easeInOutCubic" -> EASE_IN_OUT_CUBIC; unknown names (and none) are linear. */
    public static Easing byName(String name) {
        if (name == null || name.isEmpty()) {
            return LINEAR;
        }
        String constant = name.replaceAll("([a-z])([A-Z])", "$1_$2").toUpperCase(Locale.ROOT);
        for (Easing easing : values()) {
            if (easing.name().equals(constant)) {
                return easing;
            }
        }
        return LINEAR;
    }

    private static double in(double t, int power) {
        return Math.pow(t, power);
    }

    private static double out(double t, int power) {
        return 1 - Math.pow(1 - t, power);
    }

    private static double inOut(double t, int power) {
        return t < 0.5 ? Math.pow(2, power - 1) * Math.pow(t, power) : 1 - Math.pow(-2 * t + 2, power) / 2;
    }
}
