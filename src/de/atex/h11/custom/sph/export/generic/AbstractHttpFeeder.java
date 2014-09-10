/*
 * File:    AbstractHttpFeeder.java
 *
 * Copyright (c) 2012,  Atex Media Command GmbH
 *                      Kurhessenstrasse 13
 *                      64546 Moerfelden-Walldorf
 *                      Germany
 *
 * Audit:
 * v01.00  25-apr-2012  st  Initial version.
 * v00.00  25-apr-2012  st  Created.
 */

package de.atex.h11.custom.sph.export.generic;

import java.io.File;
import java.net.URL;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.util.AbstractQueue;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.logging.Logger;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 *
 * @author tstuehler
 */
public abstract class AbstractHttpFeeder extends Commons implements Feeder {
    
    /**
     * Create a new QueueElement.
     * 
     * @param doc a story document to be queued for further processing. 
     * @return the queue element.
     */
    public abstract QueueElement newQueueElement (Document doc);
    
    /**
     * Get default filter stylesheet.
     * 
     * @return the built-in stylesheet name or null
     */
    public abstract String getDefaultFilter ();

    /**
     * Get default stylesheet for transformation.
     * 
     * @return the built-in stylesheet resource name or null
     */
    public abstract String getDefaultStylesheet ();
        
    /**
     * Initialize this feeder.
     * 
     * @param docq
     * @param props
     * @param args 
     */
    @Override
    public void init (AbstractQueue<QueueElement> queue, Properties props) 
            throws MalformedURLException, ParserConfigurationException, 
                   TransformerConfigurationException {
        this.queue = queue;
        this.props = props;
        
        String strInputURL = System.getProperty("ncmInputURL");
        if (strInputURL == null) {
            logger.severe("System property ncmInputURL not set.");
            System.exit(1);
        } else {
            url = new URL(strInputURL);
        }

        // Check filter.
        String strFilter = props.getProperty("filter");
        if (strFilter != null) {
            filterFile = new File(strFilter);
            if (!filterFile.exists()) {
                throw new RuntimeException(filterFile.getPath() + ": does not exist.");
            } else if (!filterFile.isFile()) {
                throw new RuntimeException(filterFile.getPath() + ": is not a file.");
            }
            if (!filterFile.canRead()) {
                throw new RuntimeException(filterFile.getPath() + ": is not readable.");
            }
        } else {
            logger.config("No filter stylesheet configured.");
        }

        // Check stylesheet.
        String strTransform = props.getProperty("transform");
        if (strTransform != null) {
            transformFile = new File(strTransform);
            if (!transformFile.exists()) {
                throw new RuntimeException(transformFile.getPath() + ": does not exist.");
            } else if (!transformFile.isFile()) {
                throw new RuntimeException(transformFile.getPath() + ": is not a file.");
            }
            if (!transformFile.canRead()) {
                throw new RuntimeException(transformFile.getPath() + ": is not readable.");
            }
        } else {
            logger.config("No transform stylesheet configured.");
        }

        bDebug = props.getProperty("debug", "false").equalsIgnoreCase("true");

        // Prepare a document builder.
        docBuilderFactory = DocumentBuilderFactory.newInstance();
        docBuilderFactory.setNamespaceAware(true);
        docBuilder = docBuilderFactory.newDocumentBuilder();

        transformerFactory = TransformerFactory.newInstance();

        // Prepare a transfomer.
        transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.METHOD, "xml");
        transformer.setOutputProperty(OutputKeys.STANDALONE, "no");
        transformer.setOutputProperty("{http://xml.apache.org/xsl}indent-amount", "4");

        logger.fine("Using class " + docBuilderFactory.getClass().getCanonicalName() + " as DocumentBuilderFactory.");
        logger.fine("Using class " + transformerFactory.getClass().getCanonicalName() + " as TransformerFactory.");
    }
    
    
    /**
     * Thread main loop.
     */
    @Override
    public void run () {
        logger.entering(getClass().getName(), "run");

        HttpURLConnection conn = null;
        Document doc = null;
        File filteredFile = null;
        long startMillis = -1;
        long endMillis = -1;
        
        try {
            // Open the connection.
            conn = (HttpURLConnection) url.openConnection();
            conn.setDoOutput(false);
            conn.setDoInput(true);
            conn.setRequestMethod("GET");
            conn.setInstanceFollowRedirects(true);
            conn.connect();
            
            // Apply the filter.
            if (filterFile != null) {
                logger.info("Applying filter stylesheet " + filterFile.getPath() + ".");
                startMillis = System.currentTimeMillis();
                filteredFile = File.createTempFile("temp", ".xml");
                StreamResult result = new StreamResult(filteredFile);
                Transformer t = transformerFactory.newTransformer(new StreamSource(filterFile));
                for (String strProp : props.stringPropertyNames()) {
                    if (strProp.startsWith("xslt.param.")) {
                        t.setParameter(strProp.replaceFirst("xslt.param.", ""), props.getProperty(strProp));
                    }
                }
                t.transform(new StreamSource(conn.getInputStream()), result);
                endMillis = System.currentTimeMillis();
                logger.info("Document filtered in " + (endMillis - startMillis) + "ms.");
            } else if (getDefaultFilter() != null) {
                logger.info("Applying built-in filter stylesheet.");
                startMillis = System.currentTimeMillis();
                filteredFile = File.createTempFile("temp", ".xml");
                StreamResult result = new StreamResult(filteredFile);
                Transformer t = transformerFactory.newTransformer(new StreamSource(
                    getClass().getClassLoader().getResourceAsStream(getDefaultFilter())));
                for (String strProp : props.stringPropertyNames()) {
                    if (strProp.startsWith("xslt.param.")) {
                        t.setParameter(strProp.replaceFirst("xslt.param.", ""), props.getProperty(strProp));
                    }
                }
                t.transform(new StreamSource(conn.getInputStream()), result);
                endMillis = System.currentTimeMillis();
                logger.info("Document filtered in " + (endMillis - startMillis) + "ms.");                
            }

            if (bDebug && filteredFile != null) {
                String strFilteredFileName = getBaseFileName() + "_filtered.xml";
                File debugFile = new File(strFilteredFileName);
                String strDebugDumpDir = props.getProperty("debugDumpDir");
                Document filteredDoc = docBuilder.parse(filteredFile);
                if (strDebugDumpDir != null && !strDebugDumpDir.equals(""))
                    dump(filteredDoc, new File(strDebugDumpDir, debugFile.getName()));
                else
                    dump(filteredDoc, debugFile);
            }

            // Transform the document.
            StreamSource source = null;
            if (filteredFile != null)
                source = new StreamSource(filteredFile);
            else
                source = new StreamSource(conn.getInputStream());
            doc = docBuilder.newDocument();
            DOMResult result = new DOMResult(doc);
            if (transformFile != null) {
                logger.info("Applying transform stylesheet " + transformFile.getPath() + ".");
                startMillis = System.currentTimeMillis();
                Transformer t = transformerFactory.newTransformer(new StreamSource(transformFile));
                for (String strProp : props.stringPropertyNames()) {
                    if (strProp.startsWith("xslt.param.")) {
                        t.setParameter(strProp.replaceFirst("xslt.param.", ""), props.getProperty(strProp));
                    }
                }
                t.transform(source, result);
                endMillis = System.currentTimeMillis();
                logger.info("Document transformed in " + (endMillis - startMillis) + "ms.");
            } else {
                logger.info("Applying built-in transform stylesheet.");
                startMillis = System.currentTimeMillis();
                Transformer t = transformerFactory.newTransformer(new StreamSource(
                    getClass().getClassLoader().getResourceAsStream(getDefaultStylesheet())));
                for (String strProp : props.stringPropertyNames()) {
                    if (strProp.startsWith("xslt.param.")) {
                        t.setParameter(strProp.replaceFirst("xslt.param.", ""), props.getProperty(strProp));
                    }
                }
                t.transform(source, result);
                endMillis = System.currentTimeMillis();
                logger.info("Document transformed in " + (endMillis - startMillis) + "ms.");
            }
            
            if (bDebug) {
                String strTransformedFileName = getBaseFileName() + "_transformed.xml";
                File transformedFile = new File(strTransformedFileName);
                String strDebugDumpDir = props.getProperty("debugDumpDir");
                if (strDebugDumpDir != null && !strDebugDumpDir.equals(""))
                    dump(doc, new File(strDebugDumpDir, transformedFile.getName()));
                else
                    dump(doc, transformedFile);
            }
            
            // Split into story elements.
            startMillis = System.currentTimeMillis();
            NodeList nl = doc.getDocumentElement().getChildNodes();
            logger.info("Found " + nl.getLength() + " stories in document.");

            for (int i = 0; i < nl.getLength(); i++) {
                Node n = nl.item(i);
                Document storyDoc = docBuilder.newDocument();
                storyDoc.appendChild(storyDoc.importNode(n, true));
                enqueue(queue, newQueueElement(storyDoc));
            }
            endMillis = System.currentTimeMillis();
            logger.info("Document split into single stories in " + (endMillis - startMillis) + "ms.");

            if (filteredFile != null) filteredFile.delete();
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            if (conn != null) conn.disconnect();
        }

        logger.exiting(getClass().getName(), "run");
    }

    /**
     * Construct a file name from url query parameters.
     * 
     * @return base file name
     */
    private String getBaseFileName () {
        String strLevel = null;
        String strName = null;
        String strPubDate = null;
        
        StringTokenizer st = new StringTokenizer(url.getQuery(), "&");
        while (st.hasMoreElements()) {
            String[] sa = st.nextToken().split("=");
            if (sa.length == 2) {
                if (sa[0].equals("level"))
                    strLevel = sa[1];
                else if (sa[0].equals("name"))
                    strName = sa[1];
                else if (sa[0].equals("pubdate"))
                    strPubDate = sa[1];
            }
        }
        StringBuilder sb = new StringBuilder();
        if (strLevel != null) sb.append(strLevel);
        else sb.append("UNKNOWN");
        if (strName != null) {
            sb.append("_");
            sb.append(strName);
        }
        if (strPubDate != null) {
            sb.append("_");
            sb.append(strPubDate);
        }
        else sb.append("_NODATE");
        
        return sb.toString();
    }

    
    protected boolean bDebug = false;
    private URL url = null;
    private File filterFile = null;
    private File transformFile = null;
    protected Properties props = null;
    private AbstractQueue<QueueElement> queue = null;
    
    private DocumentBuilderFactory docBuilderFactory = null;
    private DocumentBuilder docBuilder = null;

    private TransformerFactory transformerFactory = null;
    private Transformer transformer = null;

    private static final String loggerName = AbstractHttpFeeder.class.getName();
    private static final Logger logger = Logger.getLogger(loggerName);    
}
