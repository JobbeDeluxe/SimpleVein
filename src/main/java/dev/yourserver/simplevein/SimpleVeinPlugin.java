package dev.yourserver.simplevein;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExpEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SimpleVeinPlugin extends JavaPlugin implements Listener {

    private final Set<UUID> toggledOff = ConcurrentHashMap.newKeySet();
    private final Random rng = new Random();

    private Set<Material> whitelist;
    private Set<Material> allowedTools;
    private boolean disableWhenSneaking;
    private int maxBlocks;
    private boolean diagonals;
    private boolean enabledByDefault;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadLocal();
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("SimpleVein aktiv (Sneak deaktiviert: " + disableWhenSneaking + ").");
    }

    private void reloadLocal() {
        FileConfiguration cfg = getConfig();
        enabledByDefault     = cfg.getBoolean("enabled-by-default", true);
        disableWhenSneaking  = cfg.getBoolean("disable-when-sneaking", true);
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
        if (disableWhenSneaking && p.isSneaking()) return;

        ItemStack tool = p.getInventory().getItemInMainHand();
        if (!isAllowedTool(tool)) return;

        Material type = start.getType();
        if (!whitelist.contains(type)) return;

        veinMine(p, start, type, tool);
    }

    private void veinMine(Player p, Block start, Material type, ItemStack tool) {
        // BFS-Queue
        ArrayDeque<Block> q = new ArrayDeque<>();
        // besucht nach Welt+Koordinate deduplizieren
        HashSet<String> seen = new HashSet<>();

        q.add(start);
        seen.add(keyOf(start));

        int extraBroken = 0;

        while (!q.isEmpty()) {
            Block b = q.poll();
            boolean isStart = (b.equals(start));

            if (!isStart) {
                if (extraBroken >= maxBlocks && !p.hasPermission("simplevein.bypass-limit")) break;

                // Typ checken (könnte sich verändert haben)
                if (b.getType() != type) continue;

                // Schutz-Plugins respektieren
                BlockBreakEvent probe = new BlockBreakEvent(b, p);
                Bukkit.getPluginManager().callEvent(probe);
                if (probe.isCancelled()) continue;

                // natürlich abbauen; nur wenn wirklich abgebaut wurde, XP etc.
                boolean brokenNow = b.breakNaturally(tool);
                if (!brokenNow) continue;

                extraBroken++;

                // XP wie Vanilla für Zusatzblöcke
                int baseXp = vanillaXpFor(p, tool, type);
                if (baseXp > 0) {
                    BlockExpEvent xpEv = new BlockExpEvent(b, baseXp);
                    Bukkit.getPluginManager().callEvent(xpEv);
                    int toDrop = Math.max(0, xpEv.getExpToDrop());
                    if (toDrop > 0) {
                        Location drop = b.getLocation().add(0.5, 0.5, 0.5);
                        b.getWorld().spawn(drop, ExperienceOrb.class, orb -> orb.setExperience(toDrop));
                    }
                }

                // Tool kaputt? raus
                if (tool != null && tool.getType() == Material.AIR) break;
            }

            // Nachbarn sammeln
            for (Block nb : neighbors(b, diagonals)) {
                if (nb.getType() == type) {
                    String k = keyOf(nb);
                    if (seen.add(k)) {
                        q.add(nb);
                    }
                }
            }
        }
    }

    private String keyOf(Block b) {
        Location l = b.getLocation();
        return l.getWorld().getUID() + ":" + l.getBlockX() + ":" + l.getBlockY() + ":" + l.getBlockZ();
    }

    private int vanillaXpFor(Player p, ItemStack tool, Material type) {
        // Creative/Adventure -> kein XP-Drop
        GameMode gm = p.getGameMode();
        if (gm == GameMode.CREATIVE || gm == GameMode.ADVENTURE) return 0;

        // Silk Touch -> kein XP-Drop
        if (tool != null && tool.containsEnchantment(Enchantment.SILK_TOUCH)) return 0;

        switch (type) {
            case COAL_ORE:
            case DEEPSLATE_COAL_ORE:
                return rnd(0, 2);
            case DIAMOND_ORE:
            case DEEPSLATE_DIAMOND_ORE:
            case EMERALD_ORE:
            case DEEPSLATE_EMERALD_ORE:
                return rnd(3, 7);
            case LAPIS_ORE:
            case DEEPSLATE_LAPIS_ORE:
            case NETHER_QUARTZ_ORE:
                return rnd(2, 5);
            case REDSTONE_ORE:
            case DEEPSLATE_REDSTONE_ORE:
                return rnd(1, 5);
            case NETHER_GOLD_ORE:
                return rnd(0, 1);
            // kein XP bei diesen:
            case COPPER_ORE:
            case DEEPSLATE_COPPER_ORE:
            case ANCIENT_DEBRIS:
                return 0;
            default:
                return 0;
        }
    }

    private int rnd(int min, int max) {
        if (max <= min) return min;
        return rng.nextInt((max - min) + 1) + min;
    }

    private List<Block> neighbors(Block b, boolean diag) {
        List<Block> list = new ArrayList<>(diag ? 26 : 6);
        int x = b.getX(), y = b.getY(), z = b.getZ();
        World w = b.getWorld();
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
                    if ((Math.abs(dx) + Math.abs(dy) + Math.abs(dz)) == 1) continue; // schon oben abgedeckt
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
