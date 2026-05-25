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
        "description": "Organic chemicals, fluorochemicals, and intermediates",
        "tickers": ["SRF.NS", "PIDILITIND.NS", "AARTIIND.NS", "DEEPAKNTR.NS", "ATUL.NS", "VINATIORGA.NS", "NAVINFLUOR.NS", "CLEAN.NS"]
    },
    "30": {
        "sector": "Pharmaceuticals & APIs",
        "description": "Formulations, generic drugs, and Active Pharmaceutical Ingredients",
        "tickers": ["SUNPHARMA.NS", "DIVISLAB.NS", "DRREDDY.NS", "CIPLA.NS", "AUROPHARMA.NS", "SYNGENE.NS", "LAURUSLABS.NS", "GLAND.NS"]
    },
    "61_62": {
        "sector": "Textiles & Apparel",
        "description": "Knitted and non-knitted apparel, garments, and retail sourcing",
        "tickers": ["KPRMILL.NS", "GOKEX.NS", "VTL.NS", "WELSPUNLIV.NS", "TRIDENT.NS", "ARVIND.NS", "PAGEIND.NS", "SPAL.NS"]
    },
    "8504": {
        "sector": "Electrical Machinery & EMS",
        "description": "Transformers, static converters, and Electronic Manufacturing Services",
        "tickers": ["DIXON.NS", "KAYNES.NS", "SYRMA.NS", "CGPOWER.NS", "CYIENTDLM.NS", "DCXINDIA.NS", "CENTUM.NS"]
    },
    "850440": {
        "sector": "Data Center - Power Backups & UPS",
        "description": "Static converters, Uninterruptible Power Supplies (UPS), and inverters",
        "tickers": ["ABB.NS", "SIEMENS.NS", "POWERINDIA.NS", "CUMMINSIND.NS", "HONAUT.NS"]
    },
    "841582": {
        "sector": "Data Center - Precision Cooling",
        "description": "Industrial air conditioning and cooling equipment",
        "tickers": ["VOLTAS.NS", "BLUESTARCO.NS", "AMBER.NS", "THERMAX.NS"]
    },
    "847150": {
        "sector": "Data Center - AI & Server Infrastructure",
        "description": "Digital processing units, server assemblies, and hardware",
        "tickers": ["NETWEB.NS", "E2ENET.NS", "AVALON.NS", "OLECTRA.NS"]
    },
    "854142": {
        "sector": "Renewable Energy - Solar Cells & Modules",
        "description": "Solar cells, photovoltaic modules, and assembled panels",
        "tickers": ["WAAREEENER.NS", "PREMIERENE.NS", "BORORENEW.NS", "SWSOLAR.NS", "WEBSOL.NS"]
    },
    "850231": {
        "sector": "Renewable Energy - Wind Turbines",
        "description": "Wind-powered generating sets and turbine components",
        "tickers": ["SUZLON.NS", "INOXWIND.NS"]
    },
    "8803": {
        "sector": "Defense & Aerospace - Components",
        "description": "Parts of aircraft, spacecraft, and satellite assemblies",
        "tickers": ["HAL.NS", "MTARTECH.NS", "BEL.NS", "DATAPATTNS.NS", "ASTRAMICRO.NS"]
    },
    "9306": {
        "sector": "Defense - Munitions & Explosives",
        "description": "Bombs, grenades, missiles, and ammunition components",
        "tickers": ["SOLARINDS.NS", "PREMEXPLN.NS"]
    },
    "8501": {
        "sector": "Clean Energy - Fuel Cells & Motors",
        "description": "Electric motors, generators, and fuel cell power modules",
        "tickers": ["MTARTECH.NS", "TDPOWERSYS.NS", "KIRLOSENG.NS"]
    },
    "8708": {
        "sector": "Auto Components - Exports",
        "description": "Parts and accessories of motor vehicles",
        "tickers": ["BHARATFORG.NS", "MOTHERSON.NS", "SONACOMS.NS", "BALKRISIND.NS", "UNOMINDA.NS", "BOSCHLTD.NS"]
    },
    "3808": {
        "sector": "Agrochemicals - Exports",
        "description": "Insecticides, fungicides, and herbicides",
        "tickers": ["PIIND.NS", "UPL.NS", "SHARDACROP.NS", "SUMICHEM.NS", "RALLIS.NS"]
    },
    "8607": {
        "sector": "Railway Components - Exports",
        "description": "Parts of railway locomotives and rolling-stock",
        "tickers": ["JWL.NS", "TEXRAIL.NS", "TITAGARH.NS", "RKFORGING.NS", "TIMKEN.NS"]
    },
    "71": {
        "sector": "Gems & Jewelry - Exports",
        "description": "Diamonds, precious stones, and jewelry",
        "tickers": ["GOLDIAM.NS", "RENAISSANCE.NS", "VAIBHAVGBL.NS", "TITAN.NS", "KALYANKJIL.NS", "RAJESHEXPO.NS", "ASIANSTAR.NS"]
    }
}

# =====================================================================
# 2. UTILITY FUNCTIONS FOR FETCHING AND PROCESSING DATA
# =====================================================================
import time

def fetch_us_import_data(hs_code):
    """
    EXPERT MODEL: Fetches 24 months of trade data to identify structural 
    shifts rather than temporary noise.
    """
    REPORTER = "842"
    PARTNER = "699"
    BASE_URL = "https://comtradeapi.un.org/public/v1/preview/C/M/HS"
    
    # Generate 24-month period list
    periods = []
    # We span 3 years to ensure we get a full 24-month window after accounting for lag
    for year in [2024, 2025, 2026]:
        for month in range(1, 13):
            periods.append(f"{year}{month:02d}")
    
    # UN Comtrade Public API limit is usually 12 periods per call, 
    # but some endpoints allow more. We'll fetch in two chunks to be safe.
    period_chunks = [",".join(periods[0:12]), ",".join(periods[12:24])]
    
    all_dfs = []
    hs_list = hs_code.split("_")
    
    for hs in hs_list:
        for p_str in period_chunks:
            time.sleep(1.2) # Rate limit protection
            try:
                params = {"reporterCode": REPORTER, "partnerCode": PARTNER, "flowCode": "M", "cmdCode": hs, "period": p_str}
                response = requests.get(BASE_URL, params=params)
                if response.status_code == 200:
                    data = response.json()
                    if data.get('data'):
                        all_dfs.append(pd.DataFrame(data['data']))
            except Exception as e:
                print(f"Error fetching chunk: {e}")

    if not all_dfs:
        return pd.DataFrame()

    combined_df = pd.concat(all_dfs).groupby('period')['primaryValue'].sum().reset_index()
    combined_df['Date'] = pd.to_datetime(combined_df['period'], format='%Y%m')
    combined_df = combined_df.rename(columns={'primaryValue': 'Import_Value_USD'})
    
    # EXPERT FILTER: Apply 3-month rolling average to smooth out shipment timing noise
    combined_df = combined_df.sort_values('Date')
    combined_df['Import_Value_USD_Smoothed'] = combined_df['Import_Value_USD'].rolling(window=3).mean()
    
    combined_df['YearMonth'] = combined_df['Date'].dt.to_period('M')
    return combined_df

def process_and_plot_signals(hs_code, target_ticker, alert_threshold=10.0):
    """
    EXPERT ANALYSIS: Synchronizes 24 months of trade data with stock price 
    performance to generate actionable signals.
    """
    sector_info = TRADE_TO_STOCK_MAP[hs_code]
    report_dir = "reports"
    if not os.path.exists(report_dir):
        os.makedirs(report_dir)
    
    trade_df = fetch_us_import_data(hs_code)
    
    if trade_df.empty or trade_df['Import_Value_USD_Smoothed'].isnull().all():
        return {"sector": sector_info['sector'], "ticker": target_ticker, "status": "Error: Insufficient Data"}
    
    try:
        # Fetch stock data matching the trade timeframe
        start_date = trade_df['Date'].min().strftime('%Y-%m-%d')
        end_date = (trade_df['Date'].max() + pd.DateOffset(months=1)).strftime('%Y-%m-%d')
        
        stock = yf.Ticker(target_ticker)
        stock_df = stock.history(start=start_date, end=end_date)
        
        if stock_df.empty:
             return {"sector": sector_info['sector'], "ticker": target_ticker, "status": "Error: No Stock Data"}
        
        stock_monthly = stock_df['Close'].resample('ME').last().reset_index()
        stock_monthly['YearMonth'] = stock_monthly['Date'].dt.tz_localize(None).dt.to_period('M')
        
        # SYNC: Merge trade and stock data
        merged = pd.merge(trade_df, stock_monthly, on='YearMonth', suffixes=('_trade', '_stock'))
        
        if merged.shape[0] < 4:
            return {"sector": sector_info['sector'], "ticker": target_ticker, "status": "Error: Data Gap"}

        # EXPERT SIGNAL: Compare latest 3-month avg vs previous 3-month avg
        latest_avg = merged['Import_Value_USD_Smoothed'].iloc[-1]
        prev_avg = merged['Import_Value_USD_Smoothed'].iloc[-4]
        recent_growth = ((latest_avg - prev_avg) / prev_avg) * 100
        
        # RENDER: Professional dual-axis visualization
        fig, ax1 = plt.subplots(figsize=(12, 6))
        ax1.set_xlabel('Timeline (Last 24 Months)')
        ax1.set_ylabel('US Imports (USD) - 3mo Moving Avg', color='navy')
        ax1.plot(merged['Date_trade'], merged['Import_Value_USD_Smoothed'], color='navy', linewidth=3, label='Trade Flow (Smoothed)')
        ax1.tick_params(axis='y', labelcolor='navy')
        ax1.grid(True, linestyle='--', alpha=0.3)
        
        ax2 = ax1.twinx()
        ax2.set_ylabel(f'{target_ticker} Price (INR)', color='forestgreen')
        ax2.plot(merged['Date_trade'], merged['Close'], color='forestgreen', linewidth=2, linestyle='--', label='Equity Value')
        ax2.tick_params(axis='y', labelcolor='forestgreen')
        
        plt.title(f"EXPERT MACRO CORRELATION: {sector_info['sector']}\nTicker: {target_ticker} | Signal: {recent_growth:+.1f}% Growth (QoQ)", fontsize=13)
        fig.tight_layout()
        
        filename = f"{report_dir}/{sector_info['sector'].replace(' ', '_')}_{target_ticker}.png"
        plt.savefig(filename)
        plt.close()
        
        return {
            "sector": sector_info['sector'],
            "ticker": target_ticker,
            "growth": recent_growth,
            "signal": recent_growth >= alert_threshold,
            "chart": filename,
            "status": "Success"
        }

    except Exception as e:
        return {"sector": TRADE_TO_STOCK_MAP[hs_code]['sector'], "ticker": target_ticker, "status": f"Error: {e}"}


# =====================================================================
# 3. SCRIPT EXECUTION ENTRY POINT
# =====================================================================
if __name__ == "__main__":
    print("===============================================================")
    print(" LAUNCHING US EXPORT -> INDIAN EQUITIES TRACKING SYSTEM ")
    print("===============================================================")
    
    results = []
    
    for hs_code, info in TRADE_TO_STOCK_MAP.items():
        lead_ticker = info['tickers'][0]
        print(f"-> Processing: {info['sector']}...")
        res = process_and_plot_signals(hs_code=hs_code, target_ticker=lead_ticker)
        results.append(res)
    
    # FINAL VERDICT REPORT
    print("\n" + "="*60)
    print(f"{'STRATEGIC MACRO VERDICT REPORT':^60}")
    print("="*60)
    print(f"{'SECTOR':<30} | {'TICKER':<12} | {'GROWTH':<8} | {'VERDICT'}")
    print("-" * 60)
    
    # Prepare historical logging
    log_file = "historical_verdicts.csv"
    timestamp = datetime.now().strftime("%Y-%m-%d %H:%M")
    log_data = []

    for r in results:
        if r['status'] == "Success":
            verdict = "🚀 STRONG BUY" if r['signal'] else "🕒 WATCHLIST"
            growth_str = f"{r['growth']:.1f}%"
            print(f"{r['sector']:<30} | {r['ticker']:<12} | {growth_str:<8} | {verdict}")
            
            # Add to log
            log_data.append({
                "Date": timestamp,
                "Sector": r['sector'],
                "Ticker": r['ticker'],
                "Growth_QoQ": r['growth'],
                "Verdict": verdict
            })
        else:
            print(f"{r['sector']:<30} | {r['ticker']:<12} | {'N/A':<8} | ❌ {r['status']}")

    # Save to Historical Log
    if log_data:
        log_df = pd.DataFrame(log_data)
        if not os.path.exists(log_file):
            log_df.to_csv(log_file, index=False)
        else:
            log_df.to_csv(log_file, mode='a', header=False, index=False)
        print(f"\n✅ Historical trends updated in: {os.path.abspath(log_file)}")
            
    print("-" * 60)
    print(f"Charts saved in: {os.path.abspath('reports/')}")
    print("="*60)