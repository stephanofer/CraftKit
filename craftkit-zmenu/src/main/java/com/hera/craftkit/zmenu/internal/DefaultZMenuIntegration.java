package com.hera.craftkit.zmenu.internal;

import com.hera.craftkit.zmenu.ZMenuBootstrap;
import com.hera.craftkit.zmenu.ZMenuIntegration;
import com.hera.craftkit.zmenu.ZMenuReloadPlan;
import fr.maxlego08.menu.api.BedrockManager;
import fr.maxlego08.menu.api.ButtonManager;
import fr.maxlego08.menu.api.DialogManager;
import fr.maxlego08.menu.api.Inventory;
import fr.maxlego08.menu.api.InventoryManager;
import fr.maxlego08.menu.api.MenuPlugin;
import fr.maxlego08.menu.api.pattern.PatternManager;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;

public final class DefaultZMenuIntegration implements ZMenuIntegration {

    private final Plugin plugin;
    private final MenuPlugin menuPlugin;
    private final InventoryManager inventoryManager;
    private final ButtonManager buttonManager;
    private final PatternManager patternManager;
    private final Optional<DialogManager> dialogManager;
    private final Optional<BedrockManager> bedrockManager;
    private final ZMenuRegistrationTracker tracker = new ZMenuRegistrationTracker();

    private DefaultZMenuReloadPlan reloadPlan;

    public DefaultZMenuIntegration(
        Plugin plugin,
        MenuPlugin menuPlugin,
        InventoryManager inventoryManager,
        ButtonManager buttonManager,
        PatternManager patternManager,
        Optional<DialogManager> dialogManager,
        Optional<BedrockManager> bedrockManager
    ) {
        this.plugin = plugin;
        this.menuPlugin = menuPlugin;
        this.inventoryManager = inventoryManager;
        this.buttonManager = buttonManager;
        this.patternManager = patternManager;
        this.dialogManager = dialogManager;
        this.bedrockManager = bedrockManager;
    }

    @Override
    public MenuPlugin menuPlugin() {
        return this.menuPlugin;
    }

    @Override
    public InventoryManager inventories() {
        return this.inventoryManager;
    }

    @Override
    public ButtonManager buttons() {
        return this.buttonManager;
    }

    @Override
    public PatternManager patterns() {
        return this.patternManager;
    }

    @Override
    public Optional<DialogManager> dialogs() {
        return this.dialogManager;
    }

    @Override
    public Optional<BedrockManager> bedrock() {
        return this.bedrockManager;
    }

    @Override
    public void open(Player player, String inventoryName) {
        this.open(player, inventoryName, 1);
    }

    @Override
    public void open(Player player, String inventoryName, int page) {
        this.findInventory(inventoryName).ifPresent(inventory -> this.inventoryManager.openInventory(player, inventory, page));
    }

    @Override
    public void openWithHistory(Player player, String inventoryName, int page) {
        this.findInventory(inventoryName).ifPresent(inventory -> this.inventoryManager.openInventoryWithOldInventories(player, inventory, page));
    }

    @Override
    public ZMenuBootstrap bootstrap() {
        return new DefaultZMenuBootstrap(this, this.tracker);
    }

    @Override
    public ZMenuReloadPlan reloadPlan() {
        if (this.reloadPlan == null) {
            throw new IllegalStateException("No CraftKit zMenu bootstrap plan has been loaded for plugin " + this.plugin.getName());
        }
        return this.reloadPlan;
    }

    @Override
    public void reload() {
        this.reloadPlan().reload();
    }

    void setReloadPlan(DefaultZMenuReloadPlan reloadPlan) {
        this.reloadPlan = reloadPlan;
    }

    Plugin plugin() {
        return this.plugin;
    }

    ZMenuRegistrationTracker tracker() {
        return this.tracker;
    }

    private Optional<Inventory> findInventory(String inventoryName) {
        Objects.requireNonNull(inventoryName, "inventoryName");
        Optional<Inventory> inventory = this.inventoryManager.getInventory(this.plugin, inventoryName);
        if (inventory.isEmpty()) {
            Logger logger = this.plugin.getLogger();
            logger.severe("Missing zMenu inventory '" + inventoryName + "' for plugin '" + this.plugin.getName() + "'. "
                + "Known plugin inventories: " + this.inventoryManager.getInventories(this.plugin).stream().map(Inventory::getName).toList()
                + ". CraftKit bootstrap loaded: " + (this.reloadPlan != null));
        }
        return inventory;
    }
}
