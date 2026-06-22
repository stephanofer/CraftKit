package com.hera.craftkit.paper.minimessage;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.ArgumentQueue;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.object.ObjectContents;
import net.kyori.adventure.text.object.PlayerHeadObjectContents;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.regex.Pattern;

public final class CraftKitMiniMessageTags {

    public static final String PLAYER_HEAD_TAG = "craftkit_head";

    private static final Pattern TEXTURE_HASH = Pattern.compile("^[a-fA-F0-9]{64}$");
    private static final String TEXTURE_URL_PREFIX = "http://textures.minecraft.net/texture/";
    private static final String TEXTURE_URL_HTTP = "http://textures.minecraft.net/texture/";
    private static final String TEXTURE_URL_HTTPS = "https://textures.minecraft.net/texture/";

    private CraftKitMiniMessageTags() {
    }

    public static TagResolver playerHead() {
        return playerHead(PLAYER_HEAD_TAG);
    }

    public static TagResolver playerHead(String tagName) {
        if (tagName == null || tagName.isBlank()) {
            throw new IllegalArgumentException("tagName cannot be null or blank");
        }
        return TagResolver.resolver(tagName, (args, context) -> Tag.selfClosingInserting(playerHeadComponent(args)));
    }

    private static Component playerHeadComponent(ArgumentQueue args) {
        if (!args.hasNext()) {
            return Component.empty();
        }

        String texture = args.pop().value().trim();
        while (args.hasNext()) {
            String next = args.pop().value().trim();
            if (!args.hasNext() && isBoolean(next)) {
                return componentFrom(texture, Boolean.parseBoolean(next));
            }
            texture = texture + ':' + next;
        }

        return componentFrom(texture, PlayerHeadObjectContents.DEFAULT_HAT);
    }

    private static Component componentFrom(String texture, boolean hat) {
        String value = textureValue(texture);
        if (value.isEmpty()) {
            return Component.empty();
        }

        PlayerHeadObjectContents contents = ObjectContents.playerHead()
            .profileProperty(PlayerHeadObjectContents.property("textures", value))
            .hat(hat)
            .build();
        return Component.object(contents);
    }

    private static String textureValue(String texture) {
        if (texture == null) {
            return "";
        }

        String input = texture.trim();
        if (input.isEmpty()) {
            return "";
        }

        String hash = textureHash(input);
        if (hash != null) {
            return encodeTextureUrl(TEXTURE_URL_PREFIX + hash);
        }

        return isValidBase64(input) ? input : "";
    }

    private static String textureHash(String input) {
        if (TEXTURE_HASH.matcher(input).matches()) {
            return input.toLowerCase(Locale.ROOT);
        }

        if (input.regionMatches(true, 0, TEXTURE_URL_HTTP, 0, TEXTURE_URL_HTTP.length())) {
            return validHashOrNull(input.substring(TEXTURE_URL_HTTP.length()));
        }

        if (input.regionMatches(true, 0, TEXTURE_URL_HTTPS, 0, TEXTURE_URL_HTTPS.length())) {
            return validHashOrNull(input.substring(TEXTURE_URL_HTTPS.length()));
        }

        return null;
    }

    private static String validHashOrNull(String value) {
        String hash = value.trim();
        return TEXTURE_HASH.matcher(hash).matches() ? hash.toLowerCase(Locale.ROOT) : null;
    }

    private static String encodeTextureUrl(String url) {
        String json = "{\"textures\":{\"SKIN\":{\"url\":\"" + url + "\"}}}";
        return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    private static boolean isValidBase64(String value) {
        try {
            byte[] decoded = Base64.getDecoder().decode(value);
            String json = new String(decoded, StandardCharsets.UTF_8);
            return json.contains("\"textures\"") && json.contains("\"SKIN\"") && json.contains("\"url\"");
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static boolean isBoolean(String value) {
        return "true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value);
    }
}
