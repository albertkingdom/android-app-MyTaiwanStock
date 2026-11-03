package com.example.mynewsapp.stocklist

import java.io.File

/** Locates the bundled list in both the Android unit-test build and standalone JVM builds. */
object TestAssets {
    private const val RELATIVE = "app/src/main/assets/StockList.json"

    fun bundledStockList(): File {
        System.getProperty("stocklist.asset")?.let { path ->
            val file = File(path)
            if (file.isFile) return file
        }
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            val candidate = File(dir, RELATIVE)
            if (candidate.isFile) return candidate
            dir = dir.parentFile
        }
        error("bundled StockList.json not found; set -Dstocklist.asset")
    }
}
