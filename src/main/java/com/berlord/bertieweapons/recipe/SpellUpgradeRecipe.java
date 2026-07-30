package com.berlord.bertieweapons.recipe;

import com.berlord.bertieweapons.logic.WeaponUpgrades;
import io.redspace.ironsspellbooks.item.armor.UpgradeOrbType;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;

/**
 * Weapon + one Iron's elemental upgrade orb = that element's slot filled in the tier currently
 * being worked on. Filling the last slot converts the ring into one generic spell-power tier.
 *
 * <p>Which orbs count is read from the configured ring rather than from the item, so an orb that
 * exists but is not part of the ring (mana, cooldown, spell resistance) is simply not a catalyst
 * here and the craft does not match.
 */
public class SpellUpgradeRecipe extends UpgradeCraftingRecipe {

    public SpellUpgradeRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Override
    protected boolean isCatalyst(ItemStack stack, HolderLookup.Provider registries) {
        Optional<ResourceKey<UpgradeOrbType>> orb = WeaponUpgrades.orbTypeOf(stack);
        if (orb.isEmpty()) {
            return false;
        }
        List<String> ring = WeaponUpgrades.elementRing(registries);
        return ring.contains(orb.get().location().toString());
    }

    @Override
    protected Optional<ItemStack> upgrade(
            ItemStack weapon, ItemStack catalyst, HolderLookup.Provider registries) {
        return WeaponUpgrades.orbTypeOf(catalyst)
                .flatMap(orb -> WeaponUpgrades.applyElement(weapon, orb, registries));
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return BertieRecipeSerializers.SPELL_UPGRADE.get();
    }
}
