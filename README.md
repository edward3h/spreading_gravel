# Spreading Gravel

A Minecraft 1.21.1 mod that adds **Spreading Gravel** — a gravity block that flows sideways toward the deepest nearby drop.

Available for **NeoForge** and **Fabric**.

## Behaviour

Spreading Gravel behaves like vanilla gravel when falling straight down. When it lands, it checks the four cardinal directions (N → E → S → W) and slides toward whichever has the deepest available drop. It also mimics vanilla gravel's water-fall behaviour.

## Crafting

Combine 3 Gravel and 1 Sand in any arrangement (shapeless recipe). The recipe is automatically unlocked in the recipe book when you pick up Gravel.

## Building

```bash
# NeoForge jar
./gradlew :neoforge:build
# Output: neoforge/build/libs/

# Fabric jar
./gradlew :fabric:build
# Output: fabric/build/libs/
```

## Development

```bash
# Launch NeoForge dev client
./gradlew :neoforge:runClient

# Run unit tests
./gradlew :common:test
```

## Credits

Block texture by [foxgames1](https://github.com/foxgames1).

## Licence

Apache 2.0 — see [LICENSE](LICENSE).
