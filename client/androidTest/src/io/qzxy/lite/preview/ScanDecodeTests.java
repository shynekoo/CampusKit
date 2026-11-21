// SPDX-License-Identifier: MPL-2.0
package io.qzxy.lite.preview;

import android.graphics.Bitmap;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import java.util.Arrays;

/** Synthetic camera-like frames; no network, real device identifiers, or water commands. */
final class ScanDecodeTests {
    static int run() throws Exception {
        String text="https://i.hnkzy.com/q/1/123456789012345";
        BitMatrix qr=new QRCodeWriter().encode(text,BarcodeFormat.QR_CODE,220,220);
        int count=0, width=1920,height=1080,side=qr.getWidth();
        for(int position=0;position<2;position++)for(int rotation=0;rotation<4;rotation++) {
            byte[] frame=new byte[width*height];Arrays.fill(frame,(byte)24);
            int left=position==0?(width-side)/2:120,top=position==0?(height-side)/2:100;
            for(int y=0;y<side;y++)for(int x=0;x<side;x++) {
                int sx=rotation==0?x:rotation==1?y:rotation==2?side-1-x:side-1-y;
                int sy=rotation==0?y:rotation==1?side-1-x:rotation==2?side-1-y:x;
                // Simulate an LCD code with a lighting gradient and limited contrast.
                int value=(qr.get(sx,sy)?95:175)+x*25/side;
                if(rotation==3)value=255-value;
                frame[(top+y)*width+left+x]=(byte)value;
            }
            try {if(!text.equals(QrDecoder.frame(frame,width,height)))throw new AssertionError("Wrong payload");}
            catch(Exception failure){throw new AssertionError("Camera QR position="+position+" rotation="+rotation,failure);}count++;
        }
        Bitmap image=Bitmap.createBitmap(side,side,Bitmap.Config.ARGB_8888);
        try {for(int y=0;y<side;y++)for(int x=0;x<side;x++)image.setPixel(x,y,qr.get(x,y)?0xffffffff:0xff000000);
            if(!text.equals(QrDecoder.bitmap(image)))throw new AssertionError("Inverted picked image");count++;
        }finally{image.recycle();}
        for(byte[] blank:new byte[][]{null,new byte[8],new byte[320*320]}) {
            boolean rejected=false;try{QrDecoder.frame(blank,320,320);}catch(com.google.zxing.NotFoundException expected){rejected=true;}
            if(!rejected)throw new AssertionError("Empty frame decoded");count++;
        }
        return count;
    }
}
