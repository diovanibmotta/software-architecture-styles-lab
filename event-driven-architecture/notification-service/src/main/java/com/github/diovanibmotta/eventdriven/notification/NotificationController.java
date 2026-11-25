package com.github.diovanibmotta.eventdriven.notification;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
class NotificationController {

    private final NotificationService notifications;

    NotificationController(NotificationService notifications) {
        this.notifications = notifications;
    }

    @GetMapping("/notifications")
    List<NotificationService.Notification> list(@RequestParam String customerId) {
        return notifications.forCustomer(customerId);
    }
}
