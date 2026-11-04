# Try the lapis update

Use the **RealisticDeposits 110 Lapis** Eclipse workspace and import its nested
`RealisticDeposits` Gradle project. Gold's workspace and saves are separate.

Refresh Gradle, then use **runLapisDepositsClient** in Eclipse's launch list.
The **prepareLapisDepositsManual** Gradle task prepares that launch and its
separate settings if needed. It does not overwrite existing settings or saves.

## A mine to explore

Create a new Creative world with cheats, the normal world type, structures on,
and seed **3346071746553592696**. Leave the supplied deposit settings unchanged.

Use:

```text
/tp @p 99516 70 117923
```

The entrance is at 99516,69,117923. Follow the stepped access to the gallery
near **99508,49,117931**. Look in the roof and sides for lapis, then follow the
thin lens out into its richer pockets. The workings deliberately leave most
of the ore for you to mine.

This site was checked with ordinary structures enabled and through save/reload.
Water, trees, infested stone and other protected blocks can seal sections at
other sites. An attempted mine is not a promise of a clear route everywhere.

For a closer look, switch to Spectator mode with `/gamemode 3` and use Night
Vision. Try both a normal pickaxe and Fortune; Silk Touch should give the ore
block instead. In this Minecraft version, normal mining yields four to eight
lapis pieces, not just one.

## Compare Mineralogy

Use a separate new world with the compatible Mineralogy build installed.
Do not add or remove it from your original saves for this test.
For this launch, its jar goes in `run-lapis-deposits/mods`. Restart Minecraft
after adding it, and make sure physical strata are enabled in OreSpawn for
the new world before making the comparison.

On the same seed, a checked marble-hosted lens is near **33201,44,39920**.
Teleport above it with `/tp @p 33201 100 39920`, then use Spectator mode to look
down through the rock. This is an ore-inspection stop, not a verified open
mine entrance. The surrounding host should be natural marble, without a
new carrier block or manufactured layer.

With physical strata disabled, use another new world. The lenses should use
natural vanilla stone variants instead. Polished stone and decorative marble
must not become ore hosts.

## Reload and settings

Save and quit normally, then reload. Revisit mined blocks, the gallery and
any blocks you placed yourself. They should remain as you left them. Explore
new chunks afterwards; generation must not rewrite the earlier terrain.

The settings file is `config/realisticdeposits/deposits/lapis.json` inside this
launch's separate `run-lapis-deposits` directory. Restart after changing lapis
settings. Existing worlds retain their captured OreSpawn profiles, so use a
fresh world when comparing defaults rather than editing an important save.

The default background supply is 15%. The automated standalone survey estimated
about 16% of vanilla's total lapis rewards, with rare districts averaging about
1,900 ore blocks before other-ore, structure and workings collisions. That is
roughly 11,000 lapis pieces per average discovery before Fortune, not a promise
for every deposit. The settings were not tuned to a vanilla-equivalent total.

Automated checks and cross-sections help establish shape and quantities; your
visual check is still useful for judging how the deposit feels to explore.
