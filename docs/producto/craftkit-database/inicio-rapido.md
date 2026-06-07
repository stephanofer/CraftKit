# Inicio rápido — `craftkit-database`

Este documento muestra el flujo mínimo recomendado para un plugin consumidor.

## Crear la configuración

Para una base compartida por varios plugins de HERA, usar `MigrationConfig.sharedDatabaseDefaults()`.

```java
DatabaseConfig config = DatabaseConfig.builder()
    .host("127.0.0.1")
    .port(3306)
    .database("hera_network")
    .username("survival")
    .password("secret")
    .tablePrefix("survival_")
    .migration(MigrationConfig.sharedDatabaseDefaults())
    .build();
```

## Crear `Database`

```java
Database database = Databases.mysql(config);
```

Con esta variante CraftKit crea y posee:

- `HikariDataSource`;
- executor dedicado de base de datos;
- integración Flyway.

Cuando el plugin llame `database.close()`, CraftKit cerrará el datasource y el executor interno.

## Ejecutar migraciones en startup

```java
database.migrate().join();
```

`migrate()` devuelve `CompletableFuture<Void>` porque se ejecuta en el executor DB. `join()` espera a que termine. En `onEnable` suele ser correcto esperar, porque las tablas deben existir antes de activar repositorios o features.

No usar `join()` durante gameplay, eventos o comandos frecuentes.

## Crear un repositorio consumidor

```java
public final class PlayerRepository {

    private final Database database;

    public PlayerRepository(Database database) {
        this.database = database;
    }

    public CompletableFuture<Optional<PlayerData>> find(UUID uuid) {
        return this.database.query(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                "SELECT coins FROM " + this.database.table("players") + " WHERE uuid = ?"
            )) {
                statement.setString(1, uuid.toString());

                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next()) {
                        return Optional.empty();
                    }

                    return Optional.of(new PlayerData(uuid, result.getInt("coins")));
                }
            }
        });
    }
}
```

## Cerrar en shutdown

```java
@Override
public void onDisable() {
    if (this.database != null) {
        this.database.close();
    }
}
```

`close()` es idempotente: llamarlo más de una vez no repite el cierre.

## Si se toca Paper API después de una query

`craftkit-database` no vuelve al main thread. El consumidor debe hacerlo:

```java
repository.find(player.getUniqueId()).thenAccept(data -> {
    getServer().getScheduler().runTask(this, task -> {
        player.sendMessage("Coins: " + data.map(PlayerData::coins).orElse(0));
    });
});
```
