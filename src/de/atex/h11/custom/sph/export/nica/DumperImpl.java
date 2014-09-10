/*
 * File:    DumperImpl.java
 *
 * Copyright (c) 2012,  Atex Media Command GmbH
 *                      Kurhessenstrasse 13
 *                      64546 Moerfelden-Walldorf
 *                      Germany
 *
 * Audit:
 * v01.00  14-may-2012  st  Initial version.
 * v00.00  17-feb-2012  st  Created.
 */

package de.atex.h11.custom.sph.export.nica;

import java.io.File;
import java.io.Writer;
import java.io.IOException;
import java.io.FilterWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.net.MalformedURLException;
import java.util.Properties;
import java.util.AbstractQueue;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathFactory;
import javax.xml.xpath.XPathConstants;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import de.atex.h11.custom.sph.export.generic.QueueElement;
import de.atex.h11.custom.sph.export.generic.AbstractDumper;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 *
 * @author tstuehler
 */
public class DumperImpl extends AbstractDumper {
    
    public DumperImpl () {}
    
    
    @Override
    public void init (AbstractQueue<QueueElement> queue, Properties props) 
            throws MalformedURLException {
        this.queue = queue;
        this.props = props;
        
        String strUrl = props.getProperty("destinationURL");
        if (strUrl == null) {
            throw new RuntimeException("Required property destinationURL not found.");
        }
        url = new URL(strUrl);
        
        String strSpecialCharMap = props.getProperty("xslt.param.specialCharMap");
        if (!strSpecialCharMap.isEmpty()) {
            LoadSpecialCharMap(strSpecialCharMap);
        }
        else {
            logger.warning("Special Char Map file not specified in properties file.");
        }        
        
        
        outputProperties.setProperty(OutputKeys.INDENT, "yes");
        outputProperties.setProperty(OutputKeys.METHOD, "xml");
        outputProperties.setProperty(OutputKeys.STANDALONE, "no");
        outputProperties.setProperty("{http://xml.apache.org/xsl}indent-amount", "4");
        //outputProperties.setProperty(OutputKeys.DOCTYPE_PUBLIC, "-//IPTC//NewsML");
        //outputProperties.setProperty(OutputKeys.DOCTYPE_SYSTEM, "NewsML_1.2.dtd");
    }
    
    
    /**
     * Thread main loop.
     */
    @Override
    public void run () {
        logger.entering(loggerName, "run");

        while (true) {
            try {
                NewsMLQueueElement qe = (NewsMLQueueElement) dequeue(queue);
                Document doc = qe.getDocument();
                
                /*String strPhysPageId = (String) xp.evaluate(
                            "//NewsItem[NewsManagement/NewsItemType/@FormalName='Page']"
                            + "/NewsComponent/Metadata/Property[@FormalName='PhysPageId']/@Value",
                            doc, XPathConstants.STRING);
                if (strPhysPageId != null && !strPhysPageId.equals("")) {
                    String strFileName = strPhysPageId + ".xml";*/
                String strPageHref = (String) xp.evaluate(
                            "//NewsItem[NewsManagement/NewsItemType/@FormalName='Page']"
                            + "/NewsComponent/(NewsComponent)[1]/ContentItem/@Href",
                            doc, XPathConstants.STRING);
                if (strPageHref != null && !strPageHref.equals("")) {
                    String strPhysPageId = strPageHref.substring(0, strPageHref.lastIndexOf('.'));
                    String strFileName = strPhysPageId + ".xml";
                    write(url, strFileName, doc, outputProperties);

                    if (!qe.getReferences().isEmpty()) {
                        File zipFile = File.createTempFile("tmp", ".zip");
                        ZipOutputStream zipOut = new ZipOutputStream(new FileOutputStream(zipFile));
                        zipOut.setLevel(9);
                        zipOut.setMethod(ZipOutputStream.DEFLATED);
                        for (Reference ref : qe.getReferences()) {
                            ZipEntry ze = new ZipEntry(ref.getName());
                            if (Document.class.isAssignableFrom(ref.getDataType())) {
                                try {
                                    logger.finer("Add to zip: " + ref.getName());
                                    zipOut.putNextEntry(ze);
                                    dump(new DOMSource(((Reference<Document>) ref).getData()), 
                                            new StreamResult(new HTMLFilterWriter(new OutputStreamWriter(zipOut))));                                    
                                } catch (Exception e) {
                                    logger.log(Level.WARNING, Thread.currentThread().getName(), e);
                                }
                            } else if (String.class.isAssignableFrom(ref.getDataType())) {
                                try {
                                    logger.finer("Add to zip: " + ref.getName());
                                    String strSourceFile = ((Reference<String>) ref).getData();
                                    File file = new File(strSourceFile);
                                    if (!file.exists()) {
                                        if (props.getProperty("debug", "false").equalsIgnoreCase("true")
                                            && props.getProperty("dummyPicture") != null) {
                                            file = new File(props.getProperty("dummyPicture"));
                                            logger.info("Using dummy picture for debugging.");
                                        } else {
                                            logger.warning(file.getCanonicalPath() + ": does not exist.");
                                            continue;
                                        }
                                    }
                                    zipOut.putNextEntry(ze);
                                    FileInputStream in = new FileInputStream(file);
                                    streamCopy(in, zipOut);
                                    in.close();                                    
                                } catch (Exception e) {
                                    logger.log(Level.WARNING, Thread.currentThread().getName(), e);
                                }
                            } else {
                                logger.warning("Unexpected reference data type (" 
                                        + ref.getDataType().getName() + ") found.");
                            }
                        }
                        zipOut.close();
                        write(url, strPhysPageId + ".zip", zipFile);
                        zipFile.delete();
                    }
                }
            } catch (InterruptedException e) {
                logger.log(Level.INFO, Thread.currentThread().getName(), e);
            } catch (Exception e) {
                logger.log(Level.WARNING, Thread.currentThread().getName(), e);
            }
        }
    }

    protected class HTMLFilterWriter extends FilterWriter {
        protected HTMLFilterWriter (Writer out) {
            super(out);
        }
        
        @Override
        public void write (char[] cbuf, int off, int len) throws IOException {
            for (int i = off; i < off + len; i++) {
                write((int) cbuf[i]);
            }
        }
        
        @Override
        public void write (int c) throws IOException {
            //if ((char) c == '’')
            //    out.write("&#8217;");
            //else if ((char) c == '“')
            //    out.write("&#8220;");
            //else if ((char) c == '”')
            //    out.write("&#8221;");
            //else if ((char) c == '–')   // 0x1320
            //    out.write("&#8211;");
            //else if ((char) c == '–')
            //    out.write("&#8212;");
            // 20120612 jpm: added more chars, as required
            //if (c == 8216 || c == 8217 || c == 8219 || c == 8242 || c == 8245 || 
            //    c == 8220 || c == 8221 || c == 8223 || c == 8243 || c == 8246 || 
            //    c == 8208 || c == 8209 || c == 8210 || c == 8211 || c == 8212 || c == 8213)
            // 20120614 jpm: convert non-ASCII
            if (specialCharHM.containsKey(c)) {
                out.write(specialCharHM.get(c));
            }
            else if (c > 127) {
                out.write("&#" + Integer.toString(c) + ";");
            }
            else {
                out.write(c);
            }
        }
        
        @Override
        public void write (String str, int off, int len) throws IOException {
            for (int i = off; i < off + len; i++) {
                write((int) str.charAt(i));
            }            
        }
    }
    
    private void LoadSpecialCharMap(String strSpecialCharMap) {
        logger.entering(loggerName, "LoadSpecialCharMap");
               
        try {
            File specialCharMap = new File(strSpecialCharMap);

            docBuilderFactory = DocumentBuilderFactory.newInstance();
            docBuilderFactory.setNamespaceAware(true);
            docBuilder = docBuilderFactory.newDocumentBuilder();
            Document doc = docBuilder.parse(specialCharMap);
            
            NodeList nl = (NodeList) xp.evaluate(
                "//lookup/map[@char-code]", doc, XPathConstants.NODESET);
            for (int i = 0; i < nl.getLength(); i++) {
                Integer key = Integer.parseInt(nl.item(i).getAttributes().getNamedItem("char-code").getNodeValue());
                String value = nl.item(i).getTextContent();
                logger.finest("LoadSpecialCharMap: key=" + Integer.toString(key) + ", value=" + value);
                specialCharHM.put(key, value);
            }
            
        }
        catch (Exception e) {
            logger.severe("Error in LoadSpecialCharMap: " + e.toString());
        }
        
        logger.exiting(loggerName, "LoadSpecialCharMap");
    }
    
            
    private URL url = null;
    private AbstractQueue<QueueElement> queue = null;
    private Properties outputProperties = new Properties();
    private XPathFactory xpf = XPathFactory.newInstance();
    private XPath xp = xpf.newXPath();
    private DocumentBuilderFactory docBuilderFactory = null;
    private DocumentBuilder docBuilder = null;    
    private HashMap<Integer, String> specialCharHM = new HashMap<Integer, String>();
    
    private static final String loggerName = DumperImpl.class.getName();
    private static final Logger logger = Logger.getLogger(loggerName);    
}
