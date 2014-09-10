/*
 * File:    DumperImpl.java
 *
 * Copyright (c) 2012,  Atex Media Command GmbH
 *                      Kurhessenstrasse 13
 *                      64546 Moerfelden-Walldorf
 *                      Germany
 *
 * Audit:
 * v01.00  06-jun-2012  st  Initial version.
 * v00.00  15-feb-2012  st  Created.
 */

package de.atex.h11.custom.sph.export.web;

import java.io.IOException;
import java.io.FileInputStream;
import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.net.URL;
import java.net.ProtocolException;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.AbstractQueue;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathFactory;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import org.w3c.dom.NodeList;
import org.w3c.dom.Document;
import org.w3c.dom.ProcessingInstruction;
import de.atex.h11.custom.sph.export.generic.FileTarget;
import de.atex.h11.custom.sph.export.generic.QueueElement;
import de.atex.h11.custom.sph.export.generic.AbstractDumper;

/**
 *
 * @author tstuehler
 */
public class DumperImpl extends AbstractDumper {
    
    public DumperImpl () {}
    
    @Override
    public void init (AbstractQueue<QueueElement> docq, Properties props) {
        this.docq = docq;
        this.props = props;
        
        outputProperties.setProperty(OutputKeys.INDENT, "yes");
        outputProperties.setProperty(OutputKeys.METHOD, "xml");
        outputProperties.setProperty(OutputKeys.STANDALONE, "no");
        outputProperties.setProperty(OutputKeys.CDATA_SECTION_ELEMENTS, 
                "caption copyright person title country kick content h1 h2 video abstract");
        outputProperties.setProperty("{http://xml.apache.org/xsl}indent-amount", "4");
        
        try {
            // Prepare the URL.
            String strUrl = null;
            String strOutputOption = null;
            if (props.containsKey("outputOption")) {
                strOutputOption = props.getProperty("outputOption").trim();
            }
            if (strOutputOption != null && !strOutputOption.isEmpty()) {
                strUrl = props.getProperty("destinationURL" + strOutputOption); // specific dest URL
            }
            else {
                strUrl = props.getProperty("destinationURL");                   // default dest URL
            }
            if (strUrl == null) {
                throw new RuntimeException("Required property destinationURL not found.");
            }
            logger.fine("Destination URL=" + strUrl);
            url = new URL(strUrl);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    
    /**
     * Thread main loop.
     */
    @Override
    public void run () {
        logger.entering(loggerName, "run");

        while (true) {
            try {
                //QueueElement qe = docq.poll();
                WebQueueElement qe = (WebQueueElement) docq.poll();
                if (qe == null) {
                    Thread.currentThread().sleep(200);
                    continue;
                } else {
                    Document doc = qe.getDocument();
                    dump(doc);
                    for (FileTarget ft : qe.getFileTargets()) {
                        long startMillis = System.currentTimeMillis();
                        write(url, ft.getFileName(), ft.getImage() != null ? new ByteArrayInputStream(ft.getImage()) : new FileInputStream(ft.getSourceFile()));
                        long endMillis = System.currentTimeMillis();
                        logger.info("File " + ft.getFileName() + " dumped in " + (endMillis - startMillis) + "ms.");
                    }
                }
            } catch (InterruptedException e) {
                logger.log(Level.INFO, Thread.currentThread().getName(), e);
            } catch (Exception e) {
                logger.log(Level.WARNING, Thread.currentThread().getName(), e);
            }
        }
    }

    
    private void dump (Document doc)
            throws IOException, FileNotFoundException, XPathExpressionException,
                   ProtocolException, TransformerException {
        long startMillis = System.currentTimeMillis();
        String strFileName = null;
        NodeList nl = (NodeList) xp.evaluate("//processing-instruction()",
                                    doc, XPathConstants.NODESET);
        for (int i = 0; i < nl.getLength(); i++) {
            String strTarget = ((ProcessingInstruction) nl.item(i)).getTarget();
            if (strTarget.equals("file-name"))
                strFileName = ((ProcessingInstruction) nl.item(i)).getData();
            nl.item(i).getParentNode().removeChild(nl.item(i));
        }
        
        write(url, strFileName, doc, outputProperties);
        long endMillis = System.currentTimeMillis();
        logger.info("Document " + strFileName + " dumped in " + (endMillis - startMillis) + "ms.");
    }


    private Properties outputProperties = new Properties();
    private URL url = null;
    private AbstractQueue<QueueElement> docq = null;
    private XPathFactory xpf = XPathFactory.newInstance();
    private XPath xp = xpf.newXPath();
    
    private static final String loggerName = DumperImpl.class.getName();
    private static final Logger logger = Logger.getLogger(loggerName);
    
}
