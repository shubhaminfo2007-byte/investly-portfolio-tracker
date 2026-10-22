package portfolio;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Scanner;

/** Console menu for the portfolio tracker. */
public class Main {
    private final Scanner in;
    private final CsvStorage storage;
    private Portfolio portfolio;

    public Main(Scanner in, CsvStorage storage) throws IOException {
        this.in = in;
        this.storage = storage;
        this.portfolio = storage.load();
    }

    /** Optional argument: the data folder (default "data" in the folder you start from). */
    public static void main(String[] args) {
        Path dataFolder = Path.of(args.length > 0 ? args[0] : "data");
        try {
            new Main(new Scanner(System.in), new CsvStorage(dataFolder)).run();
        } catch (IOException e) {
            System.out.println("Cannot read your saved data in " + dataFolder.toAbsolutePath() + ": " + e.getMessage());
            System.out.println("Nothing was changed. Fix the problem and start again.");
            System.exit(1);
        }
    }

    public void run() {
        System.out.println("=== Portfolio Tracker ===");
        System.out.println("Data folder: " + storage.getFolder().toAbsolutePath());
        for (String warning : storage.getWarnings()) {
            System.out.println("WARNING: " + warning);
        }
        boolean running = true;
        while (running) {
            printMenu();
            try {
                String choice = in.hasNextLine() ? in.nextLine().trim() : "0";
                switch (choice) {
                    case "1" -> addTransaction(TransactionType.BUY);
                    case "2" -> addTransaction(TransactionType.SELL);
                    case "3" -> setPrice();
                    case "4" -> showHoldings();
                    case "5" -> showTransactions();
                    case "6" -> save();
                    case "7" -> loadDemoData();
                    case "8" -> showRealized();
                    case "0" -> { save(); running = false; }
                    default -> System.out.println("Please choose a number from the menu.");
                }
            } catch (IllegalArgumentException | IOException e) {
                System.out.println("Error: " + e.getMessage());
            } catch (NoSuchElementException e) {
                // Input ended in the middle of a question (for example Ctrl+D).
                System.out.println("Input ended. Saving and exiting.");
                try {
                    save();
                } catch (IOException ioe) {
                    System.out.println("Error: " + ioe.getMessage());
                }
                running = false;
            }
        }
        System.out.println("Goodbye!");
    }

    private void printMenu() {
        System.out.println();
        System.out.println("1) Add BUY    2) Add SELL    3) Set current price");
        System.out.println("4) View holdings    5) View transactions    8) View realized profit/loss");
        System.out.println("6) Save    7) Load demo data    0) Save and exit");
        System.out.print("> ");
    }

    private void addTransaction(TransactionType type) {
        String symbol = ask("Symbol: ");
        int quantity = readWholeNumber("Quantity: ");
        BigDecimal price = readNumber("Price per share: ");
        BigDecimal realizedBefore = portfolio.totalRealizedGain();

        portfolio.addTransaction(new Transaction(LocalDate.now(), type, symbol, quantity, price));

        System.out.println(type + " recorded.");
        if (type == TransactionType.SELL) {
            BigDecimal thisSale = portfolio.totalRealizedGain().subtract(realizedBefore);
            System.out.printf(Locale.US, "Realized profit/loss on this sale: %.2f%n", thisSale);
        }
        autoSave();
    }

    private void setPrice() {
        String symbol = ask("Symbol: ");
        BigDecimal price = readNumber("Current price: ");
        portfolio.setPrice(symbol, price);
        System.out.println("Price updated.");
        autoSave();
    }

    private void showHoldings() {
        if (portfolio.getHoldings().isEmpty()) {
            if (portfolio.getTransactions().isEmpty()) {
                System.out.println("No holdings yet. Add a BUY first.");
                return;
            }
            System.out.println("No current holdings (everything has been sold).");
            printProfitSummary();
            return;
        }
        System.out.printf(Locale.US, "%-8s %5s %10s %10s %12s %12s %8s %7s%n",
                "SYMBOL", "QTY", "AVG COST", "PRICE", "VALUE", "UNREAL P/L", "RETURN %", "ALLOC %");
        boolean anyPriceMissing = false;
        for (Holding h : portfolio.getHoldings()) {
            String price = String.format(Locale.US, "%.2f", portfolio.currentPrice(h));
            if (!portfolio.hasPrice(h)) {
                price += "*";
                anyPriceMissing = true;
            }
            System.out.printf(Locale.US, "%-8s %5d %10.2f %10s %12.2f %12.2f %7.2f%% %6.2f%%%n",
                    h.getSymbol(), h.getQuantity(), h.averageCost(), price,
                    portfolio.marketValue(h), portfolio.unrealizedGain(h),
                    portfolio.unrealizedReturnPercent(h), portfolio.allocationPercent(h));
        }
        System.out.println("-".repeat(84));
        printProfitSummary();
        if (anyPriceMissing) {
            System.out.println("* No price set: the average cost is used, so unrealized profit shows as 0. Use option 3.");
        }
    }

    private void printProfitSummary() {
        System.out.printf(Locale.US, "Cost of current holdings : %12.2f%n", portfolio.totalCost());
        System.out.printf(Locale.US, "Market value             : %12.2f%n", portfolio.totalValue());
        System.out.printf(Locale.US, "Unrealized profit/loss   : %12.2f  (%.2f%%)%n",
                portfolio.totalUnrealizedGain(), portfolio.totalUnrealizedReturnPercent());
        System.out.printf(Locale.US, "Realized profit/loss     : %12.2f%n", portfolio.totalRealizedGain());
        System.out.printf(Locale.US, "Total profit/loss        : %12.2f  (%.2f%% of everything bought)%n",
                portfolio.totalProfit(), portfolio.totalReturnPercent());
    }

    private void showRealized() {
        if (portfolio.getRealizedSymbols().isEmpty()) {
            System.out.println("No sales recorded yet, so nothing is realized.");
            return;
        }
        System.out.printf(Locale.US, "%-8s %14s%n", "SYMBOL", "REALIZED P/L");
        for (String symbol : portfolio.getRealizedSymbols()) {
            System.out.printf(Locale.US, "%-8s %14.2f%n", symbol, portfolio.realizedGainFor(symbol));
        }
        System.out.println("-".repeat(23));
        System.out.printf(Locale.US, "Total realized: %.2f  (%.2f%% of the %.2f cost of shares sold)%n",
                portfolio.totalRealizedGain(), portfolio.realizedReturnPercent(), portfolio.costOfSharesSold());
    }

    private void showTransactions() {
        if (portfolio.getTransactions().isEmpty()) {
            System.out.println("No transactions yet.");
            return;
        }
        portfolio.getTransactions().forEach(System.out::println);
    }

    private void save() throws IOException {
        storage.save(portfolio);
        System.out.println("Saved.");
    }

    /** Saves after every change, so closing the window never loses work. */
    private void autoSave() {
        try {
            storage.save(portfolio);
            System.out.println("(saved)");
        } catch (IOException e) {
            System.out.println("WARNING: recorded, but could not save to disk: " + e.getMessage()
                    + " (use option 6 to try again)");
        }
    }

    /** Handy for a quick demo. Asks first if it would replace real data. */
    private void loadDemoData() {
        int existing = portfolio.getTransactions().size();
        if (existing > 0) {
            String answer = ask("This replaces your current portfolio (" + existing
                    + " transactions) with demo data. Type YES to continue: ");
            if (!answer.equalsIgnoreCase("YES")) {
                System.out.println("Cancelled. Your data was not changed.");
                return;
            }
        }
        Portfolio demo = new Portfolio();
        demo.addTransaction(new Transaction(LocalDate.of(2026, 1, 10), TransactionType.BUY, "AAPL", 10, new BigDecimal("180.00")));
        demo.addTransaction(new Transaction(LocalDate.of(2026, 2, 5), TransactionType.BUY, "AAPL", 5, new BigDecimal("190.00")));
        demo.addTransaction(new Transaction(LocalDate.of(2026, 2, 20), TransactionType.BUY, "MSFT", 8, new BigDecimal("400.00")));
        demo.addTransaction(new Transaction(LocalDate.of(2026, 3, 15), TransactionType.BUY, "INFY", 40, new BigDecimal("18.50")));
        demo.addTransaction(new Transaction(LocalDate.of(2026, 4, 2), TransactionType.SELL, "AAPL", 5, new BigDecimal("200.00")));
        demo.setPrice("AAPL", new BigDecimal("210.00"));
        demo.setPrice("MSFT", new BigDecimal("425.50"));
        demo.setPrice("INFY", new BigDecimal("17.80"));
        portfolio = demo;
        System.out.println("Demo data loaded. Choose 4 to view it. It is saved when you save (6) or exit (0).");
    }

    private String ask(String prompt) {
        System.out.print(prompt);
        return in.nextLine().trim();
    }

    private int readWholeNumber(String prompt) {
        String text = ask(prompt);
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Quantity must be a whole number (you typed '" + text + "')");
        }
    }

    private BigDecimal readNumber(String prompt) {
        String text = ask(prompt);
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("'" + text + "' is not a valid number");
        }
    }
}
