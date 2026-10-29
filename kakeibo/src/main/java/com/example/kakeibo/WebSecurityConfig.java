package com.example.kakeibo;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.HandlerInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Configuration
public class WebSecurityConfig implements WebMvcConfigurer {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // Existing login controller manages the session. Keep Spring Security's
        // CSRF protection and default security headers for every form.
        return http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                .build();
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request,
                    HttpServletResponse response, Object handler) throws Exception {
                var session = request.getSession(false);
                if (session != null && Boolean.TRUE.equals(session.getAttribute("loggedIn"))
                        && session.getAttribute("expenseUserId") != null) {
                    return true;
                }
                response.sendRedirect(request.getContextPath() + "/login");
                return false;
            }
        }).addPathPatterns("/expenses", "/expenses/**");
    }
}
