package net.optionfactory.spring.pdf;

import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle;

public class PdfFontInfo {

    public String path;
    public String family;
    public int weight;
    public FontStyle style;
    public boolean subset;

    public static PdfFontInfo of(String path, String family, int weight, FontStyle style, boolean subset) {
        final var fi = new PdfFontInfo();
        fi.path = path;
        fi.family = family;
        fi.weight = weight;
        fi.style = style;
        fi.subset = subset;
        return fi;
    }

}
