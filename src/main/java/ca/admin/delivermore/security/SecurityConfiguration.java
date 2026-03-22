package ca.admin.delivermore.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;

import com.vaadin.flow.spring.security.VaadinSecurityConfigurer;

import ca.admin.delivermore.views.login.LoginView;

@EnableWebSecurity
@Configuration
public class SecurityConfiguration {

    private final Logger log = LoggerFactory.getLogger(SecurityConfiguration.class);

    public static final String LOGOUT_SUCCESS_URL = "/login?logout";

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // Open public API endpoints; vaadin.exclude-urls=/api/** must be set in application.properties
    @Order(1)
    @Bean
    SecurityFilterChain configurePublicApi(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/api/**")
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**"))
                .authorizeHttpRequests(authz -> authz.anyRequest().permitAll());
        log.info("****SECURITY...here 1.1 - after new public api setup");
        return http.build();
    }

    @Order(20)
    @Bean
    public SecurityFilterChain vaadinSecurityFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher(new NegatedRequestMatcher(PathPatternRequestMatcher.pathPattern("/api/**")));
        // Delegate Vaadin-specific security to VaadinSecurityConfigurer (replaces VaadinWebSecurity)
        http.with(VaadinSecurityConfigurer.vaadin(), vaadin -> vaadin.loginView(LoginView.class, LOGOUT_SUCCESS_URL));
        log.info("****SECURITY...here 0.1 - after new loginView");
        return http.build();
    }

}
