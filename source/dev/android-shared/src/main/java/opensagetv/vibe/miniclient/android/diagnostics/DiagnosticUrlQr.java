package opensagetv.vibe.miniclient.android.diagnostics;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import java.util.EnumMap;

/** Pure offline matrix. Only validated device LAN URLs, never credentials. */
public final class DiagnosticUrlQr {
    private DiagnosticUrlQr() { }
    public static BitMatrix encode(String url) throws WriterException {
        if (!DiagnosticExportPolicy.validWebUrl(url)) throw new IllegalArgumentException("Invalid diagnostic web URL");
        EnumMap<EncodeHintType,Object> hints=new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.ERROR_CORRECTION,ErrorCorrectionLevel.M);
        hints.put(EncodeHintType.MARGIN,4);
        return new QRCodeWriter().encode(url,BarcodeFormat.QR_CODE,0,0,hints);
    }
}
