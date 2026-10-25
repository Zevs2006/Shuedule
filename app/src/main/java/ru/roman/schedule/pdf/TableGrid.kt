package ru.roman.schedule.pdf

import android.graphics.Path
import android.graphics.PointF
import com.tom_roush.pdfbox.contentstream.PDFGraphicsStreamEngine
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImage

/**
 * Извлекает линии таблицы из страницы PDF. Нужен, чтобы ячейки
 * (в том числе объединённые на несколько пар) определялись по
 * настоящим границам, а не по догадкам о координатах текста.
 */
internal object TableGrid {

    data class Segment(val x0: Float, val y0: Float, val x1: Float, val y1: Float)

    fun segments(document: PDDocument, pageIndex: Int): List<Segment> {
        return try {
            val page = document.getPage(pageIndex)
            val collector = Collector(page)
            collector.processPage(page)
            collector.segments
        } catch (error: Exception) {
            emptyList()
        }
    }

    private class Collector(page: PDPage) : PDFGraphicsStreamEngine(page) {
        val segments = ArrayList<Segment>()
        private var lastX = 0f
        private var lastY = 0f

        override fun appendRectangle(p0: PointF, p1: PointF, p2: PointF, p3: PointF) {
            segments.add(Segment(p0.x, p0.y, p2.x, p2.y))
        }

        override fun drawImage(pdImage: PDImage) {}

        override fun clip(fillType: Path.FillType) {}

        override fun moveTo(x: Float, y: Float) {
            lastX = x
            lastY = y
        }

        override fun lineTo(x: Float, y: Float) {
            segments.add(Segment(lastX, lastY, x, y))
            lastX = x
            lastY = y
        }

        override fun curveTo(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) {
            lastX = x3
            lastY = y3
        }

        override fun getCurrentPoint(): PointF = PointF(lastX, lastY)

        override fun closePath() {}

        override fun endPath() {}

        override fun fillPath(fillType: Path.FillType) {}

        override fun fillAndStrokePath(fillType: Path.FillType) {}

        override fun strokePath() {}

        override fun shadingFill(shadingName: COSName) {}
    }
}