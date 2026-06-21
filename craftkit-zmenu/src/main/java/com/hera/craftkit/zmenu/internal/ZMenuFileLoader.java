package com.hera.craftkit.zmenu.internal;

import com.hera.craftkit.zmenu.ZMenuException;
import fr.maxlego08.menu.api.inventory.bedrock.BedrockInventory;
import fr.maxlego08.menu.api.inventory.dialog.DialogInventory;
import fr.maxlego08.menu.api.Inventory;
import fr.maxlego08.menu.api.pattern.ActionPattern;
import fr.maxlego08.menu.api.pattern.Pattern;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

public final class ZMenuFileLoader {

    private final DefaultZMenuIntegration integration;
    private final ZMenuRegistrationTracker tracker;
    private final DefaultZMenuBootstrap.BootstrapPlan plan;
    private final Plugin plugin;

    ZMenuFileLoader(DefaultZMenuIntegration integration, ZMenuRegistrationTracker tracker, DefaultZMenuBootstrap.BootstrapPlan plan) {
        this.integration = integration;
        this.tracker = tracker;
        this.plan = plan;
        this.plugin = integration.plugin();
    }

    public void load() {
        this.registerConfiguredExtensions();
        this.loadDefaultResources();
        this.loadDirectory(this.plan.actionPatternsFolder(), "action pattern", this::loadActionPattern);
        this.loadDirectory(this.plan.patternsFolder(), "pattern", this::loadPattern);
        this.loadDirectory(this.plan.inventoriesFolder(), "inventory", this::loadInventory);
        this.integration.dialogs().ifPresent(dialogs -> this.loadDirectory(this.plan.dialogsFolder(), "dialog", this::loadDialog));
        this.integration.bedrock().ifPresent(bedrock -> this.loadDirectory(this.plan.bedrockFolder(), "bedrock", this::loadBedrock));
    }

    private void registerConfiguredExtensions() {
        this.plan.buttonLoaders().forEach(loader -> {
            this.integration.buttons().register(loader);
            this.tracker.trackButtonLoader(loader);
        });
        this.plan.buttonOptions().forEach(option -> {
            this.integration.inventories().registerOption(this.plugin, option);
            this.tracker.trackButtonOption(option);
        });
        this.plan.inventoryOptions().forEach(option -> {
            this.integration.inventories().registerInventoryOption(this.plugin, option);
            this.tracker.trackInventoryOption(option);
        });
    }

    private void loadDefaultResources() {
        this.loadDefaults(this.plan.defaultActionPatterns(), this.plan.actionPatternsFolder(), "action pattern", false, this::loadActionPattern);
        this.loadDefaults(this.plan.defaultPatterns(), this.plan.patternsFolder(), "pattern", false, this::loadPattern);
        this.loadDefaults(this.plan.defaultInventories(), this.plan.inventoriesFolder(), "inventory", true, this::loadInventory);
        if (this.integration.dialogs().isPresent()) {
            this.loadDefaults(this.plan.defaultDialogs(), this.plan.dialogsFolder(), "dialog", false, this::loadDialog);
        }
        if (this.integration.bedrock().isPresent()) {
            this.loadDefaults(this.plan.defaultBedrock(), this.plan.bedrockFolder(), "bedrock", false, this::loadBedrock);
        }
    }

    private void loadDefaults(List<String> resourcePaths, String scannedFolder, String category, boolean inventoryDefault, Consumer<File> loader) {
        Set<String> distinctResourcePaths = new LinkedHashSet<>();
        for (String resourcePath : resourcePaths) {
            distinctResourcePaths.add(normalizeResourcePath(resourcePath));
        }
        distinctResourcePaths.forEach(path -> this.loadDefault(path, scannedFolder, category, inventoryDefault, loader));
    }

    private void loadDefault(String resourcePath, String scannedFolder, String category, boolean inventoryDefault, Consumer<File> loader) {
        String normalizedResourcePath = normalizeResourcePath(resourcePath);
        if (isCoveredByDirectory(normalizedResourcePath, scannedFolder)) {
            this.saveDefaultResource(normalizedResourcePath, category);
            return;
        }

        if (inventoryDefault) {
            try {
                Inventory inventory = this.integration.inventories().loadInventoryOrSaveResource(this.plugin, normalizedResourcePath);
                this.tracker.trackInventory(inventory);
                return;
            } catch (Exception exception) {
                throw new ZMenuException("Failed to load default zMenu inventory resource '" + normalizedResourcePath + "' for plugin '" + this.plugin.getName() + "'", exception);
            }
        }

        File file = this.saveDefaultResource(normalizedResourcePath, category);
        loader.accept(file);
    }

    private File saveDefaultResource(String resourcePath, String category) {
        File file = new File(this.plugin.getDataFolder(), resourcePath);
        if (file.exists()) {
            return file;
        }
        try {
            this.plugin.saveResource(resourcePath, false);
            return file;
        } catch (IllegalArgumentException exception) {
            throw new ZMenuException("Missing declared default zMenu " + category + " resource '" + resourcePath + "' for plugin '" + this.plugin.getName() + "'", exception);
        }
    }

    static boolean isCoveredByDirectory(String resourcePath, String folder) {
        if (folder == null || folder.isBlank()) {
            return false;
        }
        String normalizedResourcePath = normalizeResourcePath(resourcePath);
        String normalizedFolder = normalizeResourcePath(folder);
        while (normalizedFolder.endsWith("/")) {
            normalizedFolder = normalizedFolder.substring(0, normalizedFolder.length() - 1);
        }
        if (normalizedFolder.isEmpty()) {
            return false;
        }
        return normalizedResourcePath.startsWith(normalizedFolder + "/");
    }

    private static String normalizeResourcePath(String path) {
        String normalized = path.replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    private void loadDirectory(String folder, String category, Consumer<File> loader) {
        if (folder == null || folder.isBlank()) {
            return;
        }
        Path root = this.plugin.getDataFolder().toPath().resolve(folder);
        try {
            Files.createDirectories(root);
            try (var stream = Files.walk(root)) {
                List<File> files = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".yml"))
                    .sorted(Comparator.comparing(Path::toString))
                    .map(Path::toFile)
                    .toList();
                files.forEach(loader);
            }
        } catch (IOException exception) {
            throw new ZMenuException("Failed to scan zMenu " + category + " folder '" + root + "' for plugin '" + this.plugin.getName() + "'", exception);
        }
    }

    private void loadInventory(File file) {
        try {
            this.tracker.trackInventory(this.integration.inventories().loadInventory(this.plugin, file));
        } catch (Exception exception) {
            throw loadFailure("inventory", file, exception);
        }
    }

    private void loadPattern(File file) {
        try {
            Pattern pattern = this.integration.patterns().loadPattern(file);
            this.tracker.trackPattern(pattern);
        } catch (Exception exception) {
            throw loadFailure("pattern", file, exception);
        }
    }

    private void loadActionPattern(File file) {
        try {
            ActionPattern pattern = this.integration.patterns().loadActionPattern(file);
            this.tracker.trackActionPattern(pattern);
        } catch (Exception exception) {
            throw loadFailure("action pattern", file, exception);
        }
    }

    private void loadDialog(File file) {
        try {
            DialogInventory dialog = this.integration.dialogs().orElseThrow().loadInventory(this.plugin, file);
            this.tracker.trackDialog(dialog);
        } catch (Exception exception) {
            throw loadFailure("dialog", file, exception);
        }
    }

    private void loadBedrock(File file) {
        try {
            BedrockInventory inventory = this.integration.bedrock().orElseThrow().loadInventory(this.plugin, file);
            this.tracker.trackBedrock(inventory);
        } catch (Exception exception) {
            throw loadFailure("bedrock", file, exception);
        }
    }

    private ZMenuException loadFailure(String category, File file, Exception exception) {
        return new ZMenuException("Failed to load zMenu " + category + " file '" + file.getPath() + "' for plugin '" + this.plugin.getName() + "'", exception);
    }
}
