package portfolio;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Saves and loads a portfolio as two small CSV files inside one folder.
 *
 *   transactions.csv   date,type,symbol,quantity,price     (the full history)
 *   prices.csv         symbol,price                        (latest prices you entered)
 *
 * Realized and unrealized profit are NOT stored: they are recalculated by
 * replaying the transactions every time the file is loaded.
 *
 * Safety rules:
 *  - load() never crashes on a bad line. It skips the line and adds a message
 *    to getWarnings(). Before that, a copy of the original file is kept as
 *    "<name>.bad-<time>" so nothing you typed by hand is ever lost.
 *  - save() writes to a temporary file first and then replaces the real file,
 *    so a crash while saving cannot leave a half-written file. The previous
 *    version is kept as "<name>.bak".
 */
public class CsvStorage {
    private static final String TRANSACTIONS_FILE = "transactions.csv";
    private static final String PRICES_FILE = "prices.csv";
    private static final String TRANSACTIONS_HEADER = "date,type,symbol,quantity,price";
    private static final String PRICES_HEADER = "symbol,price";
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    /** Usernames become folder names, so only these characters are allowed (blocks "../" tricks). */
    private static final Pattern USERNAME = Pattern.compile("[a-z0-9_-]{1,32}");

    private final Path folder;
    private final List<String> warnings = new ArrayList<>();

    public CsvStorage(Path folder) {
        this.folder = folder;
    }

    /** Trims and lower-cases a username, or throws if it is not safe to use as a folder name. */
    public static String normalizeUsername(String username) {
        String clean = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        if (!USERNAME.matcher(clean).matches()) {
            throw new IllegalArgumentException(
                    "Username must be 1-32 characters: letters, numbers, '-' or '_' (no spaces)");
        }
        return clean;
    }

    /** Storage for one user: {dataRoot}/users/{username}/transactions.csv and prices.csv */
    public static CsvStorage forUser(Path dataRoot, String username) {
        return new CsvStorage(dataRoot.resolve("users").resolve(normalizeUsername(username)));
    }

    public Path getFolder() {
        return folder;
    }

    /** Problems found by the most recent load(). Empty if everything was fine. */
    public List<String> getWarnings() {
        return Collections.unmodifiableList(warnings);
    }

    // ------------------------------------------------------------------ save

    public void save(Portfolio portfolio) throws IOException {
        Files.createDirectories(folder);

        List<String> lines = new ArrayList<>();
        lines.add(TRANSACTIONS_HEADER);
        for (Transaction t : portfolio.getTransactions()) {
            lines.add(String.join(",", t.getDate().toString(), t.getType().name(),
                    t.getSymbol(), String.valueOf(t.getQuantity()), t.getPrice().toPlainString()));
        }
        writeSafely(TRANSACTIONS_FILE, lines);

        List<String> priceLines = new ArrayList<>();
        priceLines.add(PRICES_HEADER);
        for (Map.Entry<String, BigDecimal> e : portfolio.getPrices().entrySet()) {
            priceLines.add(e.getKey() + "," + e.getValue().toPlainString());
        }
        writeSafely(PRICES_FILE, priceLines);
    }

    /** temp file -> keep old version as .bak -> move temp over the real file. */
    private void writeSafely(String fileName, List<String> lines) throws IOException {
        Path target = folder.resolve(fileName);
        Path temp = folder.resolve(fileName + ".tmp");
        Files.write(temp, lines);
        if (Files.exists(target)) {
            Files.copy(target, folder.resolve(fileName + ".bak"), StandardCopyOption.REPLACE_EXISTING);
        }
        try {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    // ------------------------------------------------------------------ load

    /**
     * Loads a portfolio. Missing files give an empty portfolio. Bad lines are
     * skipped and reported through getWarnings(). Only real file problems
     * (for example no permission to read) throw an IOException.
     */
    public Portfolio load() throws IOException {
        warnings.clear();
        Portfolio portfolio = new Portfolio();
        loadTransactions(portfolio);
        loadPrices(portfolio);   // after transactions: prices are only accepted for known symbols
        return portfolio;
    }

    private void loadTransactions(Portfolio portfolio) throws IOException {
        Path file = folder.resolve(TRANSACTIONS_FILE);
        if (!Files.exists(file)) {
            return;
        }
        List<String> lines = Files.readAllLines(file);
        int problems = 0;
        for (int i = 0; i < lines.size(); i++) {
            String line = (i == 0) ? stripBom(lines.get(i)) : lines.get(i);
            if (line.isBlank() || (i == 0 && isHeader(line, "date"))) {
                continue;
            }
            try {
                portfolio.addTransaction(parseTransaction(line));
            } catch (IllegalArgumentException e) {
                warnings.add(TRANSACTIONS_FILE + " line " + (i + 1) + " skipped: " + e.getMessage());
                problems++;
            }
        }
        if (problems > 0) {
            keepCopyOfBadFile(file);
        }
    }

    private void loadPrices(Portfolio portfolio) throws IOException {
        Path file = folder.resolve(PRICES_FILE);
        if (!Files.exists(file)) {
            return;
        }
        List<String> lines = Files.readAllLines(file);
        int problems = 0;
        for (int i = 0; i < lines.size(); i++) {
            String line = (i == 0) ? stripBom(lines.get(i)) : lines.get(i);
            if (line.isBlank() || (i == 0 && isHeader(line, "symbol"))) {
                continue;
            }
            try {
                String[] parts = line.split(",", -1);
                if (parts.length != 2) {
                    throw new IllegalArgumentException("expected 2 values but found " + parts.length);
                }
                portfolio.setPrice(parts[0], parsePrice(parts[1]));
            } catch (IllegalArgumentException e) {
                warnings.add(PRICES_FILE + " line " + (i + 1) + " skipped: " + e.getMessage());
                problems++;
            }
        }
        if (problems > 0) {
            keepCopyOfBadFile(file);
        }
    }

    private Transaction parseTransaction(String line) {
        String[] p = line.split(",", -1);
        if (p.length != 5) {
            throw new IllegalArgumentException("expected 5 values but found " + p.length);
        }
        LocalDate date;
        try {
            date = LocalDate.parse(p[0].trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("invalid date '" + p[0].trim() + "' (use YYYY-MM-DD)");
        }
        TransactionType type;
        try {
            type = TransactionType.valueOf(p[1].trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("unknown type '" + p[1].trim() + "' (use BUY or SELL)");
        }
        int quantity;
        try {
            quantity = Integer.parseInt(p[3].trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("quantity '" + p[3].trim() + "' is not a whole number");
        }
        // The Transaction constructor checks symbol, quantity and price rules.
        return new Transaction(date, type, p[2], quantity, parsePrice(p[4]));
    }

    private BigDecimal parsePrice(String text) {
        try {
            return new BigDecimal(text.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("price '" + text.trim() + "' is not a number");
        }
    }

    private boolean isHeader(String line, String firstColumn) {
        return line.trim().toLowerCase(Locale.ROOT).startsWith(firstColumn);
    }

    private String stripBom(String line) {
        return line.startsWith("﻿") ? line.substring(1) : line;
    }

    /** Copies the untouched original next to it, because the next save will rewrite the file. */
    private void keepCopyOfBadFile(Path file) throws IOException {
        Path copy = folder.resolve(file.getFileName() + ".bad-" + LocalDateTime.now().format(STAMP));
        Files.copy(file, copy, StandardCopyOption.REPLACE_EXISTING);
        warnings.add("The original " + file.getFileName() + " was kept as " + copy.getFileName());
    }
}
