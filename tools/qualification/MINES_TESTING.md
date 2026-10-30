# Trying the abandoned mines

Use a new world for these checks. Existing worlds keep their captured settings
and any mines they already contain.

1. Refresh the Gradle project in Eclipse, then run **prepareAbandonedMinesManual**
   from Gradle Tasks. It prepares a separate configuration with mines enabled
   for every suitable deposit. Running it again keeps your existing edits.
2. Launch **runAbandonedMinesClient.launch**.
3. Create a Creative world using seed **3346071746553592696**, normal world type,
   structures enabled and cheats allowed. Leave OreSpawn's terrain defaults
   unchanged for this comparison.
4. For a deep diamond mine, use **runUndergroundMinesClient.launch** instead,
   with the same world settings. Run `/tp 49521 75 56635`. The entrance is at
   **49521, 67, 56635**, and the landing at **49497, 24, 56653**.

Look for the timber-framed opening and stepped tunnel. Walk down rather than
using Spectator mode first: check headroom, footing, turning landings and the junctions.
Walk back up without jumping. There should be no long open drop to bedrock.
The workings should follow a short part of the seam, with ore still visible in
the surrounding rock. Broken rails, timber supports, cobwebs and small rubble
piles are intentional. An obstruction reported in the log should remain sealed.

Save, quit and reopen the world. Check that the same entrance and passages are
still there. Place a distinctive block in a finished passage before quitting;
it must remain in place. The automated server checks separately cover mines
whose remaining chunks are not generated until after a restart.

The separate profiles use `run-abandoned-mines` and `run-underground-mines`.
Fresh catalogues attempt workings at every iron formation, coalfield and
diamond pipe where safe. River placers are unchanged. To check opt-out, change
`enabled` to `false`, restart and create another new world. Existing workings
are not removed.

## Using the regular client

The local `runClient` configuration now enables the same mine attempts. Its
older iron distribution settings have been kept; only the mine setting was
added. Coal and diamond catalogues were installed because they were absent.
Restart Minecraft and create a new world using the seed and settings above.
The diamond entrance coordinates above also work with this configuration.

No existing saves have been changed. Older worlds keep their saved OreSpawn
profiles, and any old ladder shafts retain their original layout. The new
stepped access applies only to newly planned mines.

## Automated checks

The separate `prepareMineQualification` task, supplied by
`tools/qualification/probes.init.gradle`, builds a packaged-server probe. Run
`mines_110.py --help` for its inputs. Use the exact public OreSpawn jar and an
official Forge server installation with an already accepted EULA.

Each run requires a new output directory and retains failed worlds and logs.
The probe is not included in public jars or the ordinary Eclipse profiles.

The checks include normal and flat terrain, generation in reversed and shuffled
chunk orders, reloads, unfinished sections, disabled settings, underwater sites,
known structure boxes and a chest obstructing a planned tunnel. Native movement
checks walk the new stairs in both directions. A Mineralogy spring fixture
checks that a flooded section remains sealed. The separate
`mines_upgrade_110.py` helper creates worlds with the published 0.1.1 alpha,
then upgrades disposable copies. It checks old terrain and chest contents on
first load, reload and when a previously unopened saved chunk is populated.
`frozen_mines_110.py` separately compares completion of an older saved ladder
shaft with and without the new planner; the finished blocks must be identical.
