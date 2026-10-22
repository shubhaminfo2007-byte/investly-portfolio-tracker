package portfolio;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Scanner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Drives the console menu with typed input and checks what it prints and saves. */
class MainTest {
    @TempDir
    Path tempDir;

    /** Runs the menu with the given keyboard input and returns everything it printed. */
    private String runMenu(String input) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        PrintStream original = System.out;
        System.setOut(new PrintStream(buffer, true, StandardCharsets.UTF_8));
        try {
            new Main(new Scanner(input), new CsvStorage(tempDir)).run();
        } finally {
            System.setOut(original);
        }
        return buffer.toString(StandardCharsets.UTF_8);
    }

    private Portfolio reload() throws IOException {
        return new CsvStorage(tempDir).load();
    }

    @Test
    void buyAndSellThroughTheMenuAreSavedAndReportRealizedProfit() throws IOException {
        String out = runMenu("1\naapl\n10\n100\n2\nAAPL\n4\n150\n8\n0\n");

        assertTrue(out.contains("BUY recorded."));
        assertTrue(out.contains("SELL recorded."));
        assertTrue(out.contains("Realized profit/loss on this sale: 200.00"));  // 4 x (150 - 100)
        assertTrue(out.contains("Total realized: 200.00"));

        Portfolio loaded = reload();
        assertEquals(6, loaded.getHoldings().iterator().next().getQuantity());
        assertEquals(new BigDecimal("200.00"), loaded.totalRealizedGain());
        assertEquals(2, loaded.getTransactions().size());
    }

    @Test
    void everyChangeIsSavedEvenIfTheProgramEndsWithoutChoosingExit() throws IOException {
        String out = runMenu("1\nAAPL\n10\n100\n1\nAAPL\n");   // input ends in the middle of a question
        assertTrue(out.contains("Input ended"));
        assertEquals(10, reload().getHoldings().iterator().next().getQuantity());
    }

    @Test
    void badInputShowsFriendlyErrorsAndChangesNothing() throws IOException {
        String out = runMenu("1\nAAPL\n10\n100\n"      // one valid buy
                + "1\nAAPL\nabc\n"                        // quantity is not a number
                + "1\nAAPL\n-5\n100\n"                   // negative quantity
                + "1\nAAPL\n5\nfree\n"                   // price is not a number
                + "1\nAAPL\n5\n-1\n"                     // negative price
                + "1\nA,B\n1\n100\n"                     // comma in symbol
                + "2\nZZZ\n1\n10\n"                      // selling something not owned
                + "2\nAAPL\n11\n10\n"                    // selling more than owned
                + "3\nNOPE\n5\n"                         // price for an unknown symbol
                + "9\n"                                  // not a menu option
                + "0\n");

        assertTrue(out.contains("Quantity must be a whole number"));
        assertTrue(out.contains("Quantity must be positive"));
        assertTrue(out.contains("'free' is not a valid number"));
        assertTrue(out.contains("Price must be positive"));
        assertTrue(out.contains("Invalid symbol"));
        assertTrue(out.contains("you do not hold it"));
        assertTrue(out.contains("you only hold 10"));
        assertTrue(out.contains("Unknown symbol NOPE"));
        assertTrue(out.contains("Please choose a number from the menu."));

        Portfolio loaded = reload();
        assertEquals(1, loaded.getTransactions().size());
        assertEquals(10, loaded.getHoldings().iterator().next().getQuantity());
    }

    @Test
    void demoDataAsksBeforeReplacingRealData() throws IOException {
        runMenu("1\nREAL\n10\n100\n0\n");

        String declined = runMenu("7\nno\n0\n");
        assertTrue(declined.contains("Cancelled"));
        assertEquals(1, reload().getTransactions().size());

        runMenu("7\nYES\n0\n");
        assertEquals(5, reload().getTransactions().size());
    }

    @Test
    void demoDataNeedsNoConfirmationWhenThereIsNoData() throws IOException {
        String out = runMenu("7\n4\n8\n0\n");
        assertTrue(out.contains("Demo data loaded"));
        assertEquals(5, reload().getTransactions().size());
    }

    @Test
    void holdingsScreenSeparatesRealizedFromUnrealizedProfit() throws IOException {
        String out = runMenu("7\n4\n0\n");
        assertTrue(out.contains("Unrealized profit/loss"));
        assertTrue(out.contains("Realized profit/loss"));
        assertTrue(out.contains("442.67"));   // unrealized: AAPL 266.67 + MSFT 204.00 + INFY -28.00
        assertTrue(out.contains("83.33"));    // realized: 5 AAPL sold at 200, average cost 183.33
        assertTrue(out.contains("526.00"));   // total
        assertTrue(out.contains("7.86%"));    // 526.00 / 6690.00 bought
    }

    @Test
    void holdingWithoutAPriceIsFlagged() throws IOException {
        String out = runMenu("1\nAAPL\n10\n100\n4\n0\n");
        assertTrue(out.contains("100.00*"));
        assertTrue(out.contains("No price set"));
    }

    @Test
    void realizedViewWithNoSalesSaysSo() throws IOException {
        String out = runMenu("1\nAAPL\n10\n100\n8\n0\n");
        assertTrue(out.contains("No sales recorded yet"));
    }

    @Test
    void fullySoldPositionStillShowsProfit() throws IOException {
        String out = runMenu("1\nAAPL\n10\n100\n2\nAAPL\n10\n150\n4\n0\n");
        assertTrue(out.contains("everything has been sold"));
        assertTrue(out.contains("500.00"));
    }

    @Test
    void badSavedFileDoesNotCrashStartupAndShowsWarnings() throws IOException {
        Files.writeString(tempDir.resolve("transactions.csv"),
                "date,type,symbol,quantity,price\n2026-01-01,BUY,AAPL,10,100.00\nnot a record\n");
        String out = runMenu("4\n0\n");
        assertTrue(out.contains("WARNING"));
        assertTrue(out.contains("line 3 skipped"));
        assertTrue(out.contains("AAPL"));
    }

    @Test
    void historyListsTransactionsInOrder() throws IOException {
        String out = runMenu("1\nAAPL\n10\n100\n1\nMSFT\n2\n400\n5\n0\n");
        assertTrue(out.indexOf("AAPL") < out.lastIndexOf("MSFT"));
        assertTrue(out.contains("BUY  AAPL"));
    }
}
