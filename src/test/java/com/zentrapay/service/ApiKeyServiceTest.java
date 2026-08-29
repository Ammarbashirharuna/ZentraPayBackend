package com.zentrapay.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zentrapay.dto.apikey.CreateApiKeyRequest;
import com.zentrapay.entity.ApiKey;
import com.zentrapay.entity.User;
import com.zentrapay.repository.ApiKeyRepository;
import com.zentrapay.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApiKeyServiceTest {

    @Mock ApiKeyRepository apiKeyRepository;
    @Mock UserRepository userRepository;
    @Spy ObjectMapper objectMapper = new ObjectMapper();
    @InjectMocks ApiKeyService service;

    private void mockCurrentUser(String email) {
        User user = User.builder().id(UUID.randomUUID()).email(email).fullName("Test").build();
        SecurityContext ctx = mock(SecurityContext.class);
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn(email);
        when(ctx.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(ctx);
        when(userRepository.findByEmailIgnoreCase(email)).thenReturn(Optional.of(user));
    }

    @Test
    void createsApiKeyAndReturnsRawKey() {
        mockCurrentUser("seller@test.com");
        when(apiKeyRepository.countByUserIdAndIsActiveTrue(any())).thenReturn(2L);
        when(apiKeyRepository.save(any(ApiKey.class))).thenAnswer(inv -> {
            ApiKey key = inv.getArgument(0);
            key.setId(UUID.randomUUID());
            return key;
        });

        var response = service.createApiKey(new CreateApiKeyRequest("Production", null));

        assertThat(response.getRawKey()).startsWith("zp_");
        assertThat(response.getRawKey()).hasSize(67);
        assertThat(response.getName()).isEqualTo("Production");
        assertThat(response.getKeyPrefix()).startsWith("zp_");
    }

    @Test
    void rejectsWhenMaxKeysReached() {
        mockCurrentUser("seller@test.com");
        when(apiKeyRepository.countByUserIdAndIsActiveTrue(any())).thenReturn(10L);

        assertThatThrownBy(() -> service.createApiKey(new CreateApiKeyRequest("Too many", null)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void revokesApiKey() {
        mockCurrentUser("seller@test.com");
        UUID keyId = UUID.randomUUID();
        ApiKey key = ApiKey.builder().id(keyId).userId(UUID.randomUUID()).isActive(true).build();
        when(apiKeyRepository.findByIdAndUserId(eq(keyId), any(UUID.class))).thenReturn(Optional.of(key));
        when(apiKeyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.revokeApiKey(keyId);

        assertThat(key.getIsActive()).isFalse();
    }
}
