package xczl.recursivecraft.runtime.execution;

public record ExecutionCommitResult(boolean success, boolean outputFallbackTriggered) {
    public static ExecutionCommitResult ok() { return new ExecutionCommitResult(true, false); }
    public static ExecutionCommitResult failed() { return new ExecutionCommitResult(false, false); }
    public static ExecutionCommitResult okWithFallback() { return new ExecutionCommitResult(true, true); }
}
