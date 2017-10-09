/* XML Stations v7 Core Process 
Project conceived 2016-09-04
Project updated 2017-10-09 */

package jASUtils;

import java.io.*;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import jASUtils.MyDBConnector;
import jASUtils.StumpJunk;
import jASUtils.xsImageOp;
import jASUtils.xsMETARAutoAdd;
import jASUtils.xsWorkerFull;
import jASUtils.xsWorkerBasic;
import jASUtils.xsWorkerMETARStream;
import jASUtils.xsWorkerBouy;
import jASUtils.xsWorkerHydro;
import jASUtils.xsWorkerWunder;

public class xs7 {

	public static void main(String args[]) {

		final long startTime = System.currentTimeMillis();

		final String xsTmp = "/dev/shm/xsTmpJ";
		final String tFHour2D = "02";
		final String tFHour4D = "0002";
		final DateTime tDateTime = new DateTime(DateTimeZone.UTC).minusHours(2);
		final DateTimeFormatter getHourFormat = DateTimeFormat.forPattern("HH");
		final DateTimeFormatter getDateFormat = DateTimeFormat.forPattern("yyyyMMdd");
		final String getHour = getHourFormat.print(tDateTime);
		final String getDate = getDateFormat.print(tDateTime);
		final File gradsOutObj = new File(xsTmp+"/grib2/iOut");
		final File helpers = new File("/dev/shm/jASUtils/helpers");
		final File hrrrCtlFile = new File(xsTmp+"/grib2/HRRR.ctl");
		final File hrrrGrib2File = new File(xsTmp+"/grib2/HRRR");
		final File jsonDebugDumpFile = new File(xsTmp+"/dbgBigString.json");
		final File jsonDebugDumpRapidFile = new File(xsTmp+"/dbgRapidString.json");
		final File logFile = new File(xsTmp+"/xs7.log");
		final File metarsZipFile = new File(xsTmp+"/metars.xml.gz");
		final File nwsObsXMLzipFile = new File(xsTmp+"/index.zip");
		final File rapCtlFile = new File(xsTmp+"/grib2/RAP.ctl");
		final File rapGrib2File = new File(xsTmp+"/grib2/RAP");
		final File wwwOutObj = new File("/var/www/G2Out/xsOut");
		final File xsTmpObj = new File(xsTmp);
		final String gVarsSQL = "SELECT gVar FROM WxObs.gradsOutType WHERE Active=1;";
		final String gVarsHSQL = "SELECT gVar FROM WxObs.gradsOutType WHERE Active=1 AND HighRes=1;";
		final String gVarsLSQL = "SELECT gVar FROM WxObs.gradsOutType WHERE Active=1 AND HighRes=0;";
		final String resHigh = "13068x6600";
		final String resLow = "2904x1440";
		final String appPath = "/usr/local/bin";
		final String[] xwuWorkerArgs = { xsTmp, "None" };
		List<String> gVars = new ArrayList<String>();
		List<String> gVarsH = new ArrayList<String>();
		List<String> gVarsL = new ArrayList<String>();
		boolean rapidRefresh = false;
		boolean onlyWunder = false;
		
		if(args.length > 0 && args[0].equals("Rapid")) { rapidRefresh = true; onlyWunder = false; }
		if(args.length > 0 && args[0].equals("Wunder")) { rapidRefresh = true; onlyWunder = true; }

		System.out.println(" -> DEBUG: (String) getHour = "+getHour);
		System.out.println(" -> DEBUG: (String) getDate = "+getDate);

		if(!onlyWunder) {
			
			/* URLs! */
			final String hrrrGrib2URL = "http://www.ftp.ncep.noaa.gov/data/nccf/com/hrrr/prod/hrrr."+getDate+"/hrrr.t"+getHour+"z.wrfprsf"+tFHour2D+".grib2";
			final String metarsURL = "http://aviationweather.gov/adds/dataserver_current/current/metars.cache.xml.gz";
			final String rapGrib2URL = "ftp://tgftp.nws.noaa.gov/SL.us008001/ST.opnl/MT.rap_CY."+getHour+"/RD."+getDate+"/PT.grid_DF.gr2/fh."+tFHour4D+"_tl.press_gr.us13km";
			final String xmlObsURL = "http://w1.weather.gov/xml/current_obs/all_xml.zip";

			if(!rapidRefresh) {
				StumpJunk.deleteDir(xsTmpObj);
				xsTmpObj.mkdirs();
			}

			gradsOutObj.mkdirs();
			wwwOutObj.mkdirs();

			if(!rapidRefresh) {
				
				Thread c1a = new Thread(new Runnable() { public void run() { StumpJunk.runProcess("wget -O \""+rapGrib2File.getPath()+"\" \""+rapGrib2URL+"\""); }});
				Thread c1b = new Thread(new Runnable() { public void run() { StumpJunk.jsoupOutBinary(hrrrGrib2URL, hrrrGrib2File, 15.0); }});
				Thread cList1[] = { c1a, c1b };
				for (Thread thread : cList1) { thread.start(); }
				for (int i = 0; i < cList1.length; i++) { try { cList1[i].join(); } catch (InterruptedException nx) { nx.printStackTrace(); } }
				
				try (
					Connection conn1 = MyDBConnector.getMyConnection(); Statement stmt1 = conn1.createStatement();
					ResultSet resultSetGVars = stmt1.executeQuery(gVarsSQL);
				) {
					while (resultSetGVars.next()) { gVars.add(resultSetGVars.getString("gVar")); }
				}
				catch (Exception e) { e.printStackTrace(); }

				try (
					Connection conn2 = MyDBConnector.getMyConnection(); Statement stmt2 = conn2.createStatement();
					ResultSet resultSetGVarsH = stmt2.executeQuery(gVarsHSQL);
				) {
					while (resultSetGVarsH.next()) { gVarsH.add(resultSetGVarsH.getString("gVar")); }
				}
				catch (Exception e) { e.printStackTrace(); }

				try (
					Connection conn3 = MyDBConnector.getMyConnection(); Statement stmt3 = conn3.createStatement();
					ResultSet resultSetGVarsL = stmt3.executeQuery(gVarsLSQL);
				) {
					while (resultSetGVarsL.next()) { gVarsL.add(resultSetGVarsL.getString("gVar")); }
				}
				catch (Exception e) { e.printStackTrace(); }

				for (String thisGVar : gVars) {
					File thisGVarPath = new File(gradsOutObj.getPath()+"/"+thisGVar);
					File thisGVarWPath = new File(wwwOutObj.getPath()+"/"+thisGVar);
					thisGVarPath.mkdirs();
					thisGVarWPath.mkdirs();
				}
				
				StumpJunk.runProcess("(\""+appPath+"/wgrib2\" "+xsTmp+"/grib2/HRRR -pdt | egrep -v \"^600:\" | \""+appPath+"/wgrib2\" -i "+xsTmp+"/grib2/HRRR -grib "+xsTmp+"/grib2/HRRR)");

				Thread c2a = new Thread(new Runnable() { public void run() { StumpJunk.runProcess("(\""+appPath+"/g2ctl\" "+rapGrib2File.getPath()+" > "+rapCtlFile.getPath()+" &>> "+logFile.getPath()+");"); }});
				Thread c2b = new Thread(new Runnable() { public void run() { StumpJunk.runProcess("(\""+appPath+"/g2ctl\" "+hrrrGrib2File.getPath()+" > "+hrrrCtlFile.getPath()+")"); }});
				Thread cList2[] = { c2a, c2b };
				for (Thread thread : cList2) { thread.start(); }
				for (int i = 0; i < cList2.length; i++) { try { cList2[i].join(); } catch (InterruptedException nx) { nx.printStackTrace(); } }

				Thread c3a = new Thread(new Runnable() { public void run() { StumpJunk.runProcess("\""+appPath+"/gribmap\" -v -i "+hrrrCtlFile.getPath()); }});
				Thread c3b = new Thread(new Runnable() { public void run() { StumpJunk.runProcess("\""+appPath+"/gribmap\" -v -i "+rapCtlFile.getPath()); }});
				Thread cList3[] = { c3a, c3b };
				for (Thread thread : cList3) { thread.start(); }
				for (int i = 0; i < cList3.length; i++) { try { cList3[i].join(); } catch (InterruptedException nx) { nx.printStackTrace(); } }

				StumpJunk.runProcess("(echo \"run "+helpers.getPath()+"/xsGraphics.gs "+getDate+" "+getHour+" "+gradsOutObj.getPath()+"\" | \""+appPath+"/grads\" -blc \"open "+xsTmp+"/grib2/HRRR.ctl\" &>> "+logFile.getPath()+")");
				for (String gVar : gVarsH) { StumpJunk.runProcess("convert \""+gradsOutObj.getPath()+"/"+gVar+"/"+getDate+"_"+getHour+"_"+gVar+".png\" -gravity Center -crop "+resHigh+"+0+0 "+gradsOutObj.getPath()+"/"+gVar+"/"+getDate+"_"+getHour+"_"+gVar+".png"); }
				for (String gVar : gVarsL) { StumpJunk.runProcess("convert \""+gradsOutObj.getPath()+"/"+gVar+"/"+getDate+"_"+getHour+"_"+gVar+".png\" -gravity Center -crop "+resLow+"+0+0 "+gradsOutObj.getPath()+"/"+gVar+"/"+getDate+"_"+getHour+"_"+gVar+".png"); }
				StumpJunk.runProcess("cp -Rv "+gradsOutObj.getPath()+"/* "+wwwOutObj.getPath());
				
				final String[] xsImageOpArgs = { xsTmp }; xsImageOp.main(xsImageOpArgs);

			}
			
			Thread d1a = new Thread(new Runnable() { public void run() { StumpJunk.jsoupOutBinary(xmlObsURL, nwsObsXMLzipFile, 15.0); }});
			Thread d1b = new Thread(new Runnable() { public void run() { StumpJunk.jsoupOutBinary(metarsURL, metarsZipFile, 15.0); }});
			Thread dList1[] = { d1a, d1b };
			for (Thread thread : dList1) { thread.start(); }
			for (int i = 0; i < dList1.length; i++) { try { dList1[i].join(); } catch (InterruptedException nx) { nx.printStackTrace(); } }

			Thread d2a = new Thread(new Runnable() { public void run() { StumpJunk.unzipFile(nwsObsXMLzipFile.getPath(), xsTmp); }});
			Thread d2b = new Thread(new Runnable() { public void run() { StumpJunk.runProcess("gunzip \""+metarsZipFile.getPath()+"\""); }});
			Thread dList2[] = { d2a, d2b };
			for (Thread thread : dList2) { thread.start(); }
			for (int i = 0; i < dList2.length; i++) { try { dList2[i].join(); } catch (InterruptedException nx) { nx.printStackTrace(); } }
	 
			final String[] addMETARStationArgs = { xsTmp };
			xsMETARAutoAdd.main(addMETARStationArgs);

			final String[] xfWorker1Args = { xsTmp, "USC" }; 
			final String[] xfWorker2Args = { xsTmp, "USE" }; 
			final String[] xfWorker3Args = { xsTmp, "USW" };
			final String[] xbWorkerArgs = { xsTmp, "None" };
			final String[] xmWorkerSArgs = { xsTmp, "None" }; 
			final String[] xwbWorkerArgs = { xsTmp, "None" };
			final String[] xwhWorkerArgs = { xsTmp, "None" };

			Thread xs1 = new Thread(new Runnable() { public void run() { xsWorkerFull.main(xfWorker1Args); }});
			Thread xs2 = new Thread(new Runnable() { public void run() { xsWorkerFull.main(xfWorker2Args); }});
			Thread xs3 = new Thread(new Runnable() { public void run() { xsWorkerFull.main(xfWorker3Args); }});
			Thread xs4 = new Thread(new Runnable() { public void run() { xsWorkerBasic.main(xbWorkerArgs); }});
			Thread xs5 = new Thread(new Runnable() { public void run() { xsWorkerMETARStream.main(xmWorkerSArgs); }});
			Thread xs6 = new Thread(new Runnable() { public void run() { xsWorkerBouy.main(xwbWorkerArgs); }});
			Thread xs7 = new Thread(new Runnable() { public void run() { xsWorkerHydro.main(xwhWorkerArgs); }});
			Thread xs8 = new Thread(new Runnable() { public void run() { xsWorkerWunder.main(xwuWorkerArgs); }});
			Thread xsPool[] = { xs1, xs2, xs3, xs4, xs5, xs6, xs7, xs8 }; 
			for (Thread thread : xsPool) { thread.start(); }
			for (int i = 0; i < xsPool.length; i++) { try { xsPool[i].join(); } catch (InterruptedException nx) { nx.printStackTrace(); } }

			String jsonBigString = null;
			try { jsonBigString = StumpJunk.runProcessOutVar("cat "+xsTmp+"/output_*.json"); } catch (IOException ix) { ix.printStackTrace(); }
			jsonBigString = ("{"+jsonBigString+"}").replace("\n","").replace(",}", "}");
			try { StumpJunk.varToFile(jsonBigString, jsonDebugDumpFile, false); } catch (FileNotFoundException fnf) { fnf.printStackTrace(); }
			String jsonBigSQLQuery = "INSERT INTO WxObs.StationDataIndexed (jsonData) VALUES ('"+jsonBigString+"');";
			try ( Connection conn4 = MyDBConnector.getMyConnection(); Statement stmt4 = conn4.createStatement();) { stmt4.executeUpdate(jsonBigSQLQuery); }
			catch (SQLException se) { se.printStackTrace(); }
			catch (Exception e) { e.printStackTrace(); }
		
		} else {
			
			xsWorkerWunder.main(xwuWorkerArgs);
			
		}
		
		String jsonRapidString = null;
		try { jsonRapidString = StumpJunk.runProcessOutVar("cat "+xsTmp+"/rapid_*.json"); } catch (IOException ix) { ix.printStackTrace(); }
		jsonRapidString = ("{"+jsonRapidString+"}").replace("\n","").replace(",}", "}");
		try { StumpJunk.varToFile(jsonRapidString, jsonDebugDumpRapidFile, false); } catch (FileNotFoundException fnf) { fnf.printStackTrace(); }
		String jsonRapidSQLQuery = "INSERT INTO WxObs.RapidSDI (jsonData) VALUES ('"+jsonRapidString+"');";
		try ( Connection conn5 = MyDBConnector.getMyConnection(); Statement stmt5 = conn5.createStatement();) { stmt5.executeUpdate(jsonRapidSQLQuery); }
		catch (SQLException se) { se.printStackTrace(); }
		catch (Exception e) { e.printStackTrace(); }

		final long endTime = System.currentTimeMillis();
	
		long totalRunTime = (endTime - startTime)/1000;

		String xs7Runtime = "INSERT INTO WxObs.Logs VALUES (Null,"+totalRunTime+");";
		try { StumpJunk.varToFile(xs7Runtime, logFile, true); } catch (FileNotFoundException fnf) { fnf.printStackTrace(); }
		try ( Connection conn6 = MyDBConnector.getMyConnection(); Statement stmt6 = conn6.createStatement();) { stmt6.executeUpdate(xs7Runtime); }
		catch (SQLException se) { se.printStackTrace(); }
		catch (Exception e) { e.printStackTrace(); }

		System.out.println("Updates completed! Runtime: "+totalRunTime); 
		
	}

}
