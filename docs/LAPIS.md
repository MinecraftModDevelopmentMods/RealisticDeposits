# Finding lapis

Lapis is a smaller discovery than an iron district or a goldfield. Look for
compact lenses with pinching edges and richer pockets, rather than a solid
blue wall. A short worked section can lead you into a useful reserve without
excavating most of it for you.

The inspiration is marble-hosted lapis such as
[Sar-e-Sang in Afghanistan](https://iugs-geoheritage.org/geoheritage_sites/the-sar-e-sang-lapis-lazuli-deposit/).
There, lapis occurs in lenses and layers associated with altered marble.
This update represents that general arrangement, not the heat, fluids and
chemical reactions that formed it.

## With Mineralogy

When physical strata are active, lenses are fitted into natural marble.
Regions without a suitable marble interval are rejected. Ordinary limestone,
dolomite, polished marble, bricks and other decorative blocks do not qualify.
The ore replaces only the matching natural host; it does not manufacture a
marble band or turn terrain replacement on.

There is no new skarn rock or other carrier block in this update. Nor is every
marble layer a lapis deposit: regional selection still makes discoveries rare.

## Without physical strata

Without Mineralogy, or with its physical strata disabled, the same lenses use
natural vanilla stone, granite, diorite and andesite. Polished variants remain
excluded. This is a simpler visual approximation, not a claim that lapis
normally forms in every one of those rocks.

OreSpawn's geological classification still helps choose districts without
replacing terrain. Older geology that lacks modern classification uses a
rarer, deterministic approximation instead. Its settings are left alone.

Other terrain generators need to support safe sampling. If they do not,
lapis lenses are skipped and a message is written to the log; the mod will
not generate neighbouring chunks or replace unfamiliar rocks to find a site.

## Mining and rewards

The generated block is ordinary Minecraft lapis ore. In 1.10.2 it drops four
to eight pieces of lapis lazuli, averaging six before Fortune. Fortune increases
that yield; Silk Touch returns the ore block instead. No new processing
recipes, lapis grades or river placers are added.

The fresh default catalogue retains 30% ordinary background lapis. With
physical strata active, `shape.marble_search_radius` lets a failed site try
nearby positions inside the same region. The default radius is 96 blocks;
absent or zero keeps the original single-site search. New lenses are
deliberately concentrated, and their reward is not forced to equal vanilla's
overall supply. Structures, caves and water can interrupt a deposit or prevent
a safe mine entrance. Not every planned lens will be fully recoverable.

## Settings and existing worlds

Settings live in `config/realisticdeposits/deposits/lapis.json`. Defaults use
64-chunk regions, a 50% candidate chance before geological rejection and one
to three lenses per accepted district. Lengths are 64-160 blocks, widths
12-24 and thicknesses 1-4, within Y8-128 and below the natural surface.

New files are copied only when absent. Existing catalogues are not overwritten,
and existing world profiles do not silently receive lapis rules or reduced
background generation. Previously generated chunks and saved mine layouts
remain unchanged. Back up a world before editing its captured OreSpawn profile.

Lapis geometry and policy settings are structural: restart after editing them.
The additive, hybrid and replacement policies use the same OreSpawn controls
as the other deposits, including imported settings and custom channels.

For a separate test world, follow the [lapis walkthrough](../tools/qualification/LAPIS_TESTING.md).
