/*
 * File:    Commons.java
 *
 * Copyright (c) 2012,  Atex Media Command GmbH
 *                      Kurhessenstrasse 13
 *                      64546 Moerfelden-Walldorf
 *                      Germany
 *
 * Audit:
 * v01.00  04-apr-2012  st  Initial version.
 * v00.00  16-feb-2012  st  Created.
 */

package de.atex.h11.custom.sph.export.generic;

import java.io.*;
import java.util.AbstractQueue;
import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.apache.commons.codec.binary.Base64;
import org.w3c.dom.Document;

/**
 *
 * @author tstuehler
 */
public class Commons {
    
    public void enqueue (AbstractQueue<QueueElement> queue, QueueElement qe) 
            throws InterruptedException, TimeoutException {
        Object[] params = new Object[2];
        params[0] = queue;
        params[1] = qe;
        logger.entering(loggerName, "enqueue", params);
        
        boolean bQStatus = false;
        int retries = QRETRIES;
        while (!(bQStatus = queue.offer(qe)) && retries-- > 0)
            Thread.currentThread().sleep(1000);
        if (!bQStatus) {
            logger.warning("Could not queue element.");
            throw new TimeoutException("Could not queue element.");
        } else if ((QRETRIES - retries) > 0) {
            logger.finest("Document queued after " + (QRETRIES - retries) + " retries.");
        }
        
        logger.exiting(loggerName, "enqueue");
    }

    
    public QueueElement dequeue (AbstractQueue<QueueElement> queue) 
            throws InterruptedException {
        logger.entering(loggerName, "dequeue", queue);

        QueueElement qe = null;
        while (qe == null) {
            if ((qe = queue.poll()) == null)
                Thread.currentThread().sleep(200);
        }
        
        logger.exiting(loggerName, "dequeue", qe);
        
        return qe;
    }
    
    
    protected void dump (Document doc, File file) 
            throws FileNotFoundException, UnsupportedEncodingException,
                   TransformerException {
        Object[] params = new Object[2];
        params[0] = doc;
        params[1] = file;
        logger.entering(loggerName, "dump", params);
        
        DOMSource source = new DOMSource(doc);
        dump(source, file);
        
        logger.exiting(loggerName, "dump");        
    }

    
    protected void dump (Source source, File file) 
            throws FileNotFoundException, UnsupportedEncodingException,
                   TransformerException {
        Object[] params = new Object[2];
        params[0] = source;
        params[1] = file;
        logger.entering(loggerName, "dump", params);

        PrintWriter out = new PrintWriter(file, "UTF-8");
        StreamResult result = new StreamResult(out);
        dump(source, result);
        out.close();

        logger.exiting(loggerName, "dump");        
    }
    
    
    protected void dump (Source source, Result result) 
            throws FileNotFoundException, UnsupportedEncodingException,
                   TransformerException {
        Object[] params = new Object[2];
        params[0] = source;
        params[1] = result;
        logger.entering(loggerName, "dump", params);

        if (tf == null) {
            tf = tff.newTransformer();
            tf.setOutputProperty(OutputKeys.INDENT, "yes");
            tf.setOutputProperty(OutputKeys.METHOD, "xml");
            tf.setOutputProperty(OutputKeys.STANDALONE, "yes");
            tf.setOutputProperty("{http://xml.apache.org/xsl}indent-amount", "4");
        }
        tf.transform(source, result);
    
        logger.exiting(loggerName, "dump");
    }
    
    
    protected void dump (Source source, Result result, Properties outputProperties) 
            throws FileNotFoundException, UnsupportedEncodingException,
                   TransformerException {
        Object[] params = new Object[3];
        params[0] = source;
        params[1] = result;
        params[2] = outputProperties;
        logger.entering(loggerName, "dump", params);

        if (tf == null) {
            tf = tff.newTransformer();
            tf.setOutputProperties(outputProperties);
        }
        tf.transform(source, result);
    
        logger.exiting(loggerName, "dump");
    }
    
    
    protected void runProgram (List<String> args)
            throws IOException, InterruptedException {
        logger.entering(getClass().getName(), "runProgram", args);
       
        ProcessBuilder procBuilder = new ProcessBuilder(args);
        Process proc = procBuilder.start();
        proc.waitFor();

        // Evaluate exit value.
        int ev = proc.exitValue();
        if (ev != 0) {
            StringBuffer errSB = new StringBuffer();
            errSB.append(args.get(0) + ": returned " + ev + ".");
            InputStreamReader err = new InputStreamReader(proc.getErrorStream());
            char[] cbuf = new char[8192];
            int length = err.read(cbuf);
            if (length > 0) {
                errSB.append("\n" + new String(cbuf, 0, length));
            }
            err.close();
            throw new IOException(errSB.toString());
        }

        logger.exiting(getClass().getName(), "runProgram");
    }
    
    
    protected String runProgramGetResp (List<String> args)
            throws IOException, InterruptedException {
        logger.entering(getClass().getName(), "runProgramGetResp", args);
        String response = "";
        
        ProcessBuilder procBuilder = new ProcessBuilder(args);
        Process proc = procBuilder.start();
        proc.waitFor();

        // Evaluate exit value.
        int ev = proc.exitValue();
        if (ev != 0) {
            StringBuffer errSB = new StringBuffer();
            errSB.append(args.get(0) + ": returned " + ev + ".");
            InputStreamReader err = new InputStreamReader(proc.getErrorStream());
            char[] cbuf = new char[8192];
            int length = err.read(cbuf);
            if (length > 0) {
                errSB.append("\n" + new String(cbuf, 0, length));
            }
            err.close();
            throw new IOException(errSB.toString());
        }
        
        InputStream is = proc.getInputStream();     // to capture output from command
        response = convertStreamToStr(is);
        logger.finer("runProgramGetResp response: " + response);

        logger.exiting(getClass().getName(), "runProgramGetResp");
        return response;
    }    
    
    protected String convertStreamToStr(InputStream is) throws IOException {
        Writer writer = new StringWriter();
        char[] buffer = new char[1024];
        try {
            Reader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            int n;
            while ((n = reader.read(buffer)) != -1) {
                writer.write(buffer, 0, n);
            }
        }
        finally {
            is.close();
        }
        return writer.toString();
    }

    
    /**
     * Encode a binary file.
     *
     * @param file the file.
     * @param isChunked whether resulting text is chunked or in a single line.
     * @return base64 encoded data.
     * @throws java.io.IOException
     */
    private byte[] encodeBase64 (File file, boolean isChunked)
            throws IOException {
        byte[] buf = null;

        if (!file.isFile())
            throw new IOException(file.getAbsolutePath() + ": is not a file.");

        buf = new byte[(int) file.length()];
        FileInputStream in = new FileInputStream(file);
        int bytesRead = 0;
        do {
            bytesRead = in.read(buf, bytesRead, buf.length - bytesRead);
        } while (bytesRead > 0);

        return Base64.encodeBase64(buf, isChunked);
    }


    protected void streamCopy (InputStream in, OutputStream out)
            throws IOException {
        logger.entering(getClass().getName(), "streamCopy");

        byte[] buf = new byte[8192];
        int bytesRead;
        do {
            bytesRead = in.read(buf);
            if (bytesRead > 0)
                out.write(buf, 0, bytesRead);
        } while (bytesRead >= 0);

        logger.exiting(getClass().getName(), "streamCopy");
    }

    
    public class TimeoutException extends Exception {
        public TimeoutException () {
            super();
        }
        
        public TimeoutException (String s) {
            super(s);
        }
    }
    
    
    private TransformerFactory tff = TransformerFactory.newInstance();
    private Transformer tf = null;
    
    private static final int QRETRIES = 120;    
    
    private static final String loggerName = Commons.class.getName();
    private static final Logger logger = Logger.getLogger(loggerName);
}
