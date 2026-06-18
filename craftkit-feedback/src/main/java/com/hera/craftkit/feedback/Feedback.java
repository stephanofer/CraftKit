package com.hera.craftkit.feedback;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.entity.Player;

import java.util.List;

public interface Feedback extends AutoCloseable {

    void send(Player player, String key, TagResolver... placeholders);

    void chat(Player player, String key, TagResolver... placeholders);

    void actionBar(Player player, String key, TagResolver... placeholders);

    void title(Player player, String key, TagResolver... placeholders);

    void sound(Player player, String key, TagResolver... placeholders);

    Component component(Player player, String key, TagResolver... placeholders);

    List<Component> components(Player player, String key, TagResolver... placeholders);

    void replaceTranslations(FeedbackTranslations translations);

    @Override
    void close();
}
