package com.getbouncer.cardscan;

import android.content.Context;
import android.graphics.Bitmap;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * This class is not thread safe, make sure that all methods run on the same thread.
 */
class Ocr {
    private static FindFourModel findFour = null;
    private static RecognizedDigitsModel recognizedDigitsModel = null;
    public List<DetectedBox> digitBoxes = new ArrayList<>();
    public DetectedBox expiryBox = null;
    public Expiry expiry = null;

    static boolean isInit() {
        return findFour != null && recognizedDigitsModel != null;
    }

    private ArrayList<DetectedBox> detectBoxes(Bitmap image) {
        ArrayList<DetectedBox> boxes = new ArrayList<>();
        for (int row = 0; row < findFour.rows; row++) {
            for (int col = 0; col < findFour.cols; col++) {

                if (findFour.hasDigits(row, col)) {
                    float confidence = findFour.digitConfidence(row, col);
                    CGSize imageSize = new CGSize(image.getWidth(), image.getHeight());
                    DetectedBox box = new DetectedBox(row, col, confidence, findFour.rows,
                            findFour.cols, findFour.boxSize, findFour.cardSize, imageSize);
                    boxes.add(box);
                }

            }
        }
        return boxes;
    }

    private ArrayList<DetectedBox> detectExpiry(Bitmap image) {
        ArrayList<DetectedBox> boxes = new ArrayList<>();
        for (int row = 0; row < findFour.rows; row++) {
            for (int col = 0; col < findFour.cols; col++) {

                if (findFour.hasExpiry(row, col)) {
                    float confidence = findFour.expiryConfidence(row, col);
                    CGSize imageSize = new CGSize(image.getWidth(), image.getHeight());
                    DetectedBox box = new DetectedBox(row, col, confidence, findFour.rows,
                            findFour.cols, findFour.boxSize, findFour.cardSize, imageSize);
                    boxes.add(box);
                }

            }
        }
        return boxes;
    }

    private String runModel(Bitmap image) {
        findFour.classifyFrame(image);
        ArrayList<DetectedBox> boxes = detectBoxes(image);
        ArrayList<DetectedBox> expiryBoxes = detectExpiry(image);
        PostDetectionAlgorithm postDetection = new PostDetectionAlgorithm(boxes, findFour);
        RecognizeNumbers recognizeNumbers = new RecognizeNumbers(image, findFour.rows,
                findFour.cols);
        ArrayList<ArrayList<DetectedBox>> lines = postDetection.horizontalNumbers();

        String algorithm = null;
        String number = recognizeNumbers.number(recognizedDigitsModel, lines);
        if (number == null) {
            ArrayList<ArrayList<DetectedBox>> verticalLines = postDetection.verticalNumbers();
            number = recognizeNumbers.number(recognizedDigitsModel, verticalLines);
            lines.addAll(verticalLines);
        } else {
            algorithm = "horizontal";
        }

        if (number == null) {
            ArrayList<ArrayList<DetectedBox>> amexLines = postDetection.amexNumbers();
            number = recognizeNumbers.amexNumber(recognizedDigitsModel, amexLines);
            lines.addAll(amexLines);
            if (number != null) {
                algorithm = "amex";
            }
        } else {
            algorithm = "vertical";
        }

        boxes = new ArrayList<>();
        for (ArrayList<DetectedBox> numbers:lines) {
            boxes.addAll(numbers);
        }

        this.expiry = null;
        if (expiryBoxes.size() > 0) {
            Collections.sort(expiryBoxes);
            DetectedBox expiryBox = expiryBoxes.get(expiryBoxes.size() - 1);
            this.expiry = Expiry.from(recognizedDigitsModel, image, expiryBox.rect);
            if (this.expiry != null) {
                this.expiryBox = expiryBox;
            } else {
                this.expiryBox = null;
            }
        }


        this.digitBoxes = boxes;
        return number;
    }

    public synchronized String predict(Bitmap image, Context context) {
        try {
            if (findFour == null) {
                findFour = new FindFourModel(context);
                try {
                    findFour.useGpu();
                } catch (Exception e) {
                    findFour = new FindFourModel(context);
                    findFour.useCPU();
                }
            }

            if (recognizedDigitsModel == null) {
                recognizedDigitsModel = new RecognizedDigitsModel(context);
            }

            try {
                return runModel(image);
            } catch (Exception e) {
                findFour = new FindFourModel(context);
                findFour.useCPU();
                return runModel(image);
            }


        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
}
