/*
    AntiXRay Server Plugin for Minecraft
    Copyright (C) 2012 Ryan Hamshire

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package me.ryanhamshire.AntiXRay;

import java.io.*;
import java.util.*;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

//abstract class for data storage.  implementing classes fill the implementation gaps for flat file storage and database storage, respectively
abstract class DataStore 
{
	//in-memory cache for player data
	private HashMap<String, PlayerData> playerNameToPlayerDataMap = new HashMap<String, PlayerData>();
	
	//in-memory cache for messages
	private String [] messages;
	
	//path information, for where stuff stored on disk is well...  stored
	final static String dataLayerFolderPath = "plugins" + File.separator + "AntiXRayData";
	final static String configFilePath = dataLayerFolderPath + File.separator + "config.yml";
	final static String messagesFilePath = dataLayerFolderPath + File.separator + "messages.yml";
	
	//initialization varies depending on flat file or database storage
	void initialize()
	{
		//load up all the messages from messages.yml
		this.loadMessages();
	}
	
	//removes cached player data from memory
	void clearCachedPlayerData(String playerName)
	{
		this.playerNameToPlayerDataMap.remove(playerName);
	}
	
	//retrieves player data from memory or file, as necessary
	//if the player has never been on the server before, this will return a fresh player data with default values
	public PlayerData getPlayerData(Player player)
	{
		String playerName = player.getName();
		
		//first, look in memory
		PlayerData playerData = this.playerNameToPlayerDataMap.get(playerName);
		
		//if not there, look on disk
		if(playerData == null)
		{
			playerData = this.loadPlayerDataFromStorage(player);
			
			//shove that new player data into the hash map cache
			this.playerNameToPlayerDataMap.put(playerName, playerData);
		}
		
		//try the hash map again.  if it's STILL not there, we have a bug to fix
		return this.playerNameToPlayerDataMap.get(playerName);
	}
	
	PlayerData getDefaultPlayerData(Player player)
	{
		PlayerData playerData = new PlayerData();
		
		//default points = max when the player has played on the server before
		//otherwise, use starting points from config file
		if(!player.hasPlayedBefore())
		{
			playerData.points = AntiXRay.instance.config_startingPoints;
		}
		else
		{
			playerData.points = AntiXRay.instance.config_maxPoints;
		}
		
		return playerData;
	}
	
	//implementation varies depending on flat file or database storage
	abstract PlayerData loadPlayerDataFromStorage(Player player);
	
	//saves changes to player data.  MUST be called after you're done making changes, otherwise a reload will lose them
	//implementation varies based on flat file or database storage
	abstract void savePlayerData(String playerName, PlayerData playerData);
	
	//loads user-facing messages from the messages.yml configuration file into memory
	private void loadMessages() 
	{
		Messages [] messageIDs = Messages.values();
		this.messages = new String[Messages.values().length];
		
		HashMap<String, CustomizableMessage> defaults = new HashMap<String, CustomizableMessage>();
		
		//initialize defaults
		this.addDefault(defaults, Messages.CantBreakYet, "Wow, you're good at mining!  You have to wait about {0} minutes to break this block.  If you wait longer, you can mine even more of this.  Consider taking a break from mining to do something else, like building or exploring.  This mining speed limit keeps our ores safe from cheaters.  :)", "0: minutes until the block can be broken");
		this.addDefault(defaults, Messages.AdminNotification, "{0} reached the mining speed limit.", "0: player name");
		
		//load the config file
		FileConfiguration config = YamlConfiguration.loadConfiguration(new File(messagesFilePath));
		
		//for each message ID
		for(int i = 0; i < messageIDs.length; i++)
		{
			//get default for this message
			Messages messageID = messageIDs[i];
			CustomizableMessage messageData = defaults.get(messageID.name());
			
			//if default is missing, log an error and use some fake data for now so that the plugin can run
			if(messageData == null)
			{
				AntiXRay.AddLogEntry("Missing message for " + messageID.name() + ".  Please contact the developer.");
				messageData = new CustomizableMessage(messageID, "Missing message!  ID: " + messageID.name() + ".  Please contact a server admin.", null);
			}
			
			//read the message from the file, use default if necessary
			this.messages[messageID.ordinal()] = config.getString("Messages." + messageID.name() + ".Text", messageData.text);
			config.set("Messages." + messageID.name() + ".Text", this.messages[messageID.ordinal()]);
			
			if(messageData.notes != null)
			{
				messageData.notes = config.getString("Messages." + messageID.name() + ".Notes", messageData.notes);
				config.set("Messages." + messageID.name() + ".Notes", messageData.notes);
			}
		}
		
		//save any changes
		try
		{
			config.save(DataStore.messagesFilePath);
		}
		catch(IOException exception)
		{
			AntiXRay.AddLogEntry("Unable to write to the configuration file at \"" + DataStore.messagesFilePath + "\"");
		}
		
		defaults.clear();
		System.gc();				
	}

	//helper for above, adds a default message and notes to go with a message ID
	private void addDefault(HashMap<String, CustomizableMessage> defaults,
			Messages id, String text, String notes)
	{
		CustomizableMessage message = new CustomizableMessage(id, text, notes);
		defaults.put(id.name(), message);		
	}

	//gets a message from memory
	public String getMessage(Messages messageID, String... args)
	{
		String message = messages[messageID.ordinal()];
		
		for(int i = 0; i < args.length; i++)
		{
			String param = args[i];
			message = message.replace("{" + i + "}", param);
		}
		
		return message;
		
	}
	
	//closes any open connections.  implementation varies depending on flat file or database storage.
	abstract void close();
}
