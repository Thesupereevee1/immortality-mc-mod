package eevee.immortality;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.mojang.brigadier.arguments.BoolArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.item.Items;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Immortality implements ModInitializer {

	private static boolean GLOBAL_IMMORTALITY = true;

	private static final Set<UUID> toggledImmortals = ConcurrentHashMap.newKeySet();

	private static final File SAVE_FILE = new File("config/immortality.json");
	private static final Gson GSON = new Gson();

	@Override
	public void onInitialize() {

		loadData();

		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			if (entity instanceof ServerPlayerEntity player) {

				boolean toggled = toggledImmortals.contains(player.getUuid());

				boolean holdingTotem =
						player.getMainHandStack().isOf(Items.TOTEM_OF_UNDYING) ||
								player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING);

				// Let totems work normally
				if (holdingTotem) {
					return true;
				}

				if (!GLOBAL_IMMORTALITY && !toggled) {
					return true;
				}

				player.setHealth(1.0F);
				return false;
			}

			return true;
		});

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {

			dispatcher.register(
					CommandManager.literal("immortal")

							// Toggle self
							.executes(ctx -> {
								ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
								toggle(player);
								return 1;
							})

							// Global toggle
							.then(CommandManager.literal("global")
									.executes(ctx -> {
										GLOBAL_IMMORTALITY = !GLOBAL_IMMORTALITY;

										ctx.getSource().sendMessage(
												Text.literal("Global immortality: " + GLOBAL_IMMORTALITY)
										);

										return 1;
									})
							)

							// Set multiple players
							.then(CommandManager.literal("set")
									.then(CommandManager.argument("target", EntityArgumentType.players())
											.then(CommandManager.argument("value", BoolArgumentType.bool())
													.executes(ctx -> {
														Collection<ServerPlayerEntity> targets = EntityArgumentType.getPlayers(ctx, "target");
														boolean value = BoolArgumentType.getBool(ctx, "value");

														for (ServerPlayerEntity target : targets) {
															setImmortality(target, value);
														}

														ctx.getSource().sendMessage(
																Text.literal("Set immortality for " + targets.size() + " player(s): " + value)
														);

														saveData();
														return 1;
													})
											)
									)
							)

							// Get command
							.then(CommandManager.literal("get")
									.then(CommandManager.argument("target", EntityArgumentType.player())
											.executes(ctx -> {
												ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "target");
												boolean value = toggledImmortals.contains(target.getUuid());

												ctx.getSource().sendMessage(
														Text.literal(target.getName().getString() + " immortal: " + value)
												);

												return 1;
											})
									)
							)
			);
		});
	}

	private static void toggle(ServerPlayerEntity player) {
		UUID id = player.getUuid();

		if (toggledImmortals.contains(id)) {
			toggledImmortals.remove(id);
			player.sendMessage(Text.literal("Immortality OFF"), false);
		} else {
			toggledImmortals.add(id);
			player.sendMessage(Text.literal("Immortality ON"), false);
		}

		saveData();
	}

	private static void setImmortality(ServerPlayerEntity player, boolean value) {
		UUID id = player.getUuid();

		if (value) {
			toggledImmortals.add(id);
			player.sendMessage(Text.literal("Immortality ON"), false);
		} else {
			toggledImmortals.remove(id);
			player.sendMessage(Text.literal("Immortality OFF"), false);
		}
	}

	// =========================
	// SAVE / LOAD
	// =========================

	private static void saveData() {
		try {
			SAVE_FILE.getParentFile().mkdirs();
			FileWriter writer = new FileWriter(SAVE_FILE);

			List<String> list = toggledImmortals.stream()
					.map(UUID::toString)
					.toList();

			GSON.toJson(list, writer);
			writer.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static void loadData() {
		try {
			if (!SAVE_FILE.exists()) return;

			FileReader reader = new FileReader(SAVE_FILE);
			Type type = new TypeToken<List<String>>(){}.getType();

			List<String> list = GSON.fromJson(reader, type);
			reader.close();

			toggledImmortals.clear();

			for (String s : list) {
				toggledImmortals.add(UUID.fromString(s));
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}