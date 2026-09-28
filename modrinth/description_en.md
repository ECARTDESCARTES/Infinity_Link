<!-- Banner: replace the URL below with the URL of banner.png once it is uploaded to the Modrinth gallery (right-click the image > copy image address). -->
![∞link banner](BANNER_URL)

# ∞link (Infinity_Link)

**∞link** is the client mod for the **∞SMP** server: it lets the server talk directly to your game. You get native distant terrain rendering, per-mod creative tabs, 3D armor and content made by other players, with nothing to set up yourself.

*Formerly SAGE Link.*

---

## Features

- **Native distant rendering**: the server sends a simplified version of the terrain beyond your normal render distance. By default the view reaches 128 chunks, or 2,048 blocks. Video memory use has an adjustable cap that is never exceeded. To stay under this cap, the mod first frees the farthest areas, then reduces the view in steps of 16 chunks.
- **Per-mod creative tabs**: in creative mode, items added by the server get their own tabs, one per mod. Empty tabs stay hidden. Up to 128 tabs, on pages of 10 (the first page keeps the vanilla tabs).
- **3D armor from mods**: armor with a real 3D model is shown on players (including you in third-person view), armor stands and humanoid mobs, without the regular armor being drawn on top.
- **Player-made content**:
  - **Preloading**: content published on the server is downloaded in the background, checked (size and hash) and cached for future sessions.
  - **Upload**: `/lk envoyer <file>` sends a file from your drop folder (`.minecraft/laconia/depot`) to the server.
  - **16×16 editor**: `/lk editeur` opens a small pixel editor (16-color palette, brush, eraser) to draw a texture, save it as PNG and send it.
- **Extended blocks (in preparation)**: the mod prepares 100,000 extra block slots for future server-added blocks. Not active yet.
- **Server-driven HUD and keys**: the server can show information in the top-left corner of the screen and respond to some of your keys.
- **Quiet log**: your `latest.log` is no longer flooded with warnings. On every resource reload, the 100,000 reserve slots without a model are summed up in a single line, instead of about 1.7 million warnings (148 MB measured). Other missing models are still reported.

## Requirements

| | |
|---|---|
| Minecraft | **26.3** |
| Loader | **Fabric Loader 0.19.0 or newer** |
| Fabric API | **Not required** |
| Side | **Client only** (nothing to install on a server) |

No special Java (JVM) arguments are needed.

## Compatibility

∞link only does something on a server that speaks its protocol, such as ∞SMP. On a vanilla server or any other server, **it has no effect**. It sends a single greeting message, which the server ignores, and nothing else.

## Installation

**With Prism Launcher or MultiMC**
1. Create a **Minecraft 26.3** instance.
2. In *Edit instance > Version*, click *Install Fabric* and pick version 0.19.0 or newer.
3. In the *Mods* tab, add the ∞link `.jar` file (or install it from Modrinth right inside the launcher).
4. Start the instance and join the ∞SMP server.

**With the official Minecraft launcher**
1. Install **Fabric Loader** for Minecraft 26.3 with the Fabric installer (version 0.19.0 or newer).
2. Put the ∞link `.jar` file in the `.minecraft/mods` folder (create it if it does not exist).
3. In the launcher, select the *fabric-loader-26.3* profile and start the game.

## Configuration

The file `.minecraft/config/sage_link.properties` is created on first launch. Edit it with a text editor while the game is closed.

| Option | Default | Values | Purpose |
|---|---|---|---|
| `lod` | `true` | `true` / `false` | Turns distant rendering on or off. |
| `lod_view` | `128` | `0` to `512` (chunks) | Distant rendering range. 128 chunks = 2,048 blocks. The server serves at most 256. `0` turns distant rendering off. |
| `lod_vram_mb` | `96` | `16` to `4096` (MiB) | Video memory cap for distant rendering. |
| `armures_3d` | `true` | `true` / `false` | Turns 3D armor rendering on or off. |

Out-of-range values are clamped. If `lod_vram_mb` or `armures_3d` is missing, it is added with its default value; a missing `lod` or `lod_view` option uses its default value. An unreadable value resets the distant rendering options to their defaults.

## Commands

These commands are handled by your game and are never sent to the server. Their names are in French.

| Command | Effect |
|---|---|
| `/lk envoyer <file>` | Sends a file from the `.minecraft/laconia/depot` folder to the server (1 byte to 2 MiB, a plain file name). The result is shown in chat. |
| `/lk editeur [name.png]` | Opens the 16×16 pixel editor. *Enregistrer* (Save) writes the PNG to the drop folder, *Envoyer* (Send) saves then uploads it. Right-click = eraser. |
| `/lk depot` | Lists the drop folder. |
| `/lk` | Shows help. |

## Privacy

**What is sent, and when**
- **On connection**: a greeting with the protocol version, the mod version, the Minecraft version and the list of supported features.
- **On a compatible server only**:
  - your distant rendering range (as chosen, reduced if the video memory cap is reached, 0 when you leave);
  - presses and releases of the keys the server asked for, and only while no screen is open;
  - the preloading result (ready or failed);
  - confirmation that the creative tabs were received;
  - the reply to a technical test of extended blocks requested by the server;
  - files that **you** send with `/lk envoyer` or the editor's *Envoyer* button. Nothing is uploaded without your action.

**What is downloaded, and from where**
- Content published on the server, from the (http or https) addresses the server provides. Each file is checked (size and SHA-1 hash), then stored in `.minecraft/sage_link/cache`.

## FAQ

**Do I need the mod to play on ∞SMP?**
No. You can join ∞SMP without it; you just won't get the features above.

**Do I need Fabric API?**
No. ∞link only needs Fabric Loader.

**Distant rendering makes my game lag. What can I do?**
Lower `lod_view` or `lod_vram_mb` in `config/sage_link.properties`, or set `lod=false`.

**I don't like the 3D armor.**
Set `armures_3d=false` in `config/sage_link.properties`.

**Why are the files still named `sage_link`?**
The mod used to be called SAGE Link. The technical ID `sage_link` was kept so your settings and cache stay valid.

**What if a feature crashes?**
An error only turns off the feature involved (distant rendering, 3D armor, HUD or keys). The game keeps running. Distant rendering stays off until the end of the session, 3D armor until you change worlds.

## Known limitations

- **Keys**: only common keys are recognized (letters, digits, F1 to F24, arrows, numpad, modifiers). Keys are only sent while no screen is open.
- **Mouse**: mouse buttons are not sent.
- **HUD**: 16 lines at most, top-left corner. It is hidden by F1 and while the debug screen (F3) is open.

## Links

- **Server**: ∞SMP (Discord or server page: to be added)
- **Server address**: `∞.ecartdescartes.eu` *(remove this line if you do not want to publish it)*

<!-- Icon: replace the URL with the URL of icon.png once it is uploaded to the Modrinth gallery (the project icon is also set in Settings > General). -->
![∞link icon](ICON_URL)

## License

Infinity_Link is released under the **Mozilla Public License 2.0** (MPL-2.0): you may use it and include it in modpacks freely; any modified version of the mod's files must be redistributed under the same license, with its source code.
