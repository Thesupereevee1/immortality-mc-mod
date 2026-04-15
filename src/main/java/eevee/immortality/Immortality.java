package eevee.immortality;

import com.google.gson.Gson;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Immortality implements ModInitializer {

	// =========================
	// DATA
	// =========================

	private static final File SAVE_FILE = new File("config/immortality.json");
	private static final Gson GSON = new Gson();

	private static ImmortalityConfig CONFIG = new ImmortalityConfig();

	private static Map<UUID, Integer> immortals = new ConcurrentHashMap<>();

	private static Set<UUID> fallImmune = ConcurrentHashMap.newKeySet();
	private static Set<UUID> armorProtected = ConcurrentHashMap.newKeySet();

	private static boolean GLOBAL_IMMORTALITY = true;
	private static int GLOBAL_TIME = -1;

	private static int tickCounter = 0;

	// =========================
	// INIT
	// =========================

	@Override
	public void onInitialize() {

		loadData();

		// =========================
		// DEATH LOGIC
		// =========================

		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			if (!(entity instanceof ServerPlayerEntity player)) return true;

			UUID id = player.getUuid();

			boolean hasImmortality = immortals.containsKey(id);
			boolean protectedByImmortality = GLOBAL_IMMORTALITY || hasImmortality;

			boolean inVoid = player.getY() < player.getEntityWorld().getBottomY() - 5;

			// FIXED FALL DETECTION (no DamageSource API issues)
			boolean inFall = source == player.getDamageSources().fall();

			boolean holdingTotem =
					player.getMainHandStack().isOf(Items.TOTEM_OF_UNDYING) ||
							player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING);

			if (holdingTotem) return true;

			// VOID IMMORTALITY RULE
			if (inVoid && !CONFIG.allowVoidImmortality && !protectedByImmortality) {
				return true;
			}

			// FALL IMMORTALITY RULE
			if (inFall && !fallImmune.contains(id) && !protectedByImmortality) {
				return true;
			}

			if (!protectedByImmortality) return true;

			player.setHealth(1.0F);
			return false;
		});

		// =========================
		// TICK SYSTEM
		// =========================

		ServerTickEvents.END_SERVER_TICK.register(server -> {

			tickCounter++;

			if (tickCounter >= 20) {
				tickCounter = 0;

				// PLAYER TIMERS
				Iterator<Map.Entry<UUID, Integer>> it = immortals.entrySet().iterator();
				while (it.hasNext()) {
					var e = it.next();

					int time = e.getValue();
					if (time == -1) continue;

					time--;

					if (time <= 0) it.remove();
					else e.setValue(time);
				}

				// GLOBAL TIMER
				if (GLOBAL_TIME > 0) {
					GLOBAL_TIME--;
					if (GLOBAL_TIME == 0) GLOBAL_IMMORTALITY = false;
				}

				// ARMOR PROTECTION
				for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {

					if (!armorProtected.contains(player.getUuid())) continue;

					for (int i = 0; i < 4; i++) {
						ItemStack stack = player.getInventory().getStack(36 + i);

						if (stack.isDamageable() && stack.getDamage() > 0) {
							stack.setDamage(Math.max(0, stack.getDamage() - 1));
						}
					}
				}

				if (server.getTicks() % 200 == 0) saveData();
			}
		});

		// =========================
		// COMMANDS
		// =========================

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {

			dispatcher.register(CommandManager.literal("immortal")

					// GLOBAL
					.then(CommandManager.literal("global")
							.executes(ctx -> {
								ctx.getSource().sendMessage(Text.literal("Global: " + GLOBAL_IMMORTALITY));
								return 1;
							})
							.then(CommandManager.argument("value", BoolArgumentType.bool())
									.executes(ctx -> {
										if (!isAdmin(ctx.getSource())) return noPerm(ctx);

										GLOBAL_IMMORTALITY = BoolArgumentType.getBool(ctx, "value");
										GLOBAL_TIME = -1;

										saveData();
										return 1;
									})
									.then(CommandManager.argument("time", IntegerArgumentType.integer(-1))
											.executes(ctx -> {
												if (!isAdmin(ctx.getSource())) return noPerm(ctx);

												GLOBAL_IMMORTALITY = BoolArgumentType.getBool(ctx, "value");
												GLOBAL_TIME = IntegerArgumentType.getInteger(ctx, "time");

												saveData();
												return 1;
											})
									)
							)
					)

							// SET
							.then(CommandManager.literal("set")
									.then(CommandManager.argument("target", EntityArgumentType.players())
											.executes(ctx -> {
												if (!isAdmin(ctx.getSource())) return noPerm(ctx);

												var targets = EntityArgumentType.getPlayers(ctx, "target");

												for (var p : targets) {
													immortals.put(p.getUuid(), -1);
												}

												String names = targets.stream()
														.map(p -> p.getName().getString())
														.reduce((a, b) -> a + ", " + b)
														.orElse("none");

												ctx.getSource().sendMessage(
														Text.literal("Made " + names + " immortal.")
																.styled(style -> style.withColor(0x55FF55))
												);

												saveData();
												return 1;
											})
											.then(CommandManager.argument("time", IntegerArgumentType.integer(-1))
													.executes(ctx -> {
														if (!isAdmin(ctx.getSource())) return noPerm(ctx);

														var targets = EntityArgumentType.getPlayers(ctx, "target");
														int time = IntegerArgumentType.getInteger(ctx, "time");

														for (var p : targets) {
															immortals.put(p.getUuid(), time);
														}

														String names = targets.stream()
																.map(p -> p.getName().getString())
																.reduce((a, b) -> a + ", " + b)
																.orElse("none");

														String timeText = (time == -1) ? "infinite" : (time + " ticks");

														ctx.getSource().sendMessage(
																Text.literal("Made " + names + " immortal for " + timeText + ".")
																		.styled(style -> style.withColor(0x55FF55))
														);

														saveData();
														return 1;
													})
											)
									)
							)

					// FALL
					.then(CommandManager.literal("fall")
							.then(CommandManager.argument("value", BoolArgumentType.bool())
									.executes(ctx -> {
										if (!isAdmin(ctx.getSource())) return noPerm(ctx);

										ServerPlayerEntity p = ctx.getSource().getPlayer();
										boolean value = BoolArgumentType.getBool(ctx, "value");

										if (value) {
											fallImmune.add(p.getUuid());
											ctx.getSource().sendMessage(
													Text.literal("Fall immunity ENABLED for " + p.getName().getString())
															.styled(style -> style.withColor(0x55FF55))
											);
										} else {
											fallImmune.remove(p.getUuid());
											ctx.getSource().sendMessage(
													Text.literal("Fall immunity DISABLED for " + p.getName().getString())
															.styled(style -> style.withColor(0xFF5555))
											);
										}

										saveData();
										return 1;
									})
							)
					)

					// ARMOR
					.then(CommandManager.literal("armor")
							.then(CommandManager.argument("value", BoolArgumentType.bool())
									.executes(ctx -> {
										if (!isAdmin(ctx.getSource())) return noPerm(ctx);

										ServerPlayerEntity p = ctx.getSource().getPlayer();
										boolean value = BoolArgumentType.getBool(ctx, "value");

										if (value) {
											armorProtected.add(p.getUuid());
											ctx.getSource().sendMessage(
													Text.literal("Armor protection ENABLED for " + p.getName().getString())
															.styled(style -> style.withColor(0x55FF55))
											);
										} else {
											armorProtected.remove(p.getUuid());
											ctx.getSource().sendMessage(
													Text.literal("Armor protection DISABLED for " + p.getName().getString())
															.styled(style -> style.withColor(0xFF5555))
											);
										}

										saveData();
										return 1;
									})
							)
					)

					// GET
					.then(CommandManager.literal("get")
							.then(CommandManager.argument("target", EntityArgumentType.player())
									.executes(ctx -> {
										var p = EntityArgumentType.getPlayer(ctx, "target");
										Integer t = immortals.get(p.getUuid());

										ctx.getSource().sendMessage(Text.literal(
												p.getName().getString() + " immortal: " + (t != null) + " time: " + t
										));
										return 1;
									})
							)
					)

					// RELOAD
					.then(CommandManager.literal("reload")
							.executes(ctx -> {
								if (!isAdmin(ctx.getSource())) return noPerm(ctx);
								loadData();
								return 1;
							})
					)
			);
		});
	}

	// =========================
	// PERMS
	// =========================

	private static boolean isAdmin(ServerCommandSource source) {
		return Permissions.check(source, "immortality.admin", 2);
	}

	private static int noPerm(com.mojang.brigadier.context.CommandContext<ServerCommandSource> ctx) {
		ctx.getSource().sendMessage(Text.literal("§cNo permission."));
		return 0;
	}

	// =========================
	// SAVE / LOAD
	// =========================

	private static void saveData() {
		try {
			SAVE_FILE.getParentFile().mkdirs();

			CONFIG.global = GLOBAL_IMMORTALITY;
			CONFIG.globalTime = GLOBAL_TIME;

			CONFIG.immortals = new HashMap<>();
			for (var e : immortals.entrySet()) {
				CONFIG.immortals.put(e.getKey().toString(), e.getValue());
			}

			FileWriter writer = new FileWriter(SAVE_FILE);
			GSON.toJson(CONFIG, writer);
			writer.close();

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static void loadData() {
		try {
			if (!SAVE_FILE.exists()) {
				saveData();
				return;
			}

			FileReader reader = new FileReader(SAVE_FILE);
			CONFIG = GSON.fromJson(reader, ImmortalityConfig.class);
			reader.close();

			if (CONFIG == null) CONFIG = new ImmortalityConfig();

			GLOBAL_IMMORTALITY = CONFIG.global;
			GLOBAL_TIME = CONFIG.globalTime;

			immortals.clear();
			for (var e : CONFIG.immortals.entrySet()) {
				immortals.put(UUID.fromString(e.getKey()), e.getValue());
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	// =========================
	// CONFIG
	// =========================

	public static class ImmortalityConfig {
		public boolean global = true;
		public int globalTime = -1;
		public boolean allowVoidImmortality = false;
		public Map<String, Integer> immortals = new HashMap<>();
	}
}