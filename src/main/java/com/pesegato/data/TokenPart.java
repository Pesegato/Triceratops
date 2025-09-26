package com.pesegato.data;

public class TokenPart {
    String label;
    Token.Color color;
    String data;
    int index;

    public TokenPart(Token t, String data, int index){
        this.label=t.label;
        this.color=t.color;
        this.data=data;
        this.index=index;
    }

    public String getLabel() {
        return label;
    }

    public Token.Color getColor() {
        return color;
    }

    public int getIndex(){
        return index;
    }
}
