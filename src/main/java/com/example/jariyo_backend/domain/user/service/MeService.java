package com.example.jariyo_backend.domain.user.service;

import java.util.List;
import java.util.UUID;
import com.example.jariyo_backend.common.error.BusinessException;
import com.example.jariyo_backend.common.error.ErrorCode;
import com.example.jariyo_backend.domain.auth.support.PhoneNumberNormalizer;
import com.example.jariyo_backend.domain.user.dto.MeResponse;
import com.example.jariyo_backend.domain.user.entity.CustomerProfile;
import com.example.jariyo_backend.domain.user.entity.UserAccount;
import com.example.jariyo_backend.domain.user.repository.CustomerProfileRepository;
import com.example.jariyo_backend.domain.user.repository.StoreMemberRepository;
import com.example.jariyo_backend.domain.user.repository.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MeService {
	private final UserAccountRepository userAccountRepository;
	private final CustomerProfileRepository customerProfileRepository;
	private final StoreMemberRepository storeMemberRepository;

	public MeService(UserAccountRepository userAccountRepository,
		CustomerProfileRepository customerProfileRepository,
		StoreMemberRepository storeMemberRepository) {
		this.userAccountRepository = userAccountRepository;
		this.customerProfileRepository = customerProfileRepository;
		this.storeMemberRepository = storeMemberRepository;
	}

	@Transactional(readOnly = true)
	public MeResponse get(UUID userId) {
		UserAccount user = userAccountRepository.findById(userId)
			.orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
		CustomerProfile profile = customerProfileRepository.findByUser_Id(userId).orElse(null);
		List<MeResponse.StoreMembershipResponse> memberships = storeMemberRepository
			.findAllByUser_IdOrderByCreatedAtAsc(userId).stream()
			.map(member -> new MeResponse.StoreMembershipResponse(
				member.getStoreId(), member.getRole(), member.getStatus()))
			.toList();
		return new MeResponse(
			user.getId(),
			user.getEmail(),
			profile == null ? null : profile.getDisplayName(),
			PhoneNumberNormalizer.mask(user.getPhoneNumber()),
			profile == null ? null : new MeResponse.CustomerProfileResponse(
				profile.isNotificationConsent(), profile.isMarketingConsent()),
			memberships);
	}
}
