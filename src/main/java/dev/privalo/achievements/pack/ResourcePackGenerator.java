package dev.privalo.achievements.pack;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.privalo.achievements.RadiantAchievements;
import dev.privalo.achievements.model.Achievement;
import dev.privalo.achievements.model.Design;
import dev.privalo.achievements.model.Glyph;
import dev.privalo.achievements.model.IconSpec;
import dev.privalo.achievements.reward.Reward;
import dev.privalo.achievements.util.Text;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.configuration.ConfigurationSection;

import javax.imageio.ImageIO;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Generates plugins/RadiantAchievements/resourcepack/ (+ resourcepack.zip):
 * <ul>
 *   <li>pack.mcmeta (1.21 → 26.x format range) and pack.png</li>
 *   <li>assets/minecraft/font/default.json (configurable) — glyph bitmaps + space provider</li>
 *   <li>assets/&lt;ns&gt;/textures/font — pixel-art glyphs (painted when you don't provide your own PNG)</li>
 *   <li>assets/&lt;ns&gt;/items + models/item + textures/item — item_model icons (badges, menu buttons)</li>
 *   <li>assets/minecraft/textures/gui/sprites/boss_bar — transparent sprites for one boss bar colour</li>
 *   <li>assets/&lt;ns&gt;/sounds.json — custom sounds</li>
 * </ul>
 * User files in plugins/RadiantAchievements/assets/ are copied first and always win.
 */
public final class ResourcePackGenerator {

    /** Generation summary. */
    public record Result(Path folder, Path zip, String sha1, int glyphs, int itemModels, int sounds, List<String> warnings) {
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final RadiantAchievements plugin;
    private volatile String lastHash = "";
    private volatile byte[] lastZip = new byte[0];
    private volatile Map<String, Integer> glyphAdvances = Map.of();

    public ResourcePackGenerator(RadiantAchievements plugin) {
        this.plugin = plugin;
    }

    /** SHA-1 (hex) of the last generated zip, or empty. */
    public String lastHash() {
        return lastHash;
    }

    /** Bytes of the last generated zip (served by the built-in HTTP server). */
    public byte[] lastZip() {
        return lastZip;
    }

    /** Measured advance (pixels) of each glyph from the last generation. */
    public Map<String, Integer> glyphAdvances() {
        return glyphAdvances;
    }

    /** Creates the user asset folders with a short readme. */
    public void prepareSourceFolders() throws IOException {
        Path src = plugin.getDataFolder().toPath().resolve("assets");
        Files.createDirectories(src.resolve("textures/font"));
        Files.createDirectories(src.resolve("textures/item"));
        Files.createDirectories(src.resolve("sounds"));
        Path readme = src.resolve("README.txt");
        if (!Files.exists(readme)) {
            Files.writeString(readme, """
                    Everything inside this folder is copied into assets/<namespace>/ of the generated pack.
                    Your files always win over the built-in pixel-art textures.

                    textures/font/<name>.png  -> glyph sprites referenced in designs.yml (glyphs.<id>.texture)
                    textures/item/<path>.png  -> icon textures for item-model "<namespace>:<path>"
                    sounds/<path>.ogg         -> custom sounds referenced in config.yml (resource-pack.sounds)
                    models/, items/, lang/... -> any other vanilla resource pack file

                    Put pack.png in plugins/RadiantAchievements/ to replace the pack icon.
                    Run /ach pack to regenerate and resend the pack.
                    """, StandardCharsets.UTF_8);
        }
    }

    /** Generates the pack. Safe to call off the main thread (only reads immutable registries). */
    public Result generate() throws IOException {
        ConfigurationSection cfg = plugin.settings().section("resource-pack");
        String ns = plugin.settings().packNamespace;
        Path data = plugin.getDataFolder().toPath();
        Path root = data.resolve("resourcepack");
        Path assetsRoot = root.resolve("assets");
        Path assets = assetsRoot.resolve(ns);
        List<String> warnings = new ArrayList<>();

        deleteRecursively(root);
        Files.createDirectories(assets);
        prepareSourceFolders();

        // 1. user assets (they win over generated ones: generation only fills missing files)
        copyRecursively(data.resolve("assets"), assets, Set.of("README.txt"));

        // 2. pack.mcmeta + icon
        writePackMeta(root, cfg);
        writePackIcon(data, root);

        // 3. font (glyphs + spaces)
        Map<String, Glyph> glyphs = plugin.designs().glyphs();
        glyphAdvances = writeFont(assetsRoot, ns, Key.key(plugin.settings().packFont), glyphs, warnings);

        // 4. item models
        int models = writeItemModels(assets, ns, collectItemModels(ns));

        // 5. transparent boss bar
        writeTransparentBossBar(assetsRoot, plugin.settings().transparentBossBarColor);

        // 6. sounds
        int sounds = writeSounds(assets, ns, cfg.getConfigurationSection("sounds"), data.resolve("assets/sounds"), warnings);

        // 7. zip + hash (kept in memory for the built-in HTTP server)
        Path zip = data.resolve("resourcepack.zip");
        byte[] bytes = zip(root);
        Files.write(zip, bytes);
        String sha1 = sha1(bytes);
        lastZip = bytes;
        lastHash = sha1;

        // 8. Oraxen
        if (plugin.settings().oraxenMerge) {
            if (plugin.hooks().oraxen() == null) {
                warnings.add("oraxen.mode is MERGE but Oraxen is not installed/hooked.");
            } else {
                Path target = plugin.hooks().oraxen().packFolder().toPath().resolve("assets");
                deleteRecursively(target.resolve(ns));
                copyRecursively(assetsRoot, target, Set.of());
                if (cfg.getBoolean("oraxen.reload-after-sync", true)) {
                    warnings.add("Assets merged into Oraxen; Oraxen pack reload requested.");
                    plugin.runSync(() -> plugin.hooks().oraxen().reloadPack());
                }
            }
        }
        return new Result(root, zip, sha1, glyphs.size(), models, sounds, warnings);
    }

    private void writePackMeta(Path root, ConfigurationSection cfg) throws IOException {
        int format = cfg.getInt("pack-format", 46);
        int min = Math.min(format, cfg.getInt("min-format", 34));
        int max = Math.max(format, cfg.getInt("max-format", 999));
        JsonObject pack = new JsonObject();
        pack.addProperty("pack_format", format);
        // < 1.21.9 clients
        JsonObject supported = new JsonObject();
        supported.addProperty("min_inclusive", min);
        supported.addProperty("max_inclusive", max);
        pack.add("supported_formats", supported);
        // 1.21.9+ / 26.x clients (must match supported_formats when min_format < 65)
        pack.addProperty("min_format", min);
        pack.addProperty("max_format", max);
        String description = cfg.getString("description", "RadiantAchievements");
        pack.add("description", JsonParser.parseString(GsonComponentSerializer.gson().serialize(Text.parse(description))));
        JsonObject meta = new JsonObject();
        meta.add("pack", pack);
        Files.writeString(root.resolve("pack.mcmeta"), GSON.toJson(meta), StandardCharsets.UTF_8);
    }

    /** User pack.png, otherwise the bundled logo scaled to 128x128. */
    private void writePackIcon(Path data, Path root) throws IOException {
        Path custom = data.resolve("pack.png");
        if (Files.exists(custom)) {
            Files.copy(custom, root.resolve("pack.png"), StandardCopyOption.REPLACE_EXISTING);
            return;
        }
        try (InputStream in = plugin.getResource("pack/pack.png")) {
            if (in == null) {
                return;
            }
            BufferedImage source = ImageIO.read(in);
            if (source == null) {
                return;
            }
            BufferedImage icon = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
            var g = icon.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.drawImage(source.getScaledInstance(128, 128, Image.SCALE_SMOOTH), 0, 0, null);
            g.dispose();
            writePng(root.resolve("pack.png"), icon);
        }
    }

    /**
     * Writes the font json and paints missing glyph textures.
     *
     * @return measured advance of each glyph (vanilla rule: rightmost opaque column * scale, rounded, + 1)
     */
    private Map<String, Integer> writeFont(Path assetsRoot, String ns, Key fontKey, Map<String, Glyph> glyphs,
                                           List<String> warnings) throws IOException {
        Path texturesRoot = assetsRoot.resolve(ns).resolve("textures");
        JsonArray providers = new JsonArray();
        JsonObject space = new JsonObject();
        space.addProperty("type", "space");
        JsonObject advances = new JsonObject();
        for (Map.Entry<String, Integer> e : Text.spaceAdvances().entrySet()) {
            advances.addProperty(e.getKey(), e.getValue());
        }
        space.add("advances", advances);
        providers.add(space);

        Map<String, Integer> measured = new HashMap<>();
        Set<String> usedChars = new LinkedHashSet<>();
        for (Glyph g : glyphs.values()) {
            if (!usedChars.add(g.chars())) {
                warnings.add("Glyph '" + g.name() + "' reuses a char already used by another glyph.");
            }
            String texture = g.texture().replace('\\', '/');
            if (!texture.endsWith(".png")) {
                texture = texture + ".png";
            }
            Path png = texturesRoot.resolve(texture);
            if (!Files.exists(png)) {
                BufferedImage painted = TexturePainter.paint(texture);
                if (painted == null) {
                    int height = Math.max(8, g.height());
                    painted = TexturePainter.placeholder(height, height, parseColor(g.color()));
                    warnings.add("Glyph '" + g.name() + "': no texture found, placeholder generated (" + texture + ").");
                }
                writePng(png, painted);
            }
            BufferedImage image = ImageIO.read(png.toFile());
            if (image != null) {
                if (g.ascent() > g.height()) {
                    warnings.add("Glyph '" + g.name() + "': ascent must be <= height, clamped.");
                }
                measured.put(g.name(), advance(image, g.height()));
            }
            JsonObject bitmap = new JsonObject();
            bitmap.addProperty("type", "bitmap");
            bitmap.addProperty("file", ns + ":" + texture);
            bitmap.addProperty("height", g.height());
            bitmap.addProperty("ascent", Math.min(g.ascent(), g.height()));
            JsonArray chars = new JsonArray();
            chars.add(g.chars());
            bitmap.add("chars", chars);
            providers.add(bitmap);
        }
        JsonObject font = new JsonObject();
        font.add("providers", providers);
        Path file = assetsRoot.resolve(fontKey.namespace()).resolve("font").resolve(fontKey.value() + ".json");
        Files.createDirectories(file.getParent());
        Files.writeString(file, GSON.toJson(font), StandardCharsets.UTF_8);
        return Map.copyOf(measured);
    }

    /** Mirrors vanilla: advance = round(actualWidth * height / imageHeight) + 1. */
    private static int advance(BufferedImage image, int height) {
        int actual = 0;
        for (int x = image.getWidth() - 1; x >= 0 && actual == 0; x--) {
            for (int y = 0; y < image.getHeight(); y++) {
                if ((image.getRGB(x, y) >>> 24) != 0) {
                    actual = x + 1;
                    break;
                }
            }
        }
        double scale = height / (double) image.getHeight();
        return (int) (0.5 + actual * scale) + 1;
    }

    private Set<String> collectItemModels(String ns) {
        Set<String> paths = new LinkedHashSet<>();
        List<IconSpec> icons = new ArrayList<>();
        for (Design d : plugin.designs().designs().values()) {
            icons.add(d.icon());
        }
        for (Achievement a : plugin.achievements().all()) {
            if (a.icon() != null) {
                icons.add(a.icon());
            }
            for (Reward r : a.rewards()) {
                if (r instanceof Reward.Item item) {
                    icons.add(item.icon());
                }
            }
        }
        ConfigurationSection guiIcons = plugin.getConfig().getConfigurationSection("gui.icons");
        if (guiIcons != null) {
            for (String key : guiIcons.getKeys(false)) {
                icons.add(IconSpec.fromSection(guiIcons.getConfigurationSection(key), IconSpec.DEFAULT));
            }
        }
        for (IconSpec icon : icons) {
            if (icon.hasItemModel()) {
                String model = icon.itemModel().toLowerCase(Locale.ROOT);
                if (model.startsWith(ns + ":")) {
                    paths.add(model.substring(ns.length() + 1));
                }
            }
        }
        return paths;
    }

    private int writeItemModels(Path assets, String ns, Set<String> paths) throws IOException {
        for (String path : paths) {
            Path itemDef = assets.resolve("items").resolve(path + ".json");
            if (!Files.exists(itemDef)) {
                JsonObject model = new JsonObject();
                model.addProperty("type", "minecraft:model");
                model.addProperty("model", ns + ":item/" + path);
                JsonObject root = new JsonObject();
                root.add("model", model);
                Files.createDirectories(itemDef.getParent());
                Files.writeString(itemDef, GSON.toJson(root), StandardCharsets.UTF_8);
            }
            Path modelFile = assets.resolve("models/item").resolve(path + ".json");
            if (!Files.exists(modelFile)) {
                JsonObject textures = new JsonObject();
                textures.addProperty("layer0", ns + ":item/" + path);
                JsonObject model = new JsonObject();
                model.addProperty("parent", "minecraft:item/generated");
                model.add("textures", textures);
                Files.createDirectories(modelFile.getParent());
                Files.writeString(modelFile, GSON.toJson(model), StandardCharsets.UTF_8);
            }
            Path texture = assets.resolve("textures/item").resolve(path + ".png");
            if (!Files.exists(texture)) {
                BufferedImage painted = TexturePainter.paint("item/" + path + ".png");
                writePng(texture, painted != null ? painted : TexturePainter.placeholder(16, 16, 0xFFE0A93A));
            }
        }
        return paths.size();
    }

    /** Transparent background/progress sprites so that boss bars of this colour only show their title. */
    private static void writeTransparentBossBar(Path assetsRoot, String color) throws IOException {
        if (color == null || color.isBlank() || color.equals("NONE")) {
            return;
        }
        String name = color.toLowerCase(Locale.ROOT);
        Path dir = assetsRoot.resolve("minecraft/textures/gui/sprites/boss_bar");
        BufferedImage empty = new BufferedImage(182, 5, BufferedImage.TYPE_INT_ARGB);
        writePng(dir.resolve(name + "_background.png"), empty);
        writePng(dir.resolve(name + "_progress.png"), empty);
    }

    private int writeSounds(Path assets, String ns, ConfigurationSection section, Path soundSource,
                            List<String> warnings) throws IOException {
        if (section == null || section.getKeys(false).isEmpty()) {
            return 0;
        }
        JsonObject root = new JsonObject();
        for (String key : section.getKeys(false)) {
            ConfigurationSection s = section.getConfigurationSection(key);
            if (s == null) {
                continue;
            }
            JsonArray files = new JsonArray();
            for (String file : s.getStringList("files")) {
                String clean = file.replace('\\', '/').replaceAll("\\.ogg$", "");
                if (!Files.exists(soundSource.resolve(clean + ".ogg"))) {
                    warnings.add("Sound '" + ns + ":" + key + "': missing assets/sounds/" + clean + ".ogg (skipped)");
                    continue;
                }
                JsonObject entry = new JsonObject();
                entry.addProperty("name", ns + ":" + clean);
                entry.addProperty("stream", s.getBoolean("stream", false));
                if (s.contains("volume")) {
                    entry.addProperty("volume", s.getDouble("volume"));
                }
                if (s.contains("pitch")) {
                    entry.addProperty("pitch", s.getDouble("pitch"));
                }
                files.add(entry);
            }
            if (files.isEmpty()) {
                continue;
            }
            JsonObject event = new JsonObject();
            event.add("sounds", files);
            if (s.contains("subtitle")) {
                event.addProperty("subtitle", s.getString("subtitle"));
            }
            root.add(key, event);
        }
        if (root.isEmpty()) {
            return 0;
        }
        Files.writeString(assets.resolve("sounds.json"), GSON.toJson(root), StandardCharsets.UTF_8);
        return root.size();
    }

    private static int parseColor(String hex) {
        try {
            return 0xFF000000 | Integer.parseInt(hex.replace("#", ""), 16);
        } catch (NumberFormatException ex) {
            return 0xFFE0A93A;
        }
    }

    private static void writePng(Path file, BufferedImage image) throws IOException {
        Files.createDirectories(file.getParent());
        try (OutputStream out = Files.newOutputStream(file)) {
            ImageIO.write(image, "png", out);
        }
    }

    private static byte[] zip(Path folder) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZipOutputStream out = new ZipOutputStream(buffer);
             Stream<Path> files = Files.walk(folder)) {
            for (Path file : (Iterable<Path>) files.filter(Files::isRegularFile).sorted()::iterator) {
                ZipEntry entry = new ZipEntry(folder.relativize(file).toString().replace('\\', '/'));
                entry.setTime(0L);
                out.putNextEntry(entry);
                Files.copy(file, out);
                out.closeEntry();
            }
        }
        return buffer.toByteArray();
    }

    private static String sha1(byte[] bytes) throws IOException {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new IOException(ex);
        }
    }

    private static void copyRecursively(Path from, Path to, Set<String> skipNames) throws IOException {
        if (!Files.isDirectory(from)) {
            return;
        }
        Files.walkFileTree(from, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Files.createDirectories(to.resolve(from.relativize(dir).toString()));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (!skipNames.contains(file.getFileName().toString())) {
                    Files.copy(file, to.resolve(from.relativize(file).toString()), StandardCopyOption.REPLACE_EXISTING);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        Files.walkFileTree(path, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
