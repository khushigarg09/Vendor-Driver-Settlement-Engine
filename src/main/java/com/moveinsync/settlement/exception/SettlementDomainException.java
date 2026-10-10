package com.moveinsync.settlement.exception;

public class SettlementDomainException extends RuntimeException {

    public SettlementDomainException(String message) {
        super(message);
    }

    public SettlementDomainException(String message, Throwable cause) {
        super(message, cause);
    }
}
