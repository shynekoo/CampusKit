package io.qzxy.lite.preview;

import android.content.Context;
import android.net.Uri;
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.pdmodel.PDPage;
import com.tom_roush.pdfbox.text.PDFTextStripper;
import com.tom_roush.pdfbox.text.TextPosition;
import io.qzxy.lite.core.TimetablePdfParser;
import java.io.*;
import java.util.*;

final class TimetablePdfReader {
    static TimetablePdfParser.Result read(Context context, Uri uri) throws Exception {
        File file=File.createTempFile("timetable-", ".pdf", context.getCacheDir());
        try {
            try(InputStream in=context.getContentResolver().openInputStream(uri); OutputStream out=new FileOutputStream(file)) {
                if(in==null) throw new IOException("无法打开文件");
                byte[] buffer=new byte[8192]; int n,total=0;
                while((n=in.read(buffer))!=-1) { total+=n; if(total>10*1024*1024) throw new IOException("请选择小于 10 MB 的 PDF"); out.write(buffer,0,n); }
            }
            PDFBoxResourceLoader.init(context.getApplicationContext());
            try(PDDocument doc=PDDocument.load(file)) {
                if(doc.isEncrypted() || doc.getNumberOfPages()>10) throw new IOException("请选择未加密、最多 10 页的课表 PDF");
                final List<List<TimetablePdfParser.Glyph>> pages=new ArrayList<>();
                PDFTextStripper stripper=new PDFTextStripper() {
                    int count;
                    @Override protected void startPage(PDPage page) throws IOException { super.startPage(page); pages.add(new ArrayList<>()); }
                    @Override protected void processTextPosition(TextPosition t) {
                        if(++count>200000) throw new IllegalArgumentException("PDF 内容过多");
                        pages.get(pages.size()-1).add(new TimetablePdfParser.Glyph(t.getXDirAdj(),t.getYDirAdj(),t.getWidthDirAdj(),t.getUnicode()));
                    }
                };
                stripper.getText(doc); return TimetablePdfParser.parse(pages);
            }
        } finally { file.delete(); }
    }
}
