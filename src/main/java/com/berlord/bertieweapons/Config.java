package com.berlord.bertieweapons;

import java.util.List;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Tier caps and the element ring, in {@code config/bertie_weapons-common.toml}.
 *
 * <p>The ring is a plain list of {@code irons_spellbooks:upgrade_orb_type} registry IDs rather
 * than anything hardcoded, because the pack keeps gaining schools - {@code cataclysm_spellbooks}
 * already ships {@code abyssal_power} and {@code technomancy_power}, and more addons will follow.
 * Adding a school to the ring is one line here; nothing in Java knows the list length.
 *
 * <p>The default is the eight elemental orbs Iron's itself ships. Eldritch is deliberately absent:
 * base Iron's has an eldritch <em>school</em> but no eldritch upgrade orb, and it is covered anyway
 * once a tier converts to generic spell power.
 *
 * <p>The per-tier magnitudes are NOT here - they live in the two upgrade_orb_type JSON files
 * ({@code weapon_power.json} = +20% attack damage, {@code spell_tier.json} = +5% spell power),
 * because that is where Iron's reads them from.
 */
public final class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue MAX_STAT_TIER = BUILDER
            .comment("How many flat base-damage tiers a weapon can take. Each is worth +20% of base attack damage.")
            .defineInRange("maxStatTier", 12, 0, 256);

    public static final ModConfigSpec.IntValue MAX_SPELL_TIER = BUILDER
            .comment("How many spell-power tiers a weapon can take. Each completed ring is worth +5% spell power.")
            .defineInRange("maxSpellTier", 12, 0, 256);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> ELEMENT_ORB_TYPES = BUILDER
            .comment(
                    "The element ring: irons_spellbooks:upgrade_orb_type IDs, each addable once per tier.",
                    "Filling every entry converts the tier into generic spell power and frees the ring.",
                    "IDs that do not resolve at runtime are skipped with a warning, so listing an orb",
                    "from a mod that is not installed is safe.")
            .defineListAllowEmpty(
                    "elementOrbTypes",
                    () -> List.of(
                            "irons_spellbooks:fire_power",
                            "irons_spellbooks:ice_power",
                            "irons_spellbooks:lightning_power",
                            "irons_spellbooks:holy_power",
                            "irons_spellbooks:ender_power",
                            "irons_spellbooks:blood_power",
                            "irons_spellbooks:evocation_power",
                            "irons_spellbooks:nature_power"),
                    entry -> entry instanceof String s && !s.isBlank());

    public static final ModConfigSpec SPEC = BUILDER.build();

    private Config() {}
}
