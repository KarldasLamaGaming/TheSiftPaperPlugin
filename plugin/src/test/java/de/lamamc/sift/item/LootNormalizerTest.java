package de.lamamc.sift.item;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.loot.LootContext;
import org.junit.jupiter.api.Test;

/** alpha.6 blocker: the listener emptied every loot chest (vanilla and Sift) by handing getLoot() to setLoot(). */
class LootNormalizerTest {

    @Test
    void listenerKeepsTheGeneratedLoot() {
        // null entries stand for loot (null = air in the API); ItemStacks need a running server
        List<ItemStack> generated = new ArrayList<>(Arrays.asList(null, null, null));
        World world = (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[] {World.class},
                (proxy, method, args) -> null);
        LootGenerateEvent event = new LootGenerateEvent(world, null, null, null,
                new LootContext.Builder(new Location(world, 0, 64, 0)).build(), generated, false);
        new LootNormalizer(null).onLoot(event);
        assertEquals(3, event.getLoot().size());
    }
}
