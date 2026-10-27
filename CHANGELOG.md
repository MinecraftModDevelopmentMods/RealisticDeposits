# Changelog

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
