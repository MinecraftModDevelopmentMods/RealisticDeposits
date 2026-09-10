# Realistic Deposits

Rare, massive, and geologically aware ore-deposit planning powered by MMD
OreSpawn.

This repository begins with Minecraft 1.10.2 and Forge 12.18.3.2511. The
initial `0.1.0.110021` alpha is runnable: it validates versioned deposit
catalogs, plans deterministic region-scale stratiform seams, exposes preview
commands, and supplies a Forge Mods configuration screen. It intentionally
does not write blocks yet.

The write path is waiting for small public OreSpawn additions: stable world,
dimension and current-chunk identity, geology sampling in the pattern context,
and a supported extension point for the OreSpawn world-settings screens.
Realistic Deposits will not reflect into or couple to OreSpawn implementation
classes to bypass those contracts. Each chunk can then render its own slice of
the same region-scale body through OreSpawn's existing safe placement method.

## Requirements

- Minecraft 1.10.2
- Forge 12.18.3.2511 or newer compatible Forge 12 build
- MMD OreSpawn 4.0.16 through, but not including, OreSpawn 5
- Java 8 for Minecraft; Java 17 to run the Gradle build

## Development

Import the project through Eclipse Buildship and use the generated ForgeGradle
runs.

## Alpha use

The first launch creates
`config/realisticdeposits/deposits/vanilla.json`. The supplied iron formation
uses a long, thin, folded `stratiform_seam`, not a spherical blob. It can cross
many chunks and is selected from world-seed regions independently of chunk
load order.

Server commands:

- `/realisticdeposits status`
- `/realisticdeposits reload` (operator)
- `/realisticdeposits preview <regionX> <regionZ> [deposit-id]`

The configuration screen is available from Forge's Mods list. Preview and
reload are non-generating operations; this alpha never changes world blocks.

Realistic Deposits is licensed under the GNU Lesser General Public License
version 2.1.
