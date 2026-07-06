package com.lemonacademy.ecommerce.security;

import com.lemonacademy.ecommerce.repository.AdminRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminUserDetailsService implements UserDetailsService {

    private final AdminRepository adminRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return adminRepository.findByEmailIgnoreCase(username)
                .map(AdminUserDetails::new)
                .orElseThrow(() -> new UsernameNotFoundException("Admin not found with email: " + username));
    }
}
