# Reload y cierre

El plugin consumidor es dueño del lifecycle de los `YamlDocument`. `craftkit-feedback` solo conserva referencias en memoria y las consulta cuando se envía o resuelve una traducción.

## Reload de idiomas

`craftkit-feedback` no llama `YamlDocument.reload()`. Un flujo correcto de reload es:

1. El consumidor ejecuta su propio reload de archivos.
2. El consumidor reconstruye `FeedbackTranslations` con los `YamlDocument` actuales.
3. El consumidor llama `feedback.replaceTranslations(newTranslations)`.

Ejemplo:

```java
public void reloadLanguages() {
    english.reload();
    spanish.reload();

    FeedbackTranslations translations = FeedbackTranslations.boostedYaml()
        .language("en", english)
        .language("es", spanish)
        .build();

    feedback.replaceTranslations(translations);
}
```

`replaceTranslations(...)` valida que el idioma default original siga presente. Si falta, lanza `IllegalArgumentException`.

## Lectura en memoria

Cada envío consulta el `YamlDocument` en memoria mediante `document.get(key, null)`. El módulo no hace I/O de disco por envío. El costo de cargar, recargar, guardar defaults o actualizar headers pertenece al consumidor y a BoostedYAML.

## Cierre obligatorio

`Feedback` extiende `AutoCloseable`, pero normalmente se usa de forma persistente durante todo el lifecycle del plugin. En `onDisable()`, llamar siempre:

```java
@Override
public void onDisable() {
    if (feedback != null) {
        feedback.close();
    }
}
```

`close()` hace lo siguiente:

- cancela las tareas de ocultado automático de bossbars aún trackeadas;
- oculta cada bossbar trackeada al jugador correspondiente;
- limpia el mapa interno de bossbars activas.

Esto es especialmente importante para bossbars con `duration <= 0`, inválida o ausente, porque quedan visibles hasta que se oculten manualmente o se cierre la integración.

## Sobre actionbars persistentes

El módulo no provee scheduler persistente de actionbars. Para colas, waiting rooms o estados que deben refrescarse cada cierto intervalo, el consumidor debe crear su propia tarea y llamar `feedback.actionBar(...)` periódicamente en el hilo principal.
