# Universal JAR validation

Validated locally on 2026-11-01 using Java 25, Fabric Loader 0.19.5 and the Fabric API version configured for each runtime in `build.gradle`.

Artifact: `build/libs/VTP-1.3.0-mc26-universal.jar` (684,985 bytes).

SHA-256: `2b6c3c3f7b8f80231eff1b760c66b9cb8e218888f14b058e6d1e5eef842ddb8e`.

| Minecraft | Selected nested implementation | Unit tests | Packaged client suite |
| --- | --- | --- | --- |
| 26.1 | 1.3.0+mc26.1 | 23 passed | Passed |
| 26.1.1 | 1.3.0+mc26.1 | 23 passed | Passed |
| 26.1.2 | 1.3.0+mc26.1 | 23 passed | Passed |
| 26.2 | 1.3.0+mc26.2 | 23 passed | Passed |
| 26.3 | 1.3.0+mc26.3 | 23 passed | Passed |

All five client runs loaded the identical artifact hash above. Each run checked that Fabric selected exactly one nested VTP implementation, loaded its actual packaged classes and initializer, used the running game's native V key to open settings, and kept the optional overlay shortcut unbound by default.

The 115 unit-test executions passed. Each client suite also passed:

- Native vanilla parity for 20,000 trade rounds, 12 live rerolls, non-mutating snapshots and repeated selection.
- Trade-codec validation, unsupported conditional-modifier rejection and minimum book prices.
- Remote-login registry reconstruction, all five librarian tiers, 500 forecast rounds, five-book manual calibration, HUD output and session reset.
- Immediate persistent seed input, hidden seed narration, retained observation drafts, captured base prices, no-book and multi-book observations, duplicate prevention and automatic calibration from both GUIs.
- GUI scales 1/2/3 and 320×240, color controls, targets, HUD sizing, locked books, future tiers and screen fitting.

The multiplayer tests exercise remote-client registry and calibration paths in an isolated test harness with integrated-server access disabled for those paths. They do not represent a separate human session on a dedicated server.

The bundle verifier passed and confirmed that release implementations exclude test classes. Screenshots were also inspected for the oldest and newest runtimes. Local logs are in `build/universal-matrix.log` and each module's `build/run/clientGameTest/logs`; unit reports are under each module's `build/reports/tests/test`. Run `gradlew.bat build testAllClients` to repeat validation. The GitHub Actions workflow is configured to run these commands but was not executed remotely during this change.

Checksums of 1,075 existing source and project files across the original 26.1.2, 26.2 and 26.3 folders matched their pre-copy snapshot. The new build contains its own shared sources, resources, wrapper and adapters; it does not depend on those reference folders.
