package portfolio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UserStorageTest {
    @Test
    void usernameIsNormalizedAndUnsafeNamesAreRejected() {
        assertEquals("alice", CsvStorage.normalizeUsername("  Alice "));
        for (String bad : new String[] {"", "  ", "../x", "a/b", "a\\b", "..", ".", "a b", "x".repeat(33)}) {
            assertThrows(IllegalArgumentException.class, () -> CsvStorage.normalizeUsername(bad), bad);
        }
        assertThrows(IllegalArgumentException.class, () -> CsvStorage.normalizeUsername(null));
    }

    @Test
    void eachUserGetsTheirOwnFolderAndData(@TempDir Path root) throws Exception {
        CsvStorage alice = CsvStorage.forUser(root, "Alice");
        CsvStorage bob = CsvStorage.forUser(root, "bob");
        assertEquals(root.resolve("users").resolve("alice"), alice.getFolder());

        Portfolio p = new Portfolio();
        p.addTransaction(new Transaction(LocalDate.of(2026, 1, 1), TransactionType.BUY, "AAPL", 3, new BigDecimal("10")));
        p.setPrice("AAPL", new BigDecimal("12"));
        alice.save(p);

        assertTrue(Files.exists(root.resolve("users/alice/transactions.csv")));
        assertTrue(Files.exists(root.resolve("users/alice/prices.csv")));
        assertEquals(1, CsvStorage.forUser(root, "alice").load().getTransactions().size());
        assertEquals(0, bob.load().getTransactions().size());
    }
}
