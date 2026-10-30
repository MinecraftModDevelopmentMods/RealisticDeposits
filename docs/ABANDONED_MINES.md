# Abandoned mines

Underground deposits can have old workings: a timber-framed entrance, a winding
stepped tunnel, a short main gallery and a couple of side passages. Broken
rails, cobwebs and small piles of fallen rock give them an abandoned look.
Most of the ore is left for you to mine. These workings contain no loot or
spawners, and they do not replace Minecraft's own mineshafts.

Fresh catalogues enable workings for iron formations, coalfields and diamond
pipes, with an attempt at every deposit where safe. Short stair runs and level
turning landings make the descent walkable in both directions, without a long
open drop. Guardrails protect the exposed approach. Iron and coal galleries
follow a seam; diamond workings make short crosscuts through one pipe. River
placers have no shafts or surface diggings.

Existing catalogues and world settings are not overwritten. An older world
with workings disabled keeps that choice.

## Settings

Before creating a world, check the deposit's `abandoned_mine` section in
`config/realisticdeposits/deposits/vanilla.json`, `coal.json` or `diamonds.json`.
The new defaults are:

```json
"abandoned_mine": {
  "enabled": true,
  "chance": 1.0
}
```

`chance` is the chance of attempting workings for each selected deposit, not
for each chunk. `0.15` means 15%; `0` disables attempts and `1` attempts every
deposit. Set `enabled` to `false` to turn workings off. Different deposit
definitions can use different values. Older definitions without this section
remain off.

Not every attempt finds a suitable site. The planner looks for dry,
reasonably level, open ground in the Overworld. It skips wooded entrances,
oceans, rivers, exposed galleries, excessive depths and known structure
collisions. It currently supports the normal and superflat terrain generators;
unrecognised terrain generators are left alone.

The seed and deposit settings choose the layout. Site safety still takes
priority: if custom deposits overlap, an already accepted mine keeps its space.
It does not join neighbouring mines into a larger complex.

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

Older ladder shafts are not rebuilt when you update. Their saved sections keep
the original layout; the stepped entrances apply only to newly planned mines.

If later generation places liquid or another structure in a planned section,
the mine leaves that section sealed rather than cutting into it. The server
log reports the obstruction. Clearing tunnels does not create item drops.

Back up important worlds before installing an experimental build. For an
isolated visual test, follow the [testing walkthrough](../tools/qualification/MINES_TESTING.md).
