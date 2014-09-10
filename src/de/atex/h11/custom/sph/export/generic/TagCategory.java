/*
 * File:    TagCategory.java
 *
 * Copyright (c) 2012,  Atex Media Command GmbH
 *                      Kurhessenstrasse 13
 *                      64546 Moerfelden-Walldorf
 *                      Germany
 *
 * Audit:
 * v01.00  19-apr-2012  st  Initial version.
 * v00.00  16-apr-2012  st  Created.
 */

package de.atex.h11.custom.sph.export.generic;

import java.util.Vector;
import java.util.TreeSet;
import java.util.Iterator;
import java.util.Comparator;
import java.util.logging.Logger;

/**
 *
 * @author tstuehler
 */
public class TagCategory {
    
    public TagCategory (String strName) {
        this.strName = strName;
    }
    
    public void addTag (String strTag) {
        if (!tagV.contains(strTag))
            tagV.add(strTag);
    }
    
    public String getName () {
        return this.strName;
    }
    
    public Iterator<String> getTags () {
        return tagV.iterator();
    }
    
    public TreeSet<Pair> getTaggedText (String strText) {
        return getTaggedText(strText, false);
    }
    
    public TreeSet<Pair> getTaggedText (String strText, boolean bIgnore) {
        logger.entering(getClass().getName(), "getTaggedText", strText);
        
        TreeSet<Pair> taggedTextTS = new TreeSet<Pair>(new PositionComparator());
        
        for (String tag : tagV) {
            if (tag == null) continue;
            String endTag = tag.charAt(0) + "/" + tag.substring(1);
            int pos = 0, endPos = 0, endTagEndPos = 0;
            
            while ((pos = strText.indexOf(tag, endPos)) >= 0) {
                endPos = strText.indexOf(endTag, pos + tag.length());
                if (endPos < 0) {
                    // no closing tag
                    if (bIgnore) { // ignore missing close tag
                    	// look for another opening tag, which starts with the char [
                    	int newTagPos = strText.indexOf("[", pos + tag.length());
                    	if (newTagPos < 0)
                    		endPos = strText.length(); // until end of string
                    	else
                    		endPos = newTagPos; // another tag found
                    } else { // don't ignore missing close tag - this block not used
                        endPos = pos + tag.length();
                        if (endPos >= strText.length()) break;
                        continue;
                    }
                    endTagEndPos = endPos;
                }
                else
                    endTagEndPos = endPos + endTag.length();

                String strTagged = strText.substring(pos + tag.length(), endPos);
                taggedTextTS.add(new Pair(tag, 
                        new TaggedText(pos, pos + tag.length(), endPos, 
                            endTagEndPos, pos + tag.length(), endPos, strTagged)));
            }
        }
        
        logger.exiting(getClass().getName(), "getTaggedText", taggedTextTS);
        
        return taggedTextTS;
    }
    
    public class Pair {
        private Pair (String strTag, TaggedText taggedText) {
            this.strTag = strTag;
            this.taggedText = taggedText;
        }
        
        public String getTag () {
            return this.strTag;
        }
        
        public TaggedText getTaggedText () {
            return this.taggedText;
        }
        
        private String strTag = null;
        private TaggedText taggedText = null;
    }
    
    public class TaggedText {
        private TaggedText (int startPos, int endPos, String strText) {
            this.startPos = startPos;
            this.endPos = endPos;
            this.strText = strText;
        }

        private TaggedText (int startTagStartPos, int startTagEndPos, 
                    int entTagStartPos, int endTagEndPos, int startPos, 
                    int endPos, String strText) {
            this.startTagStartPos = startTagStartPos;
            this.startTagEndPos = startTagEndPos;
            this.endTagStartPos = entTagStartPos;
            this.endTagEndPos = endTagEndPos;
            this.startPos = startPos;
            this.endPos = endPos;
            this.strText = strText;
        }

        public int getStartPos () {
            return this.startPos;
        }
        
        public int getEndPos () {
            return this.endPos;
        }
        
        public String getText () {
            return this.strText;
        }
        
        public int getStartTagStartPos () {
            return this.startTagStartPos;
        }
        
        public int getStartTagEndPos () {
            return this.startTagEndPos;
        }
        
        public int getEndTagStartPos () {
            return this.endTagStartPos;
        }
        
        public int getEndTagEndPos () {
            return this.endTagEndPos;
        }
        
        private String strText = null;
        private int startPos = -1;
        private int endPos = -1;
        private int startTagStartPos = -1;
        private int startTagEndPos = -1;
        private int endTagStartPos = -1;
        private int endTagEndPos = -1;
    }
    
    public class PositionComparator implements Comparator<Pair> {
        @Override
        public int compare (Pair x, Pair y) {
            return x.getTaggedText().startPos - y.getTaggedText().startPos;
        }
    }
    
    private String strName = null;
    private Vector<String> tagV = new Vector<String>();
    
    private static final String loggerName = TagCategory.class.getName();
    private static final Logger logger = Logger.getLogger(loggerName);
}
