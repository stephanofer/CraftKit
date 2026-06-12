package com.hera.craftkit.zmenu.internal;

import com.hera.craftkit.zmenu.ZMenuReloadPlan;

public final class DefaultZMenuReloadPlan implements ZMenuReloadPlan {

    private final DefaultZMenuIntegration integration;
    private final DefaultZMenuBootstrap.BootstrapPlan plan;

    DefaultZMenuReloadPlan(DefaultZMenuIntegration integration, DefaultZMenuBootstrap.BootstrapPlan plan) {
        this.integration = integration;
        this.plan = plan;
    }

    @Override
    public void reload() {
        ZMenuRegistrationTracker tracker = this.integration.tracker();
        this.integration.buttons().unregisters(this.integration.plugin());
        this.integration.inventories().deleteInventories(this.integration.plugin());
        this.integration.inventories().unregisterOptions(this.integration.plugin());
        this.integration.inventories().unregisterInventoryOptions(this.integration.plugin());
        this.integration.inventories().unregisterListener(this.integration.plugin());
        tracker.patterns().forEach(this.integration.patterns()::unregisterPattern);
        tracker.actionPatterns().forEach(this.integration.patterns()::unregisterActionPattern);
        this.integration.dialogs().ifPresent(dialogs -> dialogs.deleteDialog(this.integration.plugin()));
        this.integration.bedrock().ifPresent(bedrock -> bedrock.deleteBedrockInventory(this.integration.plugin()));
        tracker.clear();
        this.loadFresh();
    }

    void loadFresh() {
        new ZMenuFileLoader(this.integration, this.integration.tracker(), this.plan).load();
    }
}
