<p align="center">
  <img src="static/logo.svg" width="96" alt="Egg logo">
</p>

<h1 align="center">Egg — Android Code Editor</h1>

A lightweight, multi-storage code editor for Android built with Jetpack Compose
and [Sora Editor](https://github.com/Rosemoe/sora-editor). Edit files from local
storage, Git repositories, or Supabase cloud notes. Includes a built-in
Ubuntu 24.04 terminal (via proot), Git integration (JGit), an extension system
with plugin store, custom Markdown preview, and Discord Rich Presence.

## Features

### Code editing
- [Sora Editor](https://github.com/Rosemoe/sora-editor) with TextMate grammars
- Syntax highlighting: 50 built-in languages (Kotlin, Java, JS/TS/JSX/TSX, Python,
  C/C++, C#, Go, Rust, Swift, Ruby, PHP, Lua, Dart, Shell, PowerShell,
  SQL, HTML/CSS/SCSS/LESS, JSON/YAML/TOML/INI, XML, Markdown, LaTeX, and more)
- More via grammar plugins (drop a `.tmLanguage.json` in an `.egg` plugin)
- Multi-tab with dirty-state tracking, save, save-as, close-others, per-tab delete
- Inline find bar (VS Code-style), project-wide search
- Line numbers, word wrap, tab width, auto-indent, autocomplete, indent guides,
  active-line highlight, auto-close brackets
- Undo / redo, custom font (system or uploaded TTF/OTF)

### File manager
- Storage Access Framework (SAF) tree browsing
- Create / rename / delete files and folders
- Collapsible tree with VS Code-style indent guides
- Colored file icons per language (light / dark aware, override via icon pack plugin)
- Private mode root: `filesDir/repos` (Git clones)

### Supabase (cloud notes)
- Login / sign-up with email + password (session persisted)
- Note tree grouped by `main_category` (nested folders via path `A/B/C`) + `category`
- CRUD: create / rename / delete notes and folders
- Per-note public / private at creation (checkbox in New File dialog);
  public notes get a globe marker in the file list
- Cascade delete: removing a folder also removes child folders and their notes
- Markdown content stored in Supabase `notes` table; schema:
  `id, title, content, category, main_category, user_id, owner_name,
  is_public, is_pinned, color, created_at, updated_at`
- Folders table: `id, name, user_id, storage_type, created_at`

### Git
- Powered by [JGit](https://www.eclipse.org/jgit/)
- Init, clone, add remote, fetch, pull, push, commit, status, log
- Per-file staging, unstage, discard, discard-all
- Side-by-side / inline diff view
- HTTPS with optional username / token credentials

### Terminal
- Embedded Ubuntu 24.04 (via proot + [termux termlib](https://github.com/termux/termux-app))
- Persistent PTY session, resize handling
- Shortcut bar (bind `/mnt/sdcard`, `/mnt/appdata`)
- Notification with "Close Session" action
- Claude-flavored ANSI palette when dark mode is active
- Working directory: project root (optional) or `~` sandbox home

### Markdown preview
WebView-based, with custom syntax (in addition to standard Markdown):

- Callout (`> [!NOTE] Title`)
- Spoiler (`||text||`)
- Underline (`==text==`), strikethrough (`~~text~~`)
- Small text (`-# text`)
- Task list (`- [ ]`, `- [x]`)
- Progress bar (`[progress 50% green]`)
- Accordion (`> [!accordion] Title`)
- Tabs (`::tabs [Title]` … `::tabs-end`)
- Timeline (`> [!timeline] Title` + `label :: time`)
- Step rotation (`> [!step] Title` + `label :: detail ::repeat`)
- Chart (`> [!chart] Title` + `bar`/`polar` + `label :: value`)
- Audio (`![audio](url)`)
- Inline badge (`![badge:left](success:DONE)`)
- YouTube embed, Twitter link card
- Discord timestamp (`<t:unix:F>`)
- Code blocks with syntax highlight, language label, line numbers, copy button

### Extension system
- **`.egg` plugins** — zip with `manifest.json`, optional `classes.dex`, and assets
- **Manual plugins (no code)** — theme / grammar / icon pack JSON only
- **Plugin store** — built-in browser, install from GitHub-hosted index
- **Hot reload** — install / uninstall without app restart
- Categories:
  - **Theme** — Compose colors + Sora syntax + editor background
  - **Grammar** — TextMate grammars (TypeScript, Rust, Go, …)
  - **Icon pack** — override per-extension icon color + bitmap
- SDK module `extension-api` (publishable to Maven / JitPack)
- Templates in `plugin-templates/` — copy, edit JSON, zip, install

### Discord Rich Presence
- Show current file being edited on Discord profile
- Token-based (paste User Token from Discord / Aliucord)

### Themes
- **Built-in:** Light (Claude web palette) and Dark (Claude web palette)
- **Plugin themes:** installable from the plugin store (community-maintained)
- Plugin theme applies to: Compose UI, Sora syntax highlight, editor background

### Settings
- Theme, font size / family, line numbers, word wrap, tab width
- Bottom bar visibility, auto-indent, autocomplete, indent guides
- Active-line highlight, auto-close brackets, exit confirmation
- Backup / restore settings + open tabs to JSON
- Extensions tab: installed + store, install / uninstall
- Others tab: Discord, backup / restore

### Splash animation
Egg-cracking intro on cold start.

## Tech Stack

| Layer | Library |
|---|---|
| UI | Jetpack Compose (BOM 2024.09.00), Material 3 |
| Editor | [Sora Editor](https://github.com/Rosemoe/sora-editor) 0.24.4 + TextMate |
| Git | [JGit](https://www.eclipse.org/jgit/) 7.1.0 |
| Terminal | [termux/termux-app termlib](https://github.com/termux/termux-app) + Ubuntu 24.04 rootfs (proot) |
| File access | AndroidX DocumentFile (SAF) |
| Network | OkHttp 4.12 (plugin store + Supabase REST) |
| JSON | kotlinx.serialization + org.json |
| Language | Kotlin (JVM 17), AGP with Compose plugin |

## Requirements

- Android Studio (latest stable)
- JDK 17
- Android SDK: `compileSdk 37`, `minSdk 28`, `targetSdk 37`
- ARM64 device required for the built-in terminal (`libpdn.so`, `libproot-loader.so`
  shipped for `arm64-v8a` only). Non-terminal features may run on other ABIs.

## Build

```bash
./gradlew assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`

> Uses `isCoreLibraryDesugaringEnabled = true` (JGit/termux desugaring) and
> `useLegacyPackaging = true` (native `.so` loading from `jniLibs`).

## Project Structure

```
Egg/
├── app/                          # Android application module
│   ├── src/main/java/com/egg/codeeditor/
│   │   ├── MainActivity.kt       # Entry point (TextMate init, no white flash)
│   │   ├── discord/              # Discord Rich Presence (gateway, RPC)
│   │   ├── extension/            # .egg loader + manager + plugin store client
│   │   ├── model/                # EditorSettings, EditorTab, FileNode
│   │   ├── provider/             # EggDocumentsProvider (SAF provider)
│   │   ├── ui/
│   │   │   ├── EggApp.kt         # Root composable
│   │   │   ├── EggDialogs.kt     # Dialog collection
│   │   │   ├── EggViewModel.kt   # Main ViewModel (tabs, file tree, git)
│   │   │   ├── FileManagerPanel.kt
│   │   │   ├── TerminalPanel.kt
│   │   │   ├── WelcomeScreen.kt
│   │   │   ├── components/       # ActionBar, TabBar, MarkdownPreview, …
│   │   │   ├── diff/             # Git diff view
│   │   │   ├── editor/           # SoraCodeEditor wrapper
│   │   │   ├── git/              # Git panel + remote dialog
│   │   │   ├── terminal/         # Terminal notifications
│   │   │   └── theme/            # Color, Theme, Type
│   │   └── util/                 # FileUtils, LineDiff, SettingsStorage, SupabaseClient
│   ├── src/main/assets/          # TextMate grammars (50 languages), themes, JS libs
│   ├── src/main/jniLibs/         # Native libs (libpdn, libproot-loader, arm64-v8a)
│   └── libs/pdn-engine-0.6.18.aar
├── extension-api/                # Plugin SDK (Kotlin, publishable)
│   └── src/main/java/com/egg/extension/api/
│       ├── ExtensionAPI.kt       # Base class for code plugins
│       ├── ExtensionContext.kt   # Context passed to plugins
│       ├── ExtensionManifest.kt  # manifest.json model
│       ├── ThemeRegistry.kt      # Compose + Sora theme registry
│       ├── GrammarRegistry.kt    # TextMate grammar registry
│       └── IconPackRegistry.kt   # File icon override registry
├── plugin-templates/             # No-code plugin templates
│   ├── theme-only/               # Copy, edit JSON, zip → .egg
│   └── grammar-only/             # Copy, add .tmLanguage.json, zip → .egg
├── plugins/                      # Code-based plugin examples (Gradle)
│   ├── theme/neon-icons/
│   └── grammar/typescript/
├── termlib-local/                # Terminal widget (termux termlib fork)
├── terminal/                     # Terminal UI + Ubuntu 24.04 rootfs runner
├── terminal-emulator/            # VT100/xterm emulator core
├── terminal-view/                # Compose terminal view
├── proot/                        # proot (C) — user-space chroot for Android
├── link2symlink/                 # proot helper (D + C)
└── build.gradle.kts, settings.gradle.kts
```

## Extension system

### Manual plugin (no code, no Gradle)

```bash
cp -r plugin-templates/theme-only my-theme
cd my-theme
# edit manifest.json + theme/compose.json
./zip.sh my-theme         # → my-theme.egg
```

Install from app: **Settings → Extensions → Install .egg**.

See `plugin-templates/README.md` for full guide (theme + grammar, English).

### Code plugin (Kotlin, Gradle)

```bash
cd plugins/theme/neon-icons
./gradlew assembleEgg      # → build/neon-icons.egg
```

Plugin depends on `com.egg:extension-api` (via `includeBuild` or Maven).

See `plugins/theme/neon-icons/` and `plugins/grammar/typescript/`.

### Plugin store

Hosted at [kLinQin/egg-plugin-store](https://github.com/kLinQin/egg-plugin-store)
(GitHub raw index). Built-in store UI in **Settings → Extensions → Store**.

Currently available: themes, grammars, and icon packs (see store for the live list).

## Third-party Notices

- **Sora Editor** — LGPL-2.1
- **JGit** — EPL-2.0
- **termux termlib** — Apache-2.0
- **pdn-engine** — bundled AAR (see `app/libs/`), legacy terminal runtime
- **OkHttp** — Apache-2.0
- **marked.js, highlight.js** — MIT / BSD (bundled in assets)

## License

Apache License 2.0 — see [LICENSE](LICENSE). Third-party components listed above
retain their own licenses.
