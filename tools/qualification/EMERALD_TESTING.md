# Try the emerald update

Use the existing **RealisticDeposits 110** Eclipse workspace. Refresh the
Gradle project and start the regular **runClient** launch. No Emerald-specific
launch is required. Fresh installations get the updated emerald catalogue.
If your local config was created for an earlier candidate, back it up and
copy the shipped `emerald.json` over it if it is uncustomised, or copy just
`shape.vein_trace`, `shape.rich_find` and `shape.large_mine` if those
settings are missing. Keep any custom values you already have.
OreSpawn may also have the old rule in `run/config/orespawn-worldgen.json`.
If so, back up that file and add the missing fields under
`ores` → `realisticdeposits:deposit/realisticdeposits/overworld_emerald_pockets`
→ `dimensions` → `minecraft:overworld` → `pattern.settings.definition.shape`.
If the rule already has the larger-find settings, just add
`"workings": true` inside its `rich_find` object and inside the matching
catalogue object. Keep your existing chance, yield and other values.
If you were already testing the linked rich workings, only
`shape.large_mine` is new. It goes alongside `rich_find`, not inside it.
If you have the earlier large-mine settings, the revised shipped value
for `shape.large_mine.chance` is `0.08`. Changing a template affects
new worlds; the prepared test save and other saved profiles are unchanged.
Leave the other rules and your existing saves alone. Restart Minecraft after
updating the files.

## Walk the short crosscut

Create a fresh Creative world with cheats, the normal world type, structures
on, and seed **3346071746553592696**. For this walk, leave physical Mineralogy
strata off. Don't reuse the world from the earlier walkthrough: its generated
chunks and saved mine layout won't be rewritten.

```text
/tp @p 32869 85 12728
```

Follow the stairs around the turns to the landing at **32875, 38, 12729**.
The short crosscut leads to an exposed emerald face. Mine that pocket, then
look for pale diorite where the ore ends and follow it without using
Spectator or the preview command. The trace can step up or down with the
pockets. A site can have emerald pockets without any entrance: unsafe
approaches are skipped, including the site from the earlier walkthrough.

## Check a rich (medium) find

In a fresh world with the same settings and seed, use the regular
`runClient` launch and teleport above the entrance:

```text
/tp @p 22043 89 86234
```

Take the stairs down to **22035, 46, 86227**. Follow the timbered headings
to two separate exposed pockets; both routes were walked in the ordinary
client. Look for pale diorite where the workings end if you want to
prospect for more. This is region `21 84` in the preview command.

### Separate packaged-server example

With the same seed, structures on and physical strata off, the packaged
server generated a different open mine. These coordinates are not the
regular-client walkthrough: infested stone blocked this route in the
ordinary client test.

```text
/tp @p 66019 80 -14364
```

On the server, take the stairs to the landing at **66019, 19, -14365**. Follow the timbered
headings to two separate exposed faces. The packaged test found **111 of
116 planned ore blocks** across four pockets on a 69-block trend. The
workings reach two of those pockets; use the diorite trace to prospect for
the other two. The preview is useful if you want to check the plan:

```text
/realisticdeposits preview 64 -15 realisticdeposits:overworld_emerald_pockets
```

It describes the plan, not the ore that survives generation or whether
an entrance is safe to open.

## Prospect an exceptional large mine

If **Emerald Large Mine - Test** appears in Singleplayer, open it from the
standard `runClient` launch. This optional disposable save starts you in
Creative at the verified entrance, facing the stairs. No teleport, new
world, or configuration change is needed to use that save.

To generate the example yourself:

Use regular `runClient` and a fresh Creative world with the same seed
**3346071746553592696**, normal world type, structures on and physical
Mineralogy strata off. Make sure the new `shape.large_mine` is present.

```text
/tp @p 37193 79 35107
```

Take the stairs to **37194, 30, 35107** and explore both headings. Look
for pale diorite behind the ore on the north side of the worked zone.
The trace trends north-west toward an unworked zone around
**37175, 29, 35066**. Try following the cue before using those coordinates.
If a cave breaks the trace, look for it on the far side rather than
expecting a continuous emerald seam.

This is region `36 34`: five zones, ten pockets and **369 planned ore
blocks**. The regular client walked to two pockets, and its saved chunks
contain an intact clue route out of the worked zone. The other zones are
not pre-excavated. Natural decorations or chunk order can shift the
portal slightly; the packaged-server entrance was one block to the east.

## See a pocket in schist

Use a fresh disposable world with compatible Mineralogy installed and
OreSpawn's physical Overworld strata active. Keep natural schist enabled.
The packaged strata check used Mineralogy **6.0.2.110021** and the same
seed. The verified pocket is near **24192, 35, -9837**; use Spectator to
inspect it. This is an ore-inspection site, not a promise of a clear mine
entrance. Different geology profiles can move or remove the pocket.

```text
/realisticdeposits preview 23 -10 realisticdeposits:overworld_emerald_pockets
```

Try an iron pickaxe and Fortune, or use Silk Touch to collect the vanilla
ore block. The new deposits do not reduce the normal emerald supply.
