## Why

Android 版的報價查詢把 `tse_` 前綴寫死在兩處（`ListViewModel.getStockPriceInfoRx`、`UpdateWidgetPeriodicTask.fetchData`），上櫃股票（例如 6488 環球晶）查不到報價。新增股票用的代號清單是 `res/values/stockNo_suggestions.xml` 裡寫死的 1,120 筆字串，沒有上櫃股票，也沒有近年新上市的 ETF（例如 00929、00981A）。iOS 版已在 `refresh-stock-list` 變更中改為官方開放資料建清單、支援上櫃報價，而 Firestore 同步會把 iOS 清單裡的上櫃代號帶到 Android，目前在 Android 上只會顯示查無報價。上櫃股票進入詳細頁時，K 線請求（證交所 STOCK_DAY 只支援上市）回傳沒有 `data` 欄位，`CandleStickChartViewModel.getCandleStickData` 對 `data!!` 會直接 crash。

## What Changes

- 新增內建清單 `app/src/main/assets/StockList.json`（2,387 筆：上市 1,380、上櫃 1,007，與 iOS 版 2026-10-26 的內建清單相同），格式 `{"entries":[{"code","name","market"}]}`；新增產生腳本 `scripts/generate_stock_list.py`，來源為證交所 STOCK_DAY_ALL 與櫃買中心 tpex_mainboard_daily_close_quotes，上櫃資料排除權證（6 碼且不以 00 開頭）。
- 新增 `StockListRepository`：先讀本機快取（`filesDir/StockList.json`），快取不存在、損毀或為空時讀內建清單，清單永不為空；提供「代號 名稱」搜尋字串、代號精確比對、代號查市場。
- 新增 `StockListRefresher`：沒有快取或距上次成功更新已達 7 天時，從兩個官方來源重抓；兩個市場各自套用 80% 防呆，失敗一律保留舊資料、不顯示錯誤；同一時間只執行一次。由 WorkManager 的一次性 unique work（需要網路）在背景執行，排入點只有 `MainActivity.onStart`，是否過期由 worker 判斷。
- 新增 `StockMarketLookup`：上市用 `tse_{code}.tw`、上櫃用 `otc_{code}.tw`，查不到市場時用 `tse_`；清單頁報價與桌面小工具共用。
- 新增股票（`AddStockFragment` 搜尋、`AddStockDialogFragment` 自動完成）改讀 `StockListRepository`；對話框輸入的代號必須精確等於清單內某筆代號才加入，否則顯示錯誤。刪除 `stockNo_suggestions.xml`。
- K 線資料取不到或為空時，詳細頁的圖表區顯示「無資料」，不 crash，買賣紀錄與總覽照常顯示；資料筆數少於均線所需時，該條均線不畫；價格不是數字的列（例如停牌日的 "--"）略過不畫。

## Capabilities

### New Capabilities

- `stock-list-refresh`: 股票代號清單的來源與過濾、本機快取與內建備援、背景更新與防呆、單一更新、代號精確比對與搜尋字串。
- `otc-stock-support`: 依市場決定報價前綴（清單頁與小工具共用），以及 K 線無資料或筆數不足時的畫面行為。

### Modified Capabilities

(none)

## Impact

- Affected specs: 新增 `stock-list-refresh`、`otc-stock-support`（Android 目前沒有任何 spec）。
- Affected code:
  - New: scripts/generate_stock_list.py
  - New: app/src/main/assets/StockList.json
  - New: app/src/main/java/com/example/mynewsapp/stocklist/StockListEntry.kt（StockMarket、StockListEntry）
  - New: app/src/main/java/com/example/mynewsapp/stocklist/StockListParser.kt
  - New: app/src/main/java/com/example/mynewsapp/stocklist/StockListRepository.kt
  - New: app/src/main/java/com/example/mynewsapp/stocklist/StockListRefresher.kt
  - New: app/src/main/java/com/example/mynewsapp/stocklist/StockMarketLookup.kt
  - New: app/src/main/java/com/example/mynewsapp/stocklist/StockSearch.kt
  - New: app/src/main/java/com/example/mynewsapp/stocklist/OkHttpStockListFetcher.kt
  - New: app/src/main/java/com/example/mynewsapp/stocklist/android/SharedPreferencesRefreshTimeStore.kt
  - New: app/src/main/java/com/example/mynewsapp/stocklist/android/StockListRefreshWorker.kt
  - New: app/src/main/java/com/example/mynewsapp/use_case/chart/MovingAverage.kt
  - New: app/src/main/java/com/example/mynewsapp/use_case/chart/CandleRows.kt
  - New: app/src/test/java/com/example/mynewsapp/stocklist/（StockListParserTest、StockListRepositoryTest、StockListRefresherTest、StockMarketLookupTest、StockSearchTest、OkHttpStockListFetcherTest、TestAssets）
  - New: app/src/test/java/com/example/mynewsapp/use_case/chart/（MovingAverageTest、CandleRowsTest）
  - Modified: app/src/main/java/com/example/mynewsapp/MyApplication.kt（持有 repository 與 refresher）
  - Modified: app/src/main/java/com/example/mynewsapp/ui/MainActivity.kt（onStart 排入更新工作）
  - Modified: app/src/main/java/com/example/mynewsapp/ui/list/ListViewModel.kt（報價前綴）
  - Modified: app/src/main/java/com/example/mynewsapp/ui/widget/UpdateWidgetPeriodicTask.kt（報價前綴）
  - Modified: app/src/main/java/com/example/mynewsapp/ui/list/AddStockFragment.kt、AddStockViewModel.kt、AddStockDialogFragment.kt（清單來源、精確比對）
  - Modified: app/src/main/res/layout/dialog_add_newstock.xml（輸入類型改為文字，才能輸入 00981A 等代號）
  - Modified: app/src/main/java/com/example/mynewsapp/ui/detail/CandleStickChartViewModel.kt、CandleStickChartFragment.kt（無資料、均線）
  - Modified: app/build.gradle（testImplementation：mockwebserver 5.0.0-alpha.2，與 app 現有 okhttp 版本一致）
  - Removed: app/src/main/res/values/stockNo_suggestions.xml
