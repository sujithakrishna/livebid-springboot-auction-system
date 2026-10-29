package com.sujitha.livebidspringboot;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class GeneratePasswordHash {
    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

        String newPassword = "LiveBidTest@2026";
        System.out.println(new BCryptPasswordEncoder().encode(newPassword));
    }
}

