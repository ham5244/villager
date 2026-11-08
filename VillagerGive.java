package dev.villagergive;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public final class VillagerGive extends JavaPlugin implements TabExecutor {

    private static final Map<String, String> ALIASES = new HashMap<>();
    private static final Set<String> TREASURE = Set.of(
            "mending", "frost_walker", "soul_speed", "swift_sneak",
            "binding_curse", "vanishing_curse");

    static {
        String[][] a = {
                {"prot", "protection"}, {"fprot", "fire_protection"}, {"bprot", "blast_protection"},
                {"pprot", "projectile_protection"}, {"ff", "feather_falling"}, {"resp", "respiration"},
                {"aqua", "aqua_affinity"}, {"thorns", "thorns"}, {"ds", "depth_strider"},
                {"fw", "frost_walker"}, {"ss", "soul_speed"}, {"sneak", "swift_sneak"},
                {"sharp", "sharpness"}, {"smite", "smite"}, {"bane", "bane_of_arthropods"},
                {"kb", "knockback"}, {"fa", "fire_aspect"}, {"loot", "looting"},
                {"sweep", "sweeping_edge"}, {"eff", "efficiency"}, {"silk", "silk_touch"},
                {"unb", "unbreaking"}, {"fort", "fortune"}, {"mend", "mending"},
                {"power", "power"}, {"punch", "punch"}, {"flame", "flame"},
                {"inf", "infinity"}, {"los", "luck_of_the_sea"}, {"lure", "lure"},
                {"loyal", "loyalty"}, {"imp", "impaling"}, {"riptide", "riptide"},
                {"chan", "channeling"}, {"qc", "quick_charge"}, {"pierce", "piercing"},
                {"multi", "multishot"}, {"dens", "density"}, {"breach", "breach"},
                {"wind", "wind_burst"}
        };
        for (String[] p : a) ALIASES.put(p[0], p[1]);
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        var cmd = getCommand("villager");
        if (cmd != null) {
            cmd.setExecutor(this);
            cmd.setTabCompleter(this);
        }
    }

    private Enchantment resolve(String input) {
        String key = input.toLowerCase(Locale.ROOT);
        key = ALIASES.getOrDefault(key, key);
        NamespacedKey nk = key.contains(":") ? NamespacedKey.fromString(key) : NamespacedKey.minecraft(key);
        return nk == null ? null : Registry.ENCHANTMENT.get(nk);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        boolean all = args.length >= 3 && args[2].equalsIgnoreCase("all");
        int start = all ? 3 : 2;
        if (args.length < 3 || !args[0].equalsIgnoreCase("give")
                || (args.length - start) % 2 != 0 || (!all && args.length < 4)) {
            sender.sendMessage(Component.text(
                    "Usage: /villager give <player> all  |  <enchant> <level> [<enchant> <level> ...]",
                    NamedTextColor.RED));
            return true;
        }

        Player target = getServer().getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(Component.text("Player not found: " + args[1], NamedTextColor.RED));
            return true;
        }

        boolean requireBook = getConfig().getBoolean("require-book", true);
        int maxUses = getConfig().getInt("max-uses", 12);
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        List<MerchantRecipe> recipes = new ArrayList<>();

        Map<Enchantment, Integer> wanted = new LinkedHashMap<>();
        if (all) {
            // every enchantment at its max level (curses skipped)
            for (Enchantment e : Registry.ENCHANTMENT) {
                if (e.getKey().getKey().endsWith("_curse")) continue;
                wanted.put(e, e.getMaxLevel());
            }
        }
        // listed pairs add to / override "all"
        for (int i = start; i < args.length; i += 2) {
            Enchantment ench = resolve(args[i]);
            if (ench == null) {
                sender.sendMessage(Component.text("Unknown enchantment: " + args[i], NamedTextColor.RED));
                return true;
            }
            int level;
            try {
                level = Integer.parseInt(args[i + 1]);
            } catch (NumberFormatException e) {
                sender.sendMessage(Component.text("Invalid level: " + args[i + 1], NamedTextColor.RED));
                return true;
            }
            if (level < 1 || level > 255) {
                sender.sendMessage(Component.text("Level must be 1-255.", NamedTextColor.RED));
                return true;
            }
            wanted.put(ench, level);
        }

        for (Map.Entry<Enchantment, Integer> entry : wanted.entrySet()) {
            Enchantment ench = entry.getKey();
            int level = entry.getValue();

            ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
            EnchantmentStorageMeta meta = (EnchantmentStorageMeta) book.getItemMeta();
            meta.addStoredEnchant(ench, level, true);
            book.setItemMeta(meta);

            // Same price formula vanilla librarians use
            int cost = 2 + rnd.nextInt(5 + level * 10) + 3 * level;
            if (TREASURE.contains(ench.getKey().getKey())) cost *= 2;
            cost = Math.min(cost, 64);

            MerchantRecipe recipe = new MerchantRecipe(book, 0, maxUses, true, 5, 0.05f);
            recipe.addIngredient(new ItemStack(Material.EMERALD, cost));
            if (requireBook) recipe.addIngredient(new ItemStack(Material.BOOK));
            recipes.add(recipe);
        }

        target.getWorld().spawn(target.getLocation(), Villager.class, v -> {
            v.setProfession(Villager.Profession.LIBRARIAN);
            v.setVillagerLevel(5);
            v.setVillagerExperience(250); // locks profession + trades
            v.setRecipes(recipes);
            v.setPersistent(true);
            // AI is left on, so the villager walks around like a normal one
        });

        sender.sendMessage(Component.text(
                "Spawned a villager with " + recipes.size() + " trade(s) for " + target.getName() + ".",
                NamedTextColor.GREEN));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        String cur = args[args.length - 1].toLowerCase(Locale.ROOT);

        if (args.length == 1) {
            out.add("give");
        } else if (args.length == 2) {
            getServer().getOnlinePlayers().forEach(p -> out.add(p.getName()));
        } else if (args.length == 3) {
            out.add("all");
            out.addAll(ALIASES.keySet());
        } else if (args.length % 2 == (args[2].equalsIgnoreCase("all") ? 0 : 1)) { // enchant slot
            out.addAll(ALIASES.keySet());
        } else { // level slot
            out.addAll(List.of("1", "2", "3", "4", "5"));
        }
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(cur));
        Collections.sort(out);
        return out;
    }
}
