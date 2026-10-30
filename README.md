# Realistic Deposits

[![Build](https://github.com/MinecraftModDevelopmentMods/RealisticDeposits/actions/workflows/ci.yml/badge.svg?branch=master-1.10.2)](https://github.com/MinecraftModDevelopmentMods/RealisticDeposits/actions/workflows/ci.yml)
[![CodeQL](https://github.com/MinecraftModDevelopmentMods/RealisticDeposits/actions/workflows/codeql-analysis.yml/badge.svg?branch=master-1.10.2)](https://github.com/MinecraftModDevelopmentMods/RealisticDeposits/actions/workflows/codeql-analysis.yml)

![Realistic Deposits logo](src/main/resources/assets/realisticdeposits/textures/logo.png)

Realistic Deposits makes finding ore a reason to explore, then build a proper
mine. Rare iron districts, layered coalfields and diamond pipes give you
deposits worth returning to, with old workings leading into some of them.
Small pockets of diamond-bearing river gravel offer another way to prospect.
It runs on [MMD OreSpawn](https://github.com/MinecraftModDevelopmentMods/OreSpawn).

The project starts with Minecraft 1.10.2 and is intended to follow the
maintained OreSpawn branches across Forge and NeoForge.

Version 0.3.0.110021 is a beta for Minecraft 1.10.2. It adds diamond deposits
and replaces deep ladder shafts in new workings with stepped entrances.
Existing worlds keep their settings and saved mine layouts. Back up important
worlds before updating.

## Current features

- Rare, region-scale deposits that remain identical regardless of chunk
  generation order.
- Long, folded `stratiform_seam` formations instead of enlarged spherical ore
  blobs. The experimental iron default is a major mining district with an
  irregular principal horizon, five to seven splays, two or three subparallel
  lenses, and short fault displacements. A sparse halo gives prospecting clues
  around the fault gaps instead of making the displaced ore impossible to
  rediscover. Each chunk writes only its own deterministic part of a deposit.
- Massive-and-rare, geome-aware, and host-rock-aware generation modes.
- Coalfields with several thin beds, folds, fault offsets and local rock
  partings. Existing Mineralogy sedimentary rocks are used when available;
  Mineralogy is not required. See [coalfields](docs/COALFIELDS.md).
- Additive, hybrid, and replacement policies for controlling interaction with
  ordinary ore generation.
- Rare diamond pipes and linked river placers, without adding new host rocks
  or reshaping rivers. See [diamond deposits](docs/DIAMONDS.md).
- Configurable support for vanilla and modded resources.
- Safe generation that avoids air, fluids, bedrock, block entities, structures,
  and undeclared host blocks.
- Live reload for iron and coal geometry. Diamond geometry and other structural
  changes wait until restart.
- An entry in both Forge's Mods configuration and OreSpawn's World Settings.
- Abandoned workings with sloping, stepped entrances and short underground galleries.
  New defaults attempt them at every iron formation, coalfield and diamond
  pipe where safe. River placers stay untouched. See the [mine settings](docs/ABANDONED_MINES.md).

Coalfields can already use Mineralogy's existing shale, siltstone and
conglomerate. Wider catalogues for Mineralogy, Base Metals, Modern Metals,
Base Minerals and Base Gems remain future work.

## Requirements

The current branch requires:

- Minecraft 1.10.2;
- Forge 12.18.3.2511 or a compatible newer Forge 12 build;
- [MMD OreSpawn 4.1.4.110021](https://www.curseforge.com/minecraft/mc-mods/mmd-orespawn/files/9356289)
  or a newer compatible 1.10.2 release, but earlier than OreSpawn 5;
- Java 8 to run Minecraft.

Realistic Deposits is independent of the similarly themed Large Ore Deposits
mod and does not require it.

## Configuration

Install Forge, OreSpawn, and Realistic Deposits in the usual `mods` directory.
The first launch creates:

```text
config/realisticdeposits/deposits/vanilla.json
config/realisticdeposits/deposits/coal.json
config/realisticdeposits/deposits/diamonds.json
```

The supplied definition describes one rare iron district using a 640–960 block
folded horizon, a 160–240 block down-dip span, and a 3–8 block thickness. Its
`density` controls the disseminated halo around the connected mineralized
routes rather than producing a uniformly random cloud. Two or three faults
introduce short, recoverable breaks and modest vertical or lateral offsets.
The shipped iron definition uses `hybrid` interaction with 15% background iron
so early play remains viable while a discovered district can support a
long-lived mine and rail network.

The separate coal catalogue adds two to four beds across a large coalfield,
keeping 15% ordinary background coal. It favours sedimentary and wetland basins
while allowing sedimentary cover in some other geological regions. Coalfields
use plain stone without physical strata, or suitable existing sedimentary
rocks when those strata are active. Old catalogues are never overwritten, and
old worlds do not receive new coal rules automatically.

Deposits only affect newly generated chunks. Changing these settings does not
retrogen, replace, or rewrite an existing chunk.

Every definition chooses one alignment mode:

- `massive_rare` selects formations without requiring OreSpawn geology;
- `geome_aligned` requires the formation centre to be in one of its configured
  `geomes`;
- `rock_aligned` also requires each output block to be in one of its configured
  `rock_families`.

Aligned candidates are safely rejected if the current dimension has no active
OreSpawn geology sampler. The available families are `sedimentary`,
`metamorphic`, `igneous_intrusive`, and `igneous_volcanic`.

The `interaction` field controls ordinary generation for the same resource and
dimension:

- `additive` leaves it unchanged;
- `hybrid` scales it to 25% by default, or to
  `background_generation_scale` when specified;
- `replacement` disables it while leaving the massive deposit active.

OreSpawn 4.1.3 applies those limits to imported OS1/OS3 ordinary rules too.
Explicit custom placement channels remain independent. Manage Vanilla Ores
must be on to activate imported vanilla rules. When it is off, those rules
stay inactive and Minecraft supplies the ordinary placements instead.
Realistic Deposits' selected policy still controls the background supply.
Saved settings and existing chunks are preserved.

Biome IDs and Forge biome-dictionary filters can be supplied with `biome_ids`,
`excluded_biome_ids`, `biome_dictionary`, and
`excluded_biome_dictionary`.

Use the diagnostic commands to inspect the loaded catalog and preview planned
formations:

```text
/realisticdeposits status
/realisticdeposits preview <regionX> <regionZ> [deposit-id]
/realisticdeposits reload
```

`reload` requires operator permission. For iron and coal it applies `enabled`,
selection, shape, height, geome, family, and alignment changes. Diamond geometry
and source links require a restart. Resource,
dimension, host, biome-filter, interaction-policy, scale, added, and removed
definition changes are structural and take effect after restart. Abandoned-mine
settings are also captured with the world's deposit definition. New provider
entries are deliberately not injected into a world profile that already
exists.

The configuration screen is available from Forge's Mods list and OreSpawn's
World Settings screen.

## Building from source

The Gradle build runs on Java 17 and compiles the mod for Java 8 using the
configured toolchain. ForgeGradle's Minecraft preparation utility uses Java 25.

OreSpawn 4.1.4.110021 is downloaded from Curse Maven, checked against its
official checksum and mapped for development automatically. The exact dependency
is `curse.maven:mmd-orespawn-245586:9356289`. No sibling checkout, local jar or
`local.properties` file is needed.

An optional local-candidate override is still available for development, but it
must name an isolated repository and a complete checksum. Publication is blocked
whenever that override is selected. Normal builds use the public release.

On Windows, run:

```powershell
.\gradlew.bat clean check build
.\gradlew.bat genEclipseRuns verifyEclipseProductionClasspath
```

Import the project into Eclipse through Buildship after generating the Forge
run configurations.

For a separate test world, use `runMajorIronClient.launch`. It keeps its saves
and configuration in `run-major-iron`, apart from the ordinary client launch.

The [generation qualification guide](tools/qualification/README.md) describes
the optional packaged-server checks for policies, imports and saved worlds.
The [mine-testing walkthrough](tools/qualification/MINES_TESTING.md) uses a
separate Eclipse profile and disposable saves for the new workings.
For coalfields, use `prepareCoalMeasuresManual` and the
[coal-testing walkthrough](tools/qualification/COAL_TESTING.md).

CI also checks reproducible jars, dependency hashes and ordinary Eclipse
launches. The [release guide](docs/RELEASING.md) explains the manual release
action and its publication checks.

Bug reports and feature ideas are welcome through the
[issue tracker](https://github.com/MinecraftModDevelopmentMods/RealisticDeposits/issues).

## Links

- [CurseForge project](https://www.curseforge.com/minecraft/mc-mods/realisticdeposits)
- [MMD OreSpawn](https://github.com/MinecraftModDevelopmentMods/OreSpawn)
- [Source repository](https://github.com/MinecraftModDevelopmentMods/RealisticDeposits)

## License

Realistic Deposits is licensed under the
[GNU Lesser General Public License version 2.1](LICENSE).
