/*
 * File:    DTDValidator.java
 *
 * Copyright (c) 2012,  Atex Media Command GmbH
 *                      Kurhessenstrasse 13
 *                      64546 Moerfelden-Walldorf
 *                      Germany
 *
 * Audit:
 * v01.00  04-apr-2012  st  Initial version.
 * v00.00  04-apr-2012  st  Created.
 */

package de.atex.h11.custom.sph.export.generic;

import java.io.File;
import java.io.InputStreamReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 *
 * @author tstuehler
 */
public class DTDValidator {
    
    /**
     * Default constructor.
     * 
     * @throws javax.xml.parsers.ParserConfigurationException
     */
    public DTDValidator () throws ParserConfigurationException, TransformerConfigurationException {
        dbFactory = DocumentBuilderFactory.newInstance();
        dbFactory.setNamespaceAware(true);
        dbFactory.setValidating(true);
        docBuilder = dbFactory.newDocumentBuilder();
        docBuilder.setErrorHandler(eh);
        docBuilder.setEntityResolver(new EntityResolver());

        tf = tff.newTransformer();
    }

    public void validate (Document doc, String systemId) 
            throws IOException, SAXException, ParseException, TransformerException {
        Object[] params = new Object[2];
        params[0] = doc;
        params[1] = systemId;
        logger.entering(loggerName, "validate", params);
            
        ByteArrayOutputStream os = new ByteArrayOutputStream(32768);
        tf.reset();
        tf.setOutputProperty(OutputKeys.INDENT, "no");
        tf.setOutputProperty(OutputKeys.METHOD, "xml");
        tf.setOutputProperty(OutputKeys.STANDALONE, "no");
        tf.setOutputProperty(OutputKeys.DOCTYPE_PUBLIC, systemId);
        tf.setOutputProperty(OutputKeys.DOCTYPE_SYSTEM, systemId);
        tf.transform(new DOMSource(doc), new StreamResult(os));
        
        ByteArrayInputStream in = new ByteArrayInputStream(os.toByteArray());
        Document doc2 = docBuilder.parse(new InputSource(in));
        if (eh.getErrorCount() > 0) {
            throw new ParseException("Document caused "
                    + eh.getErrorCount() + " errors during parse.");
        }
        
        logger.exiting(loggerName, "validate");
    }
    
    /**
     * Validate a XML file against its DTD.
     * 
     * @param file
     * @throws java.io.IOException
     * @throws org.xml.sax.SAXException
     */
    public void validate (File file) throws IOException, SAXException, ParseException {
        Document doc = docBuilder.parse(file);
        if (eh.getErrorCount() > 0) {
            throw new ParseException("File caused "
                    + eh.getErrorCount() + " errors during parse.");
        }
    }

    private class ErrorHandler implements org.xml.sax.ErrorHandler {
        @Override
        public void warning (SAXParseException e) {
            logger.log(Level.WARNING, "", e);
            warnCount++;
        }

        @Override
        public void error (SAXParseException e) {
            logger.log(Level.SEVERE, "", e);
            errCount++;
        }

        @Override
        public void fatalError (SAXParseException e) {
            throw new RuntimeException(e);
        }

        public int getErrorCount () {
            return this.errCount;
        }

        public int getWarnCount () {
            return this.warnCount;
        }

        int errCount = 0;
        int warnCount = 0;
    }

    private class ParseException extends Exception {
        public ParseException () {
            super();
        }

        public ParseException (String s) {
            super(s);
        }
    }
    
    public class EntityResolver implements org.xml.sax.EntityResolver {
        @Override
        public InputSource resolveEntity (String publicId, String systemId) {
            Object[] params = new Object[2];
            params[0] = publicId;
            params[1] = systemId;
            logger.entering(loggerName, "resolveEntity", params);
            try {
                InputStreamReader reader = new InputStreamReader(getClass().getClassLoader().getResourceAsStream(publicId));
                logger.exiting(loggerName, "resolveEntity", reader);
                return new InputSource(reader);                
            } catch (Exception e) {
                logger.log(Level.INFO, "", e);
                logger.exiting(loggerName, "resolveEntity");
                return null;
            }
        }
    }

    
    private DocumentBuilder docBuilder= null;
    private DocumentBuilderFactory dbFactory = null;
    private TransformerFactory tff = TransformerFactory.newInstance();
    private Transformer tf = null;
    private ErrorHandler eh = new ErrorHandler();
    
    private static final String loggerName = DTDValidator.class.getName();
    private static final Logger logger = Logger.getLogger(loggerName);        
}
