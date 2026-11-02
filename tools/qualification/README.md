# Forge 1.10.2 generation qualification

This local-only, opt-in check runs the packaged mod on disposable dedicated
servers. It does not open the ordinary development saves or run during a normal
build. Hosted CI/CD runs only builds, unit/helper tests and static/artifact
checks; it must not launch these Minecraft suites.

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

Coalfield checks use the published OreSpawn 4.1.5.110021 dependency and its
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

## Diamond checks

`prepareDiamondQualification`, supplied by `probes.init.gradle`, builds the
separate server probe for `diamonds_110.py`. It checks native gravel harvesting,
Silk Touch, falling and save/reload, explosions, pipes, linked placers, disabled
rules and saved terrain. Normal launches and public jars do not include it.

`qualify_110.py --resource diamond` runs the policy/import matrix with controlled
pipes above native diamonds. It disables unrelated shipped diamond definitions
so only the intended rule controls background generation.

`prepareDiamondSurvey` builds the area-weighted survey. `diamond_survey_110.py`
uses four fixed calibration seeds and two separate holdouts. The default pass
measures complete pipe geometry against in-memory native terrain and samples
river sediment. `--native-sediment` also checks fully populated riverbeds;
`--control` measures ordinary diamonds with both new rules disabled. Keep these
results separate: pre-decoration sediment is not the complete river supply.
Multiply rich gravel by 1/16 before comparing rewards. Fortune applies only
after that roll and uses the same multiplier as vanilla diamond ore.

See [the diamond walkthrough](DIAMOND_TESTING.md) for the separate manual launch.

`diamond_balance_110.py` combines complete pipe surveys, fully decorated river
samples and matched vanilla controls. All inputs must identify the same candidate.
It checks the calibration, holdout and combined means against the 20% budget and
reports seed-level uncertainty and Fortune rewards rather than counting rich
gravel as if every block dropped a diamond.

Mine checks cover iron, coal and individual diamond pipes, including the regular
development configuration. Clear routes are walked up and down using native
entity movement. Separate chest, water and structure fixtures check that unsafe
sections remain sealed. Changing an ore pass can change vanilla's later
decoration; that is not permission to remove an obstruction or rewrite old terrain.

`frozen_mines_110.py` completes an older saved ladder shaft with both the old
planner and the new stepped-access build. It compares their blocks, player
markers, remaining sections and reloads. The source fixture is never modified.

## Running the policy matrix

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
| `--orespawn` | Public OreSpawn 4.1.5.110021; its official checksum is checked |
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

Local qualification uses an official Forge server from a checksum-pinned
installer and runs the complete 32-case fresh/reload/later-chunk matrix.
Retain failed logs, result reports and disposable worlds locally. The upgrade
suite also checks disposable copies of worlds made before the fix.

## Diamond-pipe workings

`pipe_mines_110.py` uses the same exact public jars and isolated mine probe.
Supply `--java`, `--runtime`, `--mod`, `--orespawn`, `--probe` and a fresh
`--output` directory. The probe is built by `prepareMineQualification` with
`--init-script tools/qualification/probes.init.gradle`.

The suite checks the shaft, galleries, player markers, saved layouts, reloads
and later-generated sections. It also checks reversed chunk order, opt-out,
zero chance, disabled source rules, water and native structures. The `normal`
case retains all factory defaults; `mineralogy` additionally requires that
target's compatible jar through `--mineralogy`. `mineralogy-flat` checks partial
workings with active strata and no cross-chunk vanilla decoration. Native
terrain cases populate the complete mine footprint before checking reloads;
vanilla decoration in adjacent new chunks can otherwise change unworked ground.
The combined local pass runs the controlled cases.

## Gold and the combined regression pass

The combined local driver covers iron, coal, diamonds and gold, plus a separate
Mesa gold policy matrix. It also runs source-disable, gravel-harvest and
workings checks. Each policy matrix includes imports, explicit overrides,
saved management toggles, reloads and later-generated chunks. Logged errors
fail the driver even when a probe has written a pass marker.

The historical filename `ci_runtime.py` is retained for existing local commands;
the driver refuses execution when `CI` or `GITHUB_ACTIONS` is true. Prepare its
isolated probes locally first:

```powershell
.\gradlew.bat preparePolicyQualification prepareCoalQualification prepareDiamondQualification prepareGoldQualification prepareMineQualification --init-script tools/qualification/probes.init.gradle
```

Then run `python tools/qualification/ci_runtime.py --java <Java-8-executable>`
with a fresh evidence directory through `--output`. Supply `--only <suite names>`
for a smaller local pass. Its optional
`--runtime`, `--mod`, `--orespawn` and `--probe-directory` arguments reuse sealed
inputs; the engine must still match the public pin. They do not enable a local
engine override or change the supplied runtime. Without those options, the
local driver installs the checksum-pinned official Forge server and downloads
the public dependency itself. Keep the candidate and dependency hashes with
the local results. A workflow/docs-only change does not invalidate exact-jar
runtime evidence when all three release jars and dependency remain identical.

`gold_survey_110.py` measures six fixed seeds, reserving the last two as
holdouts. Run lode, `--control` and `--native-sediment` passes, then combine
their results with `gold_balance_110.py`. The report counts expected nuggets
and ingots, includes Fortune and records uncertainty without enforcing a
vanilla-equivalent reward target. All three passes must use the same mod,
engine and survey probe. `--structures --reload` additionally checks a manual
site under ordinary structure generation and after a clean restart.

`gold_maps.py` draws measured lode sections or a `--placer` river section.
Keep the generated images with the disposable evidence, not in public jars.
The player-facing steps and fixed test locations are in [the gold walkthrough](GOLD_TESTING.md).
