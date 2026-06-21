# Instalación e integración

Este documento describe cómo declarar `craftkit-feedback` desde un plugin consumidor y qué dependencias deben existir en compilación y runtime.

## Dependencia Gradle del plugin consumidor

Si `CraftKit` fue publicado con `publishToMavenLocal`, el consumidor puede usar `mavenLocal()` y la coordenada del módulo:

```kotlin
repositories {
    mavenLocal()
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("com.hera.craftkit:craftkit-feedback:1.1.0")
    compileOnly("io.papermc.paper:paper-api:26.1.2.build.69-stable")
    compileOnly("dev.dejvokep:boosted-yaml:1.3.7")
    compileOnly("com.stephanofer:networkplayersettings:2.0.0")
}
```

Si el consumidor forma parte del mismo build multi-módulo, puede depender del proyecto:

```kotlin
dependencies {
    compileOnly(project(":craftkit-feedback"))
}
```

`craftkit-feedback` declara BoostedYAML como `compileOnlyApi` porque la API pública expone `YamlDocument` en `FeedbackTranslations`. El plugin consumidor compila contra `YamlDocument`, pero sigue siendo responsable de tener BoostedYAML disponible según su estrategia de empaquetado/runtime.

## NetworkPlayerSettings

`craftkit-feedback` compila contra:

```kotlin
compileOnly("com.stephanofer:networkplayersettings:2.0.0")
```

En este repositorio, `craftkit-feedback` agrega `mavenLocal()` porque esa coordenada se espera disponible localmente. El servidor debe tener el plugin `NetworkPlayerSettings` habilitado y debe registrar `PlayerSettingsService` en Bukkit `ServicesManager`.

En `plugin.yml` o `paper-plugin.yml`, el consumidor debería declarar dependencia fuerte si el feedback localizado es obligatorio:

```yaml
depend: [NetworkPlayerSettings]
```

Con dependencia fuerte, el consumidor puede construir `Feedback` en `onEnable()` después de cargar sus idiomas. Si se usa una dependencia blanda, el consumidor debe manejar la excepción de dependencia faltante y degradar funcionalidad.

## Imports principales

```java
import com.hera.craftkit.feedback.Feedback;
import com.hera.craftkit.feedback.FeedbackPlaceholders;
import com.hera.craftkit.feedback.FeedbackTranslations;
import com.hera.craftkit.feedback.Feedbacks;
import dev.dejvokep.boostedyaml.YamlDocument;
```

## Flujo de integración recomendado

1. El plugin consumidor carga sus propios archivos `YamlDocument` de idioma.
2. El plugin crea `FeedbackTranslations` con `FeedbackTranslations.boostedYaml()`.
3. El plugin crea `Feedback` con `Feedbacks.paper(plugin)`.
4. El builder llama obligatoriamente a `.networkPlayerSettingsRequired()`.
5. El builder define `.defaultLanguage("en")` o el idioma default real del plugin.
6. El builder recibe `.translations(...)`.
7. El plugin llama `.build()`.
8. En `onDisable()`, el plugin llama `feedback.close()`.

Ejemplo:

```java
FeedbackTranslations translations = FeedbackTranslations.boostedYaml()
    .language("en", englishDocument)
    .language("es", spanishDocument)
    .build();

Feedback feedback = Feedbacks.paper(this)
    .networkPlayerSettingsRequired()
    .defaultLanguage("en")
    .translations(translations)
    .build();
```

## Validaciones de construcción

`build()` falla si:

- no se llamó `.networkPlayerSettingsRequired()`;
- no se pasaron traducciones;
- el idioma default no existe en `FeedbackTranslations`;
- `PlayerSettingsService` no está registrado en Bukkit `ServicesManager`.

La falta de servicio produce `FeedbackMissingDependencyException` con un mensaje orientado a revisar que el plugin consumidor dependa de NetworkPlayerSettings y construya `Feedback` después de que ese plugin esté habilitado.
