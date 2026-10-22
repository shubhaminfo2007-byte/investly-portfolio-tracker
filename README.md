# Investment Portfolio Tracker

A responsive Java web application for recording stock buys and sells, tracking holdings, viewing transaction history, and calculating realized and unrealized profit/loss.

## Technology
- Java 17
- Spring Boot, Spring MVC
- Thymeleaf, HTML, CSS, vanilla JavaScript
- CSV persistence

## Prerequisites
- Java 17 or newer (`java -version`)
- Maven 3.9+ (`mvn -version`). On macOS with Homebrew: `brew install maven`
- Git (only needed for GitHub / Render deployment)
- Docker (optional: only to test the container locally; Render builds it for you)

## Run locally
From the folder that contains `pom.xml`:

```bash
mvn test
mvn spring-boot:run
```

Open http://localhost:8080. The port is `8080` unless the `PORT` environment variable is set.
Open it, type a username on the login page, and start. Data is saved in `./data/users/<username>/` (relative to the folder you start the app from). Start it from the same
folder each time so it finds your earlier data. To use another folder, set `PORTFOLIO_DATA_DIR`:

```bash
PORTFOLIO_DATA_DIR=/path/to/folder mvn spring-boot:run     # macOS / Linux
```

Build a runnable jar: `mvn package`, then `java -jar target/portfolio-tracker-1.0.0.jar`.

Optional local Docker test:

```bash
docker build -t investly .
docker run --rm -p 8080:8080 -v investly-data:/app/data investly
```

## Features
- Dashboard summary for current cost basis, portfolio value, total profit/loss and realized profit/loss.
- Holdings table with quantity, average cost, entered current price, market value and unrealized profit/loss.
- Buy and sell transactions using the existing weighted-average-cost portfolio logic.
- Validation for invalid symbols, prices, quantities, and selling more shares than held.
- Manual price updates; no live stock-market feed is used.
- Simple multi-user support: log in with a username (no password); every username has its own portfolio, stored in `./data/users/<username>/transactions.csv` and `prices.csv`.
- Recent transaction history and success/error messages.
- Profile menu (top-right "P") showing your username, About Investly, Application details and Logout.

## Data and assumptions
The application saves data automatically. Start the app from this project folder each time so it uses the same `./data` directory. If no current price has been entered for a holding, the app values it at cost and unrealized profit/loss is zero. Prices are manually entered and are not live market quotes. The model uses whole shares, one currency, weighted average cost, and excludes fees, taxes, dividends and stock splits.

## Configuration (environment variables)

| Variable | Default | Purpose |
|---|---|---|
| `PORT` | `8080` | HTTP port. Render sets this automatically. |
| `PORTFOLIO_DATA_DIR` | `./data` | Root data folder. Each user gets `users/<username>/transactions.csv` and `prices.csv` inside it. |
| `THYMELEAF_CACHE` | `false` (Docker image: `true`) | Template caching. |

## Put the project on GitHub

Run these inside the folder that contains `pom.xml` and `Dockerfile` (that folder must be the root of the repository):

```bash
git init
git add .
git status          # check: no target/ folder and no data/ or .csv files are listed
git commit -m "Investly portfolio tracker"
git branch -M main
```

Create an empty repository on github.com (no README, no .gitignore), then:

```bash
git remote add origin https://github.com/YOUR-USERNAME/YOUR-REPO.git
git push -u origin main
```

`.gitignore` keeps `target/`, IDE files and your local `data/` (CSV files and backups) out of GitHub.
If you committed `data/` earlier, run `git rm -r --cached data` and commit again (your local files are kept).

## Deploy to Render (Docker)

1. Sign in at render.com and connect your GitHub account.
2. **New +** → **Web Service** → choose your repository.
3. **Language: Docker** (Render finds the `Dockerfile`). Leave the Dockerfile path as the default. If the app lives in a sub-folder of the repo, set **Root Directory** to that folder.
4. Choose an **Instance Type**:
   - **Free**: fine for a demo, but your data is NOT permanent (see below).
   - **Starter or any paid type**: required if you want data to survive restarts and redeploys.
5. For permanent data (paid only): open **Advanced** → **Add Disk**:
   - **Mount Path:** `/var/data`
   - **Size:** 1 GB is plenty.
   - Then **Add Environment Variable**: `PORTFOLIO_DATA_DIR` = `/var/data` (it must be exactly the mount path).
6. Click **Create Web Service**. After the build, open the `https://<your-service>.onrender.com` URL. The dashboard is the root page `/`.
7. Check the persistence: add a transaction, then use **Manual Deploy** → **Restart service**. The transaction should still be there. If it is gone, the disk or `PORTFOLIO_DATA_DIR` is not set up correctly.

The app listens on the `PORT` Render provides (Render's default is 10000) and on all network interfaces; nothing needs to be configured for that.

## Storage limitations (please read)

- Data is stored in plain CSV files. A cloud server's normal filesystem is temporary: **without a persistent disk, every restart, redeploy, or free-instance spin-down deletes your data.** Render persistent disks are available only on paid instances.
- A service with a disk runs as a single instance (no scaling) and redeploys briefly interrupt the service. CSV storage is designed for one running copy of the app.
- Render does not back up the disk for you automatically; use Render's disk snapshots or copy the CSV files if the data matters.
- **The login is username-only, with no password.** It separates portfolios but is NOT secure: anyone who types your username opens your portfolio. Do not enter personal or sensitive financial information on a public deployment. Sessions are kept in memory, so everyone is logged out when the app restarts.
- If the data folder is not writable, a transaction may show an error because the CSV could not be saved, and the dashboard can still show it until the app restarts. Fix the storage path first.

## Users and login

- Usernames are 1-32 characters: letters, numbers, `-` and `_` (stored in lower case, so `Alice` and `alice` are the same user). This also keeps usernames safe to use as folder names.
- A new username starts an empty portfolio. Logout (top bar or profile menu) ends the session.
- Data saved before multi-user support lived directly in `data/transactions.csv` and `data/prices.csv`. To keep it, create `data/users/<your-username>/` and move those two files into it.

## Project structure
- `portfolio.Portfolio`, `Holding`, `Transaction`, `TransactionType`: core portfolio rules and calculations.
- `portfolio.CsvStorage`: CSV loading and safe saving.
- `portfolio.WebApplication`, `PortfolioService`, `WebController`: web application and routes.
- `src/main/resources/templates/dashboard.html`: Thymeleaf dashboard.
- `src/main/resources/static/css/style.css`: responsive styling.
- `src/main/resources/static/js/app.js`: small client-side validation, alerts and the profile menu.
- `Dockerfile`, `.dockerignore`: container build for Render.
- `src/main/resources/application.properties`: port and data-folder settings (environment-driven).

## Important
This project was extended with AI assistance. Review and understand the code and follow your college's disclosure rules before submitting.
