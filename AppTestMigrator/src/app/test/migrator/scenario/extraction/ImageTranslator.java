package app.test.migrator.scenario.extraction;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public class ImageTranslator {

	private HashMap<String, String> id_image = new HashMap<String, String>();
	private HashMap<String, String> id_inputType = new HashMap<String, String>();

	private String userDir;
	private String appDirectory;
	private String moduleName;
	private String hierarchy_directory_path;
	private Boolean inputType;

	public ImageTranslator(String userDir, String appDirectory, String moduleName, String hierarchy_directory_path, Boolean inputType) {
		this.userDir = userDir;
		this.appDirectory = appDirectory;
		this.moduleName = moduleName;
		this.hierarchy_directory_path = hierarchy_directory_path;
		this.inputType = inputType;
	}

	public HashMap<String, String> getIdImage()	{ return id_image; }
	public HashMap<String, String> getIdInputType()	{ return id_inputType; }

	@SuppressWarnings("unchecked")
	public void translateImages(){
		String resPath = appDirectory + "/" + moduleName + "/src/main/res";

		Collection<File> appResourceFiles = FileUtils.listFiles(new File(resPath), new String[] { "xml" }, true);		
		for(File appResourceFile : appResourceFiles){
			parseXml(appResourceFile.getAbsolutePath());
		}

		if (!inputType) {
			Collection<File> appUIHierarchiesFiles = FileUtils.listFiles(new File(hierarchy_directory_path),  new String[] { "xml" }, true);		
			for(File appUIHierarchiesFile : appUIHierarchiesFiles){
				updateHierarchyAttributes(appUIHierarchiesFile.getAbsolutePath(), resPath, appUIHierarchiesFile.getName().replace(".xml", ".png"), userDir);
			}
		}
	}

	public void parseXml(String xmlPath) {
		SAXParserFactory factory = SAXParserFactory.newInstance();
		SAXParser parser = null;
		try {
			parser = factory.newSAXParser();
		} catch (ParserConfigurationException e) {
			e.printStackTrace();
		} catch (SAXException e) {
			e.printStackTrace();
		}

		DefaultHandler handler = new DefaultHandler(){
			@Override
			public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
				for (int i = 0; i < attributes.getLength(); i++) {
					String attributeValue = attributes.getValue(i);
					String attributeLocalName = attributes.getLocalName(i);
					if (attributeLocalName.contains("src") || attributeLocalName.contains("icon")) {
						if (attributeValue.contains("drawable") || attributeValue.contains("mipmap")){
							String id = attributes.getValue("android:id");
							if (id != null && !id.equals("")) {
								id = id.replace("@+id/", "");
								String imageName = attributeValue;	
								id_image.put(id, imageName);

								//break;
							}
						}
					} else if (attributeLocalName.contains("fab_title")) {
						String id = attributes.getValue("android:id");
						if (id != null && !id.equals("")) {
							id = id.replace("@+id/", "");
							String imageName = attributeValue;	
							id_image.put(id, imageName);

							//break;
						}
					}

					if (inputType && attributeLocalName.equals("android:id")) {
						String id = attributeValue;
						if (id != null && !id.equals("")) {
							id = id.replace("@+id/", "");
							String inputType = attributes.getValue("android:inputType");
							if (id_inputType.containsKey(id) && inputType != null && !id_inputType.get(id).equals(inputType)) {
								id_inputType.remove(id);
								continue;
							}
							if (inputType != null) {
								id_inputType.put(id, inputType);
							} else id_inputType.put(id, "");
						}
					}
				}
			}
		};

		try {
			parser.parse(new File(xmlPath), handler);
		} catch (SAXException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void updateHierarchyAttributes(String xmlPath, String resPath, String xmlName, String userDir) {
		File xmlFile = new File(xmlPath);
		DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder dBuilder;
		try {
			dBuilder = dbFactory.newDocumentBuilder();
			Document doc = dBuilder.parse(xmlFile);
			doc.getDocumentElement().normalize();

			//update attribute value
			Boolean fileChanged = updateAttributeValue(doc, resPath, xmlName, userDir);

			if(fileChanged){
				//write the updated document to file or console
				doc.getDocumentElement().normalize();
				TransformerFactory transformerFactory = TransformerFactory.newInstance();
				Transformer transformer = transformerFactory.newTransformer();
				DOMSource source = new DOMSource(doc);
				StreamResult result = new StreamResult(xmlFile);
				transformer.setOutputProperty(OutputKeys.INDENT, "yes");
				transformer.transform(source, result);
			}
		} catch (SAXException | ParserConfigurationException | IOException | TransformerException e1) {
			e1.printStackTrace();
		}
	}

	@SuppressWarnings("unchecked")
	private boolean updateAttributeValue(Document doc, String resPath, String xmlName, String userDir) {
		boolean fileChanged = false;
		NodeList nodes = doc.getElementsByTagName("node");
		Element node = null;

		for(int i = 0; i < nodes.getLength();i++){
			node = (Element) nodes.item(i);

			if (node.getAttribute("content-desc") != null && !node.getAttribute("content-desc").equals(""))	continue;

			String id = node.getAttribute("resource-id");
			if(!id.equals("") && id.split("/").length > 1){
				id = id.split("/")[1];
				if(id_image.containsKey(id)){
					String image = id_image.get(id);
					String[] imageSplittedBySlash = image.split("/");
					if(imageSplittedBySlash.length > 1){
						String drawableType = imageSplittedBySlash[0];
						String drawableName = imageSplittedBySlash[1];

						String newText = "";
						if(drawableType.equals("@android:drawable") || drawableType.equals("@drawable") || drawableType.equals("@mipmap") || drawableType.equals("@string")){
							newText = drawableName.replaceAll("\\d","");
							node.setAttribute("src", drawableName);
							if(!node.getAttribute("class").contains("Image"))	node.setAttribute("class", "android.widget.ImageButton");
						} else {
							Pattern BOUNDS_PATTERN = Pattern.compile("\\[-?(\\d+),-?(\\d+)\\]\\[-?(\\d+),-?(\\d+)\\]");
							Matcher m = BOUNDS_PATTERN.matcher(node.getAttribute("bounds"));
							if (m.matches()) {
								Collection<File> appResourceFiles = FileUtils.listFiles(new File(resPath), new String[] { "png", "jpg" }, true);		
								for(File appResourceFile : appResourceFiles){
									if(FilenameUtils.removeExtension(appResourceFile.getName()).equals(drawableName)){
										String path = userDir + "/screenshots/" + xmlName;
										if(new File(path).exists()){
											node.setAttribute("src", drawableName);
										} else throw new RuntimeException("screenshot " + xmlName + " not found!");
									}
								}
							}
						}

						String textOrContentDesc = node.getAttribute("textOrContentDesc");
						if (textOrContentDesc == null || textOrContentDesc.equals(""))    textOrContentDesc = node.getAttribute("content-desc");
						if(textOrContentDesc != null && textOrContentDesc.equals("") && !newText.equals("")){
							node.setAttribute("text", newText);
						}

						fileChanged = true;
					}

				}
			}
		}

		return fileChanged;
	}

}
