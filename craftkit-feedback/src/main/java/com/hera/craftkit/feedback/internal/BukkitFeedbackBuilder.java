package com.hera.craftkit.feedback.internal;

import com.hera.craftkit.feedback.Feedback;
import com.hera.craftkit.feedback.FeedbackMissingDependencyException;
import com.hera.craftkit.feedback.FeedbackTranslations;
import com.stephanofer.networkplayersettings.settings.api.PlayerSettingsService;
import java.util.Objects;
import org.bukkit.plugin.Plugin;

public final class BukkitFeedbackBuilder {

    private final Plugin plugin;
    private boolean networkPlayerSettingsRequired;
    private String defaultLanguage = "en";
    private FeedbackTranslations translations;

    public BukkitFeedbackBuilder(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    public BukkitFeedbackBuilder networkPlayerSettingsRequired() {
        this.networkPlayerSettingsRequired = true;
        return this;
    }

    public BukkitFeedbackBuilder defaultLanguage(String defaultLanguage) {
        String normalized = FeedbackTranslations.normalizeLanguage(
            defaultLanguage
        );
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(
                "defaultLanguage cannot be blank"
            );
        }
        this.defaultLanguage = normalized;
        return this;
    }

    public BukkitFeedbackBuilder translations(
        FeedbackTranslations translations
    ) {
        this.translations = Objects.requireNonNull(
            translations,
            "translations"
        );
        return this;
    }

    public Feedback build() {
        if (!networkPlayerSettingsRequired) {
            throw new IllegalStateException(
                "NetworkPlayerSettings is required by craftkit-feedback. Call networkPlayerSettingsRequired()."
            );
        }
        if (translations == null) {
            throw new IllegalStateException("translations are required");
        }
        if (!translations.supports(defaultLanguage)) {
            throw new IllegalStateException(
                "Default language '" +
                    defaultLanguage +
                    "' has no translation document."
            );
        }

        PlayerSettingsService settings = plugin
            .getServer()
            .getServicesManager()
            .load(PlayerSettingsService.class);
        if (settings == null) {
            throw new FeedbackMissingDependencyException(
                "NetworkPlayerSettings PlayerSettingsService is not registered. Ensure the consumer plugin depends on NetworkPlayerSettings and builds Feedback after it is enabled."
            );
        }

        PlayerLanguageResolver resolver = player -> {
            if (!settings.isReady(player.getUniqueId())) {
                return defaultLanguage;
            }
            return settings.resolvedLanguage(player).code();
        };
        return new DefaultFeedback(
            plugin,
            defaultLanguage,
            translations,
            resolver
        );
    }
}
