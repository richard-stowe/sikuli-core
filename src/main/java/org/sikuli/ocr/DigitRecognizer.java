package org.sikuli.ocr;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import edu.umd.cs.piccolo.PLayer;
import edu.umd.cs.piccolo.nodes.PImage;
import edu.umd.cs.piccolo.nodes.PText;
import org.imgscalr.Scalr;
import org.sikuli.core.cv.TextMap;
import org.sikuli.core.draw.ImageRenderer;
import org.sikuli.core.draw.PiccoloImageRenderer;
import org.sikuli.core.logging.ImageExplainer;
import org.sikuli.core.search.ImageQuery;
import org.sikuli.core.search.ImageSearcher;
import org.sikuli.core.search.RegionMatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class DigitRecognizer {

    final static private ImageExplainer explainer = ImageExplainer.getExplainer(DigitRecognizer.class);
	final static private Logger logger = LoggerFactory.getLogger(DigitRecognizer.class);

	// Digit search parameters
	static final int HORIZONTAL_SPLIT_THRESHOLD = 12;
	static final double DIGIT_MATCH_MIN_SCORE = 0.65;

	// Digit template parameters
	static final int digitY = 20;
	static final int digitX = 12;
	static final int margin = 5;
    private static final int TEMPLATE_ROWS = 10;
    private static final int TEMPLATE_FONT_SIZE = 15;
    private static final int TRACKING = 0;

    static ImageSearcher digitImageSearcher = new ImageSearcher(generateDigitTemplateImage());

	private DigitRecognizer(){
	}

	static private Integer convertLocationToDigit(int x){
		return Math.round((x - margin) / digitX);
	}

	static private BufferedImage generateDigitTemplateImage(){

        final List<Font> fonts = Lists.newArrayList();
        fonts.add(new Font("sansserif",0,0));
        fonts.add(new Font("serif",0,0));
        fonts.add(new Font("monaco",0,0));

		PiccoloImageRenderer canvas = new PiccoloImageRenderer(digitX * TEMPLATE_ROWS + digitX, digitY * fonts.size() + digitY){

			@Override
			protected void addContent(PLayer layer) {
				int x = margin;
				int y = margin;
				for (Font font : fonts){
                    for (int i=0;i<=9;i++){
                        BufferedImage digitImage = TextImageRenderer.render(""+i, font, TEMPLATE_FONT_SIZE, TRACKING);
                        PImage pi = new PImage(digitImage);
                        pi.setOffset(x,y);
                        layer.addChild(pi);
                        x += digitX;
                    }
                    y += digitY;
                    x = margin;
				}
			}
		};
		explainer.step(canvas, "generated digit template images");
		return canvas.render();
	}


	static public List<RecognizedDigit> recognize(BufferedImage inputImage){

		List<RecognizedDigit> recognizedDigits = Lists.newArrayList();

		TextMap tm = TextMap.createFrom(inputImage);

		for (Rectangle r : tm.getCharacterBounds()){
			recognizeDigit(inputImage, r, digitImageSearcher, recognizedDigits);
		}

		explainer.step(visualize(inputImage, recognizedDigits), "recognized digits");

		return recognizedDigits;
	}

	static private ImageRenderer visualize(BufferedImage inputImage, final List<RecognizedDigit> recognizedDigits){
		return new PiccoloImageRenderer(inputImage){
			@Override
			protected void addContent(PLayer layer) {
				for (RecognizedDigit r : recognizedDigits){
					//Rectangle r = md.bounds;
					PText t = new PText(""+r.digit);
					t.setOffset(r.x, r.y+r.height);
					t.setScale(0.7f);
					t.setTextPaint(Color.red);
					layer.addChild(t);
				}
			}
		};
	}

    static private void recognizeDigit(BufferedImage inputImage, Rectangle r, final ImageSearcher digitImageSearcher,
			List<RecognizedDigit> recognizedDigits){

        if (r.width == 0 || r.height <= 3)
			return;

		final BufferedImage charImage = inputImage.getSubimage(r.x, r.y, r.width, r.height);

        List<RegionMatch> matches = new ArrayList<RegionMatch>(){{
            addAll(digitImageSearcher.search(new ImageQuery(Scalr.resize(charImage, Scalr.Method.ULTRA_QUALITY, digitX)), null, 1));
            addAll(digitImageSearcher.search(new ImageQuery(Scalr.resize(charImage, Scalr.Method.ULTRA_QUALITY, digitX - 1)), null, 1));
            addAll(digitImageSearcher.search(new ImageQuery(Scalr.resize(charImage, Scalr.Method.ULTRA_QUALITY, digitX - 2)), null, 1));
        }};

        Collections.sort(matches, Collections.reverseOrder(new Comparator<RegionMatch>() {
            @Override
            public int compare(RegionMatch regionMatch1, RegionMatch regionMatch2) {
                return Double.compare(regionMatch1.getScore(), regionMatch2.getScore());
            }
        }));

        RegionMatch bestRegionMatch = Iterables.getFirst(matches, null);

		if (bestRegionMatch != null && bestRegionMatch.getScore() > DIGIT_MATCH_MIN_SCORE){
            Integer i = convertLocationToDigit(bestRegionMatch.x);

            logger.trace("[" + i + "] (" + bestRegionMatch.x + "," + bestRegionMatch.y + ") score: " + bestRegionMatch.getScore());

            RecognizedDigit md = new RecognizedDigit();
			md.x = r.x;
			md.y = r.y;
			md.width = r.width;
			md.height = r.height;
			md.digit = Integer.toString(i).charAt(0);
			recognizedDigits.add(md);
		} else {
			if (r.width > HORIZONTAL_SPLIT_THRESHOLD){
				Rectangle r1 = new Rectangle(r.x,r.y,r.width/2,r.height);
				Rectangle r2 = new Rectangle(r.x + r.width/2,r.y,r.width/2,r.height);
				recognizeDigit(inputImage, r1, digitImageSearcher, recognizedDigits);
				recognizeDigit(inputImage, r2, digitImageSearcher, recognizedDigits);
			}
		}
	}
}
