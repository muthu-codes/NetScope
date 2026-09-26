package com.netscope.repository;

import com.netscope.entity.NetworkInterfaceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NetworkInterfaceRepository extends JpaRepository<NetworkInterfaceEntity, Long> {
    Optional<NetworkInterfaceEntity> findByName(String name);
}
