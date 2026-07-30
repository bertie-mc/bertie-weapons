package com.berlord.bertieweapons.recipe;

import com.berlord.bertieweapons.logic.WeaponUpgrades;
import java.util.Optional;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;

/**
 * Weapon + one catalyst from {@code #bertie_weapons:stat_catalysts} = one flat base-damage tier.
 * The test build stocks that tag with a diamond; real tiers get their own materials later.
 */
public class StatUpgradeRecipe extends UpgradeCraftingRecipe {

    public StatUpgradeRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Override
    protected boolean isCatalyst(ItemStack stack, HolderLookup.Provider registries) {
        return WeaponUpgrades.isStatCatalyst(stack);
    }

    @Override
    protected Optional<ItemStack> upgrade(
            ItemStack weapon, ItemStack catalyst, HolderLookup.Provider registries) {
        return WeaponUpgrades.applyStatTier(weapon, registries);
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return BertieRecipeSerializers.STAT_UPGRADE.get();
    }
}
