package com.berlord.bertieweapons.recipe;

import com.berlord.bertieweapons.logic.WeaponUpgrades;
import java.util.Optional;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.level.Level;

/**
 * Shared shape for both upgrade crafts: exactly one upgradable weapon plus exactly one catalyst,
 * anywhere in the grid, in any arrangement.
 *
 * <p>These are {@link CustomRecipe}s on {@code RecipeType.CRAFTING} rather than data-driven shaped
 * recipes, because the result depends on the input weapon's current tier state - there is no fixed
 * output stack to put in a JSON. Being ordinary crafting recipes means every station that runs the
 * vanilla crafting registry picks them up for free; Malum's Spirit Altar and Hephaestus Forge use
 * their own recipe types and will each need a thin adapter that calls the same
 * {@link WeaponUpgrades} entry points.
 */
public abstract class UpgradeCraftingRecipe extends CustomRecipe {

    protected UpgradeCraftingRecipe(CraftingBookCategory category) {
        super(category);
    }

    /** True if this catalyst is the kind the subclass consumes. */
    protected abstract boolean isCatalyst(ItemStack stack, HolderLookup.Provider registries);

    /** The upgraded weapon, or empty if the transition is not legal (capped, slot taken, ...). */
    protected abstract Optional<ItemStack> upgrade(
            ItemStack weapon, ItemStack catalyst, HolderLookup.Provider registries);

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return assembleFrom(input, level.registryAccess()).isPresent();
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        return assembleFrom(input, registries).orElse(ItemStack.EMPTY);
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    /**
     * Single source of truth for both {@code matches} and {@code assemble} - computing the result
     * twice risks the two disagreeing, which shows up as a ghost output that vanishes on pickup.
     */
    private Optional<ItemStack> assembleFrom(CraftingInput input, HolderLookup.Provider registries) {
        ItemStack weapon = ItemStack.EMPTY;
        ItemStack catalyst = ItemStack.EMPTY;

        for (int slot = 0; slot < input.size(); slot++) {
            ItemStack stack = input.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            if (WeaponUpgrades.isUpgradable(stack)) {
                if (!weapon.isEmpty()) {
                    return Optional.empty();
                }
                weapon = stack;
            } else if (isCatalyst(stack, registries)) {
                if (!catalyst.isEmpty()) {
                    return Optional.empty();
                }
                catalyst = stack;
            } else {
                // Anything else in the grid disqualifies the craft outright.
                return Optional.empty();
            }
        }

        if (weapon.isEmpty() || catalyst.isEmpty()) {
            return Optional.empty();
        }
        return upgrade(weapon, catalyst, registries);
    }
}
