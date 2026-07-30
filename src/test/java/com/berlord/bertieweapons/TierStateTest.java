package com.berlord.bertieweapons;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.berlord.bertieweapons.logic.TierState;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TierStateTest {
    /** The eight orbs Iron's actually ships - the shipped default ring. */
    private static final List<String> RING = List.of(
            "irons_spellbooks:fire_power",
            "irons_spellbooks:ice_power",
            "irons_spellbooks:lightning_power",
            "irons_spellbooks:holy_power",
            "irons_spellbooks:ender_power",
            "irons_spellbooks:blood_power",
            "irons_spellbooks:evocation_power",
            "irons_spellbooks:nature_power");

    private static final int MAX = 12;

    @Test
    void statTiersAccumulateToTheCap() {
        TierState state = TierState.empty();
        for (int i = 0; i < MAX; i++) {
            assertTrue(state.canApplyStat(MAX), "tier " + i + " should be applicable");
            state = state.applyStat(MAX);
        }
        assertEquals(MAX, state.statTier());
        assertFalse(state.canApplyStat(MAX), "stat track must stop at the cap");
        assertThrows(IllegalStateException.class, () -> TierState.empty().applyStat(0));
    }

    @Test
    void statTrackDoesNotDisturbTheSpellTrack() {
        TierState state = TierState.empty()
                .applyElement("irons_spellbooks:fire_power", RING, MAX)
                .applyStat(MAX);
        assertEquals(1, state.statTier());
        assertEquals(0, state.spellTier());
        assertEquals(Set.of("irons_spellbooks:fire_power"), state.filledElements());
    }

    @Test
    void eachElementIsAcceptedOnlyOncePerTier() {
        TierState state = TierState.empty().applyElement("irons_spellbooks:fire_power", RING, MAX);
        assertFalse(state.canApplyElement("irons_spellbooks:fire_power", RING, MAX));
        assertTrue(state.canApplyElement("irons_spellbooks:ice_power", RING, MAX));
    }

    @Test
    void offRingElementsAreRejected() {
        TierState state = TierState.empty();
        // A real orb type, but one the ring deliberately excludes.
        assertFalse(state.canApplyElement("irons_spellbooks:cooldown", RING, MAX));
        assertFalse(state.canApplyElement("irons_spellbooks:eldritch_power", RING, MAX));
    }

    @Test
    void closingTheRingConvertsAndClearsIt() {
        TierState state = TierState.empty();
        for (int i = 0; i < RING.size() - 1; i++) {
            state = state.applyElement(RING.get(i), RING, MAX);
            assertEquals(0, state.spellTier(), "tier must not tick over early");
        }
        assertEquals(1, state.elementsRemaining(RING));

        state = state.applyElement(RING.get(RING.size() - 1), RING, MAX);
        assertEquals(1, state.spellTier(), "closing the ring completes exactly one tier");
        assertEquals(Set.of(), state.filledElements(), "the ring must be free for the next tier");
        assertEquals(RING.size(), state.elementsRemaining(RING));
    }

    @Test
    void twelveTiersCostEightOrbsEach() {
        TierState state = TierState.empty();
        int orbs = 0;
        while (state.spellTier() < MAX) {
            for (String element : RING) {
                state = state.applyElement(element, RING, MAX);
                orbs++;
            }
        }
        assertEquals(MAX, state.spellTier());
        assertEquals(MAX * RING.size(), orbs, "12 tiers x 8 elements = 96 orbs");
        assertFalse(state.canApplyElement(RING.get(0), RING, MAX), "capped ring accepts nothing");
    }

    @Test
    void aGrownRingRaisesTheCostWithoutCodeChanges() {
        // Adding cataclysm_spellbooks' orbs to the config must simply make each tier longer.
        List<String> grown = new java.util.ArrayList<>(RING);
        grown.add("cataclysm_spellbooks:abyssal_power");
        grown.add("cataclysm_spellbooks:technomancy_power");

        TierState state = TierState.empty();
        for (int i = 0; i < RING.size(); i++) {
            state = state.applyElement(grown.get(i), grown, MAX);
        }
        assertEquals(0, state.spellTier(), "the old eight no longer close a ten-element ring");
        state = state.applyElement(grown.get(8), grown, MAX);
        state = state.applyElement(grown.get(9), grown, MAX);
        assertEquals(1, state.spellTier());
    }

    @Test
    void filledElementsAreImmutableFromOutside() {
        Set<String> mutable = new java.util.LinkedHashSet<>();
        mutable.add("irons_spellbooks:fire_power");
        TierState state = new TierState(0, 0, mutable);
        mutable.add("irons_spellbooks:ice_power");
        assertEquals(Set.of("irons_spellbooks:fire_power"), state.filledElements());
    }
}
