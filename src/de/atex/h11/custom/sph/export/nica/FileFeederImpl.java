/*
 * File:    FileFeederImpl.java
 *
 * Copyright (c) 2012,  Atex Media Command GmbH
 *                      Kurhessenstrasse 13
 *                      64546 Moerfelden-Walldorf
 *                      Germany
 *
 * Audit:
 * v01.00  25-apr-2012  st  Initial version.
 * v00.00  25-apr-2012  st  Created.
 */

package de.atex.h11.custom.sph.export.nica;

import org.w3c.dom.Document;
import de.atex.h11.custom.sph.export.generic.QueueElement;
import de.atex.h11.custom.sph.export.generic.AbstractFileFeeder;

/**
 *
 * @author tstuehler
 */
public class FileFeederImpl extends AbstractFileFeeder {
    

    /**
     * Default contructor.
     */
    public FileFeederImpl () {}

    /**
     * Create a new QueueElement.
     * 
     * @param doc a story document to be queued for further processing. 
     * @return the queue element.
     */
    @Override
    public QueueElement newQueueElement (Document doc) {
        return new NewsMLQueueElement(doc);
    }
    
    /**
     * Get default filter stylesheet. This class has none thus, 
     * returning always null.
     * 
     * @return null
     */
    @Override
    public String getDefaultFilter () {
        return null;
    }

    /**
     * Get default stylesheet for transformation.
     * 
     * @return the built-in stylesheet resource name
     */
    @Override
    public String getDefaultStylesheet () {
        return null;
    }

}
