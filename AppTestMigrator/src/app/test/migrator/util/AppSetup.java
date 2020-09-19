package app.test.migrator.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.commons.io.FileUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.xml.sax.SAXException;

public class AppSetup {

	public void prepareAndroidManifest(String appDirectory, String userDir, String moduleName) throws IOException, TransformerFactoryConfigurationError, IllegalArgumentException, TransformerException, ParserConfigurationException, SAXException {
		File xmlFile = new File(appDirectory + "/" + moduleName + "/src/main/AndroidManifest.xml");

		DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();					
		Document doc = dBuilder.parse(xmlFile);
		Element root = doc.getDocumentElement();

		boolean have_externalStorage_permission = false, have_internet_permission = false;
		org.w3c.dom.NodeList nList = doc.getElementsByTagName("uses-permission");

		for (int temp = 0; temp < nList.getLength(); temp++) {
			org.w3c.dom.Node nNode = nList.item(temp);
			NamedNodeMap attrs = nNode.getAttributes();
			for (int y = 0; y < attrs.getLength(); y++ ) {
				org.w3c.dom.Node attr = attrs.item(y);
				if(attr.getNodeValue().equals("android.permission.WRITE_EXTERNAL_STORAGE")){
					have_externalStorage_permission = true;
				}
				if(attr.getNodeValue().equals("android.permission.INTERNET")){
					have_internet_permission = true;
				}
				if(have_externalStorage_permission && have_internet_permission)	break;
			}
			if(have_externalStorage_permission && have_internet_permission)	break;
		}

		if(!have_externalStorage_permission){
			Element permission = doc.createElement("uses-permission");
			permission.setAttribute("android:name", "android.permission.WRITE_EXTERNAL_STORAGE");
			root.appendChild(permission);
		}	
		if(!have_internet_permission){
			Element permission = doc.createElement("uses-permission");
			permission.setAttribute("android:name", "android.permission.INTERNET");
			root.appendChild(permission);
		}

		boolean largeheap = false;
		org.w3c.dom.NodeList applicationList = doc.getElementsByTagName("application");
		for (int temp = 0; temp < applicationList.getLength(); temp++) {
			org.w3c.dom.Node nNode = applicationList.item(temp);
			if(nNode.getNodeName().equals("application")){
				NamedNodeMap attrs = nNode.getAttributes();
				for (int y = 0; y < attrs.getLength(); y++ ) {
					org.w3c.dom.Node attr = attrs.item(y);
					if(attr.getNodeName().equals("android:largeHeap") && attr.getNodeValue().equals("true")){
						largeheap = true;
						break;
					}
					if(largeheap)	break;
				}
				
				if(largeheap)	break;
				else {
					((Element)nNode).setAttribute("android:largeHeap", "true");
				}
			}
		}
		
		FileUtils.copyFileToDirectory(xmlFile, new File(userDir + "/resources/"));

		DOMSource source = new DOMSource(doc);
		TransformerFactory transformerFactory = TransformerFactory.newInstance();
		transformerFactory.newTransformer().setOutputProperty(OutputKeys.INDENT, "yes");
		transformerFactory.newTransformer().setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
		Transformer transformer = transformerFactory.newTransformer();
		StreamResult result = new StreamResult(xmlFile);
		transformer.transform(source, result);
	}

	public void cleanApp(String appDirectory, String userDir, String moduleName, File gradleFile, Boolean isSourceApp) throws IOException {
		File originalAndroidManifestFile = new File(userDir + "/resources/AndroidManifest.xml");
		if(originalAndroidManifestFile.exists()){
			FileUtils.copyFile(originalAndroidManifestFile, new File(appDirectory + "/" + moduleName + "/src/main/AndroidManifest.xml"));
			originalAndroidManifestFile.delete();
		}

		File originalBuildGradleFile = new File(userDir + "/resources/build.gradle");
		FileUtils.copyFile(originalBuildGradleFile, gradleFile);
		originalBuildGradleFile.delete();

		if(isSourceApp){
			File libsDirectory = new File(appDirectory + "/" + moduleName + "/libs");
			new File(libsDirectory + "/dagger-2.1-20150513.195741-5.jar").delete();
			new File(libsDirectory + "/dagger-compiler-2.1-20150513.195817-5.jar").delete();
			new File(libsDirectory + "/dagger-producers-2.1-20150513.195750-5.jar").delete();
			new File(libsDirectory + "/guava-18.0.jar").delete();
			if(libsDirectory.listFiles().length == 0)	Files.delete(libsDirectory.toPath());

			File settingFile = new File(appDirectory + "/settings.gradle");
			String gradleSettingsFileContent = FileUtils.readFileToString(settingFile);
			gradleSettingsFileContent = gradleSettingsFileContent.replace(",':espresso-contrib-release', ':espresso-core-release', ':espresso-idling-resource-release', ':espresso-intents-release'", "");
			gradleSettingsFileContent = gradleSettingsFileContent.replace(", ':uiautomator-v18-release'", "");
			FileUtils.writeStringToFile(settingFile, gradleSettingsFileContent);

			FileUtils.deleteDirectory(new File(appDirectory + "/espresso-contrib-release"));
			FileUtils.deleteDirectory(new File(appDirectory + "/espresso-core-release"));
			FileUtils.deleteDirectory(new File(appDirectory + "/espresso-intents-release"));
			FileUtils.deleteDirectory(new File(appDirectory + "/espresso-idling-resource-release"));
			FileUtils.deleteDirectory(new File(appDirectory + "/uiautomator-v18-release"));
		}
	}
}
