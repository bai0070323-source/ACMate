package com.itnoduck.acmate.user.service;

import com.itnoduck.acmate.common.exception.BusinessException;
import com.itnoduck.acmate.config.LoginProperties;
import com.itnoduck.acmate.security.DatabaseUserDetailsService;
import com.itnoduck.acmate.user.mapper.AppUserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import com.itnoduck.acmate.user.entity.AppUser;
import com.itnoduck.acmate.security.AuthenticatedUser;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mail.SimpleMailMessage;
import org.mockito.ArgumentCaptor;

class AlternativeLoginServiceTest {
    final LoginProperties properties = new LoginProperties();
    @SuppressWarnings("unchecked") final ObjectProvider<JavaMailSender> mail = mock(ObjectProvider.class);
    final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    final AppUserMapper users = mock(AppUserMapper.class);
    final DatabaseUserDetailsService details = mock(DatabaseUserDetailsService.class);
    final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    final AlternativeLoginService service = new AlternativeLoginService(properties, mail, jdbc, users, encoder, details,
            mock(SecurityContextRepository.class), mock(SessionAuthenticationStrategy.class));

    void enable() {
        properties.setEmailEnabled(true); properties.setMailFrom("site@example.com");
        when(mail.getIfAvailable()).thenReturn(mock(JavaMailSender.class));
    }

    @Test void missingConfigurationFailsBeforeAccessingDatabase() {
        assertEquals(503, assertThrows(BusinessException.class, () -> service.sendCode("user@example.com", "127.0.0.1")).getCode());
        verifyNoInteractions(jdbc, users);
    }

    @Test void expiredOrExhaustedChallengeCannotLogin() {
        enable();
        assertEquals(401, assertThrows(BusinessException.class, () -> service.emailLogin("user@example.com", "123456", null, null)).getCode());
        verifyNoInteractions(details, users);
    }

    @Test void mismatchedCodeCannotEstablishSession() {
        enable();
        when(jdbc.update(startsWith("UPDATE login_email_code"), eq("user@example.com"))).thenReturn(1);
        when(jdbc.queryForList(anyString(), eq(String.class), eq("user@example.com"))).thenReturn(List.of(encoder.encode("654321")));
        assertEquals(401, assertThrows(BusinessException.class, () -> service.emailLogin(" USER@example.com ", "123456", null, null)).getCode());
        verifyNoInteractions(details, users);
    }

    @Test void concurrentlyConsumedCodeCannotLoginTwice() {
        enable();
        when(jdbc.update(startsWith("UPDATE login_email_code"), eq("user@example.com"))).thenReturn(1);
        when(jdbc.queryForList(anyString(), eq(String.class), eq("user@example.com"))).thenReturn(List.of(encoder.encode("123456")));
        // DELETE returns zero: another request consumed the code first.
        assertEquals(401, assertThrows(BusinessException.class, () -> service.emailLogin("user@example.com", "123456", null, null)).getCode());
        verifyNoInteractions(details, users);
    }

    @Test void validCodeLogsInExistingUserAndUpdatesSession() {
        enable();
        when(jdbc.update(startsWith("UPDATE login_email_code"), eq("user@example.com"))).thenReturn(1);
        String hash = encoder.encode("123456");
        when(jdbc.queryForList(anyString(), eq(String.class), eq("user@example.com"))).thenReturn(List.of(hash));
        when(jdbc.update(startsWith("DELETE FROM login_email_code"), eq("user@example.com"), eq(hash))).thenReturn(1);
        AppUser user = new AppUser(); user.setUsername("existing"); user.setStatus(1);
        when(users.selectOne(any())).thenReturn(user);
        when(details.loadUserByUsername("existing")).thenReturn(new AuthenticatedUser(1L, "existing", "hash", "用户", "user@example.com",
                null, null, false, true, List.of()));
        var response = service.emailLogin("user@example.com", "123456", new MockHttpServletRequest(), new MockHttpServletResponse());
        assertEquals("existing", response.username());
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    @Test void disabledUserCannotEstablishSession() {
        when(details.loadUserByUsername("disabled")).thenReturn(new AuthenticatedUser(1L, "disabled", "hash", "用户", null,
                null, null, false, false, List.of()));
        assertThrows(org.springframework.security.authentication.DisabledException.class,
                () -> service.signIn("disabled", new MockHttpServletRequest(), new MockHttpServletResponse()));
    }

    @Test void sendsSixDigitCodeWithoutReturningIt() {
        enable();
        JavaMailSender sender = mock(JavaMailSender.class);
        when(mail.getObject()).thenReturn(sender);
        when(jdbc.update(startsWith("UPDATE login_rate_limit"), anyString(), anyInt())).thenReturn(1);
        AppUser user = new AppUser(); user.setStatus(1);
        when(users.selectOne(any())).thenReturn(user);
        service.sendCode(" USER@example.com ", "127.0.0.1");
        ArgumentCaptor<SimpleMailMessage> capture = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(capture.capture());
        assertArrayEquals(new String[]{"user@example.com"}, capture.getValue().getTo());
        assertTrue(capture.getValue().getText().matches("(?s).*验证码为：[0-9]{6}.*"));
    }
}
