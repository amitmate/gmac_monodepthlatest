package com.example.monoculardepth;

import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.ImageView;
import android.widget.MediaController;
import android.widget.VideoView;

//import net.majorkernelpanic.streaming.rtsp.RtspServer;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.common.ops.NormalizeOp;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.image.ops.ResizeOp;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.HashMap;

import static android.graphics.Bitmap.Config.ARGB_8888;

import wseemann.media.FFmpegMediaMetadataRetriever;

public class demo extends AppCompatActivity {
    ImageView bottom,top;

    MediaMetadataRetriever mediaMetadataRetriever[] = new MediaMetadataRetriever[2];
    ImageProcessor imageProcessor = null;
    MediaMetadataRetriever mediaMetadataRetriever1;
    Bitmap bitmap = Bitmap.createBitmap(640, 480, ARGB_8888);
    Bitmap bitmap1 = Bitmap.createBitmap(640, 480, ARGB_8888);

    FFmpegMediaMetadataRetriever mmr = new FFmpegMediaMetadataRetriever();

    int w = 320;
    int h = 240;
    float[][][] f1 = new float[1][h][w];
    float[] pixels = new float[w * h];
    int[] grayIntVals = new int[w * h];

    int framecount = 1;
    VideoView videoView;
    int x=0,y=0,x1=0,y1=0;
    int fcount=0;
    long[] timeInMS = new long[2];

    private TensorImage inputImageBuffer;
    private static final String TAG = "DemoActivity";
    protected Interpreter tflite;


    Runnable r2 = new Runnable() {
        @Override
        public void run() {

            //if(fcount % 2 == 0)
            //    updateImage(0);
            //else
                updateImage(1);
            framecount += 16666*9;
            //timeInMS[1] = System.currentTimeMillis() -timeInMS[1];
            h2.post(r2);

        }
    };

    Runnable r1 = new Runnable() {
        @Override
        public void run() {

            //if(fcount % 2 == 0)
                updateImage(0);
            //else
            //    updateImage(1);
            framecount += 16666*9;
            //timeInMS[0] = System.currentTimeMillis() -timeInMS[0];

            h1.post(r1);

        }
    };

    Bitmap rgbDepthBitmap = Bitmap.createBitmap(w, h, ARGB_8888);
    Bitmap rgbDepthBitmap1 = Bitmap.createBitmap(w, h, ARGB_8888);


    private void updateCanvas(int streamid)
    {
        if(streamid == 0)
        {
            bottom.clearColorFilter();
            rgbDepthBitmap.setPixels(grayIntVals, 0, w, 0, 0, w, h);

            //top.setImageBitmap(topf);
            bottom.setImageBitmap(rgbDepthBitmap);
        }
        else
        {
            top.clearColorFilter();
            rgbDepthBitmap1.setPixels(grayIntVals, 0, w, 0, 0, w, h);

            //top.setImageBitmap(mmr.getFrameAtTime());
            top.setImageBitmap(rgbDepthBitmap1);
        }
        fcount++;
        Log.d(TAG, "After update bitmap");
    }

    protected  void runInBackground(final Runnable r) {
        if (h2 != null) {
            h2.post(r);
        }
    }

    protected  void runInBackground2(final Runnable r) {
        if (h1 != null) {
            h1.post(r);
        }
    }

    void processRTSPFrame(int streamid)

    {
        runInBackground( () -> {


            Log.d("rtspstreamdelay", " rtspstream before");
            inputImageBuffer = loadImage(mmr.getFrameAtTime());
            Log.d("rtspstreamdelay", " rtspstream after");

            Log.d(TAG, "Before Running TFLITTE");
            tflite.run(inputImageBuffer.getBuffer(), f1);

            Log.d(TAG, "Before Array conversion");


            int pixelsIndex = 0;
            //for depth model only!!
            float max = 0.0f;
            float min = 355.0f;


            for (int x = 0; x < h; x++) {
                for (int y = 0; y < w; y++) {

                    if (f1[0][x][y] > max)
                        max = f1[0][x][y];
                    if (f1[0][x][y] < min)
                        min = f1[0][x][y];

                    pixels[pixelsIndex] = f1[0][x][y];

                    float gVal = 255.0f * (pixels[pixelsIndex] - min) / (max - min);
                    int gray = (int) gVal;
                    //grayIntVals[pixelsIndex]= Color.argb(255, f1[0][x][y], f1[0][x][y], f1[0][x][y]);
                    grayIntVals[pixelsIndex] = Color.argb(255, gray, gray, gray);
                    pixelsIndex++;

                }
            }
            updateCanvas(streamid);
        });
    }

    private void processVideoFileFrame(int streamid)
    {


            Log.d("delay", "mmrtr before" +  streamid);
            long frameCount1 = 0;

            if(timeInMS[streamid] ==0)
            {
                timeInMS[streamid] = System.currentTimeMillis() ;

            }
            else
            {
                frameCount1 =  (System.currentTimeMillis() - timeInMS[streamid])*1000;
                Log.d("timeInMs", " frameCount " + frameCount1/1000000 + " " + streamid);

            }
            Bitmap bmFrame = mediaMetadataRetriever[streamid].getFrameAtTime(frameCount1, MediaMetadataRetriever.OPTION_CLOSEST); //unit in microsecond
            Log.d("delay", "mmrtr after" +  streamid);

            if(streamid ==0)
            {
                bitmap = bmFrame.copy(Bitmap.Config.ARGB_8888, true);

                inputImageBuffer = loadImage(bitmap);
            }
            else
            {
                bitmap1 = bmFrame.copy(Bitmap.Config.ARGB_8888, true);

                inputImageBuffer = loadImage(bitmap1);
            }

            Log.d(TAG, "Before Running TFLITTE");
            tflite.run(inputImageBuffer.getBuffer(), f1);

            Log.d(TAG, "Before Array conversion");



            int pixelsIndex = 0;
            //for depth model only!!
            float max = 0.0f;
            float min = 355.0f;


            for (int x = 0; x < h; x++) {
                for (int y = 0; y < w; y++) {

                    if (f1[0][x][y] > max)
                        max = f1[0][x][y];
                    if (f1[0][x][y] < min)
                        min = f1[0][x][y];

                    pixels[pixelsIndex] = f1[0][x][y];

                    float gVal = 255.0f * ( pixels[pixelsIndex] - min) / (max - min);
                    int gray = (int) gVal;
                    //grayIntVals[pixelsIndex]= Color.argb(255, f1[0][x][y], f1[0][x][y], f1[0][x][y]);
                    grayIntVals[pixelsIndex] = Color.argb(255, gray, gray, gray);
                    pixelsIndex++;

                }
            }

            updateCanvas(streamid);
    }

    private void processVideoFileFrame1(int streamid)
    {


        Log.d("delay", "mmrtr before" +  streamid);

        long frameCount1 = 0;

        if(timeInMS[streamid] ==0)
        {
            timeInMS[streamid] = System.currentTimeMillis() ;

        }
        else
        {
            frameCount1 =  (System.currentTimeMillis() - timeInMS[streamid])*1000;

            Log.d("timeInMs", " frameCount " + frameCount1/1000000 + " " + streamid);
        }

        Bitmap bmFrame = mediaMetadataRetriever[streamid].getFrameAtTime(frameCount1, MediaMetadataRetriever.OPTION_CLOSEST); //unit in microsecond
        Log.d("delay", "mmrtr after" +  streamid);
        if(streamid ==0)
        {
            bitmap = bmFrame.copy(Bitmap.Config.ARGB_8888, true);

            inputImageBuffer = loadImage(bitmap);
        }
        else
        {
            bitmap1 = bmFrame.copy(Bitmap.Config.ARGB_8888, true);

            inputImageBuffer = loadImage(bitmap1);
        }

        Log.d(TAG, "Before Running TFLITTE");
        tflite.run(inputImageBuffer.getBuffer(), f1);

        Log.d(TAG, "Before Array conversion");



        int pixelsIndex = 0;
        //for depth model only!!
        float max = 0.0f;
        float min = 355.0f;


        for (int x = 0; x < h; x++) {
            for (int y = 0; y < w; y++) {

                if (f1[0][x][y] > max)
                    max = f1[0][x][y];
                if (f1[0][x][y] < min)
                    min = f1[0][x][y];

                pixels[pixelsIndex] = f1[0][x][y];

                float gVal = 255.0f * ( pixels[pixelsIndex] - min) / (max - min);
                int gray = (int) gVal;
                //grayIntVals[pixelsIndex]= Color.argb(255, f1[0][x][y], f1[0][x][y], f1[0][x][y]);
                grayIntVals[pixelsIndex] = Color.argb(255, gray, gray, gray);
                pixelsIndex++;

            }
        }

        updateCanvas(streamid);
    }

    private void updateImage(int streamid) {

            if(streamid == 1)
            {
                //processRTSPFrame(streamid);
                Log.d("mmrtr", " background mmrtr before" + streamid);
                runInBackground( () -> {
                    processVideoFileFrame1(streamid);
                });
                Log.d("mmrtr", " background mmrtr after" + streamid);

            }
            else {
                Log.d("mmrtr", " background mmrtr before" + streamid);

                runInBackground2( () -> {
                    processVideoFileFrame(streamid);
                });
                Log.d("mmrtr", " background mmrtr after" + streamid);

            }



    }

    Handler h2 = new Handler();

    Handler h1 = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_demo);
        bottom = findViewById(R.id.bottom);
        top=findViewById(R.id.top);
        Uri uri = Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.flower);
        mediaMetadataRetriever[0] = new MediaMetadataRetriever();
        mediaMetadataRetriever[1] = new MediaMetadataRetriever();

        mediaMetadataRetriever[0].setDataSource(getApplication(), uri);

        Uri uri1 = Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.demoo);
        //Uri uri1 = Uri.parse("rtsp://192.168.1.14:8080/h264_pcm.sdp");
        //mediaMetadataRetriever[1].setDataSource("http://192.168.1.14:8080", new HashMap<String, String>());

        //mediaMetadataRetriever[1].setDataSource(getApplication(),Uri.parse("http://192.168.1.14:8080"));
        Log.d("ffmpeg", " ffmpeg instantiating retriever");
        //mmr.setDataSource("rtsp://192.168.1.14:8080/h264_pcm.sdp");
        Log.d("ffmpeg", " ffmpeg data source set");

        //Bitmap b = mmr.getFrameAtTime(); // frame at 2 seconds
        //byte [] artwork = mmr.getEmbeddedPicture();
        Log.d("ffmpeg", " ffmpeg got frame");

        mediaMetadataRetriever[1].setDataSource(getApplication(), uri1);

        imageProcessor =
                new ImageProcessor.Builder()
                        .add(new ResizeOp(480,640, null))
                        .add(new NormalizeOp(128.0f,128.0f))
                        .build();

        try {
            Interpreter.Options tfliteOptions = new Interpreter.Options();
            tfliteOptions.setNumThreads(4);

            tflite = new Interpreter(loadModelFile(this), tfliteOptions);
        } catch (IOException e) {
            e.printStackTrace();

        }
        int imageTensorIndex = 0;
        DataType imageDataType = tflite.getInputTensor(imageTensorIndex).dataType();
        inputImageBuffer = new TensorImage(imageDataType);

        Thread background = new Thread() {
            public void run() {
                try {
                    // Thread will sleep for 10 seconds
                    //Change 10 to the amount of time needed for the video
                    sleep(40*1000);

                    // After 10 seconds redirect to another intent

                    Intent i=new Intent(getBaseContext(),MainActivity.class);
                    startActivity(i);

                    //Remove activity
                    finish();
                } catch (Exception e) {
                }

            }
        };
        background.start();
    }
    @Override
    protected void onPostResume() {
        super.onPostResume();
        h2.postDelayed(r2,1);
        h1.postDelayed(r1,1);

    }

    @Override
    protected void onPause() {
        super.onPause();
        h2.removeCallbacks(r2);
        h1.removeCallbacks(r1);

    }

    private MappedByteBuffer loadModelFile(Activity activity) throws IOException {
        AssetFileDescriptor fileDescriptor = activity.getAssets().openFd("modellowres.tflite");
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }
    private TensorImage loadImage(final Bitmap bitmap) {
        inputImageBuffer.load(bitmap);
        // Creates processor for the TensorImage
        // TODO(b/143564309): Fuse ops inside ImageProcessor.

        return imageProcessor.process(inputImageBuffer);
    }
}