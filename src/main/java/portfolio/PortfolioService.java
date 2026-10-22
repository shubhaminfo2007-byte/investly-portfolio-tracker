package portfolio;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Keeps one Portfolio (loaded from that user's own CSV folder) per username. */
@Service
public class PortfolioService {
    private static class UserData {
        final CsvStorage storage;
        final Portfolio portfolio;
        final List<String> warnings;
        UserData(CsvStorage storage, Portfolio portfolio, List<String> warnings) {
            this.storage = storage; this.portfolio = portfolio; this.warnings = warnings;
        }
    }

    private final Path dataRoot;
    private final Map<String, UserData> users = new HashMap<>();

    public PortfolioService(@Value("${portfolio.data-dir:./data}") String dataDir) {
        this.dataRoot = Path.of(dataDir);
    }

    /** Loads the user's CSV files the first time that user is seen, then reuses them. */
    private UserData data(String username) {
        String name = CsvStorage.normalizeUsername(username);
        UserData existing = users.get(name);
        if (existing != null) {
            return existing;
        }
        try {
            CsvStorage storage = CsvStorage.forUser(dataRoot, name);
            Portfolio portfolio = storage.load();
            UserData created = new UserData(storage, portfolio, new ArrayList<>(storage.getWarnings()));
            users.put(name, created);
            return created;
        } catch (IOException e) {
            throw new IllegalStateException("Could not read the saved data for '" + name + "': " + e.getMessage(), e);
        }
    }

    public synchronized Portfolio getPortfolio(String username) { return data(username).portfolio; }
    public synchronized List<String> getWarnings(String username) { return new ArrayList<>(data(username).warnings); }

    public synchronized void transact(String username, String type, String symbol, int quantity, BigDecimal price) throws IOException {
        UserData d = data(username);
        TransactionType transactionType = TransactionType.valueOf(type.trim().toUpperCase());
        d.portfolio.addTransaction(new Transaction(LocalDate.now(), transactionType, symbol, quantity, price));
        d.storage.save(d.portfolio);
    }

    public synchronized void updatePrice(String username, String symbol, BigDecimal price) throws IOException {
        UserData d = data(username);
        d.portfolio.setPrice(symbol, price);
        d.storage.save(d.portfolio);
    }
}
