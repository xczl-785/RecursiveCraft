# Changelog

All notable public-facing changes to RecursiveCraft will be documented here.

## [0.84] - 2026-06-13

### Changed

- Use English mod metadata by default for international release packaging.
- Improve recursive crafting missing-material diagnostics for target-output and NBT-sensitive crafting paths.

### Fixed

- Expanded regression coverage for target output, NBT matching, and transaction calculation cases that previously made missing materials harder to inspect.

## [0.83] - 2026-06-06

### Fixed

- Fixed recursive crafting material matching when recipe or JEI template stacks carry an explicit default `Damage:0` tag.
- Fixed first-attempt `MISSING` failures for wooden pickaxe-style recursive chains where plain inventory materials should match default-damage recipe ingredients.
- Kept non-zero durability damage distinct, so damaged tools are still not merged with undamaged tools during material matching.

## [0.81] - 2026-06-03

### Added

- Recursive Crafter blocks are now mineable with an axe.

### Changed

- Public repository docs were reduced to stable capability documents only.
- Added public Chinese and English README files.
- Added local and GitHub Actions packaging automation scaffolding.

### Fixed

- Fabric build metadata now preserves UTF-8 mod name and description correctly.
