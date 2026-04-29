package io.openharness4j.runtime;

import io.openharness4j.api.Cost;
import io.openharness4j.api.Usage;

import java.math.BigDecimal;
import java.math.MathContext;

public class TokenPricingCostEstimator implements CostEstimator {

    private static final BigDecimal ONE_MILLION = BigDecimal.valueOf(1_000_000);

    private final String currency;
    private final BigDecimal promptTokenPricePerMillion;
    private final BigDecimal completionTokenPricePerMillion;

    public TokenPricingCostEstimator(
            String currency,
            BigDecimal promptTokenPricePerMillion,
            BigDecimal completionTokenPricePerMillion
    ) {
        this.currency = currency == null || currency.isBlank() ? "USD" : currency.trim().toUpperCase();
        this.promptTokenPricePerMillion = requireNonNegative(promptTokenPricePerMillion, "promptTokenPricePerMillion");
        this.completionTokenPricePerMillion = requireNonNegative(
                completionTokenPricePerMillion,
                "completionTokenPricePerMillion"
        );
    }

    @Override
    public Cost estimate(Usage usage) {
        Usage safeUsage = usage == null ? Usage.zero() : usage;
        BigDecimal promptCost = BigDecimal.valueOf(safeUsage.promptTokens())
                .multiply(promptTokenPricePerMillion)
                .divide(ONE_MILLION, MathContext.DECIMAL64);
        BigDecimal completionCost = BigDecimal.valueOf(safeUsage.completionTokens())
                .multiply(completionTokenPricePerMillion)
                .divide(ONE_MILLION, MathContext.DECIMAL64);
        return new Cost(currency, promptCost.add(completionCost));
    }

    private static BigDecimal requireNonNegative(BigDecimal value, String field) {
        BigDecimal safeValue = value == null ? BigDecimal.ZERO : value;
        if (safeValue.signum() < 0) {
            throw new IllegalArgumentException(field + " must be greater than or equal to zero");
        }
        return safeValue;
    }
}
