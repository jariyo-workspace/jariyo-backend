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
		StoreMember member = requireActiveMembership(userId, storeId);
		if (!member.getRole().includes(requiredRole)) {
			throw new BusinessException(ErrorCode.STORE_ACCESS_DENIED);
		}
		return member;
	}

	@Transactional(readOnly = true)
	@PreAuthorize("#userId.toString() == authentication.name")
	public StoreMember requireStaff(UUID userId, UUID storeId) {
		return requireRole(userId, storeId, StoreMemberRole.STAFF);
	}

	@Transactional(readOnly = true)
	@PreAuthorize("#userId.toString() == authentication.name")
	public StoreMember requireManager(UUID userId, UUID storeId) {
		return requireRole(userId, storeId, StoreMemberRole.MANAGER);
	}

	@Transactional(readOnly = true)
	@PreAuthorize("#userId.toString() == authentication.name")
	public StoreMember requireOwner(UUID userId, UUID storeId) {
		return requireRole(userId, storeId, StoreMemberRole.OWNER);
	}

	private StoreMember requireActiveMembership(UUID userId, UUID storeId) {
		StoreMember member = storeMemberRepository.findByUser_IdAndStoreId(userId, storeId)
			.orElseThrow(() -> new BusinessException(ErrorCode.STORE_ACCESS_DENIED));
		if (member.getStatus() != StoreMemberStatus.ACTIVE) {
			throw new BusinessException(ErrorCode.STORE_ACCESS_DENIED);
		}
		return member;
	}
}
