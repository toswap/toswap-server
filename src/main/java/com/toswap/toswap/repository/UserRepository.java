package com.toswap.toswap.repository;

import com.toswap.toswap.entity.Provider;
import com.toswap.toswap.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByProviderAndProviderId(Provider provider, String providerId);

    Optional<User> findByEmail(String email);
}
