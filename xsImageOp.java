package jASUtils;

import java.io.*;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

import jASUtils.StumpJunk;

public class xsImageOp {

	public static void main(String args[]) {

		final int iCyc = 192;
		final String xsTmp = args[0];
		final String gradsOut = xsTmp+"/grib2/iOut";
		final String gVarsSQL = "SELECT gVar FROM WxObs.gradsOutType WHERE Active=1;";
		final String gVarsHSQL = "SELECT gVar FROM WxObs.gradsOutType WHERE Active=1 AND HighRes=1;";
		final String gVarsLSQL = "SELECT gVar FROM WxObs.gradsOutType WHERE Active=1 AND HighRes=0;";
		final String wwwOut = "/var/www/G2Out";
		List<String> gVars = new ArrayList<String>();
		List<String> gVarsH = new ArrayList<String>();
		List<String> gVarsL = new ArrayList<String>();

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

		for (String gVar : gVarsL) { StumpJunk.runProcess("(ls "+wwwOut+"/xsOut/"+gVar+"/*.png -t | head -n "+iCyc+"; ls "+wwwOut+"/xsOut/"+gVar+"/*.png)|sort|uniq -u|xargs rm"); }
		for (String gVar : gVarsH) { StumpJunk.runProcess("(ls "+wwwOut+"/xsOut/"+gVar+"/*.png -t | head -n 12; ls "+wwwOut+"/xsOut/"+gVar+"/*.png)|sort|uniq -u|xargs rm"); }

		StumpJunk.runProcess("cp -R "+wwwOut+"/xsOut/* "+gradsOut+"/");

		for (String gVar : gVarsL) {
			StumpJunk.runProcess("bash /dev/shm/Sequence.sh "+gradsOut+"/"+gVar+" png");
			StumpJunk.runProcess("ffmpeg -threads 8 -r 10 -i "+gradsOut+"/"+gVar+"/%05d.png -vcodec libx264 -pix_fmt yuv420p "+xsTmp+"/_HRRRLoop_"+gVar+".mp4");
		}

		StumpJunk.runProcess("mv "+xsTmp+"/_HRRRLoop_* "+wwwOut+"/");
		StumpJunk.runProcess("chown -R www-data "+wwwOut+"/");

	}

}
