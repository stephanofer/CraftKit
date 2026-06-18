package com.hera.craftkit.feedback;

import com.hera.craftkit.feedback.internal.BukkitFeedbackBuilder;
import org.bukkit.plugin.Plugin;

import java.util.Objects;

public final class Feedbacks {

    private Feedbacks() {
    }

    public static BukkitFeedbackBuilder paper(Plugin plugin) {
        return new BukkitFeedbackBuilder(Objects.requireNonNull(plugin, "plugin"));
    }
}
