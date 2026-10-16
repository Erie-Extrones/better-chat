# Better Chat

Client-side [Fabric](https://fabricmc.net/) mod that makes chat nicer to look at and keeps a full transcript of it. Built to sit next to [RankMute](https://github.com/erie-extrones/mute-wanderers): the transcript still records every line RankMute hides, and a server-side chat clear cannot wipe it. Nothing is ever sent to the server.

Supported Minecraft versions: **26.3** and **26.1.2** (one jar per version). Requires Java 25, Fabric Loader 0.19.5+ and Fabric API.

## What it does

**Chat fonts.** Pick a font for the chat HUD from a menu. Bundled: Chewy, Fredoka, Comic Neue, Patrick Hand, Nunito, Pixelify Sans, plus the vanilla default. Switching is instant and applies to every new line (player chat, server messages and command feedback). Server resource-pack glyphs such as rank icons keep their own font, and anything a bundled font lacks (icons, CJK, symbols) falls back to the vanilla font instead of showing boxes.

**Transcript.** A scrollable screen with every chat line received, oldest at the top, newest at the bottom, with the sender's head next to each message, the time, and the sender's name.

- Lines RankMute hid from chat are in the transcript too, tinted red and labelled `hidden from chat by RankMute`.
- A server-side chat clear (the usual burst of blank lines) never reaches the transcript. Instead one grey marker says the chat was cleared; everything above it stays.
- Search box (message text or player name) and a filter: all messages, only those shown in chat, only hidden ones, only player messages.
- Everything is saved to `config/betterchat/transcripts/<date>.jsonl`, one file per day, so logs survive restarts. `<` and `>` page through earlier days; `Today (live)` jumps back to the live view.
- Click a message to copy its text to the clipboard. Hovering shows the exact date and time, the sender's UUID, the server address and why it was hidden, if it was.

## Install

1. Install Fabric Loader 0.19.5 or newer for your Minecraft version.
2. Put [Fabric API](https://modrinth.com/mod/fabric-api) for that Minecraft version in `mods/`.
3. Download the Better Chat jar whose name ends in `+mc<your version>.jar` from the [Releases](../../releases) page and put it in `mods/`. RankMute is optional; put its jar in `mods/` too if you want hidden lines labelled.
4. Start the game. A default config is written to `config/betterchat.json` on first launch.

The mod is marked `"environment": "client"` and is ignored on servers.

## Using it

| How | What |
|-----|------|
| `H` key | Open the transcript. Rebind under Options > Controls > Better Chat. |
| `/betterchat` | Open the Better Chat menu (font picker, with a preview; button to the transcript). |
| `/betterchat transcript` | Open the transcript. |
| `/betterchat fonts` | List font ids, each drawn in its own font. |
| `/betterchat font <id>` | Set the chat font, e.g. `/betterchat font chewy`. Tab-completes. |
| `/betterchat reload` | Re-read `config/betterchat.json` and today's transcript file. |

There is also an unbound key for the menu under Options > Controls > Better Chat.

### How the sender is worked out

Most servers with rank plugins send chat as plain system messages with no sender attached, so the mod has to guess:

1. If the server attached a signed sender (vanilla-style player chat), that is used.
2. Otherwise, a player hover card or a `/msg <name>` click action embedded in the line by the chat plugin.
3. Otherwise, the earliest online username (from the tab list) that appears as a whole word in the line. This is what matches the `Wanderer : Meigha hello` format.
4. Otherwise, the optional `senderPattern` regex from the config.

Lines where no sender is found get a grey `!` head and are labelled `Server`.

## Config

File: `config/betterchat.json`. Written with defaults if missing; see [`config.example.json`](config.example.json). The font is the only setting with a menu; edit the rest in the file and run `/betterchat reload`.

| Key | Default | Meaning |
|-----|---------|---------|
| `font` | `default` | Chat font id: `default`, `chewy`, `fredoka`, `comicneue`, `patrickhand`, `nunito`, `pixelifysans`. |
| `transcriptMaxEntries` | `5000` | Most lines kept in memory for the live (today) view. Files on disk are not trimmed. |
| `transcriptSaveToDisk` | `true` | Append every line to `config/betterchat/transcripts/<date>.jsonl`. With `false` the transcript only lasts for the session and `/betterchat reload` empties it. |
| `transcriptKeepHidden` | `true` | Record lines that RankMute (or any other mod) hid from chat. |
| `chatClearThreshold` | `20` | This many blank lines within `chatClearWindowMs` of each other count as a chat clear and become one marker. Blank lines are never recorded on their own. |
| `chatClearWindowMs` | `3000` | See above. |
| `senderPattern` | `""` | Optional Java regex whose first capture group is the sender name, used only when steps 1 to 3 above fail. Example for `Rank : Name msg`: `^\S+ : (\S+)`. Invalid patterns are logged and ignored. |
| `timeFormat` | `HH:mm:ss` | java.time pattern for the time shown in the transcript. |

If the file cannot be parsed, a warning is logged and defaults are used in memory. The broken file is left untouched until the next change saves it.

## How it works

- **Fonts.** Each bundled TTF has a font definition in `assets/betterchat/font/<id>.json` that lists the TTF first and vanilla's `minecraft:default` as a fallback. A small mixin on `ChatComponent.addMessage` wraps each incoming line in a parent style carrying the selected font; text that already has an explicit font keeps it. No resource reload is needed to switch.
- **Capture.** The mod listens to Fabric API's `ClientReceiveMessageEvents.ALLOW_CHAT` / `ALLOW_GAME` in its own event phase ordered before the default phase, so it sees every line before RankMute decides to drop it. If a later listener cancels the line, the `*_CANCELED` event marks the transcript entry as hidden. Action bar text is ignored.
- **Storage.** Each line is snapshotted into plain text runs with colour and formatting (hover and click events are dropped) and appended as one JSON object per line. The transcript never touches the chat HUD's own message list, which is what a chat clear empties.

Out of scope for now: changing the font of the chat input box, loading your own TTF files, and any server-side component.

## Building from source

Requires JDK 25.

```
./gradlew build                                                       # Minecraft 26.3 (default in gradle.properties)
./gradlew build -Pminecraft_version=26.1.2 -Pfabric_api_version=0.155.3+26.1.2   # Minecraft 26.1.2
```

Jars land in `build/libs/` as `betterchat-<version>+mc<minecraft>.jar`. Unit tests for the Minecraft-free core (sender detection, chat-clear detection, transcript files) run as part of `build` and need no Minecraft installation.

The few client APIs that differ between the two Minecraft versions live in `src/compat/mc<version>/java`; only the directory matching `minecraft_version` is compiled.

## Releasing

CI (`.github/workflows/build.yml`) builds and tests every push for all supported Minecraft versions.

To publish a release, bump `mod_version` in `gradle.properties`, commit, then push a matching tag:

```
git tag v1.0.0
git push origin v1.0.0
```

`.github/workflows/release.yml` builds one jar per Minecraft version with the version taken from the tag, and publishes a GitHub Release with the jars attached and generated release notes. Tags with a `-` suffix (e.g. `v1.1.0-beta.1`) are marked as pre-releases. To support another Minecraft version, add it to the matrix in `build.yml` and add a `src/compat/mc<version>/java` directory.

## Licenses

The mod is [MIT](LICENSE). The bundled fonts keep their own licenses, shipped next to them in `assets/betterchat/`: Chewy (Apache 2.0, Font Diner), and Fredoka, Comic Neue, Patrick Hand, Nunito and Pixelify Sans (SIL Open Font License 1.1).
