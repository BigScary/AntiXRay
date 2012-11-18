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
import org.bukkit.entity.Player;

//singleton class which manages all AntiXRay data (except for config options)
class FlatFileDataStore extends DataStore
{
	final static String playerDataFolderPath = dataLayerFolderPath + File.separator + "PlayerData";
	
	FlatFileDataStore()
	{
		this.initialize();
	}
	
	@Override
	void initialize()
	{
		super.initialize();
		
		//ensure data folders exist
		new File(playerDataFolderPath).mkdirs();
	}
	
	@Override
	PlayerData loadPlayerDataFromStorage(Player player)
	{
		String playerName = player.getName();
		
		File playerFile = new File(playerDataFolderPath + File.separator + playerName);
						
		PlayerData playerData = new PlayerData();
		
		//if it doesn't exist as a file
		if(!playerFile.exists())
		{
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
			
			//create a file with defaults
			this.savePlayerData(playerName, playerData);
		}
		
		//otherwise, read the file
		else
		{			
			BufferedReader inStream = null;
			try
			{					
				inStream = new BufferedReader(new FileReader(playerFile.getAbsolutePath()));
				
				//first line is points
				String pointsString = inStream.readLine();
				
				//convert that to a number and store it
				playerData.points = Integer.parseInt(pointsString);
				
				inStream.close();
			}
				
			//if there's any problem with the file's content, log an error message
			catch(Exception e)
			{
				 AntiXRay.AddLogEntry("Unable to load data for player \"" + playerName + "\": " + e.getMessage());			 
			}
			
			try
			{
				if(inStream != null) inStream.close();
			}
			catch(IOException exception) {}
		}
		
		return playerData;
	}
	
	@Override
	void savePlayerData(String playerName, PlayerData playerData)
	{
		BufferedWriter outStream = null;
		try
		{
			//open the player's file
			File playerDataFile = new File(playerDataFolderPath + File.separator + playerName);
			playerDataFile.createNewFile();
			outStream = new BufferedWriter(new FileWriter(playerDataFile));
			
			//first line is available points
			outStream.write(String.valueOf(playerData.points));
			outStream.newLine();			
		}		
		
		//if any problem, log it
		catch(Exception e)
		{
			AntiXRay.AddLogEntry("Unexpected exception saving data for player \"" + playerName + "\": " + e.getMessage());
		}
		
		try
		{
			//close the file
			if(outStream != null)
			{
				outStream.close();
			}
		}
		catch(IOException exception){}
	}
	
	@Override
	void close()
	{ 
		//nothing to do here because files are not left open after reading or writing
	}
}
