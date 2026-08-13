package net.optionfactory.spring.pdf;

import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle;

public record PdfFontInfo(String path, String family, int weight, FontStyle style, boolean subset) {

    public static PdfFontInfo of(String path, String family, int weight, FontStyle style, boolean subset) {
        return new PdfFontInfo(path, family, weight, style, subset);
    }
}
