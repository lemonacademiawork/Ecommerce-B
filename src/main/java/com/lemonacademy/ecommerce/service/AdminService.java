package com.lemonacademy.ecommerce.service;

import com.lemonacademy.ecommerce.dto.AdminLoginRequest;
import com.lemonacademy.ecommerce.dto.AdminAuthResponse;

public interface AdminService {
    AdminAuthResponse login(AdminLoginRequest request);
}
