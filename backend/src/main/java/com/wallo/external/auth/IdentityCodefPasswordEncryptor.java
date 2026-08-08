package com.wallo.external.auth;

public class IdentityCodefPasswordEncryptor implements CodefPasswordEncryptor {

    @Override
    public String encrypt(String password) {
        return password;
    }
}
