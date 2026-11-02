# Universal project development

Open the root folder as one Gradle project in IntelliJ IDEA. Java 25 and an initial Internet connection for Minecraft/Fabric dependencies are required.

## Edit shared features once

- `src/main/java`: prediction, calibration, persistence, settings, HUD, automatic rerolls, commands and keybindings.
- `src/main/resources`: shared assets, translations and implementation metadata templates.
- `src/compat/legacy`: the trade and rendering API adapters shared by 26.1.x and 26.2.
- `src/compat/mc261`, `mc262`, `mc263`: the client API boundary for each release line. The 26.3 folder also contains its new trade codecs and mixins.
- `src/gametest`: shared client regressions. Codec-specific tests live beside their adapters.
- `src/test`: shared unit tests.

The feature sources compile against every target. Keep version-dependent Minecraft calls in `ClientCompat` or `TradeCompat` and version-specific mixins in the appropriate adapter folder. A feature fix in the shared source tree reaches every implementation in the next build.

## Build and run

```text
gradlew.bat build
gradlew.bat testAllClients
gradlew.bat :mc263:runClient
gradlew.bat :mc262:runClient
gradlew.bat :mc261:runClient
```

The distributable is `build/libs/VTP-<version>-mc26-universal.jar`, with a permanent copy in `releases/`. Individual module JARs are build intermediates. `mod_version` is the release prefix and `build_number` is the next build counter in `gradle.properties`. With `mod_version=1.3.0`, consecutive packages become `1.3.0.1`, `1.3.0.2`, and so on. The counter is reserved immediately before writing a new universal JAR; a failed package may leave a gap. Builds skip existing filenames and refuse to overwrite an existing artifact. Gradle sync, help, and dry runs do not consume a number. `clean` removes build outputs but preserves `releases/` and the counter. Reference projects are unchanged.

Use `gradlew.bat build testAllClients` in one invocation to run both checks against the same numbered artifact; standalone `testAllClients` packages a new version. `build` checks the bundle and runs the same unit tests in five environments. `testAllClients` launches the actual universal JAR on **26.1, 26.1.1, 26.1.2, 26.2 and 26.3**. Client runs are serialized to keep desktop/GPU use predictable. Test saves, screenshots and configs are isolated under each module's `build/run/clientGameTest`.

`mc261`, `mc262` and `mc263` build the three bundled implementations. `test2610` and `test2611` provide older runtime environments only; their binaries are excluded from the universal JAR. All five test launches use the same three-implementation distributable.

## How one JAR supports different APIs

The outer mod declares three nested implementations with the same VTP mod ID and different Minecraft dependency ranges. It requires exactly the shared release version. [Fabric's nested JAR resolver](https://wiki.fabricmc.net/documentation:fabric_loader#nested_jars) selects the compatible implementation and loads only its initializer and mixins. This uses Fabric's normal dependency resolution, with no custom classloader or runtime bytecode rewriting.

The integration test asserts the selected implementation, its nested origin, the absence of development classes, a single initializer, the game's native V key and the optional unbound overlay shortcut. It logs the outer JAR's SHA-256 so runs can be checked against the identical artifact. The remaining tests compare native vanilla trades, exercise remote-login registries and both calibration workflows, and check GUI layouts and HUD behavior.

## Add another release line

1. Add the target Minecraft and Fabric API versions to the target map in root `build.gradle` and include its module in `settings.gradle`.
2. Reuse existing adapter groups where possible; isolate new API differences under `src/compat`.
3. Add the implementation module to `implementationProjects` and update the outer Minecraft ranges and bundle verifier count.
4. Include its runtime in the client-test ordering and run `build` and `testAllClients`.
5. Install the newly generated universal JAR, keeping Fabric API matched to the running game.

The existing 26.1.2, 26.2 and 26.3 projects are reference copies. This build reads no sources from them and does not change their files.
