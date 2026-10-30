package de.lamamc.sift.item;

import de.lamamc.sift.core.SiftiteStats;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Consumable;
import io.papermc.paper.datacomponent.item.Equippable;
import io.papermc.paper.datacomponent.item.FoodProperties;
import io.papermc.paper.datacomponent.item.ItemAttributeModifiers;
import io.papermc.paper.datacomponent.item.ItemLore;
import io.papermc.paper.datacomponent.item.JukeboxPlayable;
import io.papermc.paper.datacomponent.item.PotionContents;
import io.papermc.paper.datacomponent.item.Repairable;
import io.papermc.paper.datacomponent.item.Tool;
import io.papermc.paper.datacomponent.item.UseRemainder;
import io.papermc.paper.datacomponent.item.consumable.ItemUseAnimation;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.TypedKey;
import io.papermc.paper.registry.set.RegistrySet;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.JukeboxSong;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.ItemRarity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Sets the components that make a Sift item behave like the original, by {@link ItemKind}. The netherite
 * base items keep everything Siftite shares with netherite (armor points, attack speed, fire resistance,
 * enchantability, trims); only the configured values change.
 */
final class ItemComponents {

    static final Key SIFTITE_EQUIPMENT = Key.key("the_sift", "siftite");
    static final Key SIFTITE_EQUIP_SOUND = Key.key("the_sift", "item.siftite_armor.equip");
    /** The item Siftite gear is repaired with on the anvil (the guard admits only the real Sift item). */
    static final String REPAIR_ITEM = "siftite_ingot";

    private final ItemSettings settings;
    private final ItemRegistry registry;
    private final Translations translations;

    ItemComponents(ItemSettings settings, ItemRegistry registry, Translations translations) {
        this.settings = settings;
        this.registry = registry;
        this.translations = translations;
    }

    void apply(ItemDefinition def, ItemStack stack) {
        switch (def.kind()) {
            case PLAIN -> {
            }
            case SIFTITE_TOOL -> siftiteTool(stack);
            case SIFTITE_ARMOR -> siftiteArmor(def.id(), stack);
            case SMITHING_TEMPLATE -> smithingTemplate(stack);
            case FOOD -> food(def.id(), stack);
            case DRINK -> drink(def.id(), stack);
            case MUSIC_DISC -> musicDisc(def.id(), stack);
            case PORTAL_KEY -> {
                stack.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
                stack.setData(DataComponentTypes.MAX_STACK_SIZE, 16);
            }
            case SIFT_COMPASS -> {
                stack.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
                stack.setData(DataComponentTypes.MAX_STACK_SIZE, 1); // each compass keeps its own target
            }
        }
    }

    private void siftiteTool(ItemStack stack) {
        SiftiteStats stats = settings.siftite();
        stack.setData(DataComponentTypes.MAX_DAMAGE, stats.toolDurability());
        Tool base = stack.getData(DataComponentTypes.TOOL);
        if (base != null) {
            List<Tool.Rule> rules = new ArrayList<>();
            base.rules().forEach(rule -> rules.add(Tool.rule(rule.blocks(),
                    rule.speed() == null ? null : stats.miningSpeedFor(rule.speed()), rule.correctForDrops())));
            stack.setData(DataComponentTypes.TOOL, Tool.tool()
                    .defaultMiningSpeed(base.defaultMiningSpeed())
                    .damagePerBlock(base.damagePerBlock())
                    .canDestroyBlocksInCreative(base.canDestroyBlocksInCreative())
                    .addRules(rules)
                    .build());
        }
        editModifiers(stack, (attribute, amount) ->
                attribute == Attribute.ATTACK_DAMAGE ? stats.attackDamageModifier(amount) : amount);
        repairWithSiftite(stack);
    }

    private void siftiteArmor(String id, ItemStack stack) {
        SiftiteStats stats = settings.siftite();
        stack.setData(DataComponentTypes.MAX_DAMAGE, stats.armorDurability(id));
        editModifiers(stack, (attribute, amount) -> {
            if (attribute == Attribute.ARMOR_TOUGHNESS) {
                return stats.armorToughness();
            }
            if (attribute == Attribute.KNOCKBACK_RESISTANCE) {
                return stats.knockbackResistance();
            }
            return amount;
        });
        Equippable base = stack.getData(DataComponentTypes.EQUIPPABLE);
        if (base != null) {
            Equippable.Builder equippable = Equippable.equippable(base.slot())
                    .assetId(SIFTITE_EQUIPMENT)
                    .equipSound(SIFTITE_EQUIP_SOUND)
                    .dispensable(base.dispensable())
                    .swappable(base.swappable())
                    .damageOnHurt(base.damageOnHurt())
                    .equipOnInteract(base.equipOnInteract())
                    .canBeSheared(base.canBeSheared());
            if (base.cameraOverlay() != null) {
                equippable.cameraOverlay(base.cameraOverlay());
            }
            if (base.allowedEntities() != null) {
                equippable.allowedEntities(base.allowedEntities());
            }
            if (base.shearSound() != null) {
                equippable.shearSound(base.shearSound());
            }
            stack.setData(DataComponentTypes.EQUIPPABLE, equippable.build());
        }
        repairWithSiftite(stack);
    }

    /** Replaces modifier amounts of the base item; key, operation, slot and display stay. */
    private static void editModifiers(ItemStack stack, ModifierEdit edit) {
        ItemAttributeModifiers base = stack.getData(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        if (base == null) {
            return;
        }
        ItemAttributeModifiers.Builder modifiers = ItemAttributeModifiers.itemAttributes();
        for (ItemAttributeModifiers.Entry entry : base.modifiers()) {
            AttributeModifier m = entry.modifier();
            double amount = edit.amount(entry.attribute(), m.getAmount());
            modifiers.addModifier(entry.attribute(),
                    new AttributeModifier(m.getKey(), amount, m.getOperation(), m.getSlotGroup()),
                    entry.getGroup(), entry.display());
        }
        stack.setData(DataComponentTypes.ATTRIBUTE_MODIFIERS, modifiers.build());
    }

    @FunctionalInterface
    private interface ModifierEdit {
        double amount(Attribute attribute, double amount);
    }

    private void repairWithSiftite(ItemStack stack) {
        ItemDefinition repair = registry.get(REPAIR_ITEM);
        if (repair == null) {
            return;
        }
        stack.setData(DataComponentTypes.REPAIRABLE, Repairable.repairable(RegistrySet.keySet(RegistryKey.ITEM,
                TypedKey.create(RegistryKey.ITEM, Key.key(repair.baseItem())))));
    }

    /** The tooltip of vanilla smithing templates, which a plain item does not get by itself. */
    private void smithingTemplate(ItemStack stack) {
        stack.setData(DataComponentTypes.RARITY, ItemRarity.UNCOMMON);
        stack.setData(DataComponentTypes.LORE, ItemLore.lore(List.of(
                line("item.minecraft.smithing_template", "Schmiedevorlage", NamedTextColor.GRAY),
                Component.empty(),
                line("item.minecraft.smithing_template.applies_to", "Anwendbar auf:", NamedTextColor.GRAY),
                Component.space().append(line("item.the_sift.smithing_template.siftite_upgrade.applies_to",
                        null, NamedTextColor.BLUE)).decoration(TextDecoration.ITALIC, false),
                line("item.minecraft.smithing_template.ingredients", "Zutaten:", NamedTextColor.GRAY),
                Component.space().append(line("item.the_sift.smithing_template.siftite_upgrade.ingredients",
                        null, NamedTextColor.BLUE)).decoration(TextDecoration.ITALIC, false))));
    }

    private Component line(String key, String fallback, NamedTextColor color) {
        String text = fallback != null ? fallback : translations.german(key);
        return Component.translatable().key(key).fallback(text).color(color)
                .decoration(TextDecoration.ITALIC, false).build();
    }

    private void food(String id, ItemStack stack) {
        ItemSettings.Food food = settings.food().get(id);
        if (food == null) {
            throw new IllegalStateException("config.yml: items.food." + id + " is missing");
        }
        stack.setData(DataComponentTypes.FOOD, FoodProperties.food()
                .nutrition(food.nutrition()).saturation(food.saturation()).build());
        stack.setData(DataComponentTypes.CONSUMABLE, Consumable.consumable()
                .consumeSeconds(1.6f).animation(ItemUseAnimation.EAT)
                .sound(Key.key("minecraft", "entity.generic.eat")).hasConsumeParticles(true).build());
    }

    /** Like the original: potion contents applied on drinking, a glass bottle stays behind. */
    private void drink(String id, ItemStack stack) {
        ItemSettings.Drink drink = settings.drinks().get(id);
        if (drink == null) {
            throw new IllegalStateException("config.yml: items.drinks." + id + " is missing");
        }
        PotionContents.Builder potion = PotionContents.potionContents()
                .customColor(Color.fromRGB(Integer.parseInt(drink.color().replace("#", ""), 16)));
        for (ItemSettings.Effect effect : drink.effects()) {
            PotionEffectType type = Registry.MOB_EFFECT.get(NamespacedKey.minecraft(effect.type()));
            if (type == null) {
                throw new IllegalStateException("config.yml: unknown effect " + effect.type() + " for " + id);
            }
            potion.addCustomEffect(new PotionEffect(type, effect.duration(), effect.amplifier()));
        }
        stack.setData(DataComponentTypes.POTION_CONTENTS, potion.build());
        stack.setData(DataComponentTypes.CONSUMABLE, Consumable.consumable()
                .consumeSeconds(1.6f).animation(ItemUseAnimation.DRINK)
                .sound(Key.key("minecraft", "entity.generic.drink")).hasConsumeParticles(false).build());
        stack.setData(DataComponentTypes.USE_REMAINDER, UseRemainder.useRemainder(ItemStack.of(org.bukkit.Material.GLASS_BOTTLE)));
        stack.setData(DataComponentTypes.MAX_STACK_SIZE, 1);
    }

    private void musicDisc(String id, ItemStack stack) {
        String song = settings.musicDiscs().get(id);
        if (song == null) {
            throw new IllegalStateException("config.yml: items.music-discs." + id + " is missing");
        }
        JukeboxSong jukeboxSong = RegistryAccess.registryAccess().getRegistry(RegistryKey.JUKEBOX_SONG).get(Key.key(song));
        if (jukeboxSong == null) {
            throw new IllegalStateException("Jukebox song " + song + " is not registered (datapack missing?)");
        }
        stack.setData(DataComponentTypes.JUKEBOX_PLAYABLE, JukeboxPlayable.jukeboxPlayable(jukeboxSong).build());
        stack.setData(DataComponentTypes.MAX_STACK_SIZE, 1);
        stack.setData(DataComponentTypes.RARITY, ItemRarity.RARE);
    }
}
