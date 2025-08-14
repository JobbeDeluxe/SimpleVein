package dev.yourserver.simplevein;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SimpleVeinPlugin extends JavaPlugin implements Listener {

    private final Set<UUID> toggledOff = ConcurrentHashMap.newKeySet();
    private Set<Material> whitelist;
    private Set<Material> allowedTools;
    private boolean disableWhenSneaking;  // NEU
    private int maxBlocks;
    private boolean diagonals;
    private boolean enabledByDefault;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadLocal();
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("SimpleVein aktiviert (invertierte Sneak-Steuerung: " + disableWhenSneaking + ").");
    }

    private void reloadLocal() {
        FileConfiguration cfg = getConfig();
        enabledByDefault     = cfg.getBoolean("enabled-by-default", true);
        disableWhenSneaking  = cfg.getBoolean("disable-when-sneaking", true); // NEU
        maxBlocks            = Math.max(1, cfg.getInt("max-blocks", 64));
        diagonals            = cfg.getBoolean("diagonals", false);

        whitelist = new HashSet<>();
        for (String m : cfg.getStringList("whitelist")) {
            try { whitelist.add(Material.valueOf(m)); } catch (IllegalArgumentException ignored) {}
        }

        allowedTools = new HashSet<>();
        for (String m : cfg.getStringList("allowed-tools")) {
            try { allowedTools.add(Material.valueOf(m)); } catch (IllegalArgumentException ignored) {}
        }
    }

    private boolean isEnabledFor(Player p) {
        return enabledByDefault != toggledOff.contains(p.getUniqueId());
    }

    private boolean isAllowedTool(ItemStack item) {
        return item == null || allowedTools.isEmpty() || allowedTools.contains(item.getType());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        Block start = e.getBlock();

        if (!isEnabledFor(p)) return;

        // NEU: VeinMining ist generell an, aber Sneak deaktiviert es
        if (disableWhenSneaking && p.isSneaking()) return;

        ItemStack tool = p.getInventory().getItemInMainHand();
        if (!isAllowedTool(tool)) return;

        Material target = start.getType();
        if (!whitelist.contains(target)) return;

        veinMine(p, start, target, tool);
    }

    private void veinMine(Player p, Block start, Material type, ItemStack tool) {
        Queue<Block> q = new ArrayDeque<>();
        Set<Block> seen = Collections.newSetFromMap(new IdentityHashMap<>());

        q.add(start);
        seen.add(start);

        int broken = 0;

        while (!q.isEmpty()) {
            Block b = q.poll();

            // Startblock NICHT hier abbauen -> lässt Minecraft selbst erledigen
            boolean isStart = (b == start);
            if (!isStart) {
                if (broken >= maxBlocks && !p.hasPermission("simplevein.bypass-limit")) break;

                // Schutz-Plugins respektieren
                BlockBreakEvent probe = new BlockBreakEvent(b, p);
                Bukkit.getPluginManager().callEvent(probe);
                if (probe.isCancelled()) continue;

                // natürlich abbauen (Fortune/Silk/Haltbarkeit etc.)
                b.breakNaturally(tool);
                broken++;

                // Tool kaputt? abbrechen
                if (tool.getType() == Material.AIR) break;
            }

            // Nachbarn sammeln (auch beim Start, damit die Ader sich ausbreitet)
            for (Block nb : neighbors(b, diagonals)) {
                if (nb.getType() == type && !seen.contains(nb)) {
                    seen.add(nb);
                    q.add(nb);
                }
            }
        }
    }

    private List<Block> neighbors(Block b, boolean diag) {
        List<Block> list = new ArrayList<>(diag ? 26 : 6);
        int x = b.getX(), y = b.getY(), z = b.getZ();
        var w = b.getWorld();
        // 6-Achsen
        list.add(w.getBlockAt(x + 1, y, z));
        list.add(w.getBlockAt(x - 1, y, z));
        list.add(w.getBlockAt(x, y + 1, z));
        list.add(w.getBlockAt(x, y - 1, z));
        list.add(w.getBlockAt(x, y, z + 1));
        list.add(w.getBlockAt(x, y, z - 1));
        if (!diag) return list;

        for (int dx = -1; dx <= 1; dx++)
            for (int dy = -1; dy <= 1; dy++)
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    if ((Math.abs(dx) + Math.abs(dy) + Math.abs(dz)) == 1) continue; // schon oben
                    list.add(w.getBlockAt(x + dx, y + dy, z + dz));
                }
        return list;
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] args) {
        if (!(s instanceof Player p)) {
            if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
                if (!s.hasPermission("simplevein.reload")) return true;
                reloadConfig(); reloadLocal();
                s.sendMessage("SimpleVein neu geladen.");
            } else {
                s.sendMessage("Nur Spieler können /vein toggle nutzen. /vein reload ist als Konsole erlaubt.");
            }
            return true;
        }

        if (!s.hasPermission("simplevein.use")) {
            s.sendMessage("§cKeine Berechtigung.");
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("toggle")) {
            if (isEnabledFor(p)) {
                toggledOff.add(p.getUniqueId());
                p.sendMessage("§eSimpleVein: §cAUS");
            } else {
                toggledOff.remove(p.getUniqueId());
                p.sendMessage("§eSimpleVein: §aAN");
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("on")) {
            toggledOff.remove(p.getUniqueId());
            p.sendMessage("§eSimpleVein: §aAN");
            return true;
        }
        if (args[0].equalsIgnoreCase("off")) {
            toggledOff.add(p.getUniqueId());
            p.sendMessage("§eSimpleVein: §cAUS");
            return true;
        }
        if (args[0].equalsIgnoreCase("reload")) {
            if (!p.hasPermission("simplevein.reload")) {
                p.sendMessage("§cKeine Berechtigung.");
                return true;
            }
            reloadConfig(); reloadLocal();
            p.sendMessage("§eSimpleVein neu geladen.");
            return true;
        }
        s.sendMessage("§7/vein [toggle|on|off|reload]");
        return true;
    }
}

