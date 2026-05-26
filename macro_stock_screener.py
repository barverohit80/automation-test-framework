import pandas as pd
import requests
import yfinance as yf
import matplotlib.pyplot as plt
from datetime import datetime
import os
import time

# =====================================================================
# 1. CONSOLIDATED HS-CODE TO INDIAN EXPORTER DICTIONARY
# =====================================================================
TRADE_TO_STOCK_MAP = {
    "29": {
        "sector": "Organic & Specialty Chemicals",
        "tickers": ["SRF.NS", "PIDILITIND.NS", "AARTIIND.NS"],
        "us_customer": "Dow, DuPont, BASF US"
    },
    "30": {
        "sector": "Pharmaceuticals & APIs",
        "tickers": ["SUNPHARMA.NS", "DIVISLAB.NS", "DRREDDY.NS"],
        "us_customer": "Pfizer, CVS Health, Teva US"
    },
    "61_62": {
        "sector": "Textiles & Apparel",
        "tickers": ["KPRMILL.NS", "GOKEX.NS", "WELSPUNLIV.NS"],
        "us_customer": "Walmart, Gap, Nike, Target"
    },
    "8504": {
        "sector": "Electrical Machinery & EMS",
        "tickers": ["DIXON.NS", "KAYNES.NS", "CGPOWER.NS"],
        "us_customer": "Google, Cisco, GE Grid"
    },
    "8803": {
        "sector": "Defense & Aerospace",
        "tickers": ["HAL.NS", "MTARTECH.NS", "BEL.NS"],
        "us_customer": "Boeing, Lockheed Martin, GE"
    },
    "9306": {
        "sector": "Defense - Munitions",
        "tickers": ["SOLARINDS.NS", "PREMEXPLN.NS"],
        "us_customer": "US Dept of Defense (via primes)"
    },
    "8501": {
        "sector": "Clean Energy - Fuel Cells",
        "tickers": ["MTARTECH.NS", "TDPOWERSYS.NS"],
        "us_customer": "Bloom Energy (USA)"
    },
    "8708": {
        "sector": "Auto Components",
        "tickers": ["BHARATFORG.NS", "MOTHERSON.NS"],
        "us_customer": "Cummins, PACCAR, GM, Ford"
    },
    "3808": {
        "sector": "Agrochemicals",
        "tickers": ["PIIND.NS", "UPL.NS", "SHARDACROP.NS"],
        "us_customer": "FMC Corp, Corteva, Bayer US"
    },
    "8607": {
        "sector": "Railway Components",
        "tickers": ["JWL.NS", "TEXRAIL.NS"],
        "us_customer": "Wabtec, Progress Rail (Caterpillar)"
    },
    "71": {
        "sector": "Gems & Jewelry",
        "tickers": ["GOLDIAM.NS", "TITAN.NS"],
        "us_customer": "Signet (Kay/Zales), Amazon US"
    }
}

# =====================================================================
# 2. UTILITY FUNCTIONS FOR FETCHING AND PROCESSING DATA
# =====================================================================
def fetch_us_import_data(hs_code):
    REPORTER = "842"
    PARTNER = "699"
    BASE_URL = "https://comtradeapi.un.org/public/v1/preview/C/M/HS"
    periods = [f"{y}{m:02d}" for y in [2024, 2025, 2026] for m in range(1, 13)]
    period_chunks = [",".join(periods[0:12]), ",".join(periods[12:24])]
    all_dfs = []
    hs_list = hs_code.split("_")
    for hs in hs_list:
        for p_str in period_chunks:
            time.sleep(1.2)
            try:
                params = {"reporterCode": REPORTER, "partnerCode": PARTNER, "flowCode": "M", "cmdCode": hs, "period": p_str}
                response = requests.get(BASE_URL, params=params)
                if response.status_code == 200:
                    data = response.json()
                    if data.get('data'):
                        all_dfs.append(pd.DataFrame(data['data']))
            except Exception as e: print(f"Error fetching: {e}")
    if not all_dfs: return pd.DataFrame()
    combined_df = pd.concat(all_dfs).groupby('period')['primaryValue'].sum().reset_index()
    combined_df['Date'] = pd.to_datetime(combined_df['period'], format='%Y%m')
    combined_df = combined_df.rename(columns={'primaryValue': 'Import_Value_USD'})
    combined_df = combined_df.sort_values('Date')
    combined_df['Import_Value_USD_Smoothed'] = combined_df['Import_Value_USD'].rolling(window=3).mean()
    combined_df['YearMonth'] = combined_df['Date'].dt.to_period('M')
    return combined_df

def process_and_plot_signals(hs_code, target_ticker, alert_threshold=10.0):
    sector_info = TRADE_TO_STOCK_MAP[hs_code]
    report_dir = "reports"
    if not os.path.exists(report_dir): os.makedirs(report_dir)
    trade_df = fetch_us_import_data(hs_code)
    if trade_df.empty or trade_df['Import_Value_USD_Smoothed'].isnull().all():
        return {"sector": sector_info['sector'], "ticker": target_ticker, "status": "Error: Insufficient Data"}
    try:
        start_date = trade_df['Date'].min().strftime('%Y-%m-%d')
        end_date = (trade_df['Date'].max() + pd.DateOffset(months=1)).strftime('%Y-%m-%d')
        stock = yf.Ticker(target_ticker)
        stock_df = stock.history(start=start_date, end=end_date)
        if stock_df.empty: return {"sector": sector_info['sector'], "ticker": target_ticker, "status": "Error: No Stock Data"}
        stock_monthly = stock_df['Close'].resample('ME').last().reset_index()
        stock_monthly['YearMonth'] = stock_monthly['Date'].dt.tz_localize(None).dt.to_period('M')
        merged = pd.merge(trade_df, stock_monthly, on='YearMonth', suffixes=('_trade', '_stock'))
        
        if merged.shape[0] < 10: return {"sector": sector_info['sector'], "ticker": target_ticker, "status": "Error: Insufficient History"}

        # Calculate 3-Quarter Trend
        def get_q_growth(idx_end, idx_start):
            v_end = merged['Import_Value_USD_Smoothed'].iloc[idx_end]
            v_start = merged['Import_Value_USD_Smoothed'].iloc[idx_start]
            return ((v_end - v_start) / v_start) * 100

        q1_growth = get_q_growth(-1, -4)
        q2_growth = get_q_growth(-4, -7)
        q3_growth = get_q_growth(-7, -10)
        
        fig, ax1 = plt.subplots(figsize=(12, 6))
        ax1.plot(merged['Date_trade'], merged['Import_Value_USD_Smoothed'], color='navy', linewidth=3)
        ax2 = ax1.twinx()
        ax2.plot(merged['Date_trade'], merged['Close'], color='forestgreen', linewidth=2, linestyle='--')
        plt.title(f"{sector_info['sector']} ({target_ticker}) | Trend: {q3_growth:+.0f}% -> {q2_growth:+.0f}% -> {q1_growth:+.0f}%")
        filename = f"{report_dir}/{sector_info['sector'].replace(' ', '_')}_{target_ticker}.png"
        plt.savefig(filename); plt.close()
        
        return {
            "sector": sector_info['sector'], "ticker": target_ticker, 
            "growth": q1_growth, "q2_growth": q2_growth, "q3_growth": q3_growth,
            "signal": q1_growth >= alert_threshold, "chart": filename, "status": "Success"
        }
    except Exception as e: return {"sector": sector_info['sector'], "ticker": target_ticker, "status": f"Error: {e}"}

def create_visual_report_table(results):
    data = []
    for r in results:
        if r['status'] == "Success":
            verdict = "STRONG BUY" if r['signal'] else "WATCHLIST"
            us_cust = TRADE_TO_STOCK_MAP.get(next(k for k, v in TRADE_TO_STOCK_MAP.items() if v['sector'] == r['sector']), {}).get('us_customer', 'N/A')
            data.append([r['sector'], r['ticker'], f"{r['q3_growth']:+.1f}%", f"{r['q2_growth']:+.1f}%", f"{r['growth']:+.1f}%", us_cust, verdict])

    if not data: return None
    df = pd.DataFrame(data, columns=['Sector', 'Ticker', 'Prev Q2', 'Prev Q1', 'Current Q', 'US Customer', 'Verdict'])
    fig, ax = plt.subplots(figsize=(16, len(data)*0.6 + 2))
    ax.axis('off')
    table = ax.table(cellText=df.values, colLabels=df.columns, cellLoc='center', loc='center', colColours=["#f2f2f2"]*7)
    table.auto_set_font_size(False); table.set_fontsize(9); table.scale(1.1, 2.2)
    for i in range(len(data)):
        for col_idx in [2, 3, 4]:
            val = float(df.iloc[i, col_idx].replace('%',''))
            table[(i+1, col_idx)].set_facecolor("#c6efce" if val > 0 else "#ffc7ce")
        if "STRONG BUY" in df.iloc[i, 6]: table[(i+1, 6)].set_facecolor("#c6efce")
    plt.title(f"Macro Strategic Report: 3-Quarter Trend Analysis (India -> USA)\nGenerated: {datetime.now().strftime('%Y-%m-%d')}", fontsize=14, pad=20)
    img = "macro_visual_report.png"; plt.savefig(img, bbox_inches='tight', dpi=150); plt.close()
    return img

# TELEGRAM CONFIGURATION
TELEGRAM_TOKEN = "8697229689:AAG5KT06bio6I2adEQ--rKicOcsXtPFxWYc"
TELEGRAM_CHAT_ID = "-1003824534754"

def send_macro_telegram_report(results, log_file):
    report_img = create_visual_report_table(results)
    if report_img:
        url = f"https://api.telegram.org/bot{TELEGRAM_TOKEN}/sendPhoto"
        with open(report_img, 'rb') as photo:
            requests.post(url, data={"chat_id": TELEGRAM_CHAT_ID, "caption": "📊 <b>Monthly Strategic Macro Table</b>", "parse_mode": "HTML"}, files={"photo": photo})
    timestamp = datetime.now().strftime("%Y-%m-%d")
    report_msg = f"📜 <b>Historical Context ({timestamp})</b>\n"
    if os.path.exists(log_file):
        hist_df = pd.read_csv(log_file).tail(30)
        for _, h_row in hist_df.iterrows():
            report_msg += f"• {h_row['Ticker']}: {h_row['Growth_QoQ']:+.1f}% ({h_row['Verdict']})\n"
    requests.post(f"https://api.telegram.org/bot{TELEGRAM_TOKEN}/sendMessage", data={"chat_id": TELEGRAM_CHAT_ID, "text": report_msg, "parse_mode": "HTML"})

if __name__ == "__main__":
    requests.post(f"https://api.telegram.org/bot{TELEGRAM_TOKEN}/sendMessage", data={"chat_id": TELEGRAM_CHAT_ID, "text": "🔍 <b>Monthly Macro Scan Started...</b>", "parse_mode": "HTML"})
    results = []
    for hs_code, info in TRADE_TO_STOCK_MAP.items():
        res = process_and_plot_signals(hs_code=hs_code, target_ticker=info['tickers'][0])
        results.append(res)
    log_file = "historical_verdicts.csv"
    timestamp = datetime.now().strftime("%Y-%m-%d %H:%M")
    log_data = []
    for r in results:
        if r['status'] == "Success":
            verdict = "🚀 STRONG BUY" if r['signal'] else "🕒 WATCHLIST"
            log_data.append({"Date": timestamp, "Sector": r['sector'], "Ticker": r['ticker'], "Growth_QoQ": r['growth'], "Verdict": verdict})
    if log_data:
        log_df = pd.DataFrame(log_data)
        if not os.path.exists(log_file): log_df.to_csv(log_file, index=False)
        else: log_df.to_csv(log_file, mode='a', header=False, index=False)
        send_macro_telegram_report(results, log_file)
