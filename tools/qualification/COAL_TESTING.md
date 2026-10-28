# Try the coalfields

Use the local OreSpawn candidate already selected for this development checkout.
Do not copy these jars into an important instance or use an original world.

1. Run the Gradle task `prepareCoalMeasuresManual`, then refresh the Gradle
   project in Eclipse.
2. Launch `runCoalMeasuresClient.launch`. It uses its own `run-coal-measures`
   settings and saves, separate from your iron and abandoned-mine tests.
3. Create a normal Creative world with cheats and seed `3346071746553592696`.
4. Enter `/tp 16702 80 18546`. Switch to Spectator with `/gamemode 3` and
   descend through roughly Y 60-35 to inspect the beds.
5. Follow a bed sideways. Look for several separate layers, rock between
   them, gentle changes in level, fault offsets and thinning near the ends.
   Caves and the ground surface can cut through a bed naturally.
6. Return to Creative, mine some coal, and compare an ordinary pickaxe with
   Fortune. The blocks should behave like vanilla coal ore.
7. Save and reload. Revisit the beds and explore farther along the field;
   existing terrain must not change.

The isolated profile attempts abandoned workings at every suitable coalfield.
The coal location above is selected for inspecting the beds, not as a promise
of a safe mine entrance. Public defaults keep workings off. Running the prepare
task again does not overwrite settings you have edited or any saves.

For a coal working in the same normal-world seed, try `/tp 20418 72 53437`.
Its entrance is at Y 68 and the principal gallery is near Y 53. Check the ladder,
walk the short galleries beside the coal, then save and reload before following
the bed into new chunks. These coordinates assume the isolated profile's
workings setting was enabled before you created the world.

The packaged `coal_110.py` helper separately checks standalone terrain,
Mineralogy strata and reloads. Its reports can be drawn with `coal_maps.py`
(Pillow required) to compare a labelled cross-section with the in-game view.
The helper always requires a new evidence directory and the exact OreSpawn
candidate checksum. Test probes never belong in ordinary launches or public jars.

## What the fixed-seed survey found

Across 256 regions, 116 passed the initial selection and 24 also passed the
geological checks. Approximate whole-field reserves ranged from 142,080 to
1,027,520 coal blocks before caves, surface height and host rocks reduced them.

An evenly spaced 81-chunk sample averaged 43.2 coal blocks per chunk with the
new defaults, compared with 187.7 in the matched vanilla-coal control. Two sample
chunks contained at least 400 coal blocks; none did in the control. Coal is much
less evenly spread, so finding a field matters more than those averages suggest.
This is one seed and a limited sample, not a promise for every world.

Native Fortune III averaged 2.1988 coal items per ore over 10,000 trials. That
would turn those sample averages into roughly 95 and 413 coal items per chunk,
assuming every ore block could be found and mined. Unenchanted mining yields
one item per block. Neither figure includes Silk Touch recycling or a new
processing bonus; the mod adds neither.
