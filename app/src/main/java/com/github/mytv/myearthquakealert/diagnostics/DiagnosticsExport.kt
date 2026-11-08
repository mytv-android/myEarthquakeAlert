package com.github.mytv.myearthquakealert.diagnostics

import android.content.Context
import com.github.mytv.myearthquakealert.util.LogExporter

/**
 * 导出诊断包: a zip with the event log, metrics JSON and redacted settings (coordinates
 * rounded to 0.1°), shared through the system sheet. Stub: falls back to the 1.0 log export.
 */
object DiagnosticsExport {
    fun share(context: Context) {
        LogExporter.export(context)
    }
}
