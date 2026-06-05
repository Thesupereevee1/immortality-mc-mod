package eevee.immortality;

import com.google.gson.Gson;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ImmortalityPlugin extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {

  private final Gson gson = new Gson();
  private File file;

  private final Map<UUID, Integer> immortals = new ConcurrentHashMap<>();

  // players who DO NOT get fall immunity (default = everyone immune)
  private final Set<UUID> fallDisabled = ConcurrentHashMap.newKeySet();

  private boolean global = true;

  // =========================
  // ENABLE
  // =========================

  @Override
  public void onEnable() {
    file = new File(getDataFolder(), "immortality.json");

    load();

    getServer().getPluginManager().registerEvents(this, this);

    Objects.requireNonNull(getCommand("immortal")).setExecutor(this);
    Objects.requireNonNull(getCommand("immortal")).setTabCompleter(this);

    tick();
  }

  // =========================
  // DAMAGE
  // =========================

  @EventHandler
  public void onDamage(EntityDamageEvent e) {
    if (!(e.getEntity() instanceof Player p)) return;

    UUID id = p.getUniqueId();

    boolean immortal = global || immortals.containsKey(id);
    if (!immortal) return;

    // TOTEM SAFETY
    ItemStack main = p.getInventory().getItemInMainHand();
    ItemStack off = p.getInventory().getItemInOffHand();

    if (main.getType() == org.bukkit.Material.TOTEM_OF_UNDYING ||
            off.getType() == org.bukkit.Material.TOTEM_OF_UNDYING) {
      return;
    }

    // FALL (default TRUE, disabled only if explicitly opted out)
    if (e.getCause() == EntityDamageEvent.DamageCause.FALL
            && fallDisabled.contains(id)) {
      return; // allow normal fall damage
    }

    if (p.getHealth() - e.getFinalDamage() <= 0) {
      e.setCancelled(true);

      new BukkitRunnable() {
        @Override
        public void run() {
          if (p.isOnline()) {
            p.setHealth(1.0);
          }
        }
      }.runTask(this);
    }
  }

  // =========================
  // COMMANDS
  // =========================

  @Override
  public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

    if (args.length == 0) {
      sendHelp(sender);
      return true;
    }

    switch (args[0].toLowerCase()) {

      case "set": {
        if (args.length < 2) return true;

        Player p = Bukkit.getPlayer(args[1]);
        if (p == null) return true;

        int time = -1;
        if (args.length >= 3) time = Integer.parseInt(args[2]);

        immortals.put(p.getUniqueId(), time);

        sender.sendMessage("§aImmortal set: " + p.getName() + " time=" + formatTime(time));
        save();
        return true;
      }

      case "get": {
        if (args.length < 2) return true;

        Player p = Bukkit.getPlayer(args[1]);
        if (p == null) return true;

        UUID id = p.getUniqueId();

        Integer t = immortals.get(id);
        boolean immortal = global || t != null;

        sender.sendMessage("§ePlayer: " + p.getName());
        sender.sendMessage("§eImmortal: " + immortal);
        sender.sendMessage("§eTime: " + formatTime(t == null ? -1 : t));
        sender.sendMessage("§eGlobal immortality: " + global);
        sender.sendMessage("§eFall default: true");
        sender.sendMessage("§eFall disabled for player: " + fallDisabled.contains(id));

        return true;
      }

      case "global": {

        if (args.length == 1) {
          sender.sendMessage("§bGlobal immortality: " + global);
          return true;
        }

        global = Boolean.parseBoolean(args[1]);

        sender.sendMessage("§bGlobal set to: " + global);
        save();
        return true;
      }

      case "fall": {

        if (!(sender instanceof Player p)) {
          sender.sendMessage("Players only.");
          return true;
        }

        UUID id = p.getUniqueId();

        if (args.length == 1) {
          sender.sendMessage("§6Fall immunity (default TRUE): " + !fallDisabled.contains(id));
          return true;
        }

        boolean enable = Boolean.parseBoolean(args[1]);

        if (enable) fallDisabled.remove(id);
        else fallDisabled.add(id);

        sender.sendMessage("§6Fall immunity now: " + enable);
        save();
        return true;
      }

      case "reload": {
        load();
        sender.sendMessage("§aReloaded.");
        return true;
      }
    }

    return true;
  }

  // =========================
  // TAB
  // =========================

  @Override
  public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {

    if (args.length == 1) {
      return Arrays.asList("set", "get", "global", "fall", "reload");
    }

    if (args.length == 2 && (args[0].equalsIgnoreCase("set") || args[0].equalsIgnoreCase("get"))) {
      List<String> list = new ArrayList<>();
      for (Player p : Bukkit.getOnlinePlayers()) list.add(p.getName());
      return list;
    }

    if (args.length == 2 && args[0].equalsIgnoreCase("global")) {
      return Arrays.asList("true", "false");
    }

    if (args.length == 2 && args[0].equalsIgnoreCase("fall")) {
      return Arrays.asList("true", "false");
    }

    return Collections.emptyList();
  }

  // =========================
  // TIMER (ONLY PLAYER SET TIMER)
  // =========================

  private void tick() {
    new BukkitRunnable() {
      @Override
      public void run() {

        Iterator<Map.Entry<UUID, Integer>> it = immortals.entrySet().iterator();

        while (it.hasNext()) {
          var e = it.next();

          int t = e.getValue();
          if (t == -1) continue;

          if (t > 0) {
            t--;
            e.setValue(t);
          }

          if (t == 0) it.remove();
        }
      }
    }.runTaskTimer(this, 20L, 20L);
  }

  // =========================
  // HELP
  // =========================

  private void sendHelp(CommandSender s) {
    s.sendMessage("§6/immortal set <player> [time]");
    s.sendMessage("§6/immortal get <player>");
    s.sendMessage("§6/immortal global [true/false]");
    s.sendMessage("§6/immortal fall [true/false]");
    s.sendMessage("§6/immortal reload");
  }

  // =========================
  // FORMAT
  // =========================

  private String formatTime(int t) {
    if (t == -1) return "∞";
    return String.valueOf(t);
  }

  // =========================
  // SAVE / LOAD
  // =========================

  private void save() {
    try {
      if (!getDataFolder().exists()) getDataFolder().mkdirs();

      Data d = new Data();
      d.global = global;

      for (var e : immortals.entrySet())
        d.immortals.put(e.getKey().toString(), e.getValue());

      for (UUID id : fallDisabled)
        d.fallDisabled.add(id.toString());

      FileWriter w = new FileWriter(file);
      gson.toJson(d, w);
      w.close();

    } catch (Exception ignored) {}
  }

  private void load() {
    try {
      if (!getDataFolder().exists()) getDataFolder().mkdirs();
      if (!file.exists()) return;

      FileReader r = new FileReader(file);
      Data d = gson.fromJson(r, Data.class);
      r.close();

      if (d == null) return;

      global = d.global;

      immortals.clear();
      for (var e : d.immortals.entrySet())
        immortals.put(UUID.fromString(e.getKey()), e.getValue());

      fallDisabled.clear();
      for (String id : d.fallDisabled)
        fallDisabled.add(UUID.fromString(id));

    } catch (Exception ignored) {}
  }

  // =========================
  // DATA
  // =========================

  public static class Data {
    boolean global = true;
    Map<String, Integer> immortals = new HashMap<>();
    List<String> fallDisabled = new ArrayList<>();
  }
}