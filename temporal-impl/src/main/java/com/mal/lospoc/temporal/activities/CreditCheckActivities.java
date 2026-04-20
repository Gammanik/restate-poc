package com.mal.lospoc.temporal.activities;

import com.mal.lospoc.common.dto.*;
import io.temporal.activity.ActivityInterface;

import java.util.UUID;

@ActivityInterface
public interface CreditCheckActivities {
    void collectUserDetails(UUID applicationId, UserDetails userDetails);
    ConsentRecord captureConsent(UUID applicationId);
    AecbReport fetchAecb(UUID applicationId, String emiratesId, String consentRecordId);
    OpenBankingSnapshot fetchOpenBanking(UUID applicationId, String consentRecordId);
    RiskScore scoreApplication(UUID applicationId, AecbReport aecb, OpenBankingSnapshot openBanking, String productId);
    void markForUnderwriting(UUID applicationId);
    void applyUnderwritingDecision(UUID applicationId, UnderwritingDecision decision);
}
