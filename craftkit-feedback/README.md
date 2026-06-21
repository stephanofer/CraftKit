# craftkit-feedback

Localized Paper/Adventure feedback helpers for CraftKit consumers.

Consumers own their BoostedYAML language document lifecycle. This module receives loaded `YamlDocument` references, resolves the player's effective language through NetworkPlayerSettings, applies fallback, parses MiniMessage, and sends chat, action bar, title, sound, and boss bar feedback.

## Compile-only NetworkPlayerSettings API

This repository resolves NetworkPlayerSettings through the shared version catalog using the published Maven coordinate `com.stephanofer:networkplayersettings:2.0.0`.

That dependency must expose `com.stephanofer.networkplayersettings.settings.api.PlayerSettingsService` as documented in `docs/NetworkPlayerSettings/api-publica.md`.
