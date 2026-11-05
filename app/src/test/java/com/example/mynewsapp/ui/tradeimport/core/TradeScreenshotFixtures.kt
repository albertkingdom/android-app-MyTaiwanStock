package com.example.mynewsapp.ui.tradeimport.core

/**
 * Text boxes from real screenshots of the 投資先生 trade detail screen, ported from the iOS app's
 * `TradeScreenshotFixtures.swift` and `TradeScreenshotVisionFixtures.swift` without changing any value.
 *
 * Pixel rectangles use a top-left origin, like the screenshot; [boxes] converts them to the
 * parser's coordinates (normalized, origin at the lower left) with the same arithmetic as iOS.
 */
object TradeScreenshotFixtures {

    data class Page(val width: Double, val height: Double)

    data class PixelBox(val text: String, val x0: Double, val y0: Double, val x1: Double, val y1: Double)

    fun px(text: String, x0: Int, y0: Int, x1: Int, y1: Int) =
        PixelBox(text, x0.toDouble(), y0.toDouble(), x1.toDouble(), y1.toDouble())

    fun boxes(pixels: List<PixelBox>, page: Page): List<TextBox> = pixels.map { box ->
        TextBox(
            text = box.text,
            x = box.x0 / page.width,
            y = 1 - box.y1 / page.height,
            width = (box.x1 - box.x0) / page.width,
            height = (box.y1 - box.y0) / page.height,
        )
    }

    // collapsed screenshot (1034 x 2000): 元大台灣50 and 華邦電, odd-lot buys

    val collapsedPage = Page(1034.0, 2000.0)

    /** Summary rows and table header above the trades. */
    val collapsedHeader = listOf(
        px("總應收付", 14, 35, 195, 85),
        px("總損益", 530, 35, 665, 85),
        px("-1,102", 12, 110, 135, 150),
        px("0", 530, 110, 553, 150),
        px("總價金", 105, 180, 240, 225),
        px("總手續費", 427, 180, 607, 225),
        px("總交易稅", 770, 180, 952, 225),
        px("1,100", 118, 255, 228, 295),
        px("2", 505, 255, 530, 295),
        px("0", 850, 255, 875, 295),
        px("日期", 57, 410, 143, 455),
        px("名稱", 275, 410, 365, 455),
        px("價格/股數", 468, 410, 665, 455),
        px("應收付/損益", 703, 410, 945, 455),
    )

    val row0Upper = listOf(
        px("2026/", 40, 500, 158, 550),
        px("元大台灣50", 205, 500, 435, 550),
        px("112.55", 552, 500, 688, 550),
        px("-563", 870, 500, 960, 550),
    )
    val row0Lower = listOf(
        px("09/30", 40, 585, 158, 632),
        px("盤中零股買進", 197, 585, 443, 632),
        px("5", 665, 585, 688, 632),
        px("--", 936, 585, 960, 632),
    )
    val row1Upper = listOf(
        px("2026/", 40, 670, 158, 716),
        px("華邦電", 253, 670, 387, 716),
        px("179.50", 552, 670, 688, 716),
        px("-539", 870, 670, 960, 716),
    )
    val row1Lower = listOf(
        px("10/01", 40, 754, 158, 800),
        px("盤中零股買進", 197, 754, 443, 800),
        px("3", 665, 754, 688, 800),
        px("--", 936, 754, 960, 800),
    )

    /** A margin trade, which the app does not support. */
    val marginUpper = listOf(
        px("2026/", 40, 835, 158, 881),
        px("長榮", 270, 835, 370, 881),
        px("200.00", 552, 835, 688, 881),
        px("-20,000", 850, 835, 960, 881),
    )
    val marginLower = listOf(
        px("10/02", 40, 920, 158, 966),
        px("融資買進", 215, 920, 425, 966),
        px("100", 640, 920, 688, 966),
        px("--", 936, 920, 960, 966),
    )

    val collapsed: List<TextBox>
        get() = boxes(collapsedHeader + row0Upper + row0Lower + row1Upper + row1Lower, collapsedPage)

    /** First trade scrolled so only its lower line is visible; the summary rows are above it. */
    val topRowCutOff: List<TextBox>
        get() = boxes(collapsedHeader + row0Lower + row1Upper + row1Lower, collapsedPage)

    val withMarginTrade: List<TextBox>
        get() = boxes(collapsedHeader + row0Upper + row0Lower + marginUpper + marginLower, collapsedPage)

    /** The recognizer sometimes returns neighbouring columns as one text box. */
    val mergedBoxes: List<TextBox>
        get() = boxes(
            collapsedHeader + listOf(
                px("2026/ 元大台灣50", 40, 500, 435, 550), px("112.55", 552, 500, 688, 550), px("-563", 870, 500, 960, 550),
                px("09/30 盤中零股買進", 40, 585, 443, 632), px("5", 665, 585, 688, 632), px("--", 936, 585, 960, 632),
            ),
            collapsedPage,
        )

    // expanded screenshot (1260 x 1209): 台積電 buy and 瑞昱 sell, details open

    val expandedPage = Page(1260.0, 1209.0)

    val expanded: List<TextBox>
        get() = boxes(
            listOf(
            px("2026/", 48, 20, 190, 70),
            px("台積電", 312, 20, 470, 70),
            px("2470.00", 640, 20, 838, 70),
            px("-2,472", 1020, 20, 1170, 70),
            px("09/22", 48, 122, 193, 168),
            px("盤中零股買進", 240, 122, 538, 168),
            px("1", 812, 122, 832, 168),
            px("--", 1140, 122, 1170, 168),
            px("委託書號：", 30, 235, 228, 290),
            px("i025R", 310, 235, 445, 290),
            px("幣別：", 660, 235, 757, 290),
            px("新台幣", 842, 235, 990, 290),
            px("持有成本：", 30, 325, 228, 380),
            px("2,472", 310, 325, 440, 380),
            px("價金：", 660, 325, 757, 380),
            px("2,470", 842, 325, 970, 380),
            px("手續費：", 30, 410, 178, 462),
            px("2", 255, 410, 282, 462),
            px("2026/", 48, 530, 190, 578),
            px("瑞昱", 337, 530, 443, 578),
            px("757.00", 672, 530, 838, 578),
            px("7,542", 1038, 530, 1170, 578),
            px("09/23", 48, 632, 193, 678),
            px("盤中零股賣出", 240, 632, 538, 678),
            px("10", 780, 632, 838, 678),
            px("-367", 1062, 632, 1170, 678),
            px("委託書號：", 30, 745, 228, 800),
            px("i01iF", 310, 745, 425, 800),
            px("幣別：", 660, 745, 757, 800),
            px("新台幣", 842, 745, 990, 800),
            px("持有成本：", 30, 835, 228, 890),
            px("7,909", 310, 835, 440, 890),
            px("報酬率：", 660, 835, 808, 890),
            px("-4.64%", 898, 835, 1055, 890),
            px("價金：", 30, 920, 128, 975),
            px("7,570", 212, 920, 340, 975),
            px("配息總額：", 660, 920, 858, 975),
            px("250", 940, 920, 1028, 975),
            px("手續費：", 30, 1005, 178, 1058),
            px("6", 255, 1005, 282, 1058),
            px("交易稅：", 660, 1005, 808, 1058),
            px("22", 898, 1005, 956, 1058),
            px("沖抵明細", 700, 1090, 900, 1150),
            ),
            expandedPage,
        )

    // Exact text boxes the iOS Vision framework returned for the real screenshots
    // (x, y, width, height in the parser's coordinates).

    /** Collapsed list: 元大台灣50 and 華邦電, odd-lot buys. */
    val visionCollapsed: List<TextBox> = listOf(
            TextBox("總應收付", 0.0084, 0.9564, 0.1826, 0.0262),
            TextBox("-1,102", 0.0054, 0.9183, 0.1268, 0.0267),
            TextBox("總價金", 0.0955, 0.8850, 0.1376, 0.0250),
            TextBox("1,100", 0.1120, 0.8455, 0.1131, 0.0270),
            TextBox("日期", 0.0534, 0.7703, 0.0871, 0.0247),
            TextBox("2026/", 0.0363, 0.7250, 0.1211, 0.0223),
            TextBox("09/30", 0.0364, 0.6845, 0.1210, 0.0206),
            TextBox("2026/", 0.0365, 0.6410, 0.1236, 0.0220),
            TextBox("10/01", 0.0364, 0.6001, 0.1154, 0.0206),
            TextBox("總損益 含息", 0.5082, 0.9573, 0.2279, 0.0258),
            TextBox("0", 0.5084, 0.9244, 0.0309, 0.0189),
            TextBox("總手續費", 0.4073, 0.8850, 0.1798, 0.0250),
            TextBox("2", 0.4860, 0.8517, 0.0309, 0.0218),
            TextBox("不含息", 0.7556, 0.9608, 0.0983, 0.0189),
            TextBox("總交易稅", 0.7415, 0.8849, 0.1800, 0.0252),
            TextBox("0", 0.8202, 0.8517, 0.0281, 0.0218),
            TextBox("名稱", 0.2612, 0.7689, 0.0927, 0.0262),
            TextBox("元大台灣50", 0.1938, 0.7238, 0.2303, 0.0247),
            TextBox("盤中零股買進", 0.1882, 0.6815, 0.2416, 0.0250),
            TextBox("華邦電", 0.2416, 0.6395, 0.1348, 0.0262),
            TextBox("盤中零股買進", 0.1882, 0.5988, 0.2416, 0.0233),
            TextBox("價格/股數 應收付/損益", 0.4494, 0.7689, 0.4663, 0.0262),
            TextBox("112.55", 0.5308, 0.7237, 0.1490, 0.0235),
            TextBox("5", 0.6404, 0.6846, 0.0309, 0.0218),
            TextBox("179.50", 0.5281, 0.6395, 0.1461, 0.0247),
            TextBox("3", 0.6404, 0.6003, 0.0253, 0.0189),
            TextBox("-563", 0.8369, 0.7265, 0.0931, 0.0208),
            TextBox("-539", 0.8371, 0.6439, 0.0955, 0.0203),
            TextBox("-", 0.8961, 0.6032, 0.0393, 0.0116),
            TextBox("× 注意事項*", 0.0028, 0.0785, 0.3174, 0.0291),
            TextBox("專家", 0.1882, 0.0334, 0.1067, 0.0247),
            TextBox("自選 交易", 0.3538, 0.0346, 0.2727, 0.0239),
            TextBox("行情", 0.7022, 0.0334, 0.0927, 0.0247),
            TextBox("e櫃台", 0.8567, 0.0334, 0.1152, 0.0247),
    )

    /** The six trade rows of IMG_2039.PNG, including the last row arrow. */
    val fullSixTrades: List<TextBox> = listOf(
            TextBox("2026/、", 0.01867392, 0.57497444, 0.14246287, 0.01429530),
            TextBox("元大台灣50", 0.15457413, 0.57994186, 0.21451104, 0.01616798),
            TextBox("10/19", 0.02208202, 0.54796512, 0.10410094, 0.02034884),
            TextBox("盤中零股買進", 0.14826499, 0.54347826, 0.22082018, 0.01756825),
            TextBox("2026/元大台灣50", 0.01521818, 0.48957837, 0.35757626, 0.02956420),
            TextBox("10/19", 0.02208202, 0.46796339, 0.10410094, 0.02059497),
            TextBox("盤中零股買進", 0.14826499, 0.46366279, 0.22082018, 0.01744186),
            TextBox("2026/元大台灣50", 0.01570537, 0.41114364, 0.35344730, 0.02654993),
            TextBox("10/20", 0.01870509, 0.38782433, 0.11085480, 0.02086297),
            TextBox("盤中零股買進", 0.14826499, 0.38367658, 0.22082018, 0.01754386),
            TextBox("2026/統一FANG+", 0.01571792, 0.33123520, 0.35973134, 0.02648309),
            TextBox("10/20", 0.01868226, 0.30785575, 0.11090046, 0.02091640),
            TextBox("盤中零股買進", 0.14826499, 0.30377907, 0.22082018, 0.01744186),
            TextBox("2026/", 0.01562762, 0.25127170, 0.12016432, 0.02071242),
            TextBox("元大台灣50", 0.14800063, 0.25664440, 0.22134890, 0.02159492),
            TextBox("10/22", 0.01892745, 0.22807018, 0.11041009, 0.02059497),
            TextBox("盤中零股買進", 0.14826499, 0.22383721, 0.22082018, 0.01758155),
            TextBox("2026/", 0.01539338, 0.17099143, 0.12063280, 0.01993575),
            TextBox("10/22", 0.01892745, 0.14825581, 0.11041009, 0.02034884),
            TextBox("華邦電", 0.19558360, 0.17732558, 0.12618296, 0.02034884),
            TextBox("盤中零股買進", 0.14826499, 0.14378337, 0.22082018, 0.01755384),
            TextBox("108.15", 0.47318612, 0.57703488, 0.12933753, 0.02034884),
            TextBox("9", 0.57097792, 0.54360465, 0.02839117, 0.01744186),
            TextBox("107.90", 0.47290886, 0.49668501, 0.12989206, 0.01971137),
            TextBox("10", 0.55205047, 0.46366279, 0.05047319, 0.01889535),
            TextBox("107.20", 0.47311446, 0.41560773, 0.12948086, 0.02198220),
            TextBox("10", 0.55205047, 0.38367658, 0.05047319, 0.01906941),
            TextBox("144.20", 0.46366857, 0.33712559, 0.13890892, 0.01906277),
            TextBox("5", 0.57097792, 0.30377907, 0.02839117, 0.01744186),
            TextBox("107.05", 0.47276804, 0.25523940, 0.13017370, 0.02149794),
            TextBox("10", 0.54889590, 0.22383721, 0.05362776, 0.01889535),
            TextBox("155.50", 0.46679645, 0.17720301, 0.13580773, 0.01914050),
            TextBox("5", 0.57097792, 0.14389535, 0.02839117, 0.01744186),
            TextBox("-974", 0.81698885, 0.57698533, 0.10419265, 0.02044795),
            TextBox("-1,080", 0.78834663, 0.49241238, 0.13308592, 0.02680315),
            TextBox("11", 0.87697161, 0.46796339, 0.04416404, 0.00878080),
            TextBox("-1,073", 0.79164440, 0.41117622, 0.12964496, 0.02648478),
            TextBox("11", 0.87381704, 0.38662791, 0.05047319, 0.01017442),
            TextBox("-722", 0.81367443, 0.33554079, 0.10766690, 0.02223238),
            TextBox("--", 0.87066246, 0.30511060, 0.05047319, 0.01182304),
            TextBox("-1,071", 0.79751034, 0.25085077, 0.12422222, 0.02736822),
            TextBox("--", 0.87066246, 0.22529070, 0.05362776, 0.01162791),
            TextBox("-778", 0.81690171, 0.17573756, 0.10436693, 0.02207138),
            TextBox("--", 0.87066246, 0.14530892, 0.05047319, 0.01182304),
            TextBox("V", 0.94006309, 0.16279070, 0.04100946, 0.01453488),
    )

    /** Cropped 統一FANG+ row. Vision misses the share count 5 and reads -- as 11. */
    val fangMissingShares: List<TextBox> = listOf(
            TextBox("2026/", 0.018242, 0.451613, 0.117745, 0.258065),
            TextBox("10/20", 0.019568, 0.112479, 0.110118, 0.280419),
            TextBox("統一FANG+", 0.147595, 0.505376, 0.227197, 0.290323),
            TextBox("盤中零股買進", 0.149254, 0.043011, 0.218905, 0.247312),
            TextBox("144.20", 0.469133, 0.512187, 0.133045, 0.276701),
            TextBox("-722", 0.824096, 0.556936, 0.096418, 0.219460),
            TextBox("11", 0.878939, 0.107527, 0.046434, 0.118280),
    )

    /** Expanded rows: 台積電 buy and 瑞昱 sell, with the detail block open. */
    val visionExpanded: List<TextBox> = listOf(
            TextBox("2026/", 0.0364, 0.9452, 0.1223, 0.0384),
            TextBox("台積電", 0.1810, 0.9405, 0.2048, 0.0446),
            TextBox("09/22 盤中零股買進", 0.0349, 0.8579, 0.3937, 0.0430),
            TextBox("委託書號：i025R", 0.0238, 0.7620, 0.3302, 0.0413),
            TextBox("持有成本：2,472", 0.0218, 0.6800, 0.3293, 0.0525),
            TextBox("手續費：2", 0.0238, 0.6182, 0.2032, 0.0413),
            TextBox("2026/", 0.0365, 0.5221, 0.1192, 0.0400),
            TextBox("瑞昱", 0.2508, 0.5190, 0.1016, 0.0446),
            TextBox("09/23 盤中零股賣出", 0.0333, 0.4329, 0.3917, 0.0476),
            TextBox("委託書號：iO1iF", 0.0238, 0.3388, 0.3143, 0.0430),
            TextBox("持有成本：7,909", 0.0222, 0.2579, 0.3286, 0.0512),
            TextBox("價金：7,570", 0.0221, 0.1860, 0.2511, 0.0528),
            TextBox("手續費：6", 0.0238, 0.1240, 0.2016, 0.0413),
            TextBox("2470.00", 0.5063, 0.9403, 0.1620, 0.0433),
            TextBox("1", 0.6302, 0.8612, 0.0317, 0.0347),
            TextBox("幣別：新台幣", 0.5254, 0.7603, 0.2603, 0.0430),
            TextBox("價金：2,470", 0.5219, 0.6761, 0.2514, 0.0577),
            TextBox("-2,472", 0.8045, 0.9331, 0.1275, 0.0511),
            TextBox("757.00", 0.5300, 0.5186, 0.1383, 0.0422),
            TextBox("10", 0.6175, 0.4397, 0.0508, 0.0364),
            TextBox("幣別：新台幣", 0.5238, 0.3388, 0.2635, 0.0430),
            TextBox("報酬率：-4.64%", 0.5222, 0.2645, 0.3175, 0.0446),
            TextBox("配息總額：250", 0.5222, 0.1950, 0.2952, 0.0430),
            TextBox("交易稅：22", 0.5238, 0.1223, 0.2381, 0.0430),
            TextBox("沖抵明細", 0.5568, 0.0500, 0.1577, 0.0454),
            TextBox("7,542", 0.8220, 0.5118, 0.1084, 0.0507),
            TextBox("-367", 0.8381, 0.4397, 0.0937, 0.0380),
    )

    /**
     * ML Kit (Chinese model) output, on an emulator, for an expanded screenshot (900 x 2000) of
     * two odd-lot buys: 元大台灣50 and 台積電. The second trade type lost its first character
     * ("中零股買進"). Status bar and navigation boxes are kept as recognized.
     * The account number and order numbers are replaced with placeholders.
     */
    val mlKitExpandedTwoBuys = RecognizedPage(900, 2000, listOf(
        listOf(PixelElement("13:29", 49, 44, 124, 69), PixelElement("@我回ox", 139, 44, 306, 69)),
        listOf(PixelElement("く", 35, 117, 56, 151), PixelElement("下單", 120, 121, 190, 154), PixelElement("委託", 266, 120, 338, 154)),
        listOf(PixelElement("|證0000-0000...▼", 27, 217, 344, 256)),
        listOf(PixelElement("總應收付", 13, 313, 185, 357)),
        listOf(PixelElement("-3,309", 12, 389, 129, 427)),
        listOf(PixelElement("總價金", 84, 449, 214, 499)),
        listOf(PixelElement("3,307", 98, 527, 200, 566)),
        listOf(PixelElement("日期", 48, 680, 126, 720), PixelElement("名稱", 234, 679, 319, 721)),
        listOf(PixelElement("2026/", 28, 770, 142, 808), PixelElement("元大台灣50", 172, 770, 380, 808)),
        listOf(PixelElement("11/05", 29, 852, 142, 890), PixelElement("盤中零股買進", 184, 852, 391, 890)),
        listOf(PixelElement("委託畫號:i0001", 25, 945, 349, 983)),
        listOf(PixelElement("持有戍本:1,068", 24, 1014, 348, 1054)),
        listOf(PixelElement("手續費:1", 24, 1080, 214, 1123)),
        listOf(PixelElement("2026/", 47, 1174, 149, 1214), PixelElement("台積電", 214, 1176, 340, 1216)),
        listOf(PixelElement("11/05", 30, 1258, 142, 1296), PixelElement("中零股買進", 199, 1258, 386, 1296)),
        listOf(PixelElement("委託書號:i0002", 26, 1352, 350, 1392)),
        listOf(PixelElement("持有成本:2,241", 26, 1416, 340, 1461)),
        listOf(PixelElement("手續費:1", 26, 1491, 216, 1528)),
        listOf(PixelElement("※注意事項※", 13, 1793, 314, 1848)),
        listOf(PixelElement("明細", 415, 120, 484, 154)),
        listOf(PixelElement("總手績費", 362, 452, 535, 499)),
        listOf(PixelElement("2", 440, 530, 460, 561)),
        listOf(PixelElement("庫存", 560, 120, 630, 154)),
        listOf(PixelElement("0獲", 457, 390, 488, 494)),
        listOf(PixelElement("今日", 453, 217, 523, 254), PixelElement("▼", 580, 224, 606, 248), PixelElement("|全部▼", 671, 216, 771, 255)),
        listOf(PixelElement("總损益含息不含息", 444, 314, 820, 358)),
        listOf(PixelElement("97.00", 486, 772, 592, 804)),
        listOf(PixelElement("11", 549, 854, 586, 886)),
        listOf(PixelElement("Vo)", 724, 43, 746, 54)),
        listOf(PixelElement("帳務", 710, 111, 886, 165)),
        listOf(PixelElement("價格/股數應收付/損益", 393, 679, 826, 721)),
        listOf(PixelElement("2240.00", 435, 1175, 593, 1212)),
        listOf(PixelElement("1", 574, 1261, 586, 1291)),
        listOf(PixelElement("總交易", 662, 448, 837, 499)),
        listOf(PixelElement("0", 740, 530, 760, 562)),
        listOf(PixelElement("幣別:新台幣", 476, 946, 732, 984)),
        listOf(PixelElement("價金:1,067", 475, 1015, 721, 1054)),
        listOf(PixelElement("-1,068", 709, 772, 827, 810)),
        listOf(PixelElement("42", 825, 45, 834, 61)),
        listOf(PixelElement("-2,241", 709, 1179, 820, 1217)),
        listOf(PixelElement("幣別:", 477, 1352, 586, 1389), PixelElement("新台幣", 619, 1352, 735, 1389)),
        listOf(PixelElement("價金:2,240", 475, 1421, 720, 1460)),
        listOf(PixelElement("く", 842, 1810, 865, 1826)),
        listOf(PixelElement("專家", 164, 1890, 292, 1940), PixelElement("自選", 338, 1890, 418, 1940), PixelElement("交易行情", 491, 1890, 732, 1940), PixelElement("e櫃台", 769, 1890, 886, 1940)),
    ))

    /**
     * ML Kit output on the user's phone for the same screenshot at full size (1260 x 2800). Both
     * trade types start with a misread character, and each row's "^" chevron is read as "》" on
     * its own line between the two lines of the trade.
     * The account number and order numbers are replaced with placeholders.
     */
    val mlKitPhoneExpandedTwoBuys = RecognizedPage(1260, 2800, listOf(
        listOf(PixelElement("13:29", 69, 61, 174, 96), PixelElement("e代回oX", 195, 61, 428, 96)),
        listOf(PixelElement("く", 49, 164, 79, 212), PixelElement("下單", 169, 169, 265, 215)),
        listOf(PixelElement("|證0000-0000...", 8, 299, 452, 366), PixelElement("▼", 444, 299, 482, 366), PixelElement("|", 517, 299, 520, 366)),
        listOf(PixelElement("總應收付", 16, 432, 261, 505)),
        listOf(PixelElement("-3,309", 17, 545, 181, 598)),
        listOf(PixelElement("總價金", 117, 629, 298, 697)),
        listOf(PixelElement("委託", 373, 168, 473, 216)),
        listOf(PixelElement("3,307", 135, 740, 283, 789)),
        listOf(PixelElement("2026/元大台灣50", 39, 1078, 532, 1132)),
        listOf(PixelElement("11/05", 40, 1189, 199, 1241), PixelElement("歸中零股買進", 252, 1189, 544, 1241)),
        listOf(PixelElement("委託書號:i0001", 35, 1322, 490, 1374)),
        listOf(PixelElement("持有成本:1,068", 35, 1421, 487, 1475)),
        listOf(PixelElement("手續費:1", 35, 1514, 300, 1571)),
        listOf(PixelElement("2026/", 39, 1650, 194, 1699), PixelElement("台積電", 300, 1645, 475, 1702)),
        listOf(PixelElement("11/05", 41, 1760, 199, 1816), PixelElement("艦中零股買進", 259, 1760, 554, 1816)),
        listOf(PixelElement("委託書號:i0002", 35, 1891, 490, 1949)),
        listOf(PixelElement("持有戌本:2,241", 35, 1990, 478, 2044)),
        listOf(PixelElement("手續費:1", 35, 2083, 300, 2140)),
        listOf(PixelElement("明紐", 581, 168, 678, 216)),
        listOf(PixelElement("總手绩費", 507, 630, 751, 699)),
        listOf(PixelElement("2", 616, 742, 645, 786)),
        listOf(PixelElement("餐注意事項*", 21, 2515, 441, 2586)),
        listOf(PixelElement("今日", 633, 306, 732, 358)),
        listOf(PixelElement("庫存", 785, 167, 884, 217)),
        listOf(PixelElement("日期", 68, 950, 177, 1008), PixelElement("名稱", 307, 947, 477, 1013), PixelElement("價格/股數應收付/損益", 550, 947, 1157, 1013)),
        listOf(PixelElement("總損益含息[不含息)", 627, 438, 1148, 501)),
        listOf(PixelElement("97.00", 680, 1082, 823, 1126)),
        listOf(PixelElement("11", 770, 1197, 819, 1238)),
        listOf(PixelElement("帳務", 967, 164, 1099, 216)),
        listOf(PixelElement("Vo)", 1016, 59, 1045, 76), PixelElement("4G+", 1056, 59, 1095, 76)),
        listOf(PixelElement("LTE", 1012, 76, 1045, 103), PixelElement("l44", 1105, 62, 1176, 93)),
        listOf(PixelElement("|全部▼", 939, 303, 1078, 358)),
        listOf(PixelElement("2240.00", 611, 1650, 825, 1694)),
        listOf(PixelElement("1", 803, 1765, 819, 1803)),
        listOf(PixelElement("總交易稅", 894, 627, 1190, 700)),
        listOf(PixelElement("幣別:新台幣", 666, 1316, 1026, 1376)),
        listOf(PixelElement("價金:1,067", 665, 1421, 1009, 1475)),
        listOf(PixelElement("-1,068", 993, 1079, 1157, 1133)),
        listOf(PixelElement("0", 1035, 740, 1066, 784)),
        listOf(PixelElement("-2,241", 994, 1646, 1147, 1704)),
        listOf(PixelElement("幣別:新台幣", 666, 1891, 1031, 1946)),
        listOf(PixelElement("償金:2,240", 665, 1990, 1008, 2044)),
        listOf(PixelElement("》", 1190, 1144, 1231, 1169)),
        listOf(PixelElement("》", 1187, 1713, 1231, 1738)),
        listOf(PixelElement("專家", 256, 2651, 374, 2709), PixelElement("自選", 448, 2644, 610, 2709), PixelElement("交易", 678, 2644, 790, 2709), PixelElement("行情青e櫃台", 886, 2644, 1228, 2709)),
    ))
}
