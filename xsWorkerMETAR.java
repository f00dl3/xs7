package jASUtils;

import java.io.*;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Scanner;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.*;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import org.json.*;

import jASUtils.StumpJunk;

public class xsWorkerMETAR {

	public static void main(String args[]) {

		final String xsTmp = args[0];
		final String stationType = "METAR";
		final String region = args[1];
		final File jsonOutFile = new File(xsTmp+"/output_"+stationType+"_"+region+".json");
		final File badStationFile = new File(xsTmp+"/badStations_"+stationType+".txt");

		int thisNullCounter = 0;
		int thisNullCounterModel = 0;
		int tVars = 0;

		List<String> wxStations = new ArrayList<String>();
		final String getStationListSQL = "SELECT Station FROM WxObs.Stations WHERE Active=1 AND Region LIKE '"+region+"%' AND Priority = 5 ORDER BY Priority, Station DESC;";
		final File xmlMetarsIn = new File(xsTmp+"/metars.xml");

		try (
			Connection conn = MyDBConnector.getMyConnection(); Statement stmt = conn.createStatement();
			ResultSet resultSetStations = stmt.executeQuery(getStationListSQL);
		) { while (resultSetStations.next()) { wxStations.add(resultSetStations.getString("Station")); } }
		catch (Exception e) { e.printStackTrace(); }

		try {

			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			DocumentBuilder builder = factory.newDocumentBuilder();
			Document xmlDoc = builder.parse(xmlMetarsIn);

			for (String thisStation : wxStations) {

				String tDewpointC = null; tVars++;
				String tPressureIn = null; tVars++;
				String tPressureMb = null; tVars++;
				String tTempC = null; tVars++;
				String tTimeString = null; tVars++;
				String tWeather = null; tVars++;
				String tWindDegrees = null; tVars++;
				String tWindSpeed = null; tVars++;
				String tVisibility = null; tVars++;
				String tVertVisFt = null; tVars++;
				String tPrecipIn = null; tVars++;
				String tRawMETAR = null; tVars++;
				String tFlightCat = null; tVars++;

				try {
					XPathFactory xPathFactory = XPathFactory.newInstance();
					XPath xpath = xPathFactory.newXPath();
					tTempC = xpath.evaluate("//*[station_id='"+thisStation+"']/temp_c", xmlDoc);
					tDewpointC = xpath.evaluate("//*[station_id='"+thisStation+"']/dewpoint_c", xmlDoc);
					tPressureIn = xpath.evaluate("//*[station_id='"+thisStation+"']/altim_in_hg", xmlDoc);
					tPressureMb = xpath.evaluate("//*[station_id='"+thisStation+"']/sea_level_pressure_mb", xmlDoc);
					tTimeString = xpath.evaluate("//*[station_id='"+thisStation+"']/observation_time", xmlDoc);
					tWeather = xpath.evaluate("//*[station_id='"+thisStation+"']/wx_string", xmlDoc);
					tWindDegrees = xpath.evaluate("//*[station_id='"+thisStation+"']/wind_dir_degrees", xmlDoc);
					tWindSpeed = xpath.evaluate("//*[station_id='"+thisStation+"']/wind_speed_kt", xmlDoc);
					tVisibility = xpath.evaluate("//*[station_id='"+thisStation+"']/visibility_statute_mi", xmlDoc);
					tVertVisFt = xpath.evaluate("//*[station_id='"+thisStation+"']/vert_vis_ft", xmlDoc);
					tPrecipIn = xpath.evaluate("//*[station_id='"+thisStation+"']/precip_in", xmlDoc);
					tRawMETAR = xpath.evaluate("//*[station_id='"+thisStation+"']/raw_text", xmlDoc);
					tFlightCat = xpath.evaluate("//*[station_id='"+thisStation+"']/flight_category", xmlDoc);

				}
				catch (XPathException xpx) { xpx.printStackTrace(); }
							
				JSONObject jStationObj = new JSONObject();
				JSONObject jStationData = new JSONObject();
				jStationObj.put(thisStation, jStationData);

				if (StumpJunk.isSet(tTempC)) { jStationData.put("Temperature", tTempC); } else { thisNullCounter++; }
				if (StumpJunk.isSet(tDewpointC)) { jStationData.put("Dewpoint", tDewpointC); } else { thisNullCounter++; }
				if (StumpJunk.isSet(tPressureMb)) { jStationData.put("Pressure", tPressureMb); } else { thisNullCounter++; }
				if (StumpJunk.isSet(tPressureIn)) { jStationData.put("PressureIn", tPressureIn); } else { thisNullCounter++; }
				if (StumpJunk.isSet(tTimeString)) { jStationData.put("TimeString", tTimeString); } else { thisNullCounter++; }
				if (StumpJunk.isSet(tVisibility)) { jStationData.put("Visibility", tVisibility); } else { thisNullCounter++; }
				if (StumpJunk.isSet(tVertVisFt)) { jStationData.put("VerticalVisibilityFt", tVertVisFt); } else { thisNullCounter++; }
				if (StumpJunk.isSet(tWeather)) { jStationData.put("Weather", tWeather); } else { thisNullCounter++; }
				if (StumpJunk.isSet(tWindDegrees)) { jStationData.put("WindDegrees", tWindDegrees); } else { thisNullCounter++; }
				if (StumpJunk.isSet(tWindSpeed)) { jStationData.put("WindSpeed", tWindSpeed); } else { thisNullCounter++; }
				if (StumpJunk.isSet(tPrecipIn)) { jStationData.put("PrecipIn", tPrecipIn); } else { thisNullCounter++; }
				if (StumpJunk.isSet(tRawMETAR)) { jStationData.put("RawMETAR", tRawMETAR); } else { thisNullCounter++; }
				if (StumpJunk.isSet(tFlightCat)) { jStationData.put("FlightCategory", tFlightCat); } else { thisNullCounter++; }

				if (thisNullCounter != tVars) {
					String thisJSONstring = jStationObj.toString().substring(1);
					thisJSONstring = thisJSONstring.substring(0, thisJSONstring.length()-1)+",";
					if(thisJSONstring.equals("\""+thisStation+"\":{},")) {
						System.out.println("!!! WARN: NO DATA FOR Station "+thisStation+" (METAR Method 2)!");
					} else {
						try { StumpJunk.varToFile(thisJSONstring, jsonOutFile, true); } catch (FileNotFoundException fnf) { fnf.printStackTrace(); }
						System.out.println(" -> Completed: "+thisStation+" ("+stationType+")");
					}
				} else {
					System.out.println("!!! WARN: NO DATA FOR Station "+thisStation+" (METAR Method 1)!");
				}
				
			}

		}

		catch (SAXException sex) { sex.printStackTrace(); }
		catch (ParserConfigurationException pcx) { pcx.printStackTrace(); }
		catch (IOException iox) { iox.printStackTrace(); }
		
	}

}
