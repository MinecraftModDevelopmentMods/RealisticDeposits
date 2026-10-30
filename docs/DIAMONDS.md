# Diamond deposits

There are two linked deposits: steep diamond pipes and small pockets of
diamond-bearing river gravel. The 1-in-16 gravel roll stays fixed; pipes make
up most of the supply, with river pockets as an occasional bonus. Ordinary
background generation stays at 15%.

The six-seed ore survey estimates 3.57 diamonds per chunk against 3.70 in
the matched vanilla controls, before Fortune. Calibration and separate holdout
averages both fall within 20% of their controls. The estimated split is about
83% pipes, 2% placers and 15% background diamonds. Fortune increases all three
sources by the same vanilla multiplier.

These are regional estimates, not a promise for each seed or mining trip.
District clustering gives a wide statistical range, and other ores, caves,
structures and excavated workings can reduce the ore left to mine. The survey
counts pipe reserves before those losses. Some areas will be much richer
than others; exploration is part of finding a productive district.

## Pipes

Pipes occur in rare districts, normally beneath OreSpawn's stable cratons. Each
district contains one to three irregular bodies, wider near the top and narrower
at depth. Diamonds occur in patches rather than filling the whole body. A few
can occur close to the surface, but the upper parts are deliberately sparse.

Pipes cut across existing rocks. Without Mineralogy, their hosts are natural
vanilla stone, granite, diorite and andesite. With physical Mineralogy strata,
they can cut through the declared natural rocks. They do not require peridotite.
Kimberlite and lamproite are not represented by new blocks yet, so barren pipe
rock looks like the surrounding country rock. No craters or replacement terrain
are added.

Older Cyano geology has no stable-craton classification. In those worlds, the
same seed-based district planner uses an additional rarity check instead. It
does not change the world's geology settings.

## River gravel

Placers need submerged natural sand or gravel in a river, within 1,024 blocks
of a planned productive pipe district. The source is the saved district plan,
not the diamonds left in the ground: mining the pipe does not remove its
placers, and exploring the river first does not prevent them.

Mining diamond-bearing gravel leaves one ordinary gravel block as an item.
There is a 1-in-16 chance of also receiving a diamond. Fortune multiplies a
successful diamond drop in the same way as vanilla diamond ore; it does not
increase the initial chance. Silk Touch returns only the rich block. Falling
gravel keeps its identity, and ordinary gravel never becomes diamond-bearing
when placed again. There are no panning or processing recipes.

The rich block uses the vanilla gravel texture beneath a subtle mineral overlay,
so resource packs can replace its background.

## Settings and existing worlds

`config/realisticdeposits/deposits/diamonds.json` contains both definitions. It
is created only if absent; existing catalogues are never overwritten. The pipe
rule alone controls ordinary background diamonds. The placer rule adds sediment
and never applies a second background reduction or substitutes gravel for ore.

Disabling the source pipe rule also disables its placers. Disabling only the
placer rule leaves the pipes available. The geometry and source relationship
are saved with the world's OreSpawn profile and require a restart to change.

Existing world profiles do not gain these rules automatically. Updating the mod
does not remove their background diamonds or change old chunks. The new block
and its item stay registered even when generation is disabled.

Diamond pipes can have a stepped surface entrance and short underground crosscuts. New
defaults attempt these at every pipe where safe, as they do for iron and coal.
River placers stay untouched. Existing settings are preserved; see
[abandoned mines](ABANDONED_MINES.md). Mineralogy
is optional; its proposed new rocks and the wider mod integrations are separate
future work.

The current terrain adapters support normal and flat Overworld generation.
Unsupported generators are reported and skipped rather than loading nearby
chunks to guess a site. If that warning appears, disable the pipe rule in
OreSpawn to keep the ordinary diamond supply.
