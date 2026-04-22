package com.mal.lospoc.los.application.config;

import com.mal.lospoc.common.domain.LoanProductConfig;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Component
public class ProductConfigLoader {
    private final Map<String, LoanProductConfig> configs;

    public ProductConfigLoader() {
        this.configs = loadConfigs();
    }

    public LoanProductConfig getConfig(String productId) {
        LoanProductConfig config = configs.get(productId);
        if (config == null) {
            throw new IllegalArgumentException("Unknown product: " + productId);
        }
        return config;
    }

    @SuppressWarnings("unchecked")
    private Map<String, LoanProductConfig> loadConfigs() {
        try (InputStream is = new ClassPathResource("product-configs.yaml").getInputStream()) {
            Yaml yaml = new Yaml();
            Map<String, Object> root = yaml.load(is);
            Map<String, Object> products = (Map<String, Object>) root.get("products");

            Map<String, LoanProductConfig> result = new HashMap<>();
            for (Map.Entry<String, Object> entry : products.entrySet()) {
                String productId = entry.getKey();
                Map<String, Object> productData = (Map<String, Object>) entry.getValue();
                result.put(productId, parseConfig(productId, productData));
            }
            return result;
        } catch (IOException e) {
            throw new RuntimeException("Failed to load product configs", e);
        }
    }

    @SuppressWarnings("unchecked")
    private LoanProductConfig parseConfig(String productId, Map<String, Object> data) {
        Map<String, Object> stages = (Map<String, Object>) data.get("stages");
        Map<String, Object> decision = (Map<String, Object>) data.get("decision");
        int slaDays = (int) data.get("underwriting_sla_days");

        return new LoanProductConfig(
            productId,
            parseStage((Map<String, Object>) stages.get("identity_verification")),
            parseStage((Map<String, Object>) stages.get("credit_bureau")),
            parseStage((Map<String, Object>) stages.get("open_banking")),
            parseStage((Map<String, Object>) stages.get("employment_verification")),
            parseStage((Map<String, Object>) stages.get("aml_screening")),
            parseStage((Map<String, Object>) stages.get("fraud_scoring")),
            parseStage((Map<String, Object>) stages.get("disbursement_notification")),
            new LoanProductConfig.DecisionThresholds(
                (int) decision.get("auto_approve_score"),
                (int) decision.get("auto_reject_score")
            ),
            Duration.ofDays(slaDays)
        );
    }

    private LoanProductConfig.StageConfig parseStage(Map<String, Object> stage) {
        return new LoanProductConfig.StageConfig(
            (boolean) stage.get("enabled"),
            (int) stage.get("timeout_seconds"),
            (int) stage.get("retries")
        );
    }
}
