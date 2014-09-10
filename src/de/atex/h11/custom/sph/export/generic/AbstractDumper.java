/*
 * File:    AbstractDumper.java
 *
 * Copyright (c) 2012,  Atex Media Command GmbH
 *                      Kurhessenstrasse 13
 *                      64546 Moerfelden-Walldorf
 *                      Germany
 *
 * Audit:
 * v01.00  04-apr-2012  st  Initial version.
 * v00.00  21-feb-2012  st  Created.
 */

package de.atex.h11.custom.sph.export.generic;

import java.io.File;
import java.io.PrintWriter;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.transform.TransformerException;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;

/**
 *
 * @author tstuehler
 */
public abstract class AbstractDumper extends Commons implements Dumper {
    
    /**
     * Write a document to a destination described by an URL.
     * This works with file, ftp and http urls.
     *
     * @param destURL URL describing the endpoint.
     * @param destFileName optional destination file name.
     * @param doc xml document.
     * @throws java.net.ProtocolException
     * @throws java.io.FileNotFoundException
     * @throws java.io.IOException
     * @throws java.io.UnsupportedEncodingException
     * @throws javax.xml.transform.TransformerException
     */
    protected void write (URL destURL, String destFileName, Document doc)
            throws ProtocolException, FileNotFoundException, IOException,
            UnsupportedEncodingException, TransformerException {
        write (destURL, destFileName, doc, null);
    }
    
    
    /**
     * Write a document to a destination described by an URL.
     * This works with file, ftp and http urls.
     *
     * @param destURL URL describing the endpoint.
     * @param destFileName optional destination file name.
     * @param doc xml document.
     * @param outputProperties xml output properties.
     * @throws java.net.ProtocolException
     * @throws java.io.FileNotFoundException
     * @throws java.io.IOException
     * @throws java.io.UnsupportedEncodingException
     * @throws javax.xml.transform.TransformerException
     */
    protected void write (URL destURL, String destFileName, Document doc, Properties outputProperties)
            throws ProtocolException, FileNotFoundException, IOException,
            UnsupportedEncodingException, TransformerException {
        Object[] logParams = new Object[3];
        logParams[0] = destURL;
        logParams[1] = destFileName;
        logParams[2] = doc;
        logger.entering(getClass().getName(), "write", logParams);

        HttpURLConnection http = null;
        PrintWriter out = null;
        FTPClient ftp = null;

        if (destFileName == null) {
            destFileName = new Date().getTime() + "-"
                    + Thread.currentThread().getId() + ".xml";
        }

        if (destURL.getProtocol().equals("file")) {
            String path = destURL.getPath();
            File file = new File(path + File.separator + destFileName);
            out = new PrintWriter(file, "UTF-8");
        } else if (destURL.getProtocol().equals("ftp")) {
            ftp = getFtpConnection(destURL);
            out = new PrintWriter(new OutputStreamWriter(
                    ftp.storeFileStream(destFileName), "UTF-8"));
        } else if (destURL.getProtocol().equals("http")) {
            httpHeaderFields = null;
            httpErrDescr = null;
            httpResponse = null;
            httpResponseCode = 0;
            String encoding = props.getProperty("http.contentEncoding", defaultContentEncoding);
            String contentType = props.getProperty("http.contentType", defaultContentType);
            http = (HttpURLConnection) destURL.openConnection();
            http.setDoOutput(true);
            http.setDoInput(true);
            http.setRequestMethod("POST");
            http.setRequestProperty("Content-Type", contentType + "; charset=" + encoding);
            http.setRequestProperty("Content-Disposition", "filename=" + destFileName);
            http.setRequestProperty("Accept", contentType);
            if (destURL.getUserInfo() != null) {
                byte[] bytes = Base64.encodeBase64(destURL.getUserInfo().getBytes());
                http.setRequestProperty("Authorization", "Basic " + new String(bytes));
            }
            http.setInstanceFollowRedirects(true);
            http.connect();
            out = new PrintWriter(new OutputStreamWriter(http.getOutputStream(), encoding));
        } else {
            throw new ProtocolException("Unsupported protocol: "
                                        + destURL.getProtocol());
        }

        DOMSource source = new DOMSource(doc);
        StreamResult result = new StreamResult(out);
        if (outputProperties == null)
            dump(source, result);
        else
            dump(source, result, outputProperties);
        out.close();

        if (ftp != null) ftp.disconnect();
        if (http != null) {
            httpHeaderFields = http.getHeaderFields();
            httpResponseCode = http.getResponseCode();
            if (httpResponseCode != HttpURLConnection.HTTP_OK
                    && httpResponseCode != HttpURLConnection.HTTP_CREATED
                    && httpResponseCode != HttpURLConnection.HTTP_ACCEPTED) {
                try {
                    InputStream errStream = http.getErrorStream();
                    if (errStream != null) {
                        BufferedReader err = new BufferedReader(
                            new InputStreamReader(errStream));
                        StringBuffer errSB = new StringBuffer(2000);
                        String errStr = null;
                        while ((errStr = err.readLine()) != null) {
                            errSB.append(errStr);
                        }
                        err.close();
                        httpErrDescr = errSB.toString();
                        //throw new IOException("Response code: " + httpResponseCode + " - " + httpErrDescr);
                        logger.logp(Level.WARNING, getClass().getName(), "write",
                            "Response code: " + httpResponseCode + " - " + httpErrDescr);
                    } else {
                        //throw new IOException("Response code: " + httpResponseCode);
                        logger.logp(Level.WARNING, getClass().getName(), "write",
                            "Response code: " + httpResponseCode);
                    }
                } catch (Exception e) {}
            } else {
                try {
                    InputStream inStream = http.getInputStream();
                    if (inStream != null) {
                        BufferedReader resp = new BufferedReader(
                            new InputStreamReader(inStream));
                        StringBuffer sb = new StringBuffer(2000);
                        String str = null;
                        while ((str = resp.readLine()) != null) {
                            sb.append(str);
                        }
                        resp.close();
                        httpResponse = sb.toString();
                        logger.logp(Level.FINE, getClass().getName(), "write", httpResponse);
                    }
                } catch (Exception e) {}
            }
            http.disconnect();
        }

        logger.exiting(getClass().getName(), "write");
    }

    
    protected void write (URL destURL, String destFileName, InputStream in)
            throws ProtocolException, FileNotFoundException, IOException,
            UnsupportedEncodingException {
        Object[] logParams = new Object[3];
        logParams[0] = destURL;
        logParams[1] = destFileName;
        logParams[2] = in;
        logger.entering(getClass().getName(), "write", logParams);
        
        HttpURLConnection http = null;
        OutputStream out = null;
        FTPClient ftp = null;

        if (destFileName == null) {
            destFileName = new Date().getTime() + "-"
                    + Thread.currentThread().getId() + ".xml";
        }

        if (destURL.getProtocol().equals("file")) {
            String path = destURL.getPath();
            File file = new File(path + File.separator + destFileName);
            out = new FileOutputStream(file);
        } else if (destURL.getProtocol().equals("ftp")) {
            ftp = getFtpConnection(destURL);
            out = ftp.storeFileStream(destFileName);
        } else if (destURL.getProtocol().equals("http")) {
            httpHeaderFields = null;
            httpErrDescr = null;
            httpResponse = null;
            httpResponseCode = 0;
            http = (HttpURLConnection) destURL.openConnection();
            http.setDoOutput(true);
            http.setDoInput(true);
            http.setRequestMethod("POST");
            http.setRequestProperty("Content-Type", "application/octet-stream");
            http.setRequestProperty("Content-Disposition", "filename=" + destFileName);
            if (destURL.getUserInfo() != null) {
                byte[] bytes = Base64.encodeBase64(destURL.getUserInfo().getBytes());
                http.setRequestProperty("Authorization", "Basic " + new String(bytes));
            }
            http.setInstanceFollowRedirects(true);
            http.connect();
            out = http.getOutputStream();
        } else {
            throw new ProtocolException("Unsupported protocol: "
                                        + destURL.getProtocol());
        }

        streamCopy(in, out);
        in.close();
        out.close();

        if (ftp != null) ftp.disconnect();
        if (http != null) {
            httpHeaderFields = http.getHeaderFields();
            httpResponseCode = http.getResponseCode();
            if (httpResponseCode != HttpURLConnection.HTTP_OK
                    && httpResponseCode != HttpURLConnection.HTTP_CREATED
                    && httpResponseCode != HttpURLConnection.HTTP_ACCEPTED) {
                try {
                    InputStream errStream = http.getErrorStream();
                    if (errStream != null) {
                        BufferedReader err = new BufferedReader(
                            new InputStreamReader(errStream));
                        StringBuffer errSB = new StringBuffer(2000);
                        String errStr = null;
                        while ((errStr = err.readLine()) != null) {
                            errSB.append(errStr);
                        }
                        err.close();
                        httpErrDescr = errSB.toString();
                        //throw new IOException("Response code: " + httpResponseCode + " - " + httpErrDescr);
                        logger.logp(Level.WARNING, getClass().getName(), "write",
                            "Response code: " + httpResponseCode + " - " + httpErrDescr);
                    } else {
                        //throw new IOException("Response code: " + httpResponseCode);
                        logger.logp(Level.WARNING, getClass().getName(), "write",
                            "Response code: " + httpResponseCode);
                    }
                } catch (Exception e) {}
            } else {
                try {
                    InputStream inStream = http.getInputStream();
                    if (inStream != null) {
                        BufferedReader resp = new BufferedReader(
                            new InputStreamReader(inStream));
                        StringBuffer sb = new StringBuffer(2000);
                        String str = null;
                        while ((str = resp.readLine()) != null) {
                            sb.append(str);
                        }
                        resp.close();
                        httpResponse = sb.toString();
                        logger.logp(Level.FINE, getClass().getName(), "write", httpResponse);
                    }
                } catch (Exception e) {}
            }
            http.disconnect();
        }
        
        logger.exiting(getClass().getName(), "write");
    }
    
    protected void write (URL destURL, String destFileName, File srcFile)
            throws ProtocolException, FileNotFoundException, IOException,
            UnsupportedEncodingException {
        Object[] logParams = new Object[3];
        logParams[0] = destURL;
        logParams[1] = destFileName;
        logParams[2] = srcFile;
        logger.entering(getClass().getName(), "write", logParams);

        HttpURLConnection http = null;
        OutputStream out = null;
        FTPClient ftp = null;

        if (destFileName == null) {
            destFileName = new Date().getTime() + "-"
                    + Thread.currentThread().getId() + ".xml";
        }

        if (destURL.getProtocol().equals("file")) {
            String path = destURL.getPath();
            File file = new File(path + File.separator + destFileName);
            out = new FileOutputStream(file);
        } else if (destURL.getProtocol().equals("ftp")) {
            ftp = getFtpConnection(destURL);
            out = ftp.storeFileStream(destFileName);
        } else if (destURL.getProtocol().equals("http")) {
            httpHeaderFields = null;
            httpErrDescr = null;
            httpResponse = null;
            httpResponseCode = 0;
            http = (HttpURLConnection) destURL.openConnection();
            http.setDoOutput(true);
            http.setDoInput(true);
            http.setRequestMethod("POST");
            if (srcFile.getName().endsWith(".zip"))
                http.setRequestProperty("Content-Type", "application/zip");
            else
                http.setRequestProperty("Content-Type", "application/octet-stream");
            http.setRequestProperty("Content-Disposition", "filename=" + destFileName);
            if (destURL.getUserInfo() != null) {
                byte[] bytes = Base64.encodeBase64(destURL.getUserInfo().getBytes());
                http.setRequestProperty("Authorization", "Basic " + new String(bytes));
            }
            http.setInstanceFollowRedirects(true);
            http.connect();
            out = http.getOutputStream();
        } else {
            throw new ProtocolException("Unsupported protocol: "
                                        + destURL.getProtocol());
        }

        FileInputStream in = new FileInputStream(srcFile);
        streamCopy(in, out);
        in.close();
        out.close();

        if (ftp != null) ftp.disconnect();
        if (http != null) {
            httpHeaderFields = http.getHeaderFields();
            httpResponseCode = http.getResponseCode();
            if (httpResponseCode != HttpURLConnection.HTTP_OK
                    && httpResponseCode != HttpURLConnection.HTTP_CREATED
                    && httpResponseCode != HttpURLConnection.HTTP_ACCEPTED) {
                try {
                    InputStream errStream = http.getErrorStream();
                    if (errStream != null) {
                        BufferedReader err = new BufferedReader(
                            new InputStreamReader(errStream));
                        StringBuffer errSB = new StringBuffer(2000);
                        String errStr = null;
                        while ((errStr = err.readLine()) != null) {
                            errSB.append(errStr);
                        }
                        err.close();
                        httpErrDescr = errSB.toString();
                        //throw new IOException("Response code: " + httpResponseCode + " - " + httpErrDescr);
                        logger.logp(Level.WARNING, getClass().getName(), "write",
                            "Response code: " + httpResponseCode + " - " + httpErrDescr);
                    } else {
                        //throw new IOException("Response code: " + httpResponseCode);
                        logger.logp(Level.WARNING, getClass().getName(), "write",
                            "Response code: " + httpResponseCode);
                    }
                } catch (Exception e) {}
            } else {
                try {
                    InputStream inStream = http.getInputStream();
                    if (inStream != null) {
                        BufferedReader resp = new BufferedReader(
                            new InputStreamReader(inStream));
                        StringBuffer sb = new StringBuffer(2000);
                        String str = null;
                        while ((str = resp.readLine()) != null) {
                            sb.append(str);
                        }
                        resp.close();
                        httpResponse = sb.toString();
                        logger.logp(Level.FINE, getClass().getName(), "write", httpResponse);
                    }
                } catch (Exception e) {}
            }
            http.disconnect();
        }

        logger.exiting(getClass().getName(), "write");
    }


    /**
     * Establish a FTP connection.
     *
     * @param url FTP URL describing the endpoint.
     * @return 
     * @throws java.io.IOException
     */
    private FTPClient getFtpConnection (URL url) throws IOException {
        logger.entering(getClass().getName(), "getFtpConnection", url);

        String protocol = url.getProtocol();
        String host = url.getHost();
        String userInfo = url.getUserInfo();
        String path = url.getPath();

        String[] credentials = userInfo.split(":");

        FTPClient ftp = new FTPClient();
        ftp.connect(host);
        ftp.login(credentials[0], credentials[1]);
        int reply = ftp.getReplyCode();
        if (reply == FTPReply.NOT_LOGGED_IN) {
            try { ftp.disconnect(); ftp = null; } catch (Exception e) {}
            throw new IOException("Login failed on FTP host " + host + ".");
        }
        String sysName = ftp.getSystemName();
        System.err.println("FTP system is: " + sysName);
        boolean bStatus = ftp.changeToParentDirectory();
        bStatus = ftp.changeWorkingDirectory(path);
        if (!bStatus) {
            try { ftp.logout(); ftp.disconnect(); ftp = null; } catch (Exception e) {}
            throw new IOException("Changing working directory to " + path
                                    + " failed on FTP host " + host + ".");
        }
        if (props.getProperty("ftpPassiveMode", "false").equalsIgnoreCase("true")) {
            ftp.enterLocalPassiveMode();
        }
        if (!ftp.setFileType(FTP.BINARY_FILE_TYPE)) {
            try { ftp.logout(); ftp.disconnect(); ftp = null; } catch (Exception e) {}
            throw new IOException("Failed to set binary transfer mode.");
        }

        logger.exiting(getClass().getName(), "getFtpConnection");

        return ftp;
    }
    
    protected Properties props = null;
    protected Map<String, List<String>> httpHeaderFields = null;
    protected String httpErrDescr = null;
    protected String httpResponse = null;
    protected int httpResponseCode = 0;
    
    private static final String defaultContentType = "text/xml";
    private static final String defaultContentEncoding = "UTF-8";
    private static final String loggerName = AbstractDumper.class.getName();
    private static final Logger logger = Logger.getLogger(loggerName);    
}
