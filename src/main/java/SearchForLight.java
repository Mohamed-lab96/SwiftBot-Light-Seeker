import swiftbot.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.*;

public class SearchForLight {
    static SwiftBotAPI swiftBot;
    static boolean running = true;

    // Variables to track execution details
    static long startTime;
    static int thresholdLightIntensity;
    static int brightestLightIntensity = 0;
    static int lightDetectionCount = 0;
    static List<Integer> lightIntensities = new ArrayList<>();
    static List<String> movements = new ArrayList<>();
    static int objectDetectionCount = 0;
    static double totalDistanceTravelled = 0.0;
    static List<String> imageLocations = new ArrayList<>();

    public static void main(String[] args) throws InterruptedException {
        try {
            swiftBot = new SwiftBotAPI();
        } catch (Exception e) {
            System.out.println("\nI2C disabled!");
            System.out.println("Run the following command:");
            System.out.println("sudo raspi-config nonint do_i2c 0\n");
            System.exit(5);
        }

        System.out.println("\n*********************************************************");
        System.out.println("**************** SWIFTBOT LIGHT SEEKER *****************");
        System.out.println("*********************************************************\n");
        System.out.println("Press Button A to start...");

        swiftBot.enableButton(Button.A, () -> {
            System.out.println("Starting Light Search...");
            startTime = System.currentTimeMillis(); // Start time tracking
            try {
                searchForLight();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });

        // Stop / Resume Button (Button X)
        swiftBot.enableButton(Button.X, () -> {
            if (running) {
                System.out.println("Stopping SwiftBot...");
                running = false; // Stop the loop
                swiftBot.move(0, 0, 100); // Stop movement safely
            } else {
                System.out.println("Resuming Light Search...");
                running = true;
                try {
                    searchForLight(); // Restart searching
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });

        // Log Execution Details when 'Y' is pressed
        swiftBot.enableButton(Button.Y, () -> {
            System.out.println("Displaying Execution Log...");
            saveExecutionLogToFile();  // Save log to a text file
        });

    }

    public static void searchForLight() throws InterruptedException {
        thresholdLightIntensity = 0; // Reset threshold
        while (running) {
            BufferedImage image = swiftBot.takeGrayscaleStill(ImageSize.SQUARE_720x720);
            if (image == null) {
                System.out.println("ERROR: Could not capture image.");
                System.exit(5);
            }

            // Save captured image as PNG
            saveImageToFile(image);

            int[] lightLevels = analyzeImage(image);
            int maxIndex = findMaxIndex(lightLevels);

            // Store initial light intensity
            if (thresholdLightIntensity == 0) {
                thresholdLightIntensity = (lightLevels[0] + lightLevels[1] + lightLevels[2]) / 3;
            }

            // Track the brightest light
            int maxLight = lightLevels[maxIndex];
            if (maxLight > brightestLightIntensity) {
                brightestLightIntensity = maxLight;
            }

            // Store light detection details
            lightDetectionCount++;
            lightIntensities.add(maxLight);

            System.out.println("\nLight Intensity Levels:");
            System.out.println("Left: " + lightLevels[0] + " | Center: " + lightLevels[1] + " | Right: " + lightLevels[2]);

            int obstacleDistance = (int) swiftBot.useUltrasound();

            if (obstacleDistance < 50) {
                System.out.println("Obstacle detected! Distance: " + obstacleDistance + " cm.");
                swiftBot.fillUnderlights(new int[]{255, 0, 0});  // Red warning lights
                objectDetectionCount++; // Track obstacle count
                waitForObstacleRemoval();

                // If still blocked, choose the next best direction
                maxIndex = chooseAlternativeDirection(lightLevels, maxIndex);
            }

            moveSwiftBot(maxIndex);
        }
    }

    public static void saveImageToFile(BufferedImage image) {
        try {
            // Create a unique filename based on the timestamp
            String fileName = "image_" + System.currentTimeMillis() + ".png";
            File outputFile = new File(fileName);
            // Save the image as PNG
            ImageIO.write(image, "PNG", outputFile);
            System.out.println("Image saved as: " + outputFile.getName());
            imageLocations.add(outputFile.getAbsolutePath());
        } catch (IOException e) {
            System.out.println("Error saving image: " + e.getMessage());
        }
    }

    public static void saveExecutionLogToFile() {
        long duration = (System.currentTimeMillis() - startTime) / 1000;
        File logFile = new File("execution_log.txt");
        try {
            // Create the log file if it doesn't exist
            if (!logFile.exists()) {
                logFile.createNewFile();
            }

            // Prepare the log content
            String logContent = "\n***** Execution Log *****\n" +
                                "Threshold Light Intensity: " + thresholdLightIntensity + "\n" +
                                "Brightest Light Intensity Detected: " + brightestLightIntensity + "\n" +
                                "Number of Light Detections: " + lightDetectionCount + "\n" +
                                "Total Execution Time: " + duration + " seconds\n" +
                                "Total Distance Travelled: " + totalDistanceTravelled + " cm\n" +
                                "Movements Taken: " + movements + "\n" +
                                "Objects Detected: " + objectDetectionCount + "\n" +
                                "***************************\n";

            // Print the log content to the console (command prompt)
            System.out.println(logContent);

            // Append the log content to the file
            java.nio.file.Files.write(logFile.toPath(), logContent.getBytes(), java.nio.file.StandardOpenOption.APPEND);
            System.out.println("Execution log saved to: " + logFile.getAbsolutePath());
        } catch (IOException e) {
            System.out.println("Error saving execution log: " + e.getMessage());
        }
    }

    public static int[] analyzeImage(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int thirdWidth = width / 3;

        int[] intensities = new int[3];

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                int pixel = image.getRGB(x, y) & 0xFF; // Extract brightness (grayscale)
                if (x < thirdWidth) {
                    intensities[0] += pixel;
                } else if (x < 2 * thirdWidth) {
                    intensities[1] += pixel;
                } else {
                    intensities[2] += pixel;
                }
            }
        }

        // Average intensity for each section
        for (int i = 0; i < 3; i++) {
            intensities[i] /= (thirdWidth * height);
        }

        return intensities;
    }

    public static int findMaxIndex(int[] values) {
        int maxIndex = 0;
        for (int i = 1; i < values.length; i++) {
            if (values[i] > values[maxIndex]) {
                maxIndex = i;
            }
        }
        return maxIndex;
    }

    public static int chooseAlternativeDirection(int[] lightLevels, int blockedIndex) {
        int secondMaxIndex = -1;
        int maxVal = Integer.MIN_VALUE;

        for (int i = 0; i < lightLevels.length; i++) {
            if (i != blockedIndex && lightLevels[i] > maxVal) {
                maxVal = lightLevels[i];
                secondMaxIndex = i;
            }
        }

        // If both remaining intensities are equal, choose randomly
        if (secondMaxIndex == -1 || (lightLevels[0] == lightLevels[2])) {
            return new Random().nextInt(2) == 0 ? 0 : 2; // Randomly choose left (0) or right (2)
        }

        return secondMaxIndex;
    }

    public static void waitForObstacleRemoval() throws InterruptedException {
        System.out.println("Waiting 10 seconds for obstacle removal...");
        for (int i = 10; i > 0; i--) {
            System.out.println(i + " seconds remaining...");
            Thread.sleep(1000);
        }

        int obstacleDistance = (int) swiftBot.useUltrasound();
        if (obstacleDistance >= 50) {
            System.out.println("Obstacle removed. Resuming light search...");
        } else {
            System.out.println("Obstacle still present. Choosing an alternative direction.");
        }
    }

    public static void moveSwiftBot(int direction) throws InterruptedException {
        int[] green = {0, 255, 0}; // Green light
        swiftBot.fillUnderlights(green);

        switch (direction) {
            case 0:
                System.out.println("Moving LEFT.");
                swiftBot.move(-50, 50, 500);
                totalDistanceTravelled += 15; // Approx distance
                movements.add("Left 15 cm");
                break;
            case 1:
                System.out.println("Moving FORWARD.");
                swiftBot.move(50, 50, 1000);
                totalDistanceTravelled += 30;
                movements.add("Straight 30 cm");
                break;
            case 2:
                System.out.println("Moving RIGHT.");
                swiftBot.move(50, -50, 500);
                totalDistanceTravelled += 15;
                movements.add("Right 15 cm");
                break;
        }
    }

}
