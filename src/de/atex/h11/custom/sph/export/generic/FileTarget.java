package de.atex.h11.custom.sph.export.generic;

import java.io.File;

public class FileTarget {
    public FileTarget (String fileName, byte[] image) {
        this.fileName = fileName;
        this.image = image;
    }

    public FileTarget (String fileName, File file) {
        this.fileName = fileName;
        this.file = file;
    }

    public String getFileName () {
        return this.fileName;
    }

    public byte[] getImage () {
        return this.image;
    }

    public File getSourceFile () {
        return this.file;
    }

    private String fileName = null;
    private byte[] image = null;
    private File file = null;
}