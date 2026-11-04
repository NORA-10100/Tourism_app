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
import jakarta.servlet.http.HttpSession;

@Configuration
// メモ: 支出画面をログイン済みの利用者だけが開けるようにします。
public class WebSecurityConfig implements WebMvcConfigurer {
    // メモ: 画面ログインは自作コントローラーで行い、CSRF対策はSpring Securityに任せます。
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // Existing login controller manages the session. Keep Spring Security's
        // CSRF protection and default security headers for every form.
        return http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                .build();
    }

    @Override
    // メモ: 支出URLへ進む前にログイン状態を確認する処理を登録します。
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            // メモ: ログインしていなければログイン画面へ移動します。
            public boolean preHandle(HttpServletRequest request,
                    HttpServletResponse response, Object handler) throws Exception {
                // メモ: 既存セッションだけを確認し、未ログイン時に空のセッションを作らないようにします。
                HttpSession session = request.getSession(false);
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
