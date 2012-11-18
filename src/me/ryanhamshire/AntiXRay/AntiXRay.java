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

import java.io.File;
import java.io.IOException;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public class AntiXRay extends JavaPlugin
{
	//for convenience, a reference to the instance of this plugin
	public static AntiXRay instance;
	
	//for logging to the console and log file
	private static Logger log = Logger.getLogger("Minecraft");
	
	//this handles data storage, like player data
	public DataStore dataStore;
	
	//configuration variables, loaded/saved from a config.yml
	public ArrayList<World> config_enabledWorlds;								//list of worlds where players are limited in how much quickly they can mine valuable ores
	public int config_pointsPerHour;											//how quickly players earn "points" which allow them to mine valuables
	public int config_maxPoints;												//the upper limit on points
	public int config_startingPoints;											//initial points for players who are new to the server
	public ArrayList<SimpleEntry<Material, Integer>> config_protectedBlocks;  	//points required to break various block types
	public boolean config_exemptCreativeModePlayers;							//whether creative mode players should be exempt from the rules
	public boolean config_notifyOnLimitReached;									//whether to notify online moderators when a player reaches his limit
	
	//adds a server log entry
	public static void AddLogEntry(String entry)
	{
		log.info("AntiXRay: " + entry);
	}
	
	//initializes well...   everything
	public void onEnable()
	{ 		
		AddLogEntry("AntiXRay enabled.");
		
		instance = this;
		
		//load the config if it exists
		FileConfiguration config = YamlConfiguration.loadConfiguration(new File(DataStore.configFilePath));
		
		//read configuration settings (note defaults)
		
		//default for worlds list (all worlds)
		ArrayList<String> defaultWorldNames = new ArrayList<String>();
		List<World> worlds = this.getServer().getWorlds(); 
		for(int i = 0; i < worlds.size(); i++)
		{
			defaultWorldNames.add(worlds.get(i).getName());
		}
		
		//get claims world names from the config file
		List<String> enabledWorldNames = config.getStringList("AntiXRay.Worlds");
		if(enabledWorldNames == null || enabledWorldNames.size() == 0)
		{			
			enabledWorldNames = defaultWorldNames;
		}
		
		//validate that list
		this.config_enabledWorlds = new ArrayList<World>();
		for(int i = 0; i < enabledWorldNames.size(); i++)
		{
			String worldName = enabledWorldNames.get(i);
			World world = this.getServer().getWorld(worldName);
			if(world == null)
			{
				AddLogEntry("Error: Configuration: There's no world named \"" + worldName + "\".  Please update your config.yml.");
			}
			else
			{
				this.config_enabledWorlds.add(world);
			}
		}
		
		this.config_startingPoints = config.getInt("AntiXRay.NewPlayerStartingPoints", -400);
		this.config_pointsPerHour = config.getInt("AntiXRay.PointsEarnedPerHourPlayed", 800);
		this.config_maxPoints = config.getInt("AntiXRay.MaximumPoints", 1600);
		
		this.config_exemptCreativeModePlayers = config.getBoolean("AntiXRay.ExemptCreativeModePlayers", true);
		
		this.config_notifyOnLimitReached = config.getBoolean("AntiXRay.NotifyOnMiningLimitReached", false);
		
		//try to load the list of valuable ores from the config file
		ArrayList<String> protectedBlockNames = new ArrayList<String>();
		ConfigurationSection protectedBlocksSection = config.getConfigurationSection("AntiXRay.ProtectedBlockValues");
		if(protectedBlocksSection != null)
		{
			Set<String> names = protectedBlocksSection.getKeys(false);
			Iterator<String> iterator = names.iterator();
			while(iterator.hasNext())
			{
				protectedBlockNames.add(iterator.next());
			}
		}		
		
		//validate the list
		this.config_protectedBlocks = new ArrayList<SimpleEntry<Material, Integer>>();
		for(int i = 0; i < protectedBlockNames.size(); i++)
		{
			String blockName = protectedBlockNames.get(i);
			
			//validate the material name
			Material material = Material.getMaterial(blockName);
			if(material == null)
			{
				AntiXRay.AddLogEntry("Material not found: " + blockName + ".");
				continue;
			}			
			
			//read the material value
			int materialValue = config.getInt("AntiXRay.ProtectedBlockValues." + blockName, 0);
			this.config_protectedBlocks.add(new SimpleEntry<Material, Integer>(material, materialValue));
		}
		
		//default for the protected blocks list
		if(this.config_protectedBlocks.size() == 0)
		{
			this.config_protectedBlocks.add(new SimpleEntry<Material, Integer>(Material.DIAMOND_ORE, 100));
			this.config_protectedBlocks.add(new SimpleEntry<Material, Integer>(Material.EMERALD_ORE, 50));
		}
		
		//write all those configuration values back to file
		//(this writes the defaults to the config file when nothing is specified)
		config.set("AntiXRay.Worlds", enabledWorldNames);
		config.set("AntiXRay.NewPlayerStartingPoints", this.config_startingPoints);
		config.set("AntiXRay.PointsEarnedPerHourPlayed", this.config_pointsPerHour);
		config.set("AntiXRay.MaximumPoints", this.config_maxPoints);
		
		config.set("AntiXRay.ExemptCreativeModePlayers", this.config_exemptCreativeModePlayers);
		
		config.set("AntiXRay.NotifyOnMiningLimitReached", this.config_notifyOnLimitReached);
		
		for(int i = 0; i < this.config_protectedBlocks.size(); i++)
		{
			SimpleEntry<Material, Integer> entry = this.config_protectedBlocks.get(i);
			config.set("AntiXRay.ProtectedBlockValues." + entry.getKey(), entry.getValue().intValue());
		}
		
		try
		{
			config.save(DataStore.configFilePath);
		}
		catch(IOException exception)
		{
			AddLogEntry("Unable to write to the configuration file at \"" + DataStore.configFilePath + "\"");
		}
		
		this.dataStore = new FlatFileDataStore();
		
		//start the task to regularly give players the points they've earned for play time
		//20L ~ 1 second
		DeliverPointsTask task = new DeliverPointsTask();
		this.getServer().getScheduler().scheduleSyncRepeatingTask(this, task, 20L * 60 * 5, 20L * 60 * 5);
		
		//register for events
		PluginManager pluginManager = this.getServer().getPluginManager();
		
		//player events
		PlayerEventHandler playerEventHandler = new PlayerEventHandler(this.dataStore);
		pluginManager.registerEvents(playerEventHandler, this);
		
		//block events
		BlockEventHandler blockEventHandler = new BlockEventHandler(this.dataStore);
		pluginManager.registerEvents(blockEventHandler, this);
				
		//entity events
		EntityEventHandler entityEventHandler = new EntityEventHandler();
		pluginManager.registerEvents(entityEventHandler, this);
	}
	
	//on disable, close any open files and/or database connections
	public void onDisable()
	{
		//ensure all online players get their data saved
		Player [] players = this.getServer().getOnlinePlayers();
		for(int i = 0; i < players.length; i++)
		{
			Player player = players[i];
			String playerName = player.getName();
			this.dataStore.savePlayerData(playerName, this.dataStore.getPlayerData(player));
		}
		
		this.dataStore.close();
	}
	
	//handles slash commands
	public boolean onCommand(CommandSender sender, Command cmd, String commandLabel, String[] args)
	{
		return true;
	}
	
	//sends a color-coded message to a player
	static void sendMessage(Player player, ChatColor color, Messages messageID, String... args)
	{
		String message = AntiXRay.instance.dataStore.getMessage(messageID, args);
		sendMessage(player, color, message);
	}
	
	//sends a color-coded message to a player
	static void sendMessage(Player player, ChatColor color, String message)
	{
		if(player == null)
		{
			AntiXRay.AddLogEntry(color + message);
		}
		else
		{
			player.sendMessage(color + message);
		}
	}
	
	//creates an easy-to-read location description
	public static String getfriendlyLocationString(Location location) 
	{
		return location.getWorld().getName() + "(" + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ() + ")";
	}
}