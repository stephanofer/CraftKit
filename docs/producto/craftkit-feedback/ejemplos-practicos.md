# Ejemplos prácticos

Los ejemplos muestran patrones de integración para plugins consumidores. La carga exacta de `YamlDocument` depende de la estrategia de recursos del plugin.

## Setup completo en `onEnable()` / `onDisable()`

```java
public final class ArenaPlugin extends JavaPlugin {

    private YamlDocument english;
    private YamlDocument spanish;
    private Feedback feedback;

    @Override
    public void onEnable() {
        this.english = loadLanguageDocument("lang/en.yml");
        this.spanish = loadLanguageDocument("lang/es.yml");

        FeedbackTranslations translations = FeedbackTranslations.boostedYaml()
            .language("en", english)
            .language("es", spanish)
            .build();

        this.feedback = Feedbacks.paper(this)
            .networkPlayerSettingsRequired()
            .defaultLanguage("en")
            .translations(translations)
            .build();
    }

    @Override
    public void onDisable() {
        if (feedback != null) {
            feedback.close();
        }
    }

    public Feedback feedback() {
        return feedback;
    }
}
```

## Feedback de comando con `send(...)`

YAML:

```yaml
party:
  invite-sent:
    outputs:
      - type: chat
        message: '<green>Invitación enviada a <target>.</green>'
      - type: sound
        sound: 'minecraft:block.note_block.pling'
        source: player
        volume: 1.0
        pitch: 1.0
```

Java:

```java
feedback.send(sender, "party.invite-sent",
    FeedbackPlaceholders.text("target", target.getName()));
```

## Actionbar persistente de cola

YAML:

```yaml
queue:
  status:
    message: '<yellow>Cola: <players>/<required> jugadores</yellow>'
```

Java:

```java
BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
    for (Player player : queue.players()) {
        feedback.actionBar(player, "queue.status",
            FeedbackPlaceholders.number("players", queue.size()),
            FeedbackPlaceholders.number("required", queue.requiredPlayers()));
    }
}, 0L, 20L);
```

El scheduler pertenece al consumidor. `craftkit-feedback` solo envía la actionbar cuando se llama el método.

## Lore de ítem con `components(...)`

YAML:

```yaml
items:
  queue-ticket:
    name: '<gold>Ticket de cola</gold>'
    lore:
      - '<gray>Modo: <mode></gray>'
      - '<gray>Jugadores: <players>/<required></gray>'
```

Java:

```java
ItemMeta meta = item.getItemMeta();
meta.displayName(feedback.component(player, "items.queue-ticket.name"));
meta.lore(feedback.components(player, "items.queue-ticket.lore",
    FeedbackPlaceholders.text("mode", mode.displayName()),
    FeedbackPlaceholders.number("players", queue.size()),
    FeedbackPlaceholders.number("required", queue.requiredPlayers())));
item.setItemMeta(meta);
```

## Flujo de comando `/reload`

```java
public void reloadPlugin() {
    reloadConfig();

    english.reload();
    spanish.reload();

    FeedbackTranslations translations = FeedbackTranslations.boostedYaml()
        .language("en", english)
        .language("es", spanish)
        .build();

    feedback.replaceTranslations(translations);
}
```

Si durante el reload se elimina el documento del idioma default, `replaceTranslations(...)` falla y el consumidor debe conservar la instancia anterior o deshabilitar la feature de forma controlada.
