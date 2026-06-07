package com.hera.craftkit.database;

import java.util.Objects;

public final class TransactionOptions {

    private static final TransactionOptions DEFAULTS = builder().build();
    private static final TransactionOptions READ_UNCOMMITTED = builder().isolation(TransactionIsolation.READ_UNCOMMITTED).build();
    private static final TransactionOptions READ_COMMITTED = builder().isolation(TransactionIsolation.READ_COMMITTED).build();
    private static final TransactionOptions REPEATABLE_READ = builder().isolation(TransactionIsolation.REPEATABLE_READ).build();
    private static final TransactionOptions SERIALIZABLE = builder().isolation(TransactionIsolation.SERIALIZABLE).build();

    private final TransactionIsolation isolation;
    private final boolean readOnly;

    private TransactionOptions(final TransactionIsolation isolation, final boolean readOnly) {
        this.isolation = isolation;
        this.readOnly = readOnly;
    }

    public static TransactionOptions defaults() {
        return DEFAULTS;
    }

    public static TransactionOptions readUncommitted() {
        return READ_UNCOMMITTED;
    }

    public static TransactionOptions readCommitted() {
        return READ_COMMITTED;
    }

    public static TransactionOptions repeatableRead() {
        return REPEATABLE_READ;
    }

    public static TransactionOptions serializable() {
        return SERIALIZABLE;
    }

    public static TransactionOptions readOnly(final TransactionIsolation isolation) {
        return builder().isolation(isolation).readOnly(true).build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public TransactionIsolation isolation() {
        return this.isolation;
    }

    public boolean readOnly() {
        return this.readOnly;
    }

    @Override
    public String toString() {
        return "TransactionOptions[isolation=" + this.isolation
            + ", readOnly=" + this.readOnly
            + ']';
    }

    public static final class Builder {

        private TransactionIsolation isolation = TransactionIsolation.DEFAULT;
        private boolean readOnly;

        public Builder isolation(final TransactionIsolation isolation) {
            this.isolation = isolation;
            return this;
        }

        public Builder readOnly(final boolean readOnly) {
            this.readOnly = readOnly;
            return this;
        }

        public TransactionOptions build() {
            return new TransactionOptions(
                Objects.requireNonNull(this.isolation, "Transaction isolation must not be null."),
                this.readOnly
            );
        }
    }
}
