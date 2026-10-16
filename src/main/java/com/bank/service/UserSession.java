package com.bank.service;

import com.bank.model.User;

import java.time.LocalDateTime;

/**
 * Manages authenticated user session state for the console application.
 */
public class UserSession {

    private User currentUser;
    private LocalDateTime loginTime;

    public synchronized void login(User user) {
        if (user == null) {
            throw new IllegalArgumentException("Cannot create session for null user");
        }
        this.currentUser = user;
        this.loginTime = LocalDateTime.now();
    }

    public synchronized void logout() {
        this.currentUser = null;
        this.loginTime = null;
    }

    public synchronized boolean isLoggedIn() {
        return this.currentUser != null;
    }

    public synchronized User getCurrentUser() {
        return this.currentUser;
    }

    public synchronized Long getCurrentUserId() {
        return this.currentUser != null ? this.currentUser.getUserId() : null;
    }

    public synchronized LocalDateTime getLoginTime() {
        return this.loginTime;
    }
}
