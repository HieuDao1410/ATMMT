package vn.edu.atmmt.sessionlab.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CsrfException;

import vn.edu.atmmt.sessionlab.security.LoginAttemptLimiter;
import vn.edu.atmmt.sessionlab.security.LoginThrottleFilter;
import vn.edu.atmmt.sessionlab.security.SecurityAudit;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
            @Value("${server.servlet.session.cookie.name:JSESSIONID}") String sessionCookieName,
            LoginAttemptLimiter limiter, SecurityAudit audit) throws Exception {
        var successHandler = new SavedRequestAwareAuthenticationSuccessHandler();
        successHandler.setDefaultTargetUrl("/me");
        successHandler.setAlwaysUseDefaultTargetUrl(true);
        var failureHandler = new SimpleUrlAuthenticationFailureHandler("/login?error");
        var deniedHandler = new AccessDeniedHandlerImpl();

        http
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/", "/login", "/css/**", "/js/app.js", "/error").permitAll()
                .anyRequest().authenticated())
            .formLogin(form -> form
                .loginPage("/login")
                .successHandler((request, response, authentication) -> {
                    LoginThrottleFilter.markSuccess(request);
                    audit.record(SecurityAudit.Event.LOGIN_SUCCESS, request);
                    successHandler.onAuthenticationSuccess(request, response, authentication);
                })
                .failureHandler((request, response, exception) -> {
                    if (exception instanceof BadCredentialsException) {
                        LoginThrottleFilter.markFailure(request);
                        audit.record(SecurityAudit.Event.LOGIN_FAILURE, request);
                    } else {
                        audit.record(SecurityAudit.Event.LOGIN_ERROR, request);
                    }
                    failureHandler.onAuthenticationFailure(request, response, exception);
                })
                .permitAll())
            .exceptionHandling(exceptions -> exceptions.accessDeniedHandler((request, response, exception) -> {
                audit.record(exception instanceof CsrfException ? SecurityAudit.Event.CSRF_REJECTED
                    : SecurityAudit.Event.ACCESS_DENIED, request);
                deniedHandler.handle(request, response, exception);
            }))
            .logout(logout -> logout
                .addLogoutHandler((request, response, authentication) -> {
                    if (authentication != null) {
                        audit.record(SecurityAudit.Event.LOGOUT, request);
                    }
                })
                .logoutSuccessUrl("/login?logout")
                .invalidateHttpSession(true)
                .clearAuthentication(true)
                .deleteCookies(sessionCookieName))
            // Construct here so Spring Boot does not register it twice as a servlet filter.
            .addFilterBefore(new LoginThrottleFilter(limiter, audit), UsernamePasswordAuthenticationFilter.class);

        // Keep Spring Security's CSRF and session fixation protection enabled.
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    UserDetailsService userDetailsService(PasswordEncoder passwordEncoder) {
        // Public, fictional lab credentials. Never reuse these for a deployed service.
        var alice = User.withUsername("alice")
            .password(passwordEncoder.encode("AliceLab!2026"))
            .roles("USER")
            .build();
        var bob = User.withUsername("bob")
            .password(passwordEncoder.encode("BobLab!2026"))
            .roles("USER")
            .build();
        return new InMemoryUserDetailsManager(alice, bob);
    }
}
