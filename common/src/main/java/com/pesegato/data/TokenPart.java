package com.pesegato.data;

public class TokenPart {
    String label;
    Token.Color color;
    String data;
    int index;
    int maxIndex;

    public TokenPart(Token t, String data, int index, int maxIndex) {
        this.label = t.label;
        this.color = t.color;
        this.data = data;
        this.index = index;
        this.maxIndex = maxIndex;
    }

    public String getLabel() {
        return label;
    }

    public Token.Color getColor() {
        return color;
    }

    public int getPrettyIndex() {
        return index + 1;
    }

    public String getPrettyPrintParts() {
        return getPrettyIndex() + "/" + maxIndex;
    }

    public String getPrettyName() {
        return "tk_" + label + "_" + getPrettyIndex();
    }
}
