package com.hera.craftkit.feedback.internal;

import org.bukkit.entity.Player;

@FunctionalInterface
interface PlayerLanguageResolver {

    String language(Player player);
}
