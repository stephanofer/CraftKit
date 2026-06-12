package com.hera.craftkit.zmenu;

import fr.maxlego08.menu.api.BedrockManager;
import fr.maxlego08.menu.api.ButtonManager;
import fr.maxlego08.menu.api.DialogManager;
import fr.maxlego08.menu.api.InventoryManager;
import fr.maxlego08.menu.api.MenuPlugin;
import fr.maxlego08.menu.api.pattern.PatternManager;
import org.bukkit.entity.Player;

import java.util.Optional;

public interface ZMenuIntegration {

    MenuPlugin menuPlugin();

    InventoryManager inventories();

    ButtonManager buttons();

    PatternManager patterns();

    Optional<DialogManager> dialogs();

    Optional<BedrockManager> bedrock();

    void open(Player player, String inventoryName);

    void open(Player player, String inventoryName, int page);

    void openWithHistory(Player player, String inventoryName, int page);

    ZMenuBootstrap bootstrap();

    ZMenuReloadPlan reloadPlan();

    void reload();
}
