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

import java.net.URL;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class EventListener implements Listener {

	private HyperSignsBukkit plugin;

	public EventListener(HyperSignsBukkit p) {
		plugin = p;
	}

	/**
	 * React to players interacting with a hyper sign.
	 * 
	 * @param event
	 */
	@EventHandler(ignoreCancelled = true)
	public void onPlayerInteract(PlayerInteractEvent event) {
		// Silently dismiss the event if the player doesn't have permission to
		// interact, and isn't in "sign editing" mode
		Player player = event.getPlayer();
		if (!player.hasPermission("hypersigns.sign.interact")
				&& !plugin.signEditPlayers.containsKey(player.getName())) {
			return;
		}

		// Ignore the event if it wasn't a block click
		if (event.getAction() != Action.LEFT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
			return;
		}

		Block block = event.getClickedBlock();

		// Ignore right clicks on any blocks that aren't signs
		if (block.getType() != Material.SIGN_POST && block.getType() != Material.WALL_SIGN) {
			return;
		}

		// If the player isn't in sign-editing mode, treat this as a normal
		// interact
		if (!plugin.signEditPlayers.containsKey(player.getName())) {
			// Look to see if this sign is loaded as a hypersign
			URL url = plugin.loadedSigns.get(block.getLocation());
			if (url == null) {
				return;
			}

			// This is a hyper sign so cancel the event now
			event.setCancelled(true);

			plugin.sendUrlTrigger(player, url);
		} else {
			// We're handling sign editing mode so cancel the event now
			event.setCancelled(true);

			URL url = plugin.signEditPlayers.get(player.getName());
			if (url != null) {
				plugin.loadedSigns.put(block.getLocation(), url);
				player.sendMessage(ChatColor.GREEN + "The sign's URL has been set.");
			} else {
				plugin.loadedSigns.remove(block.getLocation());
				player.sendMessage(ChatColor.GREEN + "Removed the sign's URL.");
			}

			// Take player out of edit mode
			plugin.signEditPlayers.remove(player.getName());

			// Save sign data to disk
			plugin.saveSignsData();
		}
	}

	/**
	 * Clear sign data when broken.
	 */
	@EventHandler(ignoreCancelled = true)
	public void onBlockBreak(BlockBreakEvent event) {
		clearSignData(event.getBlock());
	}

	/**
	 * Clear sign data when burnt.
	 */
	@EventHandler(ignoreCancelled = true)
	public void onBlockBurn(BlockBurnEvent event) {
		clearSignData(event.getBlock());
	}

	/**
	 * Clear sign data when faded.
	 */
	@EventHandler(ignoreCancelled = true)
	public void onBlockFade(BlockFadeEvent event) {
		clearSignData(event.getBlock());
	}

	/**
	 * Clear sign data when new sign is placed down.
	 * 
	 * This is only needed in case the location of the block is "unclean" and
	 * contains sign data already.
	 */
	@EventHandler(ignoreCancelled = true)
	public void onBlockPlace(BlockPlaceEvent event) {
		clearSignData(event.getBlock());
	}

	/**
	 * Clear link data from a sign for any reason. Called when sign is broken or
	 * new normal sign is placed.
	 * 
	 * @param block
	 */
	private void clearSignData(Block block) {
		if (plugin.isHyperSign(block)) {
			plugin.loadedSigns.remove(block.getLocation());
			plugin.saveSignsData();
		}
	}

	/**
	 * Remove player's edit mode if they quit.
	 */
	@EventHandler(ignoreCancelled = true)
	public void onPlayerQuit(PlayerQuitEvent event) {
		clearPlayerEditing(event.getPlayer());
	}

	/**
	 * Remove player's edit mode if they're kicked.
	 */
	@EventHandler(ignoreCancelled = true)
	public void onPlayerKick(PlayerKickEvent event) {
		clearPlayerEditing(event.getPlayer());
	}

	/**
	 * Remove player's edit mode for any reason. Called when a player leaves or
	 * joins the server.
	 * 
	 * @param player
	 */
	private void clearPlayerEditing(Player player) {
		if (plugin.isPlayerEditing(player)) {
			plugin.signEditPlayers.remove(player.getName());
		}
	}
}
