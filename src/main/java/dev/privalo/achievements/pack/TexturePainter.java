package dev.privalo.achievements.pack;

import java.awt.image.BufferedImage;
import java.util.Locale;
import java.util.function.BiPredicate;

/**
 * Procedural pixel-art textures for the built-in glyphs and item icons.
 * <p>
 * Recognised paths (relative to {@code textures/}):
 * <ul>
 *   <li>{@code font/pip_<rarity>.png} — 9x9 chat badge</li>
 *   <li>{@code font/badge_<rarity>.png}, {@code item/badge/<rarity>.png} — 16x16 medal</li>
 *   <li>{@code font/card_<rarity>.png} — 240x16 boss bar card</li>
 *   <li>{@code font/banner_<rarity>.png} — 96x16 title ribbon</li>
 *   <li>{@code font/gui_achievements_<all|unlocked|pending>.png} — 256x256 menu background</li>
 *   <li>{@code item/gui/<previous|next|tab_all|tab_unlocked|tab_pending|in_progress|locked|secret|blank>.png}</li>
 * </ul>
 * Rarities: common, rare, epic, secret, event (anything else uses the common palette).
 */
public final class TexturePainter {

    private TexturePainter() {
    }

    /** @return a painted texture, or null when the path is not a built-in one. */
    public static BufferedImage paint(String texturePath) {
        String path = texturePath.replace('\\', '/').toLowerCase(Locale.ROOT);
        if (path.endsWith(".png")) {
            path = path.substring(0, path.length() - 4);
        }
        String name = path.substring(path.lastIndexOf('/') + 1);
        if (path.startsWith("item/badge/")) {
            return badge(rarity(name));
        }
        if (path.startsWith("item/gui/")) {
            return guiIcon(name);
        }
        if (name.startsWith("pip_")) {
            return pip(rarity(name.substring(4)));
        }
        if (name.startsWith("badge_")) {
            return badge(rarity(name.substring(6)));
        }
        if (name.startsWith("card_")) {
            return card(rarity(name.substring(5)));
        }
        if (name.startsWith("banner_")) {
            return banner(rarity(name.substring(7)));
        }
        if (name.startsWith("gui_achievements")) {
            String filter = name.length() > 17 ? name.substring(17) : "all";
            return gui(filter);
        }
        return null;
    }

    // ── Palettes ───────────────────────────────────────────────────

    private record Palette(int base, int light, int dark, int emblem) {
    }

    private static Palette rarity(String name) {
        return switch (name) {
            case "rare" -> new Palette(0xFF3F8FE0, 0xFF9BD4FF, 0xFF143A6B, 0xFFE6F6FF);
            case "epic" -> new Palette(0xFFA548E8, 0xFFE3A8FF, 0xFF431566, 0xFFFFEFFF);
            case "secret" -> new Palette(0xFF4E5A6E, 0xFF8FE3C8, 0xFF161A22, 0xFFB8FFE6);
            case "event" -> new Palette(0xFFF2588F, 0xFFFFC2D6, 0xFF6B1534, 0xFFFFF3C4);
            default -> new Palette(0xFFE0A93A, 0xFFFFE08A, 0xFF6B4510, 0xFFFFF8DC);
        };
    }

    private static String emblemKey(Palette p) {
        if (p.base() == 0xFF3F8FE0) {
            return "rare";
        }
        if (p.base() == 0xFFA548E8) {
            return "epic";
        }
        if (p.base() == 0xFF4E5A6E) {
            return "secret";
        }
        if (p.base() == 0xFFF2588F) {
            return "event";
        }
        return "common";
    }

    private static final String[] EMBLEM_COMMON = {
            "...#...",
            "..###..",
            "#######",
            ".#####.",
            "..###..",
            ".##.##.",
            ".#...#."};
    private static final String[] EMBLEM_RARE = {
            ".#####.",
            "##.####",
            "#######",
            ".#####.",
            "..###..",
            "...#...",
            "......."};
    private static final String[] EMBLEM_EPIC = {
            ".......",
            "#..#..#",
            "##.#.##",
            "#######",
            "#######",
            ".#####.",
            "......."};
    private static final String[] EMBLEM_SECRET = {
            "..###..",
            ".#...#.",
            ".....#.",
            "...##..",
            "...#...",
            ".......",
            "...#..."};
    private static final String[] EMBLEM_EVENT = {
            "#..#..#",
            ".#.#.#.",
            "..###..",
            "###.###",
            "..###..",
            ".#.#.#.",
            "#..#..#"};

    private static String[] emblem(Palette p) {
        return switch (emblemKey(p)) {
            case "rare" -> EMBLEM_RARE;
            case "epic" -> EMBLEM_EPIC;
            case "secret" -> EMBLEM_SECRET;
            case "event" -> EMBLEM_EVENT;
            default -> EMBLEM_COMMON;
        };
    }

    // ── Badges ─────────────────────────────────────────────────────

    /** 16x16 medal: dark outline, bevelled ring, recessed face, emblem with drop shadow. */
    static BufferedImage badge(Palette p) {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        double c = 7.5;
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                double d = Math.hypot(x - c, y - c);
                double t = y / 15.0;
                if (d > 7.6) {
                    continue;
                }
                int color;
                if (d > 6.6) {
                    color = p.dark();
                } else if (d > 5.2) {
                    color = lerp(p.light(), p.base(), t);
                    if (x + y < 11) {
                        color = lerp(color, 0xFFFFFFFF, 0.35);
                    } else if (x + y > 20) {
                        color = lerp(color, p.dark(), 0.35);
                    }
                } else if (d > 4.4) {
                    color = lerp(p.dark(), 0xFF000000, 0.2);
                } else {
                    color = lerp(lerp(p.base(), p.dark(), 0.6), lerp(p.base(), p.dark(), 0.85), t);
                }
                img.setRGB(x, y, color);
            }
        }
        drawEmblem(img, emblem(p), 4, 4, p.emblem(), lerp(p.dark(), 0xFF000000, 0.4));
        // sparkle
        img.setRGB(4, 3, 0xFFFFFFFF);
        img.setRGB(3, 4, 0xC0FFFFFF);
        return img;
    }

    /** 9x9 chat-sized badge. */
    static BufferedImage pip(Palette p) {
        BufferedImage img = new BufferedImage(9, 9, BufferedImage.TYPE_INT_ARGB);
        double c = 4;
        for (int y = 0; y < 9; y++) {
            for (int x = 0; x < 9; x++) {
                double d = Math.hypot(x - c, y - c);
                if (d > 4.4) {
                    continue;
                }
                int color = d > 3.5 ? p.dark() : lerp(p.light(), p.base(), y / 8.0);
                img.setRGB(x, y, color);
            }
        }
        int gem = p.emblem();
        img.setRGB(4, 2, gem);
        img.setRGB(3, 3, gem);
        img.setRGB(4, 3, 0xFFFFFFFF);
        img.setRGB(5, 3, gem);
        img.setRGB(2, 4, gem);
        img.setRGB(3, 4, gem);
        img.setRGB(4, 4, gem);
        img.setRGB(5, 4, gem);
        img.setRGB(6, 4, gem);
        img.setRGB(3, 5, lerp(gem, p.dark(), 0.3));
        img.setRGB(4, 5, lerp(gem, p.dark(), 0.3));
        img.setRGB(5, 5, lerp(gem, p.dark(), 0.3));
        img.setRGB(4, 6, lerp(gem, p.dark(), 0.5));
        return img;
    }

    private static void drawEmblem(BufferedImage img, String[] pattern, int ox, int oy, int color, int shadow) {
        for (int y = 0; y < pattern.length; y++) {
            for (int x = 0; x < pattern[y].length(); x++) {
                if (pattern[y].charAt(x) == '#') {
                    setIfInside(img, ox + x + 1, oy + y + 1, shadow);
                }
            }
        }
        for (int y = 0; y < pattern.length; y++) {
            for (int x = 0; x < pattern[y].length(); x++) {
                if (pattern[y].charAt(x) == '#') {
                    int shade = y == 0 ? lerp(color, 0xFFFFFFFF, 0.4) : lerp(color, shadow, y / (double) (pattern.length * 5));
                    img.setRGB(ox + x, oy + y, shade);
                }
            }
        }
    }

    // ── Boss bar card & title banner ───────────────────────────────

    /** 240x16 translucent card with coloured frame, icon socket and corner ornaments. */
    static BufferedImage card(Palette p) {
        int w = 240;
        int h = 16;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (corner(x, y, w, h, 2)) {
                    continue;
                }
                int color;
                if (x == 0 || y == 0 || x == w - 1 || y == h - 1) {
                    color = p.dark();
                } else if (x == 1 || y == 1 || x == w - 2 || y == h - 2) {
                    color = y <= 1 ? p.light() : (y >= h - 2 ? lerp(p.base(), p.dark(), 0.4) : p.base());
                } else {
                    int body = lerp(0xEB24242E, 0xEB14141A, (y - 2) / (double) (h - 4));
                    double tint = Math.max(0, 1 - x / 70.0) * 0.28;
                    color = lerp(body, p.base() & 0x00FFFFFF | 0xEB000000, tint);
                    if (y == 2) {
                        color = lerp(color, p.light() & 0x00FFFFFF | 0xEB000000, 0.18);
                    }
                }
                img.setRGB(x, y, color);
            }
        }
        // icon socket
        for (int y = 2; y < h - 2; y++) {
            for (int x = 3; x < 17; x++) {
                boolean edge = x == 3 || x == 16 || y == 2 || y == h - 3;
                img.setRGB(x, y, edge ? lerp(p.base(), p.dark(), 0.3) : lerp(p.dark(), 0xFF000000, 0.45));
            }
        }
        // right ornament + dotted bottom accents
        diamond(img, w - 9, h / 2, 3, p.light(), p.dark());
        diamond(img, w - 16, h / 2, 1, p.base(), p.dark());
        for (int x = 24; x < w - 24; x += 12) {
            img.setRGB(x, h - 3, p.base() & 0x00FFFFFF | 0x90000000);
        }
        return img;
    }

    /** 96x16 ribbon banner with notched tails and a central gem (used in titles). */
    static BufferedImage banner(Palette p) {
        int w = 96;
        int h = 16;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        int tail = 8;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                boolean inTailLeft = x < tail;
                boolean inTailRight = x >= w - tail;
                int notch = Math.abs(y - h / 2);
                if (inTailLeft && (y < 3 || y > h - 4 || x < (h / 2 - notch) - 2)) {
                    continue;
                }
                if (inTailRight && (y < 3 || y > h - 4 || (w - 1 - x) < (h / 2 - notch) - 2)) {
                    continue;
                }
                int color;
                if (inTailLeft || inTailRight) {
                    color = lerp(p.dark(), p.base(), 0.35 + (y - 3) / 40.0);
                    if (y == 3 || y == h - 4) {
                        color = p.dark();
                    }
                } else if (y == 0 || y == h - 1 || x == tail || x == w - tail - 1) {
                    color = p.dark();
                } else if (y == 1) {
                    color = p.light();
                } else if (y == h - 2) {
                    color = lerp(p.base(), p.dark(), 0.5);
                } else {
                    color = lerp(p.base(), lerp(p.base(), p.dark(), 0.55), (y - 2) / (double) (h - 4));
                    if (y == 3 || y == h - 4) {
                        color = lerp(color, p.light(), 0.4);
                    }
                }
                img.setRGB(x, y, color);
            }
        }
        diamond(img, w / 2, h / 2, 4, p.emblem(), p.dark());
        img.setRGB(w / 2 - 1, h / 2 - 2, 0xFFFFFFFF);
        diamond(img, tail + 6, h / 2, 1, p.light(), p.dark());
        diamond(img, w - tail - 7, h / 2, 1, p.light(), p.dark());
        return img;
    }

    private static void diamond(BufferedImage img, int cx, int cy, int r, int fill, int outline) {
        for (int y = -r - 1; y <= r + 1; y++) {
            for (int x = -r - 1; x <= r + 1; x++) {
                int d = Math.abs(x) + Math.abs(y);
                if (d == r + 1) {
                    setIfInside(img, cx + x, cy + y, outline);
                } else if (d <= r) {
                    int color = y < 0 ? lerp(fill, 0xFFFFFFFF, 0.25) : lerp(fill, outline, 0.25);
                    setIfInside(img, cx + x, cy + y, color);
                }
            }
        }
    }

    // ── Menu background ────────────────────────────────────────────

    private static final int FRAME = 0xFF0E0E12;
    private static final int GOLD = 0xFFE0B04A;
    private static final int GOLD_DARK = 0xFF7A5A1E;
    private static final int PANEL_TOP = 0xFF2A2A36;
    private static final int PANEL_BOTTOM = 0xFF1E1E27;

    /**
     * 256x256 canvas with a 176x222 dark themed double-chest texture: header, bordered achievement grid
     * (columns 1-7), decorative pillars (columns 0 and 8), navigation row with filter tabs and player inventory.
     */
    static BufferedImage gui(String activeFilter) {
        BufferedImage img = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
        int w = 176;
        int h = 222;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (corner(x, y, w, h, 3)) {
                    continue;
                }
                int color = lerp(PANEL_TOP, PANEL_BOTTOM, y / (double) h);
                if (x == 0 || y == 0 || x == w - 1 || y == h - 1) {
                    color = FRAME;
                } else if (x == 1 || y == 1) {
                    color = GOLD;
                } else if (x == w - 2 || y == h - 2) {
                    color = GOLD_DARK;
                }
                img.setRGB(x, y, color);
            }
        }
        // header band (title area)
        for (int y = 2; y < 16; y++) {
            for (int x = 2; x < w - 2; x++) {
                img.setRGB(x, y, lerp(0xFF4A3A18, 0xFF241C0D, (y - 2) / 13.0));
            }
        }
        for (int x = 2; x < w - 2; x++) {
            img.setRGB(x, 16, GOLD);
            img.setRGB(x, 2, lerp(GOLD, 0xFF4A3A18, 0.5));
        }
        diamond(img, w - 12, 9, 3, GOLD, GOLD_DARK);
        diamond(img, w - 22, 9, 1, GOLD, GOLD_DARK);

        // achievement grid rows 0..4
        for (int row = 0; row < 5; row++) {
            int sy = 17 + row * 18;
            pillar(img, 7, sy);
            pillar(img, 7 + 8 * 18, sy);
            for (int col = 1; col <= 7; col++) {
                slot(img, 7 + col * 18, sy, false);
            }
        }

        // navigation row (slots 45..53)
        int ny = 17 + 5 * 18;
        for (int x = 2; x < w - 2; x++) {
            img.setRGB(x, ny, GOLD_DARK);
            for (int y = ny + 1; y < ny + 18; y++) {
                img.setRGB(x, y, 0xFF17171E);
            }
        }
        button(img, 7, ny + 1, GOLD, false);
        button(img, 7 + 18, ny + 1, 0xFF8C8CA0, false);
        slot(img, 7 + 2 * 18, ny + 1, true);
        button(img, 7 + 3 * 18, ny + 1, 0xFF4F9BE0, "all".equals(activeFilter));
        button(img, 7 + 4 * 18, ny + 1, 0xFF56C25A, "unlocked".equals(activeFilter));
        button(img, 7 + 5 * 18, ny + 1, 0xFFE89A2E, "pending".equals(activeFilter));
        slot(img, 7 + 6 * 18, ny + 1, true);
        slot(img, 7 + 7 * 18, ny + 1, true);
        button(img, 7 + 8 * 18, ny + 1, GOLD, false);

        // separator + player inventory
        int sep = ny + 20;
        for (int x = 4; x < w - 4; x++) {
            img.setRGB(x, sep, lerp(GOLD_DARK, PANEL_BOTTOM, Math.abs(x - w / 2.0) / (w / 2.0)));
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                slot(img, 7 + col * 18, 139 + row * 18, false);
            }
        }
        for (int col = 0; col < 9; col++) {
            slot(img, 7 + col * 18, 197, false);
        }
        return img;
    }

    /** 18x18 recessed slot (the item renders at +1,+1). */
    private static void slot(BufferedImage img, int x0, int y0, boolean muted) {
        for (int y = 0; y < 18; y++) {
            for (int x = 0; x < 18; x++) {
                int color;
                if (x == 0 || y == 0) {
                    color = 0xFF0A0A0D;
                } else if (x == 17 || y == 17) {
                    color = muted ? 0xFF2A2A33 : 0xFF3D3D4C;
                } else {
                    color = muted ? 0xFF111116 : lerp(0xFF15151B, 0xFF1C1C24, y / 17.0);
                }
                img.setRGB(x0 + x, y0 + y, color);
            }
        }
    }

    /** Raised 18x18 button; active buttons glow. */
    private static void button(BufferedImage img, int x0, int y0, int base, boolean active) {
        int fill = active ? lerp(base, 0xFFFFFFFF, 0.15) : lerp(base, 0xFF000000, 0.45);
        for (int y = 0; y < 18; y++) {
            for (int x = 0; x < 18; x++) {
                int color;
                if (x == 0 || y == 0 || x == 17 || y == 17) {
                    color = active ? 0xFFFFFFFF : 0xFF050507;
                } else if (x == 1 || y == 1) {
                    color = lerp(base, 0xFFFFFFFF, active ? 0.55 : 0.2);
                } else if (x == 16 || y == 16) {
                    color = lerp(base, 0xFF000000, 0.6);
                } else {
                    color = lerp(fill, lerp(fill, 0xFF000000, 0.25), y / 17.0);
                }
                img.setRGB(x0 + x, y0 + y, color);
            }
        }
        if (active) {
            for (int i = 0; i < 3; i++) {
                for (int x = 9 - i; x <= 8 + i; x++) {
                    setIfInside(img, x0 + x, y0 + 18 + (2 - i), 0xFFFFFFFF);
                }
            }
        }
    }

    /** Decorative gold pillar filling one 18x18 cell. */
    private static void pillar(BufferedImage img, int x0, int y0) {
        for (int y = 0; y < 18; y++) {
            for (int x = 0; x < 18; x++) {
                int color = lerp(0xFF2E2638, 0xFF231D2B, x / 17.0);
                if (x == 3 || x == 14) {
                    color = GOLD_DARK;
                } else if (x == 4 || x == 13) {
                    color = lerp(GOLD, GOLD_DARK, 0.5);
                }
                img.setRGB(x0 + x, y0 + y, color);
            }
        }
        diamond(img, x0 + 8, y0 + 8, 3, GOLD, GOLD_DARK);
        img.setRGB(x0 + 7, y0 + 6, 0xFFFFF4C8);
    }

    // ── Menu item icons (16x16) ────────────────────────────────────

    static BufferedImage guiIcon(String name) {
        return switch (name) {
            case "previous" -> shape(0xFFE0B04A, (x, y) -> arrow(x, y));
            case "next" -> shape(0xFFE0B04A, (x, y) -> arrow(15 - x, y));
            case "tab_all" -> shape(0xFF4F9BE0, (x, y) -> (x >= 2 && x <= 6 || x >= 9 && x <= 13)
                    && (y >= 2 && y <= 6 || y >= 9 && y <= 13));
            case "tab_unlocked" -> shape(0xFF56C25A, TexturePainter::check);
            case "tab_pending", "in_progress" -> hourglass("in_progress".equals(name) ? 0xFFF2C14E : 0xFFE89A2E);
            case "locked" -> padlock();
            case "secret" -> secretIcon();
            case "blank" -> new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
            default -> shape(0xFF9A9AAA, (x, y) -> Math.hypot(x - 7.5, y - 7.5) < 5.5);
        };
    }

    private static boolean arrow(int x, int y) {
        boolean head = x >= 2 && x <= 7 && Math.abs(y - 7.5) <= (x - 2) + 0.5;
        boolean shaft = x >= 7 && x <= 13 && y >= 6 && y <= 9;
        return head || shaft;
    }

    private static boolean check(int x, int y) {
        return distToSegment(x, y, 3, 8, 6, 11) < 1.3 || distToSegment(x, y, 6, 11, 12, 4) < 1.3;
    }

    private static BufferedImage hourglass(int sand) {
        BufferedImage img = shape(0xFFB8B8C8, (x, y) -> {
            if (y == 2 || y == 13) {
                return x >= 3 && x <= 12;
            }
            if (y < 3 || y > 12) {
                return false;
            }
            double half = y <= 7 ? 4.5 - (y - 3) * 0.9 : 1 + (y - 8) * 0.9;
            return Math.abs(x - 7.5) <= half + 0.5;
        });
        for (int y = 4; y <= 12; y++) {
            for (int x = 4; x <= 11; x++) {
                boolean upper = y >= 5 && y <= 6;
                boolean lower = y >= 10 && y <= 12;
                boolean stream = (x == 7 || x == 8) && y >= 7 && y <= 9;
                if ((upper || lower || stream) && (img.getRGB(x, y) >>> 24) != 0 && !edge(img, x, y)) {
                    img.setRGB(x, y, lerp(sand, 0xFFFFFFFF, y < 7 ? 0.25 : 0));
                }
            }
        }
        return img;
    }

    private static BufferedImage padlock() {
        BufferedImage img = shape(0xFF8E8E9E, (x, y) -> {
            boolean body = x >= 3 && x <= 12 && y >= 7 && y <= 13;
            double d = Math.hypot(x - 7.5, y - 6.5);
            boolean shackle = y <= 7 && d >= 2.6 && d <= 4.4;
            return body || shackle;
        });
        for (int y = 9; y <= 11; y++) {
            img.setRGB(7, y, 0xFF1A1A20);
            img.setRGB(8, y, 0xFF1A1A20);
        }
        img.setRGB(7, 9, 0xFF2A2A30);
        return img;
    }

    private static BufferedImage secretIcon() {
        BufferedImage img = shape(0xFF5B3C88, (x, y) -> Math.hypot(x - 7.5, y - 7.5) < 6.8);
        drawEmblem(img, EMBLEM_SECRET, 4, 4, 0xFFB8FFE6, 0xFF1E1230);
        return img;
    }

    private static boolean edge(BufferedImage img, int x, int y) {
        return (img.getRGB(x, y) & 0xFFFFFF) == 0x0A0A0E;
    }

    /** Fills a mask with a vertical gradient, top highlight, dark outline and soft drop shadow. */
    private static BufferedImage shape(int base, BiPredicate<Integer, Integer> mask) {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        boolean[][] m = new boolean[16][16];
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                m[y][x] = mask.test(x, y);
            }
        }
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                if (!m[y][x] && x > 0 && y > 0 && m[y - 1][x - 1]) {
                    img.setRGB(x, y, 0x60000000);
                }
            }
        }
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                if (m[y][x]) {
                    continue;
                }
                if (near(m, x, y)) {
                    img.setRGB(x, y, 0xFF0A0A0E);
                }
            }
        }
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                if (!m[y][x]) {
                    continue;
                }
                int color = lerp(lerp(base, 0xFFFFFFFF, 0.25), lerp(base, 0xFF000000, 0.3), y / 15.0);
                if (y == 0 || !m[y - 1][x]) {
                    color = lerp(base, 0xFFFFFFFF, 0.5);
                }
                img.setRGB(x, y, color);
            }
        }
        return img;
    }

    private static boolean near(boolean[][] m, int x, int y) {
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] d : dirs) {
            int nx = x + d[0];
            int ny = y + d[1];
            if (nx >= 0 && ny >= 0 && nx < 16 && ny < 16 && m[ny][nx]) {
                return true;
            }
        }
        return false;
    }

    // ── Helpers ────────────────────────────────────────────────────

    /** Generic framed placeholder for user textures that are missing. */
    public static BufferedImage placeholder(int width, int height, int argb) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Palette p = new Palette(argb | 0xFF000000, lerp(argb | 0xFF000000, 0xFFFFFFFF, 0.5),
                lerp(argb | 0xFF000000, 0xFF000000, 0.6), 0xFFFFFFFF);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (corner(x, y, width, height, 1)) {
                    continue;
                }
                boolean border = x == 0 || y == 0 || x == width - 1 || y == height - 1;
                img.setRGB(x, y, border ? p.dark() : lerp(p.light(), p.base(), y / (double) Math.max(1, height - 1)));
            }
        }
        return img;
    }

    private static boolean corner(int x, int y, int w, int h, int r) {
        int dx = x < r ? r - x : (x >= w - r ? x - (w - r - 1) : 0);
        int dy = y < r ? r - y : (y >= h - r ? y - (h - r - 1) : 0);
        return dx > 0 && dy > 0 && dx + dy > r;
    }

    private static double distToSegment(double px, double py, double ax, double ay, double bx, double by) {
        double dx = bx - ax;
        double dy = by - ay;
        double t = Math.max(0, Math.min(1, ((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy)));
        return Math.hypot(px - (ax + t * dx), py - (ay + t * dy));
    }

    private static void setIfInside(BufferedImage img, int x, int y, int argb) {
        if (x >= 0 && y >= 0 && x < img.getWidth() && y < img.getHeight()) {
            img.setRGB(x, y, argb);
        }
    }

    /** Linear interpolation of two ARGB colours (alpha included). */
    static int lerp(int a, int b, double t) {
        t = Math.max(0, Math.min(1, t));
        int aa = a >>> 24, ar = a >> 16 & 0xFF, ag = a >> 8 & 0xFF, ab = a & 0xFF;
        int ba = b >>> 24, br = b >> 16 & 0xFF, bg = b >> 8 & 0xFF, bb = b & 0xFF;
        return (int) Math.round(aa + (ba - aa) * t) << 24
                | (int) Math.round(ar + (br - ar) * t) << 16
                | (int) Math.round(ag + (bg - ag) * t) << 8
                | (int) Math.round(ab + (bb - ab) * t);
    }
}
