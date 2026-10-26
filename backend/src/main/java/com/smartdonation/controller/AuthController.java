package com.smartdonation.controller;

import com.smartdonation.dto.AuthDtos.LoginRequest;
import com.smartdonation.dto.AuthDtos.LoginResponse;
import com.smartdonation.dto.AuthDtos.RegisterRequest;
import com.smartdonation.dto.UserResponse;
import com.smartdonation.security.LoginRateLimiter;
import com.smartdonation.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final UserService userService;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository contextRepository;
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
    private final CsrfTokenRepository csrfTokenRepository;
    private final LoginRateLimiter loginRateLimiter;

    public AuthController(UserService userService, AuthenticationManager authenticationManager,
                          SecurityContextRepository contextRepository,
                          SessionAuthenticationStrategy sessionAuthenticationStrategy,
                          CookieCsrfTokenRepository csrfTokenRepository,
                          LoginRateLimiter loginRateLimiter) {
        this.userService = userService;
        this.authenticationManager = authenticationManager;
        this.contextRepository = contextRepository;
        this.sessionAuthenticationStrategy = sessionAuthenticationStrategy;
        this.csrfTokenRepository = csrfTokenRepository;
        this.loginRateLimiter = loginRateLimiter;
    }

    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of("token", token.getToken());
    }

    @PostMapping("/register")
    public UserResponse register(@RequestBody RegisterRequest request) {
        return userService.register(request);
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody(required = false) LoginRequest request,
                                   HttpServletRequest servletRequest, HttpServletResponse servletResponse) {
        String clientKey = servletRequest.getRemoteAddr();
        if (loginRateLimiter.isBlocked(clientKey)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of("error", "Too many login attempts"));
        }
        if (request == null || request.email() == null || request.password() == null) {
            loginRateLimiter.recordFailure(clientKey);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid email or password"));
        }
        try {
            Authentication authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(request.email().trim().toLowerCase(java.util.Locale.ROOT), request.password()));
            servletRequest.getSession(true);
            sessionAuthenticationStrategy.onAuthentication(authentication, servletRequest, servletResponse);
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            contextRepository.saveContext(context, servletRequest, servletResponse);
            csrfTokenRepository.saveToken(null, servletRequest, servletResponse);
            loginRateLimiter.recordSuccess(clientKey);
            return ResponseEntity.ok(new LoginResponse(true, userService.profile(authentication.getName())));
        } catch (AuthenticationException ex) {
            SecurityContextHolder.clearContext();
            loginRateLimiter.recordFailure(clientKey);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid email or password"));
        }
    }

    @GetMapping("/me")
    public UserResponse me(Authentication authentication) {
        return userService.profile(authentication.getName());
    }
}
