# Errores, límites y buenas prácticas

## Errores frecuentes

### Falta `networkPlayerSettingsRequired()`

`build()` lanza:

```text
IllegalStateException: NetworkPlayerSettings is required by craftkit-feedback. Call networkPlayerSettingsRequired().
```

Solución: llamar explícitamente `.networkPlayerSettingsRequired()` en el builder.

### Falta `PlayerSettingsService`

Si NetworkPlayerSettings no está habilitado o todavía no registró el servicio, `build()` lanza `FeedbackMissingDependencyException`.

Soluciones:

- declarar `depend: [NetworkPlayerSettings]`;
- construir `Feedback` después del enable de NetworkPlayerSettings;
- si la integración es opcional, capturar la excepción y desactivar solo las features dependientes.

### Falta el idioma default

`build()` y `replaceTranslations(...)` verifican que el idioma default exista. El documento default debe ser el más completo, porque es el fallback para idiomas o keys faltantes.

### Key faltante

Si una key no existe en el idioma actual ni en el default:

- se registra un warning una vez por combinación `language:key`;
- `send(...)`, `component(...)`, `components(...)` y `title(...)` muestran fallback visible según el método;
- `sound(...)` no reproduce nada si no hay valor resoluble.

Fallback visible:

```text
[missing translation: key]
```

### Enum inválido

`Sound.Source`, `BossBar.Color` y `BossBar.Overlay` se parsean aceptando guiones como guiones bajos y case-insensitive. Valores inválidos caen al default y registran warning en el logger global de Bukkit.

Defaults:

- `Sound.Source`: `PLAYER`;
- `BossBar.Color`: `WHITE`;
- `BossBar.Overlay`: `PROGRESS`.

### Números inválidos

Campos numéricos inválidos caen silenciosamente a sus defaults:

- title timings: `10`, `70`, `20` ticks;
- sound `volume`: `1.0`;
- sound `pitch`: `1.0`;
- bossbar `progress`: `1.0`;
- bossbar `duration`: `0`.

## Límites operativos

- Las llamadas que tocan Paper/Adventure audience del jugador deben ejecutarse en el hilo principal del servidor.
- `CENTERED_CHAT` no centra visualmente el texto en la implementación actual.
- No hay scheduler persistente para actionbars; el consumidor debe crear loops propios.
- No hay limpieza automática por quit de las bossbars trackeadas; `close()` limpia todas las que sigan registradas.
- El módulo no hace I/O por envío; el consumidor controla reload y actualización de documentos.

## Do / Don't

| Do | Don't |
| --- | --- |
| Usar `send(...)` para feedback declarativo multi-canal. | Mezclar `outputs` con métodos directos esperando el mismo comportamiento. |
| Mantener completo el documento del idioma default. | Asumir que un idioma no soportado fallará inmediatamente. Hace fallback al default. |
| Usar `FeedbackPlaceholders.text(...)` para input de jugadores. | Usar `parsed(...)` con texto escrito por usuarios. |
| Llamar `feedback.close()` en `onDisable()`. | Dejar bossbars sin cierre cuando `duration` es `0` o inválida. |
| Recargar `YamlDocument` en el consumidor y luego llamar `replaceTranslations(...)`. | Esperar que `craftkit-feedback` recargue archivos desde disco. |
| Declarar `depend: [NetworkPlayerSettings]` si el módulo es obligatorio. | Construir `Feedback` antes de que `PlayerSettingsService` exista. |
| Ejecutar envíos desde el hilo principal. | Tocar APIs Paper desde callbacks async sin volver al scheduler correcto. |
