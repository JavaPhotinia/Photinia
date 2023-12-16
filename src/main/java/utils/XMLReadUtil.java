package utils;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;
import soot.Scene;
import soot.SootClass;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.FileReader;

public class XMLReadUtil {
    public static Document parseXML(File file) {
        try {
            DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
            documentBuilderFactory.setValidating(false);
            documentBuilderFactory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
            FileReader fr = new FileReader(file);
            InputSource is = new InputSource(fr);
            return documentBuilder.parse(is);
        } catch (Exception ignored) {

        }
        return null;
    }

    public static SootClass findMapper(Document document) {
        Element element = document.getDocumentElement();
        String namespace = element.getAttribute("namespace");
        if (namespace.equals("")) {
            return null;
        }
        return Scene.v().getSootClass(namespace);
    }
}
