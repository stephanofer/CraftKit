package com.hera.craftkit.zmenu.internal;

import fr.maxlego08.menu.api.inventory.bedrock.BedrockInventory;
import fr.maxlego08.menu.api.inventory.dialog.DialogInventory;
import fr.maxlego08.menu.api.Inventory;
import fr.maxlego08.menu.api.InventoryOption;
import fr.maxlego08.menu.api.button.ButtonOption;
import fr.maxlego08.menu.api.loader.ButtonLoader;
import fr.maxlego08.menu.api.pattern.ActionPattern;
import fr.maxlego08.menu.api.pattern.Pattern;

import java.util.ArrayList;
import java.util.List;

public final class ZMenuRegistrationTracker {

    private final List<ButtonLoader> buttonLoaders = new ArrayList<>();
    private final List<Pattern> patterns = new ArrayList<>();
    private final List<ActionPattern> actionPatterns = new ArrayList<>();
    private final List<Inventory> inventories = new ArrayList<>();
    private final List<DialogInventory> dialogs = new ArrayList<>();
    private final List<BedrockInventory> bedrockInventories = new ArrayList<>();
    private final List<Class<? extends ButtonOption>> buttonOptions = new ArrayList<>();
    private final List<Class<? extends InventoryOption>> inventoryOptions = new ArrayList<>();

    void trackButtonLoader(ButtonLoader loader) { this.buttonLoaders.add(loader); }
    void trackPattern(Pattern pattern) { if (pattern != null) this.patterns.add(pattern); }
    void trackActionPattern(ActionPattern pattern) { if (pattern != null) this.actionPatterns.add(pattern); }
    void trackInventory(Inventory inventory) { if (inventory != null) this.inventories.add(inventory); }
    void trackDialog(DialogInventory dialog) { if (dialog != null) this.dialogs.add(dialog); }
    void trackBedrock(BedrockInventory inventory) { if (inventory != null) this.bedrockInventories.add(inventory); }
    void trackButtonOption(Class<? extends ButtonOption> option) { this.buttonOptions.add(option); }
    void trackInventoryOption(Class<? extends InventoryOption> option) { this.inventoryOptions.add(option); }

    List<Pattern> patterns() { return List.copyOf(this.patterns); }
    List<ActionPattern> actionPatterns() { return List.copyOf(this.actionPatterns); }

    void clear() {
        this.buttonLoaders.clear();
        this.patterns.clear();
        this.actionPatterns.clear();
        this.inventories.clear();
        this.dialogs.clear();
        this.bedrockInventories.clear();
        this.buttonOptions.clear();
        this.inventoryOptions.clear();
    }
}
