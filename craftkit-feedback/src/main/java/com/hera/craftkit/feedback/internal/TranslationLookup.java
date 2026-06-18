package com.hera.craftkit.feedback.internal;

import com.hera.craftkit.feedback.FeedbackTranslations;
import dev.dejvokep.boostedyaml.YamlDocument;

import java.util.Optional;

final class TranslationLookup {

    private TranslationLookup() {
    }

    static Optional<TranslationValue> find(FeedbackTranslations translations, String preferredLanguage, String defaultLanguage, String key) {
        String normalizedPreferred = FeedbackTranslations.normalizeLanguage(preferredLanguage);
        Optional<TranslationValue> preferred = valueIn(translations, normalizedPreferred, key);
        if (preferred.isPresent()) {
            return preferred;
        }
        if (!normalizedPreferred.equals(defaultLanguage)) {
            return valueIn(translations, defaultLanguage, key);
        }
        return Optional.empty();
    }

    private static Optional<TranslationValue> valueIn(FeedbackTranslations translations, String language, String key) {
        Optional<YamlDocument> document = translations.document(language);
        if (document.isEmpty()) {
            return Optional.empty();
        }
        Object value = document.get().get(key, null);
        if (value == null) {
            return Optional.empty();
        }
        return Optional.of(new TranslationValue(language, value));
    }
}
