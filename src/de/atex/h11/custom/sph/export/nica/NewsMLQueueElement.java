/*
 * File:    NewsMLQueueElement.java
 *
 * Copyright (c) 2012,  Atex Media Command GmbH
 *                      Kurhessenstrasse 13
 *                      64546 Moerfelden-Walldorf
 *                      Germany
 *
 * Audit:
 * v01.00  23-feb-2012  st  Initial version.
 * v00.00  16-feb-2012  st  Created.
 */

package de.atex.h11.custom.sph.export.nica;

import java.util.List;
import java.util.LinkedList;
import java.util.logging.Logger;
import org.w3c.dom.Document;
import de.atex.h11.custom.sph.export.generic.QueueElement;

/**
 *
 * @author tstuehler
 */
public class NewsMLQueueElement extends QueueElement {
    
    public NewsMLQueueElement (Document d) {
        super(d);
    }
    
    public void addReference (Reference ref) {
        logger.entering("NewsMLQueueElement", "addReference", ref.getName());
        refList.add(ref);
        logger.exiting("NewsMLQueueElement", "addReference");
    }
    
    public List<Reference> getReferences () {
        return refList;
    }
    
    private LinkedList<Reference> refList = new LinkedList<Reference>();
    
    private static final String loggerName = NewsMLQueueElement.class.getName();
    private static final Logger logger = Logger.getLogger(loggerName);        
}
