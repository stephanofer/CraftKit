# craftkit-feedback

Localized Paper/Adventure feedback helpers for CraftKit consumers.

Consumers own their BoostedYAML language document lifecycle. This module receives loaded `YamlDocument` references, resolves the player's effective language through NetworkPlayerSettings, applies fallback, parses MiniMessage, and sends chat, action bar, title, sound, and boss bar feedback.

## Compile-only NetworkPlayerSettings API

This repository does not contain a published Maven coordinate or local JAR for NetworkPlayerSettings. The module build expects the API jar at:

```text
libs/NetworkPlayerSettings.jar
```

That jar must expose `com.stephanofer.networkplayersettings.api.PlayerSettingsService` as documented in `docs/NetworkPlayerSettings/api-publica.md`.
