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
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.data.BlockData;
import org.bukkit.attribute.Attribute;
import org.bukkit.inventory.Inventory;
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
        languages();
        messages();
        charoiteWisp();
        templeWalk(world);
        bossBalance();
        blockStates();
        advancementSources();
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
        int expected = recipes.expected();
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
        check(crafted(matrix(willow, willow, willow, willow, null, willow, willow, willow, willow), world)
                .equals("the_sift:willow_chest"), "Weidenbretter -> Truhe (#14)");
        check(crafted(matrix(oak, oak, oak, oak, null, oak, oak, oak, oak), world)
                .equals("minecraft:chest"), "Eichenbretter -> Truhe bleibt Vanilla");
        ItemStack glass = sift("ichor_glass");
        ItemStack fronds = sift("overgrown_fronds");
        // the guard empties the result of vanilla recipes with protected items; 6 ichor glass is a Sift recipe
        check(items.isProtected(glass), "Ichorglas ist geschützt (kein Glas für Vanilla-Rezepte)");
        check(crafted(matrix(glass, glass, glass, glass, glass, glass), world).startsWith("the_sift:"),
                "6 Ichorglas -> Ichorglasscheibe (Sift-Rezept)");
        check(items.isProtected(fronds), "Überwucherte Wedel sind geschützt (kein Faden)");
        ItemStack cobbledSlate = sift("cobbled_siftslate");
        check(SiftiteGuard.isSiftStonecutting(cobbledSlate, sift("chiseled_siftslate")),
                "Steinsäge: Siftschiefer-Bruchstein -> gemeißelter Siftschiefer bleibt erlaubt");
        check(!SiftiteGuard.isSiftStonecutting(cobbledSlate, ItemStack.of(Material.CHISELED_DEEPSLATE)),
                "Steinsäge: kein Vanilla-Ergebnis aus Siftschiefer-Bruchstein");

        for (String id : List.of("echo_resonator", "soul_ember_core", "void_resonator", "sift_compass")) {
            ItemStack item = sift(id);
            check(item != null && Boolean.TRUE.equals(item.getData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE))
                    && Bukkit.getRecipe(new NamespacedKey(SiftRecipes.NAMESPACE, id)) != null, id + ": Item und Rezept");
        }
        check(sift("sift_compass").getType() == Material.COMPASS, "Sift-Kompass ist ein Kompass (Nadel)");
        for (String id : List.of("warden_heart", "dragon_scale", "wither_core", "golem_trophy")) {
            ItemStack item = sift(id);
            check(item != null && item.getData(DataComponentTypes.RARITY) == org.bukkit.inventory.ItemRarity.EPIC,
                    id + ": Boss-Item (episch, alpha.39)");
        }
        check(sift("sculk_enchanter") != null && sift("sculk_enchanter").getType() == Material.RED_MUSHROOM_BLOCK,
                "sculk_enchanter: eigener Block (alpha.40.1)");
        check(Bukkit.getRecipe(new NamespacedKey(SiftRecipes.NAMESPACE, "charoite_core")) != null, "charoite_core: Rezept (alpha.40)");
        ItemStack ancientKey = sift("ancient_resonator");
        check(ancientKey != null && Bukkit.getRecipe(new NamespacedKey(SiftRecipes.NAMESPACE, "ancient_resonator")) == null,
                "ancient_resonator: Item ohne Rezept (nur Truhenfund, alpha.37)");

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
        check(count == 23, "Advancements der Sift geladen: " + count + " von 23 (22 des Originals, 1 eigenes)");
        check(Bukkit.getAdvancement(new NamespacedKey("the_sift", "the_sift/great_echo_golem")) != null,
                "Advancement the_sift:the_sift/great_echo_golem (Tempel-Boss, alpha.42b.2) geladen");
    }

    /** alpha.42c: the lang files of the pack in the jar, every key in German and English, same placeholders. */
    private void languages() {
        java.util.Map<String, String> en = new java.util.HashMap<>();
        java.util.Map<String, String> german = new java.util.HashMap<>();
        Translations.read("/lang/en_us.json", en);
        Translations.read("/lang/lamakingdom_en_us.json", en);
        Translations.read("/lang/de_de.json", german);
        List<String> problems = de.lamamc.sift.core.lang.LangCheck.compare("de_de", german, "en_us", en);
        check(problems.isEmpty() && !german.isEmpty(), "Sprachdateien des Packs: " + german.size() + " Keys Deutsch, " + en.size()
                + " Englisch" + (problems.isEmpty() ? ", alle in beiden Sprachen mit gleichen Platzhaltern"
                : ", Abweichungen: " + String.join("; ", problems.subList(0, Math.min(5, problems.size())))));
    }

    /** alpha.42c: the server's messages.yml and messages_en.yml, every key in both, same placeholders. */
    private void messages() {
        de.lamamc.sift.Messages m = de.lamamc.sift.Messages.current();
        java.util.Map<String, String> german = m.all(de.lamamc.sift.core.lang.Lang.DE);
        java.util.Map<String, String> english = m.all(de.lamamc.sift.core.lang.Lang.EN);
        List<String> problems = de.lamamc.sift.core.lang.LangCheck.compare("messages.yml", german, "messages_en.yml", english);
        check(problems.isEmpty() && !german.isEmpty(), "Spielertexte: " + german.size() + " Keys Deutsch, " + english.size()
                + " Englisch" + (problems.isEmpty() ? ", alle in beiden Sprachen mit gleichen Platzhaltern"
                : ", Abweichungen: " + String.join("; ", problems.subList(0, Math.min(5, problems.size())))));
    }

    /** alpha.42d: the Charoite Wisp is registered with its rig, animations, names and is in the zone 3 mix. */
    /**
     * alpha.42h: every Sift advancement can be earned with the current config. Datapack criteria (other than "impossible")
     * need nothing; the plugin awards the others, each only while its feature is on. An advancement the plugin never
     * awards is an error; one whose feature is off (rifts) or missing is listed for a decision.
     */
    private void advancementSources() {
        var c = org.bukkit.plugin.java.JavaPlugin.getPlugin(de.lamamc.sift.SiftPlugin.class).getConfig();
        boolean mobs = c.getBoolean("mobs.enabled", true);
        java.util.Map<String, Boolean> byPlugin = new java.util.LinkedHashMap<>();
        byPlugin.put("the_sift/kinda_purpleish", true); // charoite ore broken
        byPlugin.put("the_sift/sifted_nugget", true); // siftite ore broken
        byPlugin.put("the_sift/spicewood", true); // wood lit in the Sift
        byPlugin.put("the_sift/better_enchanter", c.getBoolean("phase4.enchanter.enabled", true));
        byPlugin.put("the_sift/keep_inventory", c.getBoolean("siftite.keep-on-death.enabled", true));
        byPlugin.put("the_sift/false_alarm", c.getBoolean("ichor.enabled", true));
        byPlugin.put("the_sift/blub", mobs && c.getBoolean("mobs.blub.enabled", true));
        byPlugin.put("the_sift/food_chain", mobs && c.getBoolean("mobs.blub.enabled", true) && c.getBoolean("mobs.dark_sniffer.enabled", true));
        byPlugin.put("the_sift/like_father_and_son", mobs && c.getBoolean("mobs.echo_golem.enabled", true));
        byPlugin.put("the_sift/friend_of_the_sift", mobs && c.getBoolean("mobs.echo_golem.enabled", true));
        byPlugin.put("the_sift/pitch_perfect", mobs && c.getBoolean("mobs.singer.enabled", true));
        byPlugin.put("story/song_of_the_past", true); // goat horn at an Ancient City portal
        byPlugin.put("the_sift/great_echo_golem", c.getBoolean("phase4.bosses.great-echo-golem.enabled", true)
                && c.getBoolean("phase4.bosses.great-echo-golem.advancement", true));
        byPlugin.put("the_sift/rifter", c.getBoolean("rifts.enabled", false)); // through a rift
        java.util.Map<String, String> open = java.util.Map.of("the_sift/planting_the_dark",
                "Sculkblume auf Sculk pflanzen – Anbau der Sculkblume fehlt noch");
        int total = 0;
        List<String> unknown = new ArrayList<>();
        List<String> off = new ArrayList<>();
        for (Iterator<Advancement> it = Bukkit.advancementIterator(); it.hasNext(); ) {
            Advancement a = it.next();
            String path = a.getKey().getKey();
            if (!a.getKey().getNamespace().equals("the_sift") || path.startsWith("recipes/")) {
                continue;
            }
            total++;
            // getCriteria() is an unmodifiable view without equals(): compare as a set
            boolean datapack = !new java.util.HashSet<>(a.getCriteria()).equals(java.util.Set.of("event"));
            if (datapack) {
                continue;
            }
            Boolean on = byPlugin.get(path);
            if (open.containsKey(path)) {
                off.add(path + " (" + open.get(path) + ")");
            } else if (on == null) {
                unknown.add(path);
            } else if (!on) {
                off.add(path + " (Funktion aus)");
            }
        }
        check(unknown.isEmpty() && total > 0, "Advancements erreichbar: " + (total - unknown.size() - off.size()) + " von " + total
                + (unknown.isEmpty() ? "" : ", ohne Vergabe: " + String.join(", ", unknown)));
        if (!off.isEmpty()) {
            lines.add("HINWEIS mit dieser Config nicht erreichbar: " + String.join("; ", off));
        }
    }

    /**
     * alpha.42g: every Sift block item carries the block state component with exactly its registered state, so the client
     * predicts the Sift block when placing it (no vanilla mushroom block flashing up).
     */
    private void blockStates() {
        int states = 0;
        int ok = 0;
        int standIns = 0;
        List<String> wrong = new java.util.ArrayList<>();
        List<String> otherKind = new java.util.ArrayList<>();
        for (String id : items.ids()) {
            var def = items.registry().get(id);
            if (def == null || def.category() == null) {
                continue;
            }
            org.bukkit.block.data.BlockData state = items.placedState(id);
            if (state == null) {
                if (def.kind() == ItemKind.PLAIN && org.bukkit.Material.matchMaterial(def.baseItem()) != null
                        && org.bukkit.Material.matchMaterial(def.baseItem()).isBlock()) {
                    standIns++; // a vanilla block standing in (stairs, slabs, walls ...): the vanilla item places it
                }
                continue;
            }
            ItemStack stack = items.create(id, 1);
            if (stack.getType() != state.getPlacementMaterial()) {
                // ichor glass: chorus needs end stone below, so its item places glass; the client predicts glass
                otherKind.add(id + " (" + stack.getType().getKey().getKey() + ")");
                continue;
            }
            states++;
            var data = stack.getData(io.papermc.paper.datacomponent.DataComponentTypes.BLOCK_DATA);
            String placed = data == null ? null : data.createBlockData(state.getMaterial().asBlockType()).getAsString();
            if (state.getAsString().equals(placed) && stack.getType() == state.getPlacementMaterial()) {
                ok++;
            } else if (wrong.size() < 5) {
                wrong.add(id + " (" + (placed == null ? "ohne Komponente" : placed) + ")");
            }
        }
        check(ok == states && states > 0, "Block-Zustand im Item (Vorhersage beim Platzieren): " + ok + " von " + states
                + " Sift-Blöcken" + (wrong.isEmpty() ? "" : ", falsch: " + String.join(", ", wrong)) + "; " + standIns
                + " Vanilla-Ersatzblöcke ohne eigenen Zustand"
                + (otherKind.isEmpty() ? "" : "; mit anderem Item gesetzt (Vorhersage dieses Blocks): " + String.join(", ", otherKind)));
    }

    /** alpha.42f: the golem's numbers for 1 to 5 players and the texts of the one-altar rule in both languages. */
    private void bossBalance() {
        de.lamamc.sift.SiftPlugin plugin = org.bukkit.plugin.java.JavaPlugin.getPlugin(de.lamamc.sift.SiftPlugin.class);
        var kind = plugin.mobs() == null ? null : plugin.mobs().kind(de.lamamc.sift.mob.boss.GreatEchoGolemKind.ID);
        if (kind instanceof de.lamamc.sift.mob.boss.GreatEchoGolemKind golem) {
            StringBuilder s = new StringBuilder();
            boolean grows = true;
            double before = 0;
            for (int players = 1; players <= 5; players++) {
                double health = golem.settings().maxHealth(players);
                grows &= health >= before;
                before = health;
                s.append(players == 1 ? "" : ", ").append(players).append(": ").append(Math.round(health)).append(" LP ×")
                        .append(String.format(java.util.Locale.ROOT, "%.2f", golem.scaling().damageFactor(players))).append(" Schaden +")
                        .append(golem.scaling().extraHelpers(players)).append(" Helfer");
            }
            check(grows, "Großer Echo-Golem je Spieler – " + s);
        }
        var m = de.lamamc.sift.Messages.current();
        boolean texts = true;
        for (String key : List.of("temple.altar-chosen", "temple.altar-won", "temple.altar-won-here")) {
            for (var lang : de.lamamc.sift.core.lang.Lang.values()) {
                String raw = m.raw(lang, key);
                texts &= raw != null && !raw.isBlank() && !raw.equals(key);
            }
        }
        var temples = plugin.temples();
        check(texts, "Ein Altar je Spieler: " + (temples == null ? "Tempel aus" : (temples.altarOnePerPlayer() ? "an" : "aus") + ", "
                + temples.bindings().size() + " Bindungen") + "; Texte Deutsch und Englisch " + (texts ? "vorhanden" : "fehlen"));
    }

    /** alpha.42e: the planned temple (configured size) can be walked by a 3 x 3 x 3 body, zone 3 and the arena 4 high. */
    private void templeWalk(World world) {
        de.lamamc.sift.SiftPlugin plugin = org.bukkit.plugin.java.JavaPlugin.getPlugin(de.lamamc.sift.SiftPlugin.class);
        if (plugin.temples() == null) {
            check(true, "Tempel-Wege: Tempel aus, nicht geprüft");
            return;
        }
        var p = plugin.temples().builder().params();
        long seed = world.getSeed();
        var layout = de.lamamc.sift.core.temple.TempleLayout.build(seed, p);
        var report = de.lamamc.sift.core.temple.TempleWalk.check(de.lamamc.sift.core.temple.TempleWalk.grid(layout),
                de.lamamc.sift.core.temple.TempleWalk.grid(de.lamamc.sift.core.temple.TempleLayout.unweathered(seed, p)),
                new de.lamamc.sift.core.temple.TempleLayout.Pos(0, 0, p.radius() - 2), de.lamamc.sift.core.temple.TempleWalk.targets(layout),
                p.extent(), p.bottom() - 1, p.top());
        check(report.ok(), "Tempel-Wege (Layout-Version " + de.lamamc.sift.world.temple.TempleRegistry.CURRENT + ", Radius " + p.radius()
                + "): " + String.join("; ", report.lines()));
    }

    private void charoiteWisp() {
        de.lamamc.sift.SiftPlugin plugin = org.bukkit.plugin.java.JavaPlugin.getPlugin(de.lamamc.sift.SiftPlugin.class);
        var kind = plugin.mobs() == null ? null : plugin.mobs().kind(de.lamamc.sift.mob.wisp.CharoiteWispKind.ID);
        if (kind == null) {
            check(!plugin.getConfig().getBoolean("mobs.charoite_wisp.enabled", true) || !plugin.getConfig().getBoolean("mobs.enabled", true),
                    "Charoit-Irrlicht: nicht registriert (nur in Ordnung, wenn mobs.charoite_wisp.enabled oder mobs.enabled aus ist)");
            return;
        }
        int cubes = kind.assets().rig().cubes().size();
        boolean anims = kind.assets().animations().keySet().containsAll(List.of("idle", "attack", "blink"));
        java.util.Map<String, String> en = new java.util.HashMap<>();
        java.util.Map<String, String> german = new java.util.HashMap<>();
        Translations.read("/lang/lamakingdom_en_us.json", en);
        Translations.read("/lang/de_de.json", german);
        String key = "entity.the_sift.charoite_wisp";
        check(cubes >= 10 && anims && en.containsKey(key) && german.containsKey(key), "Charoit-Irrlicht: " + cubes + " Würfel, Animationen "
                + (anims ? "idle/attack/blink" : "fehlen") + ", Name „" + german.get(key) + "“ / „" + en.get(key) + "“");
        var zone = plugin.getConfig().getStringList("phase4.temples.spawners.zones.3.mobs");
        lines.add((zone.contains(de.lamamc.sift.mob.wisp.CharoiteWispKind.ID) ? "OK      " : "HINWEIS ")
                + "Brut-Zone 3: " + zone + (zone.contains(de.lamamc.sift.mob.wisp.CharoiteWispKind.ID) ? "" : " (ohne Irrlicht)"));
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

        // fill real chests through the server's loot path (LootGenerateEvent with all listeners, as when opened)
        chestLoot(Bukkit.getWorlds().getFirst(), NamespacedKey.minecraft("chests/simple_dungeon"), false);
        chestLoot(world, new NamespacedKey("the_sift", "chests/abandoned_portal"), true);
    }

    /** Places a chest high above spawn, fills it from the table, checks the content and restores the block. */
    private void chestLoot(World world, NamespacedKey key, boolean siftTable) {
        LootTable table = Bukkit.getLootTable(key);
        if (table == null) {
            check(false, "Loot-Tabelle " + key + " fehlt");
            return;
        }
        Location spawn = world.getSpawnLocation();
        Block block = world.getBlockAt(spawn.getBlockX(), world.getMaxHeight() - 2, spawn.getBlockZ());
        BlockData before = block.getBlockData();
        int stacks = 0;
        int siftStacks = 0;
        int complete = 0;
        try {
            block.setType(Material.CHEST, false);
            if (!(block.getState(false) instanceof Chest chest)) {
                check(false, "Truhe für " + key + " ließ sich nicht setzen");
                return;
            }
            Inventory inventory = chest.getBlockInventory();
            inventory.clear();
            Random random = new Random(7);
            for (int round = 0; round < 5 && stacks == 0; round++) {
                table.fillInventory(inventory, random, new LootContext.Builder(block.getLocation()).build());
                for (ItemStack stack : inventory.getContents()) {
                    if (stack != null && !stack.isEmpty()) {
                        stacks++;
                        String id = SiftItems.idOf(stack);
                        if (id != null) {
                            siftStacks++;
                            if (stack.getData(DataComponentTypes.ITEM_NAME) != null) {
                                complete++;
                            }
                        }
                    }
                }
            }
            inventory.clear();
        } finally {
            block.setBlockData(before, false);
        }
        check(stacks > 0, "Truhe mit " + key + " gefüllt: " + stacks + " Stapel (darf nicht leer sein)");
        if (siftTable) {
            check(complete == siftStacks, "Truhe mit " + key + ": " + complete + " von " + siftStacks + " Sift-Items vervollständigt");
        }
    }
}
