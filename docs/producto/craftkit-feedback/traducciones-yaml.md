# Traducciones y formatos YAML

`craftkit-feedback` usa `FeedbackTranslations` como contenedor inmutable de documentos BoostedYAML: cada código de idioma apunta a un `YamlDocument` ya cargado por el consumidor.

## Crear traducciones

```java
FeedbackTranslations translations = FeedbackTranslations.boostedYaml()
    .language("en", englishDocument)
    .language("es", spanishDocument)
    .build();
```

Reglas reales del builder:

- `language(null, document)` o un idioma blanco falla con `IllegalArgumentException`.
- `document` no puede ser `null`.
- `build()` requiere al menos un documento.
- Los códigos se normalizan con `trim().toLowerCase(Locale.ROOT)`.
- Si se registra dos veces el mismo idioma normalizado, el último documento reemplaza al anterior en el mapa interno.

## Idioma default obligatorio

`Feedbacks.paper(plugin).defaultLanguage("en")` también normaliza el código. `build()` y `replaceTranslations(...)` verifican que el idioma default exista en `FeedbackTranslations`.

Si el idioma default no está presente, se lanza:

```text
IllegalStateException: Default language 'en' has no translation document.
```

o `IllegalArgumentException` en `replaceTranslations(...)`.

## Lookup de keys

Para una llamada `feedback.component(player, "queue.joined")`, el módulo hace:

1. obtiene idioma preferido del jugador;
2. busca `queue.joined` en el documento del idioma preferido;
3. si no existe y el preferido no es el default, busca la key en el documento default;
4. si no existe en ningún documento, registra warning una vez por combinación `language:key` y devuelve `[missing translation: queue.joined]` en métodos que resuelven componentes o envían fallback visible.

La búsqueda usa `YamlDocument#get(key, null)`, por lo que las rutas con puntos siguen las reglas de BoostedYAML.

## Formato estructurado `outputs`

El método principal `send(...)` espera, para flujos multi-canal, una sección con lista `outputs`:

```yaml
queue:
  joined:
    outputs:
      - type: chat
        messages:
          - '<green>Entraste a la cola.</green>'
          - '<gray>Modo: <mode></gray>'
      - type: action_bar
        message: '<yellow>Buscando partida...</yellow>'
      - type: sound
        sound: 'minecraft:block.note_block.pling'
```

`send(player, "queue.joined", ...)` itera la lista en orden y despacha cada salida.

Si la key existe pero no es una sección, `send(...)` la convierte a string, la parsea como MiniMessage y la envía por chat.

Si la key es una sección sin `outputs` o con `outputs` vacío, `send(...)` no envía nada.

## Tipos de salida

El campo `type` se procesa con `trim()`, reemplazo de `-` por `_` y uppercase. Por ejemplo, `action-bar`, `action_bar` y `ACTION_BAR` resuelven a `ACTION_BAR`.

Tipos soportados:

| Tipo | Comportamiento |
| --- | --- |
| `none` | No hace nada. También es fallback para `type` ausente o desconocido. |
| `chat` | Envía `messages` si existe; si no, `message`; si no hay ninguno, envía componente vacío. |
| `centered_chat` | En el código actual se comporta igual que `chat`; no calcula centrado visual. |
| `action_bar` | Envía `message` por actionbar; si falta, envía string vacío. |
| `title` | Usa `title`, `subtitle`, `fade-in`, `stay`, `fade-out`. |
| `sound` | Usa `sound`, `source`, `volume`, `pitch`. Sin `sound`, no reproduce nada. |
| `boss_bar` | Usa `message`, `progress`, `color`, `overlay`, `duration`. |

## Ejemplos por canal

### `CHAT` y `CENTERED_CHAT`

```yaml
welcome:
  outputs:
    - type: chat
      messages:
        - '<green>Bienvenido, <player>.</green>'
        - '<gray>Usá /help para ver comandos.</gray>'
    - type: centered_chat
      message: '<gold>Servidor HERA</gold>'
```

### `ACTION_BAR`

```yaml
queue:
  searching:
    outputs:
      - type: action_bar
        message: '<yellow>Buscando partida... <players>/<required></yellow>'
```

### `TITLE`

```yaml
match:
  found:
    outputs:
      - type: title
        title: '<green>Partida encontrada</green>'
        subtitle: '<gray>Preparando conexión...</gray>'
        fade-in: 10
        stay: 70
        fade-out: 20
```

Los tiempos están en ticks y se convierten internamente a `Duration` multiplicando por 50 ms. Defaults: `fade-in=10`, `stay=70`, `fade-out=20`.

### `SOUND`

```yaml
reward:
  claimed:
    outputs:
      - type: sound
        sound: 'minecraft:entity.player.levelup'
        source: player
        volume: 1.0
        pitch: 1.2
```

`source` usa `Sound.Source`. Valores inválidos caen a `PLAYER` y registran warning en el logger de Bukkit.

### `BOSS_BAR`

```yaml
event:
  active:
    outputs:
      - type: boss_bar
        message: '<yellow>Evento activo</yellow>'
        progress: 0.75
        color: yellow
        overlay: progress
        duration: 100
```

`progress` se limita entre `0.0` y `1.0`. `duration` está en ticks; si es mayor a `0`, agenda ocultado automático. Si es `0`, negativo, inválido o ausente, la bossbar queda visible hasta que el consumidor la reemplace/oculte por otro medio o llame `feedback.close()`.

### `NONE`

```yaml
debug:
  disabled:
    outputs:
      - type: none
```

## Formatos simples para métodos directos

Los métodos `chat(...)`, `actionBar(...)`, `title(...)`, `sound(...)`, `component(...)` y `components(...)` no usan `outputs` como contrato principal. Interpretan strings, listas o secciones según el canal.

```yaml
direct:
  chat-line: '<green>Mensaje simple</green>'
  lore:
    - '<gray>Línea 1</gray>'
    - '<gray>Línea 2</gray>'
  actionbar:
    message: '<yellow>Acción</yellow>'
  title:
    title: '<green>Título</green>'
    subtitle: '<gray>Subtítulo</gray>'
  sound:
    sound: 'minecraft:block.note_block.pling'
    source: player
    volume: 1.0
    pitch: 1.0
```
