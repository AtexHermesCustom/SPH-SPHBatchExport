/*
 * File:    Feeder.java
 *
 * Copyright (c) 2012,  Atex Media Command GmbH
 *                      Kurhessenstrasse 13
 *                      64546 Moerfelden-Walldorf
 *                      Germany
 *
 * Audit:
 * v01.00  15-feb-2012  st  Initial version.
 * v00.00  14-feb-2012  st  Created.
 */

package de.atex.h11.custom.sph.export.generic;

import java.util.AbstractQueue;
import java.util.Properties;

/**
 * Interface for NCM XML feeders.
 * 
 * @author tstuehler
 */
public interface Feeder extends Runnable {
    
    public void init (AbstractQueue<QueueElement> queue, Properties props) 
            throws Exception;
    
}
