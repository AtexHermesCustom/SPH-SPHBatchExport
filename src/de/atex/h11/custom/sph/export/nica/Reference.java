/*
 * File:    Reference.java
 *
 * Copyright (c) 2012,  Atex Media Command GmbH
 *                      Kurhessenstrasse 13
 *                      64546 Moerfelden-Walldorf
 *                      Germany
 *
 * Audit:
 * v01.00  13-apr-2012  st  Initial version.
 * v00.00  16-feb-2012  st  Created.
 */

package de.atex.h11.custom.sph.export.nica;

/**
 *
 * @author tstuehler
 */
public class Reference<T> {
    
    public Reference (String name, T data) {
        if (name == null || data == null)
            throw new RuntimeException("Invalid argument(s).");
        this.strName = name;
        this.data = data;
    }
    
    public String getName () {
        return strName;
    }
    
    public T getData () {
        return data;
    }
    
    public Class getDataType () {
        return data.getClass();
    }

    private String strName = null;
    private T data;
    
}
