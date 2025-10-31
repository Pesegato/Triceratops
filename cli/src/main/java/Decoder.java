import java.util.Base64;

public class Decoder {

    static String red = null;
    static String green = null;
    static String blue = null;

    static byte[][] data=new byte[MainJ.parts+2][];



    public static void main(String[] args) {
        //while (a==null) {
            addAmber();
        /*}
        while (b==null) {
            addAmber();
        }
        while (c==null){
            addAmber();
        }*/

        /*
        System.out.println("\na");
        for (int i = 0; i < Main.SIZE; i++) {
            System.out.printf("%02X", a[i]);
        }
        System.out.println("\nb");
        for (int i = 0; i < Main.SIZE; i++) {
            System.out.printf("%02X", b[i]);
        }
        System.out.println("\nc");
        for (int i = 0; i < Main.SIZE; i++) {
            System.out.printf("%02X", c[i]);
        }
*/

        byte[] password = new byte[data[0].length];

        for (int i = 0; i < data[0].length; i++) {
            int c=data[0][i];
            for (int j=1;j<data.length;j++) {
                c=(byte)c^data[j][i];
                //password[i] = (byte) (a[i] ^ b[i] ^ c[i]);
            }
            password[i] = (byte) c;
        }
        System.out.println("Your password is " + new String(password).trim());
    }

    public static void addAmber() {
        System.out.println("Please input an amber:");
        //Scanner scanIn = new Scanner(System.in);
        //String amber = scanIn.nextLine();
        parseAmber(null);
        //System.out.println("Please input an amber:");
        //parseAmber(scanIn.nextLine());
        //scanIn.close();
    }

    private static void parseAmber(String amber) {
        String[] DATA64 =new String[MainJ.parts+2];

        DATA64[0]="nSV0HP/3HGM8toQPGNA/0kthOXNsYHxqL7xNlBTOSavKdjchYRF/75mFcsvDRwwIZ3fUY8CsgIzkyW/7RAvjmS0/26higvEz0abntsvD5DjWFoV9g0/Bd8DF3dyU76lZtaQZytmnFWkvETryFMG4ZtaHIoXoW9V/gL6CI3JfMZo=";
        DATA64[1]="8FwHeZz3HGM8toQPGNA/0kthOXNsYHxqL7xNlBTOSavKdjchYRF/75mFcsvDRwwIZ3fUY8CsgIzkyW/7RAvjmS0/26higvEz0abntsvD5DjWFoV9g0/Bd8DF3dyU76lZtaQZytmnFWkvETryFMG4ZtaHIoXoW9V/gL6CI3JfMZo=";

        for (int i=0;i<DATA64.length;i++)
            data[i]= Base64.getDecoder().decode(DATA64[i]);
        //b= Base64.getDecoder().decode(DATA64[1]);
        //c= Base64.getDecoder().decode(DATA64[2]);
        /*
        switch (amber.charAt(0)) {
            case 'r':
                red = amber.substring(2);
                byte[] ab = Base64.getDecoder().decode(red);
                a = Arrays.copyOfRange(ab, 0, Main.SIZE);
                b = Arrays.copyOfRange(ab, Main.SIZE, Main.SIZE * 2);
                break;
            case 'g':
                green = amber.substring(2);
                byte[] ac = Base64.getDecoder().decode(green);
                a = Arrays.copyOfRange(ac, 0, Main.SIZE);
                c = Arrays.copyOfRange(ac, Main.SIZE, Main.SIZE * 2);
                break;
            case 'b':
                blue = amber.substring(2);
                byte[] bc = Base64.getDecoder().decode(blue);
                b = Arrays.copyOfRange(bc, 0, Main.SIZE);
                c = Arrays.copyOfRange(bc, Main.SIZE, Main.SIZE * 2);
                break;
            default:
                System.out.println("Not valid");
        }

         */
    }
}
