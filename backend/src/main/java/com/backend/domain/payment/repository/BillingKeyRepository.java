package com.backend.domain.payment.repository;

import com.backend.domain.payment.entity.BillingKey;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BillingKeyRepository extends JpaRepository<BillingKey, String> {
}
