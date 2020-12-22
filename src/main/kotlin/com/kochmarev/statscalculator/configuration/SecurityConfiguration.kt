package com.kochmarev.statscalculator.configuration

import com.kochmarev.statscalculator.service.UserDetailsServiceImpl
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder

@Configuration
@EnableWebSecurity
class SecurityConfiguration : WebSecurityConfigurerAdapter() {
    @Throws(Exception::class)
    override fun configure(http: HttpSecurity) {
        http.authorizeRequests()
            .antMatchers("/", "welcome").hasAnyRole("ROLE_USER", "ROLE_ADMIN")
            .antMatchers("/admin/**").hasRole("ROLE_ADMIN").anyRequest()
            .authenticated().and()
            .formLogin()
            .loginPage("/login").failureUrl("/login?error")
            .defaultSuccessUrl("/welcome")
            .usernameParameter("username")
            .passwordParameter("password")
            .and().logout()
            .logoutSuccessUrl("login?logout")
    }

    @Autowired
    @Throws(Exception::class)
    fun configureGlobal(auth: AuthenticationManagerBuilder) {
        auth.userDetailsService(userDetailsService()).passwordEncoder(
            passwordEncoder()
        )
    }

    @Bean
    fun passwordEncoder(): PasswordEncoder {
        return BCryptPasswordEncoder()
    }

    @Bean
    public override fun userDetailsService(): UserDetailsService {
        return UserDetailsServiceImpl()
    }
}