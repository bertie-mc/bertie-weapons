package com.berlord.bertieweapons.logic;

import com.berlord.bertieweapons.BertieWeapons;
import com.berlord.bertieweapons.Config;
import com.mojang.logging.LogUtils;
import io.redspace.ironsspellbooks.api.item.UpgradeData;
import io.redspace.ironsspellbooks.item.armor.UpgradeOrbType;
import io.redspace.ironsspellbooks.registries.ComponentRegistry;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

/**
 * Translates between Iron's {@code upgrade_data} component and {@link TierState}.
 *
 * <p>Storage is entirely Iron's: our tiers are ordinary entries in their
 * {@code irons_spellbooks:upgrade_orb_type} datapack registry, and the count that component already
 * keeps per entry <em>is</em> the tier number. That buys attribute application, tooltips, network
 * sync and Shriving-Stone handling for free - {@code ServerPlayerEvents.handleUpgradeModifiers}
 * applies upgrade attributes on {@code ItemAttributeModifierEvent} with no item-type check, so a
 * sword works the same as a chestplate.
 *
 * <p>Every write goes through {@link #withState}, which starts from {@code input.copy()} and only
 * ever rewrites the keys this mod owns. Enchantments, durability, Apotheosis affixes, spell
 * containers and any unrelated Iron's upgrades survive untouched - that is the non-destructive
 * guarantee, and it is a property of the code path rather than of any individual recipe.
 */
public final class WeaponUpgrades {
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Mirror of {@code UpgradeOrbTypeRegistry.UPGRADE_ORB_REGISTRY_KEY}. Built here rather than
     * referenced so we never trigger Iron's registry class-init from our own; a rename upstream
     * surfaces as the loud lookup failure in {@link #orbRegistry}.
     */
    public static final ResourceKey<Registry<UpgradeOrbType>> UPGRADE_ORB_REGISTRY =
            ResourceKey.createRegistryKey(
                    ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "upgrade_orb_type"));

    /** Our own two tier entries, shipped as datapack JSON under {@code data/bertie_weapons/}. */
    public static final ResourceKey<UpgradeOrbType> STAT_TIER =
            ResourceKey.create(UPGRADE_ORB_REGISTRY, BertieWeapons.id("weapon_power"));
    public static final ResourceKey<UpgradeOrbType> SPELL_TIER =
            ResourceKey.create(UPGRADE_ORB_REGISTRY, BertieWeapons.id("spell_tier"));

    /** What may be upgraded at all. Datapack-editable; defaults to the Simply Swords uniques. */
    public static final TagKey<Item> UPGRADABLE_WEAPONS =
            ItemTags.create(BertieWeapons.id("upgradable_weapons"));

    /** What pays for a stat tier. One tag for the test build; per-tier costs come later. */
    public static final TagKey<Item> STAT_CATALYSTS =
            ItemTags.create(BertieWeapons.id("stat_catalysts"));

    private WeaponUpgrades() {}

    // ---- queries -------------------------------------------------------------------------

    public static boolean isUpgradable(ItemStack stack) {
        return !stack.isEmpty() && stack.is(UPGRADABLE_WEAPONS);
    }

    public static boolean isStatCatalyst(ItemStack stack) {
        return !stack.isEmpty() && stack.is(STAT_CATALYSTS);
    }

    /** The orb type an Iron's upgrade orb carries, if this stack is one. */
    public static Optional<ResourceKey<UpgradeOrbType>> orbTypeOf(ItemStack stack) {
        return Optional.ofNullable(stack.get(ComponentRegistry.UPGRADE_ORB_TYPE));
    }

    /**
     * The configured ring, resolved against the live registry. IDs that do not resolve - an orb
     * from a mod that is not installed - are dropped with a warning rather than failing the craft,
     * which keeps the config portable across pack variants.
     *
     * <p>Memoised on the registry instance and the raw config list. {@code matches} runs for every
     * crafting-grid change, so without the cache this would re-resolve eight IDs per stack per
     * keystroke and, worse, re-log the same warning every time. A datapack reload hands us a fresh
     * lookup instance, which is what invalidates the cache.
     */
    public static List<String> elementRing(HolderLookup.Provider registries) {
        Optional<HolderLookup.RegistryLookup<UpgradeOrbType>> lookup = orbRegistry(registries);
        if (lookup.isEmpty()) {
            return List.of();
        }
        List<? extends String> raw = Config.ELEMENT_ORB_TYPES.get();
        CachedRing cached = ringCache;
        if (cached != null && cached.lookup() == lookup.get() && cached.raw().equals(raw)) {
            return cached.ring();
        }
        List<String> ring = resolveRing(raw, lookup.get());
        ringCache = new CachedRing(lookup.get(), List.copyOf(raw), ring);
        return ring;
    }

    private record CachedRing(
            HolderLookup.RegistryLookup<UpgradeOrbType> lookup, List<String> raw, List<String> ring) {}

    private static volatile @Nullable CachedRing ringCache;

    private static List<String> resolveRing(
            List<? extends String> raw, HolderLookup.RegistryLookup<UpgradeOrbType> lookup) {
        List<String> ring = new ArrayList<>();
        for (String entry : raw) {
            ResourceLocation location = ResourceLocation.tryParse(entry);
            if (location == null) {
                LOGGER.warn("[bertie_weapons] elementOrbTypes entry '{}' is not a valid ID, skipping", entry);
                continue;
            }
            ResourceKey<UpgradeOrbType> key = ResourceKey.create(UPGRADE_ORB_REGISTRY, location);
            if (lookup.get(key).isEmpty()) {
                LOGGER.warn("[bertie_weapons] upgrade orb type '{}' is not loaded, dropping it from the ring", entry);
                continue;
            }
            ring.add(location.toString());
        }
        return List.copyOf(ring);
    }

    /** Reads the two tier counters and the filled part of the ring off a stack. */
    public static TierState readState(ItemStack stack, List<String> ring) {
        UpgradeData data = UpgradeData.getUpgradeData(stack);
        if (data == UpgradeData.NONE) {
            return TierState.empty();
        }
        int statTier = 0;
        int spellTier = 0;
        Set<String> filled = new LinkedHashSet<>();
        for (Map.Entry<Holder<UpgradeOrbType>, Integer> entry : data.upgrades().entrySet()) {
            String id = idOf(entry.getKey());
            if (id == null || entry.getValue() == null || entry.getValue() <= 0) {
                continue;
            }
            if (id.equals(STAT_TIER.location().toString())) {
                statTier = entry.getValue();
            } else if (id.equals(SPELL_TIER.location().toString())) {
                spellTier = entry.getValue();
            } else if (ring.contains(id)) {
                // Any positive count reads as "slot filled". Counts above 1 can only come from an
                // outside path (the Arcane Anvil), and the conversion clears the slot regardless.
                filled.add(id);
            }
        }
        return new TierState(statTier, spellTier, filled);
    }

    // ---- transitions ---------------------------------------------------------------------

    /** Adds one stat tier, or returns empty if the weapon is capped or not eligible. */
    public static Optional<ItemStack> applyStatTier(ItemStack input, HolderLookup.Provider registries) {
        List<String> ring = elementRing(registries);
        TierState state = readState(input, ring);
        if (!state.canApplyStat(Config.MAX_STAT_TIER.get())) {
            return Optional.empty();
        }
        return withState(input, state.applyStat(Config.MAX_STAT_TIER.get()), ring, registries);
    }

    /** Adds one element to the ring, converting the tier if that closed it. */
    public static Optional<ItemStack> applyElement(
            ItemStack input, ResourceKey<UpgradeOrbType> orb, HolderLookup.Provider registries) {
        List<String> ring = elementRing(registries);
        String element = orb.location().toString();
        TierState state = readState(input, ring);
        if (!state.canApplyElement(element, ring, Config.MAX_SPELL_TIER.get())) {
            return Optional.empty();
        }
        TierState next = state.applyElement(element, ring, Config.MAX_SPELL_TIER.get());
        return withState(input, next, ring, registries);
    }

    /**
     * Rebuilds the stack with {@code state} applied. Starts from a full copy and rewrites only the
     * keys we own, so anything else on the item - including upgrades this mod knows nothing about -
     * is carried across verbatim.
     */
    private static Optional<ItemStack> withState(
            ItemStack input, TierState state, List<String> ring, HolderLookup.Provider registries) {
        Optional<HolderLookup.RegistryLookup<UpgradeOrbType>> lookup = orbRegistry(registries);
        if (lookup.isEmpty()) {
            return Optional.empty();
        }

        ItemStack result = input.copy();
        result.setCount(1);

        UpgradeData existing = UpgradeData.getUpgradeData(input);
        Map<Holder<UpgradeOrbType>, Integer> upgrades = new LinkedHashMap<>();
        String slot = existing == UpgradeData.NONE || existing.getUpgradedSlot().isEmpty()
                ? EquipmentSlot.MAINHAND.getName()
                : existing.getUpgradedSlot();

        // Carry over everything that is not ours to manage.
        if (existing != UpgradeData.NONE) {
            for (Map.Entry<Holder<UpgradeOrbType>, Integer> entry : existing.upgrades().entrySet()) {
                String id = idOf(entry.getKey());
                if (id == null || isManaged(id, ring)) {
                    continue;
                }
                upgrades.put(entry.getKey(), entry.getValue());
            }
        }

        if (state.statTier() > 0) {
            Optional<Holder.Reference<UpgradeOrbType>> holder = lookup.get().get(STAT_TIER);
            if (holder.isEmpty()) {
                LOGGER.error("[bertie_weapons] upgrade orb type {} is missing - is the mod's datapack loaded?", STAT_TIER.location());
                return Optional.empty();
            }
            upgrades.put(holder.get(), state.statTier());
        }
        if (state.spellTier() > 0) {
            Optional<Holder.Reference<UpgradeOrbType>> holder = lookup.get().get(SPELL_TIER);
            if (holder.isEmpty()) {
                LOGGER.error("[bertie_weapons] upgrade orb type {} is missing - is the mod's datapack loaded?", SPELL_TIER.location());
                return Optional.empty();
            }
            upgrades.put(holder.get(), state.spellTier());
        }
        for (String element : state.filledElements()) {
            ResourceLocation location = ResourceLocation.tryParse(element);
            if (location == null) {
                continue;
            }
            lookup.get()
                    .get(ResourceKey.create(UPGRADE_ORB_REGISTRY, location))
                    .ifPresent(holder -> upgrades.put(holder, 1));
        }

        if (upgrades.isEmpty()) {
            UpgradeData.removeUpgradeData(result);
        } else {
            UpgradeData.set(result, new UpgradeData(Map.copyOf(upgrades), slot));
        }
        return Optional.of(result);
    }

    // ---- plumbing ------------------------------------------------------------------------

    /** True for the keys this mod rewrites: both tier counters and every ring element. */
    private static boolean isManaged(String id, List<String> ring) {
        return id.equals(STAT_TIER.location().toString())
                || id.equals(SPELL_TIER.location().toString())
                || ring.contains(id);
    }

    private static @Nullable String idOf(Holder<UpgradeOrbType> holder) {
        ResourceKey<UpgradeOrbType> key = holder.getKey();
        return key == null ? null : key.location().toString();
    }

    private static Optional<HolderLookup.RegistryLookup<UpgradeOrbType>> orbRegistry(
            HolderLookup.Provider registries) {
        Optional<HolderLookup.RegistryLookup<UpgradeOrbType>> lookup = registries.lookup(UPGRADE_ORB_REGISTRY);
        if (lookup.isEmpty()) {
            LOGGER.error(
                    "[bertie_weapons] registry {} is absent - Iron's Spells is a hard dependency, "
                            + "so this means the registry was renamed upstream",
                    UPGRADE_ORB_REGISTRY.location());
        }
        return lookup;
    }
}
