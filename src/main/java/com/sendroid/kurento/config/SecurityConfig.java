package com.sendroid.kurento.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.ServletListenerRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.authentication.configuration.GlobalAuthenticationConfigurerAdapter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.JdbcUserDetailsManager;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import javax.sql.DataSource;

@Configuration
@EnableWebSecurity
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SecurityConfig {
    private static final String[] UNSECURED_RESOURCE_LIST = new String[]{"/static/**", "/js/**", "/webjars/**"};
    @Order(Ordered.HIGHEST_PRECEDENCE)
    @Configuration
    @Profile({"dev","test"})
    protected static class ExternalAuthenticationSecurity extends GlobalAuthenticationConfigurerAdapter {
        @Autowired
        private DataSource dataSource;

        @Override
        public void init(AuthenticationManagerBuilder auth) throws Exception {
            String authoritiesByUsernameQuery = "select username, authority from user_authorities " +
                    "inner join users on user_authorities.user_id = users.id " +
                    "inner join authorities on user_authorities.authority_id = authorities.id " +
                    "where username = ?";

            JdbcUserDetailsManager userDetailsService = new JdbcUserDetailsManager();
            userDetailsService.setDataSource(dataSource);
            userDetailsService.setAuthoritiesByUsernameQuery(authoritiesByUsernameQuery);
            PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

            auth
                    .userDetailsService(userDetailsService)
                    .passwordEncoder(passwordEncoder)
                    .and()
                    .jdbcAuthentication()
                    .authoritiesByUsernameQuery(authoritiesByUsernameQuery)
                    .passwordEncoder(passwordEncoder)
                    .dataSource(dataSource)
            ;
        }
    }
    @Configuration
    @Order(1)
    @Profile({"dev","test"})
    public static class DevWebSecurityConfigurerAdapter extends WebSecurityConfigurerAdapter {

        @Override
        public void configure(WebSecurity web) {
            web.ignoring().antMatchers(UNSECURED_RESOURCE_LIST);
        }
        @Override
        protected void configure(HttpSecurity http) throws Exception {
            http
                    .headers()
                    .frameOptions()
                    .sameOrigin()
                    .and()
                    .authorizeRequests()
                    .anyRequest()
                    .authenticated()
                    .and()
                    .formLogin()
                    .loginPage("/login").defaultSuccessUrl("/",true)
                    .permitAll()
                    .and()
                    .headers()
                    .cacheControl()
                    .and()
                    .frameOptions()
                    .deny()
                    .and()
                    .exceptionHandling()
                    .accessDeniedPage("/access?error")
                    .and()
                    .logout()
                    .logoutRequestMatcher(new AntPathRequestMatcher("/logout"))
                    .logoutSuccessUrl("/?logout")
                    .and()
                    .sessionManagement()
                    .maximumSessions(1)
                    .expiredUrl("/login?expired");
        }
    }
    @Bean
    public static ServletListenerRegistrationBean<HttpSessionEventPublisher> httpSessionEventPublisher() {
        return new ServletListenerRegistrationBean<>(new HttpSessionEventPublisher());
    }
}
