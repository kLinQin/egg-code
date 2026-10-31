# Egg — Android Code Editor

A lightweight, offline-first code editor for Android built with Jetpack Compose and
[Sora Editor](https://github.com/Rosemoe/sora-editor). Includes a built-in Alpine
Linux terminal (via proot), Git integration (JGit), and Backblaze B2 cloud sync.

## Features

- **Code editing** — Sora Editor with TextMate grammars and syntax highlighting for
  Kotlin, Java, JavaScript, JSON, HTML, CSS, Python, and Markdown.
- **Themes** — Dracula (dark) and GitHub Light, with a Claude-flavored ANSI palette
  in the terminal when dark mode is active.
- **Multi-tab editing** — dirty-state tracking, save / save-as, close-others, and
  per-tab delete. Inline find bar (à la VS Code).
- **File manager** — Storage Access Framework (SAF) tree browsing, create / rename /
  delete files and folders, copy-path, collapsible tree.
- **Git** — init, clone, add remote, fetch, pull, push, commit, status, and log,
  powered by JGit. HTTPS with optional username/token credentials.
- **Cloud sync** — Backblaze B2 (list, upload, download, version history, diff
  against local project).
- **Terminal** — embedded Alpine Linux via `pdn-engine` (proot-based), with PTY
  session, resize handling, and Claude-themed colors.
- **Settings** — theme, font size / family, line numbers, word wrap, tab width,
  bottom bar visibility, auto-indent, autocomplete, indent guides, active-line
  highlight, auto-close brackets, exit confirmation. Backup / restore settings
  and open tabs to JSON.
- **Splash animation** — egg-cracking intro on cold start.

## Tech Stack

| Layer | Library |
|---|---|
| UI | Jetpack Compose (BOM 2024.09.00), Material 3 |
| Editor | [Sora Editor](https://github.com/Rosemoe/sora-editor) 0.24.4 + TextMate |
| Git | [JGit](https://www.eclipse.org/jgit/) 7.1.0 |
| Terminal | [termux/termux-app termlib](https://github.com/termux/termux-app) 0.0.40 + `pdn-engine` 0.6.18 |
| Cloud | Backblaze B2 (custom client, `util/B2Api.kt`) |
| File access | AndroidX DocumentFile (SAF) |
| Language | Kotlin (JVM 17), AGP with Compose plugin |

## Requirements

- Android Studio (latest stable)
- JDK 17
- Android SDK: `compileSdk 37`, `minSdk 28`, `targetSdk 37`
- ARM64 device or emulator (native `libpdn.so`, `libproot-loader.so`, `libptyjni.so`
  are shipped for `arm64-v8a` only)

## Build

```bash
./gradlew assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`

> The project uses `isCoreLibraryDesugaringEnabled = true` and
> `useLegacyPackaging = true` for JNI libs — both are required by `pdn-engine`.

## Project Structure

```
app/src/main/java/com/egg/codeeditor/
├── MainActivity.kt              # Entry point; sync TextMate init (no white flash)
├── model/                       # State holders (EditorSettings, EditorTab, …)
├── ui/
│   ├── EggApp.kt                # Root composable: sidebar, tabs, editor, dialogs
│   ├── cloud/                   # Backblaze B2 panel + connect dialog
│   ├── git/                     # Git panel + remote dialog
│   ├── components/              # ActionBars, FindBar, TabBar, Sidebar, …
│   ├── editor/                  # SoraCodeEditor wrapper + language registry
│   └── theme/                   # Color, Theme, Type, system bars
└── util/                        # B2Api, FileUtils, Git helpers

app/src/main/assets/             # TextMate grammars, themes, language.json
app/src/main/jniLibs/arm64-v8a/  # Native libs (pdn, proot-loader, ptyjni)
app/libs/pdn-engine-0.6.18.aar   # Terminal engine
```

## Third-party Notices

- **Sora Editor** — LGPL-2.1
- **JGit** — EPL-2.0
- **termux termlib** — Apache-2.0
- **pdn-engine** — bundled AAR (see `app/libs/`)

## License

Add your preferred license here (e.g. MIT, Apache-2.0). The third-party
components above retain their own licenses.