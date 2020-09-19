package app.test.migrator.matching.util;

import java.io.StringReader;
import java.io.IOException;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

public class ImageTranslator {

    private Map<String, String> id_image = new HashMap<String, String>();

    public ImageTranslator(Map<String, String> id_image){
        this.id_image = id_image;
    }

    public String updateHierarchyAttributes(String xml, String resPath) {
        String updatedXML = null;

        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder;
        try {
            dBuilder = dbFactory.newDocumentBuilder();
            InputSource is = new InputSource(new StringReader(xml));
            Document doc = dBuilder.parse(is);
            doc.getDocumentElement().normalize();

            //update attribute value
            Boolean fileChanged = updateAttributeValue(doc, resPath);

            if(fileChanged){
                doc.getDocumentElement().normalize();
                TransformerFactory transformerFactory = TransformerFactory.newInstance();
                Transformer transformer = transformerFactory.newTransformer();
                DOMSource source = new DOMSource(doc);
                StringWriter sw = new StringWriter();
                StreamResult result = new StreamResult(sw);
                transformer.setOutputProperty(OutputKeys.INDENT, "yes");
                transformer.transform(source, result);

                updatedXML = sw.toString();
            }
        } catch (SAXException | ParserConfigurationException | IOException | TransformerException e) {
            throw new RuntimeException(e.getMessage());
        }

        return updatedXML;
    }

    @SuppressWarnings("unchecked")
    private boolean updateAttributeValue(Document doc, String resPath) throws IOException {
        boolean fileChanged = false;
        NodeList nodes = doc.getElementsByTagName("node");
        Element node = null;

        for(int i = 0; i < nodes.getLength();i++){
            node = (Element) nodes.item(i);

            String id = node.getAttribute("resource-id");
            if(!id.equals("") && id.contains("/")){
                id = id.split("/")[1];
                if(id_image.containsKey(id)){
                    String image = id_image.get(id);
                    String[] imageSplittedBySlash = image.split("/");
                    if(imageSplittedBySlash.length > 1){
                        String drawableType = imageSplittedBySlash[0];
                        String drawableName = imageSplittedBySlash[1];

                        String newText = "";
                        newText = drawableName.replaceAll("\\d","");
                        node.setAttribute("src", drawableName);
                        if(node.getAttribute("class").contains("CheckBox")){
                            System.out.println("Debug: %%%%class%%%% " + node.getAttribute("class"));
                            node.setAttribute("class", "android.widget.ImageButton");
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