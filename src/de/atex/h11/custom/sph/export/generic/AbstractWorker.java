/*
 * File:    AbstractWorker.java
 *
 *
 * Audit:
 *      20120724    jpm moved common WorkerImpl methods to this class
 *      20120719    jpm replaceReservedCharMarkers function added
 */

package de.atex.h11.custom.sph.export.generic;

import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ColorConvertOp;
import java.awt.image.Raster;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.*;
import java.util.logging.Logger;
import javax.imageio.*;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.ProcessingInstruction;

public abstract class AbstractWorker extends Commons implements Worker {
    
    protected String getTaggedTextByCategory (HashMap<String,TagCategory> tcHM, String strCategory, StringBuilder sbText, 
            boolean removeExtractedText, String adjacentDelimiter, String nonAdjacentDelimiter) {
        String strTaggedText = null;
        int lastStartTagStartPos = -1;
        
        // default delimiters
        if (adjacentDelimiter == null) { adjacentDelimiter = ""; }  // no space in between
        if (nonAdjacentDelimiter == null) { nonAdjacentDelimiter = " "; } // a space in between
        
        if (tcHM.containsKey(strCategory)) {
            TreeSet<TagCategory.Pair> pairSet = tcHM.get(strCategory).getTaggedText(sbText.toString(), true);
            
            // iterate through all tagged text
            // iterate in descending order - to delete extracted text from sbText more easily
            Iterator<TagCategory.Pair> itr = pairSet.descendingIterator();
            while (itr.hasNext()) {
            	TagCategory.Pair pair = itr.next();
            	if (pair.getTaggedText().getText().length() > 0) {            	
                    if (strTaggedText != null) {
                        if (pair.getTaggedText().getEndTagEndPos() == lastStartTagStartPos) {
                            strTaggedText = pair.getTaggedText().getText() + adjacentDelimiter + strTaggedText; // adjacent tagged text
                        }
                        else {
                            strTaggedText = pair.getTaggedText().getText() + nonAdjacentDelimiter + strTaggedText; // non-adjacent tagged text
                        }
                    }
	            else
                        strTaggedText = pair.getTaggedText().getText();
                }
                logger.fine(strCategory + "/" + pair.getTag() + ": " + strTaggedText);
                
                if (removeExtractedText) {
                    // delete extracted tagged text
                    sbText.delete(pair.getTaggedText().getStartTagStartPos(), 
                            pair.getTaggedText().getEndTagEndPos());
                }
                lastStartTagStartPos = pair.getTaggedText().getStartTagStartPos();  // record start pos of last tagged text found
            }
        }

        return strTaggedText;        
    }
    
    protected String getTaggedTextByCategory (HashMap<String,TagCategory> tcHM, String strCategory, StringBuilder sbText, boolean removeExtractedText) {
        return getTaggedTextByCategory(tcHM, strCategory, sbText, removeExtractedText, null, null);
    }    
    
    protected String getTaggedTextByCategory (HashMap<String,TagCategory> tcHM, String strCategory, StringBuilder sbText) {
        return getTaggedTextByCategory(tcHM, strCategory, sbText, true, null, null);
    }
                
    protected String getTaggedTextByCategory (HashMap<String,TagCategory> tcHM, String strCategory, String strText) {
        String strTaggedText = null;
        
        if (tcHM.containsKey(strCategory)) {
            TreeSet<TagCategory.Pair> pairSet = tcHM.get(strCategory).getTaggedText(strText);
            if (!pairSet.isEmpty()) {
                TagCategory.Pair pair = pairSet.first();
                strTaggedText = pair.getTaggedText().getText();
                logger.fine(strCategory + "/" + pair.getTag() + ": " + strTaggedText);
            }
        }

        return strTaggedText;
    }
        
    
    protected int findTagByCategory (HashMap<String,TagCategory> tcHM, String strCategory, String strText) {
        int pos = -1;
        
       if (tcHM.containsKey(strCategory)) {
            TreeSet<TagCategory.Pair> pairSet = tcHM.get(strCategory).getTaggedText(strText.toString(), true);
            if (!pairSet.isEmpty()) {
                TagCategory.Pair pair = pairSet.first();
                pos = pair.getTaggedText().getStartPos();
           }
       }
        
        return pos;
    }   
    
    
    protected String removeNotes (String strText) {    // not used, notice mode text removed in xsl
        // note: (?s) - for multiline matching
        strText = strText.replaceAll("(?s)__NOTE_CMD_BEGIN__.*?__NOTE_CMD_END__", "");
        strText = strText.replace("(?s)__NOTE_CMD_BEGIN__.*?$", ""); // last note, until end of text
        strText = strText.replaceAll("__NOTE_CMD_(BEGIN|END)__", ""); // just making sure nothing's left
        return strText;
    }
    
    
    protected byte[] crop (Properties props, File srcFile, File medRes, Rectangle cropRect, Dimension dimension, 
            int rotationAngle, boolean flipX, boolean flipY) throws IOException, InterruptedException {
        byte[] imageBytes = null;

        File tempFile = File.createTempFile("export", ".jpg");
        try {
            crop(props, srcFile, medRes, tempFile, 
                cropRect, dimension, rotationAngle, flipX, flipY);
            FileInputStream in = new FileInputStream(tempFile);
            imageBytes = new byte[(int) tempFile.length()];
            int bytesRead = 0;
            do {
                bytesRead = in.read(imageBytes, bytesRead, imageBytes.length - bytesRead);
            } while (bytesRead >= 0);
            in.close();
        } finally {
            tempFile.delete();
        }
        
        return imageBytes;
    }    
    

    protected void crop (Properties props, File srcFile, File medFile, File dstFile, 
            Rectangle cropRect, Dimension dimension, 
            int rotationAngle, boolean flipX, boolean flipY) throws IOException, InterruptedException {
                
        Rectangle adjustedCropRect = null;
        if (cropRect != null && dimension != null) {
            // get source/high-res image dimensions
            Dimension srcDim = getImageDimensions(srcFile, props);
            int srcW = srcDim.width;
            int srcH = srcDim.height;

            // get med-res image dimensions
            Dimension medDim = getImageDimensions(medFile, props);
            int medW = medDim.width;
            int medH = medDim.height;  
            
            // compute adjusted crop
            logger.finer("Image file " + srcFile.getName() + 
                    ": origres-dim=" + srcW + "x" + srcH +
                    ", medres-dim=" + medW + "x" + medH +
                    ", api-dim=" + dimension.width + "x" + dimension.height);
            /* change computation of ratio
            float ratioX = (float) dimension.width / (float) srcW;
            float ratioY = (float) dimension.height / (float) srcH;      
            */
            float ratioX, ratioY;
            // determine whether the high-res or med-res was used
            if (dimension.width == srcW && dimension.height == srcH) {
                ratioX = 1;
                ratioY = 1;                   
            }
            else {
                ratioX = (float) medW / (float) srcW;
                ratioY = (float) medH / (float) srcH;                      
            }
            logger.finer("Image file " + srcFile.getName() + ": x-ratio=" + ratioX + ", y-ratio=" + ratioY);
            int cropX = (int) ((float) cropRect.x / ratioX);
            int cropY = (int) ((float) cropRect.y / ratioY);
            int cropW = (int) ((float) cropRect.width / ratioX);
            int cropH = (int) ((float) cropRect.height / ratioY);
            logger.finer("Image file " + srcFile.getName() + 
                ": adjusted: cropX=" + cropX + ", cropY=" + cropY + ", cropW=" + cropW + ", cropH=" + cropH);
            adjustedCropRect = new Rectangle(cropX, cropY, cropW, cropH);            
        }
        
        if (props.containsKey("converterProgArgs")) {
            String progArgsStr = props.getProperty("converterProgArgs");
            cropImage(srcFile.getCanonicalPath(), dstFile.getCanonicalPath(),
                      adjustedCropRect, dimension, rotationAngle, flipX, flipY, progArgsStr);
        } else {
            cropImage(props, srcFile, dstFile, 
                      adjustedCropRect, dimension, rotationAngle, flipX, flipY, false);
        }
    }
    
    
    protected void cropImage (Properties props, File srcFile, File dstFile, Rectangle cropRect,
                              Dimension dimension, int rotationAngle, boolean flipX, boolean flipY,
                              boolean bConvertToRGB)
            throws IOException {
        Object[] logParams = new Object[9];
        logParams[0] = props;
        logParams[1] = srcFile;
        logParams[2] = dstFile;
        logParams[3] = cropRect;
        logParams[4] = dimension;
        logParams[5] = new Integer(rotationAngle);
        logParams[5] = flipX;
        logParams[6] = flipY;
        logParams[8] = bConvertToRGB;
        logger.entering(getClass().getName(), "cropImage", logParams);

        // read the source file
        //BufferedImage srcImage = ImageIO.read(srcFile); // note: this doesn't work for CMYK images
        BufferedImage srcImage = readJPGFile(srcFile);      // this can read CMYK
        srcImage = convertCMYK2RGB(srcImage);

        BufferedImage croppedImage = srcImage.getSubimage(cropRect.x, cropRect.y, 
                                                          cropRect.width, cropRect.height);
        BufferedImage dstImage = croppedImage;

        // see if color space conversion is required
        ColorSpace colorSpace = srcImage.getColorModel().getColorSpace();
        boolean bNeedsConversion = false;
        switch (colorSpace.getType()) {
            case ColorSpace.TYPE_CMY:
            case ColorSpace.TYPE_CMYK:
                bNeedsConversion = true;
                break;
        }

        if (bConvertToRGB && bNeedsConversion) {
            // convert the cropped image to rgb
            ColorConvertOp op = new ColorConvertOp(
                    croppedImage.getColorModel().getColorSpace(),
                    ColorSpace.getInstance(ColorSpace.CS_sRGB), null);
            dstImage = op.createCompatibleDestImage(croppedImage, null);
            op.filter(croppedImage, dstImage);
        }

        // Find a jpeg writer
        ImageWriter writer = null;
        Iterator iter = ImageIO.getImageWritersByFormatName("jpg");
        if (iter.hasNext()) {
            writer = (ImageWriter) iter.next();
        }

        // Prepare output file
        ImageOutputStream ios = ImageIO.createImageOutputStream(dstFile);
        writer.setOutput(ios);

        // Set the compression quality
        ImageWriteParam writeParam = writer.getDefaultWriteParam();
        writeParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        writeParam.setCompressionQuality(
                Float.parseFloat(props.getProperty("jpegQuality", "0.5")));

        // Write the image
        writer.write(null, new IIOImage(dstImage, null, null), writeParam);

        // Cleanup
        ios.flush();
        writer.dispose();
        ios.close();

        logger.exiting(getClass().getName(), "cropImage");
    }

    
    protected void cropImage (String srcFileName, String dstFileName, Rectangle cropRect,
                              Dimension dimension, int rotationAngle, boolean flipX, boolean flipY, 
                              String progArgs)
            throws IOException {
        Object[] logParams = new Object[8];
        logParams[0] = srcFileName;
        logParams[1] = dstFileName;
        logParams[2] = cropRect;
        logParams[3] = dimension;
        logParams[4] = new Integer(rotationAngle);
        logParams[5] = flipX;
        logParams[6] = flipY;
        logParams[7] = progArgs;
        logger.entering(getClass().getName(), "cropImage", logParams);

        if (progArgs != null) {
            // Build the external program's argument list.
            try {
                List<String> argList = new LinkedList<String>();
                Scanner scanner = new Scanner(progArgs);
                scanner.useDelimiter("\\s");
                while (scanner.hasNext()) {
                    String token = scanner.next();
                    if (token.contains("$INFILE")) {
                        argList.add(token.replace("$INFILE", srcFileName));
                    } else if (token.contains("$OUTFILE")) {
                        argList.add(token.replace("$OUTFILE", dstFileName));
                    } else if (token.contains("$CROPRECT")) {
                        if (cropRect != null) {
                            argList.add(token.replace("$CROPRECT", "-crop"));
                            String s = Integer.toString(cropRect.width) + "x" + Integer.toString(cropRect.height) + "+" +
                                       Integer.toString(cropRect.x) + "+" + Integer.toString(cropRect.y);
                            argList.add(s);
                        }                        
                    } else if (token.contains("$FLIPX")) {
                        if (flipX) argList.add(token.replace("$FLIPX", "-flop"));
                    } else if (token.contains("$FLIPY")) {
                        if (flipY) argList.add(token.replace("$FLIPY", "-flip"));
                    } else if (token.contains("$ROTATE")) {
                        if (rotationAngle != 0) {
                            argList.add(token.replace("$ROTATE", "-rotate"));
                            String s = Integer.toString(rotationAngle);
                            argList.add(s);
                        }
                    } else {
                        argList.add(token);
                    }
                }
                runProgram(argList);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        } else throw new IllegalArgumentException("progArgs == null");

        logger.exiting(getClass().getName(), "cropImage");
    }
    
    
    private Dimension getImageDimensions(File imgFile, Properties props) throws IOException, InterruptedException {
        logger.entering(getClass().getName(), "getImageDimensions", imgFile.getName());
        
        int width = 0;
        int height = 0;
        
        if (props.containsKey("imageTestProgArgs") &&
            props.containsKey("imageTestWidthPattern") &&
            props.containsKey("imageTestHeightPattern")) {
            // use external program to get dimensions
            List<String> argList = new LinkedList<String>();
            Scanner scanner = new Scanner(props.getProperty("imageTestProgArgs"));
            scanner.useDelimiter("\\s");
            while (scanner.hasNext()) {
                String token = scanner.next();
                if (token.contains("$INFILE")) {
                    argList.add(token.replace("$INFILE", imgFile.getCanonicalPath()));
                } else {
                    argList.add(token);
                }
            }
            String response = runProgramGetResp(argList);
            String widthStr = response.replaceAll(props.getProperty("imageTestWidthPattern"), "$1");
            String heightStr = response.replaceAll(props.getProperty("imageTestHeightPattern"), "$1");
            logger.finer("getImageDimensions - parsed values: width=" + widthStr + ", height=" + heightStr);
            width = Integer.parseInt(widthStr);
            height = Integer.parseInt(heightStr);
        }
        else {
            // get dimensions
            //BufferedImage srcImage = ImageIO.read(imgFile);   // note: this doesn't work for CMYK images
            BufferedImage img = readJPGFile(imgFile);           // this can read CMYK
            // get image dimensions
            width = img.getWidth();
            height = img.getHeight();
        }
        
        Dimension dimension = new Dimension(width, height);
        
        logger.exiting(getClass().getName(), "getImageDimensions", dimension);
        return dimension;        
    }
    
    
    private BufferedImage convertCMYK2RGB(BufferedImage image) throws IOException{
        logger.finer("Converting a CYMK image to RGB");
        //Create a new RGB image
        BufferedImage rgbImage = new BufferedImage(image.getWidth(), image.getHeight(),
        		BufferedImage.TYPE_3BYTE_BGR);
        // then do a funky color convert
        ColorConvertOp op = new ColorConvertOp(null);
        op.filter(image, rgbImage);
        return rgbImage;
    }       


    protected Rectangle getCropRect (Node contNode) throws ParseException {
        logger.entering(getClass().getName(), "getCropRect", contNode);
        Rectangle cropRect = null;
        String strValue = getProcessingInstructionValue(contNode, "crop-rect");
        if (strValue != null) {
            String[] s = strValue.split(" ");
            if (s.length == 4) {
                int bottom = Integer.parseInt(s[0]);
                int left = Integer.parseInt(s[1]);
                int top = Integer.parseInt(s[2]);
                int right = Integer.parseInt(s[3]);
                logger.finer("bottom=" + bottom + ", left=" + left + ", top=" + top + ", right=" + right);
                cropRect = new Rectangle(left, top, right - left, bottom - top);
            } else {
                logger.warning("Invalid crop data found in processing instruction.");
            }            
        }
        logger.exiting(getClass().getName(), "getCropRect", cropRect);
        return cropRect;
    }

    
    protected Dimension getDimension (Node contNode) throws ParseException {
        logger.entering(getClass().getName(), "getDimension", contNode);
        Dimension dimension = null;
        String strValue = getProcessingInstructionValue(contNode, "dimension");
        if (strValue != null) {
            String[] s = strValue.split(" ");
            if (s.length == 2) {
                int width = df.parse(s[0]).intValue();
                int height = df.parse(s[1]).intValue();
                dimension = new Dimension(width, height);
            } else {
                logger.warning("Invalid dimension data found in processing instruction.");
            }
        }
        logger.exiting(getClass().getName(), "getDimension", dimension);
        return dimension;
    }


    protected int getRotationAngle (Node contNode) throws ParseException {
        logger.entering(getClass().getName(), "getRotationAngle", contNode);
        int rotationAngle = 0;
        String strValue = getProcessingInstructionValue(contNode, "rotate");
        if (strValue != null) rotationAngle = df.parse(strValue).intValue();
        logger.exiting(getClass().getName(), "getRotationAngle", rotationAngle);
        return rotationAngle;
    }
    
    
    protected boolean getFlipX (Node contNode) throws ParseException {
        logger.entering(getClass().getName(), "getFlipX", contNode);
        boolean flipX = false;
        String strValue = getProcessingInstructionValue(contNode, "flip-x");
        if (strValue != null) flipX = strValue.equalsIgnoreCase("true");
        logger.exiting(getClass().getName(), "getFlipX", flipX);
        return flipX;
    }
    
    
    protected boolean getFlipY (Node contNode) throws ParseException {
        logger.entering(getClass().getName(), "getFlipY", contNode);
        boolean flipY = false;
        String strValue = getProcessingInstructionValue(contNode, "flip-y");
        if (strValue != null) flipY = strValue.equalsIgnoreCase("true");
        logger.exiting(getClass().getName(), "getFlipY", flipY);
        return flipY;
    }            


    protected String getHighResImagePath (Node contNode) throws ParseException {
        logger.entering(getClass().getName(), "getHighResImagePath", contNode);
        String strImagePath = getProcessingInstructionValue(contNode, "highres-imagepath");
        logger.exiting(getClass().getName(), "getHighResImagePath", strImagePath);
        return strImagePath;
    }


    protected String getMedResImagePath (Node contNode) throws ParseException {
        logger.entering(getClass().getName(), "getMedResImagePath", contNode);
        String strImagePath = getProcessingInstructionValue(contNode, "medres-imagepath");
        logger.exiting(getClass().getName(), "getMedResImagePath", strImagePath);
        return strImagePath;
    }


    protected String getLowResImagePath (Node contNode) throws ParseException {
        logger.entering(getClass().getName(), "getLowResImagePath", contNode);
        String strImagePath = getProcessingInstructionValue(contNode, "lowres-imagepath");
        logger.exiting(getClass().getName(), "getLowResImagePath", strImagePath);
        return strImagePath;
    }    
    
    
    protected String getProcessingInstructionValue (Node contNode, String key) throws ParseException {
        String strValue = null;
        NodeList nl = contNode.getChildNodes();
        for (int i = 0; i < nl.getLength(); i++) {
            if (nl.item(i).getNodeType() == Node.PROCESSING_INSTRUCTION_NODE) {
                String strTarget = ((ProcessingInstruction) nl.item(i)).getTarget();
                if (strTarget.equals(key)) {
                    strValue = ((ProcessingInstruction) nl.item(i)).getData();  // get value
                    // remove the processing instruction
                    Node parent = nl.item(i).getParentNode();                    
                    parent.removeChild(nl.item(i));
                    break;
                }
            }
        }
        return strValue;
    }      
    
    
   private BufferedImage readJPGFile(File srcFile) 
    	throws IOException {
    	
        // Find a JPEG reader which supports reading Rasters.
        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("JPEG");
        ImageReader reader = null;
        while (readers.hasNext()) {
            reader = (ImageReader) readers.next();
            if (reader.canReadRaster())
                break;
        }

        // Set the input.
        ImageInputStream input = ImageIO.createImageInputStream(srcFile);
        reader.setInput(input);

        // Create the image.
        BufferedImage image = null;
        try {
            // Try reading an image (including color conversion).
            image = reader.read(0);
        } catch(IIOException e) {
            // Try reading a Raster (no color conversion).
            Raster raster = reader.readRaster(0, null);

            // Arbitrarily select a BufferedImage type.
            int imageType;
            switch (raster.getNumBands()) {
                case 1:
                    imageType = BufferedImage.TYPE_BYTE_GRAY;
                    break;
                case 3:
                    imageType = BufferedImage.TYPE_3BYTE_BGR;
                    break;
                case 4:
                    imageType = BufferedImage.TYPE_4BYTE_ABGR;
                    break;
                default:
                    throw new UnsupportedOperationException();
            }

            // Create a BufferedImage.
            image = new BufferedImage(raster.getWidth(), raster.getHeight(), imageType);

            // Set the image data.
            image.getRaster().setRect(raster);
        }
        
        return image;
    }	    
    
    
    protected void convertJpegToPdf (String strSrcFile, String strDstFile, String strProgArgs) {
        Object[] logParams = new Object[3];
        logParams[0] = strSrcFile;
        logParams[1] = strDstFile;
        logParams[2] = strProgArgs;
        logger.entering(getClass().getName(), "convertJpegToPdf", logParams);

        if (strProgArgs != null) {
            // Build the external program's argument list.
            try {
                List<String> argList = new LinkedList<String>();
                Scanner scanner = new Scanner(strProgArgs);
                scanner.useDelimiter("\\s");
                while (scanner.hasNext()) {
                    String token = scanner.next();
                    if (token.contains("$INFILE")) {
                        argList.add(token.replace("$INFILE", strSrcFile));
                    } else if (token.contains("$OUTFILE")) {
                        argList.add(token.replace("$OUTFILE", strDstFile));
                    } else {
                        argList.add(token);
                    }
                }
                runProgram(argList);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        } else throw new IllegalArgumentException("progArgs == null");

        logger.exiting(getClass().getName(), "convertJpegToPdf");
        
    }    
    
    
    protected String replaceReservedCharMarkers(String s) {
        // Replace markers that were put in the xsl transformation for particular chars
        // Literal '[' and ']' were replaced in the xsl with markers since these are used for tag matching
        s = s.replace(OPEN_SQRBRACKET_MARKER, "[");
        s = s.replace(CLOSE_SQRBRACKET_MARKER, "]");
        return s;
    }    
    
    
    private static final String OPEN_SQRBRACKET_MARKER = "__OPEN_SQR_BRACKET__";
    private static final String CLOSE_SQRBRACKET_MARKER = "__CLOSE_SQR_BRACKET__";
    
    private DecimalFormat df = (DecimalFormat) NumberFormat.getNumberInstance(Locale.US);
    
    private static final String loggerName = AbstractWorker.class.getName();
    private static final Logger logger = Logger.getLogger(loggerName);        
}
