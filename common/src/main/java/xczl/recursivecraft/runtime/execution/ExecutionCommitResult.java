package xczl.recursivecraft.runtime.execution;

import java.util.Objects;

public record ExecutionCommitResult(Status status) {
    public ExecutionCommitResult {
        Objects.requireNonNull(status);
    }

    public boolean success() {
        return status == Status.SUCCESS || status == Status.SUCCESS_OUTPUT_FALLBACK;
    }

    public boolean outputFallbackTriggered() {
        return status == Status.SUCCESS_OUTPUT_FALLBACK;
    }

    public static ExecutionCommitResult ok() { return new ExecutionCommitResult(Status.SUCCESS); }
    public static ExecutionCommitResult failedRevalidate() { return new ExecutionCommitResult(Status.FAILED_REVALIDATION); }
    public static ExecutionCommitResult failedConsume() { return new ExecutionCommitResult(Status.FAILED_CONSUME); }
    public static ExecutionCommitResult okWithFallback() { return new ExecutionCommitResult(Status.SUCCESS_OUTPUT_FALLBACK); }

    public enum Status {
        SUCCESS,
        FAILED_REVALIDATION,
        FAILED_CONSUME,
        SUCCESS_OUTPUT_FALLBACK
    }
}
