/*
 * File:    WorkerImpl.java
 *
 * Copyright (c) 2012,  Atex Media Command GmbH
 *                      Kurhessenstrasse 13
 *                      64546 Moerfelden-Walldorf
 *                      Germany
 *
 * Audit:
 *         20140909     jpm -value for <byline> element -grouping of byline, email, title and twitter -tagged text
 *         20131002     jpm -add special tag handling (use TagSpecialHandler class)
 *         20130906     jpm -differentiate between photo and graphic objects by using different xml tag names (photo vs graphic)        
 *         20130807     jpm add processing of 'abstract' and 'video' elements
 *         20130320     jpm get byline markers (e.g. by, oleh, report:) that need to be removed from the properties file* 
 *         20120724     jpm extend AbstractWorker class
 *         20120722     jpm 1. look for byline, etc from summary objects in addition to text and header objects
 *                          2. teaser and subtitle components of headline go to h2
 *         20120719     jpm 1. handling for [ and ] reserved chars (tag markers)
 *         20120717     jpm 1. no need to get kicker and subheadline text from h1.
 *                          -kicker text already set in the xsl (taken from 'Supertitle' component of headline)
 *                          -subheadline text to be taken from Summary object
 *                          2. always create a copyright element if a caption exists
 * v01.00  06-jun-2012  st  Initial version.
 * v00.00  15-feb-2012  st  Created.
 */

package de.atex.h11.custom.sph.export.web;

import de.atex.h11.custom.sph.export.generic.AbstractWorker;
import de.atex.h11.custom.sph.export.generic.QueueElement;
import de.atex.h11.custom.sph.export.generic.StyleReader;
import de.atex.h11.custom.sph.export.generic.TagCategory;
import de.atex.h11.custom.sph.export.generic.TagSpecialHandler;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.io.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.w3c.dom.*;
import org.xml.sax.SAXException;

/**
 *
 * @author tstuehler
 */
public class WorkerImpl extends AbstractWorker {

    public WorkerImpl() {
    }

    @Override
    public void init(AbstractQueue<QueueElement> inq, AbstractQueue<QueueElement> outq, Properties props)
            throws FileNotFoundException, IOException, ParserConfigurationException, SAXException {
        this.inq = inq;
        this.outq = outq;
        this.props = props;

        // Prepare a document builder.
        docBuilderFactory = DocumentBuilderFactory.newInstance();
        docBuilderFactory.setNamespaceAware(true);
        docBuilder = docBuilderFactory.newDocumentBuilder();

        String strStyleFile = props.getProperty("styleFile");
        if (strStyleFile != null) {
            tcHM = StyleReader.getTagCategories(strStyleFile);

            // Just for debugging purposes.
            if (props.getProperty("debug", "false").equalsIgnoreCase("true")) {
                for (Map.Entry<String, TagCategory> entry : tcHM.entrySet()) {
                    StringBuilder sb = new StringBuilder();
                    sb.append(entry.getKey());
                    sb.append(": ");
                    Iterator<String> iter = entry.getValue().getTags();
                    while (iter.hasNext()) {
                        sb.append(iter.next());
                        sb.append("; ");
                    }
                    logger.finest(sb.toString());
                }
            }
        }

        // special tags handling
        String strSpecialTagMap = props.getProperty("specialTagMap");
        if (strSpecialTagMap != null) {
            tagSpecialHandler =
                    new TagSpecialHandler(docBuilder.parse(strSpecialTagMap), xp);
        }

        String strDummyPicture = props.getProperty("dummyPicture");
        if (strDummyPicture != null) {
            File dummyFile = new File(strDummyPicture);
            if (!dummyFile.exists()) {
                logger.warning(dummyFile + ": does not exists.");
            } else if (!dummyFile.isFile()) {
                logger.warning(dummyFile + ": not a regular file.");
            } else if (!dummyFile.canRead()) {
                logger.warning(dummyFile + ": not readable.");
            } else {
                dummyPicture = dummyFile;
            }
        }

        String strDummyGraphic = props.getProperty("dummyGraphic");
        if (strDummyGraphic != null) {
            File dummyFile = new File(strDummyGraphic);
            if (!dummyFile.exists()) {
                logger.warning(dummyFile + ": does not exists.");
            } else if (!dummyFile.isFile()) {
                logger.warning(dummyFile + ": not a regular file.");
            } else if (!dummyFile.canRead()) {
                logger.warning(dummyFile + ": not readable.");
            } else {
                dummyGraphic = dummyFile;
            }
        }

        logger.fine("Using class " + xpf.getClass().getCanonicalName() + " as XPathFactory.");
    }

    /**
     * Thread main loop.
     */
    public void run() {
        logger.entering(loggerName, "run");

        while (true) {
            try {
                //QueueElement qe = inq.poll();
                WebQueueElement qe = (WebQueueElement) inq.poll();
                if (qe == null) {
                    Thread.currentThread().sleep(200);
                    continue;
                } else {
                    Document doc = qe.getDocument();
                    long startMillis = System.currentTimeMillis();

                    // retrieve Newsroom tags and finalize text formatting
                    handleTags(doc);

                    // check if this is a STORY or an IMAGE/GRAPHIC xml file
                    boolean isImageXML = ((Boolean) xp.evaluate("/nitf/head/docdata/definition/@type!='STORY'",
                            doc, XPathConstants.BOOLEAN)).booleanValue();

                    // include image files
                    // 20120607 jpm: process photo nodes of both STORY and IMAGE xml files
                    // NodeList nl = (NodeList) xp.evaluate("/nitf[head/docdata/definition/@type='IMAGE']/body/photo", doc, XPathConstants.NODESET);
                    NodeList nl = (NodeList) xp.evaluate("/nitf/body/photo | /nitf/body/graphic", doc, XPathConstants.NODESET);
                    for (int i = 0; i < nl.getLength(); i++) {
                        int rotationAngle = getRotationAngle(nl.item(i));
                        Dimension dimension = getDimension(nl.item(i));
                        Rectangle cropRect = getCropRect(nl.item(i));
                        boolean flipX = getFlipX(nl.item(i));
                        boolean flipY = getFlipY(nl.item(i));
                        String strHighResPath = getHighResImagePath(nl.item(i));
                        String strMedResPath = getMedResImagePath(nl.item(i));
                        String strLowResPath = getLowResImagePath(nl.item(i));

                        String objTypeStr;
                        if (nl.item(i).getNodeName().equals("graphic")) {
                            objTypeStr = "graphic";
                        } else {
                            objTypeStr = "image";
                        }

                        // get the destination file name
                        String strThumbnailTarget = null;
                        String strLowResTarget = null;
                        Node node = (Node) xp.evaluate("./" + objTypeStr + "_thumbnail", nl.item(i), XPathConstants.NODE);
                        if (node != null) {
                            strThumbnailTarget = node.getTextContent();
                        }
                        node = (Node) xp.evaluate("./" + objTypeStr + "_low", nl.item(i), XPathConstants.NODE);
                        if (node != null) {
                            strLowResTarget = node.getTextContent();
                        }

                        File highResFile = null;
                        File medResFile = null;
                        File lowResFile = null;
                        if (strHighResPath != null) {
                            highResFile = new File(strHighResPath);
                        }
                        if (strMedResPath != null) {
                            medResFile = new File(strMedResPath);
                        }
                        if (strLowResPath != null) {
                            lowResFile = new File(strLowResPath);
                        }

                        if (highResFile != null && !highResFile.exists()) {
                            String strSuffix = null;
                            int pos = highResFile.getName().lastIndexOf('.');
                            if (pos > 0) {
                                strSuffix = highResFile.getName().substring(pos);
                            }
                            if (strSuffix.equalsIgnoreCase(".jpg")) {
                                highResFile = dummyPicture;
                                logger.info("Using dummy picture " + dummyPicture + " for hires.");
                            } else {
                                highResFile = dummyGraphic;
                                logger.info("Using dummy graphic " + dummyGraphic + " for hires.");
                            }
                        }
                        if (medResFile != null && !medResFile.exists() && dummyPicture != null) {
                            medResFile = dummyPicture;
                            logger.info("Using dummy picture " + dummyPicture + " for lowres.");
                        }
                        if (lowResFile != null && !lowResFile.exists() && dummyPicture != null) {
                            lowResFile = dummyPicture;
                            logger.info("Using dummy picture " + dummyPicture + " for thumbnail.");
                        }

                        if (props.getProperty("useOriginalAsLowres", "false").equalsIgnoreCase("true")) {
                            String strSuffix = null;
                            int pos = highResFile.getName().lastIndexOf('.');
                            if (pos > 0) {
                                strSuffix = highResFile.getName().substring(pos);
                            }
                            if (!strSuffix.equalsIgnoreCase(".jpg")) {
                                pos = strLowResTarget.lastIndexOf('.');
                                if (pos > 0) {
                                    strLowResTarget = strLowResTarget.substring(0, pos) + strSuffix.toLowerCase();
                                    Element e = (Element) xp.evaluate("./" + objTypeStr + "_low", nl.item(i), XPathConstants.NODE);
                                    if (e != null) {
                                        e.setTextContent(strLowResTarget);
                                    }
                                }
                            }
                            if (isImageXML) {
                                if (cropRect != null && props.getProperty("cropLowres", "true").equalsIgnoreCase("true") && strSuffix.equalsIgnoreCase(".jpg")) {
                                    byte[] imageBytes = crop(props, highResFile, medResFile, cropRect, dimension, rotationAngle, flipX, flipY);
                                    qe.addFileTarget(strLowResTarget, imageBytes);
                                } else {
                                    //ByteArrayOutputStream out = new ByteArrayOutputStream((int) highResFile.length());
                                    //FileInputStream in = new FileInputStream(highResFile);
                                    //in.close();
                                    //streamCopy(in, out);
                                    //qe.addFileTarget(strLowResTarget, out.toByteArray());
                                    //out.close();
                                    qe.addFileTarget(strLowResTarget, highResFile);
                                }
                                logger.fine("Added file target " + strLowResTarget + ".");
                            }
                        } else {
                            if (isImageXML) {
                                if (cropRect != null && props.getProperty("cropLowres", "true").equalsIgnoreCase("true")) {
                                    byte[] imageBytes = crop(props, medResFile, medResFile, cropRect, dimension, rotationAngle, flipX, flipY);
                                    qe.addFileTarget(strLowResTarget, imageBytes);
                                } else {
                                    ByteArrayOutputStream out = new ByteArrayOutputStream((int) medResFile.length());
                                    FileInputStream in = new FileInputStream(medResFile);
                                    streamCopy(in, out);
                                    in.close();
                                    qe.addFileTarget(strLowResTarget, out.toByteArray());
                                    out.close();
                                }
                                logger.fine("Added file target " + strLowResTarget + ".");
                            }
                        }

                        if (props.getProperty("omitThumbnail", "false").equalsIgnoreCase("false")) {
                            if (isImageXML) {
                                if (cropRect != null && props.getProperty("cropThumbnail", "true").equalsIgnoreCase("true")) {
                                    byte[] imageBytes = crop(props, lowResFile, medResFile, cropRect, dimension, rotationAngle, flipX, flipY);
                                    qe.addFileTarget(strThumbnailTarget, imageBytes);
                                } else {
                                    ByteArrayOutputStream out = new ByteArrayOutputStream((int) lowResFile.length());
                                    FileInputStream in = new FileInputStream(lowResFile);
                                    streamCopy(in, out);
                                    in.close();
                                    qe.addFileTarget(strThumbnailTarget, out.toByteArray());
                                    out.close();
                                }
                                logger.fine("Added file target " + strThumbnailTarget + ".");
                            }
                        } else {
                            Element e = (Element) xp.evaluate("./" + objTypeStr + "_thumbnail", nl.item(i), XPathConstants.NODE);
                            if (e != null) {
                                e.setTextContent("");
                            }
                        }
                    }

                    // Get rid of processing instructions.
                    nl = (NodeList) xp.evaluate("//processing-instruction()",
                            doc, XPathConstants.NODESET);
                    for (int i = 0; i < nl.getLength(); i++) {
                        String strTarget = ((ProcessingInstruction) nl.item(i)).getTarget();
                        if (strTarget.equals("file-name")) {
                            continue;
                        }
                        nl.item(i).getParentNode().removeChild(nl.item(i));
                    }

                    // Get rid of comments.
                    nl = (NodeList) xp.evaluate("//comment()", doc, XPathConstants.NODESET);
                    for (int i = 0; i < nl.getLength(); i++) {
                        nl.item(i).getParentNode().removeChild(nl.item(i));
                    }

                    boolean bQStatus = false;
                    int retries = 120;
                    while (!(bQStatus = outq.offer(qe)) && retries-- > 0) {
                        Thread.currentThread().sleep(1000);
                    }
                    if (!bQStatus) {
                        logger.warning("Could not queue document.");
                    } else if ((QRETRIES - retries) > 0) {
                        logger.finest("Document queued after " + (QRETRIES - retries) + " retries.");
                    }

                    long endMillis = System.currentTimeMillis();
                    logger.info("Document processed in " + (endMillis - startMillis) + "ms.");
                }
            } catch (InterruptedException e) {
                logger.log(Level.INFO, Thread.currentThread().getName(), e);
            } catch (Exception e) {
                logger.log(Level.WARNING, Thread.currentThread().getName(), e);
            }
        }
    }

    private void handleTags(Document doc) throws XPathExpressionException {
        String strContent = null;
        String strHeadline = null;
        String strByline = null;
        String strEmail = null;
        String strKicker = null;
        String strTitle = null;
        String strPlace = null;
        String strTwitter = null;
        String strSubHeadline = null;
        String strHeader = null;
        String strVideoLink = null;
        String strAbstract = null;

        StringBuilder sbByline = new StringBuilder();
        StringBuilder sbTitle = new StringBuilder();
        StringBuilder sbPlace = new StringBuilder();
        StringBuilder sbEmail = new StringBuilder();
        StringBuilder sbTwitter = new StringBuilder();

        // content
        NodeList nl = (NodeList) xp.evaluate("//body/content", doc, XPathConstants.NODESET);
        for (int i = 0; i < nl.getLength(); i++) {
            Element e = (Element) nl.item(i);
            String strText = e.getTextContent();
            if (tagSpecialHandler != null) {
                strText = tagSpecialHandler.ProcessSpecialTags(strText);    // special tags
            }
            StringBuilder sbText = new StringBuilder(strText);
            getDesignations(sbText, sbByline, sbTitle, sbPlace, sbEmail, sbTwitter, true);
            strText = sbText.toString();     // get remaining text
            strText = strText.replaceAll("\\[.*?\\]", "").trim();   // remove tags
            while (strText.startsWith(LINEBREAK)) {
                strText = strText.substring(LINEBREAK.length()).trim();
            }
            if (i > 0) {
                // we have more than a single content
                strContent += LINEBREAK + strText.trim();
                e.getParentNode().removeChild(e);   // get rid of this node
            } else {
                strContent = strText.trim();
            }
        }
        if (nl.getLength() == 0) {
            // add a content element
            Node n = (Node) xp.evaluate("/nitf[head/docdata/definition/@type='STORY']/body", doc, XPathConstants.NODE);
            if (n != null) {
                n.appendChild(doc.createElement("content"));
            }
        }

        // check if we have a headline
        nl = (NodeList) xp.evaluate("//body/h1", doc, XPathConstants.NODESET);
        if (nl.getLength() == 0) {
            // no headline, add one
            nl = (NodeList) xp.evaluate("/nitf[head/docdata/definition/@type='STORY']/body/content", doc, XPathConstants.NODESET);
            if (nl.getLength() > 0) {
                nl.item(0).getParentNode().insertBefore(doc.createElement("h1"), nl.item(0));
            }
        }

        // check if we have a subheadline
        nl = (NodeList) xp.evaluate("//body/h2", doc, XPathConstants.NODESET);
        if (nl.getLength() == 0) {
            // no subheadline, add one
            nl = (NodeList) xp.evaluate("/nitf[head/docdata/definition/@type='STORY']/body/content", doc, XPathConstants.NODESET);
            if (nl.getLength() > 0) {
                nl.item(0).getParentNode().insertBefore(doc.createElement("h2"), nl.item(0));
            }
        }

        // headline
        nl = (NodeList) xp.evaluate("//body/h1", doc, XPathConstants.NODESET);
        for (int i = 0; i < nl.getLength(); i++) {
            Element e = (Element) nl.item(i);
            String strText = e.getTextContent();
            if (tagSpecialHandler != null) {
                strText = tagSpecialHandler.ProcessSpecialTags(strText);    // special tags
            }
            strText = strText.replaceAll("\\[.*?\\]", "").trim();   // remove tags
            if (i > 0) {
                // we have more than a single headline
                strHeadline += " " + strText.trim();
                e.getParentNode().removeChild(e);   // get rid of this node
            } else {
                strHeadline = strText;
            }
        }

        // kicker
        nl = (NodeList) xp.evaluate("//body/kick", doc, XPathConstants.NODESET);
        for (int i = 0; i < nl.getLength(); i++) {
            Element e = (Element) nl.item(i);
            String strText = e.getTextContent();
            if (tagSpecialHandler != null) {
                strText = tagSpecialHandler.ProcessSpecialTags(strText);    // special tags
            }
            strText = strText.replaceAll("\\[.*?\\]", "").trim();   // remove tags
            if (i > 0) {
                // we have more than a single kicker
                strKicker += " " + strText.trim();
                e.getParentNode().removeChild(e);   // get rid of this node
            } else {
                strKicker = strText;
            }
        }

        // teaser component of headline
        nl = (NodeList) xp.evaluate("//body/hteaser", doc, XPathConstants.NODESET);
        for (int i = 0; i < nl.getLength(); i++) {
            Element e = (Element) nl.item(i);
            String strText = e.getTextContent();
            if (tagSpecialHandler != null) {
                strText = tagSpecialHandler.ProcessSpecialTags(strText);    // special tags
            }
            strText = strText.replaceAll("\\[.*?\\]", "").trim();   // remove tags
            if (strSubHeadline == null) {
                strSubHeadline = strText;
            } else {
                strSubHeadline += " " + strText;
            }
            e.getParentNode().removeChild(e);
        }

        // subtitle component of headline
        nl = (NodeList) xp.evaluate("//body/hsubtitle", doc, XPathConstants.NODESET);
        for (int i = 0; i < nl.getLength(); i++) {
            Element e = (Element) nl.item(i);
            String strText = e.getTextContent();
            if (tagSpecialHandler != null) {
                strText = tagSpecialHandler.ProcessSpecialTags(strText);    // special tags
            }
            strText = strText.replaceAll("\\[.*?\\]", "").trim();   // remove tags
            if (strSubHeadline == null) {
                strSubHeadline = strText;
            } else {
                strSubHeadline += " " + strText;
            }
            e.getParentNode().removeChild(e);
        }

        // summary
        nl = (NodeList) xp.evaluate("//body/summary", doc, XPathConstants.NODESET);
        for (int i = 0; i < nl.getLength(); i++) {
            Element e = (Element) nl.item(i);
            String strText = e.getTextContent();
            if (tagSpecialHandler != null) {
                strText = tagSpecialHandler.ProcessSpecialTags(strText);    // special tags
            }
            StringBuilder sbText = new StringBuilder(strText);
            getDesignations(sbText, sbByline, sbTitle, sbPlace, sbEmail, sbTwitter, false);    // do not remove extracted text from summary
            strText = sbText.toString();     // get remaining text            
            strText = strText.replaceAll("\\[.*?\\]", "").trim();   // remove tags
            if (strSubHeadline == null) {
                strSubHeadline = strText;
            } else {
                strSubHeadline += " " + strText;
            }
            e.getParentNode().removeChild(e);
        }

        // header
        nl = (NodeList) xp.evaluate("//body/header", doc, XPathConstants.NODESET);
        for (int i = 0; i < nl.getLength(); i++) {
            Element e = (Element) nl.item(i);
            String strText = e.getTextContent();
            if (tagSpecialHandler != null) {
                strText = tagSpecialHandler.ProcessSpecialTags(strText);    // special tags
            }
            StringBuilder sbText = new StringBuilder(strText);
            getDesignations(sbText, sbByline, sbTitle, sbPlace, sbEmail, sbTwitter, true);
            strText = sbText.toString();     // get remaining text
            strText = strText.replaceAll("\\[.*?\\]", "").trim();   // remove tags
            if (strHeader == null) {
                strHeader = strText;
            } else {
                strHeader += " " + strText;
            }
            e.getParentNode().removeChild(e);
        }

        // photo/graphic
        NodeList nl2 = (NodeList) xp.evaluate("//body/photo | //body/graphic", doc, XPathConstants.NODESET);
        for (int j = 0; j < nl2.getLength(); j++) {
            String strCaption = null;
            String strPhotoCredit = null;
            // caption
            nl = (NodeList) xp.evaluate("caption", nl2.item(j), XPathConstants.NODESET);
            for (int i = 0; i < nl.getLength(); i++) {
                Element e = (Element) nl.item(i);
                String strText = e.getTextContent();
                if (tagSpecialHandler != null) {
                    strText = tagSpecialHandler.ProcessSpecialTags(strText);    // special tags
                }
                StringBuilder sbText = new StringBuilder(strText);
                String strText2 = getTaggedTextByCategory(tcHM, "PHOTO_CREDIT", sbText);
                if (strText2 == null) {
                    strText2 = "";
                }
                if (strPhotoCredit != null) {
                    strPhotoCredit += " " + strText2;
                } else {
                    strPhotoCredit = strText2;
                }
                strText = sbText.toString();    // get remaining text
                if (strText == null) {
                    strText = "";
                }
                if (strCaption != null) {
                    strCaption += " " + strText;
                } else {
                    strCaption = strText;
                }
            }
            if (nl.getLength() > 0) {
                Element e = (Element) nl.item(0);   // first caption element
                strCaption = strCaption.replaceAll("\\[.*?\\]", "");    // remove tags
                strCaption = strCaption.replace(LINEBREAK, " ").replaceAll("\\ +", " ").trim();
                strCaption = replaceReservedCharMarkers(strCaption);
                e.setTextContent(strCaption.trim());

                Element ee = doc.createElement("copyright");    // new copyright element
                strPhotoCredit = strPhotoCredit.replaceAll("\\[.*?\\]", "");    // remove tags
                strPhotoCredit = strPhotoCredit.replace(LINEBREAK, " ").replaceAll("\\ +", " ").trim();
                strPhotoCredit = replaceReservedCharMarkers(strPhotoCredit);
                ee.setTextContent(strPhotoCredit.trim());
                e.getParentNode().appendChild(ee);

                // remove other caption elements, if there are any
                for (int i = 1; i < nl.getLength(); i++) {
                    nl.item(i).getParentNode().removeChild(nl.item(i));
                }
            }
        }

        // set person
        strByline = sbByline.toString();
        if (strByline != null) {
            strByline = strByline.replace(LINEBREAK, " ").replaceAll("\\ +", " ").trim();
            nl = (NodeList) xp.evaluate("//body/person", doc, XPathConstants.NODESET);
            if (nl.getLength() == 1) {
                strByline = replaceReservedCharMarkers(strByline);
                strByline = strByline.replaceAll("\\ *,\\ *", ",");
                ((Element) nl.item(0)).setTextContent(strByline.trim());
            }
        }
        
        // set byline - combination of byline, email, twitter and title
        strByline = getByline(sbByline, sbTitle, sbEmail, sbTwitter);
        if (strByline != null) {
            strByline = strByline.replace(LINEBREAK, " ").replaceAll("\\ +", " ").trim();
            nl = (NodeList) xp.evaluate("//body/byline", doc, XPathConstants.NODESET);
            if (nl.getLength() == 1) {
                strByline = replaceReservedCharMarkers(strByline);
                ((Element) nl.item(0)).setTextContent(strByline.trim());
            }
        }        

        // set title
        strTitle = sbTitle.toString();
        if (strTitle != null) {
            strTitle = strTitle.replace(LINEBREAK, " ").replaceAll("\\ +", " ").trim();
            nl = (NodeList) xp.evaluate("//body/title", doc, XPathConstants.NODESET);
            if (nl.getLength() == 1) {
                strTitle = replaceReservedCharMarkers(strTitle);
                strTitle = strTitle.replaceAll("\\ *,\\ *", ",");
                ((Element) nl.item(0)).setTextContent(strTitle.trim());
            }
        }

        // set place
        strPlace = sbPlace.toString();
        if (strPlace != null) {
            strPlace = strPlace.replace(LINEBREAK, " ").replaceAll("\\ +", " ").trim();
            nl = (NodeList) xp.evaluate("//body/country", doc, XPathConstants.NODESET);
            if (nl.getLength() == 1) {
                strPlace = replaceReservedCharMarkers(strPlace);
                strPlace = strPlace.replaceAll("\\ *,\\ *", ",");
                ((Element) nl.item(0)).setTextContent(strPlace.trim());
            }
        }

        // set email
        strEmail = sbEmail.toString();
        if (strEmail != null) {
            strEmail = strEmail.replace(LINEBREAK, " ").replaceAll("\\ +", " ").trim();
            nl = (NodeList) xp.evaluate("//body/series", doc, XPathConstants.NODESET);
            if (nl.getLength() == 1) {
                strEmail = replaceReservedCharMarkers(strEmail);
                strEmail = strEmail.replaceAll("\\ *,\\ *", ",");
                ((Element) nl.item(0)).setTextContent(strEmail.trim());
            }
        }
        
        // set twitter
        strTwitter = sbTwitter.toString();
        if (strTwitter != null) {
            strTwitter = strTwitter.replace(LINEBREAK, " ").replaceAll("\\ +", " ").trim();
            nl = (NodeList) xp.evaluate("//body/twitter", doc, XPathConstants.NODESET);
            if (nl.getLength() == 1) {
                strTwitter = replaceReservedCharMarkers(strTwitter);
                strTwitter = strTwitter.replaceAll("\\ *,\\ *", ",");
                ((Element) nl.item(0)).setTextContent(strTwitter.trim());
            }
        }        

        // set kick
        if (strKicker != null) {
            strKicker = strKicker.replace(LINEBREAK, " ").replaceAll("\\ +", " ").trim();
            nl = (NodeList) xp.evaluate("//body/kick", doc, XPathConstants.NODESET);
            if (nl.getLength() == 1) {
                strKicker = replaceReservedCharMarkers(strKicker);
                ((Element) nl.item(0)).setTextContent(strKicker.trim());
            }
        }

        // set headline
        if (strHeadline != null) {
            strHeadline = strHeadline.replace(LINEBREAK, " ").replaceAll("\\ +", " ").trim();
            nl = (NodeList) xp.evaluate("//body/h1", doc, XPathConstants.NODESET);
            if (nl.getLength() > 0) {
                strHeadline = replaceReservedCharMarkers(strHeadline);
                ((Element) nl.item(0)).setTextContent(strHeadline.trim());
            }
        }

        // set subheadline
        if (strSubHeadline != null) {
            strSubHeadline = strSubHeadline.replace(LINEBREAK, " ").replaceAll("\\ +", " ").trim();
            nl = (NodeList) xp.evaluate("//body/h2", doc, XPathConstants.NODESET);
            if (nl.getLength() == 1) {
                strSubHeadline = replaceReservedCharMarkers(strSubHeadline);
                nl.item(0).setTextContent(strSubHeadline.trim());
            }
        }

        // set content
        if (strContent != null || strHeader != null) {
            if (strContent == null) {
                strContent = "";
            }
            if (strHeader != null) {
                strContent = strContent.trim() + LINEBREAK + strHeader.trim(); // append header to content
            }
            strContent = "<p>" + strContent; //beginning para
            strContent = strContent.replace(LINEBREAK, "</p><p>"); // replace line breaks with para breaks
            strContent = strContent + "</p>"; //ending para

            nl = (NodeList) xp.evaluate("//body/content", doc, XPathConstants.NODESET);
            if (nl.getLength() > 0) {
                strContent = replaceReservedCharMarkers(strContent);
                ((Element) nl.item(0)).setTextContent(strContent.trim());
            }
        }

        // video
        nl = (NodeList) xp.evaluate("//body/video", doc, XPathConstants.NODESET);
        if (nl.getLength() > 0) {
            for (int i = 0; i < nl.getLength(); i++) {
                Element e = (Element) nl.item(i);
                String strText = e.getTextContent();
                if (tagSpecialHandler != null) {
                    strText = tagSpecialHandler.ProcessSpecialTags(strText);    // special tags
                }
                strText = strText.replaceAll("\\[.*?\\]", "").trim();   // remove tags
                if (i > 0) {
                    // we have more than one
                    strVideoLink += " " + strText.trim();
                    e.getParentNode().removeChild(e);   // get rid of this node
                } else {
                    strVideoLink = strText;
                }
            }
            if (strVideoLink != null) {
                strVideoLink = strVideoLink.replace(LINEBREAK, " ").replaceAll("\\ +", " ").trim();
                nl = (NodeList) xp.evaluate("//body/video", doc, XPathConstants.NODESET);
                if (nl.getLength() == 1) {
                    strVideoLink = replaceReservedCharMarkers(strVideoLink);
                    nl.item(0).setTextContent(strVideoLink.trim());
                }
            }
        } else {
            Node n = (Node) xp.evaluate("/nitf[head/docdata/definition/@type='STORY']/body", doc, XPathConstants.NODE);
            if (n != null) {
                n.appendChild(doc.createElement("video"));
            }
        }

        // abstract (web summary)
        nl = (NodeList) xp.evaluate("//body/abstract", doc, XPathConstants.NODESET);
        if (nl.getLength() > 0) {
            for (int i = 0; i < nl.getLength(); i++) {
                Element e = (Element) nl.item(i);
                String strText = e.getTextContent();
                if (tagSpecialHandler != null) {
                    strText = tagSpecialHandler.ProcessSpecialTags(strText);    // special tags
                }
                strText = strText.replaceAll("\\[.*?\\]", "").trim();   // remove tags
                if (i > 0) {
                    // we have more than one
                    strAbstract += LINEBREAK + strText.trim();
                    e.getParentNode().removeChild(e);   // get rid of this node
                } else {
                    strAbstract = strText;
                }
            }
            if (strAbstract != null) {
                strAbstract = strAbstract.replaceAll("\\ +", " ").trim();
                strAbstract = "<p>" + strAbstract; //beginning para
                strAbstract = strAbstract.replace(LINEBREAK, "</p><p>"); // replace line breaks with para breaks
                strAbstract = strAbstract + "</p>"; //ending para                
                nl = (NodeList) xp.evaluate("//body/abstract", doc, XPathConstants.NODESET);
                if (nl.getLength() == 1) {
                    strAbstract = replaceReservedCharMarkers(strAbstract);
                    nl.item(0).setTextContent(strAbstract.trim());
                }
            }
        } else {
            Node n = (Node) xp.evaluate("/nitf[head/docdata/definition/@type='STORY']/body", doc, XPathConstants.NODE);
            if (n != null) {
                n.appendChild(doc.createElement("abstract"));
            }
        }
    }

    private void getDesignations(StringBuilder sbSourceText,
            StringBuilder sbByline, StringBuilder sbTitle, StringBuilder sbPlace, 
            StringBuilder sbEmail, StringBuilder sbTwitter,
            boolean removeExtractedText) {
        String s = null;
        s = getTaggedTextByCategory(tcHM, "BYLINE", sbSourceText, removeExtractedText, ",", ",");
        if (s != null) {
            s = s.replace(LINEBREAK, " ").trim();
            // remove unnecessary text
            String[] bylineMarkers = props.getProperty("byline.markers").split(",");
            for (int i = 0; i < bylineMarkers.length; i++) {
                if (s.toUpperCase().startsWith(bylineMarkers[i].toUpperCase())) {
                    s = s.substring(bylineMarkers[i].length()).trim();
                }
            }
            s = s.replaceAll("\\s+(?i)and\\s+", ", ");  // replace " and " with a comma
            if (sbByline.length() > 0 && s.length() > 0) {
                sbByline.append(", ");
            }
            sbByline.append(s);
        }
        s = getTaggedTextByCategory(tcHM, "BYLINE_TITLE", sbSourceText, removeExtractedText, ",", ",");
        if (s != null) {
            s = s.replace(LINEBREAK, " ").trim();
            if (sbTitle.length() > 0 && s.length() > 0) {
                sbTitle.append(", ");
            }
            sbTitle.append(s);
        }
        s = getTaggedTextByCategory(tcHM, "BYLINE_PLACE", sbSourceText, removeExtractedText, ",", ",");
        if (s != null) {
            s = s.replace(LINEBREAK, " ").trim();
            // remove unnecessary text
            if (s.toUpperCase().startsWith("IN ")) {
                s = s.substring(3);
            }
            if (sbPlace.length() > 0 && s.length() > 0) {
                sbPlace.append(", ");
            }
            sbPlace.append(s);
        }
        s = getTaggedTextByCategory(tcHM, "EMAIL", sbSourceText, removeExtractedText, ",", ",");
        if (s != null) {
            s = s.replace(LINEBREAK, " ").trim();
            if (sbEmail.length() > 0 && s.length() > 0) {
                sbEmail.append(", ");
            }
            sbEmail.append(s);
        }
        s = getTaggedTextByCategory(tcHM, "TWITTER", sbSourceText, removeExtractedText, ",", ",");
        if (s != null) {
            s = s.replace(LINEBREAK, " ").trim();
            if (sbTwitter.length() > 0 && s.length() > 0) {
                sbTwitter.append(", ");
            }
            sbTwitter.append(s);
        }        
    }
    
    private String getByline(StringBuilder sbByline, StringBuilder sbTitle, 
            StringBuilder sbEmail, StringBuilder sbTwitter) {
        // person string: [name1], [email1], [twitter1], [title1] | [name2], [email2], [twitter2], [title2] | ...
        String value = "";
        
        String[] bylines = sbByline.toString().split(",");
        String[] emails = sbEmail.toString().split(",");
        String[] twitters = sbTwitter.toString().split(",");
        String[] titles = sbTitle.toString().split(",");
        
        for (int i = 0; i < bylines.length; i++) {
            String byline = "";
            String email = "";
            String twitter = "";
            String title = "";
            
            if (i > 0) { value += "|"; }
            
            byline = bylines[i].trim();
            if (emails.length > i) { email = emails[i].trim(); }
            if (twitters.length > i) { twitter = twitters[i].trim(); }
            if (titles.length > i) { title = titles[i].trim(); }
            
            value += (byline + "," + email + "," + twitter + "," + title);
        }
        
        return value;
    }
    
    
    private Properties props = null;
    private AbstractQueue<QueueElement> inq = null;
    private AbstractQueue<QueueElement> outq = null;
    private HashMap<String, TagCategory> tcHM = new HashMap<String, TagCategory>();
    private DocumentBuilderFactory docBuilderFactory = null;
    private DocumentBuilder docBuilder = null;
    private XPathFactory xpf = XPathFactory.newInstance();
    private XPath xp = xpf.newXPath();
    private File dummyPicture = null;
    private File dummyGraphic = null;
    private TagSpecialHandler tagSpecialHandler = null;
    
    private final static int QRETRIES = 120;
    private final String LINEBREAK = "<br/>";
    
    private static final String loggerName = WorkerImpl.class.getName();
    private static final Logger logger = Logger.getLogger(loggerName);
}
