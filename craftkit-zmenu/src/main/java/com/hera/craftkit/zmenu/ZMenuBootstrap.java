package com.hera.craftkit.zmenu;

import fr.maxlego08.menu.api.InventoryOption;
import fr.maxlego08.menu.api.button.ButtonOption;
import fr.maxlego08.menu.api.loader.ButtonLoader;

import java.util.function.Consumer;

public interface ZMenuBootstrap {

    ZMenuBootstrap buttons(Consumer<ButtonRegistry> consumer);

    ZMenuBootstrap buttonOptions(Class<? extends ButtonOption>... options);

    ZMenuBootstrap inventoryOptions(Class<? extends InventoryOption>... options);

    ZMenuBootstrap defaultInventories(String... paths);

    ZMenuBootstrap defaultPatterns(String... paths);

    ZMenuBootstrap defaultActionPatterns(String... paths);

    ZMenuBootstrap defaultDialogs(String... paths);

    ZMenuBootstrap defaultBedrock(String... paths);

    ZMenuBootstrap inventories(String folder);

    ZMenuBootstrap patterns(String folder);

    ZMenuBootstrap actionPatterns(String folder);

    ZMenuBootstrap dialogs(String folder);

    ZMenuBootstrap bedrock(String folder);

    ZMenuReloadPlan load();

    interface ButtonRegistry {
        void button(ButtonLoader loader);
    }
}
