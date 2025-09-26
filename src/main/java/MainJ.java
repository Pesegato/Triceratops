import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageConfig;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.lowagie.text.*;
import com.lowagie.text.Image;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfWriter;
import com.pesegato.data.Certificate;
import com.pesegato.data.Token;
import com.pesegato.data.TokenPart;
import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.Moshi;

import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.PublicKey;
import java.util.*;

import static java.nio.charset.StandardCharsets.UTF_8;

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
public class MainJ {
    static int SIZE = 128;
    static int parts = 0;
    static String version;
    static Moshi moshi = new Moshi.Builder().build();
    static JsonAdapter<TokenPart> jsonAdapterTP = moshi.adapter(TokenPart.class);
    static JsonAdapter<Certificate> jsonAdapterC = moshi.adapter(Certificate.class);

    public static void buildToken(String label, String color, String secret, int parts) throws IOException {

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

        TokenPart p0 = new TokenPart(token, A64, 0);

        //String json = jsonAdapter.toJson(p0);
        //System.out.println(json);

        writePDF(p0);
        for (int i = 0; i < parts; i++) {
            System.out.println(" DATA64[" + (i + 1) + "]=\"" + DATA64[i] + "\";");
            writePDF(new TokenPart(token, DATA64[i], i + 1));
        }
        System.out.println(" DATA64[" + (parts + 1) + "]=\"" + C64 + "\";");
        writePDF(new TokenPart(token, C64, (parts + 1)));

        //System.out.println("r:" + new String(AB64));
        //System.out.println("g:" + new String(AC64));
        //System.out.println("b:" + new String(BC64));


    }

    private static void writePDF(TokenPart tokenPart) {
        Document document = new Document();
        try {
            document.setPageSize(PageSize.A4);
            PdfWriter pdfWriter = PdfWriter.getInstance(document, new FileOutputStream("tk_" + tokenPart.getIndex() + ".pdf"));
            document.open();

            PdfContentByte cb = pdfWriter.getDirectContent();

            BaseFont bf = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
            cb.beginText();
            cb.setFontAndSize(bf, 12);
            cb.showTextAligned(PdfContentByte.ALIGN_LEFT, "LABEL:________", 70, 690, 0);
            cb.showTextAligned(PdfContentByte.ALIGN_LEFT, "COLOR:________", 200, 690, 0);
            cb.showTextAligned(PdfContentByte.ALIGN_LEFT, "PART:________", 330, 690, 0);
            cb.showTextAligned(PdfContentByte.ALIGN_LEFT, "CODE:________", 460, 690, 0);
            bf = BaseFont.createFont(BaseFont.COURIER, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
            cb.setFontAndSize(bf, 12);
            cb.showTextAligned(PdfContentByte.ALIGN_LEFT, tokenPart.getLabel(), 120, 692, 0);
            cb.showTextAligned(PdfContentByte.ALIGN_LEFT, tokenPart.getColor().toString(), 250, 692, 0);
            cb.showTextAligned(PdfContentByte.ALIGN_LEFT, String.valueOf(tokenPart.getIndex()), 380, 692, 0);
            cb.showTextAligned(PdfContentByte.ALIGN_LEFT, "4589", 510, 692, 0);
            cb.showTextAligned(PdfContentByte.ALIGN_LEFT, "Forged on " + new Date() + " with version " + version, 70, 50, 0);
            cb.setFontAndSize(bf, 5);
            bf = BaseFont.createFont(BaseFont.TIMES_ROMAN, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
            cb.setFontAndSize(bf, 12);
            /*
            cb.showTextAligned(PdfContentByte.ALIGN_LEFT, "Gentile " + gui.paziente.getText() + ", il Suo appuntamento è il " + df.format(model.getValue()) + ".", 70, 470, 0);
            cb.showTextAligned(PdfContentByte.ALIGN_LEFT, "La sera prima inizi la terapia antibiotica:", 70, 450, 0);
            cb.showTextAligned(PdfContentByte.ALIGN_LEFT, "Amoxicillina + Acido Clavulanico 1 gr. 1 cpr ogni 12 ore per 6 gg", 70, 430, 0);
            cb.showTextAligned(PdfContentByte.ALIGN_LEFT, "Inquadrare il codice con l'app DRM ti ricorda.", 70, 270, 0);

             */
            cb.endText();

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

            Image securedby = Image.getInstance(MainJ.class.getResource("securedby.png"));
            securedby.scalePercent(33);
            securedby.setAlignment(Element.ALIGN_CENTER);
            document.add(securedby);

            Image image = Image.getInstance(pngData);
            image.scaleAbsolute(350f, 350f);
            image.setAlignment(Element.ALIGN_CENTER);
            image.setAbsolutePosition(100, 100);

            document.add(image);
        } catch (DocumentException | WriterException de) {
            System.err.println(de.getMessage());
        } catch (IOException ioe) {
            System.err.println(ioe.getMessage());
        }
        document.close();
    }

    static byte[] concat(byte[] x, byte[] y) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        outputStream.write(x);
        outputStream.write(y);
        return outputStream.toByteArray();
    }

    public static byte[] getCertificate(String label, String publicKey){
        return jsonAdapterC.toJson(new Certificate(label, publicKey)).getBytes(UTF_8);
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
}