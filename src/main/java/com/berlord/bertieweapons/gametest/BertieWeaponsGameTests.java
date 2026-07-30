package com.berlord.bertieweapons.gametest;

import com.berlord.bertieweapons.Config;
import com.berlord.bertieweapons.logic.TierState;
import com.berlord.bertieweapons.logic.WeaponUpgrades;
import io.redspace.ironsspellbooks.api.item.UpgradeData;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.enchantment.Enchantments;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Integration proof against a real server: real registries, the real datapack entries this mod
 * ships, the real recipe manager and the real attribute pipeline.
 *
 * <p>The unit tests pin the arithmetic; these pin the parts that only exist at runtime - that our
 * two {@code upgrade_orb_type} JSON files actually parsed, that the crafting recipes are findable
 * and produce what {@link WeaponUpgrades} produces, and that Iron's really does turn our stored
 * counts into attack-damage and spell-power modifiers on a sword.
 */
@GameTestHolder("bertie_weapons")
@PrefixGameTestTemplate(false)
public final class BertieWeaponsGameTests {
    private static final String TEMPLATE = "empty";

    private BertieWeaponsGameTests() {}

    /** A Simply Swords unique, which is what {@code #bertie_weapons:upgradable_weapons} covers. */
    private static ItemStack weapon(GameTestHelper helper) {
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse("simplyswords:soulkeeper"));
        ItemStack stack = new ItemStack(item);
        if (!WeaponUpgrades.isUpgradable(stack)) {
            helper.fail("simplyswords:soulkeeper is not in #bertie_weapons:upgradable_weapons - "
                    + "either Simply Swords is absent from the run or the tag did not resolve");
        }
        return stack;
    }

    private static HolderLookup.Provider registries(GameTestHelper helper) {
        return helper.getLevel().registryAccess();
    }

    // ---- datapack + registry ---------------------------------------------------------------

    @GameTest(template = TEMPLATE)
    public static void ourUpgradeOrbTypesLoaded(GameTestHelper helper) {
        HolderLookup.Provider registries = registries(helper);
        HolderLookup.RegistryLookup<io.redspace.ironsspellbooks.item.armor.UpgradeOrbType> lookup =
                registries.lookupOrThrow(WeaponUpgrades.UPGRADE_ORB_REGISTRY);

        if (lookup.get(WeaponUpgrades.STAT_TIER).isEmpty()) {
            helper.fail("bertie_weapons:weapon_power did not load from our datapack");
        }
        if (lookup.get(WeaponUpgrades.SPELL_TIER).isEmpty()) {
            helper.fail("bertie_weapons:spell_tier did not load from our datapack");
        }
        // The shipped default ring must resolve in full against a pack that has Iron's.
        List<String> ring = WeaponUpgrades.elementRing(registries);
        if (ring.size() != 8) {
            helper.fail("expected the 8 Iron's elemental orbs in the ring, got " + ring);
        }
        helper.succeed();
    }

    // ---- stat track ------------------------------------------------------------------------

    @GameTest(template = TEMPLATE)
    public static void statTierAddsPercentOfBaseDamage(GameTestHelper helper) {
        HolderLookup.Provider registries = registries(helper);
        ItemStack base = weapon(helper);
        double baseDamage = attackDamageAddValue(base);

        ItemStack upgraded = WeaponUpgrades.applyStatTier(base, registries)
                .orElseGet(() -> {
                    helper.fail("first stat tier was refused");
                    return ItemStack.EMPTY;
                });

        if (WeaponUpgrades.readState(upgraded, List.of()).statTier() != 1) {
            helper.fail("stat tier did not reach 1");
        }
        // Iron's applies the stored count via ItemAttributeModifierEvent; ADD_MULTIPLIED_BASE at
        // 0.20 x 1 is what our weapon_power.json asks for.
        double multiplied = attackDamageMultipliedBase(upgraded);
        if (Math.abs(multiplied - 0.20) > 1.0e-6) {
            helper.fail("expected a +0.20 ADD_MULTIPLIED_BASE attack damage modifier, got " + multiplied);
        }
        // The flat base must be untouched - our tier multiplies it, it does not replace it.
        if (Math.abs(attackDamageAddValue(upgraded) - baseDamage) > 1.0e-6) {
            helper.fail("base attack damage changed: " + baseDamage + " -> " + attackDamageAddValue(upgraded));
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void statTiersStackAdditivelyToTheCap(GameTestHelper helper) {
        HolderLookup.Provider registries = registries(helper);
        ItemStack stack = weapon(helper);
        int cap = Config.MAX_STAT_TIER.get();
        for (int i = 0; i < cap; i++) {
            stack = WeaponUpgrades.applyStatTier(stack, registries).orElse(ItemStack.EMPTY);
            if (stack.isEmpty()) {
                helper.fail("stat tier " + (i + 1) + " was refused before the cap");
                return;
            }
        }
        double multiplied = attackDamageMultipliedBase(stack);
        double expected = 0.20 * cap;
        if (Math.abs(multiplied - expected) > 1.0e-6) {
            helper.fail("expected +" + expected + " at the cap (additive, not compounding), got " + multiplied);
        }
        if (WeaponUpgrades.applyStatTier(stack, registries).isPresent()) {
            helper.fail("stat track went past its cap of " + cap);
        }
        helper.succeed();
    }

    // ---- spell track -----------------------------------------------------------------------

    @GameTest(template = TEMPLATE)
    public static void closingTheRingConvertsToGenericSpellPower(GameTestHelper helper) {
        HolderLookup.Provider registries = registries(helper);
        List<String> ring = WeaponUpgrades.elementRing(registries);
        ItemStack stack = weapon(helper);

        for (int i = 0; i < ring.size(); i++) {
            String element = ring.get(i);
            stack = WeaponUpgrades.applyElement(stack, orbKey(element), registries).orElse(ItemStack.EMPTY);
            if (stack.isEmpty()) {
                helper.fail("element " + element + " was refused");
                return;
            }
            TierState state = WeaponUpgrades.readState(stack, ring);
            boolean last = i == ring.size() - 1;
            if (last) {
                if (state.spellTier() != 1 || !state.filledElements().isEmpty()) {
                    helper.fail("closing the ring did not convert: " + state);
                }
            } else if (state.spellTier() != 0 || state.filledElements().size() != i + 1) {
                helper.fail("ring filled wrongly at step " + i + ": " + state);
            }
        }

        // After conversion the elemental modifiers must be gone and generic spell power present.
        double generic = modifierAmount(stack, "irons_spellbooks:spell_power");
        if (Math.abs(generic - 0.05) > 1.0e-6) {
            helper.fail("expected +0.05 generic spell power after one tier, got " + generic);
        }
        double fire = modifierAmount(stack, "irons_spellbooks:fire_spell_power");
        if (fire != 0.0) {
            helper.fail("elemental spell power should have been consumed by the conversion, got " + fire);
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void anElementIsRefusedTwiceInOneTier(GameTestHelper helper) {
        HolderLookup.Provider registries = registries(helper);
        List<String> ring = WeaponUpgrades.elementRing(registries);
        ItemStack stack = WeaponUpgrades
                .applyElement(weapon(helper), orbKey(ring.get(0)), registries)
                .orElse(ItemStack.EMPTY);
        if (stack.isEmpty()) {
            helper.fail("first element was refused");
            return;
        }
        if (WeaponUpgrades.applyElement(stack, orbKey(ring.get(0)), registries).isPresent()) {
            helper.fail("the same element was accepted twice within one tier");
        }
        helper.succeed();
    }

    // ---- the non-destructive guarantee ------------------------------------------------------

    @GameTest(template = TEMPLATE)
    public static void upgradingPreservesEverythingElse(GameTestHelper helper) {
        HolderLookup.Provider registries = registries(helper);
        MinecraftServer server = helper.getLevel().getServer();

        ItemStack stack = weapon(helper);
        stack.enchant(
                server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SHARPNESS),
                3);
        stack.setDamageValue(17);
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                net.minecraft.network.chat.Component.literal("Berlord's Toothpick"));

        ItemStack upgraded = WeaponUpgrades.applyStatTier(stack, registries).orElse(ItemStack.EMPTY);
        if (upgraded.isEmpty()) {
            helper.fail("stat tier was refused");
            return;
        }
        // One more pass through the other track, to prove the two do not clobber each other.
        List<String> ring = WeaponUpgrades.elementRing(registries);
        upgraded = WeaponUpgrades.applyElement(upgraded, orbKey(ring.get(0)), registries).orElse(ItemStack.EMPTY);
        if (upgraded.isEmpty()) {
            helper.fail("element was refused after a stat tier");
            return;
        }

        if (upgraded.getEnchantments().getLevel(
                server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SHARPNESS)) != 3) {
            helper.fail("enchantment was lost");
        }
        if (upgraded.getDamageValue() != 17) {
            helper.fail("durability was reset: " + upgraded.getDamageValue());
        }
        if (upgraded.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME) == null) {
            helper.fail("custom name was lost");
        }
        TierState state = WeaponUpgrades.readState(upgraded, ring);
        if (state.statTier() != 1 || state.filledElements().size() != 1) {
            helper.fail("the two tracks interfered: " + state);
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void unrelatedIronsUpgradesSurvive(GameTestHelper helper) {
        HolderLookup.Provider registries = registries(helper);
        HolderLookup.RegistryLookup<io.redspace.ironsspellbooks.item.armor.UpgradeOrbType> lookup =
                registries.lookupOrThrow(WeaponUpgrades.UPGRADE_ORB_REGISTRY);
        Holder<io.redspace.ironsspellbooks.item.armor.UpgradeOrbType> cooldown = lookup
                .get(net.minecraft.resources.ResourceKey.create(
                        WeaponUpgrades.UPGRADE_ORB_REGISTRY,
                        ResourceLocation.parse("irons_spellbooks:cooldown")))
                .orElseThrow();

        ItemStack stack = weapon(helper);
        UpgradeData.set(stack, new UpgradeData(java.util.Map.of(cooldown, 2), EquipmentSlot.MAINHAND.getName()));

        ItemStack upgraded = WeaponUpgrades.applyStatTier(stack, registries).orElse(ItemStack.EMPTY);
        if (upgraded.isEmpty()) {
            helper.fail("stat tier was refused");
            return;
        }
        Integer kept = UpgradeData.getUpgradeData(upgraded).upgrades().get(cooldown);
        if (kept == null || kept != 2) {
            helper.fail("an Iron's upgrade this mod does not manage was dropped: " + kept);
        }
        helper.succeed();
    }

    // ---- the actual crafting path -----------------------------------------------------------

    @GameTest(template = TEMPLATE)
    public static void statCraftMatchesInACraftingGrid(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        CraftingInput input = CraftingInput.of(2, 1, List.of(weapon(helper), new ItemStack(Items.DIAMOND)));

        Optional<RecipeHolder<net.minecraft.world.item.crafting.CraftingRecipe>> found =
                server.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel());
        if (found.isEmpty()) {
            helper.fail("no crafting recipe matched weapon + diamond");
            return;
        }
        ResourceLocation id = found.get().id();
        if (!id.equals(ResourceLocation.parse("bertie_weapons:stat_upgrade"))) {
            helper.fail("a different recipe won the match: " + id);
            return;
        }
        ItemStack result = found.get().value().assemble(input, server.registryAccess());
        if (WeaponUpgrades.readState(result, List.of()).statTier() != 1) {
            helper.fail("the crafted result carries no stat tier");
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void spellCraftMatchesAndRejectsOffRingOrbs(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();

        ItemStack fireOrb = new ItemStack(
                BuiltInRegistries.ITEM.get(ResourceLocation.parse("irons_spellbooks:fire_upgrade_orb")));
        CraftingInput good = CraftingInput.of(2, 1, List.of(weapon(helper), fireOrb));
        if (server.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, good, helper.getLevel()).isEmpty()) {
            helper.fail("weapon + fire upgrade orb did not match");
            return;
        }

        // Cooldown is a real orb but deliberately not part of the ring, so it must not craft.
        ItemStack cooldownOrb = new ItemStack(
                BuiltInRegistries.ITEM.get(ResourceLocation.parse("irons_spellbooks:cooldown_upgrade_orb")));
        CraftingInput bad = CraftingInput.of(2, 1, List.of(weapon(helper), cooldownOrb));
        if (server.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, bad, helper.getLevel()).isPresent()) {
            helper.fail("an orb outside the ring was accepted");
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void nonUniqueWeaponsAreNotUpgradable(GameTestHelper helper) {
        ItemStack plain = new ItemStack(
                BuiltInRegistries.ITEM.get(ResourceLocation.parse("simplyswords:iron_longsword")));
        if (WeaponUpgrades.isUpgradable(plain)) {
            helper.fail("a non-unique Simply Swords weapon is in the upgradable tag");
        }
        if (WeaponUpgrades.isUpgradable(new ItemStack(Items.DIAMOND_SWORD))) {
            helper.fail("a vanilla sword is in the upgradable tag");
        }
        helper.succeed();
    }

    // ---- helpers ----------------------------------------------------------------------------

    private static net.minecraft.resources.ResourceKey<io.redspace.ironsspellbooks.item.armor.UpgradeOrbType>
            orbKey(String id) {
        return net.minecraft.resources.ResourceKey.create(
                WeaponUpgrades.UPGRADE_ORB_REGISTRY, ResourceLocation.parse(id));
    }

    private static double attackDamageAddValue(ItemStack stack) {
        return modifierAmount(stack, "minecraft:generic.attack_damage", AttributeModifier.Operation.ADD_VALUE);
    }

    private static double attackDamageMultipliedBase(ItemStack stack) {
        return modifierAmount(
                stack, "minecraft:generic.attack_damage", AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
    }

    private static double modifierAmount(ItemStack stack, String attributeId) {
        return modifierAmount(stack, attributeId, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
    }

    /**
     * Sums modifiers for one attribute/operation as the game sees them - via
     * {@code getAttributeModifiers}, which fires {@code ItemAttributeModifierEvent} and therefore
     * runs Iron's upgrade handler. This is the path a real player's damage goes through.
     */
    private static double modifierAmount(
            ItemStack stack, String attributeId, AttributeModifier.Operation operation) {
        Holder<Attribute> attribute = BuiltInRegistries.ATTRIBUTE
                .getHolder(ResourceLocation.parse(attributeId))
                .orElseThrow();
        ItemAttributeModifiers modifiers = stack.getAttributeModifiers();
        double total = 0.0;
        for (ItemAttributeModifiers.Entry entry : modifiers.modifiers()) {
            if (entry.attribute().equals(attribute)
                    && entry.modifier().operation() == operation
                    && entry.slot().test(EquipmentSlot.MAINHAND)) {
                total += entry.modifier().amount();
            }
        }
        return total;
    }
}
