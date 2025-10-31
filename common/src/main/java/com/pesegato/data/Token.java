package com.pesegato.data;

public class Token {
    String label;
    Color color;
    TokenPart[] parts;

    public Token(String label, String color) {
        this.label=label;
        this.color=Color.valueOf(color.toUpperCase());
    }

    public enum Color {
        GREEN,
        RED,
        BLUE,
        WHITE,
        GRAY,
        BLACK
    }

}
