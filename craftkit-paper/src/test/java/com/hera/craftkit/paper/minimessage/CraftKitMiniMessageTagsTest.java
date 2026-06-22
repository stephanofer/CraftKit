package com.hera.craftkit.paper.minimessage;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ObjectComponent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.object.PlayerHeadObjectContents;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CraftKitMiniMessageTagsTest {

    private static final String HASH = "c1a6dff7ef4f96f8be24ee808f8e9fb201155101b2567e64f80812df9660035b";
    private static final String VALUE = Base64.getEncoder().encodeToString((
        "{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/" + HASH + "\"}}}"
    ).getBytes(StandardCharsets.UTF_8));

    private final MiniMessage miniMessage = MiniMessage.builder()
        .editTags(tags -> tags.resolver(CraftKitMiniMessageTags.playerHead()))
        .build();

    @Test
    void createsPlayerHeadFromBase64Value() {
        Component component = miniMessage.deserialize("<craftkit_head:" + VALUE + ">");

        PlayerHeadObjectContents contents = headContents(component);

        assertEquals(VALUE, contents.profileProperties().getFirst().value());
        assertTrue(contents.hat());
    }

    @Test
    void createsPlayerHeadFromTextureHash() {
        Component component = miniMessage.deserialize("<craftkit_head:" + HASH + ">");

        PlayerHeadObjectContents contents = headContents(component);

        assertEquals(VALUE, contents.profileProperties().getFirst().value());
    }

    @Test
    void createsPlayerHeadFromQuotedTextureUrl() {
        Component component = miniMessage.deserialize("<craftkit_head:\"https://textures.minecraft.net/texture/" + HASH + "\">");

        PlayerHeadObjectContents contents = headContents(component);

        assertEquals(VALUE, contents.profileProperties().getFirst().value());
    }

    @Test
    void supportsHatArgument() {
        Component component = miniMessage.deserialize("<craftkit_head:" + VALUE + ":false>");

        PlayerHeadObjectContents contents = headContents(component);

        assertEquals(false, contents.hat());
    }

    @Test
    void invalidTextureProducesEmptyComponent() {
        Component component = miniMessage.deserialize("before<craftkit_head:not-a-texture>after");

        assertEquals("beforeafter", PlainTextComponentSerializer.plainText().serialize(component));
    }

    @Test
    void supportsCustomTagName() {
        MiniMessage custom = MiniMessage.builder()
            .editTags(tags -> tags.resolver(CraftKitMiniMessageTags.playerHead("hera_head")))
            .build();

        Component component = custom.deserialize("<hera_head:" + VALUE + ">");

        assertEquals(VALUE, headContents(component).profileProperties().getFirst().value());
    }

    private static PlayerHeadObjectContents headContents(Component component) {
        ObjectComponent object = assertInstanceOf(ObjectComponent.class, component);
        return assertInstanceOf(PlayerHeadObjectContents.class, object.contents());
    }
}
