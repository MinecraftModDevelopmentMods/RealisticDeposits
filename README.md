# Realistic Deposits

Rare, massive, and geologically aware ore deposits powered by MMD OreSpawn.

This repository begins with Minecraft 1.10.2 and Forge 12.18.3.2511. The
initial `0.1.0.110021` framework registers the mod and its required OreSpawn
dependency but intentionally generates no deposits until the generation and
save-stability contracts are implemented and tested.

## Requirements

- Minecraft 1.10.2
- Forge 12.18.3.2511 or newer compatible Forge 12 build
- MMD OreSpawn 4.0.16 through, but not including, OreSpawn 5
- Java 8 for Minecraft; Java 17 to run the Gradle build

## Development

Import the project through Eclipse Buildship and use the generated ForgeGradle
runs. Automated 1.10 server runs use the literal `nogui` argument.

Realistic Deposits is licensed under the GNU Lesser General Public License
version 2.1.
