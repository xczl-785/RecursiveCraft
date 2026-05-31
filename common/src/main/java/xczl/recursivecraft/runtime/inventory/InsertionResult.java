package xczl.recursivecraft.runtime.inventory;

import java.util.Objects;

public record InsertionResult(Status status) {
    public InsertionResult {
        Objects.requireNonNull(status);
    }

    public boolean inserted() {
        return status == Status.INSERTED;
    }

    public static InsertionResult ok() { return new InsertionResult(Status.INSERTED); }
    public static InsertionResult inventoryFull() { return new InsertionResult(Status.INVENTORY_FULL); }
    public static InsertionResult sourceRejected() { return new InsertionResult(Status.SOURCE_REJECTED); }

    public enum Status {
        INSERTED,
        INVENTORY_FULL,
        SOURCE_REJECTED
    }
}
