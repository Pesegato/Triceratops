public class TokenPart {
    String name;
    Token.Color color;
    String data;
    int index;

    public TokenPart(Token t, String data, int index){
        this.name=t.name;
        this.color=t.color;
        this.data=data;
        this.index=index;
    }
}
