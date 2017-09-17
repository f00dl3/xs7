package jASUtils;

import java.io.*;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Scanner;

import org.json.*;

import jASUtils.StumpJunk;

public class xsWorkerBouy {

	public static void main(String args[]) {

		final String xsTmp = args[0];
		final String stationType = "Bouy";
		final String region = args[1];
		final File jsonOutFile = new File(xsTmp+"/output_"+stationType+"_"+region+".json");
		final File badStationFile = new File(xsTmp+"/badStations_"+stationType+".txt");

		int thisNullCounter = 0;
		int thisNullCounterModel = 0;
		int tVars = 0;
		
		List<String> wxStations = new ArrayList<String>();
		final String getStationListSQL = "SELECT SUBSTR(Station,2) FROM WxObs.Stations WHERE Active=1 AND Region='"+region+"' AND Priority = 6 ORDER BY Priority, Station DESC;";

		try (
			Connection conn = MyDBConnector.getMyConnection(); Statement stmt = conn.createStatement();
			ResultSet resultSetStations = stmt.executeQuery(getStationListSQL);
		) { while (resultSetStations.next()) { wxStations.add(resultSetStations.getString("Station")); } }
		catch (Exception e) { e.printStackTrace(); }

		for (String thisStation : wxStations) {
		
			File xmlOut = new File(xsTmp+"/"+thisStation+".xml");
		
			JSONObject jStationObj = new JSONObject();
			JSONObject jStationData = new JSONObject();
			jStationObj.put(thisStation, jStationData);

			String tDewpointF = null; tVars++;
			String tPressureMb = null; tVars++;
			String tRelativeHumidity = null; tVars++;
			String tTempF = null; tVars++;
			String tTimeString = null; tVars++;
			String tWaterTempF = null; tVars++;
			String tWaveDegrees = null; tVars++;
			String tWaveDirection = null; tVars++;
			String tWaveHeight = null; tVars++;
			String tWavePeriodAvg = null; tVars++;
			String tWavePeriodDom = null; tVars++;
			String tWeather = null; tVars++;
			String tWindDegrees = null; tVars++;
			String tWindDirection = null; tVars++;
			String tWindSpeed = null; tVars++;
			String tWindGust = null; tVars++;
			String tVisibility = null; tVars++;

			Scanner xmlScanner = null; try {		
				xmlScanner = new Scanner(xmlOut);
				while(xmlScanner.hasNext()) {
					String line = xmlScanner.nextLine();
					if(line.contains("<dewpoint_f>")) { Pattern p = Pattern.compile("<dewpoint_f>(.*)</dewpoint_f>"); Matcher m = p.matcher(line); if (m.find()) { tDewpointF = m.group(1); } }
					if(line.contains("<observation_time>")) { Pattern p = Pattern.compile("<observation_time>(.*)</observation_time>"); Matcher m = p.matcher(line); if (m.find()) { tTimeString = m.group(1); } }
					if(line.contains("<pressure_mb>")) { Pattern p = Pattern.compile("<pressure_mb>(.*)</pressure_mb>"); Matcher m = p.matcher(line); if (m.find()) { tPressureMb = m.group(1); } }
					if(line.contains("<relative_humidity>")) { Pattern p = Pattern.compile("<relative_humidity>(.*)</relative_humidity>"); Matcher m = p.matcher(line); if (m.find()) { tRelativeHumidity = m.group(1); } }
					if(line.contains("<temp_f>")) { Pattern p = Pattern.compile("<temp_f>(.*)</temp_f>"); Matcher m = p.matcher(line); if (m.find()) { tTempF = m.group(1); } }
					if(line.contains("<weather>")) { Pattern p = Pattern.compile("<weather>(.*)</weather>"); Matcher m = p.matcher(line); if (m.find()) { tWeather = m.group(1); } }
					if(line.contains("<water_temp_f>")) { Pattern p = Pattern.compile("<water_temp_f>(.*)</water_temp_f>"); Matcher m = p.matcher(line); if (m.find()) { tWaterTempF = m.group(1); } }
					if(line.contains("<mean_wave_degrees>")) { Pattern p = Pattern.compile("<mean_wave_degrees>(.*)</mean_wave_degrees>"); Matcher m = p.matcher(line); if (m.find()) { tWaveDegrees = m.group(1); } }
					if(line.contains("<mean_wave_dir>")) { Pattern p = Pattern.compile("<mean_wave_dir>(.*)</mean_wave_dir>"); Matcher m = p.matcher(line); if (m.find()) { tWaveDirection = m.group(1); } }
					if(line.contains("<wave_height_m>")) { Pattern p = Pattern.compile("<wave_height_m>(.*)</wave_height_m>"); Matcher m = p.matcher(line); if (m.find()) { tWaveHeight = m.group(1); } }
					if(line.contains("<average_period_sec>")) { Pattern p = Pattern.compile("<average_period_sec>(.*)</average_period_sec>"); Matcher m = p.matcher(line); if (m.find()) { tWavePeriodAvg = m.group(1); } }
					if(line.contains("<dominant_period_sec>")) { Pattern p = Pattern.compile("<dominant_period_sec>(.*)</dominant_period_sec>"); Matcher m = p.matcher(line); if (m.find()) { tWavePeriodDom = m.group(1); } }
					if(line.contains("<wind_degrees>")) { Pattern p = Pattern.compile("<wind_degrees>(.*)</wind_degrees>"); Matcher m = p.matcher(line); if (m.find()) { tWindDegrees = m.group(1); } }
					if(line.contains("<wind_dir>")) { Pattern p = Pattern.compile("<wind_dir>(.*)</wind_dir>"); Matcher m = p.matcher(line); if (m.find()) { tWindDirection = m.group(1); } }
					if(line.contains("<wind_mph>")) { Pattern p = Pattern.compile("<wind_mph>(.*)</wind_mph>"); Matcher m = p.matcher(line); if (m.find()) { tWindSpeed = m.group(1); } }
					if(line.contains("<wind_gust_mph>")) { Pattern p = Pattern.compile("<wind_gust_mph>(.*)</wind_gust_mph>"); Matcher m = p.matcher(line); if (m.find()) { tWindGust = m.group(1); } }
					if(line.contains("<visibility_mi>")) { Pattern p = Pattern.compile("<visibility_mi>(.*)</visibility_mi>"); Matcher m = p.matcher(line); if (m.find()) { tVisibility = m.group(1); } }
				}
			}
			catch (FileNotFoundException fnf) { fnf.printStackTrace(); }
		
			if (StumpJunk.isSet(tTempF)) { jStationData.put("Temperature", tTempF); } else { thisNullCounter++; }
			if (StumpJunk.isSet(tDewpointF)) { jStationData.put("Dewpoint", tDewpointF); } else { thisNullCounter++; }
			if (StumpJunk.isSet(tRelativeHumidity)) { jStationData.put("RelativeHumidity", tRelativeHumidity); } else { thisNullCounter++; }
			if (StumpJunk.isSet(tPressureMb)) { jStationData.put("Pressure", tPressureMb); } else { thisNullCounter++; }
			if (StumpJunk.isSet(tTimeString)) { jStationData.put("TimeString", tTimeString); } else { thisNullCounter++; }
			if (StumpJunk.isSet(tVisibility)) { jStationData.put("Visibility", tVisibility); } else { thisNullCounter++; }
			if (StumpJunk.isSet(tWaterTempF)) { jStationData.put("WaterTemp", tWaterTempF); } else { thisNullCounter++; }
			if (StumpJunk.isSet(tWaveDegrees)) { jStationData.put("WaveDegrees", tWaveDegrees); } else { thisNullCounter++; }
			if (StumpJunk.isSet(tWaveDirection)) { jStationData.put("WaveDirection", tWaveDirection); } else { thisNullCounter++; }
			if (StumpJunk.isSet(tWaveHeight)) { jStationData.put("WaveHeight", tWaveHeight); } else { thisNullCounter++; }
			if (StumpJunk.isSet(tWavePeriodAvg)) { jStationData.put("WavePeriodAverage", tWavePeriodAvg); } else { thisNullCounter++; }
			if (StumpJunk.isSet(tWavePeriodDom)) { jStationData.put("WavePeriodDominant", tWavePeriodDom); } else { thisNullCounter++; }
			if (StumpJunk.isSet(tWeather)) { jStationData.put("Weather", tWeather); } else { thisNullCounter++; }
			if (StumpJunk.isSet(tWindDegrees)) { jStationData.put("WindDegrees", tWindDegrees); } else { thisNullCounter++; }
			if (StumpJunk.isSet(tWindDirection)) { jStationData.put("WindDirection", tWindDirection); } else { thisNullCounter++; }
			if (StumpJunk.isSet(tWindGust)) { jStationData.put("WindGust", tWindGust); } else { thisNullCounter++; }
			if (StumpJunk.isSet(tWindSpeed)) { jStationData.put("WindSpeed", tWindSpeed); } else { thisNullCounter++; }

			if (thisNullCounter != tVars) {
				String thisJSONstring = jStationObj.toString().substring(1);
				thisJSONstring = thisJSONstring.substring(0, thisJSONstring.length()-1)+",";
				try { StumpJunk.varToFile(thisJSONstring, jsonOutFile, true); } catch (FileNotFoundException fnf) { fnf.printStackTrace(); }
				System.out.println(" -> Completed: "+thisStation+" ("+stationType+")");
			} else {
				System.out.println("!!! WARN: NO DATA FOR Station "+thisStation+" !");
				String thisBadStation = thisStation+", ";
				try { StumpJunk.varToFile(thisBadStation, badStationFile, true); } catch (FileNotFoundException fnf) { fnf.printStackTrace(); }
			}				
			xmlOut.delete();
			
		}
	}		
}
