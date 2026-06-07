# PRD — CraftKit

## 1. Resumen

**CraftKit** será una librería interna modular para acelerar el desarrollo de plugins y modalidades de **HERA Network**, evitando repetir infraestructura crítica entre proyectos.

CraftKit **no será un plugin instalado en `/plugins`**. Será consumido como dependencia Gradle por los plugins independientes y empaquetado dentro del JAR final del plugin consumidor mediante Shadow.

La filosofía principal es:

> CraftKit solo debe existir donde aporte valor real: rendimiento, consistencia, reducción de bugs, reducción de boilerplate crítico y estandarización interna de HERA.

No se crearán módulos por envolver librerías externas sin aportar valor.

---

## 2. Problema

Actualmente, cada plugin o modalidad tiende a reimplementar piezas comunes:

- conexión a base de datos;
- Redis/pub-sub;
- logging/debug;
- detección de entorno Paper/Minecraft;
- cierre manual de recursos;
- configuración repetitiva de infraestructura.

Esto provoca:

- duplicación de código;
- bugs repetidos en varios plugins;
- inconsistencias entre modalidades;
- arranque lento de nuevos proyectos;
- mantenimiento costoso;
- riesgo de rendimiento por configuraciones incorrectas;
- dificultad para depurar errores en producción.

---

## 3. Objetivos

CraftKit debe:

- reducir boilerplate crítico en plugins nuevos;
- estandarizar infraestructura común de HERA;
- mantener bajo acoplamiento;
- evitar sobreingeniería;
- ser ligero, modular y fácil de entender;
- permitir correcciones centralizadas;
- permitir evolución controlada mediante versionado unificado.

---

## 4. No objetivos

CraftKit no debe:

- convertirse en un framework gigante;
- controlar el ciclo de vida completo del plugin;
- envolver todas las librerías externas;
- obligar a los plugins consumidores a usar módulos innecesarios;
- ser publicado como plugin de servidor.

---

## 5. Decisiones cerradas

### 5.1 CraftKit será una toolkit library, no un runtime

No habrá contexto central tipo:

```java
CraftKit craftKit = CraftKit.paper(this);
craftKit.manage(database);
craftKit.shutdown();
```

Cada módulo expondrá su propia API/factory cuando tenga sentido.

Ejemplo:

Ojo esto es algo conceptual para que el equipo de trabajo aterrice este PRD entocns solo es referencial tu luego tienes que deciri el diseño correcto

```java
Database database = Databases.mysql(settings.database());
RedisClient redis = RedisClients.lettuce(settings.redis());
```

El plugin consumidor será dueño explícito de los recursos que crea y los cerrará manualmente.

---

### 5.2 No habrá lifecycle centralizado

No existirá:

- `ServiceRegistry`;
- `ModuleRegistry`;
- `LifecycleManager`;
- `ManagedResource`;
- `onShutdown`;
- auto-discovery;
- dependency injection interna.

---

### 5.3 Versionado unificado sin BOM

No habrá `craftkit-bom`.

Todos los módulos publicados compartirán la misma versión:

```text
craftkit-paper:1.0.0
craftkit-logging:1.0.0
craftkit-database:1.0.0
craftkit-redis:1.0.0
```

Si solo cambia `craftkit-database`, igualmente se publica un nuevo release completo:

```text
craftkit-paper:1.0.1
craftkit-logging:1.0.1
craftkit-database:1.0.1
craftkit-redis:1.0.1
```

Los plugins consumidores deberán usar una única variable:

```kotlin
val craftkitVersion = "1.0.0"

dependencies {
    implementation("com.hera.craftkit:craftkit-database:$craftkitVersion")
    implementation("com.hera.craftkit:craftkit-redis:$craftkitVersion")
}
```

Regla interna:

> No se deben mezclar versiones distintas de módulos CraftKit dentro de un mismo plugin consumidor.

---

## 6. Módulos iniciales

Al comienzo del proyecto de CraftKit tendrá estos módulos:

```text
craftkit-paper
craftkit-logging
craftkit-database
craftkit-redis
```

No se crearán inicialmente:

```text
craftkit-config
craftkit-command
craftkit-scoreboard
craftkit-menu
```

---

## 7. Módulo `craftkit-paper`

### Propósito

Utilidades específicas para Paper/Minecraft.

Este módulo sí puede depender de Paper API.

---

## 8. Módulo `craftkit-logging`

### Propósito

Logging profesional y consistente para todos los plugins de HERA.

---

## 9. Módulo `craftkit-database`

### Propósito

Estandarizar el acceso a MySQL en HERA.

### Base técnica

- MySQL;
- HikariCP;
- JDBC;

### Por qué sí merece módulo

Database es infraestructura crítica. Si cada plugin configura MySQL/Hikari a su manera, aparecen problemas de rendimiento, pools mal configurados, queries en main thread y recursos sin cerrar.

### Responsabilidades

- conexión MySQL estándar;
- pool HikariCP con defaults oficiales;
- queries async;
- timeouts comunes;
- errores claros;
- cierre explícito;
- futura base para transacciones;
- futura base para health checks;
- evitar acceso bloqueante desde main thread.

---

## 10. Módulo `craftkit-redis`

### Propósito

Estandarizar Redis para comunicación entre servidores de la network.

### Base técnica

- Lettuce;
- Redis pub/sub;
- serialización estándar;
- canales convencionales de HERA.

### Por qué sí merece módulo

Redis en HERA se usará para comunicación crítica entre servidores:

- parties;
- matchmaking;
- colas;
- eventos entre lobbies/modalidades;
- cache compartida;
- sincronización de estado ligero.

Si cada plugin usa Redis directo, se duplican convenciones de canales, serialización, reconexión y manejo de errores.

---

## 11. Librerías que se usarán directamente en plugins consumidores

Estas librerías no tendrán módulo CraftKit inicial porque, por ahora, CraftKit no aportaría suficiente valor encima.

### BoostedYAML

Uso directo para configuración.

Motivo:

- la librería ya resuelve el problema;
- no queremos replicar toda su API;
- un wrapper fino aportaría poco al inicio.

### Cloud

Uso directo para comandos.

Motivo:

- Cloud ya es el framework de comandos;
- no se debe crear “Cloud pero nuestro”;
- solo tendría sentido un módulo futuro si HERA define parsers, errores, permisos y mensajes comunes.

### scoreboard-library

Uso directo para scoreboards.

Motivo:

- todavía no hay convención visual fuerte de HERA;
- un módulo solo tendría sentido si se crean helpers oficiales de sidebar, temas, refresh y layouts.

### zMenu

Debe tratarse como plugin externo instalado en `/plugins`.

Motivo:

- zMenu es principalmente un plugin con API;
- no se debe shadeíar dentro de CraftKit;
- si se necesita integración, se creará un módulo tipo `craftkit-zmenu` o `craftkit-menu-zmenu`.

---

## 12. Criterio para crear nuevos módulos

Un módulo CraftKit solo se crea si cumple mínimo 2 condiciones:

- se usará en varios plugins;
- evita boilerplate repetido real;
- evita errores graves;
- estandariza una convención de HERA;
- oculta configuración delicada;
- requiere integración con Paper;
- requiere cierre/limpieza;
- centraliza algo que luego querremos cambiar en un solo lugar;
- protege contra mal uso que puede afectar rendimiento o estabilidad.

Si no cumple esto, el plugin consumidor usa la librería directamente.

---

## 13. Nombres y arquitectura pública

### Convenciones

```text
Module       -> dependencia Gradle/Maven.
EntryPoint   -> clase pública para crear/acceder a la feature.
API Object   -> objeto que usa el plugin consumidor.
Internal     -> implementación interna, no usar desde plugins consumidores.
```

### Ejemplos

```text
craftkit-database
  EntryPoint: Databases
  API Object: Database
  Internal: HikariDatabase

craftkit-redis
  EntryPoint: RedisClients
  API Object: RedisClient
  Internal: LettuceRedisClient

craftkit-logging
  EntryPoint: Loggers
  API Object: CraftLogger
  Internal: PaperCraftLogger
```

### Regla

No usar `Manager` o `Service` como nombres públicos por defecto.

Preferir nombres concretos:

```text
Database
RedisClient
CraftLogger
ServerInfo
PluginLookup
```

Las clases internas pueden usar `internal`, pero el plugin consumidor no debe depender de ellas.

---

## 14. Dependencias y Shadow

### Decisión

Los módulos CraftKit publicados serán JARs normales, no JARs ya shadeados.

El plugin consumidor será quien genere el JAR final con Shadow.

### Regla general

```text
Plugin consumidor
  -> depende de módulos CraftKit
  -> depende de librerías directas necesarias
  -> genera shadowJar final
```

### Ejemplo

```kotlin
val craftkitVersion = "1.0.0"

dependencies {
    implementation("com.hera.craftkit:craftkit-paper:$craftkitVersion")
    implementation("com.hera.craftkit:craftkit-logging:$craftkitVersion")
    implementation("com.hera.craftkit:craftkit-database:$craftkitVersion")
    implementation("com.hera.craftkit:craftkit-redis:$craftkitVersion")

    implementation("dev.dejvokep:boosted-yaml:<version>")
    implementation("org.incendo:cloud-paper:<version>")
}
```

### Regla Gradle interna

- `api(...)`: cuando una dependencia aparece en la API pública.
- `implementation(...)`: cuando es detalle interno del módulo.
- `compileOnly(...)`: cuando es API provista por Paper o por plugin externo.
- `runtimeOnly(...)`: cuando solo se necesita en runtime y no aparece en compile.

---

## 15. Stack técnico base

- Java 25;
- Gradle 9.5.1;
- Kotlin DSL para Gradle;
- JUnit para testing;
- Shadow en plugins consumidores;
- Paper como plataforma principal;
- MySQL como base de datos inicial;
- HikariCP para pool;
- Lettuce para Redis.

---

## 19. Decisión final

CraftKit será una plataforma interna modular, pequeña y pragmática.

No busca reemplazar librerías maduras. Busca resolver las partes que, si cada plugin hace distinto, dañan la estabilidad de HERA:

```text
database
redis
logging
utilidades Paper/Minecraft
```

Todo lo demás se usará directo hasta que exista una necesidad real y repetida de estandarizarlo.

La regla final:

> CraftKit solo envuelve lo que puede romper rendimiento, estabilidad, consistencia o capacidad de debug de la network.
