package com.hera.craftkit.feedback;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

public final class FeedbackPlaceholders {

    private FeedbackPlaceholders() {
    }

    public static TagResolver text(String name, String value) {
        return Placeholder.unparsed(name, value == null ? "" : value);
    }

    public static TagResolver parsed(String name, String value) {
        return Placeholder.parsed(name, value == null ? "" : value);
    }

    public static TagResolver component(String name, Component value) {
        return Placeholder.component(name, value == null ? Component.empty() : value);
    }

    public static TagResolver number(String name, Number value) {
        return Placeholder.unparsed(name, value == null ? "0" : value.toString());
    }
}
