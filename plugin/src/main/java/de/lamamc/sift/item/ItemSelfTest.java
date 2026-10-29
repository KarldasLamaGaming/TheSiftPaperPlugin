package de.lamamc.sift.item;

import de.lamamc.sift.block.HeldTools;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemAttributeModifiers;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.stream.StreamSupport;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.advancement.Advancement;
import org.bukkit.attribute.Attribute;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.SmithingTransformRecipe;
import org.bukkit.loot.LootContext;
import org.bukkit.loot.LootTable;

/**
 * Checks items, recipes, loot and advancements on the running server without a player
 * ({@code /sift debug selftest}). Things only a player can trigger (crafting result events, anvils) are
 * covered by the unit tests of the rules and docs/TESTING.md.
 */
public final class ItemSelfTest {

    private final SiftItems items;
    private final SiftRecipes recipes;
    private final LootNormalizer loot;
    private final List<String> lines = new ArrayList<>();
    private int failures;

    public ItemSelfTest(SiftItems items, SiftRecipes recipes) {
        this.items = items;
        this.recipes = recipes;
        this.loot = new LootNormalizer(items);
    }

    public List<String> run(World world) {
        recipes(world);
        siftiteGear();
        otherItems();
        advancements();
        loot(world);
        lines.add(failures == 0 ? "Selbsttest bestanden" : "Selbsttest: " + failures + " Fehler");
        return lines;
    }

    private void check(boolean ok, String what) {
        if (!ok) {
            failures++;
        }
        lines.add((ok ? "OK      " : "FEHLER  ") + what);
    }

    private ItemStack sift(String id) {
        return items.create(id, 1);
    }

    private static ItemStack[] matrix(ItemStack... slots) {
        ItemStack[] matrix = new ItemStack[9];
        System.arraycopy(slots, 0, matrix, 0, slots.length);
        return matrix;
    }

    private static String crafted(ItemStack[] matrix, World world) {
        Recipe recipe = Bukkit.getCraftingRecipe(matrix, world);
        return recipe instanceof Keyed keyed ? keyed.getKey().toString() : "-";
    }

    private void recipes(World world) {
        int expected = (int) SiftRecipes.bundled(items.registry()).stream().filter(r -> !r.skipped()).count();
        check(recipes.keys().size() == expected, "Rezepte registriert: " + recipes.keys().size() + " von " + expected);

        ItemStack nugget = sift("siftite_nugget");
        ItemStack[] nuggets = matrix(nugget, nugget, nugget, nugget, nugget, nugget, nugget, nugget, nugget);
        check(crafted(nuggets, world).equals("the_sift:siftite_ingot"), "9 Siftitklumpen -> Siftitbarren");
        ItemStack paper = ItemStack.of(Material.PAPER);
        check(!crafted(matrix(paper, paper, paper, paper, paper, paper, paper, paper, paper), world).startsWith("the_sift:"),
                "9 Papier ergeben keinen Siftitbarren");

        ItemStack log = sift("overgrown_willow_log");
        ItemStack renamed = log.clone();
        renamed.setData(DataComponentTypes.CUSTOM_NAME, Component.text("Mein Stamm"));
        check(crafted(matrix(log), world).equals("the_sift:overgrown_willow_planks"), "Weidenstamm -> Weidenbretter");
        check(crafted(matrix(renamed), world).equals("the_sift:overgrown_willow_planks"), "umbenannter Weidenstamm -> Weidenbretter");
        check(crafted(matrix(ItemStack.of(Material.BROWN_MUSHROOM_BLOCK)), world).equals("-"),
                "brauner Pilzblock ergibt keine Weidenbretter");

        ItemStack cobbled = sift("cobbled_siftslate");
        ItemStack oak = ItemStack.of(Material.OAK_PLANKS);
        ItemStack willow = sift("overgrown_willow_planks");
        ItemStack iron = ItemStack.of(Material.IRON_INGOT);
        ItemStack redstone = ItemStack.of(Material.REDSTONE);
        check(crafted(matrix(oak, oak, oak, cobbled, iron, cobbled, cobbled, redstone, cobbled), world)
                .equals("the_sift:piston_from_cobbled_siftslate"), "Kolben aus Siftschiefer-Bruchstein");
        check(crafted(matrix(willow, null, null, willow), world).equals("the_sift:overgrown_willow_sticks"), "Weidenbretter -> Stöcke");

        Recipe upgrade = Bukkit.getRecipe(new NamespacedKey(SiftRecipes.NAMESPACE, "siftite_sword_smithing"));
        if (upgrade instanceof SmithingTransformRecipe smithing) {
            check(smithing.getTemplate().test(sift("siftite_upgrade_smithing_template")), "Schmieden: Vorlage passt");
            check(!smithing.getTemplate().test(paper), "Schmieden: Papier ist keine Vorlage");
            check(smithing.getBase().test(ItemStack.of(Material.NETHERITE_SWORD)), "Schmieden: Netheritschwert passt");
            check(!smithing.getBase().test(sift("siftite_sword")), "Schmieden: Siftitschwert ist kein Netheritschwert");
            check(smithing.getAddition().test(sift("siftite_ingot")), "Schmieden: Siftitbarren passt");
            check(!smithing.getAddition().test(ItemStack.of(Material.NETHERITE_INGOT)), "Schmieden: Netheritbarren ist kein Siftitbarren");
        } else {
            check(false, "Schmiederezept siftite_sword_smithing fehlt");
        }
    }

    private void siftiteGear() {
        ItemStack sword = sift("siftite_sword");
        check(Integer.valueOf(2200).equals(sword.getData(DataComponentTypes.MAX_DAMAGE)), "Siftitschwert Haltbarkeit 2200");
        check(Math.abs(modifier(sword, Attribute.ATTACK_DAMAGE) - 7.5) < 1e-6, "Siftitschwert Angriffsschaden 8,5");
        check(Math.abs(HeldTools.of(sift("siftite_pickaxe")).speed() - 9.5) < 1e-6, "Siftitspitzhacke Abbautempo 9,5");
        check(Math.abs(HeldTools.of(ItemStack.of(Material.NETHERITE_PICKAXE)).speed() - 9.0) < 1e-6,
                "Netheritspitzhacke unverändert 9");
        ItemStack helmet = sift("siftite_helmet");
        check(Integer.valueOf(429).equals(helmet.getData(DataComponentTypes.MAX_DAMAGE)), "Siftithelm Haltbarkeit 429");
        check(Math.abs(modifier(helmet, Attribute.ARMOR) - 3) < 1e-6, "Siftithelm Rüstung 3");
        check(Math.abs(modifier(helmet, Attribute.ARMOR_TOUGHNESS) - 3) < 1e-6, "Siftithelm Härte 3");
        check(Math.abs(modifier(helmet, Attribute.KNOCKBACK_RESISTANCE) - 0.12) < 1e-6, "Siftithelm Rückstoßresistenz 0,12");
        var equippable = helmet.getData(DataComponentTypes.EQUIPPABLE);
        check(equippable != null && ItemComponents.SIFTITE_EQUIPMENT.equals(equippable.assetId()), "Siftithelm Rüstungsoptik the_sift:siftite");
        check(sift("siftite_boots").getData(DataComponentTypes.REPAIRABLE) != null, "Siftitstiefel reparierbar");
        check(Math.abs(modifier(ItemStack.of(Material.NETHERITE_SWORD), Attribute.ATTACK_DAMAGE) - 7.0) < 1e-6,
                "Netheritschwert unverändert 8");
    }

    private void otherItems() {
        var food = sift("cooked_sifter_meat").getData(DataComponentTypes.FOOD);
        check(food != null && food.nutrition() == 8, "Gebratenes Sifterfleisch: 8 Nahrung");
        var potion = sift("ichor_bottle").getData(DataComponentTypes.POTION_CONTENTS);
        check(potion != null && potion.customEffects().size() == 1, "Ichorflasche: Regeneration");
        var disc = sift("music_disc_wellspring").getData(DataComponentTypes.JUKEBOX_PLAYABLE);
        check(disc != null, "Schallplatte Wellspring spielbar");
        var template = sift("siftite_upgrade_smithing_template").getData(DataComponentTypes.LORE);
        check(template != null && template.lines().size() == 6, "Aufwertungsvorlage mit Tooltip");
    }

    private static double modifier(ItemStack stack, Attribute attribute) {
        ItemAttributeModifiers modifiers = stack.getData(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        if (modifiers == null) {
            return Double.NaN;
        }
        return modifiers.modifiers().stream().filter(e -> e.attribute() == attribute)
                .mapToDouble(e -> e.modifier().getAmount()).sum();
    }

    private void advancements() {
        Iterator<Advancement> it = Bukkit.advancementIterator();
        long count = StreamSupport.stream(((Iterable<Advancement>) () -> it).spliterator(), false)
                .filter(a -> a.getKey().getNamespace().equals("the_sift")).count();
        check(count == 22, "Advancements der Sift geladen: " + count + " von 22");
    }

    private void loot(World world) {
        LootTable table = Bukkit.getLootTable(new NamespacedKey("the_sift", "chests/abandoned_portal"));
        if (table == null) {
            check(false, "Loot-Tabelle the_sift:chests/abandoned_portal fehlt");
            return;
        }
        int siftItems = 0;
        int complete = 0;
        Random random = new Random(1);
        for (int i = 0; i < 20; i++) {
            LootContext context = new LootContext.Builder(new Location(world, 0, 64, 0)).build();
            for (ItemStack stack : table.populateLoot(random, context)) {
                if (SiftItems.idOf(stack) != null) {
                    siftItems++;
                    ItemStack real = loot.normalize(stack);
                    if (real.getType() == Material.matchMaterial(items.registry().get(SiftItems.idOf(stack)).baseItem())
                            && real.getData(DataComponentTypes.ITEM_NAME) != null) {
                        complete++;
                    }
                }
            }
        }
        check(siftItems > 0 && complete == siftItems, "Truhen-Loot: " + complete + " von " + siftItems + " Sift-Items vervollständigt");
    }
}
