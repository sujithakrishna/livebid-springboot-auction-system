package com.sujitha.livebidspringboot.controller;

import com.sujitha.livebidspringboot.dao.NotificationDAO;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users/me")
public class NotificationController {

    private final NotificationDAO notificationDAO;

    public NotificationController(NotificationDAO notificationDAO) {
        this.notificationDAO = notificationDAO;
    }

    @GetMapping("/notifications")
    public List<Map<String, Object>> getMyNotifications(
            Authentication authentication) {

        long userId = (Long) authentication.getPrincipal();

        return notificationDAO.findByUserId(userId);
    }
}