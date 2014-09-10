/*
 * File:    WorkerImpl.java
 *
 * Copyright (c) 2012,  Atex Media Command GmbH
 *                      Kurhessenstrasse 13
 *                      64546 Moerfelden-Walldorf
 *                      Germany
 *
 * Audit:
 *         20130320     jpm get byline markers (e.g. by, oleh, report:) that need to be removed from the properties file* 
 *         20120807     jpm 1. no need to extract byline place and byline email
 *         20120727     jpm 1. separate multiple captions with line breaks
 *                          2. add check for existence of page pdf before performing export of page and its contents
 *         20120724     jpm extend AbstractWorker class
 *         20120722     jpm 1. add handling for kickers - include in body text
 *                          2. remove linebreaks from summary, header text to be included in body text
 *                          3. look for byline, etc from summary objects in addition to text and header objects
 *                          4. teaser and subtitle components of headline go to summary
 *         20120719     jpm 1. remove linebreaks from captions; do not include photo credits in exported caption
 *                          2. handling for [ and ] reserved chars (tag markers)
 * v01.00  05-jun-2012  st  Initial version.
 * v00.00  17-feb-2012  st  Created.
 */

package de.atex.h11.custom.sph.export.nica;

import de.atex.h11.custom.sph.export.generic.*;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 *
 * @author tstuehler
 */
public class WorkerImpl extends AbstractWorker {
    
    public WorkerImpl () {}
    
    @Override
    public void init (AbstractQueue<QueueElement> inq, 
                    AbstractQueue<QueueElement> outq, Properties props) 
            throws ParserConfigurationException, 
                   TransformerConfigurationException, 
                   FileNotFoundException, IOException {
        this.inq = inq;
        this.outq = outq;
        this.props = props;

        // Prepare a document builder.
        docBuilderFactory = DocumentBuilderFactory.newInstance();
        docBuilderFactory.setNamespaceAware(true);
        docBuilder = docBuilderFactory.newDocumentBuilder();
        
        validator = new DTDValidator();
        
        String strStyleFile = props.getProperty("styleFile");
        if (strStyleFile != null) {
            tcHM = StyleReader.getTagCategories(strStyleFile);
            
            // Just for debugging purposes.
            if (props.getProperty("debug", "false").equalsIgnoreCase("true")) {
                for (Map.Entry<String,TagCategory> entry : tcHM.entrySet()) {
                    StringBuffer sb = new StringBuffer();
                    sb.append(entry.getKey() + ": ");
                    Iterator<String> iter =entry.getValue().getTags();
                    while (iter.hasNext()) {
                        sb.append(iter.next() + "; ");
                    }
                    logger.finest(sb.toString());
                }
            }
        }
    }

    
    @Override
    public void run () {
        logger.entering(loggerName, "run");

        while (true) {
            try {
                NewsMLQueueElement qe = (NewsMLQueueElement) dequeue(inq);
                Document doc = qe.getDocument();
                
                if (isReady(doc)) {
                    processDoc(qe, doc);
                    enqueue(outq, qe);
                }
            } catch (InterruptedException e) {
                logger.log(Level.INFO, Thread.currentThread().getName(), e);
            } catch (Exception e) {
                logger.log(Level.WARNING, Thread.currentThread().getName(), e);
            }
        }
    }
    
    private boolean isReady(Document doc)
            throws Exception {
        
        if (props.getProperty("checkPagePDFExists", "false").equalsIgnoreCase("true")) {
            // check if PDF for the page exists
            String strPagePDF = "";
            NodeList nl = (NodeList) xp.evaluate("//NewsItem[NewsManagement/NewsItemType/@FormalName='Page']"
                        + "/NewsComponent/NewsComponent/ContentItem/@Href",
                        doc, XPathConstants.NODESET);
            if (nl.getLength() == 1)
                strPagePDF = nl.item(0).getTextContent();
            strPagePDF = props.getProperty("xslt.param.pagePDFPath") + strPagePDF;
            File pagePDF = new File(strPagePDF);
            if (!pagePDF.exists()) {
                logger.warning("Page PDF " + strPagePDF + " does not exist. Page and its contents will not be exported.");
                return false;
            }
            else 
                logger.finer("Page PDF " + strPagePDF + " exists.");
        }

        return true;
    }
    
    private void processDoc(NewsMLQueueElement qe, Document doc) 
            throws InterruptedException, Exception {
        CheckMemoryUsage("processDoc start");
        
        // Process text items.
        NodeList nl = (NodeList) xp.evaluate(
                    "//NewsItem[NewsManagement/NewsItemType/@FormalName='Text']",
                    doc, XPathConstants.NODESET);
        for (int i = 0; i < nl.getLength(); i++) {
            Document storyDoc = docBuilder.newDocument();
            Element rootElement = storyDoc.createElement("body");
            storyDoc.appendChild(rootElement);

            StringBuilder sbByline = new StringBuilder(); // note: byline info can be found in text, summary or header objects

            // Process NewsLine elements.
            NodeList nl2 = null;

            nl2 = (NodeList) xp.evaluate(
                    ".//NewsLine[NewsLineType/@FormalName='Kicker']/NewsLineText",
                    nl.item(i), XPathConstants.NODESET);
            String strKicker = "";
            for (int j = 0; j < nl2.getLength(); j++) {
                String strText = ((Element) nl2.item(j)).getTextContent();
                strText = strText.replaceAll("\\[.*?\\]", "").trim();
                strText = formatNoLineBreak(strText);
                if (strText.length() > 0)
                    strKicker += (strKicker.length() > 0 ? LINEBREAK + strText : strText);
            }              

            nl2 = (NodeList) xp.evaluate(
                    ".//NewsLine[NewsLineType/@FormalName='Headline']/NewsLineText",
                    nl.item(i), XPathConstants.NODESET);
            String strHeadline = "";
            for (int j = 0; j < nl2.getLength(); j++) {
                String strText = ((Element) nl2.item(j)).getTextContent();
                strText = strText.replaceAll("\\[.*?\\]", "").trim();
                strText = formatNoLineBreak(strText);
                if (strText.length() > 0)
                    strHeadline += (strHeadline.length() > 0 ? LINEBREAK + strText : strText);
            }

            nl2 = (NodeList) xp.evaluate(
                    ".//NewsLine[NewsLineType/@FormalName='Teaser' or @FormalName='SubTitle']/NewsLineText",
                    nl.item(i), XPathConstants.NODESET);
            String strSummary = "";
            for (int j = 0; j < nl2.getLength(); j++) {
                String strText = ((Element) nl2.item(j)).getTextContent();
                strText = strText.replaceAll("\\[.*?\\]", "").trim();
                strText = formatNoLineBreak(strText);
                if (strText.length() > 0)
                    strSummary += (strSummary.length() > 0 ? LINEBREAK + strText : strText);
            }

            nl2 = (NodeList) xp.evaluate(
                    ".//NewsLine[NewsLineType/@FormalName='Summary']/NewsLineText",
                    nl.item(i), XPathConstants.NODESET);
            for (int j = 0; j < nl2.getLength(); j++) {
                String strText = ((Element) nl2.item(j)).getTextContent();
                StringBuilder sbText = new StringBuilder(strText);
                getBylineText(sbByline, sbText, false); 
                strText = sbText.toString(); // remaining text       
                strText = strText.replaceAll("\\[.*?\\]", "").trim();
                strText = formatNoLineBreak(strText);
                if (strText.length() > 0)
                    strSummary += (strSummary.length() > 0 ? LINEBREAK + strText : strText);
            }

            nl2 = (NodeList) xp.evaluate(
                    ".//NewsLine[NewsLineType/@FormalName='Header']/NewsLineText",
                    nl.item(i), XPathConstants.NODESET);
            String strHeader = "";
            for (int j = 0; j < nl2.getLength(); j++) {
                String strText = ((Element) nl2.item(j)).getTextContent();
                StringBuilder sbText = new StringBuilder(strText);
                getBylineText(sbByline, sbText, true); 
                strText = sbText.toString(); // remaining text       
                strText = strText.replaceAll("\\[.*?\\]", "").trim();
                strText = formatNoLineBreak(strText);
                if (strText.length() > 0)
                    strHeader += (strHeader.length() > 0 ? LINEBREAK + strText : strText);
            }

            nl2 = (NodeList) xp.evaluate(
                    ".//NewsLine[NewsLineType/@FormalName='Caption']/NewsLineText",
                    nl.item(i), XPathConstants.NODESET);
            String strCaption = "";
            for (int j = 0; j < nl2.getLength(); j++) {
                String strText = ((Element) nl2.item(j)).getTextContent();
                StringBuilder sbText = new StringBuilder(strText);
                // get photo credit text, but don't export them
                String strPhotoCredit = getTaggedTextByCategory(tcHM, "PHOTO_CREDIT", sbText);  
                strText = sbText.toString(); // remaining text    
                strText = strText.replaceAll("\\[.*?\\]", "").trim();
                strText = formatNoLineBreak(strText);   // each caption should not have linebreaks
                if (strText.length() > 0)
                    strCaption += (strCaption.length() > 0 ? LINEBREAK + strText : strText);
            }

            nl2 = (NodeList) xp.evaluate(
                    ".//NewsLine[NewsLineType/@FormalName='Text']/NewsLineText",
                    nl.item(i), XPathConstants.NODESET);
            String strContent = "";
            for (int j = 0; j < nl2.getLength(); j++) {
                String strText = ((Element) nl2.item(j)).getTextContent();
                StringBuilder sbText = new StringBuilder(strText);
                getBylineText(sbByline, sbText, true); 
                strText = sbText.toString(); // remaining text  
                strText = strText.replaceAll("\\[.*?\\]", "").trim();
                while (strText.startsWith(LINEBREAK)) {
                    strText = strText.substring(LINEBREAK.length()).trim();
                }       
                if (strText.length() > 0)
                    strContent += (strContent.length() > 0 ? LINEBREAK + strText : strText);
            }

            // output elements, in required order
            if (strCaption != null && !strCaption.isEmpty()) { // combine into one Caption element
                Element e = storyDoc.createElement("Caption");
                rootElement.appendChild(e);
                breakUpText(e, strCaption.trim());  // separate multiple captions with line breaks
            }                    

            if (strHeadline != null && !strHeadline.isEmpty()) { // combine into one Headline element
                Element e = storyDoc.createElement("Headline");
                rootElement.appendChild(e);
                strHeadline = formatNoLineBreak(strHeadline);
                e.setTextContent(strHeadline.trim());
            }                    

            if (sbByline.length() > 0) {    
                Element e = storyDoc.createElement("Byline");
                rootElement.appendChild(e);
                String strByline = sbByline.toString();
                strByline = formatNoLineBreak(strByline);
                e.setTextContent(strByline.trim());
            }

            if ((strKicker != null && !strKicker.trim().isEmpty()) ||
                (strContent != null && !strContent.trim().isEmpty()) ||                            
                (strSummary != null && !strSummary.trim().isEmpty()) ||
                (strHeader != null && !strHeader.trim().isEmpty())) {
                Element e = storyDoc.createElement("BodyText");
                rootElement.appendChild(e);
                Element ee = storyDoc.createElement("StoryText");
                e.appendChild(ee);
                boolean hasStoryText = false;
                if (strKicker != null) {  // put kicker at beginning
                    if (!strKicker.trim().isEmpty()) {
                        breakUpText(ee, strKicker.trim());
                        hasStoryText = true;
                    }
                }                        
                if (strSummary != null) {  // put summary next
                    if (!strSummary.trim().isEmpty()) {
                        if (hasStoryText) {
                            ee.appendChild(storyDoc.createElement("br"));
                            ee.appendChild(storyDoc.createElement("br"));
                        }                            
                        breakUpText(ee, strSummary.trim());
                        hasStoryText = true;                                
                    }
                }
                if (strContent != null) {  // put text content
                    if (!strContent.trim().isEmpty()) {
                        if (hasStoryText) {
                            ee.appendChild(storyDoc.createElement("br"));
                            ee.appendChild(storyDoc.createElement("br"));
                        }
                        breakUpText(ee, strContent.trim());
                        hasStoryText = true;
                    }
                }
                if (strHeader != null) {    // put header at the end
                    if (!strHeader.trim().isEmpty()) {
                        if (hasStoryText) {
                            ee.appendChild(storyDoc.createElement("br"));
                            ee.appendChild(storyDoc.createElement("br"));
                        }
                        breakUpText(ee, strHeader.trim());
                    }
                }
            }

            // Validate the document.
            validator.validate(storyDoc, "de/atex/h11/custom/sph/export/nica/hermes_text.dtd");

            String strFileName = (String) xp.evaluate(
                    ".//NewsComponent/ContentItem/@Href",
                    nl.item(i), XPathConstants.STRING);
            if (strFileName != null && !strFileName.equals("")) {
                qe.addReference(new Reference<Document>(strFileName, storyDoc));
            } else {
                logger.warning("Could not find file reference in xml doc.");
            }

            // Get rid of text elements that have been copied to the text file.
            NodeList nl3 = (NodeList) xp.evaluate(
                    "//NewsComponent[Role/@FormalName='Text']/NewsLines",
                    nl.item(i), XPathConstants.NODESET);
            if (nl3.getLength() > 0) {
                nl3.item(0).getParentNode().removeChild(nl3.item(0));
            }
        }

        // Process image/graphic caption.
        nl = (NodeList) xp.evaluate(
                    "//NewsItem[NewsManagement/NewsItemType[@FormalName='Image' or @FormalName='Graphic']]",
                    doc, XPathConstants.NODESET);
        for (int i = 0; i < nl.getLength(); i++) {
            NodeList nl2 = (NodeList) xp.evaluate(
                    ".//NewsLine[NewsLineType/@FormalName='Caption']/NewsLineText",
                    nl.item(i), XPathConstants.NODESET);
            for (int j = 0; j < nl2.getLength(); j++) {
                String strText = ((Element) nl2.item(j)).getTextContent();
                StringBuilder sbText = new StringBuilder(strText);
                // get photo credit text, but don't export them
                String strPhotoCredit = getTaggedTextByCategory(tcHM, "PHOTO_CREDIT", sbText);                        
                strText = sbText.toString(); // remaining text                           
                strText = strText.replaceAll("\\[.*?\\]", "").trim();
                strText = formatNoLineBreak(strText);
                ((Element) nl2.item(j)).setTextContent(strText);
            }
        }

        // Process page pdf/image/graphic items.
        nl = (NodeList) xp.evaluate(
                    "//NewsItem[NewsManagement/NewsItemType/@FormalName='Page']"
                    + "//NewsComponent[processing-instruction('highres-imagepath')]",
                    doc, XPathConstants.NODESET);
        for (int i = 0; i < nl.getLength(); i++) {
            String strRef = (String) xp.evaluate("ContentItem/@Href", nl.item(i), XPathConstants.STRING);
            int rotationAngle = getRotationAngle(nl.item(i));
            Dimension dimension = getDimension(nl.item(i));
            Rectangle cropRect = getCropRect(nl.item(i));
            boolean flipX = getFlipX(nl.item(i));
            boolean flipY = getFlipY(nl.item(i));
            String strHighResPath = getHighResImagePath(nl.item(i));   
            String strMedResPath = getMedResImagePath(nl.item(i));
            String strLowResPath = getLowResImagePath(nl.item(i));            

            logger.finer("File info: href=" + strRef +
                    ", highResPath=" + strHighResPath + 
                    ", medResPath=" + strMedResPath + 
                    ", lowResPath=" + strLowResPath + 
                    ", dimensions=" + (dimension != null ? dimension.toString() : "") +
                    ", cropRect=" + (cropRect != null ? cropRect.toString() : "") + 
                    ", rotation=" + Integer.toString(rotationAngle) +
                    ", flipx=" + (flipX ? "true" : "false") +
                    ", flipy=" + (flipY ? "true" : "false"));

            String strSrcExt = null;
            String strDstExt = null;
            int pos = strHighResPath.lastIndexOf('.');
            if (pos > 0) strSrcExt = strHighResPath.substring(pos);
            pos = strRef.lastIndexOf('.');
            if (pos > 0) strDstExt = strRef.substring(pos);

            // crop image
            if (props.getProperty("cropImage", "false").equalsIgnoreCase("true")) {
                if (strSrcExt.equalsIgnoreCase(".jpg") && cropRect != null && dimension != null) {
                    logger.finer("Crop image: " + strHighResPath);
                    CheckMemoryUsage("crop start");
                    File highResFile = null;
                    if (strHighResPath != null) highResFile = new File(strHighResPath);
                    File medResFile = null;
                    if (strMedResPath != null) medResFile = new File(strMedResPath);
                    File tmpFile = File.createTempFile("nica", ".jpg");
                    crop(props, highResFile, medResFile, tmpFile, 
                        cropRect, dimension, rotationAngle, flipX, flipY);
                    highResFile = null;
                    medResFile = null;
                    CheckMemoryUsage("crop end");
                    tmpFile.deleteOnExit();
                    strHighResPath = tmpFile.getCanonicalPath();
                }
            }

            // check if conversion is required
            if (strSrcExt != null && strDstExt != null && !strSrcExt.equalsIgnoreCase(strDstExt)) {
                if (strDstExt.equalsIgnoreCase(".pdf")) {
                    if (strSrcExt.equalsIgnoreCase(".jpg") && props.containsKey("jpegToPdfConverterProgArgs")) {
                        File tmpFile = File.createTempFile("nica", ".pdf");
                        convertJpegToPdf(strHighResPath, tmpFile.getCanonicalPath(), props.getProperty("jpegToPdfConverterProgArgs"));
                        tmpFile.deleteOnExit();
                        strHighResPath = tmpFile.getCanonicalPath();
                    } else if (props.containsKey("dummyPage")) {
                        strHighResPath = props.getProperty("dummyPage");
                    }
                }
            }

            qe.addReference(new Reference<String>(strRef, strHighResPath));                    
        }

        // Get rid of processing instructions.
        nl = (NodeList) xp.evaluate("//processing-instruction()", doc, XPathConstants.NODESET);
        for (int i = 0; i < nl.getLength(); i++) {
            nl.item(i).getParentNode().removeChild(nl.item(i));
        }                

        // Get rid of comments.
        nl = (NodeList) xp.evaluate("//comment()", doc, XPathConstants.NODESET);
        for (int i = 0; i < nl.getLength(); i++) {
            nl.item(i).getParentNode().removeChild(nl.item(i));
        }

        // Validate the document.
        validator.validate(doc, "de/atex/h11/custom/sph/export/nica/NewsML_1.2.dtd");       
        
        CheckMemoryUsage("processDoc end");
    }
    
    private void getBylineText(StringBuilder sbByline, StringBuilder sbSourceText,
            boolean removeExtractedText) {            
        // get byline, byline title, place and remove it from the source text
        String strByline = getTaggedTextByCategory(tcHM, "BYLINE", sbSourceText, removeExtractedText);
        // 20120807 no need to extract byline place and byline email
        //String strTitle = getTaggedTextByCategory(tcHM, "BYLINE_TITLE", sbSourceText);
        //String strPlace = getTaggedTextByCategory(tcHM, "BYLINE_PLACE", sbSourceText);
        if (strByline != null) {
            logger.finer("byline str=" + strByline);
            strByline = strByline.replaceAll("\\[.*?\\]", "").replace(LINEBREAK, " ").replaceAll("\\ +", " ").trim();
            logger.finer("byline all markers=" + props.getProperty("byline.markers"));
            String[] bylineMarkers = props.getProperty("byline.markers").split(",");
            for (int i = 0; i < bylineMarkers.length; i++) {
                logger.finer("byline marker=[" + bylineMarkers[i] + "]");
                if (strByline.toUpperCase().startsWith(bylineMarkers[i].toUpperCase())) {
                    strByline = strByline.substring(bylineMarkers[i].length()).trim();
                    logger.finer("Remove byline marker. result=" + strByline);
                }
            }
            sbByline.append(strByline);
        }                           
        //if (strTitle != null) {
        //    strTitle = strTitle.replaceAll("\\[.*?\\]", "").replace(LINEBREAK, " ").replaceAll("\\ +", " ").trim();
        //    if (sbByline.length() > 0 && strTitle.length() > 0) sbByline.append(", ");
        //    sbByline.append(strTitle);
        //}
        //if (strPlace != null) {
        //    strPlace = strPlace.replaceAll("\\[.*?\\]", "").replace(LINEBREAK, " ").replaceAll("\\ +", " ").trim();
        //    if (strPlace.toUpperCase().startsWith("IN "))
        //        strPlace =  strPlace.substring(3).trim();
        //    if (sbByline.length() > 0 && strPlace.length() > 0) sbByline.append(", ");
        //    sbByline.append(strPlace);
        //}        
    }
    
    /**
     * Inject <br/> elements required by Nica.
     */
    private void breakUpText(Element e, String s) {
        String[] parts = s.split(LINEBREAK);
        for (int i = 0; i < parts.length; i++) {
            String t = parts[i].replaceAll("\\ +", " ").trim();
            t = replaceReservedCharMarkers(t);  // replace markers
            if (!t.isEmpty())
                e.appendChild(e.getOwnerDocument().createTextNode(t));
            if (i < parts.length - 1) {
                e.appendChild(e.getOwnerDocument().createElement("br"));
                e.appendChild(e.getOwnerDocument().createElement("br"));
            }
        }
    }
    
    private String formatNoLineBreak(String s) {
        s = s.replace(LINEBREAK, " ").replaceAll("\\ +", " ").trim(); 
        s = replaceReservedCharMarkers(s);   // replace markers
        return s;
    }
    
    private void CheckMemoryUsage(String s) {
        int mb = 1024*1024;
        Runtime runtime = Runtime.getRuntime();

        logger.finer(s + " -- Memory Check: Used=" + ((runtime.totalMemory() - runtime.freeMemory()) / mb)
                + ", Free=" + (runtime.freeMemory() / mb)
                + ", Total=" + (runtime.totalMemory() / mb)
                + ", Max=" + runtime.maxMemory() / mb);
    }
    
       
    private Properties props = null;
    private AbstractQueue<QueueElement> inq = null;
    private AbstractQueue<QueueElement> outq = null;
    private DocumentBuilderFactory docBuilderFactory = null;
    private DocumentBuilder docBuilder = null;
    private XPathFactory xpf = XPathFactory.newInstance();
    private XPath xp = xpf.newXPath();
    private DTDValidator validator = null;
    private HashMap<String,TagCategory> tcHM = new HashMap<String,TagCategory>();

    private final String LINEBREAK = "<br/>";    
    
    private static final String loggerName = WorkerImpl.class.getName();
    private static final Logger logger = Logger.getLogger(loggerName);    
}
