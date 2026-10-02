package net.optionfactory.spring.pdf;

import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle;

/// A font made available to the documents of a [ThymeleafToPdfRenderer], under a CSS
/// `font-family`, weight and style.
///
/// The font is a TrueType file loaded from the classpath with `Class.getResourceAsStream`: an
/// absolute path such as `/fonts/Roboto.ttf` starts at the classpath root, while a relative one is
/// resolved against the `net/optionfactory/spring/pdf` package, where this module ships
/// `font_opensans.ttf` and `font_opensans_bold.ttf`. Register one [PdfFontInfo] per file, all with
/// the same family, to cover the weights and styles of a family.
///
/// @param path the classpath location of the TrueType file
/// @param family the CSS `font-family` the font is used for
/// @param weight the CSS `font-weight` the font is used for, e.g. 400 for normal and 700 for bold
/// @param style the CSS `font-style` the font is used for
/// @param subset whether to embed only the glyphs the document uses, which keeps documents small
public record PdfFontInfo(String path, String family, int weight, FontStyle style, boolean subset) {

    /// @param path the classpath location of the TrueType file
    /// @param family the CSS `font-family` the font is used for
    /// @param weight the CSS `font-weight` the font is used for
    /// @param style the CSS `font-style` the font is used for
    /// @param subset whether to embed only the glyphs the document uses
    /// @return the font info
    public static PdfFontInfo of(String path, String family, int weight, FontStyle style, boolean subset) {
        return new PdfFontInfo(path, family, weight, style, subset);
    }
}
