package de.atex.h11.custom.sph.export.generic;

import java.util.Comparator;
import java.util.Iterator;
import java.util.TreeSet;
import java.util.logging.Logger;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class TagSpecialHandler {
	
    public TagSpecialHandler(Document specialTagDoc, XPath xp) 
            throws ParserConfigurationException, SAXException {
        this.specialTagDoc = specialTagDoc;
        this.xp = xp;
    }

    public String ProcessSpecialTags(String strText) 
            throws XPathExpressionException {
        logger.entering(getClass().getName(), "ProcessSpecialTags");
        
        // first step: get positions where additional strings need to be inserted to
        TreeSet<TagSpecialHandler.Pair> insertList = new TreeSet<TagSpecialHandler.Pair>(new PositionComparator());

        NodeList nl = 
            (NodeList) xp.evaluate("/lookup/map", specialTagDoc, XPathConstants.NODESET);
        
        for (int i = 0; i < nl.getLength(); i++) {
            String tag = nl.item(i).getAttributes().getNamedItem("tag").getNodeValue();
            String openStr = (String) xp.evaluate("open", nl.item(i), XPathConstants.STRING);
            String closeStr = (String) xp.evaluate("close", nl.item(i), XPathConstants.STRING);
            logger.finest("special tag=" + tag + ", open str=" + openStr + ", close str=" + closeStr);

            String startTag = "[" + tag + "]";
            String endTag = "[/" + tag + "]";
            int searchPos = 0, startPos = 0, endPos = 0;

            while ((startPos = strText.indexOf(startTag, searchPos)) >= 0) {
                insertList.add(new Pair(startPos + startTag.length(), openStr)); // insert open string
                logger.finest("open for " + tag + ", pos=" + Integer.toString(startPos));

                endPos = strText.indexOf(endTag, startPos + startTag.length()); // look for closing tag
                if (endPos < 0) {
                    // no closing tag
                    // look for the next tag that's different from the current one
                    int newTagPos = 0, sameTagPos = 0;
                    int innerSearchPos = startPos + startTag.length();
                    while (true) {
                        newTagPos = strText.indexOf("[", innerSearchPos);
                        sameTagPos = strText.indexOf(startTag, innerSearchPos);
                        if (newTagPos < 0) {	
                            // no other tags found in the string
                            HandleLineBreaks(strText, tag, openStr, closeStr, startPos, strText.length(), insertList); // look for line breaks
                            insertList.add(new Pair(strText.length(), closeStr)); // insert close string at end of string
                            logger.finest("last close for " + tag + ", pos=" + Integer.toString(strText.length()));
                            searchPos = strText.length();		
                            break;
                        }
                        else if (newTagPos < sameTagPos || sameTagPos < 0) {
                            // found a different tag
                            HandleLineBreaks(strText, tag, openStr, closeStr, startPos, newTagPos, insertList); // look for line breaks
                            if (strText.substring(newTagPos - LINEBREAK.length(), newTagPos).equals(LINEBREAK)) {
                                insertList.add(new Pair(newTagPos - LINEBREAK.length(), closeStr)); // insert before the LINEBREAK and next tag
                            }
                            else {
                                insertList.add(new Pair(newTagPos, closeStr)); // insert before the next tag
                            }
                            logger.finest("close for " + tag + ", pos=" + Integer.toString(newTagPos));
                            searchPos = newTagPos + 1;														
                            break;
                        }
                        else {
                            // found the exact same tag, continue looking
                            innerSearchPos = sameTagPos + startTag.length();
                            logger.finest("found same tag " + startTag);
                        }
                    }
                }
                else {
                    // found closing tag
                    HandleLineBreaks(strText, tag, openStr, closeStr, startPos, endPos, insertList); // look for line breaks
                    insertList.add(new Pair(endPos, closeStr)); // insert close string
                    searchPos = endPos + endTag.length();
                }
            }
        }

        // second step: insert strings to the original text
        StringBuilder sb = new StringBuilder(strText);

        Iterator<TagSpecialHandler.Pair> itr = insertList.descendingIterator();
        while (itr.hasNext()) {
            TagSpecialHandler.Pair pair = itr.next();
            sb.insert(pair.getPosition(), pair.getInsertText());
            logger.finest("insert at pos " + Integer.toString(pair.getPosition()) + ": " + pair.getInsertText());
        }

        logger.exiting(getClass().getName(), "ProcessSpecialTags");
        return sb.toString();
    }
    
    private void HandleLineBreaks(String strText, String tag, String openStr, String closeStr,
    		int startCheckPos, int endCheckPos, TreeSet<TagSpecialHandler.Pair> insertList) {
    	// look for any line breaks
    	int lbPos = 0;
    	strText = strText.substring(0, endCheckPos);
    	
    	while ((lbPos = strText.indexOf(LINEBREAK, startCheckPos))>= 0) {
    		insertList.add(new Pair(lbPos, closeStr)); // insert close string before line break
    		logger.finest("close for " + tag + ", pos=" + Integer.toString(lbPos) + " (HandleLineBreaks)");
    		
    		if (lbPos < (endCheckPos - LINEBREAK.length())) {
    			insertList.add(new Pair(lbPos + LINEBREAK.length(), openStr)); // insert open string after line break, if line break is not at the end
                logger.finest("open for " + tag + ", pos=" + Integer.toString(lbPos + LINEBREAK.length()) + " (HandleLineBreaks)");                        
    		}
    		
    		startCheckPos = lbPos + LINEBREAK.length();
    	}
    }

    private class Pair {
        private Pair (Integer position, String insertText) {
            this.position = position;
            this.insertText = insertText;
        }

        public Integer getPosition() {
            return this.position;
        }

        public String getInsertText() {
            return this.insertText;
        }

        private Integer position;
        private String insertText = null;
    }	

    private class PositionComparator implements Comparator<Pair> {
        @Override
        public int compare (Pair x, Pair y) {
            return x.getPosition() - y.getPosition();
        }
    }    

    private static final String LINEBREAK = "<br/>"; 
    
    private Document specialTagDoc = null;
    private XPath xp = null;
    
    private static final String loggerName = TagSpecialHandler.class.getName();
    private static final Logger logger = Logger.getLogger(loggerName);    
}

