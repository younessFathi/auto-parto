# US-017: Fix JaCoCo Configuration to Enforce Minimum Coverage

**As** a quality engineer  
**I want** JaCoCo to enforce a minimum coverage threshold and stop excluding critical packages like `config` and `advicer`  
**so that** coverage cannot silently drop to 0% and security/configuration code is measured.

## Acceptance Criteria

1. A JaCoCo `check` execution is added to the `verify` phase with:
   - Minimum instruction coverage: 70%
   - Minimum branch coverage: 60%
   - Minimum class coverage: 80%
2. The excludes list is trimmed:
   - REMOVE: `auto.parto.config/**` — configuration classes must be measured
   - REMOVE: `auto.parto.advicer/**` — exception handlers must be measured
   - KEEP: `auto.parto.model/**`, `auto.parto.dto/**`, `auto.parto.mapper/**` — boilerplate
   - KEEP: `auto.parto.exception/**` — exception classes
   - KEEP: `**/Constants.java` — string constants
3. The build fails if coverage thresholds are not met
4. CI pipeline includes `verify` goal to run JaCoCo check

## Technical Notes

- File: `pom.xml` lines 306-335
- Current state: Only `report` goal, no `check`, broad exclusions
- The JaCoCo `check` goal must be added as a separate execution bound to `verify` phase
- Coverage targets are reasonable starting points — adjust based on current baseline

## Risk

🟡 **MEDIUM** — No coverage enforcement means quality degrades without detection. Security config excluded from measurement.

## Dependencies

US-022, US-023, US-024, US-025 (test additions needed to meet coverage targets)
