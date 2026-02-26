package com.backend.domain.payment.service;

import com.backend.domain.payment.entity.BillingKey;
import com.backend.domain.payment.repository.BillingKeyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 고객 키와 BillingKey의 영속 매핑을 관리하는 서비스.
 */
@Service
@RequiredArgsConstructor
public class BillingKeyService {

    private final BillingKeyRepository billingKeyRepository;

    /**
     * Brandpay 결제 승인에 사용할 BillingKey를 조회한다.
     */
    @Transactional(readOnly = true)
    public Optional<String> findBillingKey(String customerKey) {
        return billingKeyRepository.findById(customerKey).map(BillingKey::getBillingKey);
    }

    /**
     * BillingKey를 신규 저장하거나 최신 값으로 갱신한다.
     */
    @Transactional
    public void saveBillingKey(String customerKey, String billingKey) {
        billingKeyRepository.findById(customerKey)
                .ifPresentOrElse(
                        saved -> saved.updateBillingKey(billingKey),
                        () -> billingKeyRepository.save(BillingKey.builder()
                                .customerKey(customerKey)
                                .billingKey(billingKey)
                                .build())
                );
    }
}
