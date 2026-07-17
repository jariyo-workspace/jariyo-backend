package com.example.jariyo_backend.domain.auth.dto;

import com.example.jariyo_backend.domain.user.entity.UserAccount;

public record RefreshResult(UserAccount user, String refreshToken) {
}
