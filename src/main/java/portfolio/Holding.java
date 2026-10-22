package portfolio;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * What you currently own of one symbol.
 *
 * Cost basis method: WEIGHTED AVERAGE COST.
 *  - Every buy adds (quantity x price) to the total cost.
 *  - Every sell removes cost in proportion to the shares sold, so the
 *    average cost per share does not change when you sell.
 * The cost removed by a sale is returned, so the Portfolio can work out
 * the realized profit or loss of that sale.
 */
public class Holding {
    /** Decimal places kept internally for cost, so tiny rounding never builds up. */
    private static final int COST_SCALE = 6;

    private final String symbol;
    private int quantity;
    private BigDecimal totalCost = BigDecimal.ZERO;

    public Holding(String symbol) {
        this.symbol = symbol;
    }

    public void buy(int qty, BigDecimal price) {
        if (qty <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        int newQuantity;
        try {
            newQuantity = Math.addExact(quantity, qty);   // int would silently wrap around
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("Quantity too large: you cannot hold more than "
                    + Integer.MAX_VALUE + " shares of " + symbol);
        }
        totalCost = totalCost.add(price.multiply(BigDecimal.valueOf(qty)));
        quantity = newQuantity;
    }

    /**
     * Removes shares and returns the cost basis of the shares removed.
     * Nothing is changed if the sale is invalid.
     */
    public BigDecimal sell(int qty) {
        if (qty <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        if (qty > quantity) {
            throw new IllegalArgumentException(
                    "Cannot sell " + qty + " " + symbol + ", you only hold " + quantity);
        }
        BigDecimal costRemoved;
        if (qty == quantity) {
            costRemoved = totalCost;                      // selling everything: remove all cost
        } else {
            costRemoved = totalCost.multiply(BigDecimal.valueOf(qty))
                    .divide(BigDecimal.valueOf(quantity), COST_SCALE, RoundingMode.HALF_UP);
        }
        // Subtracting (instead of recalculating) keeps remaining cost + removed cost exact.
        totalCost = totalCost.subtract(costRemoved);
        quantity -= qty;
        if (quantity == 0) {
            totalCost = BigDecimal.ZERO;
        }
        return costRemoved;
    }

    /** Average cost per share, rounded to 2 decimals for display. */
    public BigDecimal averageCost() {
        if (quantity == 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return totalCost.divide(BigDecimal.valueOf(quantity), 2, RoundingMode.HALF_UP);
    }

    public String getSymbol() { return symbol; }
    public int getQuantity() { return quantity; }

    /** Total purchase cost of the shares still held, rounded to 2 decimals. */
    public BigDecimal getTotalCost() { return totalCost.setScale(2, RoundingMode.HALF_UP); }

    /** Same as getTotalCost() but with full internal precision (used for calculations). */
    public BigDecimal getExactTotalCost() { return totalCost; }
}
