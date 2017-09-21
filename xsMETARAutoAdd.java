package jASUtils;

import java.io.*;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import jASUtils.StumpJunk;

public class xsMETARAutoAdd {

	public static String getTagValue(String tag, Element eElement) throws NullPointerException {
		NodeList nl = eElement.getElementsByTagName(tag).item(0).getChildNodes();
		Node nVal = (Node) nl.item(0);
		if(nVal == null) { return null; }
		return nVal.getNodeValue();
	}

	public static void main(String args[]) {

		final String xsTmp = args[0];

		List<String> wxStations = new ArrayList<String>();
		final String getStationListSQL = "SELECT Station FROM WxObs.Stations;";
		final File xmlMetarsIn = new File(xsTmp+"/metars.xml");
		String addStationTestSQL = "INSERT IGNORE INTO WxObs.Stations (Station, Point, City, State, Active, Priority, Region, DataSource, Frequency) VALUES";

		try (
			Connection conn = MyDBConnector.getMyConnection(); Statement stmt = conn.createStatement();
			ResultSet resultSetStations = stmt.executeQuery(getStationListSQL);
		) { while (resultSetStations.next()) { wxStations.add(resultSetStations.getString("Station")); } }
		catch (Exception e) { e.printStackTrace(); }

		try {

			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			DocumentBuilder builder = factory.newDocumentBuilder();
			Document xmlDoc = builder.parse(xmlMetarsIn);
			NodeList nodeList = xmlDoc.getElementsByTagName("METAR");
	
			if (nodeList != null) {
				for(int i=0; i < nodeList.getLength(); i++) {
					Node thisMetar = nodeList.item(i);
					if(thisMetar.getNodeType() == Node.ELEMENT_NODE) {
						Element tElement = (Element) thisMetar;	
						String thisStation = tElement.getElementsByTagName("station_id").item(0).getTextContent();
						String latitude = null;
						String longitude = null;
						try { latitude = getTagValue("latitude", tElement); } catch (NullPointerException npx) { npx.printStackTrace(); }
						try { longitude = getTagValue("longitude", tElement); } catch (NullPointerException npx) { npx.printStackTrace(); }
						if(!wxStations.contains(thisStation) && latitude != null && longitude != null) {
							addStationTestSQL = addStationTestSQL+" ('"+thisStation+"','["+longitude+","+latitude+"]','Auto METAR Add',Null,1,5,'AUTO','NWS',60),";
						}
					}
				}
			}

		}

		catch (SAXException sex) { sex.printStackTrace(); }
		catch (ParserConfigurationException pcx) { pcx.printStackTrace(); }
		catch (IOException iox) { iox.printStackTrace(); }

		addStationTestSQL = (addStationTestSQL+";").replace(",;",";");

		System.out.println(addStationTestSQL);

		try ( Connection conn = MyDBConnector.getMyConnection(); Statement stmt = conn.createStatement();) { stmt.executeUpdate(addStationTestSQL); }
		catch (SQLException se) { se.printStackTrace(); }
		catch (Exception e) { e.printStackTrace(); }
		
	}

}
