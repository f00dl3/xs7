package jASUtils;

import java.io.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.jsoup.*;

public class StumpJunk {

	public static void copyFile(String sourceFile, String destFile) throws IOException {
		try { 
			Files.copy(Paths.get(sourceFile),
				Paths.get(destFile),
				StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException ix) { ix.printStackTrace(); }
	}

	public static String fileScanner(String path, Charset encoding) throws IOException {
		byte[] encoded = Files.readAllBytes(Paths.get(path));
		return new String(encoded, encoding);
	}

	public static List<String> fileSorter(Path folderPath, String objectsToSort) {	
		List<String> sorterList = new ArrayList<>();
		try {
			DirectoryStream<Path> stream = Files.newDirectoryStream(folderPath, objectsToSort);
			for (Path path : stream) { sorterList.add(path.toString()); }
		}
		catch (IOException ix) { ix.printStackTrace(); }
		Collections.sort(sorterList);
		return sorterList;
	}

	public static boolean isSet(String tStr) {
		if (tStr != null && !tStr.isEmpty()) {
			return true;
		} else { return false; }
	}

	public static void jsoupOutBinary(String thisUrl, File outFile, double toS) {
		int toLength = (int) (1000.0*toS);
		String stripPath = outFile.getPath();
		File cacheFile = new File(stripPath+".tmp");
		System.out.println(" --> Downloading [ "+thisUrl+" ] NO MIME AS BINARY");
		try {
			FileOutputStream out = new FileOutputStream(cacheFile);
			org.jsoup.Connection.Response binaryResult = Jsoup.connect(thisUrl)
				.ignoreContentType(true)
				.maxBodySize(1024*1024*1024*100)
				.timeout(toLength)
				.execute();
			out.write(binaryResult.bodyAsBytes());
			out.close();
		}
		catch (Exception e) { e.printStackTrace(); }
		if (cacheFile.length() > 0) {
			moveFile(cacheFile.getPath(), outFile.getPath());
		} else { System.out.println("0 byte download!"); }
		cacheFile.delete();
		System.out.flush();
	}

	public static void jsoupOutFile(String thisUrl, File outFile) {
		System.out.println(" --> Downloading [ "+thisUrl+" ] NO MIME");
		PrintStream console = System.out;
		try {
			org.jsoup.Connection.Response html = Jsoup.connect(thisUrl).ignoreContentType(true).execute();
			System.setOut(new PrintStream(new FileOutputStream(outFile, false)));
			System.out.println(html.body());
		}
		catch (Exception e) { e.printStackTrace(); }
		System.out.flush();
		System.setOut(console);
	}

	public static void deleteDir(File file) {
		File[] contents = file.listFiles();
		if (contents != null) {
			for (File f : contents) { deleteDir(f); }
		}
		file.delete();
	}
	
	public static void moveFile(String oldFileName, String newFileName) {
		System.out.println(" --> Moving [ "+oldFileName+" ] to [ "+newFileName+" ]");
		Path oldFileFile = Paths.get(oldFileName);
		Path newFileFile = Paths.get(newFileName);
		try { 
			Files.move(oldFileFile, newFileFile, StandardCopyOption.REPLACE_EXISTING);
		}
		catch (IOException io) { io.printStackTrace(); }
	}

	public static void runProcess(String pString) {
		System.out.println(" --> Running [ "+pString+" ]");
		String s = null;
		String[] pArray = { "bash", "-c", pString };
		try { 
			Process p = new ProcessBuilder(pArray).start();
			BufferedReader stdInput = new BufferedReader(new InputStreamReader(p.getInputStream()));
			BufferedReader stdError = new BufferedReader(new InputStreamReader(p.getErrorStream()));
			while ((s = stdInput.readLine()) != null) { System.out.println(s); }
			while ((s = stdError.readLine()) != null) { System.out.println(s); }
			p.destroy();
		}
		catch (IOException e) { e.printStackTrace(); }
		System.out.flush();
	}

	public static void runProcessSilently(String pString) {
		String s = null;
		String[] pArray = { "bash", "-c", pString };
		try { 
			Process p = new ProcessBuilder(pArray).start();
			BufferedReader stdInput = new BufferedReader(new InputStreamReader(p.getInputStream()));
			BufferedReader stdError = new BufferedReader(new InputStreamReader(p.getErrorStream()));
			while ((s = stdInput.readLine()) != null) { System.out.println(s); }
			while ((s = stdError.readLine()) != null) { System.out.println(s); }
			p.destroy();
		}
		catch (IOException e) { e.printStackTrace(); }
		System.out.flush();
	}

	public static void runProcessOutFile(String pString, File outFile, boolean appendFlag) throws FileNotFoundException {
		System.out.println(" --> (Output following result to file: "+outFile.getPath()+")");
		String tmpVar = null;
		try { tmpVar = runProcessOutVar(pString); } catch (IOException ix) { ix.printStackTrace(); }
		varToFile(tmpVar, outFile, appendFlag);
	}

	public static String runProcessOutVar(String pString) throws java.io.IOException {
		String[] pArray = { "bash", "-c", pString };
		Process proc = new ProcessBuilder(pArray).start();
		InputStream is = proc.getInputStream();
		Scanner co = new Scanner(is).useDelimiter("\\A");
		String val = "";
		if (co.hasNext()) { val = co.next(); } else { val = ""; }
		return val;
	}

	public static void sedFileDeleteFirstLine(String fileName) {
		try {
			File thisFileObject = new File(fileName);
			Scanner fileScanner = new Scanner(thisFileObject);
			if (fileScanner.hasNextLine()) {
				fileScanner.nextLine();
				FileWriter fileStream = new FileWriter(thisFileObject);
				BufferedWriter out = new BufferedWriter(fileStream);
				while (fileScanner.hasNextLine()) {
					String next = fileScanner.nextLine();
					if(next.equals("\n")) { out.newLine(); } else { out.write(next); }
					out.newLine();
				}
				out.close();
				fileStream.close();
			}
		} catch (IOException ix) { ix.printStackTrace(); }
	}

	public static void sedFileInsertEachLineNew(String subjectFile, String toInsert, String targetFile) {
		try {
			FileInputStream fstream = new FileInputStream(subjectFile);
			BufferedReader br = new BufferedReader(new InputStreamReader(fstream));
			BufferedWriter bw = new BufferedWriter(new FileWriter(new File(targetFile), true));
			String strLine = null;
			while ((strLine = br.readLine()) != null) {
				if (strLine.equals("")) {
					System.out.println(" --> Skipping a line...");
				} else {
					System.out.println(" --> Processing: \""+strLine+"\"");		
					String upLine = toInsert+strLine;
					bw.write(upLine);
					bw.newLine();
				}
			}
			bw.close();
			br.close();
		}
		catch (FileNotFoundException fnf) { fnf.printStackTrace(); }
		catch (IOException iox) { iox.printStackTrace(); }
		System.out.flush();
	}

	public static void sedFileReplace(String fileName, String toFind, String replaceTo) {
		Path path = Paths.get(fileName);
		Charset charset = StandardCharsets.UTF_8;
		try {		
			String content = new String(Files.readAllBytes(path), charset);
			content = content.trim().replaceAll(toFind, replaceTo);
			Files.write(path, content.getBytes(charset));
		}
		catch (IOException io) { io.printStackTrace(); }
	}

	public static void unTarGz(String tarFileStr, String destStr) {
		File tarFile = new File(tarFileStr);
		File dest = new File(destStr);
		dest.mkdirs();
		TarArchiveInputStream tarIn = null;
		try {
			tarIn = new TarArchiveInputStream(new GzipCompressorInputStream(new BufferedInputStream(new FileInputStream(tarFile))));
			TarArchiveEntry tarEntry = tarIn.getNextTarEntry();
			while (tarEntry != null) {
				File destPath = new File(dest, tarEntry.getName());
				System.out.println("Working: " + destPath.getCanonicalPath());
				if (tarEntry.isDirectory()) { destPath.mkdirs(); }
				else {
					if(!destPath.getParentFile().exists()) { destPath.getParentFile().mkdirs(); }					
					destPath.createNewFile();
					byte [] btoRead = new byte[4096];
					BufferedOutputStream bout = new BufferedOutputStream(new FileOutputStream(destPath));
					int len = 0;
					while ((len = tarIn.read(btoRead)) != -1) { bout.write(btoRead, 0, len); }
					bout.close();
					btoRead = null;
				}
				tarEntry = tarIn.getNextTarEntry();
			}
			tarIn.close();
		}
		catch (FileNotFoundException fnf) { fnf.printStackTrace(); }
		catch (IOException iox) { iox.printStackTrace(); }
	}
	
	public static void unzipFile(String zipFile, String outputFolder) {
		byte[] buffer = new byte[4096];
		try {
			File folder = new File(outputFolder);
			if(!folder.exists()) { folder.mkdirs(); }
			ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(zipFile)));
			ZipEntry ze = zis.getNextEntry();
			while(ze != null) {
				String fileName = ze.getName();
				File newFile = new File(outputFolder+File.separator+fileName);
				System.out.println("Unzip : "+newFile.getAbsoluteFile());
				new File(newFile.getParent()).mkdirs();
				FileOutputStream fos = new FileOutputStream(newFile);
				int len;
				while ((len = zis.read(buffer)) > 0) { fos.write(buffer, 0, len); }
				fos.close();
				ze = zis.getNextEntry();
			}
			zis.closeEntry();
			zis.close();
			System.out.println("Done");
		} catch (IOException ex) { ex.printStackTrace(); }
	}

	public static void varToFile(String thisVar, File outFile, boolean appendFlag) throws FileNotFoundException {
		try ( PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(outFile, appendFlag))) ) {
			out.println(thisVar);
		} catch (IOException io) { io.printStackTrace(); }
	}

}
