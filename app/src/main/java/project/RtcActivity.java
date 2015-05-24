package project;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Point;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager.LayoutParams;
import android.widget.Toast;

import com.firebase.client.DataSnapshot;
import com.firebase.client.Firebase;
import com.firebase.client.FirebaseError;
import com.firebase.client.ValueEventListener;

import org.json.JSONException;
import org.webrtc.MediaStream;
import org.webrtc.VideoRenderer;
import org.webrtc.VideoRendererGui;

import fr.pchab.androidrtc.R;
import Libraries.WebRtcClient;
import Libraries.PeerConnectionParameters;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;
import android.annotation.SuppressLint;

import javax.net.ssl.HttpsURLConnection;

public class RtcActivity extends Activity implements WebRtcClient.RtcListener {
    private final String FIREBASE_URL = "https://dazzling-inferno-1709.firebaseio.com/";
    private final static int VIDEO_CALL_SENT = 666;
    private static final String VIDEO_CODEC_VP9 = "VP9";
    private static final String AUDIO_CODEC_OPUS = "opus";
    // Local preview screen position before call is connected.
    private static final int LOCAL_X_CONNECTING = 0;
    private static final int LOCAL_Y_CONNECTING = 0;
    private static final int LOCAL_WIDTH_CONNECTING = 100;
    private static final int LOCAL_HEIGHT_CONNECTING = 100;
    // Local preview screen position after call is connected.
    private static final int LOCAL_X_CONNECTED = 72;
    private static final int LOCAL_Y_CONNECTED = 72;
    private static final int LOCAL_WIDTH_CONNECTED = 25;
    private static final int LOCAL_HEIGHT_CONNECTED = 25;
    // Remote video screen position
    private static final int REMOTE_X = 0;
    private static final int REMOTE_Y = 0;
    private static final int REMOTE_WIDTH = 100;
    private static final int REMOTE_HEIGHT = 100;
    private VideoRendererGui.ScalingType scalingType = VideoRendererGui.ScalingType.SCALE_ASPECT_FILL;
    private GLSurfaceView vsv;
    private VideoRenderer.Callbacks localRender;
    private VideoRenderer.Callbacks remoteRender;
    private WebRtcClient client;
    private String mSocketAddress;
    private String host;
    private String callerId;
    private Firebase mFirebaseRef;
    private String DATA;
    private int lock = 0;
    private String robot_id = "";


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //thi will be changed to the right one later on
        host = "temp";
        //initialize firebase library
        Firebase.setAndroidContext(this);
        //initiate firebase reference
        mFirebaseRef = new Firebase(FIREBASE_URL);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(
                LayoutParams.FLAG_FULLSCREEN
                        | LayoutParams.FLAG_KEEP_SCREEN_ON
                        | LayoutParams.FLAG_DISMISS_KEYGUARD
                        | LayoutParams.FLAG_SHOW_WHEN_LOCKED
                        | LayoutParams.FLAG_TURN_SCREEN_ON);
        setContentView(R.layout.main);
        //this listener event is for determining 'host' value
        mFirebaseRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                for (DataSnapshot child : snapshot.getChildren()) {
                    host = snapshot.child("host_ip").getValue().toString();
                    mFirebaseRef.child("users/try").setValue(host);
                    lock = 2;
                }
            }

            @Override
            public void onCancelled(FirebaseError firebaseError) {

            }
        });
        //set the listener to handle "CONNECTION_ISSUE"
        mFirebaseRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if(snapshot.child("users/robot_"+robot_id+"/robot_response").getValue().toString()=="CONNECTION_ISSUE") {
                    mFirebaseRef.child("users/robot_" + robot_id + "/robot_response").setValue("CONNECTION_OK");
                    setFirebase();
                }
            }

            @Override
            public void onCancelled(FirebaseError firebaseError) {

            }
        });
        mFirebaseRef.child("users/try").setValue("Temp1");
        mFirebaseRef.child("users/try").setValue("Temp2");
        vsv = (GLSurfaceView) findViewById(R.id.glview_call);
        vsv.setPreserveEGLContextOnPause(true);
        vsv.setKeepScreenOn(true);
        VideoRendererGui.setView(vsv, new Runnable() {
            @Override
            public void run() {
                init();
            }
        });

        // local and remote render
        remoteRender = VideoRendererGui.create(
                REMOTE_X, REMOTE_Y,
                REMOTE_WIDTH, REMOTE_HEIGHT, scalingType, false);
        localRender = VideoRendererGui.create(
                LOCAL_X_CONNECTING, LOCAL_Y_CONNECTING,
                LOCAL_WIDTH_CONNECTING, LOCAL_HEIGHT_CONNECTING, scalingType, true);

        final Intent intent = getIntent();
        final String action = intent.getAction();

        if (Intent.ACTION_VIEW.equals(action)) {
            final List<String> segments = intent.getData().getPathSegments();
            callerId = segments.get(0);
        }
    }

    private void init() {
        Point displaySize = new Point();
        getWindowManager().getDefaultDisplay().getSize(displaySize);
        PeerConnectionParameters params = new PeerConnectionParameters(
                true, false, displaySize.x, displaySize.y, 30, 1, VIDEO_CODEC_VP9, true, 1, AUDIO_CODEC_OPUS, true);
        while(lock==0){}
        mSocketAddress = "http://" + host;
        client = new WebRtcClient(this, mSocketAddress, params, VideoRendererGui.getEGLContext());
    }

    @Override
    public void onPause() {
        super.onPause();
        vsv.onPause();
        if(client != null) {
            client.onPause();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        vsv.onResume();
        if(client != null) {
            client.onResume();
        }
    }

    @Override
    public void onDestroy() {
        if(client != null) {
            client.onDestroy();
        }
        super.onDestroy();
    }

    @Override
    public void onCallReady(String callId) {
        if (callerId != null) {
            try {
                answer(callerId);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        } else {
            call(callId);
        }
    }

    public void answer(String callerId) throws JSONException {
        client.sendMessage(callerId, "init", null);
        startCam();
    }

    public void call(String callId) {
        // my change to avoid the gmail choosing
        /*Intent msg = new Intent(Intent.ACTION_SEND);
        msg.putExtra(Intent.EXTRA_TEXT, mSocketAddress + callId);
        msg.setType("text/plain");
        startActivityForResult(Intent.createChooser(msg, "Call someone :"), VIDEO_CALL_SENT);*/
        //for now ill send mail to myself, but later on ill update firebase argument
        /*Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("message/rfc822");
        i.putExtra(Intent.EXTRA_EMAIL  , new String[]{"tamirtamir20@gmail.com"});
        i.putExtra(Intent.EXTRA_SUBJECT, "vhh");
        i.putExtra(Intent.EXTRA_TEXT   , mSocketAddress + callId);
        try {
            startActivity(Intent.createChooser(i, "Send mail..."));
        } catch (android.content.ActivityNotFoundException ex) {
            Toast.makeText(RtcActivity.this, "There are no email clients installed.", Toast.LENGTH_SHORT).show();
        }*/

        //setFirebase(callId); was originally here but we want the robot's id to be constant, so
        // we determine its value to be the first call id
        if(robot_id == ""){
            robot_id = callId;
        }
        setFirebase();
        startCam();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == VIDEO_CALL_SENT) {
            startCam();
        }
    }

    public void startCam() {
        // Camera settings
        client.start("android_test");
    }

    @Override
    public void onStatusChanged(final String newStatus) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), newStatus, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onLocalStream(MediaStream localStream) {
        localStream.videoTracks.get(0).addRenderer(new VideoRenderer(localRender));
        VideoRendererGui.update(localRender,
                LOCAL_X_CONNECTING, LOCAL_Y_CONNECTING,
                LOCAL_WIDTH_CONNECTING, LOCAL_HEIGHT_CONNECTING,
                scalingType);
    }

    @Override
    public void onAddRemoteStream(MediaStream remoteStream, int endPoint) {
        remoteStream.videoTracks.get(0).addRenderer(new VideoRenderer(remoteRender));
        VideoRendererGui.update(remoteRender,
                REMOTE_X, REMOTE_Y,
                REMOTE_WIDTH, REMOTE_HEIGHT, scalingType);
        VideoRendererGui.update(localRender,
                LOCAL_X_CONNECTED, LOCAL_Y_CONNECTED,
                LOCAL_WIDTH_CONNECTED, LOCAL_HEIGHT_CONNECTED,
                scalingType);
    }

    @Override
    public void onRemoveRemoteStream(int endPoint) {
        VideoRendererGui.update(localRender,
                LOCAL_X_CONNECTING, LOCAL_Y_CONNECTING,
                LOCAL_WIDTH_CONNECTING, LOCAL_HEIGHT_CONNECTING,
                scalingType);
    }

    public void setFirebase() {
        //this line was commeted because first we have to check for "ISSUE_CONNECTION" value before
        //we can answer "CONNECTION_OK"
        //mFirebaseRef.child("users/robot_"+id+"/robot_response").setValue("CONNECTION_OK");

        mFirebaseRef.child("users/robot_"+robot_id+"/rtsp_stream_url").setValue(mSocketAddress + "/" +robot_id);
    }
}