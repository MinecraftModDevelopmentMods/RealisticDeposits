# Realistic Deposits

![Realistic Deposits logo](src/main/resources/assets/realisticdeposits/textures/logo.png)

Realistic Deposits is an extension for
[MMD OreSpawn](https://github.com/MinecraftModDevelopmentMods/OreSpawn) that
is being built to make valuable minerals feel like geological discoveries.
Instead of scattering a steady supply of small, isolated ore clusters
throughout the world, it plans rare formations large enough to reward
exploration and support sprawling mines, rail networks, and long-term resource
projects.

The project starts with Minecraft 1.10.2 and is intended to follow the
maintained OreSpawn branches across Forge and NeoForge.

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
- Additive, hybrid, and replacement policies for controlling interaction with
  ordinary ore generation.
- Configurable support for vanilla and modded resources.
- Safe generation that avoids air, fluids, bedrock, block entities, structures,
  and undeclared host blocks.
- Live reload for geometry and alignment tuning, with structural changes
  clearly deferred until restart.
- An entry in both Forge's Mods configuration and OreSpawn's World Settings.

Compatibility catalogs for Mineralogy, Base Metals, Modern Metals, Base
Minerals, and Base Gems will be added as those product lines become available
on each target.

## Requirements

The current branch requires:

- Minecraft 1.10.2;
- Forge 12.18.3.2511 or a compatible newer Forge 12 build;
- MMD OreSpawn 4.1.0 or newer, but earlier than OreSpawn 5;
- Java 8 to run Minecraft.

Realistic Deposits is independent of the similarly themed Large Ore Deposits
mod and does not require it.

## Configuration

Install Forge, OreSpawn, and Realistic Deposits in the usual `mods` directory.
The first launch creates:

```text
config/realisticdeposits/deposits/vanilla.json
```

The supplied definition describes one rare iron district using a 640–960 block
folded horizon, a 160–240 block down-dip span, and a 3–8 block thickness. Its
`density` controls the disseminated halo around the connected mineralized
routes rather than producing a uniformly random cloud. Two or three faults
introduce short, recoverable breaks and modest vertical or lateral offsets.
The shipped iron definition uses `hybrid` interaction with 15% background iron
so early play remains viable while a discovered district can support a
long-lived mine and rail network.

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

`reload` requires operator permission. It immediately applies `enabled`,
selection, shape, height, geome, family, and alignment changes. Resource,
dimension, host, biome-filter, interaction-policy, scale, added, and removed
definition changes are structural and take effect after restart. New provider
entries are deliberately not injected into a world profile that already
exists.

The configuration screen is available from Forge's Mods list and OreSpawn's
World Settings screen.

## Building from source

The Gradle build runs on Java 17 and compiles the mod for Java 8 using the
configured toolchain. Until OreSpawn 4.1.0 is published, development is pinned
to its exact local release and mapped development jars. Point an ignored
`local.properties` file at them:

```properties
orespawn.local.jar=D:/path/to/OreSpawn-4.1.0.110021.jar
orespawn.local.deobf.jar=D:/path/to/OreSpawn-4.1.0.110021-deobf.jar
```

The build verifies both artifact hashes and has no fallback to OreSpawn 4.0.x.
On Windows, run:

```powershell
.\gradlew.bat clean check build
.\gradlew.bat genEclipseRuns
```

Import the project into Eclipse through Buildship after generating the Forge
run configurations.

Bug reports and feature ideas are welcome through the
[issue tracker](https://github.com/MinecraftModDevelopmentMods/RealisticDeposits/issues).

## Links

- [CurseForge project](https://www.curseforge.com/minecraft/mc-mods/realisticdeposits)
- [MMD OreSpawn](https://github.com/MinecraftModDevelopmentMods/OreSpawn)
- [Source repository](https://github.com/MinecraftModDevelopmentMods/RealisticDeposits)

## License

Realistic Deposits is licensed under the
[GNU Lesser General Public License version 2.1](LICENSE).
