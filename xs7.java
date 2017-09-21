/* XML Stations v7 Core Process 
Project conceived 2016-09-04
Project updated 2017-09-21 */

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

import jASUtils.StumpJunk;
import jASUtils.xsImageOp;
import jASUtils.xsMETARAutoAdd;
import jASUtils.xsWorkerFull;
import jASUtils.xsWorkerBasic;
import jASUtils.xsWorkerMETAR;
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
		List<String> gVars = new ArrayList<String>();
		List<String> gVarsH = new ArrayList<String>();
		List<String> gVarsL = new ArrayList<String>();

		/* URLs! */
		final String hrrrGrib2URL = "http://www.ftp.ncep.noaa.gov/data/nccf/com/hrrr/prod/hrrr."+getDate+"/hrrr.t"+getHour+"z.wrfprsf"+tFHour2D+".grib2";
		final String metarsURL = "http://aviationweather.gov/adds/dataserver_current/current/metars.cache.xml.gz";
		final String rapGrib2URL = "ftp://tgftp.nws.noaa.gov/SL.us008001/ST.opnl/MT.rap_CY."+getHour+"/RD."+getDate+"/PT.grid_DF.gr2/fh."+tFHour4D+"_tl.press_gr.us13km";
		final String xmlObsURL = "http://w1.weather.gov/xml/current_obs/all_xml.zip";

		System.out.println(" -> DEBUG: (String) getHour = "+getHour);
		System.out.println(" -> DEBUG: (String) getDate = "+getDate);

		StumpJunk.deleteDir(xsTmpObj);
		xsTmpObj.mkdirs();
		gradsOutObj.mkdirs();
		wwwOutObj.mkdirs();

		Thread c1a = new Thread(new Runnable() { public void run() { StumpJunk.jsoupOutBinary(xmlObsURL, nwsObsXMLzipFile, 15.0); }});
		Thread c1b = new Thread(new Runnable() { public void run() { StumpJunk.runProcess("wget -O \""+rapGrib2File.getPath()+"\" \""+rapGrib2URL+"\""); }});
		Thread c1c = new Thread(new Runnable() { public void run() { StumpJunk.jsoupOutBinary(hrrrGrib2URL, hrrrGrib2File, 15.0); }});
		Thread c1d = new Thread(new Runnable() { public void run() { StumpJunk.jsoupOutBinary(metarsURL, metarsZipFile, 15.0); }});
		Thread cList1[] = { c1a, c1b, c1c, c1d };
		for (Thread thread : cList1) { thread.start(); }
		for (int i = 0; i < cList1.length; i++) { try { cList1[i].join(); } catch (InterruptedException nx) { nx.printStackTrace(); } }

		Thread c2a = new Thread(new Runnable() { public void run() { StumpJunk.unzipFile(nwsObsXMLzipFile.getPath(), xsTmp); }});
		Thread c2b = new Thread(new Runnable() { public void run() { StumpJunk.runProcess("gunzip \""+metarsZipFile.getPath()+"\""); }});
		Thread cList2[] = { c2a, c2b };
		for (Thread thread : cList2) { thread.start(); }
		for (int i = 0; i < cList2.length; i++) { try { cList2[i].join(); } catch (InterruptedException nx) { nx.printStackTrace(); } }
 
		final String[] addMETARStationArgs = { xsTmp };
		xsMETARAutoAdd.main(addMETARStationArgs);

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

		Thread c3a = new Thread(new Runnable() { public void run() { StumpJunk.runProcess("(\""+appPath+"/g2ctl\" "+rapGrib2File.getPath()+" > "+rapCtlFile.getPath()+" &>> "+logFile.getPath()+");"); }});
		Thread c3b = new Thread(new Runnable() { public void run() { StumpJunk.runProcess("(\""+appPath+"/g2ctl\" "+hrrrGrib2File.getPath()+" > "+hrrrCtlFile.getPath()+")"); }});
		Thread cList3[] = { c3a, c3b };
		for (Thread thread : cList3) { thread.start(); }
		for (int i = 0; i < cList3.length; i++) { try { cList3[i].join(); } catch (InterruptedException nx) { nx.printStackTrace(); } }

		Thread c4a = new Thread(new Runnable() { public void run() { StumpJunk.runProcess("\""+appPath+"/gribmap\" -v -i "+hrrrCtlFile.getPath()); }});
		Thread c4b = new Thread(new Runnable() { public void run() { StumpJunk.runProcess("\""+appPath+"/gribmap\" -v -i "+rapCtlFile.getPath()); }});
		Thread cList4[] = { c4a, c4b };
		for (Thread thread : cList4) { thread.start(); }
		for (int i = 0; i < cList4.length; i++) { try { cList4[i].join(); } catch (InterruptedException nx) { nx.printStackTrace(); } }

		StumpJunk.runProcess("(echo \"run "+helpers.getPath()+"/xsGraphics.gs "+getDate+" "+getHour+" "+gradsOutObj.getPath()+"\" | \""+appPath+"/grads\" -blc \"open "+xsTmp+"/grib2/HRRR.ctl\" &>> "+logFile.getPath()+")");
		for (String gVar : gVarsH) { StumpJunk.runProcess("convert \""+gradsOutObj.getPath()+"/"+gVar+"/"+getDate+"_"+getHour+"_"+gVar+".png\" -gravity Center -crop "+resHigh+"+0+0 "+gradsOutObj.getPath()+"/"+gVar+"/"+getDate+"_"+getHour+"_"+gVar+".png"); }
		for (String gVar : gVarsL) { StumpJunk.runProcess("convert \""+gradsOutObj.getPath()+"/"+gVar+"/"+getDate+"_"+getHour+"_"+gVar+".png\" -gravity Center -crop "+resLow+"+0+0 "+gradsOutObj.getPath()+"/"+gVar+"/"+getDate+"_"+getHour+"_"+gVar+".png"); }
		StumpJunk.runProcess("cp -Rv "+gradsOutObj.getPath()+"/* "+wwwOutObj.getPath());

		final String[] xsImageOpArgs = { xsTmp }; xsImageOp.main(xsImageOpArgs);

		final String[] xfWorker1Args = { xsTmp, "USC" }; 
		final String[] xfWorker2Args = { xsTmp, "USE" }; 
		final String[] xfWorker3Args = { xsTmp, "USW" };

		final String[] xbWorkerArgs = { xsTmp, "None" };

		final String[] xmWorker1Args = { xsTmp, "US" }; 
		final String[] xmWorker2Args = { xsTmp, "CA" }; 
		final String[] xmWorker3Args = { xsTmp, "ME" }; 
		final String[] xmWorker4Args = { xsTmp, "AUS" };
		final String[] xmWorker5Args = { xsTmp, "SAM" }; 
		final String[] xmWorker6Args = { xsTmp, "INC" }; 
		final String[] xmWorker7Args = { xsTmp, "RUC" }; 
		final String[] xmWorker8Args = { xsTmp, "EUC" };
		final String[] xmWorker9Args = { xsTmp, "EUE" }; 
		final String[] xmWorker10Args = { xsTmp, "EUW" }; 
		final String[] xmWorker11Args = { xsTmp, "AFR" };
		final String[] xmWorker12Args = { xsTmp, "AUT" };

		final String[] xwbWorkerArgs = { xsTmp, "None" };
		final String[] xwhWorkerArgs = { xsTmp, "None" };
		
		final String[] xwuWorkerArgs = { xsTmp, "None" };

		Thread xs01 = new Thread(new Runnable() { public void run() { xsWorkerFull.main(xfWorker1Args); }});
		Thread xs02 = new Thread(new Runnable() { public void run() { xsWorkerFull.main(xfWorker2Args); }});
		Thread xs03 = new Thread(new Runnable() { public void run() { xsWorkerFull.main(xfWorker3Args); }});
		Thread xs04 = new Thread(new Runnable() { public void run() { xsWorkerBasic.main(xbWorkerArgs); }});
		Thread xs05 = new Thread(new Runnable() { public void run() { xsWorkerMETAR.main(xmWorker1Args); }});
		Thread xs06 = new Thread(new Runnable() { public void run() { xsWorkerMETAR.main(xmWorker2Args); }});
		Thread xs07 = new Thread(new Runnable() { public void run() { xsWorkerMETAR.main(xmWorker3Args); }});
		Thread xs08 = new Thread(new Runnable() { public void run() { xsWorkerMETAR.main(xmWorker4Args); }});
		Thread xs09 = new Thread(new Runnable() { public void run() { xsWorkerMETAR.main(xmWorker5Args); }});
		Thread xs10 = new Thread(new Runnable() { public void run() { xsWorkerMETAR.main(xmWorker6Args); }});
		Thread xs11 = new Thread(new Runnable() { public void run() { xsWorkerMETAR.main(xmWorker7Args); }});
		Thread xs12 = new Thread(new Runnable() { public void run() { xsWorkerMETAR.main(xmWorker8Args); }});
		Thread xs13 = new Thread(new Runnable() { public void run() { xsWorkerMETAR.main(xmWorker9Args); }});
		Thread xs14 = new Thread(new Runnable() { public void run() { xsWorkerMETAR.main(xmWorker10Args); }});
		Thread xs15 = new Thread(new Runnable() { public void run() { xsWorkerMETAR.main(xmWorker11Args); }});
		Thread xs16 = new Thread(new Runnable() { public void run() { xsWorkerMETAR.main(xmWorker12Args); }});
		Thread xs17 = new Thread(new Runnable() { public void run() { xsWorkerBouy.main(xwbWorkerArgs); }});
		Thread xs18 = new Thread(new Runnable() { public void run() { xsWorkerHydro.main(xwhWorkerArgs); }});
		Thread xs19 = new Thread(new Runnable() { public void run() { xsWorkerWunder.main(xwuWorkerArgs); }});
		Thread xsPool[] = { xs01, xs02, xs03, xs04, xs05, xs06, xs07, xs08, xs09, xs10, xs11, xs12, xs13, xs14, xs15, xs16, xs17, xs18 }; 
		for (Thread thread : xsPool) { thread.start(); }
		for (int i = 0; i < xsPool.length; i++) { try { xsPool[i].join(); } catch (InterruptedException nx) { nx.printStackTrace(); } }

		String jsonBigString = null;
		try { jsonBigString = StumpJunk.runProcessOutVar("cat "+xsTmp+"/output_*.json"); } catch (IOException ix) { ix.printStackTrace(); }
		jsonBigString = ("{"+jsonBigString+"}").replace("\n","").replace(",}", "}");

		try { StumpJunk.varToFile(jsonBigString, jsonDebugDumpFile, false); } catch (FileNotFoundException fnf) { fnf.printStackTrace(); }

		String jsonBigSQLQuery = "INSERT INTO WxObs.StationDataIndexed (jsonData) VALUES ('"+jsonBigString+"');";

		try ( Connection conn4 = MyDBConnector.getMyConnection(); Statement stmt4 = conn4.createStatement();) {
			stmt4.executeUpdate(jsonBigSQLQuery);
		}
		catch (SQLException se) { se.printStackTrace(); }
		catch (Exception e) { e.printStackTrace(); }

		final long endTime = System.currentTimeMillis();
	
		long totalRunTime = (endTime - startTime)/1000;

		String xs7Runtime = "INSERT INTO WxObs.Logs VALUES (Null,"+totalRunTime+");";
		try { StumpJunk.varToFile(xs7Runtime, logFile, true); } catch (FileNotFoundException fnf) { fnf.printStackTrace(); }

		try ( Connection conn5 = MyDBConnector.getMyConnection(); Statement stmt5 = conn5.createStatement();) { stmt5.executeUpdate(xs7Runtime); }
		catch (SQLException se) { se.printStackTrace(); }
		catch (Exception e) { e.printStackTrace(); }

		System.out.println("Updates completed! Runtime: "+totalRunTime); 

	}

}
