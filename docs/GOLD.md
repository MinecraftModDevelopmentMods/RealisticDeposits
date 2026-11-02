# Goldfields and river gold

A goldfield is worth exploring, but its gold is not spread evenly. Narrow,
steep veins branch and pinch out, with rich shoots separated by lean stretches.
Following one promising vein can lead to another pocket rather than a solid
wall of gold. Where it is safe, abandoned workings provide a gradual entrance
and a short route into the principal vein.

The idea comes from gold deposited by fluids moving through fractures, followed
by weathering and concentration in rivers. This is one family of gold deposits,
not a model of every way gold occurs in nature. The
[Geological Survey of South Australia](https://energymining.sa.gov.au/industry/minerals-and-mining/mineral-commodities/gold)
describes both lode and alluvial deposits.

## With Mineralogy

Goldfields favour mountain belts and selected older crustal or volcanic
districts. They use suitable declared natural rocks, including slate, phyllite,
schist, amphibolite and selected granitic or metasedimentary hosts. The veins
cut across the rocks already there; they do not create surrounding strata.

Mineralogy does not yet have vein quartz. This update therefore represents the
gold-bearing parts of a fracture system, without a visible quartz filling.
Quartzite is not used as a substitute for vein quartz. A modern surface biome
also need not resemble the setting in which the deposit originally formed.

## Without Mineralogy

The same vein planner works in natural vanilla stone, granite, diorite and
andesite. Polished variants are excluded. OreSpawn still supplies geological
classification without replacing terrain. If a legacy geology profile lacks
that classification, a rarer, seed-based approximation is used instead. It
does not change the world's geology settings.

## River placers

Some rivers near a planned goldfield contain patches of gold-bearing gravel.
Only suitable natural submerged sediment is replaced. Rivers are not reshaped,
and the source link is a regional approximation, not a simulation of drainage
and erosion. Mining the source first does not change the placer.

Mine rich gravel with a shovel. Each block leaves one ordinary gravel and has
a one-in-four chance of one vanilla gold nugget. Fortune multiplies a successful
drop; Silk Touch returns the rich block instead. Falling also preserves its
identity. The ordinary gravel left behind is spent: placing it again cannot
produce more gold. There are no panning or processing recipes for rich gravel.

Both the gravel background and the small gold flecks use vanilla textures, so
resource packs can replace them. River placers do not receive mine entrances.

Before Fortune, 36 rich gravel blocks give one ingot's worth of nuggets on
average. Individual drops vary, and a small pocket may give no gold at all.

## How much gold to expect

Gold is deliberately concentrated rather than evenly available. In the
six-seed standalone survey, the default settings gave an estimated 2.41
ingots per chunk overall, against 10.91 in the vanilla controls: about 22% of
vanilla's generated supply. The two holdout seeds gave a similar result. The
defaults were not changed to meet a vanilla-equivalent target.

The 179 productive fields in that survey averaged about 27,000 ore blocks
each, ranging from roughly 7,400 to 57,700. Those are terrain-based reserve
estimates before collisions with other ores, structures or workings. They
are not a promise that every field can be mined completely.

From fixed prospecting points, the median horizontal distance to a productive
district was about 1.1–1.5 km across the six seeds. The 90th-percentile distance
was about 2.2–3.1 km. Walking routes and finding a safe entrance can take longer.

Background generation supplied about 68% of the estimated total, lodes 32%
and placers less than 1%. Placers are a little extra while exploring rivers,
not the main source of gold. Fortune helps nugget drops; vanilla 1.10 gold
ore still smelts to one ingot regardless of Fortune.

These figures come from a limited, fixed sample: four survey seeds and two
holdouts, with 1,024 sampled chunks per seed for decorated river sediments.
The bulk comparison leaves vanilla structures off; the manual mine and river
locations were checked separately with normal structures enabled.
The whole-seed bootstrap range was about 21–23% of vanilla, but it is not a
guarantee for other seeds. River sampling is particularly sparse. Mineralogy
and third-party ore processing can change the experience; they are not part
of these standalone reward estimates. Mob drops, chest loot and trading are
not included either.

## Settings and existing worlds

The first launch installs `config/realisticdeposits/deposits/gold.json` only if
it is absent. Defaults use 64-chunk regions with 50% candidate selection before
geological filtering, two to four veins, 256–512 block strike lengths, widths
of one to three blocks and dips of 55–85 degrees. Gold stays within Y 8–128
and beneath the local surface. River pockets are one to three blocks thick
and must be within 1,024 blocks of a planned source district.

The lode rule retains 15% ordinary background gold, including the managed Mesa
supply. The placer does not reduce it again. Explicit OreSpawn overrides and
custom placement channels retain their existing meaning. Turning off the
effective lode rule also turns off its linked placers; placers can be disabled
independently.

Gold geometry and source links are captured with the world and require a
restart after structural changes. Installing this version does not silently
add gold rules to an existing world, reduce its gold supply or regenerate old
chunks. Existing catalogues and saved mine layouts are preserved.

Use `/realisticdeposits preview <regionX> <regionZ> realisticdeposits:overworld_gold_lodes`
for a read-only preview. The separate [gold walkthrough](../tools/qualification/GOLD_TESTING.md)
uses disposable saves for manual testing.
