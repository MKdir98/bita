/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ir.bita.esb.ws;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringReader;
import java.io.StringWriter;
import org.w3c.dom.*;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class WsdlTool {

    private final static Map<String, String> attributeMap = new HashMap<>();

    static {
        attributeMap.put("xmlns:xsd", "http://www.w3.org/2001/XMLSchema");
        attributeMap.put("xmlns:soap12", "http://schemas.xmlsoap.org/wsdl/soap12/");
        attributeMap.put("xmlns:xs", "http://www.w3.org/2001/XMLSchema");
        attributeMap.put("xmlns:ns1", "http://schemas.xmlsoap.org/soap/http");
        attributeMap.put("xmlns:soapenc", "http://schemas.xmlsoap.org/soap/encoding/");
        attributeMap.put("xmlns:wsa10", "http://www.w3.org/2005/08/addressing");
        attributeMap.put("xmlns:wsp", "http://schemas.xmlsoap.org/ws/2004/09/policy");
        attributeMap.put("xmlns:wsu", "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd");
        attributeMap.put("xmlns:wsap", "http://schemas.xmlsoap.org/ws/2004/08/addressing/policy");
        attributeMap.put("xmlns:wsa", "http://schemas.xmlsoap.org/ws/2004/08/addressing");
        attributeMap.put("xmlns:wsam", "http://www.w3.org/2007/05/addressing/metadata");
        attributeMap.put("xmlns:wsaw", "http://www.w3.org/2006/05/addressing/wsdl");
        attributeMap.put("xmlns:http", "http://schemas.xmlsoap.org/wsdl/http/");
        attributeMap.put("xmlns:wsdl", "http://schemas.xmlsoap.org/wsdl/");
    }

    private static Element createWspPolicyNode(Document wsdlDoc, String nodeValue) throws DOMException {
        Element element = wsdlDoc.createElement("wsp:PolicyReference");
        element.setAttribute("URI", nodeValue);
        return element;
    }

    public String createClientWsdl(String camelCxfWsdl, boolean injectBITAPolicy) throws Exception {
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document doc = dBuilder.parse(new InputSource(new StringReader(camelCxfWsdl)));
        if (injectBITAPolicy) {

            Node wsdlDefinitions = doc.getElementsByTagName("wsdl:definitions").item(0);
            NamedNodeMap wsdlDefinitionsAtt = wsdlDefinitions.getAttributes();

            File fXmlFile = new File("/home/mehdi/all/repositories/github.com/bita/bita-esb-core/src/test/resources/policy.wsdl");
            Document securitySample = dBuilder.parse(fXmlFile);
            for (String attribute : attributeMap.keySet()) {
                if (wsdlDefinitionsAtt.getNamedItem(attribute) == null) {//tekrari naboodan
                    //add attribute to wsdl
                    ((Element) wsdlDefinitions).setAttribute(attribute, attributeMap.get(attribute));
                }
            }
            Node rootNode = securitySample.getChildNodes().item(0);
            for (int i = 0; i < rootNode.getChildNodes().getLength(); i++) {
                Node item = rootNode.getChildNodes().item(i);
                if (item.getNodeName().equals("wsp:Policy")) {
                    // Create a duplicate node
                    Node newNode = item.cloneNode(true);
                    // Transfer ownership of the new node into the destination document
                    doc.adoptNode(newNode);
                    wsdlDefinitions.appendChild(newNode);
                }
            }

            Node wsdlEndpoint = doc.getElementsByTagName("wsdl:binding").item(0);

            Element element = createWspPolicyNode(doc, "#Service_Binding_Policy");
            wsdlEndpoint.appendChild(element);
            for (int i = 0; i < wsdlEndpoint.getChildNodes().getLength(); i++) {
                Node item = wsdlEndpoint.getChildNodes().item(i);
                if (item.getNodeName().equals("wsdl:operation")) {
                    for (int j = 0; j < item.getChildNodes().getLength(); j++) {
                        Node item1 = item.getChildNodes().item(j);
                        if (item1.getNodeName().equals("wsdl:input")) {
                            Element element1 = createWspPolicyNode(doc, "#Service_Input_Policy");
                            item1.appendChild(element1);
                        } else if (item1.getNodeName().equals("wsdl:output")) {
                            Element element1 = createWspPolicyNode(doc, "#Service_Output_Policy");
                            item1.appendChild(element1);
                        }
                    }
                }
            }
        }
        DOMSource domSource = new DOMSource(doc);
        StringWriter writer = new StringWriter();
        StreamResult result = new StreamResult(writer);
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer = tf.newTransformer();
        transformer.transform(domSource, result);
        return writer.toString();
    }
}
