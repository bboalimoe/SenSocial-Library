package com.ubhave.sensocial.manager;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import com.ubhave.sensocial.http.IdSenderToEnableTrigger;

public class SenSocialService {

	private String SERVICE_CLASSNAME;
	
	/**
	 * Method to start the service. <br/>
	 * If the MQTT server IP has been set then it will start the MQTT service
	 * else it starts HTPP service.
	 * @param context Application-Context
	 */
	protected void startService(Context context) {
		SharedPreferences sp=context.getSharedPreferences("snmbData",0);
		if(!sp.getBoolean("sendId", false)){			
			new IdSenderToEnableTrigger(sp.getString("fbusername", "null"), sp.getString("new_access_token", "null"),
					sp.getString("twitterusername", "null"), sp.getString("uuid", "null"), sp.getString("server", "null"))
					.sendIdToServer();
		}		
		if(sp.getString("mqtt","null").equals("null")){
			Intent myIntent = new Intent(context, com.ubhave.sensocial.http.HTTPService.class);
			context.startService(myIntent);
		}else{
			Intent myIntent = new Intent(context, com.ubhave.sensocial.mqtt.MQTTService.class);
			context.startService(myIntent);
		}
	}
	
	/**
	 * Method to stop the service. It will stop HTTP or MQTT service, which ever service will be running.
	 * @param context Application-Context
	 */	
	protected void stopService(Context context) {
		SharedPreferences sp=context.getSharedPreferences("snmbData",0);
		if(sp.getString("mqtt","null").equals("null")){
			Intent myIntent = new Intent(context, com.ubhave.sensocial.http.HTTPService.class);
			context.stopService(myIntent);
		}else{
			Intent myIntent = new Intent(context, com.ubhave.sensocial.mqtt.MQTTService.class);
			context.stopService(myIntent);
		}
	}
	
	protected Boolean isRunning(Context context){
		SharedPreferences sp=context.getSharedPreferences("snmbData",0);
		if(sp.getString("mqtt","null").equals("null")){
			SERVICE_CLASSNAME="com.ubhave.sensocial.http.HTTPService";
		}else{
			SERVICE_CLASSNAME="com.ubhave.sensocial.mqtt.MQTTService";
		}
		ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
		for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
			if (SERVICE_CLASSNAME.equals(service.service.getClassName())) {
				return true;
			}
		}
		return false;		
	}

}
