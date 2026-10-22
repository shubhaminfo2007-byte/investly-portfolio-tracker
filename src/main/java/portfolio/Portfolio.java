package portfolio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * The whole portfolio: the transaction history, the holdings built from it,
 * the latest prices you entered, and the profit you have already locked in.
 *
 * ASSUMPTIONS (also in README.md)
 *  - Cost basis is WEIGHTED AVERAGE COST (see Holding).
 *  - Realized profit/loss of a sale = (sale price x quantity) - cost basis of the shares sold.
 *  - Unrealized profit/loss = current market value - cost of the shares you still hold.
 *  - Total profit = realized + unrealized. It is NOT "sale proceeds minus purchases".
 *  - Prices are typed in by you. If no price was set for a holding, its market value is
 *    taken as its cost, so its unrealized profit shows as 0 (hasPrice() tells you which).
 *  - No fees, taxes, dividends or stock splits. One currency.
 *  - Transactions are processed in the order they were recorded.
 *
 * Money is BigDecimal. Calculations use full precision; results are rounded
 * to 2 decimals (HALF_UP) only when they are returned. Because of that, rows
 * shown on screen can differ from a displayed total by a cent.
 */
public class Portfolio {
    private final List<Transaction> transactions = new ArrayList<>();
    private final Map<String, Holding> holdings = new TreeMap<>();
    private final Map<String, BigDecimal> prices = new TreeMap<>();
    /** Realized profit/loss per symbol, kept even after the position is fully sold. */
    private final Map<String, BigDecimal> realizedBySymbol = new TreeMap<>();
    /** Every symbol that has at least one transaction. */
    private final Set<String> tradedSymbols = new TreeSet<>();
    /** Total cost basis of all shares sold so far. */
    private BigDecimal costOfSharesSold = BigDecimal.ZERO;

    // ---------------------------------------------------------------- recording

    /**
     * Records a transaction and updates holdings and realized profit.
     * If the transaction is invalid (for example selling more than you own),
     * an IllegalArgumentException is thrown and NOTHING is changed.
     */
    public void addTransaction(Transaction t) {
        if (t == null) {
            throw new IllegalArgumentException("Transaction is required");
        }
        String symbol = t.getSymbol();

        if (t.getType() == TransactionType.BUY) {
            Holding holding = holdings.get(symbol);
            if (holding == null) {
                holding = new Holding(symbol);
            }
            holding.buy(t.getQuantity(), t.getPrice());   // throws before changing anything
            holdings.put(symbol, holding);
        } else {
            Holding holding = holdings.get(symbol);
            if (holding == null) {
                throw new IllegalArgumentException("Cannot sell " + symbol + ": you do not hold it");
            }
            BigDecimal costRemoved = holding.sell(t.getQuantity());   // throws if not enough shares
            BigDecimal proceeds = t.getPrice().multiply(BigDecimal.valueOf(t.getQuantity()));
            realizedBySymbol.merge(symbol, proceeds.subtract(costRemoved), BigDecimal::add);
            costOfSharesSold = costOfSharesSold.add(costRemoved);
            if (holding.getQuantity() == 0) {
                holdings.remove(symbol);
            }
        }
        tradedSymbols.add(symbol);
        transactions.add(t);
    }

    /** Sets the current price. The symbol must already appear in a transaction. */
    public void setPrice(String symbol, BigDecimal price) {
        String clean = Transaction.normalizeSymbol(symbol);
        Transaction.checkPrice(price);
        if (!tradedSymbols.contains(clean)) {
            throw new IllegalArgumentException("Unknown symbol " + clean + ": record a BUY for it first");
        }
        prices.put(clean, price);
    }

    // ------------------------------------------------------- one holding

    public boolean hasPrice(Holding h) {
        return prices.containsKey(h.getSymbol());
    }

    /** The price you entered, or the average cost if you have not entered one. */
    public BigDecimal currentPrice(Holding h) {
        return prices.getOrDefault(h.getSymbol(), h.averageCost());
    }

    public BigDecimal marketValue(Holding h) {
        return money(exactMarketValue(h));
    }

    /** Unrealized profit/loss of one holding (not yet sold). */
    public BigDecimal unrealizedGain(Holding h) {
        return money(exactMarketValue(h).subtract(h.getExactTotalCost()));
    }

    /** Unrealized return of one holding, in percent of the cost of the shares still held. */
    public BigDecimal unrealizedReturnPercent(Holding h) {
        return percent(exactMarketValue(h).subtract(h.getExactTotalCost()), h.getExactTotalCost());
    }

    /** Share of the whole portfolio this holding makes up, in percent. */
    public BigDecimal allocationPercent(Holding h) {
        return percent(exactMarketValue(h), exactTotalValue());
    }

    // ------------------------------------------------- whole portfolio

    /** Current market value of everything you still hold. */
    public BigDecimal totalValue() {
        return money(exactTotalValue());
    }

    /** Purchase cost of the shares you still hold (not of shares already sold). */
    public BigDecimal totalCost() {
        return money(exactTotalCost());
    }

    public BigDecimal totalUnrealizedGain() {
        return money(exactTotalValue().subtract(exactTotalCost()));
    }

    public BigDecimal totalUnrealizedReturnPercent() {
        return percent(exactTotalValue().subtract(exactTotalCost()), exactTotalCost());
    }

    /** Profit or loss already locked in by selling. */
    public BigDecimal totalRealizedGain() {
        return money(exactRealized());
    }

    /** Realized profit/loss for one symbol (0.00 if you never sold it). */
    public BigDecimal realizedGainFor(String symbol) {
        return money(realizedBySymbol.getOrDefault(Transaction.normalizeSymbol(symbol), BigDecimal.ZERO));
    }

    /** Cost basis of all shares sold so far. */
    public BigDecimal costOfSharesSold() {
        return money(costOfSharesSold);
    }

    /** Realized return in percent of the cost basis of the shares sold. */
    public BigDecimal realizedReturnPercent() {
        return percent(exactRealized(), costOfSharesSold);
    }

    /** Realized + unrealized profit. */
    public BigDecimal totalProfit() {
        return money(exactRealized().add(exactTotalValue()).subtract(exactTotalCost()));
    }

    /**
     * Total profit in percent of everything you ever bought
     * (cost of shares still held + cost basis of shares sold).
     */
    public BigDecimal totalReturnPercent() {
        BigDecimal profit = exactRealized().add(exactTotalValue()).subtract(exactTotalCost());
        return percent(profit, exactTotalCost().add(costOfSharesSold));
    }

    // ---------------------------------------------------------- read-only views

    public Collection<Holding> getHoldings() {
        return Collections.unmodifiableCollection(holdings.values());
    }

    /** The full history, in the order it was recorded. */
    public List<Transaction> getTransactions() {
        return Collections.unmodifiableList(transactions);
    }

    public Map<String, BigDecimal> getPrices() {
        return Collections.unmodifiableMap(prices);
    }

    /** Symbols that have at least one SELL. */
    public Set<String> getRealizedSymbols() {
        return Collections.unmodifiableSet(realizedBySymbol.keySet());
    }

    public boolean isTradedSymbol(String symbol) {
        return tradedSymbols.contains(Transaction.normalizeSymbol(symbol));
    }

    // -------------------------------------------------------------- helpers

    private BigDecimal exactMarketValue(Holding h) {
        BigDecimal price = prices.get(h.getSymbol());
        if (price == null) {
            return h.getExactTotalCost();      // no price entered: value = cost, so gain = 0
        }
        return price.multiply(BigDecimal.valueOf(h.getQuantity()));
    }

    private BigDecimal exactTotalValue() {
        BigDecimal sum = BigDecimal.ZERO;
        for (Holding h : holdings.values()) {
            sum = sum.add(exactMarketValue(h));
        }
        return sum;
    }

    private BigDecimal exactTotalCost() {
        BigDecimal sum = BigDecimal.ZERO;
        for (Holding h : holdings.values()) {
            sum = sum.add(h.getExactTotalCost());
        }
        return sum;
    }

    private BigDecimal exactRealized() {
        BigDecimal sum = BigDecimal.ZERO;
        for (BigDecimal gain : realizedBySymbol.values()) {
            sum = sum.add(gain);
        }
        return sum;
    }

    private static BigDecimal money(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    /** part / whole x 100, rounded to 2 decimals; 0.00 when whole is zero. */
    private static BigDecimal percent(BigDecimal part, BigDecimal whole) {
        if (whole.signum() == 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return part.multiply(BigDecimal.valueOf(100))
                .divide(whole, 2, RoundingMode.HALF_UP);
    }
}
