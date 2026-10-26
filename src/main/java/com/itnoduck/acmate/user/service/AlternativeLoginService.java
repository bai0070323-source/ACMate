package com.itnoduck.acmate.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.itnoduck.acmate.common.exception.BusinessException;
import com.itnoduck.acmate.config.LoginProperties;
import com.itnoduck.acmate.security.DatabaseUserDetailsService;
import com.itnoduck.acmate.user.dto.LoginResponse;
import com.itnoduck.acmate.user.entity.AppUser;
import com.itnoduck.acmate.user.mapper.AppUserMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Locale;

@Service
public class AlternativeLoginService {
    private final LoginProperties properties;
    private final ObjectProvider<JavaMailSender> mail;
    private final JdbcTemplate jdbc;
    private final AppUserMapper users;
    private final PasswordEncoder encoder;
    private final DatabaseUserDetailsService details;
    private final SecurityContextRepository contexts;
    private final SessionAuthenticationStrategy sessions;
    private final SecureRandom random = new SecureRandom();

    public AlternativeLoginService(LoginProperties properties, ObjectProvider<JavaMailSender> mail,
            JdbcTemplate jdbc, AppUserMapper users, PasswordEncoder encoder, DatabaseUserDetailsService details,
            SecurityContextRepository contexts, SessionAuthenticationStrategy sessions) {
        this.properties = properties; this.mail = mail; this.jdbc = jdbc; this.users = users;
        this.encoder = encoder; this.details = details; this.contexts = contexts; this.sessions = sessions;
    }

    public boolean emailAvailable() {
        return properties.isEmailEnabled() && !properties.getMailFrom().isBlank() && mail.getIfAvailable() != null;
    }

    public void sendCode(String address, String ip) {
        if (!emailAvailable()) throw new BusinessException(503, "邮箱登录尚未配置，请使用账号密码登录");
        String email = normalizeEmail(address);
        // Shared database counters apply across sessions and application instances. Never trust X-Forwarded-For here.
        jdbc.update("DELETE FROM login_rate_limit WHERE expires_at < NOW()");
        jdbc.update("DELETE FROM login_email_code WHERE expires_at < NOW()");
        limit("ip:" + ip, 3600, 10);
        limit("email:" + email, 60, 1);
        // Send to new addresses too; a valid code will verify mailbox ownership before account creation.
        // Send the same response and email flow regardless of account state to avoid account enumeration.
        String code = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
        String hash = encoder.encode(code);
        jdbc.update("INSERT INTO login_email_code(email,code_hash,expires_at,attempts) VALUES(?,?,DATE_ADD(NOW(), INTERVAL 5 MINUTE),0) "
                + "ON DUPLICATE KEY UPDATE code_hash=VALUES(code_hash),expires_at=VALUES(expires_at),attempts=0", email, hash);
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.getMailFrom()); message.setTo(email);
        message.setSubject("ACMate 登录验证码");
        message.setText("你的 ACMate 登录验证码为：" + code + "\n有效期为 5 分钟，请勿转发给他人。如非本人操作，请忽略此邮件。");
        try { mail.getObject().send(message); }
        catch (org.springframework.mail.MailException ex) {
            jdbc.update("DELETE FROM login_email_code WHERE email=? AND code_hash=?", email, hash);
            throw new BusinessException(503, "邮件发送失败，请稍后重试或使用账号密码登录");
        }
    }

    private void limit(String key, int seconds, int maximum) {
        String bucket = digest(key) + ":" + seconds;
        jdbc.update("INSERT IGNORE INTO login_rate_limit(bucket_key,hits,expires_at) VALUES(?,0,DATE_ADD(NOW(), INTERVAL ? SECOND))", bucket, seconds);
        if (jdbc.update("UPDATE login_rate_limit SET hits=hits+1 WHERE bucket_key=? AND hits<?", bucket, maximum) != 1)
            throw new BusinessException(429, "验证码请求过于频繁，请稍后重试");
    }

    @Transactional
    public LoginResponse emailLogin(String address, String code, HttpServletRequest request, HttpServletResponse response) {
        if (!emailAvailable()) throw new BusinessException(503, "邮箱登录尚未配置，请使用账号密码登录");
        String email = normalizeEmail(address);
        if (jdbc.update("UPDATE login_email_code SET attempts=attempts+1 WHERE email=? AND expires_at>NOW() AND attempts<5", email) != 1)
            throw invalidCode();
        var hashes = jdbc.queryForList("SELECT code_hash FROM login_email_code WHERE email=? AND expires_at>NOW()", String.class, email);
        if (hashes.isEmpty() || !encoder.matches(code, hashes.get(0))) throw invalidCode();
        if (jdbc.update("DELETE FROM login_email_code WHERE email=? AND code_hash=? AND expires_at>NOW()", email, hashes.get(0)) != 1)
            throw invalidCode();
        AppUser user = users.selectOne(new LambdaQueryWrapper<AppUser>().eq(AppUser::getEmail, email));
        if (user == null) user = createEmailAccount(email);
        if (!Integer.valueOf(1).equals(user.getStatus())) throw new DisabledException("账号已被禁用");
        return signIn(user.getUsername(), request, response);
    }

    private AppUser createEmailAccount(String email) {
        for (int attempt = 0; attempt < 3; attempt++) {
            AppUser user = new AppUser();
            user.setUsername("mail_" + randomHex(12));
            user.setPasswordHash(encoder.encode(randomHex(32)));
            user.setNickname("邮箱用户_" + randomHex(6));
            user.setEmail(email);
            user.setIsAdmin(0);
            user.setStatus(1);
            try {
                if (users.insert(user) != 1 || user.getId() == null) {
                    throw new BusinessException(500, "自动注册失败，请稍后重试");
                }
                return user;
            } catch (org.springframework.dao.DuplicateKeyException ex) {
                // A normal registration may have claimed this address after the code was sent.
                AppUser existing = users.selectOne(new LambdaQueryWrapper<AppUser>().eq(AppUser::getEmail, email));
                if (existing != null) return existing;
                if (attempt == 2) throw new BusinessException(409, "账号创建冲突，请重新获取验证码");
            }
        }
        throw new BusinessException(500, "自动注册失败，请稍后重试");
    }

    private String randomHex(int byteCount) {
        byte[] bytes = new byte[byteCount];
        random.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    public LoginResponse signIn(String username, HttpServletRequest request, HttpServletResponse response) {
        var principal = details.loadUserByUsername(username);
        if (!principal.isEnabled()) throw new DisabledException("账号已被禁用");
        var authentication = UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities());
        sessions.onAuthentication(authentication, request, response);
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        contexts.saveContext(context, request, response);
        return new LoginResponse(principal.getId(), principal.getUsername(), principal.getNickname(), principal.getEmail(),
                principal.getAvatarUrl(), principal.getBio(), principal.isAdmin());
    }

    static String normalizeEmail(String email) { return email.strip().toLowerCase(Locale.ROOT); }
    private BusinessException invalidCode() { return new BusinessException(401, "验证码错误、已过期或尝试次数过多，请重新获取"); }
    private String digest(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
}
