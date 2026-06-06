# Flows

A tiny **server-side** patch for a **Paper** Minecraft server (**MC 26.1.2**, Java 25) that:

- makes **water flow ~2.5× faster** (tick delay 5 → **2**), and
- makes **lava flow dramatically faster** (tick delay 30/10 → **4**, all dimensions).

It's a Java *agent* — loaded into the server's own JVM at startup. **Vanilla, unmodded clients can just join** (the change is pure server-side world simulation).

## Recommended: run it via [Primer](https://github.com/ndalton57/primer)

1. Drop `flows.jar` into `./agents/`.
2. On first launch, Primer generates `agents/flows.conf` from the defaults bundled in the jar:
   ```
   water=2
   lava=4
   ```
   Edit those values and restart to tune (lower = faster; minimum 1).

## Standalone (without Primer)

Add `-javaagent` directly to your Paper start command, **before** `-jar`, on Java 25:

```
java ... -javaagent:"C:\path\to\flows.jar"=water=2,lava=4 -jar paper.jar nogui
```

Remove the flag to revert to vanilla.

## Build

```powershell
.\build.ps1            # ephemerally downloads JDK 25 into .build\, compiles, bundles flows.conf, produces flows.jar
.\build.ps1 -Purge     # delete .build\ ; leaves flows.jar + sources
```

No Gradle, no third-party libraries (it uses the JDK's built-in `java.lang.classfile` API).

On startup you should see:

```
[Flows] Loading server-side fluid flow patch (water getTickDelay -> 2, lava getTickDelay -> 4).
[Flows] Patched net/minecraft/world/level/material/WaterFluid.getTickDelay -> return 2 (1 method(s)).
[Flows] Patched net/minecraft/world/level/material/LavaFluid.getTickDelay -> return 4 (1 method(s)).
```

A `WARNING: method 'getTickDelay' not found` means the Mojang mapping changed for your MC version — update `TARGET_METHOD` in `GetTickDelayTransformer.java` and rebuild.