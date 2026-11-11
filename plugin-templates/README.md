# Egg Plugin Templates

Manual plugin templates for Egg Code Editor. **No code, no Gradle, no Kotlin** required — just edit JSON files and zip them into a `.egg` file.

## What is a `.egg` file?

A `.egg` file is a regular ZIP archive containing:

```
manifest.json          # plugin metadata (required)
theme/compose.json     # theme definition (optional)
grammar/<lang>.json    # TextMate grammar (optional)
```

> Code-based plugins (Kotlin) also include `classes.dex`. For manual plugins, **`classes.dex` is optional** — Egg loads theme/grammar/icon packs directly from assets.

## Available Templates

| Template | What it does | Files |
|----------|--------------|-------|
| `theme-only/` | Custom color scheme (background, surface, primary, etc.) | `manifest.json` + `theme/compose.json` |
| `grammar-only/` | Add syntax highlighting for a new language | `manifest.json` + `grammar/*.tmLanguage.json` + `.meta.json` |

## Quick Start (Theme Plugin)

### 1. Copy the template

```bash
cp -r plugin-templates/theme-only my-awesome-theme
cd my-awesome-theme
```

### 2. Edit `manifest.json`

```json
{
  "id": "com.yourname.awesome",
  "name": "Awesome Theme",
  "mainClass": "com.yourname.awesome.Main",
  "version": "1.0.0",
  "description": "My first Egg theme",
  "author": { "displayName": "Your Name" }
}
```

| Field | Required | Description |
|-------|----------|-------------|
| `id` | Yes | Unique identifier (reverse-DNS recommended). |
| `name` | Yes | Display name shown in Settings > Extensions. |
| `mainClass` | Yes | Kotlin class name. For assets-only plugins, unused. |
| `version` | optional | Semantic version string. |
| `description` | optional | Short description. |
| `author.displayName` | optional | Author name. |

### 3. Edit `theme/compose.json`

```json
{
  "id": "awesome",
  "name": "Awesome",
  "base": "dark",
  "dark": true,
  "colors": {
    "primary": "#FF6B6B",
    "onPrimary": "#000000",
    "background": "#1A1A2E",
    "onBackground": "#EAEAEA",
    "surface": "#16213E",
    "onSurface": "#EAEAEA",
    "surfaceVariant": "#0F3460",
    "onSurfaceVariant": "#B0B0C0",
    "outline": "#404060"
  }
}
```

| Field | Required | Description |
|-------|----------|-------------|
| `id` | Yes | Theme ID (internal). Must be unique. |
| `name` | Yes | Display name in Settings > App > Plugin Themes. |
| `base` | Yes | `"dark"` or `"light"` — base scheme to extend. |
| `dark` | optional | Override dark detection. |
| `colors` | Yes | Map of color key to hex string (`#RRGGBB` or `#AARRGGBB`). |

#### Available Color Keys

All keys are **optional** — missing keys inherit from base scheme:

```
primary, onPrimary, secondary, onSecondary, tertiary, onTertiary,
background, onBackground, surface, onSurface,
surfaceVariant, onSurfaceVariant, outline,
error, onError,
primaryContainer, secondaryContainer,
surfaceContainerLowest, surfaceContainerLow, surfaceContainer,
surfaceContainerHigh, surfaceContainerHighest
```

### 4. Package as `.egg`

**Linux / macOS:**
```bash
cd my-awesome-theme
zip -r ../my-awesome-theme.egg .
```

**Windows (PowerShell):**
```powershell
Compress-Archive -Path .\* -DestinationPath ..\my-awesome-theme.zip
Rename-Item ..\my-awesome-theme.zip my-awesome-theme.egg
```

**Or use provided script:**
```bash
./zip.sh my-awesome-theme
```

### 5. Install on device

1. Open Egg → **Settings** → **Extensions** tab
2. Tap **Install .egg**
3. Pick `my-awesome-theme.egg`
4. Go to **Settings** → **App** → scroll to **Plugin Themes**
5. Select **Awesome**

## Quick Start (Grammar Plugin)

### 1. Copy template

```bash
cp -r plugin-templates/grammar-only my-language-grammar
cd my-language-grammar
```

### 2. Get a TextMate grammar

Download from:
- [VS Code grammars](https://github.com/microsoft/vscode/tree/main/extensions) — e.g. `extensions/typescript-basics/syntaxes/TypeScript.tmLanguage.json`
- [shiki tm-grammars](https://github.com/shikijs/textmate-grammars-themes/tree/main/packages/tm-grammars/grammars)

Place file as `grammar/<language>.tmLanguage.json`.

### 3. Create `grammar/<language>.meta.json`

```json
{
  "name": "typescript",
  "scopeName": "source.ts",
  "extensions": [".ts", ".tsx"]
}
```

### 4. Edit `manifest.json`

Same as theme — change `id`, `name`, `description`.

### 5. Package + install

Same as theme (zip → `.egg` → Install via Settings).

## Reference: Loader Behavior

Egg's `ExtensionLoader`:

1. Extract `.egg` (zip) → `filesDir/extensions/<id>/`
2. Read `manifest.json` (required)
3. If `classes.dex` exists → load `mainClass` via `DexClassLoader`
4. **Always** auto-scan assets:
   - `theme/compose.json` → `ThemeRegistry`
   - `grammar/*.tmLanguage.json` (+ `.meta.json`) → `GrammarRegistry`

## Troubleshooting

| Problem | Cause | Fix |
|---------|-------|-----|
| "manifest.json missing" | Wrong zip structure | `manifest.json` must be at **root** of zip, not inside a folder. |
| Theme doesn't appear | Missing/invalid `theme/compose.json` | Check with `unzip -l your.egg`. Validate JSON. |
| Grammar not applied | `scopeName` mismatch | Grammar's internal `scopeName` must match `.meta.json`'s `scopeName`. |
| "classes.dex missing" | Old Egg version | Update Egg — optional since `e93cde6+`. |

## Manual vs Code Plugin

| Feature | Manual (this folder) | Code (Kotlin) |
|---------|---------------------|---------------|
| Theme | Yes | Yes |
| Grammar | Yes | Yes |
| Icon pack | Yes (planned) | Yes |
| Custom commands | No | Yes |
| Network / API | No | Yes |
| Build tools needed | **None** | Android Studio + Gradle |

For code plugins, see `plugins/` folder.

## License

MIT.
