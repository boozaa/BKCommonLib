package com.bergerkiller.bukkit.common;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;

import net.minecraft.server.MathHelper;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.Configuration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.config.FileConfiguration;
import com.bergerkiller.bukkit.common.internal.CommonPlugin;
import com.bergerkiller.bukkit.common.permissions.IPermissionDefault;
import com.bergerkiller.bukkit.common.permissions.NoPermissionException;
import com.bergerkiller.bukkit.common.reflection.classes.PluginDescriptionFileRef;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.common.utils.ParseUtil;
import com.bergerkiller.bukkit.common.utils.StringUtil;

@SuppressWarnings("unused")
public abstract class PluginBase extends JavaPlugin {
	private String disableMessage = null;
	private FileConfiguration permissionconfig, localizationconfig;
	private boolean enabled = false;

	/**
	 * Logs a message to the server console
	 * 
	 * @param level of the message
	 * @param message to log
	 */
	public void log(Level level, String message) {
		Bukkit.getLogger().log(level, "[" + this.getName() + "] " + message);
	}

	/**
	 * Gets the version of this Plugin
	 * 
	 * @return Plugin version
	 */
	public final String getVersion() {
		return this.getDescription().getVersion();
	}

	/**
	 * Gets the file of the path relative to this plugin's data folder
	 * 
	 * @param path of the file
	 * @return relative data File
	 */
	public File getDataFile(String... path) {
		if (path == null || path.length == 0) {
			return this.getDataFolder();
		}
		StringBuilder name = new StringBuilder(path.length * 15);
		for (int i = 0; i < path.length; i++) {
			if (i != 0) {
				name.append(File.separatorChar);
			}
			name.append(path[i]);
		}
		return new File(this.getDataFolder(), name.toString());
	}

	/**
	 * Gets a Permission, creates one if it doesn't exist
	 * 
	 * @param path of the Permission to obtain
	 * @return Permission
	 */
	public static Permission getPermission(String path) {
		Permission perm = Bukkit.getServer().getPluginManager().getPermission(path);
		if (perm == null) {
			perm = new Permission(path, PermissionDefault.FALSE);
			Bukkit.getServer().getPluginManager().addPermission(perm);
		}
		return perm;
	}

	/**
	 * Gets a permission configuration node
	 * 
	 * @param path of the node to get
	 * @return Permission configuration node
	 */
	public final ConfigurationNode getPermissionNode(String path) {
		return this.permissionconfig.getNode(path);
	}

	/**
	 * Gets a localization configuration node
	 * 
	 * @param path of the node to get
	 * @return Localization configuration node
	 */
	public final ConfigurationNode getLocalizationNode(String path) {
		return this.localizationconfig.getNode(path);
	}

	/**
	 * Registers this main class for one or more commands
	 * 
	 * @param commands to register this Plugin class for
	 */
	public final void register(String... commands) {
		this.register(this, commands);
	}

	/**
	 * Registers a command executor for one or more commands
	 * 
	 * @param executor to register
	 * @param commands to register it for
	 */
	public final void register(CommandExecutor executor, String... commands) {
		for (String command : commands) {
			PluginCommand cmd = this.getCommand(command);
			if (cmd != null) {
				cmd.setExecutor(executor);
			}
		}
	}

	/**
	 * Registers a listener instance
	 * 
	 * @param listener to register
	 */
	public final void register(Listener listener) {
		if (listener != this) {
			Bukkit.getPluginManager().registerEvents(listener, this);
		}
	}

	/**
	 * Registers a listener class
	 * 
	 * @param listener class to register
	 */
	public final void register(Class<? extends Listener> listener) {
		try {
			this.register(listener.newInstance());
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Loads all the permissions from a Permissions enumeration
	 * 
	 * @param permissionDefaults class
	 */
	public final void loadPermissions(Class<? extends IPermissionDefault> permissionDefaults) {
		for (IPermissionDefault perm : permissionDefaults.getEnumConstants()) {
			this.loadPermission(perm);
		}
	}

	/**
	 * Loads a single permission using a permission default
	 * 
	 * @param permissionDefault to use
	 * @return Permission that was loaded
	 */
	public final Permission loadPermission(IPermissionDefault permissionDefault) {
		return this.loadPermission(permissionDefault.getName(), permissionDefault.getDefault(), permissionDefault.getDescription());
	}

	/**
	 * Loads a single permission using a permission path
	 * 
	 * @param path of the Permission
	 * @return Permission that was loaded
	 */
	public final Permission loadPermission(String path) {
		return this.loadPermission(getPermission(path));
	}

	/**
	 * Loads a single permission using a Permission
	 * 
	 * @param permission to load
	 * @return Permission that was loaded
	 */
	public final Permission loadPermission(Permission permission) {
		return this.loadPermission(permission.getName(), permission.getDefault(), permission.getDescription());
	}

	/**
	 * Loads a single permission using the path, default and description
	 * 
	 * @param path of the Permission
	 * @param def value of the Permission
	 * @param description value of the Permission
	 * @return Permission that was loaded
	 */
	public final Permission loadPermission(String path, PermissionDefault def, String description) {
		return this.loadPermission(getPermissionNode(path), def, description);
	}

	/**
	 * Loads a single permission using the configuration node, default and description
	 * 
	 * @param node to use for the permission path, default and description
	 * @param def value to use if the node is unusable
	 * @param description to use if the node is unusable
	 * @return Permission that was loaded
	 */
	public final Permission loadPermission(ConfigurationNode node, PermissionDefault def, String description) {
		Permission permission = getPermission(node.getPath());
		permission.setDefault(node.get("default", def));
		permission.setDescription(node.get("description", description));
		return permission;
	}

	/**
	 * Fired when the Permission nodes have to be created
	 */
	public void permissions() {
	}

	/**
	 * Fired when the Localization nodes have to be created
	 */
	public void localization() {
	}

	/**
	 * Gets the disable message shown when this Plugin disables
	 * 
	 * @return disable message
	 */
	public final String getDisableMessage() {
		return this.disableMessage;
	}

	/**
	 * Sets the disable message shown when this Plugin disables
	 * 
	 * @param msg to set to, null to disable the message
	 */
	public void setDisableMessage(String msg) {
		this.disableMessage = msg;
	}

	/**
	 * Gets the minimum BKCommonLib version required for this Plugin to function
	 * 
	 * @return Minimum BKCommonLib version number
	 */
	public int getMinimumLibVersion() {
		return 0;
	}

	/**
	 * Handles a possible throwable thrown somewhere in the Plugin<br>
	 * If the throwable is too severe, the plugin is automatically disabled<br>
	 * Additional exception types can be handled if needed
	 * 
	 * @param reason to throw
	 */
	public void handle(Throwable reason) {
		if (reason instanceof Exception) {
			reason.printStackTrace();
		} else if (reason instanceof OutOfMemoryError) {
			log(Level.SEVERE, "The server is running out of memory! Do something!");
		} else if (reason instanceof NoClassDefFoundError) {
			log(Level.WARNING, "Class is missing (plugin was hot-swapped?): " + reason.getMessage());
		} else {
			log(Level.SEVERE, "Encountered a critical error and had to be disabled.");
			log(Level.SEVERE, "You may have to update " + this.getName() + " or look for a newer CraftBukkit build.");
			reason.printStackTrace();
			Bukkit.getPluginManager().disablePlugin(this);
		}
	}

	/**
	 * Called when this Plugin loads up<br>
	 * <b>Do not forget to call super.onLoad() when overriding!</b>
	 */
	@Override
	public void onLoad() {
		// Load permission configuration
		this.permissionconfig = new FileConfiguration(this, "PermissionDefaults.yml");
		if (this.permissionconfig.exists()) {
			this.loadPermissions();
		}
		// header
		this.permissionconfig.setHeader("Below are the default permissions set for plugin '" + this.getName() + "'.");
		this.permissionconfig.addHeader("These permissions are ignored if the permission is set for a group or player.");
		this.permissionconfig.addHeader("Use the defaults as a base to keep the permissions file small");

		// Load localization configuration
		this.localizationconfig = new FileConfiguration(this, "Localization.yml");
		if (this.localizationconfig.exists()) {
			this.loadLocalization();
		}
		// header
		this.localizationconfig.setHeader("Below are the localization nodes set for plugin '" + this.getName() + "'.");

		// Load all the commands for this Plugin
		Map<String, Map<String, Object>> commands = this.getDescription().getCommands();
		if (commands != null && PluginDescriptionFileRef.commands.isValid()) {
			// Prepare commands localization node
			ConfigurationNode commandsNode = getLocalizationNode("commands");
			commandsNode.setHeader("Command descriptions and usages");

			// Create a new modifiable commands map to replace with
			commands = new HashMap<String, Map<String, Object>>(commands);
			for (Entry<String, Map<String, Object>> commandEntry : commands.entrySet()) {
				ConfigurationNode node = commandsNode.getNode(commandEntry.getKey());

				// Transfer description and usage
				Map<String, Object> data = new HashMap<String, Object>(commandEntry.getValue());
				node.shareWith(data, "description", "No description specified");
				node.shareWith(data, "usage", "/" + commandEntry.getKey());
				commandEntry.setValue(Collections.unmodifiableMap(data));
			}

			// Set the new commands map using reflection
			PluginDescriptionFileRef.commands.set(this.getDescription(), Collections.unmodifiableMap(commands));
		}
	}

	@Override
	public final void onEnable() {
		if (this.getMinimumLibVersion() > Common.VERSION) {
			log(Level.SEVERE, "Requires a newer BKCommonLib version, please update BKCommonLib to the latest version!");
			log(Level.SEVERE, "Verify that there is only one BKCommonLib.jar in the plugins folder before retrying");
			Bukkit.getPluginManager().disablePlugin(this);
			return;
		}

		this.setDisableMessage(this.getName() + " disabled!");

		// ==== Permissions ====
		this.permissions();
		if (!this.permissionconfig.isEmpty()) {
			this.savePermissions();
		}

		// ==== Localization ====
		this.localization();
		if (!this.localizationconfig.isEmpty()) {
			this.saveLocalization();
		}

		// ==== Enabling ====
		try {
			this.enable();
			this.enabled = true;
		} catch (Throwable t) {
			log(Level.SEVERE, "An error occurred while enabling, the plugin will be disabled");
			log(Level.SEVERE, "You may have to update " + this.getName() + " or look for a newer CraftBukkit build.");
			t.printStackTrace();
			Bukkit.getPluginManager().disablePlugin(this);
			return;
		}

		// update dependencies
		CommonPlugin.plugins.add(this);
		for (Plugin plugin : Bukkit.getPluginManager().getPlugins()) {
			if (plugin.isEnabled()) {
				this.updateDependency(plugin, plugin.getName(), true);
			}
		}

		Bukkit.getLogger().log(Level.INFO, this.getName() + " version " + this.getVersion() + " enabled!");
	}

	@Override
	public final void onDisable() {
		// are there any plugins that depend on me?
		List<String> depend;
		for (Plugin plugin : Bukkit.getServer().getPluginManager().getPlugins()) {
			if (!plugin.isEnabled())
				continue;
			depend = (List<String>) plugin.getDescription().getDepend();
			if (depend != null && depend.contains(this.getName())) {
				Bukkit.getServer().getPluginManager().disablePlugin(plugin);
			}
		}

		try {
			if (this.enabled) {
				this.disable();
				this.enabled = false;
			}
		} catch (Throwable t) {
			log(Level.SEVERE, "An error occurred while disabling:");
			t.printStackTrace();
			CommonPlugin.plugins.remove(this);
			return;
		}

		CommonPlugin.plugins.remove(this);

		if (this.disableMessage != null) {
			Bukkit.getLogger().log(Level.INFO, this.disableMessage);
		}
	}

	@Override
	public final boolean onCommand(CommandSender sender, org.bukkit.command.Command cmd, String command, String[] args) {
		try {
			args = StringUtil.convertArgs(args);
			// Handle regularly
			if (command(sender, command, args)) {
				return true;
			}
			// Default commands for all plugins
			if (args.length >= 1 && LogicUtil.contains(args[0].toLowerCase(), "version", "ver")) {
				sender.sendMessage(ChatColor.GREEN + this.getName() + " v" + this.getVersion() + " using BKCommonLib v" + CommonPlugin.instance.getVersion());
			}
			// Handle regularly
			sender.sendMessage(ChatColor.RED + "Unknown command, for help use /help " + command);
		} catch (NoPermissionException ex) {
			if (sender instanceof Player) {
				sender.sendMessage(ChatColor.RED + "You do not have permission to use this command!");
			} else {
				sender.sendMessage("This command is only for players!");
			}
		} catch (Throwable t) {
			StringBuilder msg = new StringBuilder("Unhandled exception executing command '");
			msg.append(command).append("' in plugin ").append(this.getName()).append(" v").append(this.getVersion());
			Bukkit.getLogger().log(Level.SEVERE, msg.toString());
			t.printStackTrace();
			sender.sendMessage(ChatColor.RED + "An internal error occured while executing this command");
		}
		return true;
	}

	/**
	 * Called when this plugin is being enabled
	 */
	public abstract void enable();

	/**
	 * Called when this plugin is being disabled
	 */
	public abstract void disable();

	/**
	 * Handles a command
	 * 
	 * @param sender of the command
	 * @param command name
	 * @param args of the command
	 * @return True if handled, False if not
	 */
	public abstract boolean command(CommandSender sender, String command, String[] args);

	public final void loadLocalization() {
		this.localizationconfig.load();
	}

	public final void saveLocalization() {
		this.localizationconfig.save();
	}
	
	public final void loadPermissions() {
		this.permissionconfig.load();
	}

	public final void savePermissions() {
		this.permissionconfig.save();
	}

	/**
	 * Called when a plugin is enabled or disabled
	 * 
	 * @param plugin that got enabled or disabled
	 * @param pluginName of the plugin
	 * @param enabled state, True if enabled, False if disabled
	 */
	public void updateDependency(Plugin plugin, String pluginName, boolean enabled) {
	};
}
