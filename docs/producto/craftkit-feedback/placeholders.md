# Placeholders

`craftkit-feedback` usa placeholders de Adventure MiniMessage mediante `TagResolver`. El módulo incluye helpers públicos en `FeedbackPlaceholders` para evitar repetir llamadas de bajo nivel.

## `text(name, value)`

```java
FeedbackPlaceholders.text("player", player.getName())
```

Equivale a `Placeholder.unparsed(name, value)`. Es la opción segura para datos de usuario, nombres de jugadores, nombres de clanes, argumentos de comandos y cualquier texto no confiable.

Si `value` es `null`, usa string vacío.

## `parsed(name, value)`

```java
FeedbackPlaceholders.parsed("prefix", "<gold>[VIP]</gold>")
```

Equivale a `Placeholder.parsed(name, value)`. El valor se interpreta como MiniMessage. Debe usarse solo con valores confiables, controlados por el plugin o por configuración administrada.

Si `value` es `null`, usa string vacío.

## `component(name, Component)`

```java
FeedbackPlaceholders.component("item", item.displayName())
```

Inserta un `Component` ya construido. Si el componente es `null`, usa `Component.empty()`.

## `number(name, Number)`

```java
FeedbackPlaceholders.number("amount", 25)
```

Convierte el número con `toString()` y lo pasa como placeholder sin parsear. Si el número es `null`, usa `"0"`.

## Ejemplo completo

YAML:

```yaml
rewards:
  claimed:
    outputs:
      - type: chat
        message: '<green><player> reclamó <amount>x <reward>.</green>'
```

Java:

```java
feedback.send(player, "rewards.claimed",
    FeedbackPlaceholders.text("player", player.getName()),
    FeedbackPlaceholders.number("amount", amount),
    FeedbackPlaceholders.component("reward", rewardName));
```

## Regla de seguridad

Usar `text(...)` por defecto. Usar `parsed(...)` únicamente cuando el valor de reemplazo debe contener MiniMessage y proviene de una fuente confiable.
