# Referencia de API pública

La API pública del módulo está en `com.hera.craftkit.feedback`. Las clases en `com.hera.craftkit.feedback.internal` son detalles de implementación aunque algunos tipos sean visibles dentro del proyecto.

## `Feedbacks`

Factory de entrada.

```java
public final class Feedbacks {
    public static BukkitFeedbackBuilder paper(Plugin plugin);
}
```

| Método | Comportamiento |
| --- | --- |
| `paper(Plugin plugin)` | Requiere `plugin` no nulo y devuelve un `BukkitFeedbackBuilder`. |

## `BukkitFeedbackBuilder`

El builder está en paquete `internal`, pero es el tipo retornado por `Feedbacks.paper(plugin)` y por eso forma parte del flujo de consumo.

```java
public final class BukkitFeedbackBuilder {
    public BukkitFeedbackBuilder networkPlayerSettingsRequired();
    public BukkitFeedbackBuilder defaultLanguage(String defaultLanguage);
    public BukkitFeedbackBuilder translations(FeedbackTranslations translations);
    public Feedback build();
}
```

| Método | Comportamiento |
| --- | --- |
| `networkPlayerSettingsRequired()` | Marca que el consumidor acepta/requiere NetworkPlayerSettings. Si no se llama, `build()` falla. |
| `defaultLanguage(String)` | Normaliza idioma. Rechaza `null`, blanco o solo espacios. Default inicial: `en`. |
| `translations(FeedbackTranslations)` | Define el set de traducciones. Rechaza `null`. |
| `build()` | Valida configuración, carga `PlayerSettingsService` desde Bukkit y crea `Feedback`. |

`build()` puede lanzar `IllegalStateException` o `FeedbackMissingDependencyException` según la causa.

## `Feedback`

Interfaz principal de uso.

```java
public interface Feedback extends AutoCloseable {
    void send(Player player, String key, TagResolver... placeholders);
    void chat(Player player, String key, TagResolver... placeholders);
    void actionBar(Player player, String key, TagResolver... placeholders);
    void title(Player player, String key, TagResolver... placeholders);
    void sound(Player player, String key, TagResolver... placeholders);
    Component component(Player player, String key, TagResolver... placeholders);
    List<Component> components(Player player, String key, TagResolver... placeholders);
    void replaceTranslations(FeedbackTranslations translations);
    void close();
}
```

| Método | Contrato observable |
| --- | --- |
| `send(...)` | Envía una key declarativa. Si hay sección `outputs`, despacha cada salida. Si la key es simple, envía chat. |
| `chat(...)` | Resuelve `components(...)` y envía cada componente por chat. |
| `actionBar(...)` | Resuelve `component(...)` y lo envía por actionbar. |
| `title(...)` | Resuelve title desde string, mapa o sección. Missing key muestra fallback visible como título. |
| `sound(...)` | Resuelve sonido desde string, mapa o sección. Missing key o sonido ausente no reproduce nada. |
| `component(...)` | Devuelve un componente parseado por MiniMessage o fallback visible. |
| `components(...)` | Devuelve lista parseada desde lista, `messages`, `message` o fallback visible. |
| `replaceTranslations(...)` | Reemplaza atómicamente las traducciones. Requiere que el idioma default exista. |
| `close()` | Cancela ocultados pendientes y oculta bossbars trackeadas. |

Parámetros `player` y `key` no deben ser `null` en los métodos que los usan. La implementación valida `key` y `send(...)` valida `player`; otros métodos delegan a llamadas Paper que también requieren jugador válido.

## `FeedbackTranslations`

Contenedor de traducciones por idioma.

```java
public final class FeedbackTranslations {
    public static BoostedYamlBuilder boostedYaml();
    public Optional<YamlDocument> document(String language);
    public boolean supports(String language);
    public Map<String, YamlDocument> documents();
    public static String normalizeLanguage(String language);
}
```

| Método | Comportamiento |
| --- | --- |
| `boostedYaml()` | Crea builder para documentos BoostedYAML. |
| `document(language)` | Normaliza idioma y devuelve documento si existe. |
| `supports(language)` | Indica si el idioma normalizado existe. |
| `documents()` | Devuelve el mapa interno inmutable. |
| `normalizeLanguage(language)` | `null -> ""`; si no, `trim().toLowerCase(Locale.ROOT)`. |

### `FeedbackTranslations.BoostedYamlBuilder`

```java
public static final class BoostedYamlBuilder {
    public BoostedYamlBuilder language(String language, YamlDocument document);
    public FeedbackTranslations build();
}
```

| Método | Comportamiento |
| --- | --- |
| `language(...)` | Normaliza idioma, rechaza blanco y documento nulo, guarda `language -> document`. |
| `build()` | Requiere al menos un idioma y devuelve `FeedbackTranslations`. |

## `FeedbackPlaceholders`

Helpers para placeholders MiniMessage.

```java
public final class FeedbackPlaceholders {
    public static TagResolver text(String name, String value);
    public static TagResolver parsed(String name, String value);
    public static TagResolver component(String name, Component value);
    public static TagResolver number(String name, Number value);
}
```

| Método | Comportamiento |
| --- | --- |
| `text(name, value)` | `Placeholder.unparsed`; `null` se vuelve `""`. Seguro para input no confiable. |
| `parsed(name, value)` | `Placeholder.parsed`; `null` se vuelve `""`. Solo para contenido confiable. |
| `component(name, value)` | `Placeholder.component`; `null` se vuelve `Component.empty()`. |
| `number(name, value)` | `Placeholder.unparsed`; `null` se vuelve `"0"`; no aplica formato local. |

## Excepciones

### `FeedbackException`

```java
public class FeedbackException extends RuntimeException {
    public FeedbackException(String message);
    public FeedbackException(String message, Throwable cause);
}
```

Excepción base unchecked del módulo.

### `FeedbackMissingDependencyException`

```java
public final class FeedbackMissingDependencyException extends FeedbackException {
    public FeedbackMissingDependencyException(String message);
}
```

Se lanza cuando `build()` no encuentra `PlayerSettingsService` registrado.
