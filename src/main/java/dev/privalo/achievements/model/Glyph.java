package dev.privalo.achievements.model;

/**
 * A font sprite placed in the generated resource pack and usable in texts as {@code <glyph:name>}.
 *
 * @param name    id used in the tag
 * @param chars   private-use unicode character(s)
 * @param texture path inside assets/&lt;namespace&gt;/textures/ (e.g. "font/banner.png")
 * @param height  bitmap height
 * @param ascent  bitmap ascent
 * @param color   hex color used for the generated placeholder texture
 */
public record Glyph(String name, String chars, String texture, int height, int ascent, String color) {
}
