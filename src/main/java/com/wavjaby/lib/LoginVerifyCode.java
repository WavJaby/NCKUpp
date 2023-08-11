package com.wavjaby.lib;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class LoginVerifyCode {
    private static final byte[][][] numbers = {
            // 0
            {
                    {0, 0, 0, 1, 1, 0, 0, 0},
                    {0, 0, 1, 1, 1, 1, 0, 0},
                    {0, 1, 1, 0, 0, 1, 1, 0},
                    {1, 1, 0, 0, 0, 0, 1, 1},
                    {1, 1, 0, 0, 0, 0, 1, 1},
                    {1, 1, 0, 0, 0, 0, 1, 1},
                    {1, 1, 0, 0, 0, 0, 1, 1},
                    {0, 1, 1, 0, 0, 1, 1, 0},
                    {0, 0, 1, 1, 1, 1, 0, 0},
                    {0, 0, 0, 1, 1, 0, 0, 0},
            },
            // 1
            {
                    {0, 0, 1, 1, 0, 0, 0, 0},
                    {0, 1, 1, 1, 0, 0, 0, 0},
                    {1, 1, 1, 1, 0, 0, 0, 0},
                    {0, 0, 1, 1, 0, 0, 0, 0},
                    {0, 0, 1, 1, 0, 0, 0, 0},
                    {0, 0, 1, 1, 0, 0, 0, 0},
                    {0, 0, 1, 1, 0, 0, 0, 0},
                    {0, 0, 1, 1, 0, 0, 0, 0},
                    {0, 0, 1, 1, 0, 0, 0, 0},
                    {1, 1, 1, 1, 1, 1, 0, 0},
            },
            // 2
            {
                    {0, 0, 1, 1, 1, 1, 0, 0},
                    {0, 1, 1, 0, 0, 1, 1, 0},
                    {1, 1, 0, 0, 0, 0, 1, 1},
                    {0, 0, 0, 0, 0, 0, 1, 1},
                    {0, 0, 0, 0, 0, 1, 1, 0},
                    {0, 0, 0, 0, 1, 1, 0, 0},
                    {0, 0, 0, 1, 1, 0, 0, 0},
                    {0, 0, 1, 1, 0, 0, 0, 0},
                    {0, 1, 1, 0, 0, 0, 0, 0},
                    {1, 1, 1, 1, 1, 1, 1, 1},
            },
            // 3
            {
                    {0, 1, 1, 1, 1, 1, 0, 0},
                    {1, 1, 0, 0, 0, 1, 1, 0},
                    {0, 0, 0, 0, 0, 0, 1, 1},
                    {0, 0, 0, 0, 0, 1, 1, 0},
                    {0, 0, 0, 1, 1, 1, 0, 0},
                    {0, 0, 0, 0, 0, 1, 1, 0},
                    {0, 0, 0, 0, 0, 0, 1, 1},
                    {0, 0, 0, 0, 0, 0, 1, 1},
                    {1, 1, 0, 0, 0, 1, 1, 0},
                    {0, 1, 1, 1, 1, 1, 0, 0},
            },
            // 4
            {
                    {0, 0, 0, 0, 0, 1, 1, 0},
                    {0, 0, 0, 0, 1, 1, 1, 0},
                    {0, 0, 0, 1, 1, 1, 1, 0},
                    {0, 0, 1, 1, 0, 1, 1, 0},
                    {0, 1, 1, 0, 0, 1, 1, 0},
                    {1, 1, 0, 0, 0, 1, 1, 0},
                    {1, 1, 1, 1, 1, 1, 1, 1},
                    {0, 0, 0, 0, 0, 1, 1, 0},
                    {0, 0, 0, 0, 0, 1, 1, 0},
                    {0, 0, 0, 0, 0, 1, 1, 0},
            },
            // 5
            {
                    {1, 1, 1, 1, 1, 1, 1, 0},
                    {1, 1, 0, 0, 0, 0, 0, 0},
                    {1, 1, 0, 0, 0, 0, 0, 0},
                    {1, 1, 0, 1, 1, 1, 0, 0},
                    {1, 1, 1, 0, 0, 1, 1, 0},
                    {0, 0, 0, 0, 0, 0, 1, 1},
                    {0, 0, 0, 0, 0, 0, 1, 1},
                    {1, 1, 0, 0, 0, 0, 1, 1},
                    {0, 1, 1, 0, 0, 1, 1, 0},
                    {0, 0, 1, 1, 1, 1, 0, 0},
            },
            // 6
            {
                    {0, 0, 1, 1, 1, 1, 0, 0},
                    {0, 1, 1, 0, 0, 1, 1, 0},
                    {1, 1, 0, 0, 0, 0, 1, 0},
                    {1, 1, 0, 0, 0, 0, 0, 0},
                    {1, 1, 0, 1, 1, 1, 0, 0},
                    {1, 1, 1, 0, 0, 1, 1, 0},
                    {1, 1, 0, 0, 0, 0, 1, 1},
                    {1, 1, 0, 0, 0, 0, 1, 1},
                    {0, 1, 1, 0, 0, 1, 1, 0},
                    {0, 0, 1, 1, 1, 1, 0, 0},
            },
            // 7
            {
                    {1, 1, 1, 1, 1, 1, 1, 1},
                    {0, 0, 0, 0, 0, 0, 1, 1},
                    {0, 0, 0, 0, 0, 0, 1, 1},
                    {0, 0, 0, 0, 0, 1, 1, 0},
                    {0, 0, 0, 0, 1, 1, 0, 0},
                    {0, 0, 0, 1, 1, 0, 0, 0},
                    {0, 0, 1, 1, 0, 0, 0, 0},
                    {0, 1, 1, 0, 0, 0, 0, 0},
                    {1, 1, 0, 0, 0, 0, 0, 0},
                    {1, 1, 0, 0, 0, 0, 0, 0},
            },
            // 8
            {
                    {0, 0, 1, 1, 1, 1, 0, 0},
                    {0, 1, 1, 0, 0, 1, 1, 0},
                    {1, 1, 0, 0, 0, 0, 1, 1},
                    {0, 1, 1, 0, 0, 1, 1, 0},
                    {0, 0, 1, 1, 1, 1, 0, 0},
                    {0, 1, 1, 0, 0, 1, 1, 0},
                    {1, 1, 0, 0, 0, 0, 1, 1},
                    {1, 1, 0, 0, 0, 0, 1, 1},
                    {0, 1, 1, 0, 0, 1, 1, 0},
                    {0, 0, 1, 1, 1, 1, 0, 0},
            },
            // 9
            {
                    {0, 0, 1, 1, 1, 1, 0, 0},
                    {0, 1, 1, 0, 0, 1, 1, 0},
                    {1, 1, 0, 0, 0, 0, 1, 1},
                    {1, 1, 0, 0, 0, 0, 1, 1},
                    {0, 1, 1, 0, 0, 1, 1, 1},
                    {0, 0, 1, 1, 1, 0, 1, 1},
                    {0, 0, 0, 0, 0, 0, 0, 1},
                    {0, 1, 0, 0, 0, 0, 1, 1},
                    {0, 1, 1, 0, 0, 1, 1, 0},
                    {0, 0, 1, 1, 1, 1, 0, 0},
            },
    };

    public static String parseCode(String url, String cookie) {
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestProperty("Cookie", cookie);
            InputStream in = connection.getInputStream();
            BufferedImage img = ImageIO.read(in);
            in.close();
            connection.disconnect();

            int[] image = convertTo2DRGB(img);
            ImageIO.write(img, "png", new File("C:\\Users\\WavJaby\\Desktop\\index.png"));
            final int width = img.getWidth();
            final int height = img.getHeight();

            int cropX1 = width - 1;
            int cropY1 = height - 1, cropY2 = 0;

            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int offset = y * width + width;
                    byte r = (byte) (image[offset] >> 16),
                            g = (byte) (image[offset] >> 8),
                            b = (byte) image[offset];
                    boolean text = (r + g + b) < 15;
                    if (text && x < cropX1)
                        cropX1 = x;
                    if (text && y < cropY1)
                        cropY1 = y;
                    if (text && y > cropY2)
                        cropY2 = y;
                }
            }
            int cropHeight = (cropY2 - cropY1 + 1);

            StringBuilder builder = new StringBuilder();
            while (cropX1 != -1) {
                int highestScoreNumber = 0;
                float highestScore = 0;
                int end = findTextEnd(cropX1, cropY1, cropHeight, width, image);
                int cropWidth = end - cropX1;
                for (int i = 0; i < 10; i++) {
                    int count = 0;
                    for (int y = 0; y < cropHeight; y++) {
                        for (int x = 0; x < cropWidth; x++) {
                            int offset = (cropY1 + y) * width + (cropX1 + x);
                            byte r = (byte) (image[offset] >> 16),
                                    g = (byte) (image[offset] >> 8),
                                    b = (byte) image[offset];
                            boolean text = ((int) r + g + b) == 0;

                            if (text && numbers[i][y][x] == 1 || !text && numbers[i][y][x] == 0)
                                count++;
                        }
                    }

                    float score = (float) count / (cropWidth * cropHeight);
                    if (score > highestScore) {
                        highestScore = score;
                        highestScoreNumber = i;
                    }
                }
                builder.append(highestScoreNumber);
                cropX1 = findNextText(end, cropY1, cropHeight, width, image);
            }
            return builder.toString();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private static int findNextText(int start, int cropY1, int cropHeight, int width, int[] image) {
        if (image.length == 0) return -1;

        for (int x = start; x < width; x++) {
            boolean empty = true;
            for (int y = 0; y < cropHeight; y++) {
                int offset = (cropY1 + y) * width + x;
                byte r = (byte) (image[offset] >> 16),
                        g = (byte) (image[offset] >> 8),
                        b = (byte) image[offset];
                if (((int) r + g + b) == 0) {
                    empty = false;
                    break;
                }
            }
            if (!empty)
                return x;
        }
        return -1;
    }

    private static int findTextEnd(int start, int cropY1, int cropHeight, int width, int[] image) {
        if (image.length == 0) return -1;

        for (int x = start; x < width; x++) {
            boolean empty = true;
            for (int y = 0; y < cropHeight; y++) {
                int offset = (cropY1 + y) * width + x;
                byte r = (byte) (image[offset] >> 16),
                        g = (byte) (image[offset] >> 8),
                        b = (byte) image[offset];
                if (((int) r + g + b) == 0) {
                    empty = false;
                    break;
                }
            }
            if (empty)
                return x;
        }
        return -1;
    }

    private static int[] convertTo2DRGB(BufferedImage image) {
        final int width = image.getWidth();
        final int height = image.getHeight();

        int[] result = new int[width * height];

        image.getRGB(0, 0, width, height, result, 0, width);
        return result;
    }
}
