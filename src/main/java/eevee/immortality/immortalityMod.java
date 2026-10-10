
package eevee.immortality;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class immortalityMod implements ModInitializer {

    private static final String MOD_ID = "immortality";
    private static final String ADMIN_PERMISSION = "immortality.admin";

    private static final Path CONFIG_PATH =
            FabricLoader.getInstance()
                    .getConfigDir()
                    .resolve("immortality.json");

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    /*
     * Supported durations:
     * -1 = infinite
     * t  = ticks (nominally 50 milliseconds per tick)
     * s  = seconds
     * m  = minutes
     * h  = hours
     * d  = days
     * w  = weeks
     *
     * Examples:
     * 50t
     * 90s
     * 5m
     * 2h
     * 1d
     * 1w
     * 1h30m
     * 2w3d4h15m
     */

    private static final Pattern DURATION_PART =
            Pattern.compile("(\\d+)([tsmhdw])");

    private static Config CONFIG = new Config();

    private static long ticksSinceSave;

    @Override
    public void onInitialize() {
        loadConfig();

        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess, environment) -> {
                    dispatcher.register(
                            Commands.literal("immortal")
                                    .executes(context -> {
                                        showHelp(context.getSource());
                                        return 1;
                                    })
                                    .then(globalCommand())
                                    .then(setCommand())
                                    .then(getCommand())
                                    .then(statusCommand())
                                    .then(listCommand())
                                    .then(gameruleCommand())
                                    .then(Commands.literal("reload")
                                            .executes(context -> {
                                                CommandSourceStack source =
                                                        context.getSource();

                                                if (!requireAdmin(source)) {
                                                    return 0;
                                                }

                                                loadConfig();

                                                source.sendSuccess(
                                                        () -> Component.literal(
                                                                "Immortality configuration reloaded."
                                                        ),
                                                        false
                                                );

                                                return 1;
                                            })
                                    )
                                    .then(Commands.literal("info")
                                            .executes(context -> {
                                                showInfo(context.getSource());
                                                return 1;
                                            })
                                    )
                    );
                }
        );

        ServerLivingEntityEvents.ALLOW_DEATH.register(
                (entity, damageSource, damageAmount) -> {
                    if (!(entity instanceof ServerPlayer player)) {
                        return true;
                    }

                    // Let the vanilla Totem of Undying mechanic handle death.
                    if (player.getMainHandItem().is(Items.TOTEM_OF_UNDYING)
                            || player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) {
                        return true;
                    }

                    if (!isImmortal(player)) {
                        return true;
                    }

                    player.setHealth(1.0F);
                    return false;
                }
        );

        ServerTickEvents.END_SERVER_TICK.register(
                immortalityMod::tickTimedGrants
        );
    }

    // ============================================================
    // COMMANDS
    // ============================================================

    private static void showHelp(CommandSourceStack source) {
        source.sendSuccess(
                () -> Component.literal("Immortality commands:"),
                false
        );

        source.sendSuccess(
                () -> Component.literal(
                        "/immortal global [true|false]"
                ),
                false
        );

        source.sendSuccess(
                () -> Component.literal(
                        "/immortal set <player> <true|false> [duration]"
                ),
                false
        );

        source.sendSuccess(
                () -> Component.literal("/immortal get <player>"),
                false
        );

        source.sendSuccess(
                () -> Component.literal("/immortal status [player]"),
                false
        );

        source.sendSuccess(
                () -> Component.literal("/immortal list"),
                false
        );

        source.sendSuccess(
                () -> Component.literal("/immortal gamerule [setting] [true|false]"),
                false
        );

        source.sendSuccess(
                () -> Component.literal("/immortal info"),
                false
        );

        source.sendSuccess(
                () -> Component.literal("/immortal reload"),
                false
        );
    }

    /*
     * /immortal global
     * /immortal global true
     * /immortal global false
     */
    private static LiteralArgumentBuilder<CommandSourceStack> globalCommand() {
        return Commands.literal("global")
                .executes(context -> {
                    context.getSource().sendSuccess(
                            () -> Component.literal(
                                    "Global immortality: " + CONFIG.global
                            ),
                            false
                    );

                    return 1;
                })
                .then(Commands.argument(
                                        "enabled",
                                        BoolArgumentType.bool()
                                )
                                .executes(context -> {
                                    CommandSourceStack source =
                                            context.getSource();

                                    if (!requireAdmin(source)) {
                                        return 0;
                                    }

                                    CONFIG.global = BoolArgumentType.getBool(
                                            context,
                                            "enabled"
                                    );

                                    saveConfig();

                                    source.sendSuccess(
                                            () -> Component.literal(
                                                    "Global immortality set to "
                                                            + CONFIG.global + "."
                                            ),
                                            true
                                    );

                                    return 1;
                                })
                );
    }

    /*
     * /immortal set <player> true
     * /immortal set <player> true -1
     * /immortal set <player> true 50t
     * /immortal set <player> true 1h30m
     * /immortal set <player> false
     */
    private static LiteralArgumentBuilder<CommandSourceStack> setCommand() {
        return Commands.literal("set")
                .then(Commands.argument(
                                        "target",
                                        EntityArgument.player()
                                )
                                .then(Commands.argument(
                                                        "enabled",
                                                        BoolArgumentType.bool()
                                                )
                                                .executes(context -> {
                                                    CommandSourceStack source =
                                                            context.getSource();

                                                    if (!requireAdmin(source)) {
                                                        return 0;
                                                    }

                                                    ServerPlayer target =
                                                            EntityArgument.getPlayer(
                                                                    context,
                                                                    "target"
                                                            );

                                                    boolean enabled =
                                                            BoolArgumentType.getBool(
                                                                    context,
                                                                    "enabled"
                                                            );

                                                    return setImmortality(
                                                            source,
                                                            target,
                                                            enabled,
                                                            null
                                                    );
                                                })
                                                .then(Commands.argument(
                                                                        "duration",
                                                                        StringArgumentType.word()
                                                                )
                                                                .executes(context -> {
                                                                    CommandSourceStack source =
                                                                            context.getSource();

                                                                    if (!requireAdmin(source)) {
                                                                        return 0;
                                                                    }

                                                                    ServerPlayer target =
                                                                            EntityArgument.getPlayer(
                                                                                    context,
                                                                                    "target"
                                                                            );

                                                                    boolean enabled =
                                                                            BoolArgumentType.getBool(
                                                                                    context,
                                                                                    "enabled"
                                                                            );

                                                                    String duration =
                                                                            StringArgumentType.getString(
                                                                                    context,
                                                                                    "duration"
                                                                            );

                                                                    return setImmortality(
                                                                            source,
                                                                            target,
                                                                            enabled,
                                                                            duration
                                                                    );
                                                                })
                                                )
                                )
                );
    }

    private static int setImmortality(
            CommandSourceStack source,
            ServerPlayer target,
            boolean enabled,
            String duration
    ) {
        String uuid = target.getUUID().toString();
        String name = target.getName().getString();

        if (!enabled) {
            CONFIG.permanentImmortals.remove(uuid);
            CONFIG.timedGrants.remove(uuid);

            saveConfig();

            source.sendSuccess(
                    () -> Component.literal(
                            "Removed individual immortality from "
                                    + name + ". The default setting now applies."
                    ),
                    true
            );

            return 1;
        }

        // No duration or -1 means infinite.
        if (duration == null || duration.equals("-1")) {
            CONFIG.timedGrants.remove(uuid);
            CONFIG.permanentImmortals.add(uuid);

            saveConfig();

            source.sendSuccess(
                    () -> Component.literal(
                            name + " now has infinite immortality."
                    ),
                    true
            );

            return 1;
        }

        if (!CONFIG.allowTimed) {
            source.sendFailure(
                    Component.literal(
                            "Timed immortality is disabled."
                    )
            );

            return 0;
        }

        final long durationMillis;

        try {
            durationMillis = parseDurationMillis(duration);
        } catch (IllegalArgumentException exception) {
            source.sendFailure(
                    Component.literal(
                            "Invalid duration. Examples: 50t, 30s, 5m, "
                                    + "2h, 1d, 1w, 1h30m. Use -1 for infinite."
                    )
            );

            return 0;
        }

        final long expiresAt;

        try {
            expiresAt = Math.addExact(
                    System.currentTimeMillis(),
                    durationMillis
            );
        } catch (ArithmeticException exception) {
            source.sendFailure(
                    Component.literal("That duration is too large.")
            );

            return 0;
        }

        CONFIG.permanentImmortals.remove(uuid);

        TimedGrant grant = new TimedGrant();
        grant.playerName = name;
        grant.expiresAtMillis = expiresAt;

        CONFIG.timedGrants.put(uuid, grant);

        saveConfig();

        source.sendSuccess(
                () -> Component.literal(
                        "Granted immortality to " + name
                                + " for " + formatDuration(durationMillis) + "."
                ),
                true
        );

        return 1;
    }

    /*
     * /immortal get <player>
     */
    private static LiteralArgumentBuilder<CommandSourceStack> getCommand() {
        return Commands.literal("get")
                .then(Commands.argument(
                                        "target",
                                        EntityArgument.player()
                                )
                                .executes(context -> {
                                    ServerPlayer target =
                                            EntityArgument.getPlayer(
                                                    context,
                                                    "target"
                                            );

                                    showPlayerStatus(
                                            context.getSource(),
                                            target
                                    );

                                    return 1;
                                })
                );
    }

    /*
     * /immortal status
     * /immortal status <player>
     */
    private static LiteralArgumentBuilder<CommandSourceStack> statusCommand() {
        return Commands.literal("status")
                .executes(context -> {
                    showInfo(context.getSource());
                    return 1;
                })
                .then(Commands.argument(
                                        "target",
                                        EntityArgument.player()
                                )
                                .executes(context -> {
                                    ServerPlayer target =
                                            EntityArgument.getPlayer(
                                                    context,
                                                    "target"
                                            );

                                    showPlayerStatus(
                                            context.getSource(),
                                            target
                                    );

                                    return 1;
                                })
                );
    }

    /*
     * /immortal list
     */
    private static LiteralArgumentBuilder<CommandSourceStack> listCommand() {
        return Commands.literal("list")
                .executes(context -> {
                    CommandSourceStack source = context.getSource();

                    if (!requireAdmin(source)) {
                        return 0;
                    }

                    long now = System.currentTimeMillis();
                    int count = 0;

                    source.sendSuccess(
                            () -> Component.literal(
                                    "Active timed immortality grants:"
                            ),
                            false
                    );

                    for (Map.Entry<String, TimedGrant> entry
                            : CONFIG.timedGrants.entrySet()) {
                        TimedGrant grant = entry.getValue();

                        if (grant == null || grant.expiresAtMillis <= now) {
                            continue;
                        }

                        count++;

                        String name = grant.playerName == null
                                ? entry.getKey()
                                : grant.playerName;

                        String remaining = CONFIG.showExpiration
                                ? formatDuration(
                                grant.expiresAtMillis - now
                        )
                                : "expiration hidden";

                        source.sendSuccess(
                                () -> Component.literal(
                                        name + " — " + remaining
                                ),
                                false
                        );
                    }

                    final int total = count;

                    source.sendSuccess(
                            () -> Component.literal(
                                    "Total active timed grants: " + total
                            ),
                            false
                    );

                    return 1;
                });
    }

    /*
     * /immortal gamerule
     * /immortal gamerule global true
     * /immortal gamerule default false
     * /immortal gamerule allowTimed false
     */
    private static LiteralArgumentBuilder<CommandSourceStack> gameruleCommand() {
        LiteralArgumentBuilder<CommandSourceStack> command =
                Commands.literal("gamerule")
                        .executes(context -> {
                            showGamerules(context.getSource());
                            return 1;
                        });

        command.then(gameruleBoolean("global"));
        command.then(gameruleBoolean("default"));
        command.then(gameruleBoolean("allowTimed"));
        command.then(gameruleBoolean("showExpiration"));
        command.then(gameruleBoolean("notifyExpiration"));

        return command;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> gameruleBoolean(
            String name
    ) {
        return Commands.literal(name)
                .executes(context -> {
                    context.getSource().sendSuccess(
                            () -> Component.literal(
                                    name + " = " + getGamerule(name)
                            ),
                            false
                    );

                    return 1;
                })
                .then(Commands.argument(
                                        "value",
                                        BoolArgumentType.bool()
                                )
                                .executes(context -> {
                                    CommandSourceStack source =
                                            context.getSource();

                                    if (!requireAdmin(source)) {
                                        return 0;
                                    }

                                    boolean value = BoolArgumentType.getBool(
                                            context,
                                            "value"
                                    );

                                    setGamerule(name, value);
                                    saveConfig();

                                    source.sendSuccess(
                                            () -> Component.literal(
                                                    name + " set to " + value + "."
                                            ),
                                            true
                                    );

                                    return 1;
                                })
                );
    }

    // ============================================================
    // PERMISSIONS
    // ============================================================

    /*
     * Commands stay visible in autocomplete.
     * Permission checks happen when a protected command executes.
     */
    private static boolean isAdmin(CommandSourceStack source) {
        // Allow the server console.
        if (source.getEntity() == null) {
            return true;
        }

        // Allow server operators.
        if (source.getEntity() instanceof ServerPlayer player) {
            //? if >=1.21.11 {
            if (player.permissions().hasPermission(
                    net.minecraft.server.permissions.Permissions.COMMANDS_MODERATOR)) {
                return true;
            }
            //?} else {
            if (source.getServer().getPlayerList().isOp(
                    new net.minecraft.server.players.NameAndId(
                            player.getUUID(),
                            player.getName().getString()))) {
                return true;
            }
            //?}
        }

        return false;
    }

    private static boolean requireAdmin(CommandSourceStack source) {
        if (isAdmin(source)) {
            return true;
        }

        source.sendFailure(
                Component.literal(
                        "You do not have permission to use this command. "
                                + "Required permission: " + ADMIN_PERMISSION
                )
        );

        return false;
    }

    // ============================================================
    // SETTINGS
    // ============================================================

    private static boolean getGamerule(String name) {
        return switch (name) {
            case "global" -> CONFIG.global;
            case "default" -> CONFIG.defaultForPlayers;
            case "allowTimed" -> CONFIG.allowTimed;
            case "showExpiration" -> CONFIG.showExpiration;
            case "notifyExpiration" -> CONFIG.notifyExpiration;
            default -> false;
        };
    }

    private static void setGamerule(String name, boolean value) {
        switch (name) {
            case "global" -> CONFIG.global = value;
            case "default" -> CONFIG.defaultForPlayers = value;
            case "allowTimed" -> CONFIG.allowTimed = value;
            case "showExpiration" -> CONFIG.showExpiration = value;
            case "notifyExpiration" -> CONFIG.notifyExpiration = value;
        }
    }

    // ============================================================
    // IMMORTALITY CHECKS
    // ============================================================

    private static boolean isImmortal(ServerPlayer player) {
        if (CONFIG.global) {
            return true;
        }

        String uuid = player.getUUID().toString();

        if (CONFIG.permanentImmortals.contains(uuid)) {
            return true;
        }

        TimedGrant grant = CONFIG.timedGrants.get(uuid);

        if (grant != null
                && grant.expiresAtMillis > System.currentTimeMillis()) {
            return true;
        }

        return CONFIG.defaultForPlayers;
    }

    // ============================================================
    // TIMED GRANTS
    // ============================================================

    private static void tickTimedGrants(MinecraftServer server) {
        if (CONFIG.timedGrants.isEmpty()) {
            return;
        }

        long now = System.currentTimeMillis();
        boolean changed = false;

        Iterator<Map.Entry<String, TimedGrant>> iterator =
                CONFIG.timedGrants.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<String, TimedGrant> entry = iterator.next();
            TimedGrant grant = entry.getValue();

            if (grant == null || grant.expiresAtMillis <= now) {
                String uuidText = entry.getKey();

                iterator.remove();
                changed = true;

                if (CONFIG.notifyExpiration) {
                    try {
                        UUID uuid = UUID.fromString(uuidText);
                        ServerPlayer player =
                                server.getPlayerList().getPlayer(uuid);

                        if (player != null) {
                            player.sendSystemMessage(
                                    Component.literal(
                                            "Your timed immortality has expired."
                                    )
                            );
                        }
                    } catch (IllegalArgumentException ignored) {
                        // Ignore malformed UUIDs in config data.
                    }
                }
            }
        }

        ticksSinceSave++;

        // Save periodically, and immediately when the last grant expires.
        if (changed
                && (ticksSinceSave >= 20 || CONFIG.timedGrants.isEmpty())) {
            saveConfig();
            ticksSinceSave = 0;
        }
    }

    // ============================================================
    // DURATION PARSING
    // ============================================================

    private static long parseDurationMillis(String input) {
        if (input == null || input.isBlank() || input.equals("-1")) {
            throw new IllegalArgumentException("Missing duration.");
        }

        long totalMillis = 0;
        int position = 0;
        boolean found = false;

        Matcher matcher = DURATION_PART.matcher(input);

        while (matcher.find()) {
            if (matcher.start() != position) {
                throw new IllegalArgumentException("Invalid duration.");
            }

            found = true;
            position = matcher.end();

            long amount;

            try {
                amount = Long.parseLong(matcher.group(1));
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Duration too large.");
            }

            if (amount <= 0) {
                throw new IllegalArgumentException(
                        "Duration parts must be positive."
                );
            }

            long multiplier = switch (matcher.group(2).charAt(0)) {
                case 't' -> 50L;
                case 's' -> 1_000L;
                case 'm' -> 60_000L;
                case 'h' -> 3_600_000L;
                case 'd' -> 86_400_000L;
                case 'w' -> 604_800_000L;
                default -> throw new IllegalArgumentException(
                        "Unknown time unit."
                );
            };

            try {
                totalMillis = Math.addExact(
                        totalMillis,
                        Math.multiplyExact(amount, multiplier)
                );
            } catch (ArithmeticException exception) {
                throw new IllegalArgumentException("Duration too large.");
            }
        }

        if (!found || position != input.length() || totalMillis <= 0) {
            throw new IllegalArgumentException("Invalid duration.");
        }

        return totalMillis;
    }

    private static String formatDuration(long millis) {
        long remaining = Math.max(0, millis);

        long weeks = remaining / 604_800_000L;
        remaining %= 604_800_000L;

        long days = remaining / 86_400_000L;
        remaining %= 86_400_000L;

        long hours = remaining / 3_600_000L;
        remaining %= 3_600_000L;

        long minutes = remaining / 60_000L;
        remaining %= 60_000L;

        long seconds = remaining / 1_000L;

        StringBuilder result = new StringBuilder();

        appendDurationPart(result, weeks, "w");
        appendDurationPart(result, days, "d");
        appendDurationPart(result, hours, "h");
        appendDurationPart(result, minutes, "m");
        appendDurationPart(result, seconds, "s");

        if (result.isEmpty()) {
            return "less than 1s";
        }

        return result.toString().trim();
    }

    private static void appendDurationPart(
            StringBuilder builder,
            long value,
            String suffix
    ) {
        if (value <= 0) {
            return;
        }

        if (!builder.isEmpty()) {
            builder.append(' ');
        }

        builder.append(value).append(suffix);
    }

    // ============================================================
    // STATUS AND INFORMATION
    // ============================================================

    private static void showPlayerStatus(
            CommandSourceStack source,
            ServerPlayer player
    ) {
        String uuid = player.getUUID().toString();
        long now = System.currentTimeMillis();

        source.sendSuccess(
                () -> Component.literal(
                        "Immortality status for "
                                + player.getName().getString() + ":"
                ),
                false
        );

        if (CONFIG.global) {
            source.sendSuccess(
                    () -> Component.literal(
                            "Status: IMMORTAL (global setting)"
                    ),
                    false
            );
        } else if (CONFIG.permanentImmortals.contains(uuid)) {
            source.sendSuccess(
                    () -> Component.literal(
                            "Status: IMMORTAL (permanent individual grant)"
                    ),
                    false
            );
        } else {
            TimedGrant grant = CONFIG.timedGrants.get(uuid);

            if (grant != null && grant.expiresAtMillis > now) {
                String remaining = CONFIG.showExpiration
                        ? formatDuration(grant.expiresAtMillis - now)
                        : "hidden";

                source.sendSuccess(
                        () -> Component.literal(
                                "Status: IMMORTAL (timed; remaining: "
                                        + remaining + ")"
                        ),
                        false
                );
            } else if (CONFIG.defaultForPlayers) {
                source.sendSuccess(
                        () -> Component.literal(
                                "Status: IMMORTAL (default setting)"
                        ),
                        false
                );
            } else {
                source.sendSuccess(
                        () -> Component.literal("Status: NOT IMMORTAL"),
                        false
                );
            }
        }

        source.sendSuccess(
                () -> Component.literal(
                        "Effective immortality: " + isImmortal(player)
                ),
                false
        );
    }

    private static void showInfo(CommandSourceStack source) {
        source.sendSuccess(
                () -> Component.literal("Immortality configuration:"),
                false
        );

        source.sendSuccess(
                () -> Component.literal(
                        "Global immortality: " + CONFIG.global
                ),
                false
        );

        source.sendSuccess(
                () -> Component.literal(
                        "Default for players: " + CONFIG.defaultForPlayers
                ),
                false
        );

        source.sendSuccess(
                () -> Component.literal(
                        "Timed grants allowed: " + CONFIG.allowTimed
                ),
                false
        );

        source.sendSuccess(
                () -> Component.literal(
                        "Show expiration: " + CONFIG.showExpiration
                ),
                false
        );

        source.sendSuccess(
                () -> Component.literal(
                        "Notify on expiration: " + CONFIG.notifyExpiration
                ),
                false
        );

        source.sendSuccess(
                () -> Component.literal(
                        "Permanent grants: "
                                + CONFIG.permanentImmortals.size()
                ),
                false
        );

        source.sendSuccess(
                () -> Component.literal(
                        "Timed grants: " + CONFIG.timedGrants.size()
                ),
                false
        );
    }

    private static void showGamerules(CommandSourceStack source) {
        source.sendSuccess(
                () -> Component.literal("Immortality settings:"),
                false
        );

        source.sendSuccess(
                () -> Component.literal("global = " + CONFIG.global),
                false
        );

        source.sendSuccess(
                () -> Component.literal(
                        "default = " + CONFIG.defaultForPlayers
                ),
                false
        );

        source.sendSuccess(
                () -> Component.literal(
                        "allowTimed = " + CONFIG.allowTimed
                ),
                false
        );

        source.sendSuccess(
                () -> Component.literal(
                        "showExpiration = " + CONFIG.showExpiration
                ),
                false
        );

        source.sendSuccess(
                () -> Component.literal(
                        "notifyExpiration = " + CONFIG.notifyExpiration
                ),
                false
        );
    }

    // ============================================================
    // CONFIGURATION
    // ============================================================

    private static void loadConfig() {
        if (!Files.exists(CONFIG_PATH)) {
            CONFIG = new Config();
            saveConfig();
            return;
        }

        try {
            Config loaded = GSON.fromJson(
                    Files.readString(CONFIG_PATH),
                    Config.class
            );

            if (loaded == null) {
                loaded = new Config();
            }

            if (loaded.permanentImmortals == null) {
                loaded.permanentImmortals = new HashSet<>();
            }

            if (loaded.timedGrants == null) {
                loaded.timedGrants = new HashMap<>();
            }

            CONFIG = loaded;

            CONFIG.permanentImmortals.removeIf(uuid -> {
                try {
                    UUID.fromString(uuid);
                    return false;
                } catch (IllegalArgumentException exception) {
                    return true;
                }
            });

            CONFIG.timedGrants.entrySet().removeIf(entry -> {
                try {
                    UUID.fromString(entry.getKey());

                    return entry.getValue() == null
                            || entry.getValue().expiresAtMillis <= 0;
                } catch (IllegalArgumentException exception) {
                    return true;
                }
            });

            saveConfig();

        } catch (Exception exception) {
            System.err.println(
                    "[" + MOD_ID + "] Failed to load config: "
                            + exception.getMessage()
            );
        }
    }

    private static void saveConfig() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());

            Files.writeString(
                    CONFIG_PATH,
                    GSON.toJson(CONFIG)
            );
        } catch (IOException exception) {
            System.err.println(
                    "[" + MOD_ID + "] Failed to save config: "
                            + exception.getMessage()
            );
        }
    }

    // ============================================================
    // DATA CLASSES
    // ============================================================

    private static class Config {
        boolean global = true;
        boolean defaultForPlayers = false;
        boolean allowTimed = true;
        boolean showExpiration = true;
        boolean notifyExpiration = true;

        Set<String> permanentImmortals = new HashSet<>();
        Map<String, TimedGrant> timedGrants = new HashMap<>();
    }

    private static class TimedGrant {
        String playerName = "Unknown";
        long expiresAtMillis;
    }
}
