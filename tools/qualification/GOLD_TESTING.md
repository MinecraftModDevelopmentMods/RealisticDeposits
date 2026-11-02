# A walk around the goldfields

Use the separate `RealisticDeposits 110 Gold` workspace for this local candidate.
Import its nested Gradle project into Eclipse, then run `prepareGoldDepositsManual`
from Gradle Tasks. Launch `runGoldDepositsClient`.

The launch keeps its configuration and saves in `run-gold-deposits`. It does not
use or change the ordinary 0.3 testing worlds. Preparation copies only missing
catalogues; it does not overwrite settings you have edited.

Create a fresh Creative world with cheats and seed `3346071746553592696`.
Leave Generate Structures on; both locations below were checked that way.
Existing worlds deliberately keep their captured rules and are not a shortcut
for testing newly added deposits.

## What to look for

- Enter a gold mine through its gradual surface entrance. Follow the main
  gallery and inspect the surrounding veins in Spectator mode.
- Look for steep, narrow bodies with branches, pinched sections and richer
  pockets. The entire fracture is not a wall of solid gold.
- Mine gold-bearing gravel from a river pocket. It leaves ordinary gravel and
  sometimes a nugget. Try Fortune and Silk Touch, then place the spent gravel
  again: it must not provide more gold.
- Save, quit and reload. Mine plans, excavated blocks and stored rich gravel
  must remain intact.
- Check the gravel in your inventory and in the world. Its small gold flecks
  should not obscure the gravel background. A resource pack can replace both
  vanilla textures.

Gold uses existing country rock. There is no vein-quartz block in this update,
and quartzite is not standing in for one. With Mineralogy active, inspect how
the veins cut across suitable natural rocks rather than following coal-like
bedding. Installing Mineralogy is optional.

## A tested mine

Without Mineralogy, the seed above has a tested entrance at **172331, 69,
187683**, with its main gallery near **172317, 34, 187699**. In Creative mode,
use `/tp @p 172331 70 187683`, then enter on foot. The packaged checks followed
the complete route and verified it again after saving and reloading.

With Mineralogy installed, other ore bodies may block parts of that route.
Water and protected blocks can also leave a section sealed. The workings do
not remove those obstacles just to finish a tunnel.

A placer is not guaranteed beside every entrance. For a quick gravel drop
test, take Gold-bearing Gravel from Creative inventory and place it on a
solid block before mining it. Use a fresh rich block for each trial: the
ordinary gravel left behind has already been spent.

## A natural river pocket

The same seed has gold-bearing gravel near **278056, 50, 272928**, below the
water beside the riverbank. Use `/tp @p 278056 66 272928`, then swim down or
switch to Spectator mode to look at the sediment. Night Vision helps. This
pocket was checked with normal structures enabled and again after reloading.

The source district is nearby, around **277321, 272526**. The river is not
reshaped to point towards it, and the link does not simulate a whole watershed.
This is a separate stop from the mine above; they are far apart, so teleport
between them for this test rather than walking.

Try a normal shovel, Fortune and Silk Touch on separate rich blocks. Expect
roughly one nugget per four blocks before Fortune, not a nugget every time.
Place the spent ordinary gravel and mine it again: it must not produce gold.
Silk-touched rich gravel should keep its identity when placed or allowed to fall.

## Things worth checking before signing it off

1. Walk the mine entrance and follow the gallery. Check that the slope feels
   comfortable and that water, buildings and other ores are left alone.
2. Look around the veins in Spectator mode. Compare rich shoots, lean sections
   and branches rather than expecting a continuous wall of gold.
3. Visit the river pocket and try the different harvesting methods above.
4. Save, quit and reload. Revisit both places and check your stored rich gravel.
5. Try a texture pack that changes vanilla gravel; the rich gravel should use
   that background too. The automated client also checks this replacement.

The defaults are scarcer overall than vanilla. The survey found about 22% of
vanilla's generated gold, concentrated into large fields rather than spread evenly.
Please judge discovery, mine size and rewards as well as the appearance.

For a Mineralogy comparison, use its compatible 1.10.2 release in a separate
test world. These exact coordinates are the standalone example, not a promise
of the same open tunnel or sediment pocket with different terrain settings.
