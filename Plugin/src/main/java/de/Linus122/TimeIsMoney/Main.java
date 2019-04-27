package de.Linus122.TimeIsMoney;

import com.earth2me.essentials.Essentials;
import de.Linus122.TimeIsMoney.tools.ActionBarUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.SimplePluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitWorker;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import static de.Linus122.TimeIsMoney.tools.Utils.CC;

/**
 * The main class for TimeIsMoney
 *
 * @author Linus122
 * @since 1.9.6.1
 */
public class Main extends JavaPlugin {
	/**
	 * The economy being used by vault.
	 */
	static net.milkbowl.vault.economy.Economy economy = null;
	/**
	 * The actionbar utils class, null if not using a supported server version.
	 */
	private static ActionBarUtils actionBarUtils = null;
	/**
	 * The config version number.
	 */
	private static final int CFG_VERSION = 12;
	/**
	 * The TimeIsMoney version.
	 */
	static String PL_VERSION;
	/**
	 * The TimeIsMoney config.
	 */
	static YamlConfiguration finalconfig;
	/**
	 * The list of worlds where the payout feature will be disabled.
	 */
	private static List<String> disabledWorlds;
	/**
	 * The IPs of each UUID.
	 */
	private static final HashMap<String, UUID> boundIPs = new HashMap<>();
	/**
	 * The payouts listed in the config.
	 */
	private final List<Payout> payouts = new ArrayList<>();
	/**
	 * The payouts for the day.
	 */
	private HashMap<String, Double> payedMoney = new HashMap<>();
	/**
	 * The time online in seconds of each player by UUID.
	 */
	private final HashMap<UUID, Integer> onlineSeconds = new HashMap<>();
	/**
	 * The last location of each player by UUID.
	 */
	private final HashMap<UUID, Location> lastLocation = new HashMap<>();
	/**
	 * The chat message.
	 */
	private String message;
	/**
	 * The actionbar message.
	 */
	private String messageActionbar;
	/**
	 * The console logger.
	 */
	private final ConsoleCommandSender clogger = this.getServer().getConsoleSender();
	/**
	 * The current day.
	 */
	private int currentDay = 0;
	/**
	 * If actionbars are supported for the server's version.
	 */
	private boolean useActionbars = true;
	
	/**
	 * {@inheritDoc}
	 */
	@SuppressWarnings({"deprecation", "unchecked"})
	@Override
	public void onEnable() {
		this.getCommand("timeismoney").setExecutor(new Cmd(this));
		PL_VERSION = this.getDescription().getVersion();
		currentDay = (new Date()).getDay();
		this.reloadConfig();
		File config = new File("plugins/TimeIsMoney/config.yml");
		if (config.exists()) {
			YamlConfiguration cfg = YamlConfiguration.loadConfiguration(config);
			String old_config = "config_old " + cfg.getInt("configuration-version") + ".yml";
			if (cfg.contains("configuration-version")) {
				if (cfg.getInt("configuration-version") < CFG_VERSION) {
					clogger.sendMessage(CC("[TimeIsMoney] &cYOU ARE USING AN OLD CONFIG-VERSION. The plugin CANT work with this."));
					clogger.sendMessage(CC("[TimeIsMoney] &cI have created an new config for you. The old one is saved as config_old.yml."));
					config.renameTo(new File("plugins/TimeIsMoney/" + old_config));
				}
			}
			this.saveDefaultConfig();
			for (String key : cfg.getConfigurationSection("").getKeys(true)) {
				if (!this.getConfig().contains(key)) {
					this.getConfig().set(key, cfg.get(key));
				}
			}
		} else {
			this.saveDefaultConfig();
		}
		
		finalconfig = YamlConfiguration.loadConfiguration(config);
		disabledWorlds = getConfig().getStringList("disabled_in_worlds");
		
		if (getConfig().getBoolean("enable_atm")) new ATM(this);
		
		final int seconds = getConfig().getInt("give_money_every_second");
		Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
			try {
				for (Player p : Bukkit.getOnlinePlayers()) {
					if (disabledWorlds.contains(p.getWorld().getName())) continue;
					if (!boundIPs.containsKey(p.getAddress().getHostName())) {
						boundIPs.put(p.getAddress().getHostName(), p.getUniqueId());
					}
					if (onlineSeconds.containsKey(p.getUniqueId())) {
						
						onlineSeconds.put(p.getUniqueId(), onlineSeconds.get(p.getUniqueId()) + 1);
					} else {
						onlineSeconds.put(p.getUniqueId(), 1);
					}
					if (onlineSeconds.get(p.getUniqueId()) >= seconds) {
						pay(p);
						onlineSeconds.remove(p.getUniqueId());
					}
				}
			} catch (NullPointerException ignored) {
			}
		}, 20L, 20L);
		Bukkit.getScheduler().scheduleSyncRepeatingTask(this, () -> {
			if (currentDay != new Date().getDay()) { //Next day, clear payouts!
				log("Cleared all payouts");
				payedMoney.clear();
				currentDay = new Date().getDay();
			}
		}, 20L * 60, 20L * 60 * 15);
		setupEconomy();
		
		message = finalconfig.getString("message");
		message = CC(message);
		messageActionbar = finalconfig.getString("message_actionbar");
		messageActionbar = CC(messageActionbar);
		
		try {
			FileInputStream fis = new FileInputStream(new File("plugins/TimeIsMoney/payed_today.data"));
			ObjectInputStream ois = new ObjectInputStream(fis);
			payedMoney = (HashMap<String, Double>) ((HashMap<String, Double>) ois.readObject()).clone();
			
			ois.close();
		} catch (Exception ignored) {
		}
		
		loadPayouts();
		
		String packageName = this.getServer().getClass().getPackage().getName();
		// Get full package string of CraftServer.
		// org.bukkit.craftbukkit.version
		String Bukkitversion = packageName.substring(packageName.lastIndexOf('.') + 1);
		// Get the last element of the package
		try {
			final Class<?> clazz = Class.forName("de.Linus122.TimeIsMoney.version." + Bukkitversion + ".NBTUtils");
			// Check if we have a NMSHandler class at that location.
			if (ActionBarUtils.class.isAssignableFrom(clazz)) { // Make sure it actually implements NMS
				actionBarUtils = (ActionBarUtils) clazz.getConstructor().newInstance(); // Set our handler
			}
		} catch (final Exception e) {
			this.getLogger().severe("Actionbars are not supported on your spigot version, sorry.");
			useActionbars = false;
			return;
		}
		
		if (Bukkit.getPluginManager().isPluginEnabled("Essentials")) {
			clogger.sendMessage("Time is Money: Essentials found. Hook in it -> Will use Essentials's AFK feature if afk is enabled.");
		}
		new Metrics(this);
		
		clogger.sendMessage(CC("&aTime is Money &2v" + PL_VERSION + " &astarted."));
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public void onDisable() {
		FileOutputStream fos;
		try {
			fos = new FileOutputStream(new File("plugins/TimeIsMoney/payed_today.data"));
			ObjectOutputStream oos = new ObjectOutputStream(fos);
			oos.writeObject(payedMoney);
			oos.close();
		} catch (Exception ignored) {
		}
	}

	/**
	 * Reloads TimeIsMoney.
	 */
	void reload() {
	    // cancelling current tasks
        for (BukkitWorker bw: Bukkit.getScheduler().getActiveWorkers()) {
            if (bw.getOwner() == this) {
                Bukkit.getScheduler().cancelTask(bw.getTaskId());
            }
        }
		Bukkit.getPluginManager().disablePlugin(this);
		Bukkit.getPluginManager().enablePlugin(this);
	}
	
	/**
	 * Loads the payouts.
	 */
	private void loadPayouts() {
		try {
			payouts.clear();
			for (String key : finalconfig.getConfigurationSection("payouts").getKeys(false)) {
				Payout payout = new Payout();
				payout.max_payout_per_day = finalconfig.getDouble("payouts." + key + ".max_payout_per_day");
				payout.payout_amount = finalconfig.getDouble("payouts." + key + ".payout_amount");
				if (finalconfig.getString("payouts." + key + ".permission") != null) {
					payout.permission = finalconfig.getString("payouts." + key + ".permission");
				}
				if (finalconfig.getString("payouts." + key + ".commands") != null) {
					payout.commands = finalconfig.getStringList("payouts." + key + ".commands");
				}
				
				if (finalconfig.getString("payouts." + key + ".chance") != null) {
					payout.chance = finalconfig.getInt("payouts." + key + ".chance");
				}
				payouts.add(payout);
			}
			clogger.sendMessage(CC("[TimeIsMoney] &aLoaded " + finalconfig.getConfigurationSection("payouts").getKeys(false).size() + " Payouts!"));
		} catch (Exception e) {
			clogger.sendMessage(CC("[TimeIsMoney] &aFailed to load Payouts! (May made a mistake in config.yml?)"));
		}
	}
	
	/**
	 * Sets up the vault economy.
	 *
	 * @return True if the economy was set up, false otherwise.
	 */
	private boolean setupEconomy() {
		RegisteredServiceProvider<net.milkbowl.vault.economy.Economy> economyProvider = getServer().getServicesManager().getRegistration(net.milkbowl.vault.economy.Economy.class);
		if (economyProvider != null) {
			economy = economyProvider.getProvider();
		}
		
		return economy != null;
	}
	
	/**
	 * Gets the payout for the specified player.
	 *
	 * @param p The player to get the payout of.
	 * @return The payout of the player.
	 */
	private Payout getPayOutForPlayer(Player p) {
		Payout finalpayout = null;
		if (!this.getConfig().getBoolean("choose-payout-by-chance")) {
			//by Permission
			for (Payout payout : payouts) {
				if (payout.permission.equalsIgnoreCase("")) finalpayout = payout;
				if (p.hasPermission(payout.permission)) {
					finalpayout = payout;
				}
			}
		} else {
			//by Chance
			Random rnd = new Random();
			List<Payout> list = new ArrayList<>();
			for (Payout payout : payouts) {
				for (int i = 0; i < payout.chance; i++) list.add(payout);
			}
			finalpayout = list.get(rnd.nextInt(list.size() - 1));
		}
		return finalpayout;
	}
	
	/**
	 * Pays the specified player.
	 *
	 * @param p The player to pay.
	 */
	@SuppressWarnings("deprecation")
	private void pay(Player p) {
		if (p == null) return;
		
		//REACHED MAX PAYOUT CHECK
		double payed = 0;
		if (payedMoney.containsKey(p.getName())) {
			payed = payedMoney.get(p.getName());
		}
		Payout payout = getPayOutForPlayer(p);
		if (payout == null) return;
		if (payout.max_payout_per_day != -1) {
			if (payed >= payout.max_payout_per_day) { //Reached max payout
				if (finalconfig.getBoolean("display-messages-in-chat")) {
					sendMessage(p, finalconfig.getString("message_payoutlimit_reached"));
				}
				if (finalconfig.getBoolean("display-messages-in-actionbar") && useActionbars) {
					sendActionbar(p, finalconfig.getString("message_payoutlimit_reached_actionbar"));
				}
				return;
			}
		}
		
		if (!finalconfig.getBoolean("allow-multiple-accounts")) {
			if (boundIPs.containsKey(p.getAddress().getHostName())) {
				if (!boundIPs.get(p.getAddress().getHostName()).equals(p.getUniqueId())) {
					sendMessage(p, finalconfig.getString("message_multiple_ips"));
					return;
				}
			}
		}
		
		//AFK CHECK
		if (!finalconfig.getBoolean("afk_payout") && !p.hasPermission("tim.afkbypass")) {
			//ESENTIALS_AFK_FEATURE
			if (Bukkit.getServer().getPluginManager().isPluginEnabled("Essentials")) {
				Essentials essentials = (com.earth2me.essentials.Essentials) Bukkit.getServer().getPluginManager().getPlugin("Essentials");
				if (essentials.getUser(p).isAfk()) {
					//AFK
					if (finalconfig.getBoolean("display-messages-in-chat")) {
						sendMessage(p, finalconfig.getString("message_afk"));
					}
					if (finalconfig.getBoolean("display-messages-in-actionbar") && useActionbars) {
						sendActionbar(p, finalconfig.getString("message_afk_actionbar"));
					}
					return;
				}
			} else {
				//PLUGIN_AFK_FEATURE
				if (lastLocation.containsKey(p.getUniqueId())) { //AntiAFK
					if (lastLocation.get(p.getUniqueId()).getX() == p.getLocation().getX() && lastLocation.get(p.getUniqueId()).getY() == p.getLocation().getY() && lastLocation.get(p.getUniqueId()).getZ() == p.getLocation().getZ() || lastLocation.get(p.getUniqueId()).getYaw() == p.getLocation().getYaw()) {
						//AFK
						if (finalconfig.getBoolean("display-messages-in-chat")) {
							sendMessage(p, finalconfig.getString("message_afk"));
						}
						if (finalconfig.getBoolean("display-messages-in-actionbar") && useActionbars) {
							sendActionbar(p, finalconfig.getString("message_afk_actionbar"));
						}
						return;
					}
				}
			}
		}
		
		//DEPOSIT
		if (finalconfig.getBoolean("store-money-in-bank")) {
			ATM.depositBank(p, payout.payout_amount);
		} else {
			double before = 0;
			if (economy.hasAccount(p)) {
				before = economy.getBalance(p);
			}
			
			Bukkit.getScheduler().runTask(this, ()->economy.depositPlayer(p, payout.payout_amount));
			log(p.getName() + ": Deposited: " + payout.payout_amount + " Balance-before: " + before + " Balance-now: " + economy.getBalance(p));
			
		}
		if (finalconfig.getBoolean("display-messages-in-chat")) {
			sendMessage(p, message.replace("%money%", economy.format(payout.payout_amount)));
		}
		if (finalconfig.getBoolean("display-messages-in-actionbar") && useActionbars) {
			sendActionbar(p, messageActionbar.replace("%money%", economy.format(payout.payout_amount)));
		}
		for (String cmd : payout.commands) {
			dispatchCommandSync(cmd.replace("/", "").replaceAll("%player%", p.getName()));
		}
		
		//ADD PAYED MONEY
		if (payedMoney.containsKey(p.getName())) {
			payedMoney.put(p.getName(), payedMoney.get(p.getName()) + payout.payout_amount);
		} else {
			payedMoney.put(p.getName(), payout.payout_amount);
		}
		
		lastLocation.put(p.getUniqueId(), p.getLocation());
	}
	
	/**
	 * Dispatches a command as sync.
	 *
	 * @param cmd The command to dispatch sync.
	 */
	private void dispatchCommandSync(final String cmd) {
		final Server server = this.getServer();
		
		this.getServer().getScheduler().runTask(this, () -> server.dispatchCommand(server.getConsoleSender(), cmd));
	}
	
	/**
	 * Logs debug information if enabled in the config.
	 *
	 * @param msg The debug message to log.
	 */
	@SuppressWarnings("deprecation")
	private void log(String msg) {
		if (!this.getConfig().getBoolean("debug-log")) {
			return;
		}
		Timestamp currentTimestamp = new Timestamp(Calendar.getInstance().getTime().getTime());
		
		File file = new File("plugins/TimeIsMoney/log.txt");
		try {
			if (!file.exists()) {
				file.createNewFile();
			}
			FileReader pr = new FileReader(file);
			int number;
			StringBuffer text = new StringBuffer();
			while ((number = pr.read()) != -1) {
				
				text.append((char) number);
			}
			text.append(currentTimestamp.toGMTString()).append(":").append(msg).append("\n");
			PrintWriter pw = new PrintWriter(file);
			pw.print(text);
			
			pw.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Sends a chat message that supports color codes to the specified player.
	 *
	 * @param p The player to send the chat message to.
	 * @param msg The message the chat should give the player.
	 */
	private void sendMessage(Player p, String msg) {
		if (msg == null) return;
		if (msg.length() == 0) return;
		p.sendMessage(CC(msg));
	}
	
	/**
	 * Sends an actionbar message to the specified player.
	 *
	 * @param p The player to send the actionbar message to.
	 * @param msg The message the actionbar should give to the player.
	 */
	private void sendActionbar(final Player p, final String msg) {
		if (msg.length() == 0) return;
		int times = finalconfig.getInt("display-messages-in-actionbar-time");
		if (times == 1) {
			if (actionBarUtils != null) {
				actionBarUtils.sendActionBarMessage(p, msg);
			}
		} else if (times > 1) {
			if (actionBarUtils != null) {
				actionBarUtils.sendActionBarMessage(p, msg);
			}
			times--;
			for (int i = 0; i < times; i++) {
				Bukkit.getScheduler().scheduleSyncDelayedTask(this, () -> actionBarUtils.sendActionBarMessage(p, CC(msg)), 20L * i);
			}
		}
	}
	
	/**
	 * Unloads the specified plugin.
	 *
	 * @param pluginName The name of the plugin to unload.
	 * @return True if the specified plugin was unloaded, false otherwise.
	 */
	private boolean unloadPlugin(String pluginName)
			throws Exception {
		PluginManager manager = getServer().getPluginManager();
		SimplePluginManager spmanager = (SimplePluginManager) manager;
		if (spmanager != null) {
			Field pluginsField = spmanager.getClass().getDeclaredField("plugins");
			pluginsField.setAccessible(true);
			List<Plugin> plugins = (List) pluginsField.get(spmanager);
			
			Field lookupNamesField = spmanager.getClass().getDeclaredField("lookupNames");
			lookupNamesField.setAccessible(true);
			Map<String, Plugin> lookupNames = (Map) lookupNamesField.get(spmanager);
			
			Field commandMapField = spmanager.getClass().getDeclaredField("commandMap");
			commandMapField.setAccessible(true);
			SimpleCommandMap commandMap = (SimpleCommandMap) commandMapField.get(spmanager);
			
			Field knownCommandsField;
			Map<String, Command> knownCommands = null;
			if (commandMap != null) {
				knownCommandsField = commandMap.getClass().getDeclaredField("knownCommands");
				knownCommandsField.setAccessible(true);
				knownCommands = (Map) knownCommandsField.get(commandMap);
			}
			Plugin plugin;
			Iterator<Map.Entry<String, Command>> it;
			for (Plugin plugin1 : manager.getPlugins()) {
				if (plugin1.getDescription().getName().equalsIgnoreCase(pluginName)) {
					manager.disablePlugin(plugin1);
					if ((plugins != null) && (plugins.contains(plugin1))) {
						plugins.remove(plugin1);
					}
					if ((lookupNames != null) && (lookupNames.containsKey(pluginName))) {
						lookupNames.remove(pluginName);
					}
					if (commandMap != null) {
						for (it = knownCommands.entrySet().iterator(); it.hasNext(); ) {
							Map.Entry<String, Command> entry = it.next();
							if ((entry.getValue() instanceof PluginCommand)) {
								PluginCommand command = (PluginCommand) entry.getValue();
								if (command.getPlugin() == plugin1) {
									command.unregister(commandMap);
									it.remove();
								}
							}
						}
					}
				}
			}
		} else {
			return true;
		}
		return true;
	}
	
	/**
	 * Loads the specified plugin.
	 *
	 * @param pluginName The name of the plugin to load.
	 * @return True if the specified plugin was loaded, false otherwise.
	 */
	private boolean loadPlugin(String pluginName) {
		try {
			PluginManager manager = getServer().getPluginManager();
			Plugin plugin = manager.loadPlugin(new File("plugins", pluginName + ".jar"));
			if (plugin == null) {
				return false;
			}
			plugin.onLoad();
			manager.enablePlugin(plugin);
		} catch (Exception e) {
			return false;
		}
		return true;
	}
	
	/**
	 * Reloads the specified plugin.
	 *
	 * @param pluginName The name of the plugin to reload.
	 * @return True if the plugin was reloaded, false otherwise.
	 * @throws Exception If an error occurred while loading or unloading the specified plugin.
	 */
	private boolean reloadPlugin(String pluginName)
			throws Exception {
		boolean unload = unloadPlugin(pluginName);
		boolean load = loadPlugin(pluginName);
		
		return unload && load;
	}
}
