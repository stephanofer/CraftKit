package com.hera.craftkit.feedback;

import dev.dejvokep.boostedyaml.YamlDocument;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class FeedbackTranslations {

    private final Map<String, YamlDocument> documents;

    private FeedbackTranslations(Map<String, YamlDocument> documents) {
        this.documents = Map.copyOf(documents);
    }

    public static BoostedYamlBuilder boostedYaml() {
        return new BoostedYamlBuilder();
    }

    public Optional<YamlDocument> document(String language) {
        return Optional.ofNullable(documents.get(normalizeLanguage(language)));
    }

    public boolean supports(String language) {
        return documents.containsKey(normalizeLanguage(language));
    }

    public Map<String, YamlDocument> documents() {
        return documents;
    }

    public static String normalizeLanguage(String language) {
        if (language == null) {
            return "";
        }
        return language.trim().toLowerCase(Locale.ROOT);
    }

    public static final class BoostedYamlBuilder {

        private final Map<String, YamlDocument> documents = new LinkedHashMap<>();

        private BoostedYamlBuilder() {
        }

        public BoostedYamlBuilder language(String language, YamlDocument document) {
            String normalized = normalizeLanguage(language);
            if (normalized.isBlank()) {
                throw new IllegalArgumentException("language cannot be blank");
            }
            documents.put(normalized, Objects.requireNonNull(document, "document"));
            return this;
        }

        public FeedbackTranslations build() {
            if (documents.isEmpty()) {
                throw new IllegalStateException("At least one language document is required.");
            }
            return new FeedbackTranslations(documents);
        }
    }
}
