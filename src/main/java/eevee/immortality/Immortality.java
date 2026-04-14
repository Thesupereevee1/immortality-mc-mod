package eevee.immortality;

import com.google.gson.Gson;
import com.mojang.brigadier.arguments.BoolArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.command.argument.EntityArgumentType;
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
import me.lucko.fabric.api.permissions.v0.Permissions;

public class Immortality implements ModInitializer {

	// =========================
	// CONFIG STATE
	// =========================

	private static final File SAVE_FILE = new File("config/immortality.json");
	private static final Gson GSON = new Gson();

	private static ImmortalityConfig CONFIG = new ImmortalityConfig();

	private static boolean GLOBAL_IMMORTALITY = true;
	private static final Set<UUID> toggledImmortals = ConcurrentHashMap.newKeySet();

	// =========================
	// INIT
	// =========================

	@Override
	public void onInitialize() {

		loadData();

		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			if (!(entity instanceof ServerPlayerEntity player)) {
				return true;
			}

			boolean toggled = toggledImmortals.contains(player.getUuid());
			boolean protectedByImmortality = GLOBAL_IMMORTALITY || toggled;
			boolean inVoid = player.getY() < player.getEntityWorld().getBottomY() - 5;

			boolean holdingTotem =
					player.getMainHandStack().isOf(Items.TOTEM_OF_UNDYING) ||
							player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING);

			if (holdingTotem) return true;

			if (inVoid && !CONFIG.allowVoidImmortality && !protectedByImmortality) return true;

			if (!GLOBAL_IMMORTALITY && !toggled) return true;

			player.setHealth(1.0F);
			return false;
		});

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {

			dispatcher.register(
					CommandManager.literal("immortal")

							// =========================
							// GLOBAL
							// =========================
							.then(CommandManager.literal("global")
									.executes(ctx -> {
										ctx.getSource().sendMessage(
												Text.literal("Global immortality: " + GLOBAL_IMMORTALITY)
										);
										return 1;
									})
									.then(CommandManager.argument("value", BoolArgumentType.bool())
											.executes(ctx -> {

												if (!isAdmin(ctx.getSource())) {
													ctx.getSource().sendMessage(Text.literal("No permission.").styled(style -> style.withColor(0xFF5555)));													return 0;
												}

												GLOBAL_IMMORTALITY = BoolArgumentType.getBool(ctx, "value");
												saveData();

												ctx.getSource().sendMessage(
														Text.literal("Global immortality set to: " + GLOBAL_IMMORTALITY)
												);
												return 1;
											})
									)
							)

							// =========================
							// VOID
							// =========================
							.then(CommandManager.literal("void")
									.executes(ctx -> {
										ctx.getSource().sendMessage(
												Text.literal("Void immortality: " + CONFIG.allowVoidImmortality)
										);
										return 1;
									})
									.then(CommandManager.argument("value", BoolArgumentType.bool())
											.executes(ctx -> {

												if (!isAdmin(ctx.getSource())) {
													ctx.getSource().sendMessage(Text.literal("No permission."));
													return 0;
												}

												CONFIG.allowVoidImmortality = BoolArgumentType.getBool(ctx, "value");
												saveData();

												ctx.getSource().sendMessage(
														Text.literal("Void immortality set to: " + CONFIG.allowVoidImmortality)
												);
												return 1;
											})
									)
							)

							// =========================
							// SET PLAYER
							// =========================
							.then(CommandManager.literal("set")
									.then(CommandManager.argument("target", EntityArgumentType.players())
											.then(CommandManager.argument("value", BoolArgumentType.bool())
													.executes(ctx -> {

														if (!isAdmin(ctx.getSource())) {
															ctx.getSource().sendMessage(Text.literal("No permission."));
															return 0;
														}

														Collection<ServerPlayerEntity> targets =
																EntityArgumentType.getPlayers(ctx, "target");

														boolean value = BoolArgumentType.getBool(ctx, "value");

														for (ServerPlayerEntity target : targets) {
															setImmortality(target, value);
														}

														saveData();

														ctx.getSource().sendMessage(
																Text.literal("Set immortality for " + targets.size() + " player(s): " + value)
														);

														return 1;
													})
											)
									)
							)

							// =========================
							// GET PLAYER
							// =========================
							.then(CommandManager.literal("get")
									.then(CommandManager.argument("target", EntityArgumentType.player())
											.executes(ctx -> {

												ServerPlayerEntity target =
														EntityArgumentType.getPlayer(ctx, "target");

												boolean value = toggledImmortals.contains(target.getUuid());

												ctx.getSource().sendMessage(
														Text.literal(target.getName().getString() + " immortal: " + value)
												);

												return 1;
											})
									)
							)

							// =========================
							// RELOAD
							// =========================
							.then(CommandManager.literal("reload")
									.executes(ctx -> {

										if (!isAdmin(ctx.getSource())) {
											ctx.getSource().sendMessage(Text.literal("No permission."));
											return 0;
										}

										loadData();

										ctx.getSource().sendMessage(
												Text.literal("Immortality config reloaded.")
										);

										return 1;
									})
							)
			);
		});
	}

	// =========================
	// ADMIN CHECK
	// =========================

	private static boolean isAdmin(ServerCommandSource source) {
		return Permissions.check(source, "immortality.admin", 2);
	}

	// =========================
	// TOGGLE LOGIC
	// =========================

	private static void setImmortality(ServerPlayerEntity player, boolean value) {
		UUID id = player.getUuid();

		if (value) {
			toggledImmortals.add(id);
		} else {
			toggledImmortals.remove(id);
		}
	}

	// =========================
	// SAVE / LOAD
	// =========================

	private static void saveData() {
		try {
			SAVE_FILE.getParentFile().mkdirs();

			CONFIG.global = GLOBAL_IMMORTALITY;

			CONFIG.immortals = toggledImmortals.stream()
					.map(UUID::toString)
					.toList();

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

			toggledImmortals.clear();
			for (String s : CONFIG.immortals) {
				toggledImmortals.add(UUID.fromString(s));
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	// =========================
	// CONFIG CLASS
	// =========================

	public static class ImmortalityConfig {
		public boolean global = true;
		public boolean allowVoidImmortality = false;
		public List<String> immortals = new ArrayList<>();
	}
}