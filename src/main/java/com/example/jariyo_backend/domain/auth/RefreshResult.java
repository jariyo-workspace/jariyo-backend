package com.example.jariyo_backend.domain.auth;

import com.example.jariyo_backend.domain.user.UserAccount;

public record RefreshResult(UserAccount user, String refreshToken) {
}
