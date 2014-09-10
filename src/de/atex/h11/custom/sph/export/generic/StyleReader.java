/*
 * File:    StyleReader.java
 *
 * Copyright (c) 2012,  Atex Media Command GmbH
 *                      Kurhessenstrasse 13
 *                      64546 Moerfelden-Walldorf
 *                      Germany
 *
 * Audit:
 *         20120717     jpm trim lines from style file before processing
 * v01.00  17-apr-2012  st  Initial version.
 * v00.00  16-apr-2012  st  Created.
 */

package de.atex.h11.custom.sph.export.generic;

import java.io.File;
import java.io.FileReader;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.logging.Logger;

/**
 *
 * @author tstuehler
 */
public class StyleReader {
    
    public static synchronized HashMap<String,TagCategory> getTagCategories (String strFileName) 
            throws FileNotFoundException, IOException {
        return getTagCategories (new File(strFileName));
    }
    
    public static synchronized HashMap<String,TagCategory> getTagCategories (File file) 
            throws FileNotFoundException, IOException {
        logger.entering("StyleReader", "getTagCategories", file);
        
        HashMap<String,TagCategory> categories = new HashMap<String,TagCategory>();
        
        BufferedReader in = new BufferedReader(new FileReader(file));
        String strLine = null;
        String strLastTag = null;
        while((strLine = in.readLine()) != null) {
            strLine = strLine.trim();       // trim line before processing
            if (strLine.startsWith("[") && strLine.endsWith("]")) {
                // got a tag
                strLastTag = strLine;
            } else if (strLine.startsWith(";!")) {
                // got the tag class
                String strCategory = strLine.substring(2);
                TagCategory tc = categories.get(strCategory);
                if (tc == null) {
                    tc = new TagCategory(strCategory);
                    categories.put(strCategory, tc);
                }
                tc.addTag(strLastTag);
                strLastTag = null;
            } else if (strLastTag != null) {
                TagCategory tc = categories.get("TEXT");
                if (tc == null) {
                    tc = new TagCategory("TEXT");
                    categories.put("TEXT", tc);
                }
                tc.addTag(strLastTag);
                strLastTag = null;
            }
        }
        in.close();
        
        logger.exiting("StyleReader", "getTagCategories", categories);

        return categories;
    }
    
    private static final String loggerName = StyleReader.class.getName();
    private static final Logger logger = Logger.getLogger(loggerName);
}
