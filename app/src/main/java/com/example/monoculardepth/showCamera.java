package com.example.monoculardepth;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.hardware.Camera;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.sql.Time;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import static android.content.ContentValues.TAG;

public class showCamera extends SurfaceView implements SurfaceHolder.Callback, Camera.PreviewCallback {
    Camera camera;
    SurfaceHolder holder; ImageView bottom;
    Bitmap bmp;
    byte[] previewBuffer;
    boolean isStreaming = true;
    AvcEncoder encoder;
    DatagramSocket udpSocket;
    InetAddress address;
    int port;
    ArrayList<byte[]> encDataList = new ArrayList<byte[]>();
    ArrayList<Integer> encDataLengthList = new ArrayList<Integer>();
    Runnable senderRun = new Runnable() {
        @Override
        public void run()
        {
            System.out.println("IN THREAD");
            while (isStreaming)
            {
                boolean empty = false;
                byte[] encData = null;

                synchronized(encDataList)
                {
                    if (encDataList.size() == 0)
                    {
                        empty = true;
                    }
                    else
                        encData = encDataList.remove(0);
                }
                if (empty)
                {
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    continue;
                }
                try
                {
                    DatagramPacket packet = new DatagramPacket(encData, encData.length, address, port);
                    udpSocket.send(packet);
                }
                catch (IOException e)
                {
                    e.printStackTrace();
                }
            }
            //TODO:
        }
    };
    public showCamera(Context context, Camera camera) {

        super(context);
        this.camera=camera;
        holder=getHolder();
        holder.addCallback(this);

        bottom=findViewById(R.id.bottom);
    }

    @Override
    public void surfaceCreated(@NonNull SurfaceHolder surfaceHolder) {
        Camera.Parameters params=camera.getParameters();

       // params.setPreviewFormat(0x20);
        if(this.getResources().getConfiguration().orientation!= Configuration.ORIENTATION_LANDSCAPE)
        {
            params.set("orientation","portrait");

            camera.setDisplayOrientation(90);
            params.setRotation(90);
        }
        else
        {
            params.set("orientation","landscape");
            camera.setDisplayOrientation(0);
            params.setRotation(0);


        }
        params.setPreviewSize(640,480);
        params.setPictureSize(640,480);

        //params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
        params.setPreviewFpsRange(30000,30000);
        camera.setParameters(params);
        try {
            camera.setPreviewDisplay(holder);
        } catch (IOException e) {
            e.printStackTrace();
        }
        camera.startPreview();

    }

    @Override
    public void surfaceChanged(@NonNull SurfaceHolder surfaceHolder, int i, int i1, int i2) {

        camera.setPreviewCallback(new Camera.PreviewCallback() {
            @Override
            public void onPreviewFrame(byte[] data, Camera camera) {
                int frameHeight = camera.getParameters().getPreviewSize().height;
                int frameWidth = camera.getParameters().getPreviewSize().width;
                // number of pixels//transforms NV21 pixel data into RGB pixels
                int rgb[] = new int[frameWidth * frameHeight];
                // convertion
                int[] myPixels = decodeYUV420SP(rgb, data, frameWidth, frameHeight);

                bmp= Bitmap.createBitmap(myPixels, 640, 480, Bitmap.Config.ARGB_8888);
                Matrix matrix = new Matrix();
                matrix.postRotate(90);
                bmp=Bitmap.createBitmap(bmp, 0, 0, 640, 480, matrix, false);



            }
        });
    }


    @Override
    public void surfaceDestroyed(@NonNull SurfaceHolder surfaceHolder) {


    }
    int[] decodeYUV420SP(int[] rgb, byte[] yuv420sp, int width, int height) {

        final int frameSize = width * height;

        for (int j = 0, yp = 0; j < height; j++) {
            int uvp = frameSize + (j >> 1) * width, u = 0, v = 0;

            for (int i = 0; i < width; i++, yp++) {

                int y = (0xff & ((int) yuv420sp[yp])) - 16;

                if (y < 0)
                y = 0;
                if ((i & 1) == 0) {

                    v = (0xff & yuv420sp[uvp++]) - 128;
                    u = (0xff & yuv420sp[uvp++]) - 128;
                }

                int y1192 = 1192 * y;

                int r = (y1192 + 1634 * v);

                int g = (y1192 - 833 * v - 400 * u);

                int b = (y1192 + 2066 * u);

                if (r < 0)
                r = 0;
        else if (r > 262143)
                r = 262143;
                if (g < 0)
                g = 0;
        else if (g > 262143)
                g = 262143;
                if (b < 0)
                b = 0;
        else if (b > 262143)
                b = 262143;

                rgb[yp] = 0xff000000 | ((r << 6) & 0xff0000) | ((g >> 2) & 0xff00) | ((b >> 10) & 0xff);

            }
        }
        return rgb;
    }

    Bitmap sendPix() {
        /*int width, height;
        height = bmp.getHeight();
        width = bmp.getWidth();
        Bitmap bmpGrayscale = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmpGrayscale);
        Paint paint = new Paint();
        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(0);
        ColorMatrixColorFilter f = new ColorMatrixColorFilter(cm);
        paint.setColorFilter(f);
        c.drawBitmap(bmp, 0, 0, paint);
        return bmpGrayscale;*/
        return bmp;
    }


    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {
        this.camera.addCallbackBuffer(this.previewBuffer);

        if (this.isStreaming)
        {
            System.out.println("y not");
            if (this.encDataLengthList.size() > 100)
            {
                Log.e(TAG, "OUT OF BUFFER");
                return;
            }

            byte[] encData = this.encoder.offerEncoder(data);
            if (encData.length > 0)
            {
                synchronized(this.encDataList)
                {
                    this.encDataList.add(encData);
                }
            }
        }
    }
    public void startStream(String ip, int port)
    {
       // System.out.println("IN START STREAM");

        this.encoder = new AvcEncoder();
        this.encoder.init(640, 480, 15, 500000);

        try
        {
            this.udpSocket = new DatagramSocket();
            this.address = InetAddress.getByName(ip);
            this.port = port;
        }
        catch (SocketException e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
            return;
        }
        catch (UnknownHostException e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
            return;
        }

        this.isStreaming = true;
        Thread thrd = new Thread(senderRun);
        thrd.start();

    }
}
