# Lucky XP

Adds a Lucky XP economy: break lucky blocks to earn blue Lucky XP and levels, then spend levels at rarity-tiered vending machines for rewards. Built on the Lucky Block mod; requires Lucky Tweaks.

Minecraft 1.20.1, Forge. Written for the [Lucky World Invasion Reloaded](https://github.com/Laink/LuckyWorldInvasionReloaded) modpack, but it runs on its own.

## Building

This mod compiles against Lucky Tweaks and Lucky Stats, whose jars are not in the repository. Download them
from CurseForge into `libs/`, at the exact versions set in `gradle.properties` (`luckytweaks_version`, `luckystats_version`).
A version mismatch shows up as dozens of `cannot find symbol` errors rather than a
missing-file message. Then:

```bash
./gradlew build
```

## Contributing

Issues and pull requests are welcome. Keep code and comments in English.

## License

Source available, not redistributable: you can read it, build it, use it, and propose changes here, but not republish it or ship it in your own modpack. See [LICENSE](LICENSE).
