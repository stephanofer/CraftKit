package com.hera.craftkit.feedback.internal;

import com.hera.craftkit.feedback.Feedback;
import com.hera.craftkit.feedback.FeedbackTranslations;
import dev.dejvokep.boostedyaml.block.implementation.Section;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;

public final class DefaultFeedback implements Feedback {

    private final Plugin plugin;
    private final String defaultLanguage;
    private final PlayerLanguageResolver languageResolver;
    private final MiniMessage miniMessage;
    private final AtomicReference<FeedbackTranslations> translations;
    private final Set<String> loggedMissingKeys = ConcurrentHashMap.newKeySet();
    private final Map<Player, List<ActiveBossBar>> activeBossBars = new ConcurrentHashMap<>();

    DefaultFeedback(Plugin plugin, String defaultLanguage, FeedbackTranslations translations, PlayerLanguageResolver languageResolver) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.defaultLanguage = Objects.requireNonNull(defaultLanguage, "defaultLanguage");
        this.translations = new AtomicReference<>(Objects.requireNonNull(translations, "translations"));
        this.languageResolver = Objects.requireNonNull(languageResolver, "languageResolver");
        this.miniMessage = MiniMessage.miniMessage();
    }

    @Override
    public void send(Player player, String key, TagResolver... placeholders) {
        Objects.requireNonNull(player, "player");
        Optional<TranslationValue> value = lookup(player, key);
        if (value.isEmpty()) {
            player.sendMessage(missingComponent(key));
            return;
        }
        Section section = asSection(value.get().value());
        if (section == null) {
            player.sendMessage(parse(String.valueOf(value.get().value()), placeholders));
            return;
        }
        List<Map<?, ?>> outputs = section.getMapList("outputs", Collections.emptyList());
        if (outputs.isEmpty()) {
            return;
        }
        for (Map<?, ?> output : outputs) {
            sendOutput(player, output, placeholders);
        }
    }

    @Override
    public void chat(Player player, String key, TagResolver... placeholders) {
        for (Component component : components(player, key, placeholders)) {
            player.sendMessage(component);
        }
    }

    @Override
    public void actionBar(Player player, String key, TagResolver... placeholders) {
        player.sendActionBar(component(player, key, placeholders));
    }

    @Override
    public void title(Player player, String key, TagResolver... placeholders) {
        Optional<TranslationValue> value = lookup(player, key);
        if (value.isEmpty()) {
            player.showTitle(Title.title(missingComponent(key), Component.empty()));
            return;
        }
        player.showTitle(titleFrom(value.get().value(), placeholders));
    }

    @Override
    public void sound(Player player, String key, TagResolver... placeholders) {
        lookup(player, key).flatMap(value -> soundFrom(value.value())).ifPresent(player::playSound);
    }

    @Override
    public Component component(Player player, String key, TagResolver... placeholders) {
        Optional<TranslationValue> value = lookup(player, key);
        if (value.isEmpty()) {
            return missingComponent(key);
        }
        Object raw = value.get().value();
        if (raw instanceof String string) {
            return parse(string, placeholders);
        }
        Section section = asSection(raw);
        if (section != null) {
            String message = section.getString("message", section.getString("title", null));
            if (message != null) {
                return parse(message, placeholders);
            }
        }
        return parse(String.valueOf(raw), placeholders);
    }

    @Override
    public List<Component> components(Player player, String key, TagResolver... placeholders) {
        Optional<TranslationValue> value = lookup(player, key);
        if (value.isEmpty()) {
            return List.of(missingComponent(key));
        }
        Object raw = value.get().value();
        if (raw instanceof List<?> list) {
            return list.stream().map(String::valueOf).map(line -> parse(line, placeholders)).toList();
        }
        Section section = asSection(raw);
        if (section != null) {
            List<String> messages = section.getStringList("messages", Collections.emptyList());
            if (!messages.isEmpty()) {
                return messages.stream().map(line -> parse(line, placeholders)).toList();
            }
            String message = section.getString("message", null);
            if (message != null) {
                return List.of(parse(message, placeholders));
            }
        }
        return List.of(component(player, key, placeholders));
    }

    @Override
    public void replaceTranslations(FeedbackTranslations translations) {
        Objects.requireNonNull(translations, "translations");
        if (!translations.supports(defaultLanguage)) {
            throw new IllegalArgumentException("Default language '" + defaultLanguage + "' has no translation document.");
        }
        this.translations.set(translations);
    }

    @Override
    public void close() {
        activeBossBars.forEach((player, bars) -> bars.forEach(bar -> {
            if (bar.hideTask() != null) {
                bar.hideTask().cancel();
            }
            player.hideBossBar(bar.bossBar());
        }));
        activeBossBars.clear();
    }

    private Optional<TranslationValue> lookup(Player player, String key) {
        Objects.requireNonNull(key, "key");
        String language = languageResolver.language(player);
        Optional<TranslationValue> value = TranslationLookup.find(translations.get(), language, defaultLanguage, key);
        if (value.isEmpty()) {
            logMissing(language, key);
        }
        return value;
    }

    private void sendOutput(Player player, Map<?, ?> output, TagResolver... placeholders) {
        FeedbackOutputType type = FeedbackOutputType.from(output.get("type"));
        switch (type) {
            case NONE -> {
            }
            case CHAT, CENTERED_CHAT -> messagesFrom(output, placeholders).forEach(player::sendMessage);
            case ACTION_BAR -> player.sendActionBar(messageFrom(output, placeholders));
            case TITLE -> player.showTitle(titleFrom(output, placeholders));
            case SOUND -> soundFrom(output).ifPresent(player::playSound);
            case BOSS_BAR -> showBossBar(player, output, placeholders);
        }
    }

    private List<Component> messagesFrom(Map<?, ?> output, TagResolver... placeholders) {
        Object messages = output.get("messages");
        if (messages instanceof List<?> list) {
            return list.stream().map(String::valueOf).map(line -> parse(line, placeholders)).toList();
        }
        Object message = output.get("message");
        if (message == null) {
            return List.of(Component.empty());
        }
        return List.of(parse(String.valueOf(message), placeholders));
    }

    private Component messageFrom(Map<?, ?> output, TagResolver... placeholders) {
        Object message = output.get("message");
        return parse(message == null ? "" : String.valueOf(message), placeholders);
    }

    private Title titleFrom(Object raw, TagResolver... placeholders) {
        if (raw instanceof Map<?, ?> map) {
            return titleFrom(map, placeholders);
        }
        Section section = asSection(raw);
        if (section != null) {
            return titleFrom(section.getStringRouteMappedValues(false), placeholders);
        }
        return Title.title(parse(String.valueOf(raw), placeholders), Component.empty());
    }

    private Title titleFrom(Map<?, ?> output, TagResolver... placeholders) {
        Object rawTitle = output.get("title");
        Object rawSubtitle = output.get("subtitle");
        Component title = parse(rawTitle == null ? "" : String.valueOf(rawTitle), placeholders);
        Component subtitle = parse(rawSubtitle == null ? "" : String.valueOf(rawSubtitle), placeholders);
        return Title.title(title, subtitle, Title.Times.times(
            ticks(output.get("fade-in"), 10L),
            ticks(output.get("stay"), 70L),
            ticks(output.get("fade-out"), 20L)
        ));
    }

    private Optional<Sound> soundFrom(Object raw) {
        if (raw instanceof Map<?, ?> map) {
            return soundFrom(map);
        }
        Section section = asSection(raw);
        if (section != null) {
            return soundFrom(section.getStringRouteMappedValues(false));
        }
        if (raw instanceof String string && !string.isBlank()) {
            return Optional.of(Sound.sound(Key.key(string), Sound.Source.PLAYER, 1.0f, 1.0f));
        }
        return Optional.empty();
    }

    private Optional<Sound> soundFrom(Map<?, ?> output) {
        Object soundName = output.get("sound");
        if (soundName == null || soundName.toString().isBlank()) {
            return Optional.empty();
        }
        Sound.Source source = enumValue(Sound.Source.class, output.get("source"), Sound.Source.PLAYER);
        float volume = floatValue(output.get("volume"), 1.0f);
        float pitch = floatValue(output.get("pitch"), 1.0f);
        return Optional.of(Sound.sound(Key.key(soundName.toString()), source, volume, pitch));
    }

    private void showBossBar(Player player, Map<?, ?> output, TagResolver... placeholders) {
        BossBar bossBar = BossBar.bossBar(
            messageFrom(output, placeholders),
            clamp(floatValue(output.get("progress"), 1.0f), 0.0f, 1.0f),
            enumValue(BossBar.Color.class, output.get("color"), BossBar.Color.WHITE),
            enumValue(BossBar.Overlay.class, output.get("overlay"), BossBar.Overlay.PROGRESS)
        );
        player.showBossBar(bossBar);
        long duration = longValue(output.get("duration"), 0L);
        ActiveBossBar activeBossBar = new ActiveBossBar(bossBar, null);
        activeBossBars.computeIfAbsent(player, ignored -> Collections.synchronizedList(new ArrayList<>()))
            .add(activeBossBar);
        if (duration > 0) {
            BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                player.hideBossBar(bossBar);
                List<ActiveBossBar> bars = activeBossBars.get(player);
                if (bars != null) {
                    bars.removeIf(active -> active.bossBar().equals(bossBar));
                    if (bars.isEmpty()) {
                        activeBossBars.remove(player, bars);
                    }
                }
            }, duration);
            activeBossBar.hideTask(task);
        }
    }

    private Component parse(String input, TagResolver... placeholders) {
        return miniMessage.deserialize(input == null ? "" : input, TagResolver.resolver(placeholders));
    }

    private Component missingComponent(String key) {
        return Component.text("[missing translation: " + key + "]");
    }

    private void logMissing(String language, String key) {
        String marker = language + ':' + key;
        if (loggedMissingKeys.add(marker)) {
            plugin.getLogger().warning("Missing feedback translation for key '" + key + "' in language '" + language + "' and default language '" + defaultLanguage + "'.");
        }
    }

    private static Section asSection(Object value) {
        return value instanceof Section section ? section : null;
    }

    private static Duration ticks(Object value, long fallbackTicks) {
        return Duration.ofMillis(longValue(value, fallbackTicks) * 50L);
    }

    private static long longValue(Object value, long fallback) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value != null) {
            try {
                return Long.parseLong(value.toString());
            } catch (NumberFormatException exception) {
                return fallback;
            }
        }
        return fallback;
    }

    private static float floatValue(Object value, float fallback) {
        if (value instanceof Number number) {
            return number.floatValue();
        }
        if (value != null) {
            try {
                return Float.parseFloat(value.toString());
            } catch (NumberFormatException exception) {
                return fallback;
            }
        }
        return fallback;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, Object value, E fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, value.toString().trim().replace('-', '_').toUpperCase());
        } catch (IllegalArgumentException exception) {
            Bukkit.getLogger().log(Level.WARNING, "Invalid feedback enum value ''{0}'' for {1}; using {2}.", new Object[]{value, type.getSimpleName(), fallback});
            return fallback;
        }
    }

    private static final class ActiveBossBar {

        private final BossBar bossBar;
        private BukkitTask hideTask;

        private ActiveBossBar(BossBar bossBar, BukkitTask hideTask) {
            this.bossBar = bossBar;
            this.hideTask = hideTask;
        }

        private BossBar bossBar() {
            return bossBar;
        }

        private BukkitTask hideTask() {
            return hideTask;
        }

        private void hideTask(BukkitTask hideTask) {
            this.hideTask = hideTask;
        }
    }
}
