package com.hera.craftkit.database;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class MigrationConfig {

    public static final String DEFAULT_LOCATION = "classpath:db/migration";

    private final boolean enabled;
    private final List<String> locations;
    private final boolean baselineOnMigrate;
    private final boolean validateOnMigrate;
    private final boolean cleanDisabled;
    private final ExistingSchemaStrategy existingSchemaStrategy;
    private final String baselineVersion;
    private final String baselineDescription;
    private final Map<String, String> placeholders;

    private MigrationConfig(
        final boolean enabled,
        final List<String> locations,
        final boolean baselineOnMigrate,
        final boolean validateOnMigrate,
        final boolean cleanDisabled,
        final ExistingSchemaStrategy existingSchemaStrategy,
        final String baselineVersion,
        final String baselineDescription,
        final Map<String, String> placeholders
    ) {
        this.enabled = enabled;
        this.locations = locations;
        this.baselineOnMigrate = baselineOnMigrate;
        this.validateOnMigrate = validateOnMigrate;
        this.cleanDisabled = cleanDisabled;
        this.existingSchemaStrategy = existingSchemaStrategy;
        this.baselineVersion = baselineVersion;
        this.baselineDescription = baselineDescription;
        this.placeholders = placeholders;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static MigrationConfig sharedDatabaseDefaults() {
        return builder()
            .existingSchemaStrategy(ExistingSchemaStrategy.BASELINE_AT_ZERO)
            .build();
    }

    public boolean enabled() {
        return this.enabled;
    }

    public List<String> locations() {
        return this.locations;
    }

    public boolean baselineOnMigrate() {
        return this.baselineOnMigrate;
    }

    public boolean validateOnMigrate() {
        return this.validateOnMigrate;
    }

    public boolean cleanDisabled() {
        return this.cleanDisabled;
    }

    public ExistingSchemaStrategy existingSchemaStrategy() {
        return this.existingSchemaStrategy;
    }

    public String baselineVersion() {
        return this.baselineVersion;
    }

    public String baselineDescription() {
        return this.baselineDescription;
    }

    public Map<String, String> placeholders() {
        return this.placeholders;
    }

    @Override
    public String toString() {
        return "MigrationConfig[enabled=" + this.enabled
            + ", locations=" + this.locations
            + ", baselineOnMigrate=" + this.baselineOnMigrate
            + ", validateOnMigrate=" + this.validateOnMigrate
            + ", cleanDisabled=" + this.cleanDisabled
            + ", existingSchemaStrategy=" + this.existingSchemaStrategy
            + ", baselineVersion=" + this.baselineVersion
            + ", baselineDescription=" + this.baselineDescription
            + ", placeholders=" + this.placeholders
            + ']';
    }

    public static final class Builder {

        private boolean enabled = true;
        private final List<String> locations = new ArrayList<>(List.of(DEFAULT_LOCATION));
        private boolean baselineOnMigrate;
        private boolean validateOnMigrate = true;
        private boolean cleanDisabled = true;
        private ExistingSchemaStrategy existingSchemaStrategy = ExistingSchemaStrategy.FAIL;
        private String baselineVersion = "0";
        private String baselineDescription = "CraftKit baseline";
        private final Map<String, String> placeholders = new LinkedHashMap<>();

        public Builder enabled(final boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder clearLocations() {
            this.locations.clear();
            return this;
        }

        public Builder locations(final List<String> locations) {
            this.locations.clear();
            this.locations.addAll(Objects.requireNonNull(locations, "Migration locations must not be null."));
            return this;
        }

        public Builder addLocation(final String location) {
            this.locations.add(location);
            return this;
        }

        public Builder baselineOnMigrate(final boolean baselineOnMigrate) {
            this.baselineOnMigrate = baselineOnMigrate;
            return this;
        }

        public Builder validateOnMigrate(final boolean validateOnMigrate) {
            this.validateOnMigrate = validateOnMigrate;
            return this;
        }

        public Builder cleanDisabled(final boolean cleanDisabled) {
            this.cleanDisabled = cleanDisabled;
            return this;
        }

        public Builder existingSchemaStrategy(final ExistingSchemaStrategy existingSchemaStrategy) {
            this.existingSchemaStrategy = existingSchemaStrategy;
            return this;
        }

        public Builder baselineVersion(final String baselineVersion) {
            this.baselineVersion = baselineVersion;
            return this;
        }

        public Builder baselineDescription(final String baselineDescription) {
            this.baselineDescription = baselineDescription;
            return this;
        }

        public Builder placeholders(final Map<String, String> placeholders) {
            this.placeholders.clear();
            this.placeholders.putAll(Objects.requireNonNull(placeholders, "Migration placeholders must not be null."));
            return this;
        }

        public Builder putPlaceholder(final String key, final String value) {
            this.placeholders.put(key, value);
            return this;
        }

        public MigrationConfig build() {
            final List<String> validatedLocations = new ArrayList<>(this.locations.size());
            for (final String location : this.locations) {
                final String sanitized = requireNonBlank(location, "Migration location must not be blank.");
                validatedLocations.add(sanitized);
            }
            if (this.enabled && validatedLocations.isEmpty()) {
                throw new DatabaseException("Migration locations must not be empty when migrations are enabled.");
            }

            final Map<String, String> validatedPlaceholders = new LinkedHashMap<>();
            for (final Map.Entry<String, String> entry : this.placeholders.entrySet()) {
                final String key = requireNonBlank(entry.getKey(), "Migration placeholder key must not be blank.");
                final String value = requireNonNullValue(entry.getValue(), "Migration placeholder value must not be null.");
                validatedPlaceholders.put(key, value);
            }

            final ExistingSchemaStrategy resolvedStrategy = Objects.requireNonNull(this.existingSchemaStrategy, "Existing schema strategy must not be null.");
            final String resolvedBaselineVersion = requireNonBlank(this.baselineVersion, "Migration baseline version must not be blank.");
            final String resolvedBaselineDescription = requireNonBlank(this.baselineDescription, "Migration baseline description must not be blank.");
            validateBaselineVersion(resolvedBaselineVersion);

            return new MigrationConfig(
                this.enabled,
                List.copyOf(validatedLocations),
                this.baselineOnMigrate,
                this.validateOnMigrate,
                this.cleanDisabled,
                resolvedStrategy,
                resolvedBaselineVersion,
                resolvedBaselineDescription,
                Map.copyOf(validatedPlaceholders)
            );
        }

        private static void validateBaselineVersion(final String baselineVersion) {
            for (int index = 0; index < baselineVersion.length(); index++) {
                final char character = baselineVersion.charAt(index);
                if (!Character.isDigit(character) && character != '.') {
                    throw new DatabaseException("Migration baseline version may contain only digits and dots.");
                }
            }
        }

        private static String requireNonBlank(final String value, final String message) {
            if (value == null) {
                throw new DatabaseException(message);
            }
            final String sanitized = value.trim();
            if (sanitized.isEmpty()) {
                throw new DatabaseException(message);
            }
            return sanitized;
        }

        private static String requireNonNullValue(final String value, final String message) {
            if (value == null) {
                throw new DatabaseException(message);
            }
            return value;
        }
    }
}
