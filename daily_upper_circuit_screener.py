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
    
    # Heartbeat message to confirm bot is working
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

        # Filter for Upper Circuit
        upper_circuits_df = hitters_df[(hitters_df['pChange'] > 0) & (hitters_df['pChange'] >= (hitters_df['priceBand'] - 0.1))]

        if upper_circuits_df.empty:
            print("No stocks hit the upper circuit today.")
            results = []
        else:
            print(f"Found {len(upper_circuits_df)} stocks at upper circuit. Filtering by Market Cap...")
            
            results = []
            for index, row in upper_circuits_df.iterrows():
                symbol = row['symbol']
                nse_symbol = f"{symbol}.NS"
                
                mcap = get_market_cap_in_cr(nse_symbol)
                
                if mcap >= 500:
                    print(f"-> Qualified: {symbol} (M-Cap: ₹{mcap:,.0f} Cr)")
                    
                    reason = get_latest_news_headline(symbol)
                    
                    # SEND TELEGRAM ALERT
                    send_telegram_alert(symbol, mcap, row['ltp'], reason)
                    
                    results.append({
                        "Symbol": symbol,
                        "LTP": row['ltp'],
                        "Change%": f"{row['pChange']}%",
                        "Market Cap (Cr)": f"₹{mcap:,.0f}",
                        "Catalyst/Reason": reason
                    })
                    time.sleep(1)

        # 4. Final Report
        if results:
            df_report = pd.DataFrame(results)
            print("\n" + "="*80)
            header_text = "TODAY'S UPPER CIRCUIT LEADERS (> ₹500 Cr)"
            print(f"{header_text:^80}")
            print("="*80)
            print(df_report.to_string(index=False))
            print("="*80)
            
            # Save to log
            log_file = "daily_circuit_log.csv"
            log_timestamp = datetime.now().strftime("%Y-%m-%d")
            df_report['Date'] = log_timestamp
            
            if not os.path.exists(log_file):
                df_report.to_csv(log_file, index=False)
            else:
                df_report.to_csv(log_file, mode='a', header=False, index=False)
            print(f"\nReport saved to: {os.path.abspath(log_file)}")
        else:
            print("\nNo stocks above ₹500 Cr hit the upper circuit today.")

        # Final completion message
        end_time = datetime.now().strftime("%Y-%m-%d %H:%M")
        finish_msg = f"✅ <b>Screener Finished</b>\nEnd time: {end_time} IST\nStocks Identified: {len(results)}"
        requests.post(url, data={"chat_id": TELEGRAM_CHAT_ID, "text": finish_msg, "parse_mode": "HTML"})

    except Exception as e:
        print(f"An error occurred: {e}")
        error_msg = f"❌ <b>Screener Error</b>\n{str(e)}"
        requests.post(url, data={"chat_id": TELEGRAM_CHAT_ID, "text": error_msg, "parse_mode": "HTML"})

if __name__ == "__main__":
    run_screener()
