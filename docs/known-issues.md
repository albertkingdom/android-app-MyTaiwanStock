# 已知問題

尚未修正、但已確認的問題。修正時請另開 change 或 fix 分支，修好後從本檔移除該項。

## 清單重新命名、刪除不會同步到雲端備份

- 記錄日期：2026-11-06
- 影響範圍：登入 Google 帳號、有使用雲端備份的使用者
- 相關程式：
  - `app/src/main/java/com/example/mynewsapp/ui/list/ListFragment.kt`（⋮ 選單的「重新命名」「刪除此清單」）
  - `app/src/main/java/com/example/mynewsapp/ui/list/EditFollowingListFragment.kt`（「管理清單」畫面的重新命名與滑動刪除）
  - `app/src/main/java/com/example/mynewsapp/ui/list/ListViewModel.kt`（`updateFollowingListName`、`deleteFollowingList`、`uploadNewStockNoToOnlineDB`、`deleteStockNoFromOnlineDB`、`syncFollowingList`）
  - `app/src/main/java/com/example/mynewsapp/firebase/FirebaseManager.kt`（`uploadNewStockNoToOnlineDB`、`deleteStockNo`、`deleteList`）

### 現況

Firestore 的 `followingList` 集合以「帳號 email + 清單名稱」找到一份清單文件。App 的清單重新命名與刪除只改本機 Room 資料庫，不更新雲端：

- 重新命名：只呼叫 `ListViewModel.updateFollowingListName`，雲端文件仍是舊名稱。
- 刪除：只呼叫 `ListViewModel.deleteFollowingList`。`ListViewModel.deleteListFromOnlineDB` 存在，但沒有任何地方呼叫（編輯清單畫面的呼叫已被註解掉）。

### 會發生什麼

1. 刪除清單後，下次「登入後下載雲端資料」（`getAllOnlineDBDataAndSaveToLocal`）會把刪掉的清單和裡面的股票加回來。
2. 把清單 A 重新命名為 B 之後：
   - 在 B 新增或滑動刪除股票時，雲端找不到名為 B 的文件。`FirebaseManager.uploadNewStockNoToOnlineDB` 與 `deleteStockNo` 不會報錯，只會略過，所以這些變更沒有備份。
   - 雲端仍保留 A。下次下載雲端資料時，本機找不到名為 A 的清單，會新建一個 A，和 B 重複。

這個問題在 Material 3 改版（`material3-watchlist-redesign`）之前就存在。改版在 ⋮ 選單加入「重新命名」「刪除此清單」，讓這兩個操作更容易被使用。

### 修正方向

- 重新命名：同時更新雲端文件的 `name` 欄位（先以舊名稱找到文件，再改名）。
- 刪除：在 ⋮ 選單與編輯清單畫面刪除時呼叫 `deleteListFromOnlineDB`。
- 長期：雲端文件改用穩定的 id 對應本機清單，不再以名稱當鍵，避免重新命名與同名問題。
- 修正時需補測試：重新命名後新增股票會寫到同一份雲端文件；刪除後再同步不會把清單加回來。

## 管理清單畫面的刪除與重新命名缺少保護

- 記錄日期：2026-11-06
- 相關程式：`app/src/main/java/com/example/mynewsapp/ui/list/EditFollowingListFragment.kt`

### 現況

- 滑動刪除立即刪掉清單與其中的股票，沒有確認，Snackbar 也沒有「復原」。⋮ 選單的「刪除此清單」會先確認。
- 任何清單都能被滑掉，包含第一個清單。舊的 RecyclerView 版本禁止刪除第一個清單，改寫成 Compose 時遺失了這條規則。
- 重新命名對話框不檢查名稱，可以存成空白或與其他清單同名（⋮ 選單的重新命名會用 `ListNameValidator` 擋下）。同名清單會讓以名稱為鍵的雲端同步合併錯清單。對話框也不會預填目前名稱。

### 修正方向

- 滑動刪除改為先確認，或在 Snackbar 提供「復原」。
- 決定是否保留「第一個清單不可刪」的規則；若不保留，刪除唯一清單時會自動建立空的 Default。
- 重新命名改用 `ListNameValidator` 與 `validateListNameOnConfirm`，並預填目前名稱。
