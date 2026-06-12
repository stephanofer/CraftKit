package com.hera.craftkit.zmenu;

import com.hera.craftkit.zmenu.internal.BukkitZMenuServices;
import org.bukkit.plugin.Plugin;

import java.util.Objects;

public final class ZMenus {

    private ZMenus() {
    }

    public static ZMenuIntegration require(Plugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        return BukkitZMenuServices.require(plugin);
    }
}
