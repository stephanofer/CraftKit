package com.hera.craftkit.feedback.internal;

import com.hera.craftkit.feedback.FeedbackPlaceholders;
import com.hera.craftkit.feedback.FeedbackTranslations;
import dev.dejvokep.boostedyaml.YamlDocument;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class DefaultFeedbackTest {

    @Test
    void componentFallsBackToDefaultLanguageWhenPreferredKeyIsMissing() throws Exception {
        FeedbackTranslations translations = FeedbackTranslations.boostedYaml()
            .language("en", yaml("greeting: '<yellow>Hello <player></yellow>'"))
            .language("es", yaml("other: '<yellow>Otro</yellow>'"))
            .build();
        DefaultFeedback feedback = feedback(translations, "es");

        Component component = feedback.component(null, "greeting", FeedbackPlaceholders.text("player", "Alex"));

        assertEquals("Hello Alex", plain(component));
    }

    @Test
    void componentsResolveStringLists() throws Exception {
        FeedbackTranslations translations = FeedbackTranslations.boostedYaml()
            .language("en", yaml("items:\n  sword:\n    lore:\n      - '<gray>Owner: <player></gray>'\n      - '<yellow>Right click to use.</yellow>'"))
            .build();
        DefaultFeedback feedback = feedback(translations, "en");

        List<Component> components = feedback.components(null, "items.sword.lore", FeedbackPlaceholders.text("player", "Alex"));

        assertEquals(List.of("Owner: Alex", "Right click to use."), components.stream().map(DefaultFeedbackTest::plain).toList());
    }

    @Test
    void missingTranslationReturnsVisibleFallback() throws Exception {
        FeedbackTranslations translations = FeedbackTranslations.boostedYaml()
            .language("en", yaml("known: '<green>Known</green>'"))
            .build();
        DefaultFeedback feedback = feedback(translations, "en");

        Component component = feedback.component(null, "missing.key");

        assertEquals("[missing translation: missing.key]", plain(component));
    }

    @Test
    void replaceTranslationsUsesNewSnapshot() throws Exception {
        FeedbackTranslations initial = FeedbackTranslations.boostedYaml()
            .language("en", yaml("status: '<red>Old</red>'"))
            .build();
        FeedbackTranslations replacement = FeedbackTranslations.boostedYaml()
            .language("en", yaml("status: '<green>New</green>'"))
            .build();
        DefaultFeedback feedback = feedback(initial, "en");

        feedback.replaceTranslations(replacement);

        assertEquals("New", plain(feedback.component(null, "status")));
    }

    @Test
    void closeHidesBossBarWithoutDuration() throws Exception {
        assertCloseHidesBossBarWithNonPositiveDuration("");
    }

    @Test
    void closeHidesBossBarWithZeroDuration() throws Exception {
        assertCloseHidesBossBarWithNonPositiveDuration("duration: 0");
    }

    @Test
    void closeHidesBossBarWithInvalidDuration() throws Exception {
        assertCloseHidesBossBarWithNonPositiveDuration("duration: invalid");
    }

    private static void assertCloseHidesBossBarWithNonPositiveDuration(String durationLine) throws Exception {
        FeedbackTranslations translations = FeedbackTranslations.boostedYaml()
            .language("en", yaml("""
                boss:
                  outputs:
                    - type: boss_bar
                      message: '<green>Boss</green>'
                      %s
                """.formatted(durationLine)))
            .build();
        DefaultFeedback feedback = feedback(translations, "en");
        BossBarPlayer player = bossBarPlayer();

        feedback.send(player.player(), "boss");
        feedback.close();

        assertEquals(1, player.shown().size());
        assertEquals(1, player.hidden().size());
        assertSame(player.shown().getFirst(), player.hidden().getFirst());
    }

    private static DefaultFeedback feedback(FeedbackTranslations translations, String language) {
        return new DefaultFeedback(plugin(), "en", translations, ignored -> language);
    }

    private static YamlDocument yaml(String yaml) throws Exception {
        return YamlDocument.create(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));
    }

    private static Plugin plugin() {
        return (Plugin) Proxy.newProxyInstance(
            DefaultFeedbackTest.class.getClassLoader(),
            new Class<?>[]{Plugin.class},
            (proxy, method, args) -> method.getName().equals("getLogger") ? Logger.getLogger("test") : null
        );
    }

    private static BossBarPlayer bossBarPlayer() {
        List<BossBar> shown = new ArrayList<>();
        List<BossBar> hidden = new ArrayList<>();
        InvocationHandler handler = (proxy, method, args) -> {
            if (method.getName().equals("showBossBar")) {
                shown.add((BossBar) args[0]);
                return null;
            }
            if (method.getName().equals("hideBossBar")) {
                hidden.add((BossBar) args[0]);
                return null;
            }
            return defaultValue(method.getReturnType());
        };
        Player player = (Player) Proxy.newProxyInstance(
            DefaultFeedbackTest.class.getClassLoader(),
            new Class<?>[]{Player.class},
            handler
        );
        return new BossBarPlayer(player, shown, hidden);
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0.0f;
        }
        if (type == double.class) {
            return 0.0d;
        }
        return null;
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    private record BossBarPlayer(Player player, List<BossBar> shown, List<BossBar> hidden) {
    }
}
