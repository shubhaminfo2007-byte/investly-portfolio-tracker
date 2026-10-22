package portfolio;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PortfolioTest {
    private Portfolio portfolio;

    private static Transaction tx(TransactionType type, String symbol, int qty, String price) {
        return new Transaction(LocalDate.of(2026, 1, 1), type, symbol, qty, new BigDecimal(price));
    }

    private static BigDecimal money(String text) {
        return new BigDecimal(text);
    }

    private Holding only() {
        return portfolio.getHoldings().iterator().next();
    }

    @BeforeEach
    void setUp() {
        portfolio = new Portfolio();
    }

    // ---------------------------------------------------------------- buying

    @Test
    void buyingIncreasesTheHolding() {
        portfolio.addTransaction(tx(TransactionType.BUY, "AAPL", 10, "180.00"));
        assertEquals(10, only().getQuantity());
        portfolio.addTransaction(tx(TransactionType.BUY, "AAPL", 5, "190.00"));
        assertEquals(15, only().getQuantity());
        assertEquals(1, portfolio.getHoldings().size());
    }

    @Test
    void averageCostAcrossTwoBuys() {
        portfolio.addTransaction(tx(TransactionType.BUY, "AAPL", 10, "180.00"));
        portfolio.addTransaction(tx(TransactionType.BUY, "AAPL", 5, "190.00"));
        assertEquals(money("183.33"), only().averageCost());
        assertEquals(money("2750.00"), only().getTotalCost());
    }

    @Test
    void differentSymbolsAreSeparateHoldings() {
        portfolio.addTransaction(tx(TransactionType.BUY, "MSFT", 2, "400.00"));
        portfolio.addTransaction(tx(TransactionType.BUY, "AAPL", 3, "100.00"));
        assertEquals(2, portfolio.getHoldings().size());
        assertEquals("AAPL", only().getSymbol()); // sorted by symbol
    }

    // --------------------------------------------------------------- selling

    @Test
    void sellingReducesTheHoldingAndKeepsAverageCost() {
        portfolio.addTransaction(tx(TransactionType.BUY, "AAPL", 10, "100.00"));
        portfolio.addTransaction(tx(TransactionType.SELL, "AAPL", 4, "150.00"));
        assertEquals(6, only().getQuantity());
        assertEquals(money("100.00"), only().averageCost());
        assertEquals(money("600.00"), only().getTotalCost());
    }

    @Test
    void sellingEverythingRemovesTheHoldingButKeepsHistory() {
        portfolio.addTransaction(tx(TransactionType.BUY, "MSFT", 3, "400.00"));
        portfolio.addTransaction(tx(TransactionType.SELL, "MSFT", 3, "410.00"));
        assertTrue(portfolio.getHoldings().isEmpty());
        assertEquals(2, portfolio.getTransactions().size());
        assertEquals(money("0.00"), portfolio.totalCost());
    }

    @Test
    void sellingMoreThanOwnedIsRejectedAndChangesNothing() {
        portfolio.addTransaction(tx(TransactionType.BUY, "AAPL", 2, "100.00"));
        assertThrows(IllegalArgumentException.class,
                () -> portfolio.addTransaction(tx(TransactionType.SELL, "AAPL", 3, "100.00")));
        assertEquals(2, only().getQuantity());
        assertEquals(money("200.00"), only().getTotalCost());
        assertEquals(1, portfolio.getTransactions().size());
        assertEquals(money("0.00"), portfolio.totalRealizedGain());
    }

    @Test
    void sellingAnUnownedSymbolFailsAndLeavesNoEmptyHolding() {
        assertThrows(IllegalArgumentException.class,
                () -> portfolio.addTransaction(tx(TransactionType.SELL, "XYZ", 1, "10.00")));
        assertTrue(portfolio.getHoldings().isEmpty());
        assertTrue(portfolio.getTransactions().isEmpty());
        assertFalse(portfolio.isTradedSymbol("XYZ"));
    }

    @Test
    void buyingAgainAfterSellingEverythingStartsFresh() {
        portfolio.addTransaction(tx(TransactionType.BUY, "AAPL", 10, "100.00"));
        portfolio.addTransaction(tx(TransactionType.SELL, "AAPL", 10, "150.00"));
        portfolio.addTransaction(tx(TransactionType.BUY, "AAPL", 5, "200.00"));
        assertEquals(5, only().getQuantity());
        assertEquals(money("200.00"), only().averageCost());
        assertEquals(money("500.00"), portfolio.totalRealizedGain()); // earlier profit is kept
    }

    // ------------------------------------------- realized vs unrealized profit

    @Test
    void realizedProfitUsesWeightedAverageCost() {
        portfolio.addTransaction(tx(TransactionType.BUY, "AAPL", 10, "100.00"));
        portfolio.addTransaction(tx(TransactionType.BUY, "AAPL", 10, "120.00")); // 20 shares, avg 110
        portfolio.addTransaction(tx(TransactionType.SELL, "AAPL", 5, "130.00"));  // cost removed 550

        assertEquals(money("100.00"), portfolio.totalRealizedGain());   // 650 - 550
        assertEquals(money("100.00"), portfolio.realizedGainFor("aapl"));
        assertEquals(15, only().getQuantity());
        assertEquals(money("1650.00"), portfolio.totalCost());          // remaining cost only
        assertEquals(money("550.00"), portfolio.costOfSharesSold());
        assertEquals(money("18.18"), portfolio.realizedReturnPercent()); // 100 / 550
    }

    @Test
    void unrealizedProfitIsSeparateFromRealizedProfit() {
        portfolio.addTransaction(tx(TransactionType.BUY, "AAPL", 10, "100.00"));
        portfolio.addTransaction(tx(TransactionType.BUY, "AAPL", 10, "120.00"));
        portfolio.addTransaction(tx(TransactionType.SELL, "AAPL", 5, "130.00"));
        portfolio.setPrice("AAPL", new BigDecimal("125.00"));

        assertEquals(money("1875.00"), portfolio.totalValue());              // 15 x 125
        assertEquals(money("225.00"), portfolio.totalUnrealizedGain());      // 1875 - 1650
        assertEquals(money("225.00"), portfolio.unrealizedGain(only()));
        assertEquals(money("13.64"), portfolio.totalUnrealizedReturnPercent()); // 225 / 1650
        assertEquals(money("13.64"), portfolio.unrealizedReturnPercent(only()));
        assertEquals(money("100.00"), portfolio.totalRealizedGain());
        assertEquals(money("325.00"), portfolio.totalProfit());              // 100 + 225
        assertEquals(money("14.77"), portfolio.totalReturnPercent());        // 325 / (1650 + 550)
    }

    @Test
    void aLossIsRealizedAsANegativeNumber() {
        portfolio.addTransaction(tx(TransactionType.BUY, "AAPL", 10, "100.00"));
        portfolio.addTransaction(tx(TransactionType.SELL, "AAPL", 4, "80.00"));
        assertEquals(money("-80.00"), portfolio.totalRealizedGain());        // 320 - 400
        assertEquals(money("600.00"), portfolio.totalCost());
        assertEquals(money("-20.00"), portfolio.realizedReturnPercent());
    }

    @Test
    void profitIsNotProceedsMinusPurchases() {
        // Bought 10 at 100 (1000). Sold 5 at 150 (750). Price now 150.
        // The wrong formula (proceeds - purchases) would say -250.
        portfolio.addTransaction(tx(TransactionType.BUY, "AAPL", 10, "100.00"));
        portfolio.addTransaction(tx(TransactionType.SELL, "AAPL", 5, "150.00"));
        portfolio.setPrice("AAPL", new BigDecimal("150.00"));

        assertEquals(money("250.00"), portfolio.totalRealizedGain());   // 5 x (150 - 100)
        assertEquals(money("250.00"), portfolio.totalUnrealizedGain()); // 750 - 500
        assertEquals(money("500.00"), portfolio.totalProfit());
        assertEquals(money("50.00"), portfolio.totalReturnPercent());   // 500 / (500 held + 500 sold)
    }

    @Test
    void fullySoldPositionStillShowsItsRealizedProfit() {
        portfolio.addTransaction(tx(TransactionType.BUY, "Y", 10, "100.00"));
        portfolio.addTransaction(tx(TransactionType.SELL, "Y", 10, "150.00"));
        assertTrue(portfolio.getHoldings().isEmpty());
        assertEquals(money("500.00"), portfolio.totalRealizedGain());
        assertEquals(money("0.00"), portfolio.totalUnrealizedGain());
        assertEquals(money("500.00"), portfolio.totalProfit());
        assertEquals(money("50.00"), portfolio.totalReturnPercent());
    }

    @Test
    void realizedProfitIsTrackedPerSymbol() {
        portfolio.addTransaction(tx(TransactionType.BUY, "AAA", 10, "10.00"));
        portfolio.addTransaction(tx(TransactionType.BUY, "BBB", 10, "10.00"));
        portfolio.addTransaction(tx(TransactionType.SELL, "AAA", 10, "12.00"));
        portfolio.addTransaction(tx(TransactionType.SELL, "BBB", 5, "9.00"));
        assertEquals(money("20.00"), portfolio.realizedGainFor("AAA"));
        assertEquals(money("-5.00"), portfolio.realizedGainFor("BBB"));
        assertEquals(money("0.00"), portfolio.realizedGainFor("CCC"));
        assertEquals(money("15.00"), portfolio.totalRealizedGain());
    }

    @Test
    void remainingCostPlusSoldCostEqualsTotalBought() {
        // 3 @ 10.00 + 4 @ 10.01 = 70.04, which does not divide evenly by 7.
        portfolio.addTransaction(tx(TransactionType.BUY, "Z", 3, "10.00"));
        portfolio.addTransaction(tx(TransactionType.BUY, "Z", 4, "10.01"));
        portfolio.addTransaction(tx(TransactionType.SELL, "Z", 1, "11.00"));
        assertEquals(money("60.03"), portfolio.totalCost());
        assertEquals(money("10.01"), portfolio.costOfSharesSold());
        assertEquals(money("0.99"), portfolio.totalRealizedGain());
        assertEquals(money("70.04"), portfolio.totalCost().add(portfolio.costOfSharesSold()));
        // sell the rest: no cost may be left behind
        portfolio.addTransaction(tx(TransactionType.SELL, "Z", 6, "11.00"));
        assertEquals(money("0.00"), portfolio.totalCost());
        assertEquals(money("70.04"), portfolio.costOfSharesSold());
        assertEquals(money("6.96"), portfolio.totalRealizedGain()); // 77.00 - 70.04
    }

    // ---------------------------------------------------- value and allocation

    @Test
    void valueGainAndAllocationUseCurrentPrices() {
        portfolio.addTransaction(tx(TransactionType.BUY, "AAA", 10, "100.00")); // cost 1000
        portfolio.addTransaction(tx(TransactionType.BUY, "BBB", 10, "50.00"));  // cost 500
        portfolio.setPrice("AAA", new BigDecimal("120.00"));                   // value 1200
        portfolio.setPrice("BBB", new BigDecimal("40.00"));                    // value 400

        assertEquals(money("1600.00"), portfolio.totalValue());
        assertEquals(money("1500.00"), portfolio.totalCost());
        assertEquals(money("100.00"), portfolio.totalUnrealizedGain());

        Holding aaa = only(); // TreeMap: AAA first
        assertEquals(money("200.00"), portfolio.unrealizedGain(aaa));
        assertEquals(money("20.00"), portfolio.unrealizedReturnPercent(aaa));
        assertEquals(money("75.00"), portfolio.allocationPercent(aaa));
    }

    @Test
    void holdingWithoutAPriceIsValuedAtCostSoUnrealizedIsZero() {
        portfolio.addTransaction(tx(TransactionType.BUY, "AAPL", 10, "100.00"));
        assertFalse(portfolio.hasPrice(only()));
        assertEquals(money("1000.00"), portfolio.marketValue(only()));
        assertEquals(money("0.00"), portfolio.totalUnrealizedGain());
        portfolio.setPrice("AAPL", new BigDecimal("110.00"));
        assertTrue(portfolio.hasPrice(only()));
        assertEquals(money("100.00"), portfolio.totalUnrealizedGain());
    }

    @Test
    void emptyPortfolioHasZeroTotalsAndNoDivisionByZero() {
        assertEquals(money("0.00"), portfolio.totalValue());
        assertEquals(money("0.00"), portfolio.totalCost());
        assertEquals(money("0.00"), portfolio.totalProfit());
        assertEquals(money("0.00"), portfolio.totalReturnPercent());
        assertEquals(money("0.00"), portfolio.totalUnrealizedReturnPercent());
        assertEquals(money("0.00"), portfolio.realizedReturnPercent());
    }

    // -------------------------------------------------------------- history

    @Test
    void historyKeepsEveryTransactionInOrderAndCannotBeEdited() {
        Transaction first = tx(TransactionType.BUY, "AAPL", 10, "100.00");
        Transaction second = tx(TransactionType.SELL, "AAPL", 2, "110.00");
        portfolio.addTransaction(first);
        portfolio.addTransaction(second);
        assertEquals(2, portfolio.getTransactions().size());
        assertTrue(portfolio.getTransactions().get(0) == first);
        assertTrue(portfolio.getTransactions().get(1) == second);
        assertThrows(UnsupportedOperationException.class,
                () -> portfolio.getTransactions().add(first));
    }

    // ----------------------------------------------------------- validation

    @Test
    void invalidQuantitiesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> tx(TransactionType.BUY, "AAPL", 0, "100.00"));
        assertThrows(IllegalArgumentException.class, () -> tx(TransactionType.BUY, "AAPL", -3, "100.00"));
    }

    @Test
    void invalidPricesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> tx(TransactionType.BUY, "AAPL", 1, "0"));
        assertThrows(IllegalArgumentException.class, () -> tx(TransactionType.BUY, "AAPL", 1, "-5"));
        assertThrows(IllegalArgumentException.class, () -> tx(TransactionType.BUY, "AAPL", 1, "100.12345"));
        assertThrows(IllegalArgumentException.class, () -> tx(TransactionType.BUY, "AAPL", 1, "1000000001"));
        assertThrows(IllegalArgumentException.class, () -> tx(TransactionType.BUY, "AAPL", 1, "1E+400"));
        assertThrows(IllegalArgumentException.class,
                () -> new Transaction(LocalDate.of(2026, 1, 1), TransactionType.BUY, "AAPL", 1, null));
    }

    @Test
    void reasonablePricesAreAccepted() {
        tx(TransactionType.BUY, "AAPL", 1, "100.1234");
        tx(TransactionType.BUY, "AAPL", 1, "100.1200000");
        tx(TransactionType.BUY, "AAPL", 1, "1000000000");
        tx(TransactionType.BUY, "AAPL", 1, "0.0001");
    }

    @Test
    void invalidSymbolsAreRejected() {
        for (String bad : new String[]{"", "   ", "A,B", "AA PL", "ABCDEFGHIJKLMNOP", "AAPL\"", "-AAPL", "AA$L"}) {
            assertThrows(IllegalArgumentException.class, () -> tx(TransactionType.BUY, bad, 1, "10"));
        }
        assertThrows(IllegalArgumentException.class,
                () -> new Transaction(LocalDate.of(2026, 1, 1), TransactionType.BUY, null, 1, new BigDecimal("10")));
    }

    @Test
    void realWorldSymbolsAreAcceptedAndNormalised() {
        assertEquals("AAPL", tx(TransactionType.BUY, " aapl ", 1, "100.00").getSymbol());
        assertEquals("M&M", tx(TransactionType.BUY, "m&m", 1, "100.00").getSymbol());
        assertEquals("BAJAJ-AUTO", tx(TransactionType.BUY, "BAJAJ-AUTO", 1, "100.00").getSymbol());
        assertEquals("BRK.B", tx(TransactionType.BUY, "BRK.B", 1, "100.00").getSymbol());
        assertEquals("500325", tx(TransactionType.BUY, "500325", 1, "100.00").getSymbol());
    }

    @Test
    void missingDateOrTypeIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new Transaction(null, TransactionType.BUY, "AAPL", 1, new BigDecimal("10")));
        assertThrows(IllegalArgumentException.class,
                () -> new Transaction(LocalDate.of(2026, 1, 1), null, "AAPL", 1, new BigDecimal("10")));
    }

    @Test
    void quantityOverflowIsRejectedAndChangesNothing() {
        portfolio.addTransaction(tx(TransactionType.BUY, "X", 2_000_000_000, "1.00"));
        assertThrows(IllegalArgumentException.class,
                () -> portfolio.addTransaction(tx(TransactionType.BUY, "X", 2_000_000_000, "1.00")));
        assertEquals(2_000_000_000, only().getQuantity());
        assertEquals(money("2000000000.00"), only().getTotalCost());
        assertEquals(1, portfolio.getTransactions().size());
    }

    @Test
    void nullTransactionIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> portfolio.addTransaction(null));
    }

    // -------------------------------------------------------------- prices

    @Test
    void settingAPriceForAnUnknownSymbolIsRejected() {
        portfolio.addTransaction(tx(TransactionType.BUY, "AAPL", 1, "100.00"));
        assertThrows(IllegalArgumentException.class,
                () -> portfolio.setPrice("NOPE", new BigDecimal("5.00")));
        assertTrue(portfolio.getPrices().isEmpty());
    }

    @Test
    void invalidPricesCannotBeSet() {
        portfolio.addTransaction(tx(TransactionType.BUY, "AAPL", 1, "100.00"));
        assertThrows(IllegalArgumentException.class, () -> portfolio.setPrice("AAPL", new BigDecimal("0")));
        assertThrows(IllegalArgumentException.class, () -> portfolio.setPrice("AAPL", new BigDecimal("-1")));
        assertThrows(IllegalArgumentException.class, () -> portfolio.setPrice("A,B", new BigDecimal("1")));
        assertThrows(IllegalArgumentException.class, () -> portfolio.setPrice("AAPL", null));
        assertTrue(portfolio.getPrices().isEmpty());
    }

    @Test
    void aPriceCanStillBeSetForASymbolYouFullySold() {
        portfolio.addTransaction(tx(TransactionType.BUY, "AAPL", 1, "100.00"));
        portfolio.addTransaction(tx(TransactionType.SELL, "AAPL", 1, "110.00"));
        portfolio.setPrice("aapl", new BigDecimal("120.00"));
        assertEquals(1, portfolio.getPrices().size());
    }
}
