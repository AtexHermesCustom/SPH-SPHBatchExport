/*
 * File:    QueueElement.java
 *
 * Copyright (c) 2012,  Atex Media Command GmbH
 *                      Kurhessenstrasse 13
 *                      64546 Moerfelden-Walldorf
 *                      Germany
 *
 * Audit:
 * v01.00  14-feb-2012  st  Initial version.
 * v00.00  14-feb-2012  st  Created.
 */

package de.atex.h11.custom.sph.export.generic;

import org.w3c.dom.Document;

/**
 * Generic queue element. 
 * @author tstuehler
 */
public class QueueElement {
    
    public QueueElement (Document d) {
        this.doc = d;
    }
    
    public Document getDocument () {
        return this.doc;
    }
    
    public void setDocument (Document d) {
        this.doc = d;
    }
    
    private Document doc = null;
    
}
