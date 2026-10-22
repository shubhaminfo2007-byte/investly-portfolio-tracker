package portfolio;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * One buy or sell. Transactions never change after they are created,
 * and the constructor refuses anything invalid, so a bad Transaction
 * can never exist.
 */
public class Transaction {
    /** Largest price per share we accept (catches typing mistakes). */
    public static final BigDecimal MAX_PRICE = new BigDecimal("1000000000");
    /** Prices may have at most this many decimal places. */
    public static final int MAX_PRICE_DECIMALS = 4;

    /** 1 to 15 characters: letters, digits, '.', '-' or '&' (e.g. AAPL, M&M, BAJAJ-AUTO, BRK.B). */
    private static final Pattern SYMBOL_PATTERN = Pattern.compile("[A-Z0-9][A-Z0-9.&-]{0,14}");

    private final LocalDate date;
    private final TransactionType type;
    private final String symbol;
    private final int quantity;
    private final BigDecimal price;

    public Transaction(LocalDate date, TransactionType type, String symbol,
                       int quantity, BigDecimal price) {
        if (date == null) {
            throw new IllegalArgumentException("Date is required");
        }
        if (type == null) {
            throw new IllegalArgumentException("Transaction type is required (BUY or SELL)");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive (got " + quantity + ")");
        }
        checkPrice(price);
        this.date = date;
        this.type = type;
        this.symbol = normalizeSymbol(symbol);
        this.quantity = quantity;
        this.price = price;
    }

    /** Trims and upper-cases a symbol, or throws if it is not a valid stock symbol. */
    public static String normalizeSymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("Symbol cannot be empty");
        }
        String clean = symbol.trim().toUpperCase(Locale.ROOT);
        if (!SYMBOL_PATTERN.matcher(clean).matches()) {
            throw new IllegalArgumentException("Invalid symbol '" + symbol.trim()
                    + "': use 1-15 letters or digits (also . - &), no spaces or commas");
        }
        return clean;
    }

    /** Throws if a price is missing, not positive, too large or too precise. */
    public static void checkPrice(BigDecimal price) {
        if (price == null) {
            throw new IllegalArgumentException("Price is required");
        }
        if (price.signum() <= 0) {
            throw new IllegalArgumentException("Price must be positive");
        }
        if (price.compareTo(MAX_PRICE) > 0) {
            throw new IllegalArgumentException("Price is too large (maximum " + MAX_PRICE.toPlainString() + ")");
        }
        if (price.stripTrailingZeros().scale() > MAX_PRICE_DECIMALS) {
            throw new IllegalArgumentException("Price can have at most " + MAX_PRICE_DECIMALS + " decimal places");
        }
    }

    public LocalDate getDate() { return date; }
    public TransactionType getType() { return type; }
    public String getSymbol() { return symbol; }
    public int getQuantity() { return quantity; }
    public BigDecimal getPrice() { return price; }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "%s  %-4s %-8s %5d @ %s",
                date, type, symbol, quantity, price.toPlainString());
    }
}
