# Finding emerald pockets

Rare underground deposits follow a short vein trend through OreSpawn's
mountain-belt geology. The surface biome doesn't need to be a mountain.
Most finds have two to four separated pockets, with 25–60 planned ore
blocks along a 24–48 block trend. A rich find can have four to six pockets
and 70–120 planned blocks along a 52–80 block trend. A large
mine has 260–420 planned blocks in four or five separated zones along a
120–168 block trend. Each zone has two pockets, with barren rock between
zones. A pale, narrow trace of diorite points onward without adding
emeralds to the gaps.
Caves, changes in host rock and protected features can interrupt the trace
or reduce the ore you find. The ore has normal vanilla drops, Fortune and
Silk Touch.

The inspiration is the vein-adjacent mineralisation of the
[Kafubu emerald district](https://repository.naturalis.nl/pub/801803).
Active Mineralogy strata use only enabled, natural schist. If the host is
missing, the deposit is skipped. Without physical strata, natural vanilla
stone variants are a simpler approximation. Unclassified geology and
unsupported terrain are skipped rather than guessing or loading new chunks.

A compact stepped descent and one short crosscut can expose a small pocket.
Rich and large finds can have timbered galleries leading from a single
stairway to two or more pockets. In a large mine these are starter workings:
follow the diorite out of the worked zone and dig your own headings into
the other zones. The complete starter workings must fit in one fresh chunk so the
game can check the route before carving. If the galleries cannot reach two
actual pockets, or if water, infested stone or a structure makes the route
unsafe, a large mine may try another route in that same chunk. If none fits
safely, the deposit can still generate without an entrance. Pockets beyond
the workings are left for prospecting.

The fresh defaults use a 35% selection chance per 64-chunk region before
geology and host checks, at Y12–48 and with at least eight blocks of cover.
Eight percent of candidates that reach the layout stage try the larger
layout. If that does not fit the mountain-belt geology and eligible host,
the generator tries the ordinary layout instead. Large mines have a
separate 8% roll, only in regions not selected for small or rich finds.
They remain more geographically restricted than iron formations; all
their zones must fit the mountain-belt geology and eligible host rock. These are gameplay
ranges for individual finds, not estimates of a real mine's production.
The default additive mode does not scale down or replace ordinary emerald
rules. Pack authors can opt into hybrid or replacement mode.

Settings are in `config/realisticdeposits/deposits/emerald.json`. Restart
after changes. The optional `shape.rich_find` sets the chance, pocket count,
strike and ore budget for larger finds. `shape.rich_find.workings` enables
the linked galleries, subject to the ordinary abandoned-mine setting.
`shape.vein_trace` enables the non-ore cue for small and rich finds.
The optional `shape.large_mine` sets the large-mine chance, zone count,
strike and ore budget. Large mines always plan their own prospecting clues;
the ordinary abandoned-mine setting still controls their starter workings.
Leave `shape.large_mine` absent to keep the two-tier layout. Older catalogues
without these fields and existing world
profiles keep their previous settings. Generated chunks are never rewritten.
Preview a catalogue candidate without writing blocks:

```text
/realisticdeposits preview <regionX> <regionZ> realisticdeposits:overworld_emerald_pockets
```
