# Envío, canales y resolución

La interfaz `Feedback` separa dos formas de uso:

- `send(...)`: contrato declarativo por `outputs`, recomendado para feedback de comandos y eventos que puede combinar varios canales.
- métodos directos: `chat(...)`, `actionBar(...)`, `title(...)`, `sound(...)`, `component(...)`, `components(...)`, útiles cuando el consumidor fuerza un canal concreto o necesita componentes sin enviar.

## `send(Player, String, TagResolver...)`

`send(...)` busca la key traducida y aplica esta lógica:

1. Si la key falta, envía por chat el componente visible `[missing translation: key]`.
2. Si la key existe y no es sección, envía el valor convertido a string por chat.
3. Si la key es sección, lee `outputs` como lista de mapas.
4. Si `outputs` está vacío o no existe, no envía nada.
5. Itera cada mapa y despacha por `type`.

Ejemplo:

```java
feedback.send(player, "queue.joined",
    FeedbackPlaceholders.text("mode", "Ranked"),
    FeedbackPlaceholders.number("players", 4));
```

## Despacho exacto por tipo

### `NONE`

No ejecuta ninguna acción. También es el fallback cuando `type` falta o no coincide con ningún valor de `FeedbackOutputType`.

### `CHAT` y `CENTERED_CHAT`

Ambos ejecutan la misma rama de código:

- si existe `messages` como lista, envía cada elemento como una línea;
- si no existe `messages`, usa `message`;
- si tampoco existe `message`, envía `Component.empty()`.

No hay lógica de centrado para `CENTERED_CHAT` en la implementación actual.

### `ACTION_BAR`

Lee `message`, lo parsea como MiniMessage y llama:

```java
player.sendActionBar(component);
```

Si `message` falta, envía un componente vacío.

### `TITLE`

Lee:

- `title`, default `""`;
- `subtitle`, default `""`;
- `fade-in`, default `10` ticks;
- `stay`, default `70` ticks;
- `fade-out`, default `20` ticks.

Los ticks se convierten a `Duration` con `ticks * 50 ms`.

### `SOUND`

Lee:

- `sound`: key Adventure, obligatoria para reproducir;
- `source`: default `PLAYER`;
- `volume`: default `1.0`;
- `pitch`: default `1.0`.

Si `sound` falta o está en blanco, no reproduce nada. `source` inválido registra warning y cae a `PLAYER`. `volume` y `pitch` inválidos caen silenciosamente a sus defaults.

### `BOSS_BAR`

Lee:

- `message`: default `""`;
- `progress`: default `1.0`, limitado entre `0.0` y `1.0`;
- `color`: default `WHITE`;
- `overlay`: default `PROGRESS`;
- `duration`: default `0` ticks.

La bossbar se muestra con `player.showBossBar(...)` y se registra internamente. Si `duration > 0`, se agenda un `runTaskLater` para ocultarla y removerla del tracking. Todas las bossbars aún trackeadas se ocultan en `feedback.close()`.

## Métodos directos

Los métodos directos son imperativos: fuerzan un canal y no interpretan `outputs` como lo hace `send(...)`.

### `chat(...)`

```java
feedback.chat(player, "direct.message");
```

Internamente llama `components(...)` y envía cada componente por chat.

### `actionBar(...)`

```java
feedback.actionBar(player, "queue.status");
```

Internamente llama `component(...)` y envía el resultado por actionbar.

### `title(...)`

```java
feedback.title(player, "match.starting");
```

Si la key falta, muestra un title con `[missing translation: key]` como título y subtitle vacío. Si existe:

- si el valor es un mapa/sección, usa `title`, `subtitle`, `fade-in`, `stay`, `fade-out`;
- si no, usa el valor como título y subtitle vacío.

### `sound(...)`

```java
feedback.sound(player, "reward.sound");
```

Si la key falta o no puede resolverse a sonido, no reproduce nada. Acepta string simple como sound key o sección/mapa con `sound`, `source`, `volume`, `pitch`.

## Resolver sin enviar

### `component(...)`

```java
Component name = feedback.component(player, "items.sword.name");
```

Interpretación real:

- string: parsea el string como MiniMessage;
- sección: usa `message`; si no existe, usa `title`; si no existe ninguno, convierte la sección a string;
- otro valor: `String.valueOf(raw)` parseado como MiniMessage;
- missing key: devuelve `[missing translation: key]`.

### `components(...)`

```java
List<Component> lore = feedback.components(player, "items.sword.lore");
```

Interpretación real:

- lista: convierte cada elemento a string y lo parsea;
- sección con `messages`: parsea cada línea;
- sección con `message`: devuelve una lista de un componente;
- otros valores: delega a `component(...)` y envuelve en lista;
- missing key: devuelve lista con `[missing translation: key]`.

Este método es el recomendado para lore de ítems, líneas de menús y textos multi-línea que no deben enviarse inmediatamente.
