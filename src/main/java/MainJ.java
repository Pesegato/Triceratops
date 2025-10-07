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
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;

import static java.nio.charset.StandardCharsets.UTF_8;

public class MainJ {
    static int SIZE = 128;
    static int parts = 0;
    static String version;
    static Moshi moshi = new Moshi.Builder().build();
    static JsonAdapter<TokenPart> jsonAdapterTP = moshi.adapter(TokenPart.class);
    static JsonAdapter<Certificate> jsonAdapterC = moshi.adapter(Certificate.class);

    public static void buildToken(String label, String color, String secret, int parts) {

        Token token = new Token(label, color);
        MainJ.parts = parts;
        Properties prop = new Properties();
        try {
            //load a properties file from class path, inside static method
            prop.load(MainJ.class.getClassLoader().getResourceAsStream("version.properties"));

            //get the property value and print it out
            version = prop.getProperty("version");

        } catch (IOException ex) {
            ex.printStackTrace();
        }
        byte[] a = new byte[SIZE];
        byte[][] b = new byte[parts][];
        byte[] c = new byte[SIZE];
        Random r = new Random();
        r.nextBytes(a);
        if (parts > 0) {
            for (int i = 0; i < parts; i++) {
                b[i] = new byte[SIZE];
                r.nextBytes(b[i]);
            }
        }

        /*
        for (int i=0; i< data.length;i++){
            data[i]=new byte[SIZE];
            r.nextBytes(data[i]);
        }
        */

        byte mypassword[] = secret.getBytes(UTF_8);

        for (int i = 0; i < SIZE; i++) {
            //System.out.printf("A 0x%02X\n", A[i]);
            //System.out.println("A " + Integer.toBinaryString(A[i]));
            c[i] = a[i];
            if (parts > 0) {
                for (int j = 0; j < parts; j++) {
                    c[i] = (byte) (c[i] ^ b[j][i]);
                }
            }

            if (i < mypassword.length)
                a[i] = (byte) (a[i] ^ mypassword[i]);

            //System.out.println("A " + Integer.toBinaryString(A[i]));
            //System.out.printf("ABC 0x%02X 0x%02X 0x%02X\n", A[i] , B[i], C[i]);
        }
/*
        for (int j = 0; j<parts-2; j++){
            for (int i=0;i<SIZE;i++){
                data[parts-1][i]= (byte) (data[j][i]^data[j+i][i]);
            }
        }

        for (int i=0;i<SIZE;i++) {
            if (i < mypassword.length)
                data[0][i] = (byte) (data[0][i] ^ mypassword[i]);
        }
        */
/*
        System.out.println(data[0][0]+" "+data[1][0]+ " "+data[2][0]);
        System.out.println(data[0][0]^data[1][0]^data[2][0]);
*/

        /*
        System.out.println("\na");
        for (int i = 0; i < SIZE; i++) {
            System.out.printf("%02X", a[i]);
        }
        System.out.println("\nb");
        for (int i = 0; i < SIZE; i++) {
            System.out.printf("%02X", b[i]);
        }
        System.out.println("\nc");
        for (int i = 0; i < SIZE; i++) {
            System.out.printf("%02X", c[i]);
        }
        */

        //byte[] ab=concat(a, b);
        //byte[] ac=concat(a, c);
        //byte[] bc=concat(b, c);

        System.out.println("\n---");

        String[] DATA64 = new String[parts];
        String A64 = Base64.getEncoder().encodeToString(a);
        for (int i = 0; i < parts; i++) {
            DATA64[i] = Base64.getEncoder().encodeToString(b[i]);
        }
        String C64 = Base64.getEncoder().encodeToString(c);

        //byte[] AB64 = Base64.getEncoder().encode(ab);
        //byte[] AC64 = Base64.getEncoder().encode(ac);
        //byte[] BC64 = Base64.getEncoder().encode(bc);


        System.out.println(" DATA64[0]=\"" + A64 + "\";");

        TokenPart p0 = new TokenPart(token, A64, 0, parts + 2);

        //String json = jsonAdapter.toJson(p0);
        //System.out.println(json);

        writePDFTokenPart(p0);
        for (int i = 0; i < parts; i++) {
            System.out.println(" DATA64[" + (i + 1) + "]=\"" + DATA64[i] + "\";");
            writePDFTokenPart(new TokenPart(token, DATA64[i], i + 1, parts + 2));
        }
        System.out.println(" DATA64[" + (parts + 1) + "]=\"" + C64 + "\";");
        writePDFTokenPart(new TokenPart(token, C64, (parts + 1), parts + 2));

        //System.out.println("r:" + new String(AB64));
        //System.out.println("g:" + new String(AC64));
        //System.out.println("b:" + new String(BC64));


    }

    public static void showQRCodeOnScreenSwing(String name, String text) {
        try {
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            // Create a larger QR code for screen display
            BitMatrix bitMatrix = qrCodeWriter.encode(text, BarcodeFormat.QR_CODE, 400, 400);

            BufferedImage bufferedImage = MatrixToImageWriter.toBufferedImage(bitMatrix);

            JFrame frame = new JFrame("Certificate for "+name);
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

    private static void writePDFTokenPart(TokenPart tokenPart) {
        try {
            PdfWriter writer = new PdfWriter(getPath() + tokenPart.getPrettyName() + ".pdf");
            PdfDocument pdfDoc = new PdfDocument(writer);
            Document document = new Document(pdfDoc, PageSize.A4);

            PdfFont helvetica = PdfFontFactory.createFont(StandardFonts.HELVETICA);
            PdfFont courier = PdfFontFactory.createFont(StandardFonts.COURIER);

            // Replicating showTextAligned with Paragraphs at fixed positions
            document.add(new Paragraph("LABEL:________").setFont(helvetica).setFontSize(12).setFixedPosition(200, 690, 200));
            document.add(new Paragraph("COLOR:________").setFont(helvetica).setFontSize(12).setFixedPosition(200, 650, 200));
            document.add(new Paragraph("PART:________").setFont(helvetica).setFontSize(12).setFixedPosition(200, 610, 200));
            document.add(new Paragraph("CODE:________").setFont(helvetica).setFontSize(12).setFixedPosition(200, 570, 200));
            document.add(new Paragraph("Scan the QR code below with the T9s token app\nto create an ephemeral token").setFont(helvetica).setFontSize(12).setFixedPosition(200, 470, 350));

            Color fgColor = switch (tokenPart.getColor()) {
                case GREEN, RED, BLUE, GRAY, BLACK -> ColorConstants.WHITE;
                case WHITE -> ColorConstants.BLACK;
            };

            Color bgColor = switch (tokenPart.getColor()) {
                case GREEN -> new DeviceRgb(0,128,0);
                case RED -> ColorConstants.RED;
                case BLUE -> ColorConstants.BLUE;
                case WHITE -> ColorConstants.LIGHT_GRAY;
                case GRAY -> ColorConstants.GRAY;
                case BLACK -> ColorConstants.BLACK;
            };

            document.add(new Paragraph(tokenPart.getLabel()).setFont(courier).setFontSize(32)
                    //.setHorizontalAlignment(HorizontalAlignment.RIGHT)
                    .setFontColor(fgColor)
                    .setBackgroundColor(bgColor));
            document.add(new Paragraph(tokenPart.getLabel()).setFont(courier).setFontSize(12).setFixedPosition(250, 692, 200));
            document.add(new Paragraph(tokenPart.getColor().toString()).setFont(courier).setFontSize(12).setFixedPosition(250, 652, 200));
            document.add(new Paragraph(String.valueOf(tokenPart.getPrettyPrintParts())).setFont(courier).setFontSize(12).setFixedPosition(250, 612, 200));
            document.add(new Paragraph("4589").setFont(courier).setFontSize(12).setFixedPosition(250, 574, 200));

            document.add(new Paragraph("Forged on " + new Date() + "\nwith version " + version).setFont(courier).setFontSize(12).setFixedPosition(200, 50, 400));

            // Use ZXing to create the QR Code
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            BitMatrix bitMatrix = qrCodeWriter
                    .encode(jsonAdapterTP.toJson(tokenPart), BarcodeFormat.QR_CODE,
                            10, 10);
/*
            int x = -1;
            System.out.println(bitMatrix.getRow(++x, null).toString().replaceAll("\\s+", ""));
            System.out.println(bitMatrix.getRow(++x, null).toString().replaceAll("\\s+", ""));
            System.out.println(bitMatrix.getRow(++x, null).toString().replaceAll("\\s+", ""));
            System.out.println(bitMatrix.getRow(++x, null).toString().replaceAll("\\s+", ""));
            System.out.println(bitMatrix.getRow(++x, null).toString().replaceAll("\\s+", ""));
            System.out.println(bitMatrix.getRow(++x, null).toString().replaceAll("\\s+", ""));
            System.out.println(bitMatrix.getRow(++x, null).toString().replaceAll("\\s+", ""));
            System.out.println(bitMatrix.getRow(++x, null).toString().replaceAll("\\s+", ""));
            System.out.println(bitMatrix.getRow(++x, null).toString().replaceAll("\\s+", ""));
            System.out.println(bitMatrix.getRow(++x, null).toString().replaceAll("\\s+", ""));
            System.out.println(bitMatrix.getRow(++x, null).toString().replaceAll("\\s+", ""));
            System.out.println(bitMatrix.getRow(++x, null).toString().replaceAll("\\s+", ""));
            System.out.println(bitMatrix.getRow(++x, null).toString().replaceAll("\\s+", ""));
            System.out.println(bitMatrix.getRow(++x, null).toString().replaceAll("\\s+", ""));
            System.out.println(bitMatrix.getRow(++x, null).toString().replaceAll("\\s+", ""));
            System.out.println(bitMatrix.getRow(++x, null).toString().replaceAll("\\s+", ""));
            System.out.println(bitMatrix.getRow(++x, null).toString().replaceAll("\\s+", ""));
            System.out.println(bitMatrix.getRow(++x, null).toString().replaceAll("\\s+", ""));
            System.out.println(bitMatrix.getRow(++x, null).toString().replaceAll("\\s+", ""));
            System.out.println(bitMatrix.getRow(++x, null).toString().replaceAll("\\s+", ""));
            System.out.println(bitMatrix.getRow(++x, null).toString().replaceAll("\\s+", ""));
            System.out.println(bitMatrix.getRow(++x, null).toString().replaceAll("\\s+", ""));
            System.out.println(bitMatrix.getRow(++x, null).toString().replaceAll("\\s+", ""));
            System.out.println(bitMatrix.getRow(++x, null).toString().replaceAll("\\s+", ""));
            System.out.println(bitMatrix.getRow(++x, null).toString().replaceAll("\\s+", ""));
            System.out.println(bitMatrix.getRow(++x, null).toString().replaceAll("\\s+", ""));
            System.out.println(bitMatrix.getRow(++x, null).toString().replaceAll("\\s+", ""));
            System.out.println(bitMatrix.getRow(++x, null).toString().replaceAll("\\s+", ""));
            System.out.println(bitMatrix.getRow(++x, null).toString().replaceAll("\\s+", ""));
            System.out.println(bitMatrix.getRow(++x, null).toString().replaceAll("\\s+", ""));
            System.out.println(bitMatrix.getRow(++x, null).toString().replaceAll("\\s+", ""));
            System.out.println(bitMatrix.getRow(++x, null).toString().replaceAll("\\s+", ""));
            System.out.println(bitMatrix.getRow(++x, null).toString().replaceAll("\\s+", ""));
            System.out.println(bitMatrix.getRow(++x, null).toString().replaceAll("\\s+", ""));
            System.out.println(bitMatrix.getRow(++x, null).toString().replaceAll("\\s+", ""));
            System.out.println(bitMatrix.getRow(++x, null).toString().replaceAll("\\s+", ""));
            System.out.println(bitMatrix.getRow(++x, null).toString().replaceAll("\\s+", ""));
            System.out.println(bitMatrix.getRow(++x, null).toString().replaceAll("\\s+", ""));
            System.out.println(bitMatrix.getRow(++x, null).toString().replaceAll("\\s+", ""));
            System.out.println(bitMatrix.getRow(++x, null).toString().replaceAll("\\s+", ""));
            System.out.println(bitMatrix.getRow(++x, null).toString().replaceAll("\\s+", ""));
            System.out.println(bitMatrix.getRow(++x, null).toString().replaceAll("\\s+", ""));
            System.out.println(bitMatrix.getRow(++x, null).toString().replaceAll("\\s+", ""));
            System.out.println(bitMatrix.getRow(++x, null).toString().replaceAll("\\s+", ""));
            System.out.println(bitMatrix.getRow(++x, null).toString().replaceAll("\\s+", ""));
            System.out.println(bitMatrix.getRow(++x, null).toString().replaceAll("\\s+", ""));
            System.out.println(bitMatrix.getRow(++x, null).toString().replaceAll("\\s+", ""));
            System.out.println(bitMatrix.getRow(++x, null).toString().replaceAll("\\s+", ""));
            System.out.println(bitMatrix.getRow(++x, null).toString().replaceAll("\\s+", ""));
            System.out.println(bitMatrix.getRow(++x, null).toString().replaceAll("\\s+", ""));
            System.out.println(bitMatrix.getRow(++x, null).toString().replaceAll("\\s+", ""));
            System.out.println(bitMatrix.getRow(++x, null).toString().replaceAll("\\s+", ""));
            System.out.println(bitMatrix.getRow(++x, null).toString().replaceAll("\\s+", ""));
            System.out.println(bitMatrix.getRow(++x, null).toString().replaceAll("\\s+", ""));
            System.out.println(bitMatrix.getRow(++x, null).toString().replaceAll("\\s+", ""));
            System.out.println(bitMatrix.getRow(++x, null).toString().replaceAll("\\s+", ""));
            System.out.println(bitMatrix.getRow(++x, null).toString().replaceAll("\\s+", ""));
            System.out.println(bitMatrix.getRow(++x, null).toString().replaceAll("\\s+", ""));
            System.out.println(bitMatrix.getRow(++x, null).toString().replaceAll("\\s+", ""));
            System.out.println(bitMatrix.getRow(++x, null).toString().replaceAll("\\s+", ""));
            System.out.println(bitMatrix.getRow(++x, null).toString().replaceAll("\\s+", ""));
            String dx = bitMatrix.getRow(x, null).toString().replaceAll("\\s+", "");
            String sx = bitMatrix.toString();
            System.out.println(bitMatrix);
            System.out.println(sx.length());
            System.out.println(dx.length());

            for (int i = 0; i < 60; i++) {
                for (int j = 0; j < 60; j++) {
                    System.out.print(bitMatrix.get(i, j) ? "!" : "#");
                }
                System.out.println(",");
            }
*/
            ByteArrayOutputStream pngOutputStream =
                    new ByteArrayOutputStream();
            MatrixToImageConfig con =
                    new MatrixToImageConfig(0xFF000000, 0xFFFFFFFF);
            MatrixToImageWriter.writeToStream
                    (bitMatrix, "PNG", pngOutputStream, con);
            byte[] pngData = pngOutputStream.toByteArray();

            Image securedby = new Image(ImageDataFactory.create(Objects.requireNonNull(MainJ.class.getResource("securedby.png"))));
            securedby.scale(0.33f, 0.33f);
            securedby.setRotationAngle(Math.PI / 2);
            securedby.setHorizontalAlignment(HorizontalAlignment.LEFT);
            document.add(securedby);

            Image image = new Image(ImageDataFactory.create(pngData));
            image.scaleAbsolute(350f, 350f);
            image.setFixedPosition(200, 100);

            document.add(image);
            document.close();
        } catch (WriterException | IOException e) {
            System.err.println(e.getMessage());
        }
    }

    static byte[] concat(byte[] x, byte[] y) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        outputStream.write(x);
        outputStream.write(y);
        return outputStream.toByteArray();
    }

    public static String getCertificate(String label, String publicKey) {
        return jsonAdapterC.toJson(new Certificate(label, publicKey));
    }

    public static void main(String[] args) {
        Clipboard clippy = Toolkit.getDefaultToolkit().getSystemClipboard();

        StringSelection tdata = new StringSelection("secretpass");
        clippy.setContents(tdata, tdata);


        Scanner scanIn = new Scanner(System.in);
        String password = scanIn.nextLine();
        //String password = "mypass";
        password = scanIn.nextLine();
        //clear clipboard
        clippy.setContents(new StringSelection(""), tdata);
        password = scanIn.nextLine();

        scanIn.close();

    }
    private static boolean isDockerEnvironment = false;

    public static void setDockerEnvironment(boolean isDocker) {
        isDockerEnvironment = isDocker;
    }

    public static String getPath() {
        if (isDockerEnvironment) {
            return "/app/output/";
        } else {
            return System.getProperty("user.home") + "/.Triceratops/";
        }
    }
}