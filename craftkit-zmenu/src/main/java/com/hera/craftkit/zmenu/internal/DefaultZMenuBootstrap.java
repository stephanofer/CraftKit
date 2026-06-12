package com.hera.craftkit.zmenu.internal;

import com.hera.craftkit.zmenu.ZMenuBootstrap;
import com.hera.craftkit.zmenu.ZMenuReloadPlan;
import fr.maxlego08.menu.api.InventoryOption;
import fr.maxlego08.menu.api.button.ButtonOption;
import fr.maxlego08.menu.api.loader.ButtonLoader;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public final class DefaultZMenuBootstrap implements ZMenuBootstrap {

    private final DefaultZMenuIntegration integration;
    private final ZMenuRegistrationTracker tracker;
    private final List<ButtonLoader> buttonLoaders = new ArrayList<>();
    private final List<Class<? extends ButtonOption>> buttonOptions = new ArrayList<>();
    private final List<Class<? extends InventoryOption>> inventoryOptions = new ArrayList<>();
    private final List<String> defaultInventories = new ArrayList<>();
    private final List<String> defaultPatterns = new ArrayList<>();
    private final List<String> defaultActionPatterns = new ArrayList<>();
    private final List<String> defaultDialogs = new ArrayList<>();
    private final List<String> defaultBedrock = new ArrayList<>();
    private String inventoriesFolder;
    private String patternsFolder;
    private String actionPatternsFolder;
    private String dialogsFolder;
    private String bedrockFolder;

    DefaultZMenuBootstrap(DefaultZMenuIntegration integration, ZMenuRegistrationTracker tracker) {
        this.integration = integration;
        this.tracker = tracker;
    }

    @Override
    public ZMenuBootstrap buttons(Consumer<ButtonRegistry> consumer) {
        Objects.requireNonNull(consumer, "consumer").accept(loader -> this.buttonLoaders.add(Objects.requireNonNull(loader, "loader")));
        return this;
    }

    @Override
    public ZMenuBootstrap buttonOptions(Class<? extends ButtonOption>... options) {
        this.buttonOptions.addAll(Arrays.asList(options));
        return this;
    }

    @Override
    public ZMenuBootstrap inventoryOptions(Class<? extends InventoryOption>... options) {
        this.inventoryOptions.addAll(Arrays.asList(options));
        return this;
    }

    @Override
    public ZMenuBootstrap defaultInventories(String... paths) {
        this.defaultInventories.addAll(Arrays.asList(paths));
        return this;
    }

    @Override
    public ZMenuBootstrap defaultPatterns(String... paths) {
        this.defaultPatterns.addAll(Arrays.asList(paths));
        return this;
    }

    @Override
    public ZMenuBootstrap defaultActionPatterns(String... paths) {
        this.defaultActionPatterns.addAll(Arrays.asList(paths));
        return this;
    }

    @Override
    public ZMenuBootstrap defaultDialogs(String... paths) {
        this.defaultDialogs.addAll(Arrays.asList(paths));
        return this;
    }

    @Override
    public ZMenuBootstrap defaultBedrock(String... paths) {
        this.defaultBedrock.addAll(Arrays.asList(paths));
        return this;
    }

    @Override
    public ZMenuBootstrap inventories(String folder) {
        this.inventoriesFolder = folder;
        return this;
    }

    @Override
    public ZMenuBootstrap patterns(String folder) {
        this.patternsFolder = folder;
        return this;
    }

    @Override
    public ZMenuBootstrap actionPatterns(String folder) {
        this.actionPatternsFolder = folder;
        return this;
    }

    @Override
    public ZMenuBootstrap dialogs(String folder) {
        this.dialogsFolder = folder;
        return this;
    }

    @Override
    public ZMenuBootstrap bedrock(String folder) {
        this.bedrockFolder = folder;
        return this;
    }

    @Override
    public ZMenuReloadPlan load() {
        DefaultZMenuReloadPlan plan = new DefaultZMenuReloadPlan(this.integration, this.snapshot());
        plan.loadFresh();
        this.integration.setReloadPlan(plan);
        return plan;
    }

    private BootstrapPlan snapshot() {
        return new BootstrapPlan(
            List.copyOf(this.buttonLoaders),
            List.copyOf(this.buttonOptions),
            List.copyOf(this.inventoryOptions),
            List.copyOf(this.defaultInventories),
            List.copyOf(this.defaultPatterns),
            List.copyOf(this.defaultActionPatterns),
            List.copyOf(this.defaultDialogs),
            List.copyOf(this.defaultBedrock),
            this.inventoriesFolder,
            this.patternsFolder,
            this.actionPatternsFolder,
            this.dialogsFolder,
            this.bedrockFolder
        );
    }

    record BootstrapPlan(
        List<ButtonLoader> buttonLoaders,
        List<Class<? extends ButtonOption>> buttonOptions,
        List<Class<? extends InventoryOption>> inventoryOptions,
        List<String> defaultInventories,
        List<String> defaultPatterns,
        List<String> defaultActionPatterns,
        List<String> defaultDialogs,
        List<String> defaultBedrock,
        String inventoriesFolder,
        String patternsFolder,
        String actionPatternsFolder,
        String dialogsFolder,
        String bedrockFolder
    ) {
    }
}
