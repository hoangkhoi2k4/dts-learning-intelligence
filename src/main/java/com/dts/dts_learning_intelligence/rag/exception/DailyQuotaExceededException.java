package com.dts.dts_learning_intelligence.rag.exception;

public class DailyQuotaExceededException extends RuntimeException {
    public DailyQuotaExceededException(String message) {
        super(message);
    }
}
