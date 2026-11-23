# RadiantAchievements

Sistema completo de **logros configurables** para servidores **Paper 1.21.x – 26.x** (Java 21+): diseños visuales con texturas propias, toasts reales, bossbars, banners centrales, sonidos custom, anuncios en chat con o sin diseño, recompensas, progreso persistente (YAML / SQLite / MySQL), generador automático de resource pack, integraciones y API pública.

> **Plataforma:** requiere **Paper** o un fork de Paper (Purpur, Pufferfish…). Usa la API nativa de Adventure/MiniMessage, que Spigot puro no trae; en Spigot el plugin se desactiva con un mensaje claro.

---

## Instalación

1. Compila con `mvn clean package` (o `bash build-local.sh` sin Maven) → `target/RadiantAchievements-1.0.0.jar`.
2. Copia el jar a `plugins/` y arranca el servidor.
3. Se generan `config.yml`, `designs.yml`, `achievements.yml`, la carpeta `assets/` y el pack en `resourcepack/` (+ `resourcepack.zip`).
4. Al entrar, cada jugador recibe el pack automáticamente desde el **servidor HTTP integrado** (puerto `8164`). No hace falta hosting externo.
5. Edita los archivos y ejecuta `/ach reload` (y `/ach pack` si cambiaste glifos, iconos o sonidos: regenera y reenvía el pack).

> En un servidor remoto abre el puerto TCP `resource-pack.http.port` en el firewall/panel del host. Detrás de un proxy (Velocity/Bungee) pon tu dominio en `resource-pack.http.public-address`.

Si `config.yml` o `designs.yml` son de una versión anterior (`config-version`), se guardan como `*.v1.old` y se crean los nuevos.

Dependencias opcionales (se detectan solas): PlaceholderAPI, Vault, WorldGuard, MythicMobs, Citizens, Oraxen.
HikariCP (para SQLite/MySQL) se descarga automáticamente mediante `libraries:` en `plugin.yml`.

---

## `designs.yml`

### Glifos (sprites de fuente)

```yaml
glyphs:
  card_rare: { char: "", texture: "font/card_rare.png", height: 16, ascent: 10 }
```

| Tag | Uso |
|---|---|
| `<glyph:card_rare>` | Inserta el sprite |
| `<overlay:gui_achievements_all>` | Inserta el sprite y vuelve atrás (ancho 0, para fondos) |
| `<space:-40>` / `<space:12>` | Mueve el cursor en píxeles |

Los glifos van en la fuente `minecraft:default` (configurable con `resource-pack.font`), así funcionan en chat, títulos, bossbars, títulos de inventario y PlaceholderAPI.

**Texturas pixel-art incluidas** (se pintan solas si no pones tu PNG en `plugins/RadiantAchievements/assets/textures/`):

| Glifo | Tamaño | Uso |
|---|---|---|
| `pip_<rareza>` | 9×9 | insignia para chat |
| `badge_<rareza>` | 16×16 | medalla (icono en tarjetas y `item-model: radiant:badge/<rareza>`) |
| `card_<rareza>` | 240×16 | tarjeta compacta del bossbar |
| `banner_<rareza>` | 96×16 | cinta para `CENTER_BANNER` |
| `gui_achievements_<all\|unlocked\|pending>` | 256×256 | fondo del menú `/ach` |

Rarezas: `common`, `rare`, `epic`, `secret`, `event`.

### Diseños

```yaml
designs:
  rare:
    style: BOSSBAR_OVERLAY    # TOAST | BOSSBAR_OVERLAY | ACTIONBAR | TITLE | CENTER_BANNER
    duration-ticks: 120
    frame: GOAL               # marco del toast: TASK | GOAL | CHALLENGE
    title: "<gradient:#9bd4ff:#3f8fe0><b><achievement_name></b></gradient>"
    description: "<#9aa6b8><achievement_description>"
    icon:
      material: DIAMOND
      item-model: "radiant:badge/rare"   # 1.21.2+ (modelo y textura generados en el pack)
      custom-model-data: 0
      oraxen: ""                         # id de ítem de Oraxen (opcional)
    bossbar:
      card: card_rare         # tarjeta de fondo
      icon: badge_rare        # icono a la izquierda
      hide-bar: true          # barra vanilla invisible
    sound:
      - { sound: "radiant:achievement.rare", volume: 1.0, pitch: 1.0 }
      - { sound: "minecraft:entity.player.levelup", volume: 0.6, pitch: 0.8, delay-ticks: 6 }
    chat:
      mode: DESIGNED          # DESIGNED (banner con hover) | COMPACT (una línea) | NONE
      scope: PUBLIC           # PUBLIC (todo el servidor) | PRIVATE (solo el jugador)
      compact: "..."
      designed: [ "...", "..." ]
```

| Estilo | Qué muestra |
|---|---|
| `TOAST` | Toast nativo arriba a la derecha con icono personalizado. Se envía **por paquete solo al jugador** (sin registrar avances ni recargar los de nadie); si el servidor no lo permite usa la API de Bukkit y, como último recurso, `notifications.toast-fallback`. |
| `BOSSBAR_OVERLAY` | Tarjeta compacta arriba al centro: fondo `card`, icono `icon` y título + descripción encima. Con `hide-bar: true` la barra vanilla es invisible (color `resource-pack.transparent-bossbar-color`, por defecto `WHITE`). |
| `ACTIONBAR` | Título + subtítulo sobre la hotbar. |
| `TITLE` | Título y subtítulo clásicos. |
| `CENTER_BANNER` | Cinta central (glifo en el título) + subtítulo + descripción en la actionbar. |

> El color transparente afecta a **todas** las bossbars de ese color (también las de otros plugins). Elige uno que no uses o pon `NONE`.

Placeholders disponibles en diseños, chat y recompensas: `<player> <achievement_name> <achievement_description> <achievement_id> <category> <progress> <target> <percent> <bar> <design>` + placeholders de PlaceholderAPI (`%...%`).

Las notificaciones de un mismo jugador se ponen en cola para no solaparse.


---

## `achievements.yml`

```yaml
achievements:
  diamond_hunter:
    name: "Cazador de diamantes"
    description: "Mina 10 menas de diamante."
    design: rare
    category: mining
    order: 11
    secret: false
    requires: [miner_100]
    icon: { material: DIAMOND_ORE }
    trigger:
      type: BLOCK_BREAK
      target: 10                       # 1 = instantáneo, N = acumulativo
      filter: [DIAMOND_ORE, DEEPSLATE_DIAMOND_ORE]
      worlds: [world]                  # opcional
    announce: { mode: COMPACT, scope: PRIVATE }   # opcional, sobrescribe el diseño
    sound: { sound: "radiant:achievement.rare" }  # opcional, sobrescribe el diseño
    rewards:
      - { type: money, amount: 500 }
      - { type: console-command, command: "give <player> diamond 3" }
```

### Triggers

| Tipo | Progreso | `filter` |
|---|---|---|
| `JOIN` | 1 al entrar | — |
| `BLOCK_BREAK` / `BLOCK_PLACE` | bloques | materiales |
| `KILL_MOB` | mobs | tipos de entidad |
| `KILL_PLAYER` | jugadores | nombres de víctima |
| `CRAFT` | ítems crafteados (incluye shift-click) | materiales |
| `FISH` | capturas | materiales |
| `CONSUME` | ítems consumidos | materiales |
| `PLAYTIME` | **minutos** jugados | — |
| `DISTANCE` | **bloques** recorridos | — |
| `LOCATION` | entradas a un área (`location: {world,x,y,z,radius,ignore-y}`) | — |
| `REGION_ENTER` | entradas a regiones (WorldGuard) | ids de región |
| `COMMAND` | comandos ejecutados | prefijos (`/rules`) |
| `PERMISSION` | se desbloquea al tener `permission:` | — |
| `LEVEL_REACH` | nivel de XP más alto alcanzado | — |
| `DEATH` | muertes | causas de daño (`FALL`, `LAVA`…) |
| `MYTHIC_KILL` | mobs de MythicMobs | nombres internos |
| `NPC_INTERACT` | clics a NPCs de Citizens | ids o nombres |
| `MANUAL` | solo comandos/API | — |

`tracking.ignore-creative` y `tracking.disabled-worlds` en `config.yml` limitan dónde se cuenta progreso.
Si un logro alcanza su objetivo pero le faltan `requires`, se desbloquea automáticamente al cumplirlos.

### Recompensas

`console-command`, `player-command`, `money` (Vault), `xp` (`levels: true` para niveles), `item` (material, amount, name, lore, custom-model-data, item-model, oraxen), `message`, `broadcast`, `effect` (effect, duration-ticks, amplifier), `firework` (color, power; nunca hace daño).

---

## Resource pack

`/ach pack` (y cada arranque) genera `plugins/RadiantAchievements/resourcepack/` + `resourcepack.zip` y **lo reenvía a los jugadores conectados**:

- `pack.mcmeta` con `pack_format`, `supported_formats` y `min_format`/`max_format` (1.21 → 26.x) y `pack.png` (logo incluido o tu `pack.png`).
- `assets/minecraft/font/default.json` — glifos + fuente de espacios.
- `assets/<ns>/textures/font/` — texturas pixel-art de insignias, tarjetas, cintas y fondos de menú.
- `assets/<ns>/items/`, `models/item/`, `textures/item/` — modelos para cada `item-model: "<ns>:..."` (insignias y botones del menú).
- `assets/minecraft/textures/gui/sprites/boss_bar/<color>_*.png` — bossbar transparente.
- `assets/<ns>/sounds.json` — desde `resource-pack.sounds` (los `.ogg` van en `assets/sounds/`; los que faltan se omiten).

Tus archivos de `plugins/RadiantAchievements/assets/` siempre tienen prioridad sobre los generados.

**Envío:** `resource-pack.host: auto` sirve el zip desde el HTTP integrado (`http://<dirección>:8164/radiantachievements/<sha1>.zip`), usando la dirección con la que se conectó el jugador (o `http.public-address`). Con `host: https://...` se usa esa URL externa. El SHA-1 se envía siempre, así el cliente solo vuelve a descargar cuando cambia. `required: true` expulsa a quien lo rechace. Los fallos de descarga se explican en consola.

**Oraxen:** `resource-pack.oraxen.mode: MERGE` copia nuestros assets dentro de `plugins/Oraxen/pack/assets/`, usa la fuente `radiant:achievements` (para no pisar la de Oraxen) y pide a Oraxen que recargue; en ese modo RadiantAchievements no envía su propio pack.

---

## Menú `/ach`

Inventario de 54 slots con fondo texturizado (`gui_achievements_<filtro>`) en el título:

- Filas 1-5: columnas decorativas a los lados y 7 logros por fila (35 por página).
- Fila inferior: `«` página anterior, cabeza con estadísticas, pestañas **Todos / Desbloqueados / Pendientes** (la activa brilla en la textura) y `»` página siguiente.
- Iconos por estado: **desbloqueado** (icono del logro con brillo), **en progreso** (reloj de arena + barra en el lore), **bloqueado** (candado) y **secreto** (interrogación). Configurables en `gui.icons` y `gui.states`.

---

## Comandos

Alias: `/achievements`, `/ach`, `/logros`, `/radiantachievements`.

| Comando | Permiso |
|---|---|
| `/ach` — abre el menú | `radiantachievements.use` |
| `/ach progress [jugador]` · `/ach menu [jugador]` | `radiantachievements.use` / `.progress.others` |
| `/ach give <jugador> <logro>` | `radiantachievements.admin` |
| `/ach take <jugador> <logro>` | `radiantachievements.admin` |
| `/ach reset <jugador> [logro\|all]` | `radiantachievements.admin` |
| `/ach setprogress\|addprogress <jugador> <logro> <valor>` | `radiantachievements.admin` |
| `/ach preview <diseño\|logro> [jugador]` | `radiantachievements.preview` |
| `/ach sound <sonido> [volumen] [tono] [jugador]` | `radiantachievements.preview` |
| `/ach pack` (regenera y reenvía el pack) · `/ach reload` | `radiantachievements.reload` |

Los comandos de administración funcionan también con jugadores desconectados (sin notificación ni recompensas).

---

## PlaceholderAPI

Prefijo `%radiantachievements_...%`:

| Placeholder | Valor |
|---|---|
| `unlocked`, `locked`, `total` | contadores |
| `percent` | % completado (1 decimal) |
| `latest` | nombre del último logro |
| `has_<id>` | `true` / `false` |
| `progress_<id>`, `target_<id>`, `percent_<id>`, `bar_<id>` | progreso de un logro |
| `name_<id>`, `date_<id>` | nombre y fecha de desbloqueo |
| `unlocked_category_<cat>`, `total_category_<cat>` | por categoría |

---

## Almacenamiento

`storage.type`: `YAML` (un archivo por jugador), `SQLITE` (`data.db`) o `MYSQL` (red de servidores, pool HikariCP). Carga en `AsyncPlayerPreLoginEvent`, guardado al salir y autoguardado periódico, todo fuera del hilo principal. Cambiar el tipo requiere reiniciar.

---

## API

```java
AchievementAPI api = AchievementAPI.get();   // o ServicesManager

api.addProgress(player, "miner_100", 5);
api.setProgress(player, "traveler", 2500);
api.unlock(player, "summer_event");
api.revoke(player, "summer_event");
boolean has = api.hasUnlocked(player, "gladiator");
double pct = api.getCompletionPercent(player);

// Notificación personalizada con un diseño
api.showNotification(player, "epic", "<gold>¡Jefe derrotado!", "<gray>Has vencido al Rey Esqueleto");
api.playSound(player, "radiant:achievement.epic", 1f, 1f);

// Eventos
api.onUnlock(this, event -> {
    if (event.getAchievement().id().equals("gladiator") && !event.getPlayer().hasPermission("pvp.allowed")) {
        event.setCancelled(true);
    }
});
api.onProgress(this, event -> event.setNewProgress(event.getNewProgress() + 1)); // doble progreso
```

También puedes escuchar `AchievementUnlockEvent` (cancelable; permite desactivar recompensas, notificación o anuncio) y `AchievementProgressEvent` (cancelable; valor modificable) con un `@EventHandler` normal.

Los métodos que cambian estado o muestran cosas deben llamarse en el hilo principal.

Dependencia en tu `plugin.yml`: `depend: [RadiantAchievements]` (o `softdepend`).

---

## Licencia

MIT — ver [LICENSE](LICENSE).
