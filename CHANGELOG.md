# Changelog

## 0.2.0.110021

Beta for Minecraft 1.10.2.

- Add large coalfields with two to four thin beds, shared folds and faults,
  gradual thinning and local rock partings. Keep 15% background coal for early
  play and retain vanilla coal drops and Fortune behaviour.
- Fit coal beds to suitable existing sedimentary rocks when Mineralogy geology
  is active. Use plain stone otherwise, without adding rocks or enabling
  terrain replacement. Keep granite, diorite, andesite and unsuitable hosts out.
- Keep the coal catalogue separate from iron. Preserve existing settings,
  world profiles, legacy coal layers and previously generated terrain.
- Require OreSpawn 4.1.4.110021 so coal replaces only the intended rock variants.
  Download and check the release automatically.

- Add optional abandoned workings to deposits: a surface shaft, ladder access,
  short seam-following galleries, timber supports, broken rails, cobwebs and
  small rubble piles. Mines are off by default, with a separate chance for each
  deposit definition.
- Keep mine layouts stable through reloads and later exploration. Protect old
  chunks, and finish only the unbuilt sections of an already accepted plan.
- Skip unsafe sites and leave obstructions sealed rather than cutting into
  liquids or another structure. Mines do not reroll deposit selection or
  produce item drops while carving.
- Add a separate Eclipse mine-testing profile without changing ordinary saves
  or configuration.

## 0.1.1.110021

- Let pack authors define deposits in JSON, starting with a supplied iron
  formation. Other resources can be added through custom definitions.
- Add deterministic, chunk-order-independent region selection and a large,
  folded stratiform iron district with irregular splays, subparallel lenses,
  short fault gaps and offsets, and sparse halo clues suitable for long-lived
  mine and rail networks.
- Add status, reload and preview commands plus a read-only Forge configuration
  screen.
- Require OreSpawn 4.1.3.110021, including its fixes for imported placement
  rules, hybrid/replacement limits and the Ore Sources editor.
  Download and map the release automatically, checking its official checksum.
- Generate each deposit consistently across chunk boundaries, without
  changing chunks that have already been generated.
- Add massive-and-rare, centre-geome and per-block rock-family alignment.
- Add additive, hybrid and replacement interaction policies. The experimental
  iron district retains 15% background generation for early-game supply.
- Add packaged-server checks for all interaction policies, legacy imports,
  saved overrides, reloads and chunks generated later.
- Add biome filters and clear warnings when a definition needs unavailable
  geology. Keep structural configuration changes pending until restart.
- Add the Realistic Deposits action to OreSpawn's World Settings screen.
- Keep generation limited to new chunks; the major-district experiment adds no
  retrogen or existing-world rewrite.
- Add automated builds, CodeQL, wrapper checks and a guarded release action.
  Build and checksum the jars before publishing the same files to Maven,
  CurseForge and GitHub. CurseForge releases remain marked as alpha.

## 0.1.0.110021

- Establish the first runnable Forge 1.10.2 alpha, with catalog validation,
  deterministic deposit planning and diagnostic commands.
