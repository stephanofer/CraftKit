package com.hera.craftkit.zmenu.internal;

import com.hera.craftkit.zmenu.ZMenuIntegration;
import com.hera.craftkit.zmenu.ZMenuMissingDependencyException;
import com.hera.craftkit.zmenu.ZMenuMissingServiceException;
import fr.maxlego08.menu.api.BedrockManager;
import fr.maxlego08.menu.api.ButtonManager;
import fr.maxlego08.menu.api.DialogManager;
import fr.maxlego08.menu.api.InventoryManager;
import fr.maxlego08.menu.api.MenuPlugin;
import fr.maxlego08.menu.api.pattern.PatternManager;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.Optional;

public final class BukkitZMenuServices {

    private static final String ZMENU_PLUGIN_NAME = "zMenu";

    private BukkitZMenuServices() {
    }

    public static ZMenuIntegration require(Plugin plugin) {
        Plugin raw = plugin.getServer().getPluginManager().getPlugin(ZMENU_PLUGIN_NAME);
        if (!(raw instanceof MenuPlugin menuPlugin)) {
            throw new ZMenuMissingDependencyException("zMenu is not loaded or does not expose MenuPlugin");
        }

        return new DefaultZMenuIntegration(
            plugin,
            menuPlugin,
            requireService(plugin, InventoryManager.class),
            requireService(plugin, ButtonManager.class),
            requireService(plugin, PatternManager.class),
            optionalService(plugin, DialogManager.class),
            optionalService(plugin, BedrockManager.class)
        );
    }

    private static <T> T requireService(Plugin plugin, Class<T> type) {
        RegisteredServiceProvider<T> provider = plugin.getServer().getServicesManager().getRegistration(type);
        if (provider == null || provider.getProvider() == null) {
            throw new ZMenuMissingServiceException("zMenu service missing: " + type.getName());
        }
        return provider.getProvider();
    }

    private static <T> Optional<T> optionalService(Plugin plugin, Class<T> type) {
        RegisteredServiceProvider<T> provider = plugin.getServer().getServicesManager().getRegistration(type);
        if (provider == null || provider.getProvider() == null) {
            return Optional.empty();
        }
        return Optional.of(provider.getProvider());
    }
}
