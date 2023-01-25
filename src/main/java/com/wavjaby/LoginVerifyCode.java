package com.wavjaby;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class LoginVerifyCode {
    private final static byte[][][] numbers = {
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

            byte[][][] image = convertTo2DRGB(img);
            ImageIO.write(img, "png", new File("C:\\Users\\WavJaby\\Desktop\\index.png"));
            final int width = img.getWidth();
            final int height = img.getHeight();

            int cropX1 = width - 1;
            int cropY1 = height - 1, cropY2 = 0;

            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int r = image[y][x][0] & 0xFF, g = image[y][x][1] & 0xFF, b = image[y][x][2] & 0xFF;
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
                int end = findTextEnd(cropX1, cropY1, cropHeight, image);
                int cropWidth = end - cropX1;
                for (int i = 0; i < 10; i++) {
                    int count = 0;
                    for (int y = 0; y < cropHeight; y++) {
                        for (int x = 0; x < cropWidth; x++) {
                            int r = image[cropY1 + y][cropX1 + x][0] & 0xFF, g = image[cropY1 + y][cropX1 + x][1] & 0xFF, b = image[cropY1 + y][cropX1 + x][2] & 0xFF;
                            boolean text = (r + g + b) == 0;

                            if (text && numbers[i][y][x] == 1 || !text && numbers[i][y][x] == 0)
                                count++;
//                            if (i == 0) System.out.print(text ? '1' : ' ');
                        }
//                        if (i == 0) System.out.println();
                    }

                    float score = (float) count / (cropWidth * cropHeight);
//                    System.out.println(i + " " + score);
                    if (score > highestScore) {
                        highestScore = score;
                        highestScoreNumber = i;
                    }
                }
                builder.append(highestScoreNumber);
                cropX1 = findNextText(end, cropY1, cropHeight, image);
            }
            return builder.toString();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private static int findNextText(int start, int cropY1, int cropHeight, byte[][][] image) {
        if (image.length == 0) return -1;
        int width = image[0].length;

        for (int x = start; x < width; x++) {
            boolean empty = true;
            for (int y = 0; y < cropHeight; y++) {
                int r = image[cropY1 + y][x][0] & 0xFF, g = image[cropY1 + y][x][1] & 0xFF, b = image[cropY1 + y][x][2] & 0xFF;
                if ((r + g + b) == 0) {
                    empty = false;
                    break;
                }
            }
            if (!empty)
                return x;
        }
        return -1;
    }

    private static int findTextEnd(int start, int cropY1, int cropHeight, byte[][][] image) {
        if (image.length == 0) return -1;
        int width = image[0].length;

        for (int x = start; x < width; x++) {
            boolean empty = true;
            for (int y = 0; y < cropHeight; y++) {
                int r = image[cropY1 + y][x][0] & 0xFF, g = image[cropY1 + y][x][1] & 0xFF, b = image[cropY1 + y][x][2] & 0xFF;
                if ((r + g + b) == 0) {
                    empty = false;
                    break;
                }
            }
            if (empty)
                return x;
        }
        return -1;
    }

    private static byte[][][] convertTo2DRGB(BufferedImage image) {
        final int width = image.getWidth();
        final int height = image.getHeight();

        byte[][][] result = new byte[height][width][4];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int color = image.getRGB(x, y);
                result[y][x][0] = (byte) (color >> 16);
                result[y][x][1] = (byte) (color >> 8);
                result[y][x][2] = (byte) (color);
            }
        }
        return result;
    }
}
