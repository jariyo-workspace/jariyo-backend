package com.example.jariyo_backend.domain.user.service;

import java.util.Optional;
import java.util.UUID;
import com.example.jariyo_backend.common.error.BusinessException;
import com.example.jariyo_backend.domain.user.entity.StoreMember;
import com.example.jariyo_backend.domain.user.entity.StoreMemberRole;
import com.example.jariyo_backend.domain.user.entity.UserAccount;
import com.example.jariyo_backend.domain.user.repository.StoreMemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StoreAuthorizationServiceTests {
	@Mock StoreMemberRepository storeMemberRepository;

	@Test
	void grantsOnlyActiveMembershipWithSufficientRole() {
		UUID userId = UUID.randomUUID();
		UUID storeId = UUID.randomUUID();
		UserAccount user = new UserAccount("staff@example.com", "+821012345678", "hash");
		StoreMember manager = new StoreMember(storeId, user, StoreMemberRole.MANAGER, "매니저", false);
		when(storeMemberRepository.findByUser_IdAndStoreId(userId, storeId)).thenReturn(Optional.of(manager));
		StoreAuthorizationService service = new StoreAuthorizationService(storeMemberRepository);

		assertSame(manager, service.requireRole(userId, storeId, StoreMemberRole.STAFF));
		assertThrows(BusinessException.class,
			() -> service.requireRole(userId, storeId, StoreMemberRole.OWNER));
	}

	@Test
	void deniesMembershipFromAnotherStore() {
		UUID userId = UUID.randomUUID();
		UUID otherStoreId = UUID.randomUUID();
		when(storeMemberRepository.findByUser_IdAndStoreId(userId, otherStoreId)).thenReturn(Optional.empty());

		assertThrows(BusinessException.class, () -> new StoreAuthorizationService(storeMemberRepository)
			.requireRole(userId, otherStoreId, StoreMemberRole.STAFF));
	}
}
