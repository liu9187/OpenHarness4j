package io.openharness4j.api;

import java.math.BigDecimal;

public record Cost(String currency, BigDecimal amount) {

    public Cost {
        currency = currency == null || currency.isBlank() ? "USD" : currency.trim().toUpperCase();
        amount = amount == null ? BigDecimal.ZERO : amount;
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("amount must be greater than or equal to zero");
        }
    }

    public static Cost zero() {
        return new Cost("USD", BigDecimal.ZERO);
    }

    public Cost plus(Cost other) {
        if (other == null) {
            return this;
        }
        if (!currency.equals(other.currency())) {
            throw new IllegalArgumentException("cannot add costs with different currencies");
        }
        return new Cost(currency, amount.add(other.amount()));
    }

    public boolean isZero() {
        return amount.signum() == 0;
    }
}
