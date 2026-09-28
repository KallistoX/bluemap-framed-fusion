# BlueMap Framed & Fusion

A [BlueMap](https://bluemap.bluecolored.de/) addon (work in progress) that renders two things BlueMap cannot do from
resource packs alone:

- **Framed Blocks**: blocks show their camouflage instead of the empty frame.
- **Fusion connected textures** (Rechiseled, Connected Glass and other mods using Fusion): textures connect to their
  neighbours like in game.

Both need data a resource pack cannot query: the camouflage lives in the block entity, and the connected texture
depends on the neighbouring blocks.

## Compatibility

Built and tested with BlueMap 5.7 (NeoForge, Minecraft 1.21.1), Framed Blocks 10.6.1 and Fusion 1.3.15a. The jar
name carries the BlueMap version it was built for (`bluemap-framed-fusion-<version>-<bluemap>.jar`). Other versions
may work, but are not tested. If a mod is not installed, the addon does nothing for it.

## Install

Put the jar into BlueMap's `packs` folder (e.g. `config/bluemap/packs/`) and restart the server; addons are only
loaded at startup. Then re-render the affected areas (`/bluemap force-update`).

## Build

JDK 21: `./gradlew build`, the jar is in `build/libs/`. The build is reproducible.

## License

MIT, see [LICENSE](LICENSE). Not affiliated with BlueMap, Framed Blocks or Fusion.
