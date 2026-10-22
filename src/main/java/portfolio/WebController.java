package portfolio;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Controller
public class WebController {
    /** Session attribute that holds the logged-in username. */
    private static final String USER = "username";

    private final PortfolioService service;
    private final String appVersion;

    public WebController(PortfolioService service, @Value("${app.version:1.0.0}") String appVersion) {
        this.service = service;
        this.appVersion = appVersion;
    }

    /** The logged-in username, or null if nobody is logged in. */
    private String currentUser(HttpSession session) {
        return (String) session.getAttribute(USER);
    }

    // ------------------------------------------------------------ login / logout

    @GetMapping("/login")
    public String loginPage(HttpSession session) {
        return currentUser(session) != null ? "redirect:/" : "login";
    }

    @PostMapping("/login")
    public String login(@RequestParam String username, HttpServletRequest request, RedirectAttributes redirect) {
        try {
            String name = CsvStorage.normalizeUsername(username);
            request.getSession(true);
            request.changeSessionId();                       // fresh session id on login
            request.getSession().setAttribute(USER, name);
            return "redirect:/";
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
            return "redirect:/login";
        }
    }

    @PostMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/login";
    }

    // ------------------------------------------------------------------ dashboard

    @GetMapping("/")
    public String dashboard(Model model, HttpSession session) {
        String user = currentUser(session);
        if (user == null) return "redirect:/login";
        Portfolio p = service.getPortfolio(user);
        model.addAttribute("username", user);
        model.addAttribute("appVersion", appVersion);
        model.addAttribute("portfolio", p);
        model.addAttribute("holdings", p.getHoldings());
        List<Transaction> transactions = new ArrayList<>(p.getTransactions());
        transactions.sort(Comparator.comparing(Transaction::getDate).reversed());
        model.addAttribute("transactions", transactions);
        model.addAttribute("warnings", service.getWarnings(user));
        model.addAttribute("totalCost", p.totalCost());
        model.addAttribute("totalValue", p.totalValue());
        model.addAttribute("unrealized", p.totalUnrealizedGain());
        model.addAttribute("realized", p.totalRealizedGain());
        model.addAttribute("totalProfit", p.totalProfit());
        model.addAttribute("totalReturn", p.totalReturnPercent());
        return "dashboard";
    }

    @PostMapping("/transactions")
    public String transact(@RequestParam String type, @RequestParam String symbol,
                           @RequestParam int quantity, @RequestParam BigDecimal price,
                           HttpSession session, RedirectAttributes redirect) {
        String user = currentUser(session);
        if (user == null) return "redirect:/login";
        try {
            service.transact(user, type, symbol, quantity, price);
            redirect.addFlashAttribute("success", type.toUpperCase() + " transaction saved successfully.");
        } catch (Exception e) {
            redirect.addFlashAttribute("error", friendly(e));
        }
        return "redirect:/";
    }

    @PostMapping("/prices")
    public String updatePrice(@RequestParam String symbol, @RequestParam BigDecimal price,
                              HttpSession session, RedirectAttributes redirect) {
        String user = currentUser(session);
        if (user == null) return "redirect:/login";
        try {
            service.updatePrice(user, symbol, price);
            redirect.addFlashAttribute("success", "Manual price updated successfully.");
        } catch (Exception e) {
            redirect.addFlashAttribute("error", friendly(e));
        }
        return "redirect:/";
    }

    private String friendly(Exception e) {
        String message = e.getMessage();
        return (message == null || message.isBlank()) ? "The request could not be completed." : message;
    }
}
