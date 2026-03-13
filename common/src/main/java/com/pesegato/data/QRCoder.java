package com.pesegato.data;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.itextpdf.kernel.xmp.impl.Base64;
import io.yurelle.Base45;

import javax.swing.*;
import java.awt.image.BufferedImage;
import java.io.IOException;

public class QRCoder {

    public static BufferedImage showQRCodeOnScreenBI(String text) {
        try {
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            // Create a larger QR code for screen display
            BitMatrix bitMatrix = qrCodeWriter.encode(text, BarcodeFormat.QR_CODE, 800, 800);

            return MatrixToImageWriter.toBufferedImage(bitMatrix);

        } catch (WriterException e) {
            System.err.println("Could not generate QR Code: " + e.getMessage());
        }
        return null;
    }

    public static BufferedImage getChroma(byte[] data) {
        System.out.println("Certificate B: " + new String(data));
        try {
            // Da Java, gli object Kotlin si accedono con .INSTANCE
            // Bisogna usare la sintassi Java (tipi espliciti e argomenti posizionali)
            ChromaRecommendation rec = ChromaCodeProfile.INSTANCE.recommend(data, 800);
            System.out.println(rec.getReason());
            BufferedImage img = ChromaCode.INSTANCE.encode(
                    data,
                    rec.getProfile(),
                    rec.getEccRatio(),
                    rec.getCellSize()
            );
            return img; // Restituisce l'immagine generata
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static BufferedImage getB45(byte[] data) {
        System.out.println("Certificate Base64: " + new String(Base64.encode(data)));
        System.out.println("---");

        try {
            String certBase45 = Base45.encode(data);
            System.out.println("Certificate Base45: " + certBase45);
            System.out.println("---");
            return showQRCodeOnScreenBI(certBase45);

        } catch (IOException e) {
            System.err.println("Could not generate QR Code of Base45: " + e.getMessage());
        }
        return null;
    }

    public static void showQRCodeOnScreenSwing(String name, String text) {
        try {
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            // Create a larger QR code for screen display
            BitMatrix bitMatrix = qrCodeWriter.encode(text, BarcodeFormat.QR_CODE, 400, 400);

            BufferedImage bufferedImage = MatrixToImageWriter.toBufferedImage(bitMatrix);

            JFrame frame = new JFrame("Certificate for " + name);
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            frame.getContentPane().add(new JLabel(new ImageIcon(bufferedImage)));
            frame.pack();
            frame.setLocationRelativeTo(null); // Center on screen
            frame.setVisible(true);

        } catch (WriterException e) {
            System.err.println("Could not generate QR Code: " + e.getMessage());
        }
    }

    public static void showQRCodeOnScreen(String text) {
        try {
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            // Create a QR code for the console. The size will affect the detail.
            BitMatrix bitMatrix = qrCodeWriter.encode(text, BarcodeFormat.QR_CODE, 40, 40);

            // Print the QR code to the console
            for (int y = 0; y < bitMatrix.getHeight(); y++) {
                for (int x = 0; x < bitMatrix.getWidth(); x++) {
                    // Use block characters for better visual representation
                    System.out.print(bitMatrix.get(x, y) ? "██" : "  ");
                }
                System.out.println();
            }

        } catch (WriterException e) {
            System.err.println("Could not generate QR Code: " + e.getMessage());
        }
    }


}
