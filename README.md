# Better Chat

Client-side [Fabric](https://fabricmc.net/) mod that makes chat nicer to look at and keeps a full transcript of it. Built to sit next to [RankMute](https://github.com/erie-extrones/mute-wanderers): the transcript still records every line RankMute hides, and a server-side chat clear cannot wipe it. Nothing is ever sent to the server.

Supported Minecraft versions: **26.3** and **26.1.2** (one jar per version). Requires Java 25, Fabric Loader 0.19.5+ and Fabric API.

## What it does

**Chat fonts.** Pick a font for the chat HUD from a menu. Bundled: Chewy, Fredoka, Comic Neue, Patrick Hand, Nunito, Pixelify Sans, plus the vanilla default. Switching is instant and applies to every new line (player chat, server messages and command feedback) and to the chat input box. Server resource-pack glyphs such as rank icons keep their own font, and anything a bundled font lacks (icons, CJK, symbols) falls back to the vanilla font instead of showing boxes.

**Your own fonts.** Drop any `.ttf` into `config/betterchat/fonts/` and it shows up in the font picker as `custom_<file name>` after a resource reload (F3+T or `/betterchat reload`). The mod builds a small always-enabled resource pack from that folder; nothing to enable in the resource pack screen.

**Chat HUD extras.** Each can be switched off in the menu.

- Timestamps: every line is prefixed with `[HH:mm]` in dark grey.
- Block chat clears: blank lines are dropped before they reach the chat, so a staff chat clear cannot push your chat away. The transcript still notes that a clear happened.
- Mention ping: a line that says your username plays a note-block pling, turns gold and gets an `@` in front. Your own messages do not ping.
- Longer history: the chat keeps 500 lines instead of vanilla's 100 (100 to 10000 in the menu).
- No bold with fonts: Minecraft fakes bold by drawing glyphs twice, which smears TrueType fonts, so bold is dropped while a bundled or custom font is selected.
- A `Transcript` button in the top-right corner of the chat screen.

**Spam rules.** Repetitive server lines such as crate announcements (`Crates » Player X just got the Y reward from the Z Crate - INFINITY!`) can be hidden, collapsed or rewritten. A rule is a regex plus an action:

| Action | Effect |
|--------|--------|
| `COLLAPSE` (default) | Show the first match in each window (15 s by default), hide the rest, then post one grey line: `Crate rewards: 12 more line(s) hidden, see the transcript`. |
| `HIDE` | Never show matches in chat. |
| `REWRITE` | Show a short line built from the regex groups instead, e.g. `Static_bee got Flash Boots (Blossom crate)`. The window limit still applies unless `maxPerWindow` is 0. |
| `OFF` | Keep the rule but do nothing. |

Hidden lines are still in the transcript, labelled with the rule that hid them. The crate rule ships enabled; switch its action in `/betterchat` > Spam rules, and add your own with `/betterchat spam add <regex>` or in the config.

**Heads in chat.** The chat HUD draws the sender's head in front of each line (after the timestamp), using the game's own inline player-head glyph.

**Transcript.** A scrollable screen with every chat line received, oldest at the top, newest at the bottom, with the sender's head next to each message, the time, and the sender's name (and nickname, when the server shows one).

- Lines RankMute hid from chat are in the transcript too, tinted red and labelled `hidden from chat by RankMute`.
- A server-side chat clear (the usual burst of blank lines) never reaches the transcript. Instead one grey marker says the chat was cleared; everything above it stays.
- Search box (message text or player name) and a filter: all messages, only those shown in chat, only hidden ones, only player messages.
- Everything is saved to `config/betterchat/transcripts/<date>.jsonl`, one file per day, so logs survive restarts. `<` and `>` page through earlier days; `Today (live)` jumps back to the live view.
- Click a message to copy its text to the clipboard. Hovering shows the exact date and time, the sender's UUID, the server address and why it was hidden, if it was.
- Click a player's head to open their page.

**Player pages.** Every private message between you and that player and every payment either way, oldest first, with totals (messages, money sent, money received). Opened by clicking a head in the transcript or with `/betterchat player <name>`. Private messages are picked up from the server's chat lines (`MESSAGE ME ✈ Name ✎ text`, `MESSAGE Name ✈ ME ✎ text`, `[Name -> me] ...`, `From Name: ...`, `Name whispers to you: ...` and the like) and from the `/msg`, `/tell`, `/w`, `/r` and `/pay` commands you send; payments from lines such as `$500 has been sent to Name`, `Name has sent you $500` or `You paid $500 to Name`. Everything is kept in `config/betterchat/interactions.jsonl`. The exact wording differs per server: paste a real line into `/betterchat interactions test <text>` to see whether it is recognised and add a pattern to the config if not.

## Install

1. Install Fabric Loader 0.19.5 or newer for your Minecraft version.
2. Put [Fabric API](https://modrinth.com/mod/fabric-api) for that Minecraft version in `mods/`.
3. Download the Better Chat jar whose name ends in `+mc<your version>.jar` from the [Releases](../../releases) page and put it in `mods/`. RankMute is optional; put its jar in `mods/` too if you want hidden lines labelled.
4. Start the game. A default config is written to `config/betterchat.json` on first launch, and `config/betterchat/fonts/` is created for your own `.ttf` files.

The mod is marked `"environment": "client"` and is ignored on servers.

## Using it

| How | What |
|-----|------|
| `H` key | Open the transcript. Rebind under Options > Controls > Better Chat. |
| `/betterchat` | Open the Better Chat menu: all toggles, chat history length, the font picker (with a preview) and a button to the transcript. |
| `/betterchat transcript` | Open the transcript. |
| `/betterchat fonts` | List font ids, each drawn in its own font. |
| `/betterchat font <id>` | Set the chat font, e.g. `/betterchat font chewy`. Tab-completes. |
| `/betterchat player <name>` | Open a player's page (private messages and payments). Tab-completes players you have interacted with. |
| `/betterchat interactions` | List players with recorded messages or payments. |
| `/betterchat interactions test <text>` | Report whether a pasted line counts as a private message or payment, and with whom. |
| `/betterchat spam` | List the spam rules with their index and action. |
| `/betterchat spam test <text>` | Report which rule a line would match and what would happen to it. Paste a real line from `.minecraft/logs/latest.log`. |
| `/betterchat spam add <regex>` | Add a collapse rule. Rename it or change its action, replacement and window in the config. |
| `/betterchat spam set <index> <off\|hide\|collapse\|rewrite>` | Change a rule's action. |
| `/betterchat spam remove <index>` | Delete a rule. |
| `/betterchat reload` | Re-read `config/betterchat.json` and today's transcript file, and pick up new `.ttf` files (triggers a resource reload if the set changed). |

There is also an unbound key for the menu under Options > Controls > Better Chat.

### How the sender is worked out

Most servers with rank plugins send chat as plain system messages with no sender attached, so the mod has to guess:

1. If the server attached a signed sender (vanilla-style player chat), that is used.
2. Otherwise, a player hover card or a `/msg <name>` click action embedded in the line by the chat plugin.
3. Otherwise, the `senderPatterns` regexes from the config, tried in order. The default matches the `Title : ~Name › message` format (any title with icons before the colon, an optional `~` nickname marker, then `›`, `»`, `>` or `:` after the name) and vanilla's `<Name> message`.
4. Otherwise, the earliest online username that appears as a whole word in the line.

A name found this way is matched against the tab list, including the nicknames and tags in players' tab-list entries, so a nickname resolves to the real player and their skin. Lines where no sender is found get a grey `!` head and are labelled `Server`.

## Config

File: `config/betterchat.json`. Written with defaults if missing; see [`config.example.json`](config.example.json). Everything in the first group has a button in `/betterchat`; edit the rest in the file and run `/betterchat reload`.

| Key | Default | Meaning |
|-----|---------|---------|
| `font` | `default` | Chat font id: `default`, `chewy`, `fredoka`, `comicneue`, `patrickhand`, `nunito`, `pixelifysans`, or `custom_<name>` for a file in `config/betterchat/fonts/`. |
| `inputBoxFont` | `true` | Draw what you type in the chat font too. |
| `hudTimestamps` | `true` | Prefix every chat line with the time it arrived. |
| `chatHistoryLines` | `500` | Lines the chat HUD keeps (100 to 10000). Vanilla keeps 100. |
| `blockChatClear` | `true` | Drop blank lines before they reach the chat HUD. |
| `mentionSound` | `true` | Play a pling when a line says your username (whole word, any case). |
| `mentionHighlight` | `true` | Colour such lines gold and mark them with `@`. Text the server coloured explicitly keeps its colour. |
| `chatScreenButton` | `true` | Show a Transcript button in the chat screen. |
| `hudHeads` | `true` | Draw the sender's head in front of each chat line. |
| `stripBoldWithFont` | `true` | Drop bold while a TrueType font is selected. |
| `hudTimestampFormat` | `HH:mm` | java.time pattern for the chat prefix. |
| `spamRules` | one crate rule | List of `{ "name", "pattern", "action", "replacement", "maxPerWindow", "windowSeconds" }`. `pattern` is a Java regex matched anywhere in the line with `§` codes stripped (use `(?i)` for case-insensitive); the first matching rule wins. `replacement` uses `$1`, `$2`... for the regex groups. `maxPerWindow` 0 = unlimited. |
| `spamSummary` | `true` | Post the "N more line(s) hidden" line after a burst goes quiet. |
| `spamSummaryDelaySeconds` | `3` | Seconds of quiet before that line is posted. |
| `customFontSize` | `9.0` | Pixel size used for fonts from `config/betterchat/fonts/`. Vanilla text is about 8 px tall; raise or lower this if a font looks small or clipped. |
| `transcriptMaxEntries` | `5000` | Most lines kept in memory for the live (today) view. Files on disk are not trimmed. |
| `transcriptSaveToDisk` | `true` | Append every line to `config/betterchat/transcripts/<date>.jsonl`. With `false` the transcript only lasts for the session and `/betterchat reload` empties it. |
| `transcriptKeepHidden` | `true` | Record lines that RankMute (or any other mod) hid from chat. |
| `chatClearThreshold` | `20` | This many blank lines within `chatClearWindowMs` of each other count as a chat clear and become one marker. Blank lines are never recorded on their own. |
| `chatClearWindowMs` | `3000` | See above. |
| `senderPatterns` | see above | Java regexes, tried in order, whose first capture group is the sender name. Invalid patterns are logged and skipped. |
| `pmIncomingPatterns`, `pmOutgoingPatterns`, `payReceivedPatterns`, `paySentPatterns` | common plugin wordings | Regexes with named groups `(?<name>...)` for the other player, `(?<text>...)` for a message body and `(?<amount>...)` for money. Matched anywhere in the line, `§` codes stripped; the first match wins. |
| `interactionsSaveToDisk` | `true` | Write private messages and payments to `config/betterchat/interactions.jsonl`. |
| `timeFormat` | `HH:mm:ss` | java.time pattern for the time shown in the transcript. |

If the file cannot be parsed, a warning is logged and defaults are used in memory. The broken file is left untouched until the next change saves it.

## How it works

- **Fonts.** Each bundled TTF has a font definition in `assets/betterchat/font/<id>.json` that lists the TTF first and vanilla's `minecraft:default` as a fallback. A small mixin on `ChatComponent.addMessage` wraps each incoming line in a parent style carrying the selected font (after adding the timestamp and mention highlight); text that already has an explicit font keeps it. No resource reload is needed to switch. The same mixin raises the two `100` line limits in the chat HUD to `chatHistoryLines`. The input box gets the font through an `EditBox` formatter added when the chat screen opens.
- **Custom fonts.** On every resource load the mod scans `config/betterchat/fonts/`, writes `config/betterchat/fontpack/` (a definition like the bundled ones plus a copy of each TTF) and adds it to the client's pack repository as a required pack, so it is always active. Nothing is generated when the folder is empty.
- **Capture.** The mod listens to Fabric API's `ClientReceiveMessageEvents.ALLOW_CHAT` / `ALLOW_GAME` in its own event phase ordered before the default phase, so it sees every line before RankMute decides to drop it. If a later listener cancels the line, the `*_CANCELED` event marks the transcript entry as hidden. With `blockChatClear` on, the same listener answers "no" for blank lines so they never reach the HUD, and the spam rules answer "no" for lines they hide or collapse (REWRITE rules swap the text in the HUD mixin). Action bar text is ignored.
- **Storage.** Each line is snapshotted into plain text runs with colour and formatting (hover and click events are dropped) and appended as one JSON object per line. The transcript never touches the chat HUD's own message list, which is what a chat clear empties.

Out of scope for now: any server-side component.

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
