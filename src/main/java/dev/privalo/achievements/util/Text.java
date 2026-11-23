package dev.privalo.achievements.util;

import dev.privalo.achievements.model.Glyph;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * MiniMessage helpers, custom tags and PlaceholderAPI bridging.
 * <ul>
 *   <li>{@code <glyph:name>} — inserts a font sprite</li>
 *   <li>{@code <overlay:name>} — inserts a sprite and moves the cursor back (zero width, used for backgrounds)</li>
 *   <li>{@code <space:px>} — moves the cursor by px pixels (negative = left)</li>
 * </ul>
 */
public final class Text {

    public static final MiniMessage MM = MiniMessage.miniMessage();
    public static final Key DEFAULT_FONT = Key.key("minecraft", "default");

    private static final int[] SPACE_STEPS = {128, 64, 32, 16, 8, 4, 2, 1};
    private static volatile TagResolver customTags = TagResolver.empty();
    private static volatile BiFunction<Player, String, String> placeholderProcessor;
    private static volatile Key font = DEFAULT_FONT;
    private static volatile Map<String, Glyph> glyphs = Map.of();
    /** Rendered advance (pixels) of every glyph, by glyph name. */
    private static volatile Map<String, Integer> glyphAdvances = Map.of();
    /** Advance of special characters (glyph chars + space chars). */
    private static volatile Map<Character, Integer> charAdvances = Map.of();

    private Text() {
    }

    /**
     * Configures the custom tags for the given glyphs and font.
     *
     * @param advances measured glyph advances by name (from the generated pack); missing ones are estimated
     */
    public static void configure(Map<String, Glyph> glyphMap, Key fontKey, Map<String, Integer> advances) {
        glyphs = Map.copyOf(glyphMap);
        font = fontKey;
        Map<String, Integer> adv = new HashMap<>();
        Map<Character, Integer> chars = new HashMap<>();
        for (Glyph g : glyphMap.values()) {
            int a = advances.getOrDefault(g.name(), g.height() + 1);
            adv.put(g.name(), a);
            for (char c : g.chars().toCharArray()) {
                chars.put(c, a);
            }
        }
        for (Map.Entry<String, Integer> e : spaceAdvances().entrySet()) {
            chars.put(e.getKey().charAt(0), e.getValue());
        }
        glyphAdvances = Map.copyOf(adv);
        charAdvances = Map.copyOf(chars);

        TagResolver glyph = TagResolver.resolver("glyph", (args, ctx) ->
                Tag.selfClosingInserting(glyph(args.popOr("<glyph:name> requires a name").value())));
        TagResolver overlay = TagResolver.resolver("overlay", (args, ctx) -> {
            String name = args.popOr("<overlay:name> requires a name").value();
            return Tag.selfClosingInserting(glyph(name).append(space(-glyphAdvance(name))));
        });
        TagResolver space = TagResolver.resolver("space", (args, ctx) -> {
            String raw = args.popOr("<space:px> requires a number").value();
            int px;
            try {
                px = Integer.parseInt(raw.trim());
            } catch (NumberFormatException ex) {
                px = 0;
            }
            return Tag.selfClosingInserting(space(px));
        });
        customTags = TagResolver.resolver(glyph, overlay, space);
    }

    private static Component withFont(Component component) {
        return DEFAULT_FONT.equals(font) ? component : component.font(font);
    }

    /** A glyph component (white so the texture keeps its colours), or empty when unknown. */
    public static Component glyph(String name) {
        Glyph g = glyphs.get(name.toLowerCase(Locale.ROOT));
        if (g == null) {
            return Component.empty();
        }
        return withFont(Component.text(g.chars(), NamedTextColor.WHITE)
                .decoration(TextDecoration.BOLD, false).decoration(TextDecoration.ITALIC, false));
    }

    /** A component that moves the cursor by px pixels. */
    public static Component space(int px) {
        if (px == 0) {
            return Component.empty();
        }
        return withFont(Component.text(spaces(px)).decoration(TextDecoration.BOLD, false));
    }

    public static boolean hasGlyph(String name) {
        return name != null && glyphs.containsKey(name.toLowerCase(Locale.ROOT));
    }

    public static int glyphAdvance(String name) {
        return glyphAdvances.getOrDefault(name.toLowerCase(Locale.ROOT), 0);
    }

    /**
     * Characters of the "space" font provider generated in the resource pack.
     * Negative: .. (-1..-128), positive: .. (1..128).
     */
    public static Map<String, Integer> spaceAdvances() {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (int i = 0; i < SPACE_STEPS.length; i++) {
            int value = SPACE_STEPS[SPACE_STEPS.length - 1 - i];
            map.put(String.valueOf((char) (0xF801 + i)), -value);
            map.put(String.valueOf((char) (0xF821 + i)), value);
        }
        return map;
    }

    /**
     * Builds a string that moves the text cursor by {@code px} pixels using the space provider.
     */
    public static String spaces(int px) {
        StringBuilder sb = new StringBuilder();
        boolean negative = px < 0;
        int remaining = Math.abs(px);
        for (int i = 0; i < SPACE_STEPS.length && remaining > 0; i++) {
            int step = SPACE_STEPS[i];
            int index = SPACE_STEPS.length - 1 - i;
            while (remaining >= step) {
                sb.append((char) ((negative ? 0xF801 : 0xF821) + index));
                remaining -= step;
            }
        }
        return sb.toString();
    }

    // ── Width estimation (default font) ────────────────────────────

    /**
     * Approximate rendered width in pixels, used to centre layouts made of glyphs + text.
     */
    public static int width(Component component) {
        return width(component, false);
    }

    private static int width(Component component, boolean parentBold) {
        TextDecoration.State state = component.decoration(TextDecoration.BOLD);
        boolean bold = state == TextDecoration.State.NOT_SET ? parentBold : state == TextDecoration.State.TRUE;
        int total = 0;
        if (component instanceof TextComponent text) {
            for (char c : text.content().toCharArray()) {
                Integer special = charAdvances.get(c);
                if (special != null) {
                    total += special;
                } else {
                    total += charWidth(c) + (bold ? 1 : 0);
                }
            }
        }
        for (Component child : component.children()) {
            total += width(child, bold);
        }
        return total;
    }

    private static int charWidth(char c) {
        return switch (c) {
            case 'i', '!', ',', '.', ':', ';', '|', '\'' -> 2;
            case 'l', '`' -> 3;
            case 'I', '[', ']', 't', ' ' -> 4;
            case 'f', 'k', '<', '>', '(', ')', '{', '}', '"', '*' -> 5;
            case '@', '~' -> 7;
            default -> 6;
        };
    }

    // ── Parsing ────────────────────────────────────────────────────

    public static void setPlaceholderProcessor(BiFunction<Player, String, String> processor) {
        placeholderProcessor = processor;
    }

    public static String applyPlaceholders(Player player, String input) {
        BiFunction<Player, String, String> processor = placeholderProcessor;
        if (player == null || processor == null || input == null || input.indexOf('%') < 0) {
            return input;
        }
        return processor.apply(player, input);
    }

    public static Component parse(String input, TagResolver... resolvers) {
        return parse(null, input, resolvers);
    }

    public static Component parse(Player player, String input, TagResolver... resolvers) {
        if (input == null || input.isEmpty()) {
            return Component.empty();
        }
        TagResolver[] all = new TagResolver[resolvers.length + 1];
        System.arraycopy(resolvers, 0, all, 0, resolvers.length);
        all[resolvers.length] = customTags;
        return MM.deserialize(applyPlaceholders(player, input), all);
    }

    /** Parses a text for item names/lore (non italic by default). */
    public static Component item(Player player, String input, TagResolver... resolvers) {
        return parse(player, input, resolvers).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public static List<Component> items(Player player, List<String> lines, TagResolver... resolvers) {
        List<Component> out = new ArrayList<>(lines.size());
        for (String line : lines) {
            out.add(item(player, line, resolvers));
        }
        return out;
    }

    public static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    public static String legacy(Component component) {
        return LegacyComponentSerializer.legacySection().serialize(component);
    }

    /** Progress bar in MiniMessage made of thin segments. */
    public static String bar(double ratio, int length) {
        int filled = (int) Math.round(Math.max(0, Math.min(1, ratio)) * length);
        return "<#5BE36B>" + "▌".repeat(filled) + "</#5BE36B><#3A3A46>" + "▌".repeat(length - filled) + "</#3A3A46>";
    }
}
