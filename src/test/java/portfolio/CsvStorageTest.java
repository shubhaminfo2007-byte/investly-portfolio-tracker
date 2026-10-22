package portfolio;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CsvStorageTest {
    @TempDir
    Path tempDir;

    private static Transaction tx(String date, TransactionType type, String symbol, int qty, String price) {
        return new Transaction(LocalDate.parse(date), type, symbol, qty, new BigDecimal(price));
    }

    private static BigDecimal money(String text) {
        return new BigDecimal(text);
    }

    private int skippedCount(CsvStorage storage) {
        return (int) storage.getWarnings().stream().filter(w -> w.contains("skipped")).count();
    }

    private List<String> filesInFolderStartingWith(String prefix) throws IOException {
        try (Stream<Path> files = Files.list(tempDir)) {
            return files.map(p -> p.getFileName().toString())
                    .filter(n -> n.startsWith(prefix)).collect(Collectors.toList());
        }
    }

    // ---------------------------------------------------- save and load work

    @Test
    void dataSurvivesARestart() throws IOException {
        Portfolio original = new Portfolio();
        original.addTransaction(tx("2026-01-10", TransactionType.BUY, "AAPL", 10, "180.00"));
        original.addTransaction(tx("2026-02-10", TransactionType.SELL, "AAPL", 4, "200.00"));
        original.addTransaction(tx("2026-02-11", TransactionType.BUY, "MSFT", 3, "400.50"));
        original.setPrice("AAPL", new BigDecimal("210.50"));
        new CsvStorage(tempDir).save(original);

        CsvStorage afterRestart = new CsvStorage(tempDir);   // a brand new object, like a new run
        Portfolio loaded = afterRestart.load();

        assertTrue(afterRestart.getWarnings().isEmpty());
        assertEquals(3, loaded.getTransactions().size());
        assertEquals(2, loaded.getHoldings().size());
        assertEquals(original.totalValue(), loaded.totalValue());
        assertEquals(original.totalCost(), loaded.totalCost());
        assertEquals(original.totalUnrealizedGain(), loaded.totalUnrealizedGain());
        assertEquals(original.totalRealizedGain(), loaded.totalRealizedGain());
        assertEquals(money("80.00"), loaded.totalRealizedGain());     // 4 x (200 - 180)
        assertEquals(new BigDecimal("210.50"), loaded.getPrices().get("AAPL"));
    }

    @Test
    void realizedProfitOfAFullySoldPositionSurvivesARestart() throws IOException {
        Portfolio original = new Portfolio();
        original.addTransaction(tx("2026-01-10", TransactionType.BUY, "Y", 10, "100.00"));
        original.addTransaction(tx("2026-01-11", TransactionType.SELL, "Y", 10, "150.00"));
        new CsvStorage(tempDir).save(original);

        Portfolio loaded = new CsvStorage(tempDir).load();
        assertTrue(loaded.getHoldings().isEmpty());
        assertEquals(money("500.00"), loaded.totalRealizedGain());
    }

    @Test
    void fileFormatIsUnchanged() throws IOException {
        Portfolio p = new Portfolio();
        p.addTransaction(tx("2026-01-10", TransactionType.BUY, "AAPL", 10, "180.00"));
        p.setPrice("AAPL", new BigDecimal("210.5"));
        new CsvStorage(tempDir).save(p);
        assertEquals(List.of("date,type,symbol,quantity,price", "2026-01-10,BUY,AAPL,10,180.00"),
                Files.readAllLines(tempDir.resolve("transactions.csv")));
        assertEquals(List.of("symbol,price", "AAPL,210.5"),
                Files.readAllLines(tempDir.resolve("prices.csv")));
    }

    @Test
    void filesWrittenByPhase1StillLoad() throws IOException {
        Files.writeString(tempDir.resolve("transactions.csv"),
                "date,type,symbol,quantity,price\n"
                + "2026-01-10,BUY,AAPL,10,180.00\n"
                + "2026-02-05,BUY,AAPL,5,190.00\n"
                + "2026-04-02,SELL,AAPL,5,200.00\n");
        Files.writeString(tempDir.resolve("prices.csv"), "symbol,price\nAAPL,210.00\n");

        CsvStorage storage = new CsvStorage(tempDir);
        Portfolio loaded = storage.load();
        assertTrue(storage.getWarnings().isEmpty());
        assertEquals(10, loaded.getHoldings().iterator().next().getQuantity());
        assertEquals(money("2100.00"), loaded.totalValue());
    }

    // ------------------------------------------------- missing / empty files

    @Test
    void missingFolderOrFilesGiveAnEmptyPortfolio() throws IOException {
        CsvStorage storage = new CsvStorage(tempDir.resolve("nothing-here"));
        Portfolio loaded = storage.load();
        assertTrue(loaded.getTransactions().isEmpty());
        assertTrue(storage.getWarnings().isEmpty());
    }

    @Test
    void emptyAndHeaderOnlyFilesAreFine() throws IOException {
        Files.writeString(tempDir.resolve("transactions.csv"), "");
        Files.writeString(tempDir.resolve("prices.csv"), "symbol,price\n");
        CsvStorage storage = new CsvStorage(tempDir);
        assertTrue(storage.load().getTransactions().isEmpty());
        assertTrue(storage.getWarnings().isEmpty());
    }

    @Test
    void windowsLineEndingsBlankLinesLowercaseAndByteOrderMarkAreAccepted() throws IOException {
        Files.writeString(tempDir.resolve("transactions.csv"),
                "﻿date,type,symbol,quantity,price\r\n2026-01-01,buy,aapl,5,100\r\n\r\n2026-01-02, SELL , AAPL , 2 , 110 \r\n");
        CsvStorage storage = new CsvStorage(tempDir);
        Portfolio loaded = storage.load();
        assertTrue(storage.getWarnings().isEmpty());
        assertEquals(2, loaded.getTransactions().size());
        assertEquals(3, loaded.getHoldings().iterator().next().getQuantity());
    }

    @Test
    void aFileWithoutAHeaderStillLoads() throws IOException {
        Files.writeString(tempDir.resolve("transactions.csv"), "2026-01-01,BUY,AAPL,5,100\n");
        Portfolio loaded = new CsvStorage(tempDir).load();
        assertEquals(1, loaded.getTransactions().size());
    }

    // ------------------------------------------------- malformed records

    @Test
    void badTransactionLinesAreSkippedAndGoodOnesAreKept() throws IOException {
        String content = "date,type,symbol,quantity,price\n"
                + "2026-01-01,BUY,AAPL,10,100.00\n"      // line 2 good
                + "garbage\n"                              // line 3 wrong column count
                + "2026-01-02,BUY,MSFT,abc,50\n"           // line 4 quantity not a number
                + "bad-date,BUY,X,1,1\n"                   // line 5 bad date
                + "2026-01-03,HOLD,X,1,1\n"                // line 6 unknown type
                + "2026-01-04,BUY,A,B,1,10\n"              // line 7 comma in symbol
                + "2026-01-05,SELL,ZZZ,1,10\n"             // line 8 sells something not owned
                + "2026-01-06,BUY,MSFT,0,50\n"             // line 9 zero quantity
                + "2026-01-07,BUY,MSFT,5,-1\n"             // line 10 negative price
                + "2026-01-08,SELL,AAPL,99,100\n"          // line 11 sells too many
                + "2026-01-09,BUY,MSFT,5,50.00\n";         // line 12 good
        Path file = tempDir.resolve("transactions.csv");
        Files.writeString(file, content);

        CsvStorage storage = new CsvStorage(tempDir);
        Portfolio loaded = storage.load();

        assertEquals(2, loaded.getTransactions().size());
        assertEquals(2, loaded.getHoldings().size());
        assertEquals(9, skippedCount(storage));
        assertTrue(storage.getWarnings().get(0).contains("line 3"));
        // the original file is untouched and a copy is kept
        assertEquals(content, Files.readString(file));
        List<String> copies = filesInFolderStartingWith("transactions.csv.bad-");
        assertEquals(1, copies.size());
        assertEquals(content, Files.readString(tempDir.resolve(copies.get(0))));
    }

    @Test
    void badPriceLinesAreSkipped() throws IOException {
        Files.writeString(tempDir.resolve("transactions.csv"),
                "date,type,symbol,quantity,price\n2026-01-01,BUY,AAPL,10,100.00\n");
        Files.writeString(tempDir.resolve("prices.csv"),
                "symbol,price\nAAPL,210.50\nNOPE,5\nAAPL\nAAPL,abc\nAAPL,-1\nAAPL,1,2\n");

        CsvStorage storage = new CsvStorage(tempDir);
        Portfolio loaded = storage.load();

        assertEquals(new BigDecimal("210.50"), loaded.getPrices().get("AAPL"));
        assertEquals(1, loaded.getPrices().size());
        assertEquals(5, skippedCount(storage));
        assertEquals(1, filesInFolderStartingWith("prices.csv.bad-").size());
    }

    @Test
    void aCleanFileCreatesNoCopies() throws IOException {
        Files.writeString(tempDir.resolve("transactions.csv"),
                "date,type,symbol,quantity,price\n2026-01-01,BUY,AAPL,10,100.00\n");
        new CsvStorage(tempDir).load();
        assertTrue(filesInFolderStartingWith("transactions.csv.bad-").isEmpty());
    }

    @Test
    void aSymbolWithACommaIsRejectedBeforeItCanCorruptTheFile() {
        assertThrows(IllegalArgumentException.class,
                () -> tx("2026-01-01", TransactionType.BUY, "A,B", 1, "10"));
    }

    // ----------------------------------------------------------- safe saving

    @Test
    void savingKeepsTheOldVersionAsBackupAndLeavesNoTempFiles() throws IOException {
        CsvStorage storage = new CsvStorage(tempDir);
        Portfolio p = new Portfolio();
        p.addTransaction(tx("2026-01-10", TransactionType.BUY, "AAPL", 10, "180.00"));
        storage.save(p);
        String firstVersion = Files.readString(tempDir.resolve("transactions.csv"));

        p.addTransaction(tx("2026-01-11", TransactionType.BUY, "MSFT", 1, "400.00"));
        storage.save(p);

        assertEquals(firstVersion, Files.readString(tempDir.resolve("transactions.csv.bak")));
        assertTrue(Files.readString(tempDir.resolve("transactions.csv")).contains("MSFT"));
        assertTrue(filesInFolderStartingWith("transactions.csv.tmp").isEmpty());
        assertTrue(filesInFolderStartingWith("prices.csv.tmp").isEmpty());
    }

    @Test
    void savingCreatesTheFolderIfItDoesNotExist() throws IOException {
        Path folder = tempDir.resolve("a").resolve("b");
        new CsvStorage(folder).save(new Portfolio());
        assertTrue(Files.exists(folder.resolve("transactions.csv")));
    }

    @Test
    void loadingTwiceDoesNotDuplicateWarningsOrData() throws IOException {
        Files.writeString(tempDir.resolve("transactions.csv"),
                "date,type,symbol,quantity,price\nbad\n2026-01-01,BUY,AAPL,1,1\n");
        CsvStorage storage = new CsvStorage(tempDir);
        storage.load();
        Portfolio second = storage.load();
        assertEquals(1, skippedCount(storage));
        assertEquals(1, second.getTransactions().size());
    }
}
