package dev.privalo.achievements.display;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Sends an advancement toast to ONE player through a {@code ClientboundUpdateAdvancementsPacket}, without
 * registering anything on the server (no global advancement reload).
 * <p>
 * Uses reflection against the Mojang-mapped server classes that Paper exposes at runtime since 1.20.5
 * (handles {@code ResourceLocation} → {@code Identifier} renames and both packet constructor shapes).
 * {@link #available()} turns false on the first incompatibility so callers can fall back.
 */
final class PacketToast {

    private boolean initialised;
    private boolean available;

    private Method getHandle;
    private Field connectionField;
    private Method send;
    private Object registryOps;
    private Object advancementCodec;
    private Method parse;
    private Method dataResultResult;
    private Constructor<?> holderConstructor;
    private Method idFactory;
    private Method requirementsAccessor;
    private Constructor<?> progressConstructor;
    private Method progressUpdate;
    private Method grantProgress;
    private Constructor<?> packetConstructor;

    boolean available() {
        init();
        return available;
    }

    private synchronized void init() {
        if (initialised) {
            return;
        }
        initialised = true;
        try {
            Class<?> serverClass = Class.forName("net.minecraft.server.MinecraftServer");
            Object server = serverClass.getMethod("getServer").invoke(null);
            Object registryAccess = serverClass.getMethod("registryAccess").invoke(server);

            Class<?> dynamicOps = Class.forName("com.mojang.serialization.DynamicOps");
            Object jsonOps = Class.forName("com.mojang.serialization.JsonOps").getField("INSTANCE").get(null);
            Class<?> registryOpsClass = Class.forName("net.minecraft.resources.RegistryOps");
            Method create = null;
            for (Method m : registryOpsClass.getMethods()) {
                if (m.getName().equals("create") && Modifier.isStatic(m.getModifiers()) && m.getParameterCount() == 2
                        && m.getParameterTypes()[0] == dynamicOps && m.getParameterTypes()[1].isInstance(registryAccess)) {
                    create = m;
                }
            }
            if (create == null) {
                throw new NoSuchMethodException("RegistryOps.create");
            }
            registryOps = create.invoke(null, jsonOps, registryAccess);

            Class<?> advancementClass = Class.forName("net.minecraft.advancements.Advancement");
            advancementCodec = advancementClass.getField("CODEC").get(null);
            parse = Class.forName("com.mojang.serialization.Decoder").getMethod("parse", dynamicOps, Object.class);
            dataResultResult = Class.forName("com.mojang.serialization.DataResult").getMethod("result");
            requirementsAccessor = advancementClass.getMethod("requirements");

            Class<?> idClass = firstClass("net.minecraft.resources.Identifier", "net.minecraft.resources.ResourceLocation");
            idFactory = idClass.getMethod("fromNamespaceAndPath", String.class, String.class);

            Class<?> holderClass = Class.forName("net.minecraft.advancements.AdvancementHolder");
            holderConstructor = holderClass.getConstructor(idClass, advancementClass);

            Class<?> progressClass = Class.forName("net.minecraft.advancements.AdvancementProgress");
            progressConstructor = progressClass.getConstructor();
            progressUpdate = progressClass.getMethod("update", requirementsAccessor.getReturnType());
            grantProgress = progressClass.getMethod("grantProgress", String.class);

            Class<?> packetClass = Class.forName("net.minecraft.network.protocol.game.ClientboundUpdateAdvancementsPacket");
            for (Constructor<?> c : packetClass.getConstructors()) {
                Class<?>[] p = c.getParameterTypes();
                if ((p.length == 4 || p.length == 5) && p[0] == boolean.class && Collection.class.isAssignableFrom(p[1])
                        && Set.class.isAssignableFrom(p[2]) && Map.class.isAssignableFrom(p[3])) {
                    packetConstructor = c;
                }
            }
            if (packetConstructor == null) {
                throw new NoSuchMethodException("ClientboundUpdateAdvancementsPacket(boolean, Collection, Set, Map[, boolean])");
            }

            Player any = Bukkit.getOnlinePlayers().stream().findFirst().orElse(null);
            Class<?> craftPlayer = any != null ? any.getClass()
                    : Class.forName(Bukkit.getServer().getClass().getPackageName() + ".entity.CraftPlayer");
            getHandle = craftPlayer.getMethod("getHandle");
            Class<?> serverPlayer = Class.forName("net.minecraft.server.level.ServerPlayer");
            connectionField = serverPlayer.getField("connection");
            Class<?> packetInterface = Class.forName("net.minecraft.network.protocol.Packet");
            send = findSend(connectionField.getType(), packetInterface);
            available = true;
        } catch (Throwable ex) {
            available = false;
        }
    }

    private static Class<?> firstClass(String... names) throws ClassNotFoundException {
        for (String name : names) {
            try {
                return Class.forName(name);
            } catch (ClassNotFoundException ignored) {
                // try next name
            }
        }
        throw new ClassNotFoundException(String.join(" / ", names));
    }

    private static Method findSend(Class<?> type, Class<?> packetInterface) throws NoSuchMethodException {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            try {
                return c.getMethod("send", packetInterface);
            } catch (NoSuchMethodException ignored) {
                // look at the superclass
            }
        }
        throw new NoSuchMethodException("send(Packet)");
    }

    /**
     * Shows the toast and schedules nothing: the caller must call {@link #remove} a few ticks later.
     *
     * @param json advancement json (datapack format) with an "impossible" criterion named "trigger"
     */
    void show(Player player, String namespace, String path, String json) throws ReflectiveOperationException {
        Object id = idFactory.invoke(null, namespace, path);
        JsonElement element = JsonParser.parseString(json);
        Object dataResult = parse.invoke(advancementCodec, registryOps, element);
        java.util.Optional<?> parsed = (java.util.Optional<?>) dataResultResult.invoke(dataResult);
        if (parsed.isEmpty()) {
            throw new IllegalArgumentException("Invalid toast advancement: " + dataResult);
        }
        Object advancement = parsed.get();
        Object holder = holderConstructor.newInstance(id, advancement);
        Object progress = progressConstructor.newInstance();
        progressUpdate.invoke(progress, requirementsAccessor.invoke(advancement));
        grantProgress.invoke(progress, "trigger");
        sendPacket(player, List.of(holder), Set.of(), Map.of(id, progress));
    }

    /** Removes the temporary advancement from the client. */
    void remove(Player player, String namespace, String path) throws ReflectiveOperationException {
        Object id = idFactory.invoke(null, namespace, path);
        sendPacket(player, List.of(), Set.of(id), Map.of());
    }

    private void sendPacket(Player player, Collection<?> added, Set<?> removed, Map<?, ?> progress)
            throws ReflectiveOperationException {
        Object packet = packetConstructor.getParameterCount() == 5
                ? packetConstructor.newInstance(false, added, removed, progress, true)
                : packetConstructor.newInstance(false, added, removed, progress);
        Object handle = getHandle.invoke(player);
        Object connection = connectionField.get(handle);
        send.invoke(connection, packet);
    }
}
