/*******************************************************************************
 *
 * SenSocial Middleware
 *
 * Copyright (c) ${2014}, University of Birmingham
 * Abhinav Mehrotra, axm514@cs.bham.ac.uk
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of the University of Birmingham 
 *       nor the names of its contributors may be used to endorse or
 *       promote products derived from this software without specific prior
 *       written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE ABOVE COPYRIGHT HOLDERS BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
 * THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 *******************************************************************************/
package com.ubhave.sensocial.sensormanager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.util.Log;

import com.ubhave.sensocial.filters.Condition;
import com.ubhave.sensocial.filters.ConfigurationHandler;
import com.ubhave.sensocial.filters.ModalValue;
import com.ubhave.sensocial.filters.ModalityType;
import com.ubhave.sensocial.filters.Operator;
import com.ubhave.sensormanager.ESException;

/**
 * Provides the way to classify required sensors based on their streams, i.e.: OSN sensor or Stream sensor.
 * Stream sensors are directly subscribed and the data is collected on specific intervals.
 * But the OSN sensor are sensed vis One-Off-Sensing process which is triggered when there is a new trigger 
 * from OSNs. This makes the system energy efficient.
 */
public class SensorClassifier {
	/**
	 * This method create two sets of configurations: OSN dependent(OSNConfigurationSet) 
	 * & Continuous stream dependent(StreamConfigurationSet).
	 * <We used two sets of configuration so that after classification of streams we filter only for Stream configs>
	 * OSN dependent configuration require one-off sensing when ever there is a new update from OSN.
	 * Continuous stream dependent configurations require sensor subscription for continuous sensing.
	 * This method also create two sets of sensors: OSN dependent (OSNSensorSet) 
	 * & Stream dependent (StreamSensorSet).
	 * And subscribes/unsubscribes sensors.
	 * @param context Application context
	 * @param filterConfigs Map of configurations names and set of conditions
	 */
	public static void run(Context context, Map<String, Set<String>> filterConfigs) {
		System.out.println("Sensor classifier: run");
		System.out.println("Map recieved: "+filterConfigs);
		Map<String, Set<String>> osnConfigs =new HashMap<String, Set<String>>();
		Map<String, Set<String>> streamConfigs =new HashMap<String, Set<String>>();
		Set<String> blankSet=new HashSet<String>();
		//Classify new configurations as OSN & Stream
		for(Map.Entry<String, Set<String>> c: filterConfigs.entrySet()){
			//			if(c.getValue().contains(ModalityType.facebook_activity+Operator.equal_to+ModalValue.active) ||
			//					c.getValue().contains(ModalityType.facebook_activity+Operator.equal_to+ModalValue.active)){
			if(isOSNConfig(c.getValue())){
				System.out.println("OSN config: "+c.getKey());
				osnConfigs.put(c.getKey(), c.getValue());
			}
			else{
				System.out.println("Stream config: "+c.getKey());
				streamConfigs.put(c.getKey(), c.getValue());
			}				
		}

		SharedPreferences sp=context.getSharedPreferences("SSDATA", 0);
		//Check if OSN config has been added/removed
		if(osnConfigs.size()>sp.getStringSet("OSNConfigurationSet", blankSet).size()){
			//added new OSN config 
			System.out.println("add OSN config");
			addOSNConfig(context,osnConfigs);
		}
		else if(osnConfigs.size()<sp.getStringSet("OSNConfigurationSet", blankSet).size()){
			//removed OSN config
			System.out.println("remove OSN config");
			removeOSNConfig(context,osnConfigs);
		}
		else{
			System.out.println("No OSN config to be added/removed");
			System.out.println("Size: "+osnConfigs.size()+" , "+sp.getStringSet("OSNConfigurationSet", blankSet).size());
		}

		//Check if Stream config has been added/removed
		if(streamConfigs.size()>sp.getStringSet("StreamConfigurationSet", blankSet).size()){
			//added new Stream config 			
			System.out.println("add Stream config");
			addAndSubscribeStreamConfig(context,streamConfigs);
		}
		else if(streamConfigs.size()<sp.getStringSet("StreamConfigurationSet", blankSet).size()){
			//removed Stream config
			System.out.println("remove stream config");
			removeAndUnsubscribeStreamConfig(context,streamConfigs);
		}
		else{
			System.out.println("No Stream config to be added/removed");
			System.out.println("Size: "+streamConfigs.size()+" , "+sp.getStringSet("StreamConfigurationSet", blankSet).size());
		}
	}

	/**
	 * Returns whether any OSN dependent condition is present in the set of conditions.
	 * @param conditions Set of conditions
	 * @return boolean
	 */
	private static Boolean isOSNConfig(Set<String> conditions){
		Boolean flag=false;
		for(String s:conditions){
			if(s.startsWith(ModalityType.facebook_activity) || s.startsWith(ModalityType.twitter_activity)){
				flag=true;
				break;
			}
		}
		return flag;
	}
	
	/**
	 * This method adds new elements to the OSN configuration and OSN sensor set
	 * @param context Application context
	 * @param osnConfigs Map of OSN configurations names and set of conditions
	 */
	
	private static void addOSNConfig(Context context, Map<String, Set<String>> osnConfigs){
		SharedPreferences sp=context.getSharedPreferences("SSDATA", 0);
		Editor ed=sp.edit();
		Set<String> config=new HashSet<String>();
		Set<String> temp_config=new HashSet<String>();
		Set<String> sensor=new HashSet<String>();
		Set<String> blankSet1=new HashSet<String>();
		Set<String> blankSet2=new HashSet<String>();
		config=sp.getStringSet("OSNConfigurationSet", blankSet1);
		for(String c:config)
			temp_config.add(c);
		sensor=sp.getStringSet("OSNSensorSet", blankSet2);
		for(Map.Entry<String, Set<String>> c: osnConfigs.entrySet()){
			if(!temp_config.contains(c.getKey())){
				//new element found
				config.add(c.getKey());
				for(String s:c.getValue()){
					if(!s.startsWith(ModalityType.facebook_activity) && !s.startsWith(ModalityType.twitter_activity)){
					Condition newC=new Condition(s);
					sensor.add(ModalityType.getSensorName(newC.getModalityType()));
					}
				}
				sensor.add(ConfigurationHandler.getRequiredData(c.getKey()));
			}
		}
		Log.e("SNnMB","Setting OSN Sensor Set:"+ sensor);
		System.out.println("remove stream config");
		ed.putStringSet("OSNSensorSet", sensor);
		ed.putStringSet("OSNConfigurationSet", config);
		ed.commit();
		Log.e("SNnMB","OSN Sensor Set:"+ sp.getStringSet("OSNSensorSet", null));
		Log.e("SNnMB","OSN Config Set:"+ sp.getStringSet("OSNConfigurationSet", null));
	}

	/**
	 * This method removes unused elements to the OSN configuration and OSN sensor set
	 * @param context Application context
	 * @param osnConfigs Map of OSN configurations names and set of conditions
	 */
	private static void removeOSNConfig(Context context, Map<String, Set<String>> osnConfigs){
		Log.e("SNnMB", "Map osn configs: "+osnConfigs);
		SharedPreferences sp=context.getSharedPreferences("SSDATA", 0);
		Editor ed=sp.edit();
		Set<String> config=new HashSet<String>();
		Set<String> sensor=new HashSet<String>();
		Set<String> blankSet=new HashSet<String>();
		config=sp.getStringSet("OSNConfigurationSet", blankSet);
		Set<String> temp_config=new HashSet<String>();
		for(String t:config){
			temp_config.add(t);
		}
		Log.e("SNnMB", "Current OSN configs: "+config);
		//sensor=sp.getStringSet("OSNSensorSet", blankSet);
		for(String c:temp_config){
			if(!osnConfigs.containsKey(c)){
				//removed element found
				Log.e("SNnMB", "CONFIG TO BE REMOVED FOUND: "+c);
				config.remove(c);
			}
		}
		
		for(Map.Entry<String, Set<String>> c: osnConfigs.entrySet()){
			if(!config.contains(c.getKey())){
				//new element found
				for(String s:c.getValue()){
					if(!s.startsWith(ModalityType.facebook_activity) && !s.startsWith(ModalityType.twitter_activity)){
					Condition newC=new Condition(s);
					sensor.add(ModalityType.getSensorName(newC.getModalityType()));
					}
				}
				sensor.add(ConfigurationHandler.getRequiredData(c.getKey()));
			}
		}
		Log.e("SNnMB","Setting OSN Sensor Set:"+ sensor);
		
		ed.putStringSet("OSNSensorSet", sensor);
		ed.putStringSet("OSNConfigurationSet", config);
		ed.commit();
	}

	/**
	 * This method adds new elements to the Stream configuration and Stream sensor set.
	 * It will also subscribe the new sensors
	 * @param context Application context
	 * @param osnConfigs Map of stream configurations names and set of conditions
	 */
	private static void addAndSubscribeStreamConfig(Context context, Map<String, Set<String>> streamConfigs){
		SharedPreferences sp=context.getSharedPreferences("SSDATA", 0);
		Editor ed=sp.edit();
		Set<String> config=new HashSet<String>();
		Set<String> sensor=new HashSet<String>();
		Set<String> sensorNew=new HashSet<String>();
		Set<String> blankSet=new HashSet<String>();
		config=sp.getStringSet("StreamConfigurationSet", null);
		sensor=sp.getStringSet("StreamSensorSet", sensor);
		for(String s:sensor)
			sensorNew.add(s);
		System.out.println("Sensor: "+sensor);
		System.out.println("Sensor New: "+sensorNew);

		for(Map.Entry<String, Set<String>> c: streamConfigs.entrySet()){
			System.out.println("Map: "+c);
			if(config==null || !config.contains(c.getKey())){
				//new element found
				for(String s:c.getValue()){
					if(s.equalsIgnoreCase("null")){
						sensorNew.add(ConfigurationHandler.getRequiredData(c.getKey()));
						System.out.println("Sensor name (for ALL): " + ConfigurationHandler.getRequiredData(c.getKey()));
						System.out.println("Set- Sensor: "+sensorNew);
					}
					else{
						Condition newC=new Condition(s);
						System.out.println("ModalityType is: "+newC.getModalityType());					
						sensorNew.add(ModalityType.getSensorName(newC.getModalityType()));
						System.out.println("Sensor name: "+ModalityType.getSensorName(newC.getModalityType()));
					}
				}
			}
		}

		System.out.println("sensor: "+sensor);
		System.out.println("sensorNew: "+sensorNew);

		//check for new sensors and subscribe 
		ArrayList<Integer> sensorIds=new ArrayList<Integer>();
		SensorUtils aps=new SensorUtils(context);
		for(String sensor_name:sensor){
			sensorIds.add(aps.getSensorIdByName(sensor_name));
		}

		System.out.println("sensor: "+sensor);
		System.out.println("sensorNew: "+sensorNew);


		//		if(sensor!=sp.getStringSet("StreamSensorSet", blankSet)){
		//if(sensorTemp.size()>0){ //
		if(!sensor.containsAll(sensorNew)){
			//new sensor required
			System.out.println("new sensor required");
			try {
				ContinuousStreamSensing.getInstance(context,sensorIds).stopSensing();

			} catch (ESException e) {
				Log.e("SNnMB", "Error at sensor classifier: "+e.toString());
			}
			for(String s:sensorNew){
				if(!sp.getStringSet("StreamSensorSet", blankSet).contains(s)){
					//found new sensor to be added
					sensorIds.add(aps.getSensorIdByName(s));
					System.out.println("sensor id: "+aps.getSensorIdByName(s));
				}					
			}
			try {
				System.out.println("All sensor Ids: "+sensorIds);
				ContinuousStreamSensing.getInstance(context,sensorIds).startSensing();
			} catch (ESException e) {
				Log.e("SNnMB", "Error at sensor classifier: "+e.toString());
			}
			ed.putStringSet("StreamSensorSet", sensorNew);
			//			ed.putStringSet("StreamConfigurationSet", config);
			ed.commit();
		}
		else{
			System.out.println("StreamSensorSet is same as before, no new sensors required.");	
			for(String s: sp.getStringSet("StreamSensorSet", blankSet))
				System.out.println("StreamSensorSet: "+s);	
			for(String s: sensor)
				System.out.println("Sensor: "+s);
		}

		if(config==null)
			config=new HashSet<String>();
		for(Map.Entry<String, Set<String>> c: streamConfigs.entrySet()){
			config.add(c.getKey());
		}
		ed.putStringSet("StreamConfigurationSet", config);
		ed.commit();
	}

	/**
	 * This method removes unused elements to the Stream configuration and Stream sensor set.
	 * It will also unsubscribe the unused sensors
	 * @param context Application context
	 * @param osnConfigs Map of stream configurations names and set of conditions
	 */
	private static void removeAndUnsubscribeStreamConfig(Context context, Map<String, Set<String>> streamConfigs){
		SharedPreferences sp=context.getSharedPreferences("SSDATA", 0);
		Editor ed=sp.edit();
		Set<String> config=new HashSet<String>();
		Set<String> temp_config=new HashSet<String>();
		Set<String> sensor=new HashSet<String>();
		Set<String> blankSet=new HashSet<String>();
		config=sp.getStringSet("StreamConfigurationSet", blankSet);
		for(String t:config)
			temp_config.add(t);

		sensor=sp.getStringSet("StreamSensorSet", blankSet);
		Log.i("SNnMB", "Configs: "+temp_config);
		for(String c:temp_config){
			Log.i("SNnMB", "Checking config: "+c);
			if(!streamConfigs.containsKey(c)){
				//removed config found
				Log.e("SNnMB", "CONFIG TO BE REMOVED FOUND: "+c);
				config.remove(c);
			}
		}
		Log.i("SNnMB", "Finally, Remaining configs: "+config);
		Set<String> sensorNew=new HashSet<String>();
		for(Map.Entry<String, Set<String>> e: streamConfigs.entrySet()){
			for(String s:e.getValue()){
				if(s.equalsIgnoreCase("null")){
					sensorNew.add(ConfigurationHandler.getRequiredData(e.getKey()));
					System.out.println("Sensor name (for ALL): " + ConfigurationHandler.getRequiredData(e.getKey()));
					System.out.println("Set- Sensor: "+sensorNew);
				}
				else{
					Condition newC=new Condition(s);
					System.out.println("ModalityType is: "+newC.getModalityType());					
					sensorNew.add(ModalityType.getSensorName(newC.getModalityType()));
					System.out.println("Sensor name: "+ModalityType.getSensorName(newC.getModalityType()));
				}
			}
		}
		Log.i("SNnMB", "New sensors: "+sensorNew);
		//check for new sensors and subscribe 
		ArrayList<Integer> sensorIds=new ArrayList<Integer>();
		SensorUtils aps=new SensorUtils(context);
		for(String sensor_name:sp.getStringSet("StreamSensorSet", blankSet)){
			sensorIds.add(aps.getSensorIdByName(sensor_name));
		}
		try {
			Log.i("SNnMB", "Stop sensing from: "+sensorIds);
			ContinuousStreamSensing.getInstance(context,sensorIds).stopSensing();
		} catch (ESException e) {
			Log.e("SNnMB", "Error at sensor classifier: "+e.toString());
		}
		if(!sensorNew.equals(sensor)) {
			//sensor to be removed
			for(String s:sensor){
				if(!sensorNew.contains(s)){
					//found sensor
					sensorIds.remove(sensorIds.indexOf(aps.getSensorIdByName(s)));
				}					
			}
			try {
				Log.i("SNnMB", "Start sensing from: "+sensorIds);
				ContinuousStreamSensing.getInstance(context,sensorIds).startSensing();
			} catch (ESException e) {
				Log.e("SNnMB", "Error at sensor classifier: "+e.toString());
			}
		}		
		ed.putStringSet("StreamSensorSet", sensorNew);
		ed.putStringSet("StreamConfigurationSet", config);
		ed.commit();
	}
}
