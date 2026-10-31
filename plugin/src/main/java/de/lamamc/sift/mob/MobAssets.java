package de.lamamc.sift.mob;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.lamamc.sift.core.mob.Animation;
import de.lamamc.sift.core.mob.Rig;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Rig and animations of a mob as packgen ships them in the plugin jar (mobs/&lt;id&gt;.rig.json, .animation.json). */
public record MobAssets(Rig rig, Map<String, Animation> animations) {

    public static MobAssets load(String id) {
        Rig rig = Rig.parse(read("/mobs/" + id + ".rig.json"));
        JsonObject animations = read("/mobs/" + id + ".animation.json");
        return new MobAssets(rig, Animation.parseAll(animations));
    }

    private static JsonObject read(String path) {
        try (InputStream in = MobAssets.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException(path + " missing in the plugin jar (run packgen importSift)");
            }
            return JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
