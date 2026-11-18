package com.pdf2cbz.app

import android.app.Application
import com.pdf2cbz.app.worker.ConversionController
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class Pdf2CbzApplication : Application() {

    val conversionController: ConversionController by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        ConversionController(applicationContext)
    }

    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(applicationContext)
    }
}
