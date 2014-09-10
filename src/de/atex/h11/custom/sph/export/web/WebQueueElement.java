/*
 * File:    WebQueueElement.java
 *
 * Copyright (c) 2012,  Atex Media Command GmbH
 *                      Kurhessenstrasse 13
 *                      64546 Moerfelden-Walldorf
 *                      Germany
 *
 * Audit:
 * v01.00  06-jun-2012  st  Initial version.
 * v00.00  03-apr-2012  st  Created.
 */

package de.atex.h11.custom.sph.export.web;

import java.io.File;
import java.util.List;
import java.util.LinkedList;
import java.util.logging.Logger;
import org.w3c.dom.Document;
import de.atex.h11.custom.sph.export.generic.FileTarget;
import de.atex.h11.custom.sph.export.generic.QueueElement;

/**
 *
 * @author tstuehler
 */
public class WebQueueElement extends QueueElement {

    public WebQueueElement (Document d) {
        super(d);
    }
    
    public List<FileTarget> getFileTargets () {
        return targetList;
    }
    
    public void addFileTarget (String fileName, byte[] image) {
        logger.entering("WebQueueElement", "addFileTarget", fileName);
        targetList.add(new FileTarget(fileName, image));
        logger.exiting("WebQueueElement", "addFileTarget");
    }
    
    public void addFileTarget (String fileName, File file) {
        logger.entering("WebQueueElement", "addFileTarget", fileName);
        targetList.add(new FileTarget(fileName, file));
        logger.exiting("WebQueueElement", "addFileTarget");
    }
    
    private LinkedList<FileTarget> targetList = new LinkedList<FileTarget>();
    
    private static final String loggerName = WebQueueElement.class.getName();
    private static final Logger logger = Logger.getLogger(loggerName);        
    
}
