# Trying the abandoned mines

Use this separate profile so your ordinary saves and settings stay untouched.

1. Refresh the Gradle project in Eclipse, then run **prepareAbandonedMinesManual**
   from Gradle Tasks. It prepares a separate configuration with mines enabled
   for every suitable deposit. Running it again keeps your existing edits.
2. Launch **runAbandonedMinesClient.launch**.
3. Create a Creative world using seed **3346071746553592696**, normal world type,
   structures enabled and cheats allowed. Leave OreSpawn's terrain defaults
   unchanged for this comparison.
4. Run `/tp 15222 73 23842`. The tested entrance is around **15222, 68, 23842**,
   with the landing around **15222, 49, 23842**.

Look for the timber-framed opening and ladder. Walk down rather than using
Spectator mode first: check headroom, footing, ladder access and the junctions.
The workings should follow a short part of the seam, with ore still visible in
the surrounding rock. Broken rails, timber supports, cobwebs and small rubble
piles are intentional. An obstruction reported in the log should remain sealed.

Save, quit and reopen the world. Check that the same entrance and passages are
still there. Place a distinctive block in a finished passage before quitting;
it must remain in place. The automated server checks separately cover mines
whose remaining chunks are not generated until after a restart.

The profile uses `run-abandoned-mines`. It does not change `run`, `run-major-iron`
or their saved worlds. The prepared catalog uses a chance of `1.0` only to make
testing easier; normal definitions default to disabled, with a suggested chance
of `0.15` when enabled. To check opt-out, change `enabled` to `false`, restart
and create another new world. Existing workings are not removed.

## Automated checks

The separate `prepareMineQualification` task, supplied by
`tools/qualification/probes.init.gradle`, builds a packaged-server probe. Run
`mines_110.py --help` for its inputs. Use the exact public OreSpawn jar and an
official Forge server installation with an already accepted EULA.

Each run requires a new output directory and retains failed worlds and logs.
The probe is not included in public jars or the ordinary Eclipse profiles.

The checks include normal and flat terrain, generation in reversed and shuffled
chunk orders, reloads, unfinished sections, disabled settings, underwater sites,
known structure boxes and a chest obstructing a planned tunnel. The separate
`mines_upgrade_110.py` helper creates worlds with the published 0.1.1 alpha,
then upgrades disposable copies. It checks old terrain and chest contents on
first load, reload and when a previously unopened saved chunk is populated.
