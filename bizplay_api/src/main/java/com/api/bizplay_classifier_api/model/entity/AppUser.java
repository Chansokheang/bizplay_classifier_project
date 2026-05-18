package com.api.bizplay_classifier_api.model.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Collections;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Setter
public class AppUser implements UserDetails {

    private UUID userId;
    private Integer roleId;
    private String username;
    private String firstname;
    private String lastname;
    private String email;
    private String password;
    private Character gender;
    private LocalDate dob;
    private Boolean isDisabled;
    private Boolean isVerified;

    @Override
    public boolean isEnabled() {
        return !Boolean.TRUE.equals(isDisabled);
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public String getUsername() {
        return this.email;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"));
    }
}
