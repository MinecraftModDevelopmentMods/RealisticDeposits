# Abandoned mines

Some deposits can have old workings: a timber-framed surface shaft, a ladder
down to the seam, a short main gallery and a couple of side passages. Broken
rails, cobwebs and small piles of fallen rock give them an abandoned look.
Most of the ore is left for you to mine. These workings contain no loot or
spawners, and they do not replace Minecraft's own mineshafts.

The feature is experimental and **off by default**. Older configuration files
remain off without needing any changes.

## Enable it

Before creating a world, add this section to a deposit in
`config/realisticdeposits/deposits/vanilla.json`:

```json
"abandoned_mine": {
  "enabled": true,
  "chance": 0.15
}
```

`chance` is the chance of attempting workings for each selected deposit, not
for each chunk. `0.15` means 15%; `0` disables attempts and `1` attempts every
deposit. Different deposit definitions can use different values.

Not every attempt finds a suitable site. This first version looks for dry,
reasonably level, open ground in the Overworld. It skips wooded entrances,
oceans, rivers, exposed galleries, excessive shaft depths and known structure
collisions. It currently supports the normal and superflat terrain generators;
unrecognised terrain generators are left alone.

The seed and deposit settings choose the layout. Site safety still takes
priority: if custom deposits overlap, an already accepted mine keeps its space.
This first version does not join neighbouring mines into a larger complex.

## Saved worlds

OreSpawn captures deposit settings for each world. Changing a catalog does not
silently enable mines in an older world or replace its saved settings. Use a
new test world when trying the feature for the first time.

Accepted mines keep their layout in the world save. Their remaining sections
can finish as new terrain is populated, including after a restart. Completed
chunks are never excavated again. Chunks saved before the feature was enabled
are protected, even if they had not yet been populated. A raw chunk first
created for a new mine can finish its initial population later, but only while
the saved plan still records that section as unbuilt.

If later generation places liquid or another structure in a planned section,
the mine leaves that section sealed rather than cutting into it. The server
log reports the obstruction. Clearing tunnels does not create item drops.

Back up important worlds before installing an experimental build. For an
isolated visual test, follow the [testing walkthrough](../tools/qualification/MINES_TESTING.md).
