package com.example.monoculardepth;

import androidx.appcompat.app.AppCompatActivity;
import org.tensorflow.lite.gpu.CompatibilityList;
import org.tensorflow.lite.gpu.GpuDelegate;

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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static android.graphics.Bitmap.Config.ARGB_8888;

import wseemann.media.FFmpegMediaMetadataRetriever;

public class demo extends AppCompatActivity {
    ImageView bottom,top,middle;
    private ExecutorService executorService = Executors.newCachedThreadPool();



    public class demoThread extends  Thread {
        private ImageView myImageView;
        private MediaMetadataRetriever mmr = null  ;
        private FFmpegMediaMetadataRetriever fmmr = null;
        private Interpreter aiModel;
        private TensorImage aiInputBuffer;
        private ImageProcessor imagePreProcessor;
        private Bitmap inputBitmap;
        private Bitmap outputBitmap;
        private long threadStartTime;
        private int[] grayIntVals ;
        private int height;
        private int width;
        protected Interpreter tflite;
        private float[][][] f1 ;
        private float[] pixels;
        private Handler h2 = new Handler();
        private String streamName ;

        public demoThread(Activity context,ImageView imgView, String uri, int inputHeight, int inputWidth, float mean, float std)
        {
            myImageView = imgView;
            aiModel = tflite;
            height = inputHeight;
            width = inputWidth;
            streamName = uri;

            if(uri.contains("rtsp://"))
            {
                fmmr = new FFmpegMediaMetadataRetriever();
                fmmr.setDataSource(uri);
            }
            else
            {
                mmr = new MediaMetadataRetriever();
                Uri myUri = Uri.parse(uri);
                mmr.setDataSource(getApplication(),myUri);
            }

            try {
                Interpreter.Options tfliteOptions = new Interpreter.Options();

                if(uri.contains("demoo"))
                    tfliteOptions.setNumThreads(4);
                else {
                    CompatibilityList compatList = new CompatibilityList();
                    // if the device has a supported GPU, add the GPU delegate
                    GpuDelegate.Options delegateOptions = compatList.getBestOptionsForThisDevice();

                    GpuDelegate gpuDelegate = new GpuDelegate(delegateOptions);
                    tfliteOptions.addDelegate(gpuDelegate);
                }
                tflite = new Interpreter(loadModelFile(context), tfliteOptions);
            } catch (IOException e) {
                e.printStackTrace();
            }

            int imageTensorIndex = 0;
            DataType imageDataType = tflite.getInputTensor(imageTensorIndex).dataType();
            aiInputBuffer = new TensorImage(imageDataType);

            inputBitmap = Bitmap.createBitmap(width, height, ARGB_8888);
            outputBitmap = Bitmap.createBitmap(width/2, height/2, ARGB_8888);
            grayIntVals =  new int[(width/2)*(height/2)];
            f1 = new float[1][(height/2)][(width/2)];
            pixels = new float[(width/2)*(height/2)];

            imagePreProcessor =
                    new ImageProcessor.Builder()
                            .add(new ResizeOp(height,width, null))
                            .add(new NormalizeOp(mean,std))
                            .build();

            myImageView.setImageBitmap(outputBitmap);

            //start();
            executorService.execute(this);
        }
        public void run()
        {

            long frameCount1 = 0;

            if(threadStartTime ==0)
            {
                threadStartTime = System.currentTimeMillis() ;
            }
            else
            {
                frameCount1 =  (System.currentTimeMillis() - threadStartTime)*1000;
                Log.d("timeInMs", " frameCount " + frameCount1/1000000 + " " );
            }

            Log.d("mmrtr", "mmrtr retrieving frame");
            if(fmmr == null)
            {
                Bitmap bmFrame = mmr.getFrameAtTime(frameCount1, MediaMetadataRetriever.OPTION_CLOSEST); //unit in microsecond
                inputBitmap = bmFrame.copy(Bitmap.Config.ARGB_8888, true);
            }
            else
            {
                inputBitmap  = fmmr.getFrameAtTime();
            }
            Log.d("mmrtr", "mmrtr retrieved frame" + streamName);

            aiInputBuffer.load(inputBitmap);
            aiInputBuffer = imagePreProcessor.process(aiInputBuffer);

            tflite.run(aiInputBuffer.getBuffer(), f1);


            myImageView.clearColorFilter();
            outputBitmap.setPixels(postProcessOutput(f1), 0, width/2, 0, 0, width/2, height/2);

            myImageView.setImageBitmap(outputBitmap);

            if(( (System.currentTimeMillis() - threadStartTime) < 15000 ) || threadStartTime ==0)
                h2.post(this);

        };
        public void setBitmap()
        {
            myImageView.setImageBitmap(outputBitmap);
        }
        public int[] postProcessOutput(float[][][] f1)
        {
            int pixelsIndex = 0;
            //for depth model only!!
            float max = 0.0f;
            float min = 355.0f;


            for (int x = 0; x < height/2; x++)
            {
                for (int y = 0; y < width/2; y++)
                {

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
            return grayIntVals;
        }
        private MappedByteBuffer loadModelFile(Activity activity) throws IOException {
            AssetFileDescriptor fileDescriptor = activity.getAssets().openFd("modellowres.tflite");
            FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
            FileChannel fileChannel = inputStream.getChannel();
            long startOffset = fileDescriptor.getStartOffset();
            long declaredLength = fileDescriptor.getDeclaredLength();
            return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_demo);
        bottom = findViewById(R.id.bottom);
        top=findViewById(R.id.top);
        middle = findViewById(R.id.middle);

        demoThread d3 = new demoThread(this,middle, "rtsp://192.168.1.14:8080/h264_pcm.sdp" , 480, 640,128.0f, 128.0f);
        demoThread d1 = new demoThread(this,top, "android.resource://" + getPackageName() + "/" + R.raw.flower, 480, 640,128.0f, 128.0f);
        demoThread d2 = new demoThread(this,bottom, "android.resource://" + getPackageName() + "/" + R.raw.demoo, 480, 640,128.0f, 128.0f);


        Thread background = new Thread() {
            public void run() {
                try {
                    // Thread will sleep for 10 seconds
                    //Change 10 to the amount of time needed for the video
                    sleep(15*1000);
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


}