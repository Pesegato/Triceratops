package com.pesegato.data;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageConfig;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.Color;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.properties.HorizontalAlignment;
import com.pesegato.data.Certificate;
import com.pesegato.data.Token;
import com.pesegato.data.TokenPart;
import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.Moshi;

import javax.swing.*;
import java.awt.image.BufferedImage;

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
