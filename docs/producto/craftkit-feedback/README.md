# `craftkit-feedback`

`craftkit-feedback` es el módulo de CraftKit para enviar feedback localizado a jugadores Paper usando Adventure y archivos `YamlDocument` de BoostedYAML. Centraliza la resolución de idioma mediante NetworkPlayerSettings, la lectura de traducciones en memoria, el parseo MiniMessage y el despacho de mensajes por canal.

La regla principal es: **el plugin consumidor sigue siendo dueño de sus archivos de idioma y de su lifecycle**. CraftKit no carga archivos por sí mismo, no persiste preferencias de idioma y no reemplaza las APIs de Paper, Adventure ni NetworkPlayerSettings.

## Qué resuelve

- Crea una instancia `Feedback` para plugins Paper mediante `Feedbacks.paper(plugin)`.
- Requiere explícitamente NetworkPlayerSettings mediante `.networkPlayerSettingsRequired()`.
- Lee `PlayerSettingsService` desde Bukkit `ServicesManager` durante `build()`.
- Resuelve el idioma efectivo del jugador cuando NetworkPlayerSettings está listo.
- Usa idioma default cuando el jugador todavía no está listo.
- Mantiene un mapa `idioma -> YamlDocument` provisto por el consumidor.
- Normaliza códigos de idioma con `trim().toLowerCase(Locale.ROOT)`.
- Aplica fallback de traducción desde idioma actual hacia idioma default.
- Parsea strings con MiniMessage y placeholders Adventure.
- Envía salidas estructuradas `CHAT`, `CENTERED_CHAT`, `ACTION_BAR`, `TITLE`, `SOUND`, `BOSS_BAR` y `NONE`.
- Trackea bossbars creadas por el módulo para ocultarlas en `feedback.close()`.
- Permite reemplazar el set de traducciones con `feedback.replaceTranslations(...)` después de un reload.

## Qué no resuelve

- No carga archivos YAML desde disco.
- No ejecuta `YamlDocument.reload()` ni administra defaults/update de BoostedYAML.
- No guarda ni decide preferencias de idioma.
- No funciona sin `PlayerSettingsService` registrado.
- No crea un scheduler persistente para actionbars, colas o waiting rooms.
- No valida todos los errores de sintaxis MiniMessage antes de enviar.
- No garantiza thread-safety para llamadas a APIs Paper fuera del hilo principal.
- No centra texto realmente para `CENTERED_CHAT`; en el código actual se envía igual que `CHAT`.

## Dependencias del módulo

`craftkit-feedback/build.gradle.kts` declara:

```kotlin
repositories {
    mavenLocal()
}

dependencies {
    compileOnly(libs.paper.api)
    compileOnlyApi(libs.boosted.yaml)
    compileOnly(libs.networkplayersettings)
}
```

Versiones actuales en `gradle/libs.versions.toml`:

| Librería | Coordenada | Versión |
| --- | --- | --- |
| Paper API | `io.papermc.paper:paper-api` | `26.1.2.build.69-stable` |
| BoostedYAML | `dev.dejvokep:boosted-yaml` | `1.3.7` |
| NetworkPlayerSettings | `com.stephanofer:networkplayersettings` | `2.0.0` |

El repositorio configura `group=com.hera.craftkit` y `version=1.1.0`, y todos los subproyectos aplican `maven-publish`. En un entorno publicado localmente, la coordenada esperada del módulo es `com.hera.craftkit:craftkit-feedback:1.1.0`.

## Documentos de esta sección

1. [Instalación e integración](./instalacion-integracion.md)
2. [Lifecycle y NetworkPlayerSettings](./lifecycle-networkplayersettings.md)
3. [Traducciones y formatos YAML](./traducciones-yaml.md)
4. [Envío, canales y resolución](./envio-canales-resolucion.md)
5. [Placeholders](./placeholders.md)
6. [Reload y cierre](./reload-cierre.md)
7. [Ejemplos prácticos](./ejemplos-practicos.md)
8. [Referencia de API pública](./referencia-api.md)
9. [Errores, límites y buenas prácticas](./errores-limites-buenas-practicas.md)

## Ejemplo mínimo

```java
public final class MyPlugin extends JavaPlugin {

    private YamlDocument english;
    private YamlDocument spanish;
    private Feedback feedback;

    @Override
    public void onEnable() {
        this.english = loadLanguage("lang/en.yml");
        this.spanish = loadLanguage("lang/es.yml");

        this.feedback = Feedbacks.paper(this)
            .networkPlayerSettingsRequired()
            .defaultLanguage("en")
            .translations(FeedbackTranslations.boostedYaml()
                .language("en", english)
                .language("es", spanish)
                .build())
            .build();
    }

    @Override
    public void onDisable() {
        if (feedback != null) {
            feedback.close();
        }
    }
}
```

`loadLanguage(...)` representa carga propia del plugin consumidor. El módulo recibe `YamlDocument` ya creados.
