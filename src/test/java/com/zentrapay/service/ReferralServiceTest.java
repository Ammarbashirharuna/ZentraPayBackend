package com.zentrapay.service;

import com.zentrapay.entity.Referral;
import com.zentrapay.entity.User;
import com.zentrapay.repository.ReferralRepository;
import com.zentrapay.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReferralServiceTest {

    @Mock ReferralRepository referralRepository;
    @Mock UserRepository userRepository;
    @InjectMocks ReferralService service;

    private void mockCurrentUser(String email) {
        User user = User.builder().id(UUID.randomUUID()).email(email).fullName("Test").build();
        SecurityContext ctx = mock(SecurityContext.class);
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn(email);
        when(ctx.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(ctx);
        when(userRepository.findByEmailIgnoreCase(email)).thenReturn(Optional.of(user));
        ReflectionTestUtils.setField(service, "appBaseUrl", "http://localhost:8080");
    }

    @Test
    void createsReferralCodeOnFirstAccess() {
        mockCurrentUser("seller@test.com");
        when(referralRepository.findByUserId(any())).thenReturn(Optional.empty());
        when(referralRepository.save(any(Referral.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = service.getMyReferral();

        assertThat(response.getReferralCode()).isNotNull();
        assertThat(response.getReferralCode()).hasSize(8);
        assertThat(response.getUsedCount()).isEqualTo(0);
        verify(referralRepository).save(any(Referral.class));
    }

    @Test
    void returnsExistingReferralCode() {
        mockCurrentUser("seller@test.com");
        Referral existing = Referral.builder()
                .userId(UUID.randomUUID())
                .referralCode("ABC12345")
                .usedCount(3)
                .totalEarnings(0L)
                .build();
        when(referralRepository.findByUserId(any())).thenReturn(Optional.of(existing));

        var response = service.getMyReferral();

        assertThat(response.getReferralCode()).isEqualTo("ABC12345");
        assertThat(response.getUsedCount()).isEqualTo(3);
        verify(referralRepository, never()).save(any());
    }

    @Test
    void appliesReferralCodeAndIncrementsCount() {
        Referral referral = Referral.builder()
                .userId(UUID.randomUUID())
                .referralCode("XYZ99999")
                .usedCount(5)
                .totalEarnings(0L)
                .build();
        when(referralRepository.findByReferralCodeIgnoreCase("XYZ99999"))
                .thenReturn(Optional.of(referral));
        when(referralRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.applyReferralCode(UUID.randomUUID(), "XYZ99999");

        assertThat(referral.getUsedCount()).isEqualTo(6);
        verify(referralRepository).save(referral);
    }

    @Test
    void doesNothingWithBlankReferralCode() {
        service.applyReferralCode(UUID.randomUUID(), "  ");
        verifyNoInteractions(referralRepository);
    }
}
