# Lifecycle y NetworkPlayerSettings

`craftkit-feedback` delega la decisión de idioma a NetworkPlayerSettings. El módulo no persiste idioma, no lee país y no implementa preferencias propias.

## Construcción de `Feedback`

El punto de entrada es:

```java
Feedback feedback = Feedbacks.paper(plugin)
    .networkPlayerSettingsRequired()
    .defaultLanguage("en")
    .translations(translations)
    .build();
```

Durante `build()`, `BukkitFeedbackBuilder` ejecuta:

```java
plugin.getServer().getServicesManager().load(PlayerSettingsService.class)
```

Si el resultado es `null`, lanza `FeedbackMissingDependencyException`.

## Resolución de idioma por jugador

La instancia final de `Feedback` usa un resolver interno con este comportamiento:

1. Consulta `settings.isReady(player.getUniqueId())`.
2. Si el jugador no está listo, devuelve el idioma default configurado en el builder.
3. Si el jugador está listo, devuelve `settings.resolvedLanguage(player).code()`.

Esto significa que antes de `PlayerSettingsReadyEvent`, o en cualquier momento donde NetworkPlayerSettings considere que el jugador no está listo, el feedback usa el idioma default.

## Responsabilidades de NetworkPlayerSettings

Según la documentación existente de `NetworkPlayerSettings`, `PlayerSettingsService` expone:

```java
boolean isReady(UUID playerId);
Language resolvedLanguage(Player player);
```

`craftkit-feedback` solo consume esos métodos. Las reglas de preferencia `AUTO`, idioma explícito, detección de locale y fallback de NetworkPlayerSettings pertenecen a NetworkPlayerSettings.

## Responsabilidades del plugin consumidor

El consumidor debe:

- declarar dependencia de runtime sobre `NetworkPlayerSettings` si el feedback es obligatorio;
- construir `Feedback` después de que `PlayerSettingsService` esté registrado;
- manejar `FeedbackMissingDependencyException` si permite operar sin NetworkPlayerSettings;
- evitar asumir que todos los jugadores están listos apenas entran al servidor;
- definir un idioma default que exista en las traducciones.

## Qué ocurre con idiomas no soportados

Si NetworkPlayerSettings devuelve un código de idioma que no existe en `FeedbackTranslations`, el lookup intenta ese idioma y luego el idioma default. Por lo tanto, el plugin consumidor debe asegurarse de que el idioma default sea completo.

Si una key no existe ni en el idioma actual ni en el idioma default, se registra un warning y se devuelve un fallback visible.
