package com.berlord.bertieweapons.logic;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The weapon-tier rule set, expressed without a single Minecraft type so it can be unit
 * tested off-game. Everything the mod does to a weapon is one of the transitions below.
 *
 * <p>Two independent tracks:
 * <ul>
 *   <li><b>stat</b> - a plain counter. Each tier is worth {@code +20%} of base attack damage,
 *       applied as one {@code ADD_MULTIPLIED_BASE} modifier whose amount scales with the count,
 *       so tiers add up rather than compounding.</li>
 *   <li><b>spell</b> - a ring. Within the tier you are working on you may add each element of
 *       {@link #elements} exactly once; each filled slot is worth {@code +5%} to that school's
 *       spell power. Filling the last one <em>converts</em>: the elemental entries are dropped and
 *       the completed-tier counter goes up by one, which is worth {@code +5%} to generic spell
 *       power.</li>
 * </ul>
 *
 * <p>The conversion is deliberately value-neutral per school - Iron's computes spell damage as
 * {@code base x spell_power x schoolPower}, both multiplicative off 1.0, so {@code +5%} generic and
 * {@code +5%} fire are worth exactly the same to a fire spell. What completing a tier buys you is
 * coverage of schools you never filled (eldritch has no orb at all) and a freed ring for the next
 * tier. It is a progression gate, not a power spike.
 */
public record TierState(int statTier, int spellTier, Set<String> filledElements) {

    public TierState {
        filledElements = Set.copyOf(filledElements);
    }

    public static TierState empty() {
        return new TierState(0, 0, Set.of());
    }

    // ---- stat track ----------------------------------------------------------------------

    public boolean canApplyStat(int maxStatTier) {
        return statTier < maxStatTier;
    }

    public TierState applyStat(int maxStatTier) {
        if (!canApplyStat(maxStatTier)) {
            throw new IllegalStateException("stat tier already at cap " + maxStatTier);
        }
        return new TierState(statTier + 1, spellTier, filledElements);
    }

    // ---- spell track ---------------------------------------------------------------------

    /**
     * An element may be added when it is part of the configured ring, the ring slot is still free
     * in the tier being worked on, and there is a tier left to complete.
     */
    public boolean canApplyElement(String element, List<String> elements, int maxSpellTier) {
        return elements.contains(element)
                && !filledElements.contains(element)
                && spellTier < maxSpellTier;
    }

    /**
     * Adds one element. If that was the last free slot the ring converts: elements are cleared and
     * the completed-tier counter goes up.
     */
    public TierState applyElement(String element, List<String> elements, int maxSpellTier) {
        if (!canApplyElement(element, elements, maxSpellTier)) {
            throw new IllegalStateException("element " + element + " cannot be applied to " + this);
        }
        Set<String> next = new LinkedHashSet<>(filledElements);
        next.add(element);
        if (next.containsAll(elements)) {
            return new TierState(statTier, spellTier + 1, Set.of());
        }
        return new TierState(statTier, spellTier, next);
    }

    /** How many more elements are needed to close the ring. Purely informational (tooltips). */
    public int elementsRemaining(List<String> elements) {
        int missing = 0;
        for (String element : elements) {
            if (!filledElements.contains(element)) {
                missing++;
            }
        }
        return missing;
    }
}
