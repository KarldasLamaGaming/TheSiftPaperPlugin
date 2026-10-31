package de.lamamc.sift.mob;

import de.lamamc.sift.core.mob.Rig;
import de.lamamc.sift.core.mob.RigPose;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.CustomModelData;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.key.Key;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.joml.Matrix4f;

/**
 * The item displays that draw one mob: one per cube of its rig, riding the invisible base mob so they move
 * with it on the client. Each update sends a new transformation (interpolated by the client) only for
 * cubes whose matrix changed, and only while a player tracks the rig.
 */
final class RigDisplays {

    private static final float DEG = (float) (Math.PI / 180.0);

    private final SiftMob mob;
    private final List<ItemDisplay> displays = new ArrayList<>();
    private final Matrix4f[] matrices;
    private final Matrix4f[] sent;
    private final Matrix4f root = new Matrix4f();
    private String appearance;

    RigDisplays(SiftMob mob) {
        this.mob = mob;
        int cubes = mob.kind.assets().rig().cubes().size();
        this.matrices = new Matrix4f[cubes];
        this.sent = new Matrix4f[cubes];
    }

    int size() {
        return displays.size();
    }

    boolean intact() {
        if (displays.size() != matrices.length) {
            return false;
        }
        for (ItemDisplay display : displays) {
            if (!display.isValid() || display.getVehicle() != mob.base) {
                return false;
            }
        }
        return true;
    }

    void spawn(RenderSettings settings) {
        remove();
        Location at = mob.base.getLocation();
        String look = appearanceKey();
        compute(0, (float) -mob.base.getHeight(), 0); // seat estimate until the first update
        for (int i = 0; i < matrices.length; i++) {
            int cube = i;
            ItemDisplay display = at.getWorld().spawn(at, ItemDisplay.class, d -> {
                d.setPersistent(false);
                d.getPersistentDataContainer().set(MobManager.RIG_KEY, PersistentDataType.STRING, mob.base.getUniqueId().toString());
                d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                d.setItemStack(item(cube));
                d.setViewRange(settings.viewRange() / 64f);
                d.setInterpolationDuration(0);
                d.setTeleportDuration(0);
                d.setTransformationMatrix(matrices[cube]);
                float shadow = (float) mob.kind.config().getDouble("shadow-radius", 0);
                if (cube == 0 && shadow > 0) {
                    d.setShadowRadius(shadow);
                    d.setShadowStrength(1.0f);
                }
            });
            mob.base.addPassenger(display);
            displays.add(display);
            sent[i] = new Matrix4f(matrices[i]);
        }
        appearance = look;
        mob.manager.stats().spawnedDisplays += matrices.length;
    }

    void remove() {
        for (ItemDisplay display : displays) {
            display.remove();
        }
        displays.clear();
    }

    /** Sends the current pose; returns how many displays got a new transformation. */
    int update(RenderSettings settings) {
        if (displays.isEmpty() || settings.onlyWhenWatched() && displays.getFirst().getTrackedBy().isEmpty()) {
            return 0;
        }
        String look = appearanceKey();
        if (!look.equals(appearance)) {
            for (int i = 0; i < displays.size(); i++) {
                displays.get(i).setItemStack(item(i));
            }
            appearance = look;
        }
        Location base = mob.base.getLocation();
        Location seat = displays.getFirst().getLocation();
        compute((float) (base.getX() - seat.getX()), (float) (base.getY() - seat.getY()), (float) (base.getZ() - seat.getZ()));
        int changed = 0;
        for (int i = 0; i < displays.size(); i++) {
            if (sent[i].equals(matrices[i], 1.0e-4f)) {
                continue;
            }
            ItemDisplay display = displays.get(i);
            display.setInterpolationDelay(0);
            display.setInterpolationDuration(settings.interpolationTicks());
            display.setTransformationMatrix(matrices[i]);
            sent[i].set(matrices[i]);
            changed++;
        }
        return changed;
    }

    /** Pose of all cubes; (dx, dy, dz) goes from the displays' seat to the mob's feet. */
    private void compute(float dx, float dy, float dz) {
        Rig rig = mob.kind.assets().rig();
        mob.animations.pose(mob.pose);
        mob.adjustPose(mob.pose);
        float scale = mob.kind.modelScale();
        root.identity().translate(dx, dy, dz).rotateY((180f - mob.base.getBodyYaw()) * DEG);
        if (mob.deathTicks > 0) {
            // vanilla death: tip over by up to 90° within the first second
            float f = (float) Math.sqrt((mob.deathTicks - 1) / 20f * 1.6f);
            root.rotateZ(Math.min(1f, f) * 90f * DEG);
        }
        root.scale(scale);
        RigPose.cubeMatrices(rig, mob.pose, root, matrices);
    }

    private String appearanceKey() {
        return mob.variant() + (mob.hurtTicks > 0 || mob.dying() ? "#hurt" : "");
    }

    private ItemStack item(int cube) {
        ItemStack stack = ItemStack.of(Material.PAPER);
        stack.setData(DataComponentTypes.ITEM_MODEL, Key.key("the_sift", "mob/" + mob.variant() + "/" + cube));
        if (mob.hurtTicks > 0 || mob.dying()) {
            stack.setData(DataComponentTypes.CUSTOM_MODEL_DATA,
                    CustomModelData.customModelData().addColor(mob.manager.renderSettings().hurtColor()).build());
        }
        return stack;
    }

    static boolean isPart(Entity entity) {
        return entity instanceof ItemDisplay && entity.getPersistentDataContainer().has(MobManager.RIG_KEY, PersistentDataType.STRING);
    }

    /** Render settings (config.yml mobs.render). */
    record RenderSettings(int updateTicks, int interpolationTicks, float viewRange, Color hurtColor,
                          int hurtTicks, boolean onlyWhenWatched) {
    }
}
