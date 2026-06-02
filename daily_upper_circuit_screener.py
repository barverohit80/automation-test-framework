import yfinance as yf
import pandas as pd
import requests
from nsepython import nse_price_band_hitters
import feedparser
import os
from datetime import datetime
import time

def get_market_cap_in_cr(symbol):
    """Fetch market cap using yfinance and convert to Crores."""
    try:
        ticker = yf.Ticker(symbol)
        info = ticker.info
        market_cap = info.get('marketCap')
        if market_cap:
            return market_cap / 10000000  # Convert to Crores (10^7)
        return 0
    except Exception:
        return 0

def get_latest_news_headline(company_name):
    """Scrapes Google News RSS for the latest headline."""
    search_query = f"{company_name} stock news India"
    rss_url = f"https://news.google.com/rss/search?q={search_query.replace(' ', '+')}&hl=en-IN&gl=IN&ceid=IN:en"
    
    try:
        feed = feedparser.parse(rss_url)
        if feed.entries:
            # Return the title of the most recent entry
            return feed.entries[0].title
        return "No recent news found."
    except Exception as e:
        return f"Error fetching news: {e}"

# TELEGRAM CONFIGURATION
TELEGRAM_TOKEN = "8697229689:AAG5KT06bio6I2adEQ--rKicOcsXtPFxWYc"
TELEGRAM_CHAT_ID = "-1003824534754"

def send_telegram_alert(symbol, mcap, price, reason):
    """Sends a formatted alert to the Telegram bot."""
    message = (
        f"🚀 <b>Upper Circuit Alert</b>\n\n"
        f"<b>Stock:</b> {symbol}\n"
        f"<b>Price:</b> ₹{price}\n"
        f"<b>M-Cap:</b> ₹{mcap:,.0f} Cr\n\n"
        f"<b>Catalyst:</b>\n{reason}"
    )
    url = f"https://api.telegram.org/bot{TELEGRAM_TOKEN}/sendMessage"
    payload = {
        "chat_id": TELEGRAM_CHAT_ID,
        "text": message,
        "parse_mode": "HTML"
    }
    try:
        requests.post(url, data=payload)
    except Exception as e:
        print(f"Failed to send Telegram alert: {e}")

def run_screener():
    print("===============================================================")
    print("      DAILY NSE UPPER CIRCUIT SCREENER (> 500 CR) ")
    print("===============================================================")
    print("Fetching data from NSE... (this may take a minute)")
    
    # Heartbeat message
    timestamp = datetime.now().strftime("%Y-%m-%d %H:%M")
    url = f"https://api.telegram.org/bot{TELEGRAM_TOKEN}/sendMessage"
    requests.post(url, data={"chat_id": TELEGRAM_CHAT_ID, "text": f"🔍 <b>Screener Active</b>\nRun started at {timestamp} IST", "parse_mode": "HTML"})
    
    try:
        # 1. Fetch Price Band Hitters from NSE
        hitters_df = nse_price_band_hitters()
        
        if hitters_df.empty:
            print("No stocks hit the price bands today.")
            return
            
        # Ensure numeric types for filtering
        hitters_df['pChange'] = pd.to_numeric(hitters_df['pChange'], errors='coerce')
        hitters_df['priceBand'] = pd.to_numeric(hitters_df['priceBand'], errors='coerce')
        hitters_df['ltp'] = pd.to_numeric(hitters_df['ltp'], errors='coerce')
        
        # Determine detection method based on available columns
        if 'upper' in hitters_df.columns:
            hitters_df['upper'] = pd.to_numeric(hitters_df['upper'], errors='coerce')
            upper_circuits_df = hitters_df[
                (hitters_df['pChange'] > 0) & 
                ((hitters_df['ltp'] >= hitters_df['upper'] - 0.05) | 
                 (hitters_df['pChange'] >= (hitters_df['priceBand'] - 0.1)))
            ]
        else:
            # Fallback: Just use pChange vs priceBand
            upper_circuits_df = hitters_df[
                (hitters_df['pChange'] > 0) & 
                (hitters_df['pChange'] >= (hitters_df['priceBand'] - 0.1))
            ]

        results = []
        if upper_circuits_df.empty:
            print("No stocks hit the upper circuit according to NSE data.")
        else:
            print(f"Found {len(upper_circuits_df)} total stocks at upper circuit.")
            print("Filtering by Market Cap (> ₹500 Cr)...")
            
            for index, row in upper_circuits_df.iterrows():
                symbol = row['symbol']
                nse_symbol = f"{symbol}.NS"
                mcap = get_market_cap_in_cr(nse_symbol)
                
                if mcap >= 500:
                    print(f"✅ QUALIFIED: {symbol} (M-Cap: ₹{mcap:,.0f} Cr)")
                    reason = get_latest_news_headline(symbol)
                    send_telegram_alert(symbol, mcap, row['ltp'], reason)
                    results.append({
                        "Symbol": symbol, "LTP": row['ltp'], "Change%": f"{row['pChange']}%",
                        "Market Cap (Cr)": f"₹{mcap:,.0f}", "Catalyst/Reason": reason, "Date": datetime.now().strftime("%Y-%m-%d")
                    })
                else:
                    print(f"❌ REJECTED: {symbol} (M-Cap: ₹{mcap:,.1f} Cr is too small)")
                time.sleep(1)

        # 4. Final Report
        log_file = "daily_circuit_log.csv"
        if results:
            df_report = pd.DataFrame(results)
            if not os.path.exists(log_file): df_report.to_csv(log_file, index=False)
            else: df_report.to_csv(log_file, mode='a', header=False, index=False)

        # Final completion message with history
        history_msg = ""
        if os.path.exists(log_file):
            history_df = pd.read_csv(log_file).tail(30)
            history_msg = "\n\n<b>📜 Last 30 Circuit Leaders:</b>\n"
            for _, h_row in history_df.iterrows():
                history_msg += f"• {h_row['Symbol']} ({h_row['Date']})\n"

        finish_msg = f"✅ <b>Screener Finished</b>\nEnd time: {datetime.now().strftime('%Y-%m-%d %H:%M')} IST\nStocks Identified: {len(results)}{history_msg}"
        requests.post(url, data={"chat_id": TELEGRAM_CHAT_ID, "text": finish_msg, "parse_mode": "HTML"})

    except Exception as e:
        print(f"An error occurred: {e}")
        requests.post(url, data={"chat_id": TELEGRAM_CHAT_ID, "text": f"❌ <b>Screener Error</b>\n{str(e)}", "parse_mode": "HTML"})

if __name__ == "__main__":
    run_screener()
