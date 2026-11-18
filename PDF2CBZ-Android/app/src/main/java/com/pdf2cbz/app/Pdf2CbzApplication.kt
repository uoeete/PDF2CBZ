package com.pdf2cbz.app

import android.app.Application
import com.pdf2cbz.app.worker.ConversionController
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class Pdf2CbzApplication : Application() {

    lateinit var conversionController: ConversionController
        private set

    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(applicationContext)
        conversionController = ConversionController(applicationContext)
    }
}
