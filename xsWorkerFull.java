package jASUtils;

import java.io.*;
import java.lang.Math;
import java.math.RoundingMode;
import java.sql.*;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Scanner;

import org.json.*;

import jASUtils.StumpJunk;

public class xsWorkerFull {

	public static double windDirCalc(double tWUin, double tWVin) { return 57.29578*(Math.atan2(tWUin, tWVin))+180; }
	public static double windSpdCalc(double tWUin, double tWVin) { return Math.sqrt(tWUin*tWUin+tWVin*tWVin)*1.944; }
	public static double calcSLCL(double tTCin, double tRHin) { return (20+(tTCin/5))*(100-tRHin); }
	public static double calcDwpt(double tTCin, double tRHin) { return tTCin-(100-tRHin)/5; }

	public static void main(String args[]) {

		final String stationType = "Full";
		final String xsTmp = args[0];
		final String region = args[1];
		final File xsTmpGrib2Obj = new File(xsTmp+"/grib2");
		final File jsonOutFile = new File(xsTmp+"/output_"+stationType+"_"+region+".json");
		final File badStationFile = new File(xsTmp+"/badStations_"+stationType+".txt");
		final String wgrib2Path = "/home/astump/src/grib2/wgrib2";

		int tNulls = 0;
		int tMNulls = 0;
		int tVars = 0;
		int tMVars = 0;

		DecimalFormat df = new DecimalFormat("#.###");
		df.setRoundingMode(RoundingMode.CEILING);

		List<String> wxStations = new ArrayList<String>();
		List<String> wxStationPoints = new ArrayList<String>();

		String pointInputString = "";

		final File pointInputDump = new File(xsTmp+"/pointDump"+region+".txt");	
		final String getStationListSQL = "SELECT Station FROM WxObs.Stations WHERE Active=1 AND Region='"+region+"' AND Priority < 4 ORDER BY Priority, Station DESC;";
		final String getStationPointListSQL = "SELECT SUBSTRING(Point, 2, CHAR_LENGTH(Point)-2) as fixedPoint FROM WxObs.Stations WHERE Active=1 AND Region='"+region+"' AND Priority < 4 ORDER BY Priority, Station DESC;";
		final File hrrrSounding = new File(xsTmp+"/grib2/outSounding_HRRR_"+region+".csv");
		final File rapSounding = new File(xsTmp+"/grib2/outSounding_RAP_"+region+".csv");
		final String hrrrMatch = "TMP|RH|UGRD|VGRD|CAPE|CIN|4LFTX|HGT|PWAT|TMP|RH|HLCY";
		final String rapMatch = hrrrMatch;

		int gribSpot = 0;
		int iterk = 0;

		try (
			Connection conn = MyDBConnector.getMyConnection(); Statement stmt = conn.createStatement();
			ResultSet resultSetStations = stmt.executeQuery(getStationListSQL);
		) { while (resultSetStations.next()) { wxStations.add(resultSetStations.getString("Station")); } }
		catch (Exception e) { e.printStackTrace(); }

		try (
			Connection conn = MyDBConnector.getMyConnection(); Statement stmt = conn.createStatement();
			ResultSet resultSetStationPoints = stmt.executeQuery(getStationPointListSQL);
		) { while (resultSetStationPoints.next()) { wxStationPoints.add(resultSetStationPoints.getString("fixedPoint")); } }
		catch (Exception e) { e.printStackTrace(); }

		for (String thisPoint : wxStationPoints) {
			String thisGeo = thisPoint.replace(",", " ");
			pointInputString = pointInputString+"-lon "+thisGeo+" ";
		}

		try { StumpJunk.varToFile(pointInputString, pointInputDump, false); } catch (FileNotFoundException fnf) { fnf.printStackTrace(); }

		System.out.println(" --> Parsing GRIB2 data HRRR for region "+region);
		try { StumpJunk.runProcessOutFile("\""+wgrib2Path+"/wgrib2\" "+xsTmp+"/grib2/HRRR "+pointInputString+" -match \":("+hrrrMatch+"):\"", hrrrSounding, false); } catch (FileNotFoundException fnf) { fnf.printStackTrace(); }

		System.out.println(" --> Parsing GRIB2 data RAP for region "+region);
		try { StumpJunk.runProcessOutFile("\""+wgrib2Path+"/wgrib2\" "+xsTmp+"/grib2/RAP "+pointInputString+" -match \":("+rapMatch+"):\"", rapSounding, false); } catch (FileNotFoundException fnf) { fnf.printStackTrace(); }
		
		StumpJunk.sedFileReplace(hrrrSounding.getPath(), ":lon", ",lon");
		StumpJunk.sedFileReplace(rapSounding.getPath(), ":lon", ",lon");

		for (String thisStation : wxStations) {

			gribSpot = (gribSpot + 3);

			File xmlOut = new File(xsTmp+"/"+thisStation+".xml");
			String thisGeo = wxStationPoints.get(iterk);

			System.out.println(" --> Processing "+thisStation+" - GRIB2 spot is "+gribSpot+", coords "+thisGeo);

			JSONObject jStationObj = new JSONObject();
			JSONObject jStationData = new JSONObject();
			jStationObj.put(thisStation, jStationData);

			String tDewpointF = null; tVars++;
			String tPressureMb = null; tVars++;
			String tPressureIn = null; tVars++;
			String tRelativeHumidity = null; tVars++;
			String tTempF = null; tVars++;
			String tTimeString = null; tVars++;
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
					if(line.contains("<pressure_in>")) { Pattern p = Pattern.compile("<pressure_in>(.*)</pressure_in>"); Matcher m = p.matcher(line); if (m.find()) { tPressureIn = m.group(1); } }
					if(line.contains("<relative_humidity>")) { Pattern p = Pattern.compile("<relative_humidity>(.*)</relative_humidity>"); Matcher m = p.matcher(line); if (m.find()) { tRelativeHumidity = m.group(1); } }
					if(line.contains("<temp_f>")) { Pattern p = Pattern.compile("<temp_f>(.*)</temp_f>"); Matcher m = p.matcher(line); if (m.find()) { tTempF = m.group(1); } }
					if(line.contains("<weather>")) { Pattern p = Pattern.compile("<weather>(.*)</weather>"); Matcher m = p.matcher(line); if (m.find()) { tWeather = m.group(1); } }
					if(line.contains("<wind_degrees>")) { Pattern p = Pattern.compile("<wind_degrees>(.*)</wind_degrees>"); Matcher m = p.matcher(line); if (m.find()) { tWindDegrees = m.group(1); } }
					if(line.contains("<wind_dir>")) { Pattern p = Pattern.compile("<wind_dir>(.*)</wind_dir>"); Matcher m = p.matcher(line); if (m.find()) { tWindDirection = m.group(1); } }
					if(line.contains("<wind_mph>")) { Pattern p = Pattern.compile("<wind_mph>(.*)</wind_mph>"); Matcher m = p.matcher(line); if (m.find()) { tWindSpeed = m.group(1); } }
					if(line.contains("<wind_gust_mph>")) { Pattern p = Pattern.compile("<wind_gust_mph>(.*)</wind_gust_mph>"); Matcher m = p.matcher(line); if (m.find()) { tWindGust = m.group(1); } }
					if(line.contains("<visibility_mi>")) { Pattern p = Pattern.compile("<visibility_mi>(.*)</visibility_mi>"); Matcher m = p.matcher(line); if (m.find()) { tVisibility = m.group(1); } }
				}
			}
			catch (FileNotFoundException fnf) { fnf.printStackTrace(); }
		
			if (StumpJunk.isSet(tTempF)) { jStationData.put("Temperature", tTempF); } else { tNulls++; }
			if (StumpJunk.isSet(tDewpointF)) { jStationData.put("Dewpoint", tDewpointF); } else { tNulls++; }
			if (StumpJunk.isSet(tRelativeHumidity)) { jStationData.put("RelativeHumidity", tRelativeHumidity); } else { tNulls++; }
			if (StumpJunk.isSet(tPressureMb)) { jStationData.put("Pressure", tPressureMb); } else { tNulls++; }
			if (StumpJunk.isSet(tPressureIn)) { jStationData.put("PressureIn", tPressureIn); } else { tNulls++; }
			if (StumpJunk.isSet(tTimeString)) { jStationData.put("TimeString", tTimeString); } else { tNulls++; }
			if (StumpJunk.isSet(tVisibility)) { jStationData.put("Visibility", tVisibility); } else { tNulls++; }
			if (StumpJunk.isSet(tWeather)) { jStationData.put("Weather", tWeather); } else { tNulls++; }
			if (StumpJunk.isSet(tWindDegrees)) { jStationData.put("WindDegrees", tWindDegrees); } else { tNulls++; }
			if (StumpJunk.isSet(tWindDirection)) { jStationData.put("WindDirection", tWindDirection); } else { tNulls++; }
			if (StumpJunk.isSet(tWindGust)) { jStationData.put("WindGust", tWindGust); } else { tNulls++; }
			if (StumpJunk.isSet(tWindSpeed)) { jStationData.put("WindSpeed", tWindSpeed); } else { tNulls++; }

			double tRH100 = 0.001; tMVars++; double tRH125 = 0.001; tMVars++; double tRH150 = 0.001; tMVars++; double tRH175 = 0.001; tMVars++; 
			double tRH200 = 0.001; tMVars++; double tRH225 = 0.001; tMVars++; double tRH250 = 0.001; tMVars++; double tRH275 = 0.001; tMVars++; 
			double tRH300 = 0.001; tMVars++; double tRH325 = 0.001; tMVars++; double tRH350 = 0.001; tMVars++; double tRH375 = 0.001; tMVars++; 
			double tRH400 = 0.001; tMVars++; double tRH425 = 0.001; tMVars++; double tRH450 = 0.001; tMVars++; double tRH475 = 0.001; tMVars++; 
			double tRH500 = 0.001; tMVars++; double tRH525 = 0.001; tMVars++; double tRH550 = 0.001; tMVars++; double tRH575 = 0.001; tMVars++; 
			double tRH600 = 0.001; tMVars++; double tRH625 = 0.001; tMVars++; double tRH650 = 0.001; tMVars++; double tRH675 = 0.001; tMVars++; 
			double tRH700 = 0.001; tMVars++; double tRH725 = 0.001; tMVars++; double tRH750 = 0.001; tMVars++; double tRH775 = 0.001; tMVars++; 
			double tRH800 = 0.001; tMVars++; double tRH825 = 0.001; tMVars++; double tRH850 = 0.001; tMVars++; double tRH875 = 0.001; tMVars++; 
			double tRH900 = 0.001; tMVars++; double tRH925 = 0.001; tMVars++; double tRH950 = 0.001; tMVars++; double tRH975 = 0.001; tMVars++; 
			double tRH1000 = 0.001; tMVars++; double tRH0 = 0.001; tMVars++; 

			double tTC100 = 0.001; tMVars++; double tTC125 = 0.001; tMVars++; double tTC150 = 0.001; tMVars++; double tTC175 = 0.001; tMVars++; 
			double tTC200 = 0.001; tMVars++; double tTC225 = 0.001; tMVars++; double tTC250 = 0.001; tMVars++; double tTC275 = 0.001; tMVars++; 
			double tTC300 = 0.001; tMVars++; double tTC325 = 0.001; tMVars++; double tTC350 = 0.001; tMVars++; double tTC375 = 0.001; tMVars++; 
			double tTC400 = 0.001; tMVars++; double tTC425 = 0.001; tMVars++; double tTC450 = 0.001; tMVars++; double tTC475 = 0.001; tMVars++; 
			double tTC500 = 0.001; tMVars++; double tTC525 = 0.001; tMVars++; double tTC550 = 0.001; tMVars++; double tTC575 = 0.001; tMVars++; 
			double tTC600 = 0.001; tMVars++; double tTC625 = 0.001; tMVars++; double tTC650 = 0.001; tMVars++; double tTC675 = 0.001; tMVars++; 
			double tTC700 = 0.001; tMVars++; double tTC725 = 0.001; tMVars++; double tTC750 = 0.001; tMVars++; double tTC775 = 0.001; tMVars++; 
			double tTC800 = 0.001; tMVars++; double tTC825 = 0.001; tMVars++; double tTC850 = 0.001; tMVars++; double tTC875 = 0.001; tMVars++; 
			double tTC900 = 0.001; tMVars++; double tTC925 = 0.001; tMVars++; double tTC950 = 0.001; tMVars++; double tTC975 = 0.001; tMVars++; 
			double tTC1000 = 0.001; tMVars++; double tTC0 = 0.001; tMVars++; 

			double tWU100 = 0.001; tMVars++; double tWU125 = 0.001; tMVars++; double tWU150 = 0.001; tMVars++; double tWU175 = 0.001; tMVars++; 
			double tWU200 = 0.001; tMVars++; double tWU225 = 0.001; tMVars++; double tWU250 = 0.001; tMVars++; double tWU275 = 0.001; tMVars++; 
			double tWU300 = 0.001; tMVars++; double tWU325 = 0.001; tMVars++; double tWU350 = 0.001; tMVars++; double tWU375 = 0.001; tMVars++; 
			double tWU400 = 0.001; tMVars++; double tWU425 = 0.001; tMVars++; double tWU450 = 0.001; tMVars++; double tWU475 = 0.001; tMVars++; 
			double tWU500 = 0.001; tMVars++; double tWU525 = 0.001; tMVars++; double tWU550 = 0.001; tMVars++; double tWU575 = 0.001; tMVars++; 
			double tWU600 = 0.001; tMVars++; double tWU625 = 0.001; tMVars++; double tWU650 = 0.001; tMVars++; double tWU675 = 0.001; tMVars++; 
			double tWU700 = 0.001; tMVars++; double tWU725 = 0.001; tMVars++; double tWU750 = 0.001; tMVars++; double tWU775 = 0.001; tMVars++; 
			double tWU800 = 0.001; tMVars++; double tWU825 = 0.001; tMVars++; double tWU850 = 0.001; tMVars++; double tWU875 = 0.001; tMVars++; 
			double tWU900 = 0.001; tMVars++; double tWU925 = 0.001; tMVars++; double tWU950 = 0.001; tMVars++; double tWU975 = 0.001; tMVars++; 
			double tWU1000 = 0.001; tMVars++; double tWU0 = 0.001; tMVars++; 

			double tWV100 = 0.001; tMVars++; double tWV125 = 0.001; tMVars++; double tWV150 = 0.001; tMVars++; double tWV175 = 0.001; tMVars++; 
			double tWV200 = 0.001; tMVars++; double tWV225 = 0.001; tMVars++; double tWV250 = 0.001; tMVars++; double tWV275 = 0.001; tMVars++; 
			double tWV300 = 0.001; tMVars++; double tWV325 = 0.001; tMVars++; double tWV350 = 0.001; tMVars++; double tWV375 = 0.001; tMVars++; 
			double tWV400 = 0.001; tMVars++; double tWV425 = 0.001; tMVars++; double tWV450 = 0.001; tMVars++; double tWV475 = 0.001; tMVars++; 
			double tWV500 = 0.001; tMVars++; double tWV525 = 0.001; tMVars++; double tWV550 = 0.001; tMVars++; double tWV575 = 0.001; tMVars++; 
			double tWV600 = 0.001; tMVars++; double tWV625 = 0.001; tMVars++; double tWV650 = 0.001; tMVars++; double tWV675 = 0.001; tMVars++; 
			double tWV700 = 0.001; tMVars++; double tWV725 = 0.001; tMVars++; double tWV750 = 0.001; tMVars++; double tWV775 = 0.001; tMVars++; 
			double tWV800 = 0.001; tMVars++; double tWV825 = 0.001; tMVars++; double tWV850 = 0.001; tMVars++; double tWV875 = 0.001; tMVars++; 
			double tWV900 = 0.001; tMVars++; double tWV925 = 0.001; tMVars++; double tWV950 = 0.001; tMVars++; double tWV975 = 0.001; tMVars++; 
			double tWV1000 = 0.001; tMVars++; double tWV0 = 0.001; tMVars++; 

			double tCAPE = 0.001; tMVars++; double tCIN = 0.001; tMVars++; double tLI = 0.001; tMVars++;
			double tPWAT = 0.001; tMVars++; double tHGT500 = 0.001; tMVars++; 

			final int iSx = 31; /* Relative Humidity Offset */
			final int iSs = 14;

			Scanner hrrrScanner = null; try {		
				hrrrScanner = new Scanner(hrrrSounding);
				while(hrrrScanner.hasNext()) {

					String line = hrrrScanner.nextLine();

					if(line.startsWith(((iSx+0)+(iSs*0))+":")) { String[] lineTmp = line.split(","); try { tRH100 = Double.parseDouble(lineTmp[gribSpot].replace("val=", "")); } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx+0)+(iSs*1))+":")) { String[] lineTmp = line.split(","); try { tRH125 = Double.parseDouble(lineTmp[gribSpot].replace("val=", "")); } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx+0)+(iSs*2))+":")) { String[] lineTmp = line.split(","); try { tRH150 = Double.parseDouble(lineTmp[gribSpot].replace("val=", "")); } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx+0)+(iSs*3))+":")) { String[] lineTmp = line.split(","); try { tRH175 = Double.parseDouble(lineTmp[gribSpot].replace("val=", "")); } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx+0)+(iSs*4))+":")) { String[] lineTmp = line.split(","); try { tRH200 = Double.parseDouble(lineTmp[gribSpot].replace("val=", "")); } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx+0)+(iSs*5))+":")) { String[] lineTmp = line.split(","); try { tRH225 = Double.parseDouble(lineTmp[gribSpot].replace("val=", "")); } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx+0)+(iSs*6))+":")) { String[] lineTmp = line.split(","); try { tRH250 = Double.parseDouble(lineTmp[gribSpot].replace("val=", "")); } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx+0)+(iSs*7))+":")) { String[] lineTmp = line.split(","); try { tRH275 = Double.parseDouble(lineTmp[gribSpot].replace("val=", "")); } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx+0)+(iSs*8))+":")) { String[] lineTmp = line.split(","); try { tRH300 = Double.parseDouble(lineTmp[gribSpot].replace("val=", "")); } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx+0)+(iSs*9))+":")) { String[] lineTmp = line.split(","); try { tRH325 = Double.parseDouble(lineTmp[gribSpot].replace("val=", "")); } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx+0)+(iSs*10))+":")) { String[] lineTmp = line.split(","); try { tRH350 = Double.parseDouble(lineTmp[gribSpot].replace("val=", "")); } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx+0)+(iSs*11))+":")) { String[] lineTmp = line.split(","); try { tRH375 = Double.parseDouble(lineTmp[gribSpot].replace("val=", "")); } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx+0)+(iSs*12))+":")) { String[] lineTmp = line.split(","); try { tRH400 = Double.parseDouble(lineTmp[gribSpot].replace("val=", "")); } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx+0)+(iSs*13))+":")) { String[] lineTmp = line.split(","); try { tRH425 = Double.parseDouble(lineTmp[gribSpot].replace("val=", "")); } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx+0)+(iSs*14))+":")) { String[] lineTmp = line.split(","); try { tRH450 = Double.parseDouble(lineTmp[gribSpot].replace("val=", "")); } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx+0)+(iSs*15))+":")) { String[] lineTmp = line.split(","); try { tRH475 = Double.parseDouble(lineTmp[gribSpot].replace("val=", "")); } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx+0)+(iSs*16))+":")) { String[] lineTmp = line.split(","); try { tRH500 = Double.parseDouble(lineTmp[gribSpot].replace("val=", "")); } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx+0)+(iSs*17))+":")) { String[] lineTmp = line.split(","); try { tRH525 = Double.parseDouble(lineTmp[gribSpot].replace("val=", "")); } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx+0)+(iSs*18))+":")) { String[] lineTmp = line.split(","); try { tRH550 = Double.parseDouble(lineTmp[gribSpot].replace("val=", "")); } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx+0)+(iSs*19))+":")) { String[] lineTmp = line.split(","); try { tRH575 = Double.parseDouble(lineTmp[gribSpot].replace("val=", "")); } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx+0)+(iSs*20))+":")) { String[] lineTmp = line.split(","); try { tRH600 = Double.parseDouble(lineTmp[gribSpot].replace("val=", "")); } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx+0)+(iSs*21))+":")) { String[] lineTmp = line.split(","); try { tRH625 = Double.parseDouble(lineTmp[gribSpot].replace("val=", "")); } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx+0)+(iSs*22))+":")) { String[] lineTmp = line.split(","); try { tRH650 = Double.parseDouble(lineTmp[gribSpot].replace("val=", "")); } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx+0)+(iSs*23))+":")) { String[] lineTmp = line.split(","); try { tRH675 = Double.parseDouble(lineTmp[gribSpot].replace("val=", "")); } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx+0)+(iSs*24))+":")) { String[] lineTmp = line.split(","); try { tRH700 = Double.parseDouble(lineTmp[gribSpot].replace("val=", "")); } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx+0)+(iSs*25))+":")) { String[] lineTmp = line.split(","); try { tRH725 = Double.parseDouble(lineTmp[gribSpot].replace("val=", "")); } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx+0)+(iSs*26))+":")) { String[] lineTmp = line.split(","); try { tRH750 = Double.parseDouble(lineTmp[gribSpot].replace("val=", "")); } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx+0)+(iSs*27))+":")) { String[] lineTmp = line.split(","); try { tRH775 = Double.parseDouble(lineTmp[gribSpot].replace("val=", "")); } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx+0)+(iSs*28))+":")) { String[] lineTmp = line.split(","); try { tRH800 = Double.parseDouble(lineTmp[gribSpot].replace("val=", "")); } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx+0)+(iSs*29))+":")) { String[] lineTmp = line.split(","); try { tRH825 = Double.parseDouble(lineTmp[gribSpot].replace("val=", "")); } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx+0)+(iSs*30))+":")) { String[] lineTmp = line.split(","); try { tRH850 = Double.parseDouble(lineTmp[gribSpot].replace("val=", "")); } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx+0)+(iSs*31))+":")) { String[] lineTmp = line.split(","); try { tRH875 = Double.parseDouble(lineTmp[gribSpot].replace("val=", "")); } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx+0)+(iSs*32))+":")) { String[] lineTmp = line.split(","); try { tRH900 = Double.parseDouble(lineTmp[gribSpot].replace("val=", "")); } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx+0)+(iSs*33))+":")) { String[] lineTmp = line.split(","); try { tRH925 = Double.parseDouble(lineTmp[gribSpot].replace("val=", "")); } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx+0)+(iSs*34))+":")) { String[] lineTmp = line.split(","); try { tRH950 = Double.parseDouble(lineTmp[gribSpot].replace("val=", "")); } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx+0)+(iSs*35))+":")) { String[] lineTmp = line.split(","); try { tRH975 = Double.parseDouble(lineTmp[gribSpot].replace("val=", "")); } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith("534:")) { String[] lineTmp = line.split(","); try { tRH1000 = Double.parseDouble(lineTmp[gribSpot].replace("val=", "")); } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith("609:")) { String[] lineTmp = line.split(","); try { tRH0 = Double.parseDouble(lineTmp[gribSpot].replace("val=", "")); } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }

					if(line.startsWith(((iSx-1)+(iSs*0))+":")) { String[] lineTmp = line.split(","); try { tTC100 = Double.parseDouble(lineTmp[gribSpot].replace("val=", ""))-273.15; } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx-1)+(iSs*1))+":")) { String[] lineTmp = line.split(","); try { tTC125 = Double.parseDouble(lineTmp[gribSpot].replace("val=", ""))-273.15; } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx-1)+(iSs*2))+":")) { String[] lineTmp = line.split(","); try { tTC150 = Double.parseDouble(lineTmp[gribSpot].replace("val=", ""))-273.15; } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx-1)+(iSs*3))+":")) { String[] lineTmp = line.split(","); try { tTC175 = Double.parseDouble(lineTmp[gribSpot].replace("val=", ""))-273.15; } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx-1)+(iSs*4))+":")) { String[] lineTmp = line.split(","); try { tTC200 = Double.parseDouble(lineTmp[gribSpot].replace("val=", ""))-273.15; } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx-1)+(iSs*5))+":")) { String[] lineTmp = line.split(","); try { tTC225 = Double.parseDouble(lineTmp[gribSpot].replace("val=", ""))-273.15; } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx-1)+(iSs*6))+":")) { String[] lineTmp = line.split(","); try { tTC250 = Double.parseDouble(lineTmp[gribSpot].replace("val=", ""))-273.15; } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx-1)+(iSs*7))+":")) { String[] lineTmp = line.split(","); try { tTC275 = Double.parseDouble(lineTmp[gribSpot].replace("val=", ""))-273.15; } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx-1)+(iSs*8))+":")) { String[] lineTmp = line.split(","); try { tTC300 = Double.parseDouble(lineTmp[gribSpot].replace("val=", ""))-273.15; } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx-1)+(iSs*9))+":")) { String[] lineTmp = line.split(","); try { tTC325 = Double.parseDouble(lineTmp[gribSpot].replace("val=", ""))-273.15; } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx-1)+(iSs*10))+":")) { String[] lineTmp = line.split(","); try { tTC350 = Double.parseDouble(lineTmp[gribSpot].replace("val=", ""))-273.15; } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx-1)+(iSs*11))+":")) { String[] lineTmp = line.split(","); try { tTC375 = Double.parseDouble(lineTmp[gribSpot].replace("val=", ""))-273.15; } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx-1)+(iSs*12))+":")) { String[] lineTmp = line.split(","); try { tTC400 = Double.parseDouble(lineTmp[gribSpot].replace("val=", ""))-273.15; } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx-1)+(iSs*13))+":")) { String[] lineTmp = line.split(","); try { tTC425 = Double.parseDouble(lineTmp[gribSpot].replace("val=", ""))-273.15; } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx-1)+(iSs*14))+":")) { String[] lineTmp = line.split(","); try { tTC450 = Double.parseDouble(lineTmp[gribSpot].replace("val=", ""))-273.15; } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx-1)+(iSs*15))+":")) { String[] lineTmp = line.split(","); try { tTC475 = Double.parseDouble(lineTmp[gribSpot].replace("val=", ""))-273.15; } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx-1)+(iSs*16))+":")) { String[] lineTmp = line.split(","); try { tTC500 = Double.parseDouble(lineTmp[gribSpot].replace("val=", ""))-273.15; } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx-1)+(iSs*17))+":")) { String[] lineTmp = line.split(","); try { tTC525 = Double.parseDouble(lineTmp[gribSpot].replace("val=", ""))-273.15; } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx-1)+(iSs*18))+":")) { String[] lineTmp = line.split(","); try { tTC550 = Double.parseDouble(lineTmp[gribSpot].replace("val=", ""))-273.15; } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx-1)+(iSs*19))+":")) { String[] lineTmp = line.split(","); try { tTC575 = Double.parseDouble(lineTmp[gribSpot].replace("val=", ""))-273.15; } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx-1)+(iSs*20))+":")) { String[] lineTmp = line.split(","); try { tTC600 = Double.parseDouble(lineTmp[gribSpot].replace("val=", ""))-273.15; } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx-1)+(iSs*21))+":")) { String[] lineTmp = line.split(","); try { tTC625 = Double.parseDouble(lineTmp[gribSpot].replace("val=", ""))-273.15; } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx-1)+(iSs*22))+":")) { String[] lineTmp = line.split(","); try { tTC650 = Double.parseDouble(lineTmp[gribSpot].replace("val=", ""))-273.15; } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx-1)+(iSs*23))+":")) { String[] lineTmp = line.split(","); try { tTC675 = Double.parseDouble(lineTmp[gribSpot].replace("val=", ""))-273.15; } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx-1)+(iSs*24))+":")) { String[] lineTmp = line.split(","); try { tTC700 = Double.parseDouble(lineTmp[gribSpot].replace("val=", ""))-273.15; } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx-1)+(iSs*25))+":")) { String[] lineTmp = line.split(","); try { tTC725 = Double.parseDouble(lineTmp[gribSpot].replace("val=", ""))-273.15; } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx-1)+(iSs*26))+":")) { String[] lineTmp = line.split(","); try { tTC750 = Double.parseDouble(lineTmp[gribSpot].replace("val=", ""))-273.15; } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx-1)+(iSs*27))+":")) { String[] lineTmp = line.split(","); try { tTC775 = Double.parseDouble(lineTmp[gribSpot].replace("val=", ""))-273.15; } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx-1)+(iSs*28))+":")) { String[] lineTmp = line.split(","); try { tTC800 = Double.parseDouble(lineTmp[gribSpot].replace("val=", ""))-273.15; } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx-1)+(iSs*29))+":")) { String[] lineTmp = line.split(","); try { tTC825 = Double.parseDouble(lineTmp[gribSpot].replace("val=", ""))-273.15; } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx-1)+(iSs*30))+":")) { String[] lineTmp = line.split(","); try { tTC850 = Double.parseDouble(lineTmp[gribSpot].replace("val=", ""))-273.15; } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx-1)+(iSs*31))+":")) { String[] lineTmp = line.split(","); try { tTC875 = Double.parseDouble(lineTmp[gribSpot].replace("val=", ""))-273.15; } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx-1)+(iSs*32))+":")) { String[] lineTmp = line.split(","); try { tTC900 = Double.parseDouble(lineTmp[gribSpot].replace("val=", ""))-273.15; } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx-1)+(iSs*33))+":")) { String[] lineTmp = line.split(","); try { tTC925 = Double.parseDouble(lineTmp[gribSpot].replace("val=", ""))-273.15; } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx-1)+(iSs*34))+":")) { String[] lineTmp = line.split(","); try { tTC950 = Double.parseDouble(lineTmp[gribSpot].replace("val=", ""))-273.15; } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx-1)+(iSs*35))+":")) { String[] lineTmp = line.split(","); try { tTC975 = Double.parseDouble(lineTmp[gribSpot].replace("val=", ""))-273.15; } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith("533:")) { String[] lineTmp = line.split(","); try { tTC1000 = Double.parseDouble(lineTmp[gribSpot].replace("val=", ""))-273.15; } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith("605:")) { String[] lineTmp = line.split(","); try { tTC0 = Double.parseDouble(lineTmp[gribSpot].replace("val=", ""))-273.15; } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }

					if(line.startsWith(((iSx+4)+(iSs*0))+":")) { String[] lineTmp = line.split(","); try { tWU100 = Double.parseDouble(lineTmp[gribSpot].replace("val=", "")); } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx+4)+(iSs*1))+":")) { String[] lineTmp = line.split(","); try { tWU125 = Double.parseDouble(lineTmp[gribSpot].replace("val=", "")); } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx+4)+(iSs*2))+":")) { String[] lineTmp = line.split(","); try { tWU150 = Double.parseDouble(lineTmp[gribSpot].replace("val=", "")); } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx+4)+(iSs*3))+":")) { String[] lineTmp = line.split(","); try { tWU175 = Double.parseDouble(lineTmp[gribSpot].replace("val=", "")); } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx+4)+(iSs*4))+":")) { String[] lineTmp = line.split(","); try { tWU200 = Double.parseDouble(lineTmp[gribSpot].replace("val=", "")); } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx+4)+(iSs*5))+":")) { String[] lineTmp = line.split(","); try { tWU225 = Double.parseDouble(lineTmp[gribSpot].replace("val=", "")); } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx+4)+(iSs*6))+":")) { String[] lineTmp = line.split(","); try { tWU250 = Double.parseDouble(lineTmp[gribSpot].replace("val=", "")); } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx+4)+(iSs*7))+":")) { String[] lineTmp = line.split(","); try { tWU275 = Double.parseDouble(lineTmp[gribSpot].replace("val=", "")); } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx+4)+(iSs*8))+":")) { String[] lineTmp = line.split(","); try { tWU300 = Double.parseDouble(lineTmp[gribSpot].replace("val=", "")); } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx+4)+(iSs*9))+":")) { String[] lineTmp = line.split(","); try { tWU325 = Double.parseDouble(lineTmp[gribSpot].replace("val=", "")); } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx+4)+(iSs*10))+":")) { String[] lineTmp = line.split(","); try { tWU350 = Double.parseDouble(lineTmp[gribSpot].replace("val=", "")); } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx+4)+(iSs*11))+":")) { String[] lineTmp = line.split(","); try { tWU375 = Double.parseDouble(lineTmp[gribSpot].replace("val=", "")); } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx+4)+(iSs*12))+":")) { String[] lineTmp = line.split(","); try { tWU400 = Double.parseDouble(lineTmp[gribSpot].replace("val=", "")); } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx+4)+(iSs*13))+":")) { String[] lineTmp = line.split(","); try { tWU425 = Double.parseDouble(lineTmp[gribSpot].replace("val=", "")); } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx+4)+(iSs*14))+":")) { String[] lineTmp = line.split(","); try { tWU450 = Double.parseDouble(lineTmp[gribSpot].replace("val=", "")); } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx+4)+(iSs*15))+":")) { String[] lineTmp = line.split(","); try { tWU475 = Double.parseDouble(lineTmp[gribSpot].replace("val=", "")); } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx+4)+(iSs*16))+":")) { String[] lineTmp = line.split(","); try { tWU500 = Double.parseDouble(lineTmp[gribSpot].replace("val=", "")); } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx+4)+(iSs*17))+":")) { String[] lineTmp = line.split(","); try { tWU525 = Double.parseDouble(lineTmp[gribSpot].replace("val=", "")); } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx+4)+(iSs*18))+":")) { String[] lineTmp = line.split(","); try { tWU550 = Double.parseDouble(lineTmp[gribSpot].replace("val=", "")); } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx+4)+(iSs*19))+":")) { String[] lineTmp = line.split(","); try { tWU575 = Double.parseDouble(lineTmp[gribSpot].replace("val=", "")); } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx+4)+(iSs*20))+":")) { String[] lineTmp = line.split(","); try { tWU600 = Double.parseDouble(lineTmp[gribSpot].replace("val=", "")); } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx+4)+(iSs*21))+":")) { String[] lineTmp = line.split(","); try { tWU625 = Double.parseDouble(lineTmp[gribSpot].replace("val=", "")); } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx+4)+(iSs*22))+":")) { String[] lineTmp = line.split(","); try { tWU650 = Double.parseDouble(lineTmp[gribSpot].replace("val=", "")); } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx+4)+(iSs*23))+":")) { String[] lineTmp = line.split(","); try { tWU675 = Double.parseDouble(lineTmp[gribSpot].replace("val=", "")); } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx+4)+(iSs*24))+":")) { String[] lineTmp = line.split(","); try { tWU700 = Double.parseDouble(lineTmp[gribSpot].replace("val=", "")); } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx+4)+(iSs*25))+":")) { String[] lineTmp = line.split(","); try { tWU725 = Double.parseDouble(lineTmp[gribSpot].replace("val=", "")); } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx+4)+(iSs*26))+":")) { String[] lineTmp = line.split(","); try { tWU750 = Double.parseDouble(lineTmp[gribSpot].replace("val=", "")); } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx+4)+(iSs*27))+":")) { String[] lineTmp = line.split(","); try { tWU775 = Double.parseDouble(lineTmp[gribSpot].replace("val=", "")); } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx+4)+(iSs*28))+":")) { String[] lineTmp = line.split(","); try { tWU800 = Double.parseDouble(lineTmp[gribSpot].replace("val=", "")); } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx+4)+(iSs*29))+":")) { String[] lineTmp = line.split(","); try { tWU825 = Double.parseDouble(lineTmp[gribSpot].replace("val=", "")); } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx+4)+(iSs*30))+":")) { String[] lineTmp = line.split(","); try { tWU850 = Double.parseDouble(lineTmp[gribSpot].replace("val=", "")); } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx+4)+(iSs*31))+":")) { String[] lineTmp = line.split(","); try { tWU875 = Double.parseDouble(lineTmp[gribSpot].replace("val=", "")); } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx+4)+(iSs*32))+":")) { String[] lineTmp = line.split(","); try { tWU900 = Double.parseDouble(lineTmp[gribSpot].replace("val=", "")); } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx+4)+(iSs*33))+":")) { String[] lineTmp = line.split(","); try { tWU925 = Double.parseDouble(lineTmp[gribSpot].replace("val=", "")); } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx+4)+(iSs*34))+":")) { String[] lineTmp = line.split(","); try { tWU950 = Double.parseDouble(lineTmp[gribSpot].replace("val=", "")); } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx+4)+(iSs*35))+":")) { String[] lineTmp = line.split(","); try { tWU975 = Double.parseDouble(lineTmp[gribSpot].replace("val=", "")); } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith("538:")) { String[] lineTmp = line.split(","); try { tWU1000 = Double.parseDouble(lineTmp[gribSpot].replace("val=", "")); } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith("610:")) { String[] lineTmp = line.split(","); try { tWU0 = Double.parseDouble(lineTmp[gribSpot].replace("val=", "")); } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }

					if(line.startsWith(((iSx+5)+(iSs*0))+":")) { String[] lineTmp = line.split(","); try { tWV100 = Double.parseDouble(lineTmp[gribSpot].replace("val=", "")); } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx+5)+(iSs*1))+":")) { String[] lineTmp = line.split(","); try { tWV125 = Double.parseDouble(lineTmp[gribSpot].replace("val=", "")); } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx+5)+(iSs*2))+":")) { String[] lineTmp = line.split(","); try { tWV150 = Double.parseDouble(lineTmp[gribSpot].replace("val=", "")); } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx+5)+(iSs*3))+":")) { String[] lineTmp = line.split(","); try { tWV175 = Double.parseDouble(lineTmp[gribSpot].replace("val=", "")); } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx+5)+(iSs*4))+":")) { String[] lineTmp = line.split(","); try { tWV200 = Double.parseDouble(lineTmp[gribSpot].replace("val=", "")); } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx+5)+(iSs*5))+":")) { String[] lineTmp = line.split(","); try { tWV225 = Double.parseDouble(lineTmp[gribSpot].replace("val=", "")); } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx+5)+(iSs*6))+":")) { String[] lineTmp = line.split(","); try { tWV250 = Double.parseDouble(lineTmp[gribSpot].replace("val=", "")); } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx+5)+(iSs*7))+":")) { String[] lineTmp = line.split(","); try { tWV275 = Double.parseDouble(lineTmp[gribSpot].replace("val=", "")); } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx+5)+(iSs*8))+":")) { String[] lineTmp = line.split(","); try { tWV300 = Double.parseDouble(lineTmp[gribSpot].replace("val=", "")); } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx+5)+(iSs*9))+":")) { String[] lineTmp = line.split(","); try { tWV325 = Double.parseDouble(lineTmp[gribSpot].replace("val=", "")); } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx+5)+(iSs*10))+":")) { String[] lineTmp = line.split(","); try { tWV350 = Double.parseDouble(lineTmp[gribSpot].replace("val=", "")); } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx+5)+(iSs*11))+":")) { String[] lineTmp = line.split(","); try { tWV375 = Double.parseDouble(lineTmp[gribSpot].replace("val=", "")); } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx+5)+(iSs*12))+":")) { String[] lineTmp = line.split(","); try { tWV400 = Double.parseDouble(lineTmp[gribSpot].replace("val=", "")); } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx+5)+(iSs*13))+":")) { String[] lineTmp = line.split(","); try { tWV425 = Double.parseDouble(lineTmp[gribSpot].replace("val=", "")); } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx+5)+(iSs*14))+":")) { String[] lineTmp = line.split(","); try { tWV450 = Double.parseDouble(lineTmp[gribSpot].replace("val=", "")); } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx+5)+(iSs*15))+":")) { String[] lineTmp = line.split(","); try { tWV475 = Double.parseDouble(lineTmp[gribSpot].replace("val=", "")); } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx+5)+(iSs*16))+":")) { String[] lineTmp = line.split(","); try { tWV500 = Double.parseDouble(lineTmp[gribSpot].replace("val=", "")); } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx+5)+(iSs*17))+":")) { String[] lineTmp = line.split(","); try { tWV525 = Double.parseDouble(lineTmp[gribSpot].replace("val=", "")); } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx+5)+(iSs*18))+":")) { String[] lineTmp = line.split(","); try { tWV550 = Double.parseDouble(lineTmp[gribSpot].replace("val=", "")); } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx+5)+(iSs*19))+":")) { String[] lineTmp = line.split(","); try { tWV575 = Double.parseDouble(lineTmp[gribSpot].replace("val=", "")); } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx+5)+(iSs*20))+":")) { String[] lineTmp = line.split(","); try { tWV600 = Double.parseDouble(lineTmp[gribSpot].replace("val=", "")); } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx+5)+(iSs*21))+":")) { String[] lineTmp = line.split(","); try { tWV625 = Double.parseDouble(lineTmp[gribSpot].replace("val=", "")); } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx+5)+(iSs*22))+":")) { String[] lineTmp = line.split(","); try { tWV650 = Double.parseDouble(lineTmp[gribSpot].replace("val=", "")); } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx+5)+(iSs*23))+":")) { String[] lineTmp = line.split(","); try { tWV675 = Double.parseDouble(lineTmp[gribSpot].replace("val=", "")); } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx+5)+(iSs*24))+":")) { String[] lineTmp = line.split(","); try { tWV700 = Double.parseDouble(lineTmp[gribSpot].replace("val=", "")); } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx+5)+(iSs*25))+":")) { String[] lineTmp = line.split(","); try { tWV725 = Double.parseDouble(lineTmp[gribSpot].replace("val=", "")); } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx+5)+(iSs*26))+":")) { String[] lineTmp = line.split(","); try { tWV750 = Double.parseDouble(lineTmp[gribSpot].replace("val=", "")); } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx+5)+(iSs*27))+":")) { String[] lineTmp = line.split(","); try { tWV775 = Double.parseDouble(lineTmp[gribSpot].replace("val=", "")); } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx+5)+(iSs*28))+":")) { String[] lineTmp = line.split(","); try { tWV800 = Double.parseDouble(lineTmp[gribSpot].replace("val=", "")); } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx+5)+(iSs*29))+":")) { String[] lineTmp = line.split(","); try { tWV825 = Double.parseDouble(lineTmp[gribSpot].replace("val=", "")); } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx+5)+(iSs*30))+":")) { String[] lineTmp = line.split(","); try { tWV850 = Double.parseDouble(lineTmp[gribSpot].replace("val=", "")); } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx+5)+(iSs*31))+":")) { String[] lineTmp = line.split(","); try { tWV875 = Double.parseDouble(lineTmp[gribSpot].replace("val=", "")); } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx+5)+(iSs*32))+":")) { String[] lineTmp = line.split(","); try { tWV900 = Double.parseDouble(lineTmp[gribSpot].replace("val=", "")); } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx+5)+(iSs*33))+":")) { String[] lineTmp = line.split(","); try { tWV925 = Double.parseDouble(lineTmp[gribSpot].replace("val=", "")); } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx+5)+(iSs*34))+":")) { String[] lineTmp = line.split(","); try { tWV950 = Double.parseDouble(lineTmp[gribSpot].replace("val=", "")); } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith(((iSx+5)+(iSs*35))+":")) { String[] lineTmp = line.split(","); try { tWV975 = Double.parseDouble(lineTmp[gribSpot].replace("val=", "")); } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith("539:")) { String[] lineTmp = line.split(","); try { tWV1000 = Double.parseDouble(lineTmp[gribSpot].replace("val=", "")); } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith("611:")) { String[] lineTmp = line.split(","); try { tWV0 = Double.parseDouble(lineTmp[gribSpot].replace("val=", "")); } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }

					if(line.startsWith("677:")) { String[] lineTmp = line.split(","); try { tCAPE = Double.parseDouble(lineTmp[gribSpot].replace("val=", "")); } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith("676:")) { String[] lineTmp = line.split(","); try { tCIN = Double.parseDouble(lineTmp[gribSpot].replace("val=", "")); } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith("633:")) { String[] lineTmp = line.split(","); try { tLI = Double.parseDouble(lineTmp[gribSpot].replace("val=", "")); } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith("636:")) { String[] lineTmp = line.split(","); try { tPWAT = Double.parseDouble(lineTmp[gribSpot].replace("val=", "")); } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith("253:")) { String[] lineTmp = line.split(","); try { tHGT500 = Double.parseDouble(lineTmp[gribSpot].replace("val=", "")); } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }

				}
			}
			catch (FileNotFoundException fnf) { fnf.printStackTrace(); }
			catch (NumberFormatException nfx) { nfx.printStackTrace(); }

			double tTD100 = calcDwpt(tTC100, tRH100); double tTD125 = tTC125-(100-tRH125)/5; double tTD150 = tTC150-(100-tRH150)/5; double tTD175 = tTC175-(100-tRH175)/5;
			double tTD200 = tTC200-(100-tRH200)/5; double tTD225 = tTC225-(100-tRH225)/5; double tTD250 = tTC250-(100-tRH250)/5; double tTD275 = tTC275-(100-tRH275)/5;
			double tTD300 = tTC300-(100-tRH300)/5; double tTD325 = tTC325-(100-tRH325)/5; double tTD350 = tTC350-(100-tRH350)/5; double tTD375 = tTC375-(100-tRH375)/5;
			double tTD400 = tTC400-(100-tRH400)/5; double tTD425 = tTC425-(100-tRH425)/5; double tTD450 = tTC450-(100-tRH450)/5; double tTD475 = tTC475-(100-tRH475)/5;
			double tTD500 = tTC500-(100-tRH500)/5; double tTD525 = tTC525-(100-tRH525)/5; double tTD550 = tTC550-(100-tRH550)/5; double tTD575 = tTC575-(100-tRH575)/5;
			double tTD600 = tTC600-(100-tRH600)/5; double tTD625 = tTC625-(100-tRH625)/5; double tTD650 = tTC650-(100-tRH650)/5; double tTD675 = tTC675-(100-tRH675)/5;
			double tTD700 = tTC700-(100-tRH700)/5; double tTD725 = tTC725-(100-tRH725)/5; double tTD750 = tTC750-(100-tRH750)/5; double tTD775 = tTC775-(100-tRH775)/5;
			double tTD800 = tTC800-(100-tRH800)/5; double tTD825 = tTC825-(100-tRH825)/5; double tTD850 = tTC850-(100-tRH850)/5; double tTD875 = tTC875-(100-tRH875)/5;
			double tTD900 = tTC900-(100-tRH900)/5; double tTD925 = tTC925-(100-tRH925)/5; double tTD950 = tTC950-(100-tRH950)/5; double tTD975 = tTC975-(100-tRH975)/5;
			double tTD1000 = tTC1000-(100-tRH1000)/5; double tTD0 = tTC0-(100-tRH0)/5;

			double tCCL = 0.001; double tFZLV = 0.001; double tWZLV = 0.001;

			double tWD100 = windDirCalc(tWU100, tWV100); double tWD125 = windDirCalc(tWU125, tWV125); double tWD150 = windDirCalc(tWU150, tWV150); double tWD175 = windDirCalc(tWU175, tWV175); 
			double tWS100 = windSpdCalc(tWU100, tWV100); double tWS125 = windSpdCalc(tWU125, tWV125); double tWS150 = windSpdCalc(tWU150, tWV150); double tWS175 = windSpdCalc(tWU175, tWV175);
			double tWD200 = windDirCalc(tWU200, tWV200); double tWD225 = windDirCalc(tWU225, tWV225); double tWD250 = windDirCalc(tWU250, tWV250); double tWD275 = windDirCalc(tWU275, tWV275); 
			double tWS200 = windSpdCalc(tWU200, tWV200); double tWS225 = windSpdCalc(tWU225, tWV225); double tWS250 = windSpdCalc(tWU250, tWV250); double tWS275 = windSpdCalc(tWU275, tWV275);
			double tWD300 = windDirCalc(tWU300, tWV300); double tWD325 = windDirCalc(tWU325, tWV325); double tWD350 = windDirCalc(tWU350, tWV350); double tWD375 = windDirCalc(tWU375, tWV375); 
			double tWS300 = windSpdCalc(tWU300, tWV300); double tWS325 = windSpdCalc(tWU325, tWV325); double tWS350 = windSpdCalc(tWU350, tWV350); double tWS375 = windSpdCalc(tWU375, tWV375);
			double tWD400 = windDirCalc(tWU400, tWV400); double tWD425 = windDirCalc(tWU425, tWV425); double tWD450 = windDirCalc(tWU450, tWV450); double tWD475 = windDirCalc(tWU475, tWV475); 
			double tWS400 = windSpdCalc(tWU400, tWV400); double tWS425 = windSpdCalc(tWU425, tWV425); double tWS450 = windSpdCalc(tWU450, tWV450); double tWS475 = windSpdCalc(tWU475, tWV475);
			double tWD500 = windDirCalc(tWU500, tWV500); double tWD525 = windDirCalc(tWU525, tWV525); double tWD550 = windDirCalc(tWU550, tWV550); double tWD575 = windDirCalc(tWU575, tWV575); 
			double tWS500 = windSpdCalc(tWU500, tWV500); double tWS525 = windSpdCalc(tWU525, tWV525); double tWS550 = windSpdCalc(tWU550, tWV550); double tWS575 = windSpdCalc(tWU575, tWV575);
			double tWD600 = windDirCalc(tWU600, tWV600); double tWD625 = windDirCalc(tWU625, tWV625); double tWD650 = windDirCalc(tWU650, tWV650); double tWD675 = windDirCalc(tWU675, tWV675); 
			double tWS600 = windSpdCalc(tWU600, tWV600); double tWS625 = windSpdCalc(tWU625, tWV625); double tWS650 = windSpdCalc(tWU650, tWV650); double tWS675 = windSpdCalc(tWU675, tWV675);
			double tWD700 = windDirCalc(tWU700, tWV700); double tWD725 = windDirCalc(tWU725, tWV725); double tWD750 = windDirCalc(tWU750, tWV750); double tWD775 = windDirCalc(tWU775, tWV775); 
			double tWS700 = windSpdCalc(tWU700, tWV700); double tWS725 = windSpdCalc(tWU725, tWV725); double tWS750 = windSpdCalc(tWU750, tWV750); double tWS775 = windSpdCalc(tWU775, tWV775);
			double tWD800 = windDirCalc(tWU800, tWV800); double tWD825 = windDirCalc(tWU825, tWV825); double tWD850 = windDirCalc(tWU850, tWV850); double tWD875 = windDirCalc(tWU875, tWV875); 
			double tWS800 = windSpdCalc(tWU800, tWV800); double tWS825 = windSpdCalc(tWU825, tWV825); double tWS850 = windSpdCalc(tWU850, tWV850); double tWS875 = windSpdCalc(tWU875, tWV875);
			double tWD900 = windDirCalc(tWU900, tWV900); double tWD925 = windDirCalc(tWU925, tWV925); double tWD950 = windDirCalc(tWU950, tWV950); double tWD975 = windDirCalc(tWU975, tWV975); 
			double tWS900 = windSpdCalc(tWU900, tWV900); double tWS925 = windSpdCalc(tWU925, tWV925); double tWS950 = windSpdCalc(tWU950, tWV950); double tWS975 = windSpdCalc(tWU975, tWV975);
			double tWD1000 = windDirCalc(tWU1000, tWV1000); double tWD0 = windDirCalc(tWU0, tWV0); 
			double tWS1000 = windSpdCalc(tWU1000, tWV1000); double tWS0 = windSpdCalc(tWU0, tWV0);

			Scanner rapScanner = null; try {		
				rapScanner = new Scanner(rapSounding);
				while(rapScanner.hasNext()) {

					String line = rapScanner.nextLine();

					if(line.startsWith("238:")) { String[] lineTmp = line.split(","); try { tCCL = Double.parseDouble(lineTmp[gribSpot].replace("val=", "")); } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith("258:")) { String[] lineTmp = line.split(","); try { tFZLV = Double.parseDouble(lineTmp[gribSpot].replace("val=", ""))*3.28084; } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }
					if(line.startsWith("224:")) { String[] lineTmp = line.split(","); try { tWZLV = Double.parseDouble(lineTmp[gribSpot].replace("val=", "")); } catch (ArrayIndexOutOfBoundsException aix) { aix.printStackTrace(); } }

				}
			}
			catch (FileNotFoundException fnf) { fnf.printStackTrace(); }
			catch (NumberFormatException nfx) { nfx.printStackTrace(); }

			double tSLCL = calcSLCL(tTC0, tRH0);
			
			if (tRH100 != 0.001) { jStationData.put("RH100", tRH100); } else { tMNulls++; }
			if (tRH125 != 0.001) { jStationData.put("RH125", tRH125); } else { tMNulls++; }
			if (tRH150 != 0.001) { jStationData.put("RH150", tRH150); } else { tMNulls++; }
			if (tRH175 != 0.001) { jStationData.put("RH175", tRH175); } else { tMNulls++; }
			if (tRH200 != 0.001) { jStationData.put("RH200", tRH200); } else { tMNulls++; }
			if (tRH225 != 0.001) { jStationData.put("RH225", tRH225); } else { tMNulls++; }
			if (tRH250 != 0.001) { jStationData.put("RH250", tRH250); } else { tMNulls++; }
			if (tRH275 != 0.001) { jStationData.put("RH275", tRH275); } else { tMNulls++; }
			if (tRH300 != 0.001) { jStationData.put("RH300", tRH300); } else { tMNulls++; }
			if (tRH325 != 0.001) { jStationData.put("RH325", tRH325); } else { tMNulls++; }
			if (tRH350 != 0.001) { jStationData.put("RH350", tRH350); } else { tMNulls++; }
			if (tRH375 != 0.001) { jStationData.put("RH375", tRH375); } else { tMNulls++; }
			if (tRH400 != 0.001) { jStationData.put("RH400", tRH400); } else { tMNulls++; }
			if (tRH425 != 0.001) { jStationData.put("RH425", tRH425); } else { tMNulls++; }
			if (tRH450 != 0.001) { jStationData.put("RH450", tRH450); } else { tMNulls++; }
			if (tRH475 != 0.001) { jStationData.put("RH475", tRH475); } else { tMNulls++; }
			if (tRH500 != 0.001) { jStationData.put("RH500", tRH500); } else { tMNulls++; }
			if (tRH525 != 0.001) { jStationData.put("RH525", tRH525); } else { tMNulls++; }
			if (tRH550 != 0.001) { jStationData.put("RH550", tRH550); } else { tMNulls++; }
			if (tRH575 != 0.001) { jStationData.put("RH575", tRH575); } else { tMNulls++; }
			if (tRH600 != 0.001) { jStationData.put("RH600", tRH600); } else { tMNulls++; }
			if (tRH625 != 0.001) { jStationData.put("RH625", tRH625); } else { tMNulls++; }
			if (tRH650 != 0.001) { jStationData.put("RH650", tRH650); } else { tMNulls++; }
			if (tRH675 != 0.001) { jStationData.put("RH675", tRH675); } else { tMNulls++; }
			if (tRH700 != 0.001) { jStationData.put("RH700", tRH700); } else { tMNulls++; }
			if (tRH725 != 0.001) { jStationData.put("RH725", tRH725); } else { tMNulls++; }
			if (tRH750 != 0.001) { jStationData.put("RH750", tRH750); } else { tMNulls++; }
			if (tRH775 != 0.001) { jStationData.put("RH775", tRH775); } else { tMNulls++; }
			if (tRH800 != 0.001) { jStationData.put("RH800", tRH800); } else { tMNulls++; }
			if (tRH825 != 0.001) { jStationData.put("RH825", tRH825); } else { tMNulls++; }
			if (tRH850 != 0.001) { jStationData.put("RH850", tRH850); } else { tMNulls++; }
			if (tRH875 != 0.001) { jStationData.put("RH875", tRH875); } else { tMNulls++; }
			if (tRH900 != 0.001) { jStationData.put("RH900", tRH900); } else { tMNulls++; }
			if (tRH925 != 0.001) { jStationData.put("RH925", tRH925); } else { tMNulls++; }
			if (tRH950 != 0.001) { jStationData.put("RH950", tRH950); } else { tMNulls++; }
			if (tRH975 != 0.001) { jStationData.put("RH975", tRH975); } else { tMNulls++; }
			if (tRH1000 != 0.001) { jStationData.put("RH1000", tRH1000); } else { tMNulls++; }
			if (tRH0 != 0.001) { jStationData.put("RH0", tRH0); } else { tMNulls++; }
			
			if (tWD100 != 0.001) { jStationData.put("WD100", df.format(tWD100)); } else { tMNulls++; }
			if (tWD125 != 0.001) { jStationData.put("WD125", df.format(tWD125)); } else { tMNulls++; }
			if (tWD150 != 0.001) { jStationData.put("WD150", df.format(tWD150)); } else { tMNulls++; }
			if (tWD175 != 0.001) { jStationData.put("WD175", df.format(tWD175)); } else { tMNulls++; }
			if (tWD200 != 0.001) { jStationData.put("WD200", df.format(tWD200)); } else { tMNulls++; }
			if (tWD225 != 0.001) { jStationData.put("WD225", df.format(tWD225)); } else { tMNulls++; }
			if (tWD250 != 0.001) { jStationData.put("WD250", df.format(tWD250)); } else { tMNulls++; }
			if (tWD275 != 0.001) { jStationData.put("WD275", df.format(tWD275)); } else { tMNulls++; }
			if (tWD300 != 0.001) { jStationData.put("WD300", df.format(tWD300)); } else { tMNulls++; }
			if (tWD325 != 0.001) { jStationData.put("WD325", df.format(tWD325)); } else { tMNulls++; }
			if (tWD350 != 0.001) { jStationData.put("WD350", df.format(tWD350)); } else { tMNulls++; }
			if (tWD375 != 0.001) { jStationData.put("WD375", df.format(tWD375)); } else { tMNulls++; }
			if (tWD400 != 0.001) { jStationData.put("WD400", df.format(tWD400)); } else { tMNulls++; }
			if (tWD425 != 0.001) { jStationData.put("WD425", df.format(tWD425)); } else { tMNulls++; }
			if (tWD450 != 0.001) { jStationData.put("WD450", df.format(tWD450)); } else { tMNulls++; }
			if (tWD475 != 0.001) { jStationData.put("WD475", df.format(tWD475)); } else { tMNulls++; }
			if (tWD500 != 0.001) { jStationData.put("WD500", df.format(tWD500)); } else { tMNulls++; }
			if (tWD525 != 0.001) { jStationData.put("WD525", df.format(tWD525)); } else { tMNulls++; }
			if (tWD550 != 0.001) { jStationData.put("WD550", df.format(tWD550)); } else { tMNulls++; }
			if (tWD575 != 0.001) { jStationData.put("WD575", df.format(tWD575)); } else { tMNulls++; }
			if (tWD600 != 0.001) { jStationData.put("WD600", df.format(tWD600)); } else { tMNulls++; }
			if (tWD625 != 0.001) { jStationData.put("WD625", df.format(tWD625)); } else { tMNulls++; }
			if (tWD650 != 0.001) { jStationData.put("WD650", df.format(tWD650)); } else { tMNulls++; }
			if (tWD675 != 0.001) { jStationData.put("WD675", df.format(tWD675)); } else { tMNulls++; }
			if (tWD700 != 0.001) { jStationData.put("WD700", df.format(tWD700)); } else { tMNulls++; }
			if (tWD725 != 0.001) { jStationData.put("WD725", df.format(tWD725)); } else { tMNulls++; }
			if (tWD750 != 0.001) { jStationData.put("WD750", df.format(tWD750)); } else { tMNulls++; }
			if (tWD775 != 0.001) { jStationData.put("WD775", df.format(tWD775)); } else { tMNulls++; }
			if (tWD800 != 0.001) { jStationData.put("WD800", df.format(tWD800)); } else { tMNulls++; }
			if (tWD825 != 0.001) { jStationData.put("WD825", df.format(tWD825)); } else { tMNulls++; }
			if (tWD850 != 0.001) { jStationData.put("WD850", df.format(tWD850)); } else { tMNulls++; }
			if (tWD875 != 0.001) { jStationData.put("WD875", df.format(tWD875)); } else { tMNulls++; }
			if (tWD900 != 0.001) { jStationData.put("WD900", df.format(tWD900)); } else { tMNulls++; }
			if (tWD925 != 0.001) { jStationData.put("WD925", df.format(tWD925)); } else { tMNulls++; }
			if (tWD950 != 0.001) { jStationData.put("WD950", df.format(tWD950)); } else { tMNulls++; }
			if (tWD975 != 0.001) { jStationData.put("WD975", df.format(tWD975)); } else { tMNulls++; }
			if (tWD1000 != 0.001) { jStationData.put("WD1000", df.format(tWD1000)); } else { tMNulls++; }
			if (tWD0 != 0.001) { jStationData.put("WD0", df.format(tWD0)); } else { tMNulls++; }
			
			if (tWS100 != 0.001) { jStationData.put("WS100", df.format(tWS100)); } else { tMNulls++; }
			if (tWS125 != 0.001) { jStationData.put("WS125", df.format(tWS125)); } else { tMNulls++; }
			if (tWS150 != 0.001) { jStationData.put("WS150", df.format(tWS150)); } else { tMNulls++; }
			if (tWS175 != 0.001) { jStationData.put("WS175", df.format(tWS175)); } else { tMNulls++; }
			if (tWS200 != 0.001) { jStationData.put("WS200", df.format(tWS200)); } else { tMNulls++; }
			if (tWS225 != 0.001) { jStationData.put("WS225", df.format(tWS225)); } else { tMNulls++; }
			if (tWS250 != 0.001) { jStationData.put("WS250", df.format(tWS250)); } else { tMNulls++; }
			if (tWS275 != 0.001) { jStationData.put("WS275", df.format(tWS275)); } else { tMNulls++; }
			if (tWS300 != 0.001) { jStationData.put("WS300", df.format(tWS300)); } else { tMNulls++; }
			if (tWS325 != 0.001) { jStationData.put("WS325", df.format(tWS325)); } else { tMNulls++; }
			if (tWS350 != 0.001) { jStationData.put("WS350", df.format(tWS350)); } else { tMNulls++; }
			if (tWS375 != 0.001) { jStationData.put("WS375", df.format(tWS375)); } else { tMNulls++; }
			if (tWS400 != 0.001) { jStationData.put("WS400", df.format(tWS400)); } else { tMNulls++; }
			if (tWS425 != 0.001) { jStationData.put("WS425", df.format(tWS425)); } else { tMNulls++; }
			if (tWS450 != 0.001) { jStationData.put("WS450", df.format(tWS450)); } else { tMNulls++; }
			if (tWS475 != 0.001) { jStationData.put("WS475", df.format(tWS475)); } else { tMNulls++; }
			if (tWS500 != 0.001) { jStationData.put("WS500", df.format(tWS500)); } else { tMNulls++; }
			if (tWS525 != 0.001) { jStationData.put("WS525", df.format(tWS525)); } else { tMNulls++; }
			if (tWS550 != 0.001) { jStationData.put("WS550", df.format(tWS550)); } else { tMNulls++; }
			if (tWS575 != 0.001) { jStationData.put("WS575", df.format(tWS575)); } else { tMNulls++; }
			if (tWS600 != 0.001) { jStationData.put("WS600", df.format(tWS600)); } else { tMNulls++; }
			if (tWS625 != 0.001) { jStationData.put("WS625", df.format(tWS625)); } else { tMNulls++; }
			if (tWS650 != 0.001) { jStationData.put("WS650", df.format(tWS650)); } else { tMNulls++; }
			if (tWS675 != 0.001) { jStationData.put("WS675", df.format(tWS675)); } else { tMNulls++; }
			if (tWS700 != 0.001) { jStationData.put("WS700", df.format(tWS700)); } else { tMNulls++; }
			if (tWS725 != 0.001) { jStationData.put("WS725", df.format(tWS725)); } else { tMNulls++; }
			if (tWS750 != 0.001) { jStationData.put("WS750", df.format(tWS750)); } else { tMNulls++; }
			if (tWS775 != 0.001) { jStationData.put("WS775", df.format(tWS775)); } else { tMNulls++; }
			if (tWS800 != 0.001) { jStationData.put("WS800", df.format(tWS800)); } else { tMNulls++; }
			if (tWS825 != 0.001) { jStationData.put("WS825", df.format(tWS825)); } else { tMNulls++; }
			if (tWS850 != 0.001) { jStationData.put("WS850", df.format(tWS850)); } else { tMNulls++; }
			if (tWS875 != 0.001) { jStationData.put("WS875", df.format(tWS875)); } else { tMNulls++; }
			if (tWS900 != 0.001) { jStationData.put("WS900", df.format(tWS900)); } else { tMNulls++; }
			if (tWS925 != 0.001) { jStationData.put("WS925", df.format(tWS925)); } else { tMNulls++; }
			if (tWS950 != 0.001) { jStationData.put("WS950", df.format(tWS950)); } else { tMNulls++; }
			if (tWS975 != 0.001) { jStationData.put("WS975", df.format(tWS975)); } else { tMNulls++; }
			if (tWS1000 != 0.001) { jStationData.put("WS1000", df.format(tWS1000)); } else { tMNulls++; }
			if (tWS0 != 0.001) { jStationData.put("WS0", df.format(tWS0)); } else { tMNulls++; }
			
			if (tTD100 != 0.001) { jStationData.put("D100", df.format(tTD100)); } else { tMNulls++; }
			if (tTD125 != 0.001) { jStationData.put("D125", df.format(tTD125)); } else { tMNulls++; }
			if (tTD150 != 0.001) { jStationData.put("D150", df.format(tTD150)); } else { tMNulls++; }
			if (tTD175 != 0.001) { jStationData.put("D175", df.format(tTD175)); } else { tMNulls++; }
			if (tTD200 != 0.001) { jStationData.put("D200", df.format(tTD200)); } else { tMNulls++; }
			if (tTD225 != 0.001) { jStationData.put("D225", df.format(tTD225)); } else { tMNulls++; }
			if (tTD250 != 0.001) { jStationData.put("D250", df.format(tTD250)); } else { tMNulls++; }
			if (tTD275 != 0.001) { jStationData.put("D275", df.format(tTD275)); } else { tMNulls++; }
			if (tTD300 != 0.001) { jStationData.put("D300", df.format(tTD300)); } else { tMNulls++; }
			if (tTD325 != 0.001) { jStationData.put("D325", df.format(tTD325)); } else { tMNulls++; }
			if (tTD350 != 0.001) { jStationData.put("D350", df.format(tTD350)); } else { tMNulls++; }
			if (tTD375 != 0.001) { jStationData.put("D375", df.format(tTD375)); } else { tMNulls++; }
			if (tTD400 != 0.001) { jStationData.put("D400", df.format(tTD400)); } else { tMNulls++; }
			if (tTD425 != 0.001) { jStationData.put("D425", df.format(tTD425)); } else { tMNulls++; }
			if (tTD450 != 0.001) { jStationData.put("D450", df.format(tTD450)); } else { tMNulls++; }
			if (tTD475 != 0.001) { jStationData.put("D475", df.format(tTD475)); } else { tMNulls++; }
			if (tTD500 != 0.001) { jStationData.put("D500", df.format(tTD500)); } else { tMNulls++; }
			if (tTD525 != 0.001) { jStationData.put("D525", df.format(tTD525)); } else { tMNulls++; }
			if (tTD550 != 0.001) { jStationData.put("D550", df.format(tTD550)); } else { tMNulls++; }
			if (tTD575 != 0.001) { jStationData.put("D575", df.format(tTD575)); } else { tMNulls++; }
			if (tTD600 != 0.001) { jStationData.put("D600", df.format(tTD600)); } else { tMNulls++; }
			if (tTD625 != 0.001) { jStationData.put("D625", df.format(tTD625)); } else { tMNulls++; }
			if (tTD650 != 0.001) { jStationData.put("D650", df.format(tTD650)); } else { tMNulls++; }
			if (tTD675 != 0.001) { jStationData.put("D675", df.format(tTD675)); } else { tMNulls++; }
			if (tTD700 != 0.001) { jStationData.put("D700", df.format(tTD700)); } else { tMNulls++; }
			if (tTD725 != 0.001) { jStationData.put("D725", df.format(tTD725)); } else { tMNulls++; }
			if (tTD750 != 0.001) { jStationData.put("D750", df.format(tTD750)); } else { tMNulls++; }
			if (tTD775 != 0.001) { jStationData.put("D775", df.format(tTD775)); } else { tMNulls++; }
			if (tTD800 != 0.001) { jStationData.put("D800", df.format(tTD800)); } else { tMNulls++; }
			if (tTD825 != 0.001) { jStationData.put("D825", df.format(tTD825)); } else { tMNulls++; }
			if (tTD850 != 0.001) { jStationData.put("D850", df.format(tTD850)); } else { tMNulls++; }
			if (tTD875 != 0.001) { jStationData.put("D875", df.format(tTD875)); } else { tMNulls++; }
			if (tTD900 != 0.001) { jStationData.put("D900", df.format(tTD900)); } else { tMNulls++; }
			if (tTD925 != 0.001) { jStationData.put("D925", df.format(tTD925)); } else { tMNulls++; }
			if (tTD950 != 0.001) { jStationData.put("D950", df.format(tTD950)); } else { tMNulls++; }
			if (tTD975 != 0.001) { jStationData.put("D975", df.format(tTD975)); } else { tMNulls++; }
			if (tTD1000 != 0.001) { jStationData.put("D1000", df.format(tTD1000)); } else { tMNulls++; }
			if (tTD0 != 0.001) { jStationData.put("D0", df.format(tTD0)); } else { tMNulls++; }
			
			if (tTC100 != 0.001) { jStationData.put("T100", df.format(tTC100)); } else { tMNulls++; }
			if (tTC125 != 0.001) { jStationData.put("T125", df.format(tTC125)); } else { tMNulls++; }
			if (tTC150 != 0.001) { jStationData.put("T150", df.format(tTC150)); } else { tMNulls++; }
			if (tTC175 != 0.001) { jStationData.put("T175", df.format(tTC175)); } else { tMNulls++; }
			if (tTC200 != 0.001) { jStationData.put("T200", df.format(tTC200)); } else { tMNulls++; }
			if (tTC225 != 0.001) { jStationData.put("T225", df.format(tTC225)); } else { tMNulls++; }
			if (tTC250 != 0.001) { jStationData.put("T250", df.format(tTC250)); } else { tMNulls++; }
			if (tTC275 != 0.001) { jStationData.put("T275", df.format(tTC275)); } else { tMNulls++; }
			if (tTC300 != 0.001) { jStationData.put("T300", df.format(tTC300)); } else { tMNulls++; }
			if (tTC325 != 0.001) { jStationData.put("T325", df.format(tTC325)); } else { tMNulls++; }
			if (tTC350 != 0.001) { jStationData.put("T350", df.format(tTC350)); } else { tMNulls++; }
			if (tTC375 != 0.001) { jStationData.put("T375", df.format(tTC375)); } else { tMNulls++; }
			if (tTC400 != 0.001) { jStationData.put("T400", df.format(tTC400)); } else { tMNulls++; }
			if (tTC425 != 0.001) { jStationData.put("T425", df.format(tTC425)); } else { tMNulls++; }
			if (tTC450 != 0.001) { jStationData.put("T450", df.format(tTC450)); } else { tMNulls++; }
			if (tTC475 != 0.001) { jStationData.put("T475", df.format(tTC475)); } else { tMNulls++; }
			if (tTC500 != 0.001) { jStationData.put("T500", df.format(tTC500)); } else { tMNulls++; }
			if (tTC525 != 0.001) { jStationData.put("T525", df.format(tTC525)); } else { tMNulls++; }
			if (tTC550 != 0.001) { jStationData.put("T550", df.format(tTC550)); } else { tMNulls++; }
			if (tTC575 != 0.001) { jStationData.put("T575", df.format(tTC575)); } else { tMNulls++; }
			if (tTC600 != 0.001) { jStationData.put("T600", df.format(tTC600)); } else { tMNulls++; }
			if (tTC625 != 0.001) { jStationData.put("T625", df.format(tTC625)); } else { tMNulls++; }
			if (tTC650 != 0.001) { jStationData.put("T650", df.format(tTC650)); } else { tMNulls++; }
			if (tTC675 != 0.001) { jStationData.put("T675", df.format(tTC675)); } else { tMNulls++; }
			if (tTC700 != 0.001) { jStationData.put("T700", df.format(tTC700)); } else { tMNulls++; }
			if (tTC725 != 0.001) { jStationData.put("T725", df.format(tTC725)); } else { tMNulls++; }
			if (tTC750 != 0.001) { jStationData.put("T750", df.format(tTC750)); } else { tMNulls++; }
			if (tTC775 != 0.001) { jStationData.put("T775", df.format(tTC775)); } else { tMNulls++; }
			if (tTC800 != 0.001) { jStationData.put("T800", df.format(tTC800)); } else { tMNulls++; }
			if (tTC825 != 0.001) { jStationData.put("T825", df.format(tTC825)); } else { tMNulls++; }
			if (tTC850 != 0.001) { jStationData.put("T850", df.format(tTC850)); } else { tMNulls++; }
			if (tTC875 != 0.001) { jStationData.put("T875", df.format(tTC875)); } else { tMNulls++; }
			if (tTC900 != 0.001) { jStationData.put("T900", df.format(tTC900)); } else { tMNulls++; }
			if (tTC925 != 0.001) { jStationData.put("T925", df.format(tTC925)); } else { tMNulls++; }
			if (tTC950 != 0.001) { jStationData.put("T950", df.format(tTC950)); } else { tMNulls++; }
			if (tTC975 != 0.001) { jStationData.put("T975", df.format(tTC975)); } else { tMNulls++; }
			if (tTC1000 != 0.001) { jStationData.put("T1000", df.format(tTC1000)); } else { tMNulls++; }
			if (tTC0 != 0.001) { jStationData.put("T0", df.format(tTC0)); } else { tMNulls++; }
			

			if (tCAPE != 0.001) { jStationData.put("CAPE", df.format(tCAPE)); } else { tMNulls++; }
			if (tCIN != 0.001) { jStationData.put("CIN", df.format(tCIN)); } else { tMNulls++; }
			if (tLI != 0.001) { jStationData.put("LI", df.format(tLI)); } else { tMNulls++; }
			if (tHGT500 != 0.001) { jStationData.put("HGT500", df.format(tHGT500)); } else { tMNulls++; }
			if (tFZLV != 0.001) { jStationData.put("FZLV", df.format(tFZLV)); } else { tMNulls++; }
			if (tWZLV != 0.001) { jStationData.put("WZLV", df.format(tWZLV)); } else { tMNulls++; }
			if (tSLCL != 0.001) { jStationData.put("SLCL", df.format(tSLCL)); } else { tMNulls++; }
			
			if (tNulls != tVars) {
				String thisJSONstring = jStationObj.toString().substring(1);
				thisJSONstring = thisJSONstring.substring(0, thisJSONstring.length()-1)+",";
				try { StumpJunk.varToFile(thisJSONstring, jsonOutFile, true); } catch (FileNotFoundException fnf) { fnf.printStackTrace(); }
				System.out.println(" -> Completed: "+thisStation+" ("+stationType+")");
				if (tMNulls == tMVars) { System.out.println("!!! WARN: NO MODEL DATA FOR Station "+thisStation+" !"); }
			} else {
				System.out.println("!!! WARN: NO DATA FOR Station "+thisStation+" !");
				String thisBadStation = thisStation+", ";
				try { StumpJunk.varToFile(thisBadStation, badStationFile, true); } catch (FileNotFoundException fnf) { fnf.printStackTrace(); }
			}

			iterk++;
			xmlOut.delete();

		}
		
	}

}
