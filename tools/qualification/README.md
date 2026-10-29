# Forge 1.10.2 generation qualification

This opt-in check runs the packaged mod on disposable dedicated servers. It
does not open the ordinary development saves or run during a normal build.

The matrix covers additive, default 25% hybrid, shipped 15% hybrid and
replacement policies, each with Manage Vanilla Ores off and on. Each policy
is checked with native defaults, customized OS1 settings, customized OS3
settings and an existing OS4 override that conflicts with the old OS3 file.

Every case starts fresh, reloads, generates a distant sample, then reloads
again. It checks that:

- ordinary iron is reduced in hybrid mode and absent in replacement mode;
- the large deposit remains active, keeps the same planned shape across
  policies and leaves no eligible stone unfilled;
- imported heights, frequency, quantity and disabled rules are preserved;
- imported vanilla rules do not add a duplicate native/default pass;
- existing overrides and legacy JSON files are not rewritten, while old
  `.cfg` values survive Forge's normal formatting and added current options;
- saved world settings take precedence over changed future-world defaults;
- existing sample terrain is unchanged and old chunks are not regenerated.

A complete matrix pass also checks switching an existing OS3-imported world
from management off to on and back off, without rewriting its ore rules or
previously generated chunks.

The fixture uses a stone-filled flat world. Ordinary iron remains below Y 96;
the test deposits are at Y 100–119, so the two sources can be counted
independently. These are test settings, not new gameplay defaults.

## Coalfield checks

Coalfield checks use the published OreSpawn 4.1.4.110021 dependency and its
official checksum. The exact-host API is included in that release; a local
OreSpawn build is not needed.

Use `qualify_110.py --resource coal` to run the same policy/import matrix for
coal. Its controlled beds are at Y 180-199 in a stone-filled test world, above
ordinary background coal. This separates the two sources without changing the
normal coalfield height range.

`coal_110.py` checks normal and flat terrain, optional Mineralogy strata, inactive
strata, structure exclusion and coal workings through first load, reload and
later chunks. `coal_upgrade_110.py` compares disposable old iron-only worlds with
old-build controls, including legacy coal rock layers. Neither opens a player's
world. All helpers require new output directories and preserve failed evidence.

`coal_survey_110.py` compares a widely spaced fixed sample with a matched world
whose coalfields are disabled. It also records regional selection, approximate
whole-field reserves and native Fortune III rewards. Its reserve estimate is
before cave, surface and host rejection, not a guarantee of recoverable coal.
See the [coal walkthrough](COAL_TESTING.md) for the isolated visual-test launch.

## Run it

For the imported-profile editor check, use the separate
[visual-test walkthrough](EDITOR_TESTING.md). It opens four disposable
profiles with a checksum-verified OreSpawn candidate, without changing normal
launches or the public dependency pin.

Use the normal Java 17 Gradle environment and Java 8 compilation toolchain:

```powershell
.\gradlew.bat check build preparePolicyQualification --init-script tools/qualification/probes.init.gradle
python -m unittest discover -s tools/qualification -p 'test_*.py'
```

Then run `python tools/qualification/qualify_110.py` with these arguments:

| Argument | Input |
| --- | --- |
| `--java` | Java 8 executable |
| `--runtime` | An official Forge 1.10.2-12.18.3.2511 server installation, including libraries and an already accepted EULA |
| `--mod` | The built Realistic Deposits release jar |
| `--orespawn` | Public OreSpawn 4.1.4.110021; its official checksum is checked |
| `--probe` | `build/qualification/policy-probe.jar` |
| `--output` | A new disposable evidence directory, preferably under an ignored `run-*` folder |
| `--jobs` | Concurrent servers; defaults to two |
| `--base-port` | First loopback port; use different ranges if running coal and iron matrices together |

Supply absolute paths. No third-party Python packages are needed. On Windows,
servers run without console windows, with literal `nogui`, on separate
loopback ports. Previous output directories are never reused. Failures keep
their logs, crash reports and worlds for inspection.

To test an unpublished OreSpawn fix, also supply
`--local-orespawn-sha256 <complete SHA-256>`. This opt-in verifies the exact
candidate bytes and records the run as local in `artifacts.json`; it does not
change the mod's public dependency pin or authorize publication. Without it,
the public-release checksum remains mandatory.

`results.json` records the outcome and counts. The individual directories also
contain the active profiles, sample terrain hashes and logs from each boot.

## Check an existing published-release world

`upgrade_110.py` copies worlds from the earlier OreSpawn 4.1.2 matrix before
installing the new OreSpawn release. Supply `--java`, `--orespawn`,
`--probe`, `--source-matrix`, `--output` and optionally `--mod` and `--jobs`.
Use the original matrix directory as `--source-matrix`, not an active
game save. The script never changes that source directory.

It checks the nine affected imported hybrid/replacement cases, additive and
native controls, and an explicit custom channel. Each copy reloads its old
samples, generates a distant new sample, then reloads again. Old blocks,
imported files and ore settings must remain unchanged; new ordinary iron must
follow the selected policy. The custom-channel control must remain independent
even in replacement mode.

## Imported-profile regression

OreSpawn 4.1.2 could treat an imported built-in pattern as an independent
custom channel. Hybrid and replacement limits then missed that ordinary ore.
The regression checks require 4.1.3 to reduce imported ordinary generation
while preserving explicit custom channels, saved settings and old terrain.

CI prepares an official Forge server from a checksum-pinned installer and
runs the complete 32-case fresh/reload/later-chunk matrix. Failures retain
their logs and result reports as workflow artifacts. The optional local
upgrade suite also checks disposable copies of worlds made before the fix.
