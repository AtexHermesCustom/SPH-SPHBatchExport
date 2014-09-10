/*
 * File:    Worker.java
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

import java.util.AbstractQueue;
import java.util.Properties;

/**
 * Interface for NCM XML workers.
 * @author tstuehler
 */
public interface Worker extends Runnable {
    
    public void init (AbstractQueue<QueueElement> inq, 
                      AbstractQueue<QueueElement> outq, Properties props) 
                      throws Exception;

}
