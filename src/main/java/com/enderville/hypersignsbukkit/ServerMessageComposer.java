/**
 * HyperSignsBukkit - Bukkit plugin for extended in-game sign interaction.
 * Copyright (C) 2012, EnderVille.com
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

package com.enderville.hypersignsbukkit;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URL;

/**
 * Facilitates parsing and creating HyperSigns networked messages. This class
 * doesn't do any networking itself.
 * 
 * @author Didz
 */
public class ServerMessageComposer {

	public enum MessageType {
		/**
		 * Prompts the client to open a URL.
		 */
		URL_TRIGGER(0x01);

		private final int value;

		private MessageType(int value) {
			this.value = value;
		}

		public int getValue() {
			return value;
		}
	}

	/**
	 * Writes a URL trigger message for a client.
	 * 
	 * @param url
	 * @return The message as a byte array. null on failure.
	 */
	public static byte[] writeClientUrlTrigger(URL url) {
		ByteArrayOutputStream message = new ByteArrayOutputStream();

		// Prefix the message with the URL_TRIGGER identifier
		message.write(MessageType.URL_TRIGGER.getValue());

		// Append the message with the URL
		try {
			message.write(url.toString().getBytes());
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}

		return message.toByteArray();
	}

}
