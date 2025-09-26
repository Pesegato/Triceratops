package com.pesegato.data;

public class Certificate {
    String name;
    //String version="1";
    String publicKey;

    public Certificate(String name, String publicKey) {
        this.name = name;
        this.publicKey = publicKey;
    }
}
