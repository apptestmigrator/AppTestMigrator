package app.test.migrator.matching.util.uiautomator;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

public class UiHierarchyXmlLoader {

    private UiNode mRootNode;
    //private List<Rectangle> mNafNodes;
    private List<BasicTreeNode> mNodeList;
    public UiHierarchyXmlLoader() {
    }

    /**
     * Uses a SAX parser to process XML dump
     * @param xmlPath
     * @return
     */
    public BasicTreeNode parseXml(String xmlPath) {
        mRootNode = null;
        //mNafNodes = new ArrayList<Rectangle>();
        mNodeList = new ArrayList<BasicTreeNode>();
        // standard boilerplate to get a SAX parser
        SAXParserFactory factory = SAXParserFactory.newInstance();
        SAXParser parser = null;
        try {
            parser = factory.newSAXParser();
        } catch (ParserConfigurationException e) {
            e.printStackTrace();
            return null;
        } catch (SAXException e) {
            e.printStackTrace();
            return null;
        }
        // handler class for SAX parser to receiver standard parsing events:
        // e.g. on reading "<foo>", startElement is called, on reading "</foo>",
        // endElement is called
        DefaultHandler handler = new DefaultHandler(){
            UiNode mParentNode;
            UiNode mWorkingNode;
            @Override
            public void startElement(String uri, String localName, String qName,
                                     Attributes attributes) throws SAXException {
                boolean nodeCreated = false;
                // starting an element implies that the element that has not yet been closed
                // will be the parent of the element that is being started here
                mParentNode = mWorkingNode;
                /*if ("hierarchy".equals(qName)) {
                    int rotation = 0;
                    for (int i = 0; i < attributes.getLength(); i++) {
                        if ("rotation".equals(attributes.getQName(i))) {
                            try {
                                rotation = Integer.parseInt(attributes.getValue(i));
                            } catch (NumberFormatException nfe) {
                                // do nothing
                            }
                        }
                    }
                    mWorkingNode = new RootWindowNode(attributes.getValue("windowName"), rotation);
                    nodeCreated = true;
                } else */if ("node".equals(qName)) {
                    UiNode tmpNode = new UiNode();
                    for (int i = 0; i < attributes.getLength(); i++) {
                        String name = attributes.getQName(i);
                        String value = attributes.getValue(i);
                        if (!name.equals("focused") && !name.equals("focusable"))
                            tmpNode.addAtrribute(name, value);
                    }
                    mWorkingNode = tmpNode;
                    nodeCreated = true;
                    // check if current node is NAF
                    String naf = tmpNode.getAttribute("NAF");
                    /*if ("true".equals(naf)) {
                        mNafNodes.add(new Rectangle(tmpNode.x, tmpNode.y,
                                tmpNode.width, tmpNode.height));
                    }*/
                }
                // nodeCreated will be false if the element started is neither
                // "hierarchy" nor "node"
                if (nodeCreated) {
                    if (mRootNode == null) {
                        // this will only happen once
                        mRootNode = mWorkingNode;
                    }
                    if (mParentNode != null) {
                        mParentNode.addChild(mWorkingNode);
                        mNodeList.add(mWorkingNode);
                    }
                }
            }

            @Override
            public void endElement(String uri, String localName, String qName) throws SAXException {
                //mParentNode should never be null here in a well formed XML
                if (mParentNode != null) {
                    // closing an element implies that we are back to working on
                    // the parent node of the element just closed, i.e. continue to
                    // parse more child nodes
                    mWorkingNode = mParentNode;
                    mParentNode = (UiNode)mParentNode.getParent();
                }
            }
        };
        try {
            parser.parse(new InputSource(new StringReader(xmlPath)), handler);
        } catch (SAXException e) {
            System.out.println("Debug: SAXException" + e.getMessage());
            e.printStackTrace();
            return null;
        } catch (IOException e) {
            System.out.println("Debug: IOException" + e.getMessage());
            e.printStackTrace();
            return null;
        }
        return mRootNode;
    }

    /**
     * Returns the list of "Not Accessibility Friendly" nodes found during parsing.
     *
     * Call this function after parsing
     *
     * @return
     */
   /* public List<Rectangle> getNafNodes() {
        return Collections.unmodifiableList(mNafNodes);
    }*/

    public List<BasicTreeNode> getAllNodes(){
        return mNodeList;
    }

    public void printTreeHierarchy(BasicTreeNode node, String indentation) {
        BasicTreeNode[] nodes = node.getChildren();
        indentation += " ";
        for (int index = 0; index < nodes.length; index++){
            System.out.println(indentation + nodes[index]);
            printTreeHierarchy(nodes[index], indentation);
        }
    }
}