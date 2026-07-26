package com.example.jariyo_backend.domain.user.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.example.jariyo_backend.domain.user.dto.MeResponse;
import com.example.jariyo_backend.domain.user.entity.CustomerProfile;
import com.example.jariyo_backend.domain.user.entity.StoreMember;
import com.example.jariyo_backend.domain.user.entity.StoreMemberRole;
import com.example.jariyo_backend.domain.user.entity.UserAccount;
import com.example.jariyo_backend.domain.user.repository.CustomerProfileRepository;
import com.example.jariyo_backend.domain.user.repository.StoreMemberRepository;
import com.example.jariyo_backend.domain.user.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MeServiceTests {
	@Mock UserAccountRepository userAccountRepository;
	@Mock CustomerProfileRepository customerProfileRepository;
	@Mock StoreMemberRepository storeMemberRepository;

	@Test
	void returnsMaskedUserProfileWithMemberships() {
		UUID userId = UUID.randomUUID();
		UUID storeId = UUID.randomUUID();
		UserAccount user = new UserAccount("user@example.com", "+821012345678", "hash");
		CustomerProfile profile = new CustomerProfile(user, "류승엽", false, true);
		StoreMember member = new StoreMember(storeId, user, StoreMemberRole.STAFF, "민지", true);
		MeService service = new MeService(userAccountRepository, customerProfileRepository, storeMemberRepository);

		when(userAccountRepository.findById(userId)).thenReturn(Optional.of(user));
		when(customerProfileRepository.findByUser_Id(userId)).thenReturn(Optional.of(profile));
		when(storeMemberRepository.findAllByUser_IdOrderByCreatedAtAsc(userId)).thenReturn(List.of(member));

		MeResponse response = service.get(userId);

		assertEquals("user@example.com", response.email());
		assertEquals("류승엽", response.displayName());
		assertEquals("010-****-5678", response.phoneNumber());
		assertNotNull(response.customerProfile());
		assertEquals(1, response.storeMemberships().size());
		assertEquals(storeId, response.storeMemberships().get(0).storeId());
		assertEquals(StoreMemberRole.STAFF, response.storeMemberships().get(0).role());
	}

	@Test
	void returnsNullCustomerProfileWhenProfileDoesNotExist() {
		UUID userId = UUID.randomUUID();
		UserAccount user = new UserAccount("user@example.com", "+821012345678", "hash");
		MeService service = new MeService(userAccountRepository, customerProfileRepository, storeMemberRepository);

		when(userAccountRepository.findById(userId)).thenReturn(Optional.of(user));
		when(customerProfileRepository.findByUser_Id(userId)).thenReturn(Optional.empty());
		when(storeMemberRepository.findAllByUser_IdOrderByCreatedAtAsc(userId)).thenReturn(List.of());

		MeResponse response = service.get(userId);

		assertNull(response.displayName());
		assertNull(response.customerProfile());
		assertEquals(0, response.storeMemberships().size());
	}
}
