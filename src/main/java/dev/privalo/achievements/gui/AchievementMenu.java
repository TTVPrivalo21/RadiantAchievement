package dev.privalo.achievements.gui;

import dev.privalo.achievements.RadiantAchievements;
import dev.privalo.achievements.data.PlayerData;
import dev.privalo.achievements.model.Achievement;
import dev.privalo.achievements.model.Design;
import dev.privalo.achievements.model.IconSpec;
import dev.privalo.achievements.util.Items;
import dev.privalo.achievements.util.Placeholders;
import dev.privalo.achievements.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.NotNull;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 54 slot achievement menu drawn over a custom background glyph ({@code gui_achievements_<filter>}).
 * <pre>
 *  row 0-4 : [border] [7 achievements] [border]
 *  row 5   : [prev] [info] [ ] [all] [unlocked] [pending] [ ] [ ] [next]
 * </pre>
 * The viewer may differ from the player whose data is displayed.
 */
public final class AchievementMenu implements InventoryHolder {

    /** Menu filter. */
    public enum Filter {
        ALL, UNLOCKED, PENDING;

        String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** Clickable areas. */
    enum Button { PREVIOUS, NEXT, FILTER_ALL, FILTER_UNLOCKED, FILTER_PENDING, NONE }

    public static final int SIZE = 54;
    private static final int[] CONTENT_SLOTS = buildContentSlots();
    private static final int PREVIOUS = 45;
    private static final int INFO = 46;
    private static final int TAB_ALL = 48;
    private static final int TAB_UNLOCKED = 49;
    private static final int TAB_PENDING = 50;
    private static final int NEXT = 53;

    private final RadiantAchievements plugin;
    private final Player viewer;
    private final PlayerData data;
    private Filter filter;
    private List<Achievement> entries;
    private int page;
    private Inventory inventory;

    public AchievementMenu(RadiantAchievements plugin, Player viewer, PlayerData data) {
        this(plugin, viewer, data, Filter.ALL, 0);
    }

    private AchievementMenu(RadiantAchievements plugin, Player viewer, PlayerData data, Filter filter, int page) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.data = data;
        this.filter = filter;
        this.entries = filtered();
        this.page = Math.max(0, Math.min(pages() - 1, page));
    }

    private static int[] buildContentSlots() {
        int[] slots = new int[35];
        int i = 0;
        for (int row = 0; row < 5; row++) {
            for (int col = 1; col <= 7; col++) {
                slots[i++] = row * 9 + col;
            }
        }
        return slots;
    }

    private List<Achievement> filtered() {
        List<Achievement> list = new ArrayList<>();
        for (Achievement a : plugin.achievements().all()) {
            boolean unlocked = data.isUnlocked(a.id());
            if (filter == Filter.ALL || (filter == Filter.UNLOCKED) == unlocked) {
                list.add(a);
            }
        }
        return list;
    }

    public void open() {
        inventory = Bukkit.createInventory(this, SIZE, title());
        render();
        viewer.openInventory(inventory);
    }

    public int pages() {
        return Math.max(1, (entries.size() + CONTENT_SLOTS.length - 1) / CONTENT_SLOTS.length);
    }

    Button button(int slot) {
        return switch (slot) {
            case PREVIOUS -> page > 0 ? Button.PREVIOUS : Button.NONE;
            case NEXT -> page < pages() - 1 ? Button.NEXT : Button.NONE;
            case TAB_ALL -> Button.FILTER_ALL;
            case TAB_UNLOCKED -> Button.FILTER_UNLOCKED;
            case TAB_PENDING -> Button.FILTER_PENDING;
            default -> Button.NONE;
        };
    }

    /** Handles a click on the top inventory (main thread). */
    void click(int slot) {
        Button b = button(slot);
        switch (b) {
            case PREVIOUS -> {
                page--;
                render();
                clickSound();
            }
            case NEXT -> {
                page++;
                render();
                clickSound();
            }
            case FILTER_ALL, FILTER_UNLOCKED, FILTER_PENDING -> {
                Filter next = b == Button.FILTER_ALL ? Filter.ALL : b == Button.FILTER_UNLOCKED ? Filter.UNLOCKED : Filter.PENDING;
                if (next != filter) {
                    clickSound();
                    // The title (background glyph) changes, so the inventory must be reopened next tick.
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (viewer.isOnline()) {
                            new AchievementMenu(plugin, viewer, data, next, 0).open();
                        }
                    });
                }
            }
            default -> {
                // decorative or empty slot
            }
        }
    }

    private void clickSound() {
        viewer.playSound(viewer.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
    }

    private ConfigurationSection gui() {
        return plugin.settings().section("gui");
    }

    private String filterName() {
        return gui().getString("filter-names." + filter.key(), filter.name());
    }

    private TagResolver summary() {
        int unlocked = plugin.manager().unlockedCount(data);
        String background = "gui_achievements_" + filter.key();
        String backgroundTag = Text.hasGlyph(background) ? "<space:-8><overlay:" + background + "><space:8>" : "";
        return TagResolver.resolver(
                Placeholder.unparsed("player", data.name()),
                Placeholder.unparsed("unlocked", String.valueOf(unlocked)),
                Placeholder.unparsed("total", String.valueOf(plugin.achievements().size())),
                Placeholder.unparsed("percent", String.format(Locale.ROOT, "%.1f", plugin.manager().completion(data))),
                Placeholder.unparsed("page", String.valueOf(page + 1)),
                Placeholder.unparsed("pages", String.valueOf(pages())),
                Placeholder.unparsed("filter", filterName()),
                Placeholder.parsed("background", backgroundTag));
    }

    private Component title() {
        return Text.parse(viewer, gui().getString("title", "<background>Achievements"), summary());
    }

    private void render() {
        inventory.clear();
        TagResolver ctx = summary();
        int start = page * CONTENT_SLOTS.length;
        for (int i = 0; i < CONTENT_SLOTS.length && start + i < entries.size(); i++) {
            inventory.setItem(CONTENT_SLOTS[i], achievementItem(entries.get(start + i)));
        }
        if (entries.isEmpty()) {
            inventory.setItem(22, named(icon("empty", Material.BARRIER), gui().getString("empty-name", "<gray>Nada por aquí"),
                    gui().getStringList("empty-lore"), ctx, false));
        }
        ItemStack border = named(icon("border", Material.BLACK_STAINED_GLASS_PANE), " ", List.of(), ctx, false);
        for (int row = 0; row < 5; row++) {
            inventory.setItem(row * 9, border);
            inventory.setItem(row * 9 + 8, border);
        }
        for (int slot : new int[]{47, 51, 52}) {
            inventory.setItem(slot, border);
        }
        inventory.setItem(PREVIOUS, page > 0
                ? named(icon("previous", Material.ARROW), gui().getString("previous-page", "<"), List.of(), ctx, false)
                : border);
        inventory.setItem(NEXT, page < pages() - 1
                ? named(icon("next", Material.ARROW), gui().getString("next-page", ">"), List.of(), ctx, false)
                : border);
        inventory.setItem(TAB_ALL, tab(Filter.ALL, "tab-all", Material.BOOK, ctx));
        inventory.setItem(TAB_UNLOCKED, tab(Filter.UNLOCKED, "tab-unlocked", Material.LIME_DYE, ctx));
        inventory.setItem(TAB_PENDING, tab(Filter.PENDING, "tab-pending", Material.CLOCK, ctx));

        ItemStack info = named(new ItemStack(Material.PLAYER_HEAD), gui().getString("info-name", "<player>"),
                gui().getStringList("info-lore"), ctx, false);
        if (info.getItemMeta() instanceof SkullMeta skull) {
            skull.setOwningPlayer(Bukkit.getOfflinePlayer(data.uuid()));
            info.setItemMeta(skull);
        }
        inventory.setItem(INFO, info);
    }

    private ItemStack tab(Filter target, String iconKey, Material fallback, TagResolver ctx) {
        boolean active = target == filter;
        String name = gui().getString("filter-names." + target.key(), target.name());
        String format = gui().getString(active ? "tab-active" : "tab-inactive", "<name>");
        TagResolver tabCtx = TagResolver.resolver(ctx, Placeholder.parsed("name", name));
        return named(icon(iconKey, fallback), format, gui().getStringList("tab-lore"), tabCtx, active);
    }

    /** Item from gui.icons.&lt;key&gt; (material + item-model), with a fallback material. */
    private ItemStack icon(String key, Material fallback) {
        ConfigurationSection s = gui().getConfigurationSection("icons." + key);
        IconSpec spec = s == null ? new IconSpec(fallback.name(), 0, "", "")
                : IconSpec.fromSection(s, new IconSpec(fallback.name(), 0, "", ""));
        return Items.build(plugin, spec, 1);
    }

    private ItemStack named(ItemStack stack, String name, List<String> lore, TagResolver ctx, boolean glint) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        meta.displayName(Text.item(viewer, name, ctx));
        if (!lore.isEmpty()) {
            meta.lore(Text.items(viewer, lore, ctx));
        }
        Items.hideExtras(meta);
        Items.glint(meta, glint);
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack achievementItem(Achievement a) {
        boolean unlocked = data.isUnlocked(a.id());
        long progress = unlocked ? a.target() : data.progress(a.id());
        Design design = plugin.designs().get(a.designId());
        String date = unlocked
                ? new SimpleDateFormat(gui().getString("date-format", "dd/MM/yyyy HH:mm")).format(new Date(data.unlockedAt(a.id())))
                : "";
        StringBuilder requires = new StringBuilder();
        if (!unlocked) {
            for (String req : a.requires()) {
                Achievement r = plugin.achievements().get(req);
                if (r != null && !data.isUnlocked(req)) {
                    requires.append(requires.isEmpty() ? "" : "<gray>, ").append("<white>").append(r.name());
                }
            }
        }
        TagResolver ctx = TagResolver.resolver(Placeholders.achievement(a, data.name(), progress, design.id()),
                Placeholder.unparsed("date", date),
                Placeholder.parsed("requires", requires.toString()));

        String state;
        ItemStack stack;
        if (unlocked) {
            state = "unlocked";
            stack = Items.build(plugin, a.icon() != null ? a.icon() : design.icon(), 1);
        } else if (a.secret()) {
            state = "secret";
            stack = icon("secret", Material.BLACK_DYE);
        } else if (progress > 0) {
            state = "in-progress";
            stack = icon("in-progress", Material.ORANGE_DYE);
        } else {
            state = "locked";
            stack = icon("locked", Material.GRAY_DYE);
        }
        String name = gui().getString("states." + state + ".name", "<achievement_name>");
        List<String> lore = new ArrayList<>(gui().getStringList("states." + state + ".lore"));
        if (!"secret".equals(state) && !requires.isEmpty()) {
            lore.addAll(gui().getStringList("requires-lore"));
        }
        return named(stack, name, lore, ctx, unlocked);
    }

    @Override
    public @NotNull Inventory getInventory() {
        if (inventory == null) {
            inventory = Bukkit.createInventory(this, SIZE, title());
            render();
        }
        return inventory;
    }
}
