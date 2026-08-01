> [!IMPORTANT]
> Development has moved to [`bertie-mc/bertie`](https://github.com/bertie-mc/bertie/tree/main/mods/bertie-weapons). This repository is retained read-only for historical tags, releases, and issues.

# Bertie Weapons (`bertie_weapons`)

Tiered, non-destructive weapon empowerment for the **bertie** modpack. NeoForge 1.21.1.

Two parallel upgrade tracks on unique weapons, both stored in **Iron's own `upgrade_data`
component**, so they coexist with Apotheosis affixes, enchantments, durability and spell containers
rather than clearing them.

> **Status: test build (0.1.0).** The numbers and the crafting costs are placeholders chosen to
> exercise the system, not balance. See [Known limitations](#known-limitations).

---

## The two tracks

### 1. Stat track — flat base damage

Each tier is **+20 % of base attack damage**, capped at **12 tiers** (`+240 %`, so ×3.4).

Applied as a single `ADD_MULTIPLIED_BASE` modifier whose amount scales with the tier count. That
operation is what makes the ordering come out right — Minecraft computes

```
d0 = attributeBase + Σ(ADD_VALUE)            <- weapon damage + flat affixes
d1 = d0 + Σ(d0 × ADD_MULTIPLIED_BASE)        <- our tiers land here, additively
d1 = d1 × Π(1 + ADD_MULTIPLIED_TOTAL)        <- Apotheosis multiplicative affixes still multiply
```

so tiers **add up rather than compound**, they apply on top of flat affixes, and anything
multiplicative downstream still scales off the improved number.

### 2. Spell track — the element ring

Within the tier you are currently working on, each element of the configured ring may be added
**once**; each filled slot is **+5 %** to that school's spell power. Filling the last slot
**converts**: the elemental entries are dropped and the completed-tier counter goes up by one,
worth **+5 % generic spell power**. Cap **12 tiers** — at 8 elements that is **96 orbs**.

The conversion is deliberately **value-neutral per school**. Iron's computes spell damage as
`base × spell_power × schoolPower`, both multiplicative off 1.0, so `+5 %` generic is worth exactly
what `+5 %` fire is worth to a fire spell. What completing a tier buys is coverage of schools you
never filled — eldritch has no orb at all — and a freed ring for the next tier. It is a
**progression gate, not a power spike**.

---

## How it is built

Almost all of this is Iron's own machinery; the mod is mostly wiring.

| Piece | Where it lives |
| --- | --- |
| Tier magnitudes | `data/bertie_weapons/irons_spellbooks/upgrade_orb_type/{weapon_power,spell_tier}.json` |
| Tier counters | Iron's `irons_spellbooks:upgrade_data` component — the per-entry count **is** the tier |
| Attribute application | Iron's `ServerPlayerEvents.handleUpgradeModifiers` (`ItemAttributeModifierEvent`) |
| Progression rules | `logic/TierState` — pure, no Minecraft types |
| Component read/write | `logic/WeaponUpgrades` |
| Crafting | `recipe/{Stat,Spell}UpgradeRecipe` on `RecipeType.CRAFTING` |

`irons_spellbooks:upgrade_orb_type` is a **datapack registry**
(`UpgradeOrbTypeRegistry#registerDatapackRegistries`), which is why our two tiers are plain JSON.
Iron's attribute handler has **no item-type check** and `getRelevantEquipmentSlot` falls through to
`mainhand`, so a sword is handled exactly like a chestplate — nothing needed patching.

### The non-destructive guarantee

`WeaponUpgrades#withState` starts from `input.copy()` and only ever rewrites the keys this mod owns
(both tier counters plus every ring element). Everything else — enchantments, durability, custom
names, Apotheosis affixes, spell containers, **and Iron's upgrades this mod knows nothing about** —
is carried across verbatim. That is a property of the single write path, not of any individual
recipe, so a new station cannot accidentally break it.

---

## Configuration

`config/bertie_weapons-common.toml`:

| Key | Default | Meaning |
| --- | --- | --- |
| `maxStatTier` | `12` | Stat tier cap |
| `maxSpellTier` | `12` | Spell tier cap |
| `elementOrbTypes` | the 8 Iron's orbs | The ring, as `upgrade_orb_type` registry IDs |

The ring is a list rather than anything hardcoded, because the pack keeps gaining schools —
`cataclysm_spellbooks` already ships `abyssal_power` and `technomancy_power`. Adding one is a single
line; nothing in Java knows the ring's length, and IDs that do not resolve are skipped with a
warning so the config stays portable across pack variants.

**Per-tier magnitudes are not here** — they live in the two `upgrade_orb_type` JSON files, because
that is where Iron's reads them from.

### Tags

| Tag | Contents |
| --- | --- |
| `#bertie_weapons:upgradable_weapons` | `#simplyswords:lootable_uniques`, `#simplyswords:conditional_uniques_type_2`, `#simplymore:uniques` (all `required: false`) |
| `#bertie_weapons:stat_catalysts` | `minecraft:diamond` — placeholder |

---

## Test-build recipes

Shapeless, in any crafting grid, in any arrangement:

- **unique weapon + diamond** → +1 stat tier
- **unique weapon + elemental upgrade orb** → that element's slot filled (converts on the 8th)

Both are `CustomRecipe`s on `RecipeType.CRAFTING` rather than data-driven shaped recipes, because
the result depends on the input weapon's current tier state — there is no fixed output stack to put
in a JSON. Being ordinary crafting recipes means **every station that runs the vanilla crafting
registry picks them up for free**.

---

## Verification

`gradle test` — 8 unit tests over `TierState` (cap behaviour, ring closure, per-tier uniqueness,
track independence, and that growing the ring raises the cost without code changes).

`gradle runGameTestServer` — 10 GameTests from the test-only `gameTest` source set against a
real server with Iron's and Simply Swords loaded. The test classes and `empty.nbt` fixture are
excluded from releases. These cover the parts that only exist at runtime:

- both `upgrade_orb_type` JSON files parsed; the 8-element ring resolves
- one stat tier produces exactly `+0.20 ADD_MULTIPLIED_BASE` attack damage **through Iron's real
  attribute pipeline**, leaving the flat base untouched
- 12 tiers produce `+2.40`, i.e. additive not compounding, and the cap holds
- closing the ring yields `+0.05` generic spell power and **zero** elemental spell power
- the same element is refused twice in one tier
- enchantment, durability and custom name survive; the two tracks do not interfere
- an unmanaged Iron's upgrade (`cooldown`) survives an upgrade
- the real `RecipeManager` matches weapon + diamond to `bertie_weapons:stat_upgrade`
- weapon + fire orb matches; weapon + **cooldown** orb (a real orb outside the ring) does not
- non-unique and vanilla weapons are not upgradable

Gradle resolves the exact runtime-only dependency closure used by the GameTest run; no jars need
to be copied into `run/mods/`.

---

## Known limitations

- **Crafting-table path only.** Malum's Spirit Altar and Hephaestus Forge use their own recipe
  types, not crafting recipes, so each needs a thin adapter calling the same `WeaponUpgrades` entry
  points. The logic is already station-agnostic for exactly this reason.
- **No EMI/JEI display.** `CustomRecipe`s have no fixed inputs to show. Needs a hand-written
  category, most naturally in the existing `berlords_emi` project.
- **No custom tooltip.** Upgrades render as Iron's stock attribute lines, so a weapon mid-ring shows
  eight separate elemental entries with no "tier 3, 5/8 elements" summary.
- **Uniques are deliberately kept out of `#irons_spellbooks:upgrade_whitelist`.** Adding them would
  let the Arcane Anvil apply orbs directly and bypass the ring rules entirely. If the anvil should
  become a valid station, it needs its own tier-aware path — and `maxUpgrades` in
  `irons_spellbooks-server.toml` raised from its default of **3**.
- **`ADD_MULTIPLIED_BASE` includes the player's own base attack damage (1.0)**, so a tier is
  "+20 % of (weapon damage + 1)". Mildly favours low-damage weapons; at 12 tiers an 8-damage weapon
  reaches 30.6 rather than 27.2.
- **Iron's merges same-attribute, same-operation modifiers.** `UpgradeUtils#collectAndRemove-
  PreexistingAttribute` folds a pre-existing modifier into the one it adds. The sum is preserved, so
  the maths still holds, but if Apotheosis lands an `ADD_MULTIPLIED_BASE` attack-damage affix the
  two collapse into one tooltip line. Worth re-checking once Apotheosis is actually installed —
  as of this build only `ApothicAttributes` (its library) is in the pack.
- **Stat tiers are not refundable.** `weapon_power` has no `containerItem`, so a Shriving Stone
  returns nothing for them.
