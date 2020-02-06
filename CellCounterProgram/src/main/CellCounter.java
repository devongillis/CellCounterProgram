package main;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JPanel;

public class CellCounter extends JPanel{
	
	private static final long serialVersionUID = 1L;
	
	public static int cellCount = 0;
	public static int pixelboxSize = 15; // a 15x15 area for each pixel pivot (needs to be an odd number)
	public static int pixelsOutFromPivot = (pixelboxSize-1)/2; // a 15x15 = 7 pixel radius out from pivot
	public static float factor = 0.25f; // a group of pixels must have at least 25% of them colored
	public static int jFrameWidth = 2250;
	public static int jFrameHeight = 1500;
	public static boolean printed = false;
	public static String fileName = "hMSC_MC_2.jpg";
	
	
	public static void main(String[] args) {
		JFrame F = new JFrame();
		F.add(new CellCounter());
		F.setSize(jFrameWidth, jFrameHeight);
		F.setVisible(true);
		F.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
	}
	
	// this function will remove the background color by checking its green component
	public BufferedImage RemoveBackground(BufferedImage original) {
		int maxGreenAllowed = 140; // any pixel with a green component greater than this value is considered background
		BufferedImage image = deepCopy(original);
		// check each pixel
		for(int i = 0; i < image.getWidth(); i++) {
			for(int j = 0; j < image.getHeight(); j++) {
				// break the pixel color down into its ARGB components
				int c = image.getRGB(i, j);
				int alpha = (c & 0xff000000) >> 24;
				int red = (c & 0x00ff0000) >> 16;
				int green = (c & 0x0000ff00) >> 8;
				int blue = c & 0x000000ff;
				
				if(green > maxGreenAllowed) {
					// the pixel is determined to be background set the pixel RGB's to white
					green = 255;
					red = 255;
					blue = 255;
				}
				image.setRGB(i, j, colorToRGB(alpha, red, green, blue));
			}
		}
		return image;
	}
	
	// this function will remove the red component that is characteristic of microcarriers
	public BufferedImage RemoveRedComponentOfMicroCarriers(BufferedImage original) {
		int maxRedAllowed = 80;
		BufferedImage image = deepCopy(original);
		// check each pixel
		for(int i = 0; i < image.getWidth(); i++) {
			for(int j = 0; j < image.getHeight(); j++) {
				// break the pixel color down into its ARGB components
				int c = image.getRGB(i, j);
				int alpha = (c & 0xff000000) >> 24;
				int red = (c & 0x00ff0000) >> 16;
				int green = (c & 0x0000ff00) >> 8;
				int blue = c & 0x000000ff;
				
				if(red > maxRedAllowed) {
					// pixel is considered part of a microcarrier and not a cell, set to white
					green = 255;
					red = 255;
					blue = 255;
				}
				image.setRGB(i, j, colorToRGB(alpha, red, green, blue));
			}
		}
		return image;
	}
	
	// this function will remove the blue component that is characteristic of microcarriers
	public BufferedImage RemoveBlueComponentOfMicroCarriers(BufferedImage original) {
		int minBlueAllowed = 80;
		BufferedImage image = deepCopy(original);
		// check each pixel
		for(int i = 0; i < image.getWidth(); i++) {
			for(int j = 0; j < image.getHeight(); j++) {
				// break the pixel color down into its ARGB components
				int c = image.getRGB(i, j);
				int alpha = (c & 0xff000000) >> 24;
				int red = (c & 0x00ff0000) >> 16;
				int green = (c & 0x0000ff00) >> 8;
				int blue = c & 0x000000ff;
				
				if(blue < minBlueAllowed) {
					// pixel is considered part of a microcarrier and not a cell, set to white
					green = 255;
					red = 255;
					blue = 255;
				}
				image.setRGB(i, j, colorToRGB(alpha, red, green, blue));
			}
		}
		return image;
	}
	
	// this function will remove the random rogue pixels left over that do not meet size requirements for a cell
	public BufferedImage RemoveRoguePixels(BufferedImage original) {
		BufferedImage image = deepCopy(original);
		// for each 15x15 group of pixels we want to assess whether it contains a cell
		for(int i = pixelsOutFromPivot; i + pixelsOutFromPivot < image.getWidth(); i+=pixelboxSize) {
			for(int j = pixelsOutFromPivot; j + pixelsOutFromPivot < image.getHeight(); j+=pixelboxSize) {
				// check nearby pixels to see if this is just a dot or a large clutter of pixels
				int count = 0; // the number of pixels in the area that contain color
				for(int x = -pixelsOutFromPivot; x < pixelsOutFromPivot + 1; x++) {
					for(int y = -pixelsOutFromPivot; y < pixelsOutFromPivot + 1; y++) {
						// break the pixel color down into its ARGB components
						int c = image.getRGB(i + x, j + y);
						int red = (c & 0x00ff0000) >> 16;
						int green = (c & 0x0000ff00) >> 8;
						int blue = c & 0x000000ff;
						if(red != 255 || green != 255 || blue != 255) {
							// a colored pixel has been found
							count++;
						}
					}
				}
				// after counting check if the number of colored pixels in the area meets the required factor
				if((float)count/(pixelboxSize * pixelboxSize) < factor) {
					// there is a good chance this is just a random clutter of pixels and not a real cell
					// set the entire area to white
					for(int x = -pixelsOutFromPivot; x < pixelsOutFromPivot + 1; x++) {
						for(int y = -pixelsOutFromPivot; y < pixelsOutFromPivot + 1; y++) {
							int c = image.getRGB(i + x, j + y);
							int alpha = (c & 0xff000000) >> 24;
							image.setRGB(i + x, j + y, colorToRGB(alpha, 255, 255, 255));
						}
					}
				}
			}
		}
		return image;
	}
	
	// this function will transform the image into a checker board like pattern where each black square
	// represents a single cell that was found, also prints out the cell count
	public BufferedImage TransformRemainingPixelGroupsIntoCountableBoxes(BufferedImage original) {
		BufferedImage image = deepCopy(original);
		for(int i = pixelsOutFromPivot; i + pixelsOutFromPivot < image.getWidth(); i+=pixelboxSize) {
			for(int j = pixelsOutFromPivot; j + pixelsOutFromPivot < image.getHeight(); j+=pixelboxSize) {
				int count = 0;
				for(int x = -pixelsOutFromPivot; x < pixelsOutFromPivot + 1; x++) {
					for(int y = -pixelsOutFromPivot; y < pixelsOutFromPivot + 1; y++) {
						int c = image.getRGB(i + x, j + y);
						int red = (c & 0x00ff0000) >> 16;
						int green = (c & 0x0000ff00) >> 8;
						int blue = c & 0x000000ff;
						if(red != 255 || green != 255 || blue != 255) {
							// a colored pixel
							count++;
						}
					}
				}
				if((float)count/(pixelboxSize * pixelboxSize) >= factor) {
					cellCount++;
					for(int x = -pixelsOutFromPivot; x < pixelsOutFromPivot + 1; x++) {
						for(int y = -pixelsOutFromPivot; y < pixelsOutFromPivot + 1; y++) {
							int c = image.getRGB(i + x, j + y);
							int alpha = (c & 0xff000000) >> 24;
							image.setRGB(i + x, j + y, colorToRGB(alpha, 0, 0, 0));
						}
					}
				}
			}
		}
		return image;
	}
	
	// this function is called by the JFrame to start the processing
	// it will go through the other functions step by step and print all pictures
	public void paintComponent(Graphics g) {
		//AffineTransform at1 = AffineTransform.getTranslateInstance(0, 0);
		BufferedImage image1 = LoadImage(fileName);
		BufferedImage image2;
		BufferedImage image3;
		BufferedImage image4;
		BufferedImage image5;
		BufferedImage image6;
		
		image2 = RemoveBackground(image1);
		image3 = RemoveRedComponentOfMicroCarriers(image2);
		image4 = RemoveBlueComponentOfMicroCarriers(image3);
		image5 = RemoveRoguePixels(image4);
		image6 = TransformRemainingPixelGroupsIntoCountableBoxes(image5);
		
		if(!printed) {
			printed = true;
			System.out.println("number of cells found: " + cellCount);
		}
		
		image1 = resize(image1, 750, 750);
		image2 = resize(image2, 750, 750);
		image3 = resize(image3, 750, 750);
		image4 = resize(image4, 750, 750);
		image5 = resize(image5, 750, 750);
		image6 = resize(image6, 750, 750);
		
		Graphics2D g2d = (Graphics2D)g;
		
		g2d.drawImage(image1, AffineTransform.getTranslateInstance(0, 0), null);
		g2d.drawImage(image2, AffineTransform.getTranslateInstance(750, 0), null);
		g2d.drawImage(image3, AffineTransform.getTranslateInstance(1500, 0), null);
		g2d.drawImage(image4, AffineTransform.getTranslateInstance(0, 750), null);
		g2d.drawImage(image5, AffineTransform.getTranslateInstance(750, 750), null);
		g2d.drawImage(image6, AffineTransform.getTranslateInstance(1500, 750), null);
	}
	
    private static BufferedImage resize(BufferedImage img, int height, int width) {
        Image tmp = img.getScaledInstance(width, height, Image.SCALE_SMOOTH);
        BufferedImage resized = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = resized.createGraphics();
        g2d.drawImage(tmp, 0, 0, null);
        g2d.dispose();
        return resized;
    }
	// converts color from ARGB values to a single int
	private int colorToRGB(int alpha, int red, int green, int blue) {
        int newPixel = 0;
        newPixel += alpha;
        newPixel = newPixel << 8;
        newPixel += red;
        newPixel = newPixel << 8;
        newPixel += green;
        newPixel = newPixel << 8;
        newPixel += blue;

        return newPixel;
    }
	
	public BufferedImage LoadImage(String FileName) {
		BufferedImage img = null;
		
		try {
			img = ImageIO.read(new File(FileName));
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		return img;
	}
	
	// this function will create an exact copy of the provided bufferedImage and return it
	public static BufferedImage deepCopy(BufferedImage bi) {
	    ColorModel cm = bi.getColorModel();
	    boolean isAlphaPremultiplied = cm.isAlphaPremultiplied();
	    WritableRaster raster = bi.copyData(bi.getRaster().createCompatibleWritableRaster());
	    return new BufferedImage(cm, raster, isAlphaPremultiplied, null);
	}

}
