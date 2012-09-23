/**
 * HyperSignsBukkit - Bukkit plugin for extended in-game sign interaction.
 * Copyright (C) 2012, Dion Williams
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package uk.co.didz.hypersignsbukkit;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

public class HyperSignsBukkit extends JavaPlugin {

	protected static final String CHANNEL_NAME = "HyperSigns";
	protected Logger log;
	protected PluginManager pm;
	private EventListener eventListener;
	private String fallbackUrlTriggerPrefix;
	private String fallbackUrlTriggerSuffix;

	/**
	 * Map of hyper signs in all worlds. Key: Block location. Value: URL as
	 * string.
	 */
	protected HashMap<Location, URL> loadedSigns;

	/**
	 * Map of players in "sign editing" mode. Key: Player name. Value: URL to
	 * set as string (null for resetting).
	 */
	protected HashMap<String, URL> signEditPlayers;

	@Override
	public void onEnable() {
		log = getLogger();
		pm = getServer().getPluginManager();

		// Save default configuration fields and header to config.yml
		// (only if they haven't been set already)
		FileConfiguration config = getConfig();
		config.options().copyDefaults(true);
		config.options().copyHeader(true);
		saveConfig();

		fallbackUrlTriggerPrefix = config.getString("fallbackUrlTriggerPrefix");
		fallbackUrlTriggerSuffix = config.getString("fallbackUrlTriggerSuffix");

		// Load all hyper signs into memory
		loadSignsData();

		// Initialise the list of players currently editing sign URLs
		signEditPlayers = new HashMap<String, URL>();

		// Register the plugin's outgoing channel to communicate with players
		getServer().getMessenger().registerOutgoingPluginChannel(this, CHANNEL_NAME);

		eventListener = new EventListener(this);

		// Register this class to listen to events
		pm.registerEvents(eventListener, this);

		log.info("Enabled!");
	}

	@Override
	public void onDisable() {
		// Unregister the plugin's outgoing channel
		getServer().getMessenger().unregisterOutgoingPluginChannel(this, CHANNEL_NAME);

		log.info("Disabled!");

		// Release all our handles now. This is helpful for the garbage
		// collector if the plugin object is kept after being disabled.
		eventListener = null;
		loadedSigns = null;
		signEditPlayers = null;

		pm = null;
		log = null;
	}

	/**
	 * Send a URL Trigger to the player.
	 * 
	 * If the player doesn't have the HyperSigns client, the URL will be
	 * messaged in the chat window.
	 * 
	 * @param recipient
	 * @param url
	 */
	protected void sendUrlTrigger(Player recipient, URL url) {
		// Check if the player has the custom client
		if (recipient.getListeningPluginChannels().contains(CHANNEL_NAME)) {
			byte[] message;
			try {
				message = ServerMessageComposer.writeClientUrlTrigger(url);
			} catch (IOException e) {
				log.log(Level.SEVERE, "Unable to create URL Trigger packet.", e);
				return;
			}
			recipient.sendPluginMessage(this, CHANNEL_NAME, message);
		} else {
			recipient.sendMessage(fallbackUrlTriggerPrefix + url.toString()
					+ fallbackUrlTriggerSuffix);
		}
	}

	/**
	 * Attempt to load signs. If the data file doesn't exist, create it.
	 */
	protected void loadSignsData() {
		log.info("Loading signs:");

		loadedSigns = new HashMap<Location, URL>();

		if (!getDataFolder().exists()) {
			getDataFolder().mkdirs();
		}

		File signsFile = new File(getDataFolder(), "signs.json");
		try {
			if (signsFile.createNewFile() == true) {
				log.info("Created data file signs.json successfully.");
				return;
			}
		} catch (IOException e) {
			log.severe("Unable to create signs.json. Disabling plugin.");
			e.printStackTrace();
			pm.disablePlugin(this);
			return;
		}

		if (signsFile.length() == 0) {
			log.info("No signs loaded. (signs.json is empty)");
			return;
		}

		FileReader fileReader;
		try {
			fileReader = new FileReader(signsFile);
		} catch (FileNotFoundException e) {
			// Shouldn't happen cause the file was created up there ^
			return;
		}

		JSONParser parser = new JSONParser();
		Object array;

		try {
			array = parser.parse(fileReader);
		} catch (ParseException pe) {
			log.severe("Unable to parse signs.json! Disabling plugin.");
			log.severe(pe.toString());
			pm.disablePlugin(this);
			return;
		} catch (IOException e) {
			log.severe("IOException while trying to parse signs.json! Disabling plugin.");
			e.printStackTrace();
			pm.disablePlugin(this);
			return;
		}

		// Ensure that the root element is an array
		JSONArray arr;
		try {
			arr = (JSONArray) array;
		} catch (ClassCastException e) {
			log.severe("Error while loading signs.json, the root element isn't an array! Disabling plugin.");
			e.printStackTrace();
			pm.disablePlugin(this);
			return;
		}

		int blockNum = 0; // For error messages
		for (Object object : arr) {
			JSONObject obj;
			try {
				obj = (JSONObject) object;
			} catch (ClassCastException e) {
				log.warning("Found a non-object in the blocks list while loading signs.json. Skipping.");
				continue;
			}

			blockNum++;

			if (!obj.containsKey("world") || !obj.containsKey("x") || !obj.containsKey("y")
					|| !obj.containsKey("z") || !obj.containsKey("url")) {
				log.warning("Missing data for block #" + blockNum
						+ " while loading signs.json. Skipping.");
				continue;
			}

			String worldName, urlString;
			int x, y, z;
			try {
				worldName = (String) obj.get("world");
				x = ((Number) obj.get("x")).intValue();
				y = ((Number) obj.get("y")).intValue();
				z = ((Number) obj.get("z")).intValue();
				urlString = (String) obj.get("url");
			} catch (ClassCastException e) {
				log.warning("Found malformed data for a block while loading signs.json. Skipping.");
				continue;
			}

			assert (worldName != null && urlString != null);

			// Ensure world for this block exists
			World world = getServer().getWorld(worldName);
			if (world == null) {
				log.warning("The world '" + worldName + "' for block #" + blockNum
						+ " doesn't exist on the server. Skipping.");
				continue;
			}

			Location location = new Location(world, x, y, z);

			// Make sure the URL is valid
			URL url = validateURL(urlString);
			if (url == null) {
				log.warning("The URL for block #" + blockNum + "is invalid. Skipping.");
				continue;
			}

			loadedSigns.put(location.clone(), url);
		}

		log.info(loadedSigns.size() + " signs loaded.");
	}

	/**
	 * Attempt to save signs. If the data file doesn't exist, create it.
	 */
	protected void saveSignsData() {
		File signsFile = new File(getDataFolder(), "signs.json");
		FileWriter fileWriter;
		try {
			fileWriter = new FileWriter(signsFile);
		} catch (IOException e) {
			log.severe("Unable to open signs.json for writing.");
			e.printStackTrace();
			return;
		}

		List<Map<String, Object>> signsList = new ArrayList<Map<String, Object>>();

		for (Entry<Location, URL> entry : loadedSigns.entrySet()) {
			Location location = entry.getKey();
			URL url = entry.getValue();

			// Using LinkedHashMap for the insertion-order retention
			Map<String, Object> obj = new LinkedHashMap<String, Object>();
			obj.put("world", location.getWorld().getName());
			obj.put("x", (int) location.getX());
			obj.put("y", (int) location.getY());
			obj.put("z", (int) location.getZ());
			obj.put("url", url.toString());
			signsList.add(obj);
		}

		try {
			// Save the file
			JSONArray.writeJSONString(signsList, fileWriter);
			fileWriter.close();
		} catch (IOException e) {
			log.severe("Unable to open signs.json for writing.");
			e.printStackTrace();
			return;
		}
	}

	/**
	 * Handle all the HyperSigns commands. Yes I know it's all in one file...
	 */
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

		if (command.getName().equalsIgnoreCase("url")) {
			/* Prompt an online player to visit a URL */

			if (!sender.hasPermission("hypersigns.command.url")) {
				sender.sendMessage(command.getPermissionMessage());
				return true;
			}

			if (args.length < 2) {
				sender.sendMessage("Not enough arguments.");
				sender.sendMessage(command.getUsage());
				return true;
			}

			if (args.length > 2) {
				sender.sendMessage("Too many arguments.");
				sender.sendMessage(command.getUsage());
				return true;
			}

			// Ensure first argument is an online player
			Player recipient = Bukkit.getPlayer(args[0]);
			if (recipient == null) {
				sender.sendMessage("'" + args[0] + "' does not match an online player.");
				return true;
			}

			// Ensure second argument is a valid URL
			URL url = validateURL(args[1]);
			if (url == null) {
				sender.sendMessage("'" + args[1] + "' is not a valid URL.");
				return true;
			}

			// We have a valid URL, send it to the player!
			sendUrlTrigger(recipient, url);

			return true;
		} else if (command.getName().equalsIgnoreCase("signurl")) {
			/* Set or remove a sign's URL trigger */

			if (!(sender instanceof Player)) {
				sender.sendMessage("This command can only be used in-game.");
			}

			Player player = (Player) sender;

			if (!player.hasPermission("hypersigns.command.signurl")) {
				player.sendMessage(command.getPermissionMessage());
				return true;
			}

			if (args.length > 1) {
				player.sendMessage("Too many arguments.");
				player.sendMessage(command.getUsage());
				return true;
			}

			if (!signEditPlayers.containsKey(player.getName())) {
				/* Player wants to set a sign's URL */

				if (args.length == 1) {
					// Ensure first argument is a valid URL
					URL url = validateURL(args[0]);
					if (url == null) {
						player.sendMessage(ChatColor.RED + "'" + args[0] + "' is not a valid URL.");
						return true;
					}
					signEditPlayers.put(player.getName(), url);
				} else {
					// No URL given, so right clicking will remove a link
					// (instead of setting)
					signEditPlayers.put(player.getName(), null);
				}

				player.sendMessage(ChatColor.YELLOW + "Right click on a sign to set its URL.");
				player.sendMessage(ChatColor.YELLOW + ChatColor.ITALIC.toString()
						+ "Do /signurl again to cancel.");
			} else {
				/* Player wants to cancel the action */

				signEditPlayers.remove(player.getName());
				sender.sendMessage(ChatColor.GREEN + "Cancelled.");
			}

			return true;
		}

		return false;
	}

	/**
	 * Validate and return a URL from the given input string. Encodes special
	 * characters in the URL.
	 * 
	 * @param inputUrl Input URL string to validate.
	 * @return URL object if valid, otherwise null.
	 */
	protected URL validateURL(String inputUrl) {
		assert inputUrl != null;

		try {
			// URI class encodes special characters, URL class doesn't.
			// http://docs.oracle.com/javase/6/docs/api/java/net/URL.html
			// Therefore convert String to URI, then URI to URL.
			URI uri = new URI(inputUrl);
			return uri.toURL();
		} catch (URISyntaxException e) {
			return null;
		} catch (MalformedURLException e) {
			return null;
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	/**
	 * Check if the given block is a hyper sign.
	 * 
	 * @param block
	 */
	protected boolean isHyperSign(Block block) {
		// Ignore blocks that aren't signs
		if (block.getType() != Material.SIGN_POST && block.getType() != Material.WALL_SIGN) {
			return false;
		}

		// Look to see if this sign is loaded as a hypersign
		URL url = loadedSigns.get(block.getLocation());
		if (url == null) {
			return false;
		}

		return true;
	}

	/**
	 * Check if the given player is in sign edit mode.
	 * 
	 * @param player
	 */
	protected boolean isPlayerEditing(Player player) {
		if (signEditPlayers.containsKey(player.getName())) {
			return true;
		}

		return false;
	}
}