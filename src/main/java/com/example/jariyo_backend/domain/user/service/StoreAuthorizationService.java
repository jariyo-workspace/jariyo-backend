package com.example.jariyo_backend.domain.user.service;

import java.util.UUID;
import com.example.jariyo_backend.common.error.BusinessException;
import com.example.jariyo_backend.common.error.ErrorCode;
import com.example.jariyo_backend.domain.user.entity.StoreMember;
import com.example.jariyo_backend.domain.user.entity.StoreMemberRole;
import com.example.jariyo_backend.domain.user.entity.StoreMemberStatus;
import com.example.jariyo_backend.domain.user.repository.StoreMemberRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service("storeAuthorization")
public class StoreAuthorizationService {
	private final StoreMemberRepository storeMemberRepository;

	public StoreAuthorizationService(StoreMemberRepository storeMemberRepository) {
		this.storeMemberRepository = storeMemberRepository;
	}

	@Transactional(readOnly = true)
	@PreAuthorize("#userId.toString() == authentication.name")
	public StoreMember requireRole(UUID userId, UUID storeId, StoreMemberRole requiredRole) {
		StoreMember member = storeMemberRepository.findByUser_IdAndStoreId(userId, storeId)
			.orElseThrow(() -> new BusinessException(ErrorCode.STORE_ACCESS_DENIED));
		if (member.getStatus() != StoreMemberStatus.ACTIVE || !member.getRole().includes(requiredRole)) {
			throw new BusinessException(ErrorCode.STORE_ACCESS_DENIED);
		}
		return member;
	}
}
