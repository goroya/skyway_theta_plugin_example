package com.ntt.ecl.webrtc.sample_p2p_videochat;

import android.Manifest;
import android.app.Activity;
import android.app.FragmentManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;

import java.util.ArrayList;

import io.skyway.Peer.Browser.Canvas;
import io.skyway.Peer.Browser.MediaConstraints;
import io.skyway.Peer.Browser.MediaStream;
import io.skyway.Peer.Browser.Navigator;
import io.skyway.Peer.CallOption;
import io.skyway.Peer.MediaConnection;
import io.skyway.Peer.OnCallback;
import io.skyway.Peer.Peer;
import io.skyway.Peer.PeerError;
import io.skyway.Peer.PeerOption;

/**
 * MainActivity.java
 * ECL WebRTC p2p video-chat sample
 */
public class MainActivity extends Activity {
    private static final String TAG = MainActivity.class.getSimpleName();
    private static final String ACTION_MAIN_CAMERA_CLOSE = "com.theta360.plugin.ACTION_MAIN_CAMERA_CLOSE";
    private static final String ACTION_MAIN_CAMERA_OPEN = "com.theta360.plugin.ACTION_MAIN_CAMERA_OPEN";
    private static final String ACTION_FINISH_PLUGIN = "com.theta360.plugin.ACTION_FINISH_PLUGIN";
    private static final String ACTION_ERROR_OCCURED = "com.theta360.plugin.ACTION_ERROR_OCCURED";
    private static final String PACKAGE_NAME = "packageName";
    private static final String EXIT_STATUS = "exitStatus";
    private static final String MESSAGE = "message";

    private static final String ACTION_LED_SHOW = "com.theta360.plugin.ACTION_LED_SHOW";
    private static final String ACTION_LED_BLINK = "com.theta360.plugin.ACTION_LED_BLINK";
    private static final String ACTION_LED_HIDE = "com.theta360.plugin.ACTION_LED_HIDE";
    private static final String TARGET = "target";
    private static final String COLOR = "color";
    private static final String PERIOD = "period";

    private static final String ACTION_AUDIO_SHUTTER = "com.theta360.plugin.ACTION_AUDIO_SHUTTER";
    private static final String ACTION_AUDIO_SH_OPEN = "com.theta360.plugin.ACTION_AUDIO_SH_OPEN";
    private static final String ACTION_AUDIO_SH_CLOSE = "com.theta360.plugin.ACTION_AUDIO_SH_CLOSE";
    private static final String ACTION_AUDIO_MOVSTART = "com.theta360.plugin.ACTION_AUDIO_MOVSTART";
    private static final String ACTION_AUDIO_MOVSTOP = "com.theta360.plugin.ACTION_AUDIO_MOVSTOP";
    private static final String ACTION_AUDIO_SELF = "com.theta360.plugin.ACTION_AUDIO_SELF";
    private static final String ACTION_AUDIO_WARNING = "com.theta360.plugin.ACTION_AUDIO_WARNING";

    private static final String ACTION_WLAN_OFF = "com.theta360.plugin.ACTION_WLAN_OFF";
    private static final String ACTION_WLAN_AP = "com.theta360.plugin.ACTION_WLAN_AP";
    private static final String ACTION_WLAN_CL = "com.theta360.plugin.ACTION_WLAN_CL";

    private static final String ACTION_DATABASE_UPDATE = "com.theta360.plugin.ACTION_DATABASE_UPDATE";
    private static final String TARGETS = "targets";

    private boolean isCamera = false;
    private AudioManager am;
    private Camera mCamera = null;
    private int mCameraId;
    private Camera.Parameters mParameters;

    public void notificationCameraOpen() {
        isCamera = false;
        sendBroadcast(new Intent(ACTION_MAIN_CAMERA_OPEN));
    }

    public void notificationCameraClose() {
        isCamera = true;
        sendBroadcast(new Intent(ACTION_MAIN_CAMERA_CLOSE));
    }

    public void notificationSuccess() {
        Intent intent = new Intent(ACTION_FINISH_PLUGIN);
        intent.putExtra(PACKAGE_NAME, getPackageName());
        intent.putExtra(EXIT_STATUS, "success");
        sendBroadcast(intent);
    }
    public void notificationWlanOff() {
        sendBroadcast(new Intent(ACTION_WLAN_OFF));
    }

    public void notificationWlanAp() {
        sendBroadcast(new Intent(ACTION_WLAN_AP));
    }

    public void notificationWlanCl() {
        sendBroadcast(new Intent(ACTION_WLAN_CL));
    }

    //
    // Set your APIkey and Domain
    //
    private static final String API_KEY = "xxxxxxxxxxxxxxxxxxxxxx";
    private static final String DOMAIN = "localhost";

    private Peer _peer;
    private MediaStream _localStream;
    private MediaStream _remoteStream;
    private MediaConnection _mediaConnection;

    private String _strOwnId;
    private boolean _bConnected;

    private Handler _handler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // for Theta
        notificationCameraClose();
        notificationWlanCl();



        Window wnd = getWindow();
        wnd.addFlags(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_main);

        _handler = new Handler(Looper.getMainLooper());
        final Activity activity = this;

        //
        // Initialize Peer
        //
        PeerOption option = new PeerOption();
        option.key = API_KEY;
        option.domain = DOMAIN;
        _peer = new Peer(this, option);

        //
        // Set Peer event callbacks
        //

        // OPEN
        _peer.on(Peer.PeerEventEnum.OPEN, new OnCallback() {
            @Override
            public void onCallback(Object object) {

                // Show my ID
                _strOwnId = (String) object;
                TextView tvOwnId = (TextView) findViewById(R.id.tvOwnId);
                tvOwnId.setText(_strOwnId);

                // Request permissions
                if (ContextCompat.checkSelfPermission(activity,
                        Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(activity,
                        Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO}, 0);
                } else {

                    // Get a local MediaStream & show it
                    startLocalStream();
                }

            }
        });

        // CALL (Incoming call)
        _peer.on(Peer.PeerEventEnum.CALL, new OnCallback() {
            @Override
            public void onCallback(Object object) {
                if (!(object instanceof MediaConnection)) {
                    return;
                }

                _mediaConnection = (MediaConnection) object;
                setMediaCallbacks();
                _mediaConnection.answer(_localStream);

                _bConnected = true;
                updateActionButtonTitle();
            }
        });

        _peer.on(Peer.PeerEventEnum.CLOSE, new OnCallback() {
            @Override
            public void onCallback(Object object) {
                Log.d(TAG, "[On/Close]");
            }
        });
        _peer.on(Peer.PeerEventEnum.DISCONNECTED, new OnCallback() {
            @Override
            public void onCallback(Object object) {
                Log.d(TAG, "[On/Disconnected]");
            }
        });
        _peer.on(Peer.PeerEventEnum.ERROR, new OnCallback() {
            @Override
            public void onCallback(Object object) {
                PeerError error = (PeerError) object;
                Log.d(TAG, "[On/Error]" + error);
            }
        });


        //
        // Set GUI event listeners
        //

        Button btnAction = (Button) findViewById(R.id.btnAction);
        btnAction.setEnabled(true);
        btnAction.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.setEnabled(false);

                if (!_bConnected) {

                    // Select remote peer & make a call
                    showPeerIDs();
                } else {

                    // Hang up a call
                    closeRemoteStream();
                    _mediaConnection.close();

                }

                v.setEnabled(true);
            }
        });

        Button switchCameraAction = (Button) findViewById(R.id.switchCameraAction);
        switchCameraAction.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (null != _localStream) {
                    Boolean result = _localStream.switchCamera();
                    if (true == result) {
                        //Success
                    } else {
                        //Failed
                    }
                }

            }
        });

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case 0: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startLocalStream();
                } else {
                    Toast.makeText(this, "Failed to access the camera and microphone.\nclick allow when asked for permission.", Toast.LENGTH_LONG).show();
                }
                break;
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        // Disable Sleep and Screen Lock
        Window wnd = getWindow();
        wnd.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        wnd.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Set volume control stream type to WebRTC audio.
        setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);
    }

    @Override
    protected void onPause() {
        // Set default volume control stream type.
        setVolumeControlStream(AudioManager.USE_DEFAULT_STREAM_TYPE);
        super.onPause();
    }

    @Override
    protected void onStop() {
        // Enable Sleep and Screen Lock
        Window wnd = getWindow();
        wnd.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        wnd.clearFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        destroyPeer();

        notificationSuccess();

        super.onDestroy();
    }

    //
    // Get a local MediaStream & show it
    //

    void startLocalStream() {
        if(am  == null){
            am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            am.setParameters("RicUseBFormat=false");
        }
        if (mCamera == null) {
            mCamera = Camera.open();

            mParameters = mCamera.getParameters();
            // mParameters.setPreviewSize(1920, 960);
            mParameters.set("RIC_SHOOTING_MODE", "RicStillPreview1920");
            mParameters.set("RIC_PROC_STITCHING", "RicStaticStitching");
            mParameters.set("recording-hint", "false");
            mCamera.setParameters(mParameters);

        }

        Navigator.initialize(_peer);
        MediaConstraints constraints = new MediaConstraints();
        _localStream = Navigator.getUserMedia(constraints);

        Canvas canvas = (Canvas) findViewById(R.id.svLocalView);
        _localStream.addVideoRenderer(canvas, 0);
    }

    //
    // Set callbacks for MediaConnection.MediaEvents
    //
    void setMediaCallbacks() {

        _mediaConnection.on(MediaConnection.MediaEventEnum.STREAM, new OnCallback() {
            @Override
            public void onCallback(Object object) {
                _remoteStream = (MediaStream) object;
                Canvas canvas = (Canvas) findViewById(R.id.svRemoteView);
                _remoteStream.addVideoRenderer(canvas, 0);
            }
        });

        _mediaConnection.on(MediaConnection.MediaEventEnum.CLOSE, new OnCallback() {
            @Override
            public void onCallback(Object object) {
                closeRemoteStream();
                _bConnected = false;
                updateActionButtonTitle();
            }
        });

        _mediaConnection.on(MediaConnection.MediaEventEnum.ERROR, new OnCallback() {
            @Override
            public void onCallback(Object object) {
                PeerError error = (PeerError) object;
                Log.d(TAG, "[On/MediaError]" + error);
            }
        });

    }

    //
    // Clean up objects
    //
    private void destroyPeer() {
        closeRemoteStream();

        if (null != _localStream) {
            Canvas canvas = (Canvas) findViewById(R.id.svLocalView);
            _localStream.removeVideoRenderer(canvas, 0);
            _localStream.close();
        }

        if (null != _mediaConnection) {
            if (_mediaConnection.isOpen()) {
                _mediaConnection.close();
            }
            unsetMediaCallbacks();
        }

        Navigator.terminate();

        if (null != _peer) {
            unsetPeerCallback(_peer);
            if (!_peer.isDisconnected()) {
                _peer.disconnect();
            }

            if (!_peer.isDestroyed()) {
                _peer.destroy();
            }

            _peer = null;
        }
    }

    //
    // Unset callbacks for PeerEvents
    //
    void unsetPeerCallback(Peer peer) {
        if (null == _peer) {
            return;
        }

        peer.on(Peer.PeerEventEnum.OPEN, null);
        peer.on(Peer.PeerEventEnum.CONNECTION, null);
        peer.on(Peer.PeerEventEnum.CALL, null);
        peer.on(Peer.PeerEventEnum.CLOSE, null);
        peer.on(Peer.PeerEventEnum.DISCONNECTED, null);
        peer.on(Peer.PeerEventEnum.ERROR, null);
    }

    //
    // Unset callbacks for MediaConnection.MediaEvents
    //
    void unsetMediaCallbacks() {
        if (null == _mediaConnection) {
            return;
        }

        _mediaConnection.on(MediaConnection.MediaEventEnum.STREAM, null);
        _mediaConnection.on(MediaConnection.MediaEventEnum.CLOSE, null);
        _mediaConnection.on(MediaConnection.MediaEventEnum.ERROR, null);
    }

    //
    // Close a remote MediaStream
    //
    void closeRemoteStream() {
        if (null == _remoteStream) {
            return;
        }

        Canvas canvas = (Canvas) findViewById(R.id.svRemoteView);
        _remoteStream.removeVideoRenderer(canvas, 0);
        _remoteStream.close();
    }

    //
    // Create a MediaConnection
    //
    void onPeerSelected(String strPeerId) {
        if (null == _peer) {
            return;
        }

        if (null != _mediaConnection) {
            _mediaConnection.close();
        }

        CallOption option = new CallOption();
        _mediaConnection = _peer.call(strPeerId, _localStream, option);

        if (null != _mediaConnection) {
            setMediaCallbacks();
            _bConnected = true;
        }

        updateActionButtonTitle();
    }

    //
    // Listing all peers
    //
    void showPeerIDs() {
        if ((null == _peer) || (null == _strOwnId) || (0 == _strOwnId.length())) {
            Toast.makeText(this, "Your PeerID is null or invalid.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Get all IDs connected to the server
        final Context fContext = this;
        _peer.listAllPeers(new OnCallback() {
            @Override
            public void onCallback(Object object) {
                if (!(object instanceof JSONArray)) {
                    return;
                }

                JSONArray peers = (JSONArray) object;
                ArrayList<String> _listPeerIds = new ArrayList<>();
                String peerId;

                // Exclude my own ID
                for (int i = 0; peers.length() > i; i++) {
                    try {
                        peerId = peers.getString(i);
                        if (!_strOwnId.equals(peerId)) {
                            _listPeerIds.add(peerId);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                // Show IDs using DialogFragment
                if (0 < _listPeerIds.size()) {
                    FragmentManager mgr = getFragmentManager();
                    PeerListDialogFragment dialog = new PeerListDialogFragment();
                    dialog.setListener(
                            new PeerListDialogFragment.PeerListDialogFragmentListener() {
                                @Override
                                public void onItemClick(final String item) {
                                    _handler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            onPeerSelected(item);
                                        }
                                    });
                                }
                            });
                    dialog.setItems(_listPeerIds);
                    dialog.show(mgr, "peerlist");
                } else {
                    Toast.makeText(fContext, "PeerID list (other than your ID) is empty.", Toast.LENGTH_SHORT).show();
                }
            }
        });

    }

    //
    // Update actionButton title
    //
    void updateActionButtonTitle() {
        _handler.post(new Runnable() {
            @Override
            public void run() {
                Button btnAction = (Button) findViewById(R.id.btnAction);
                if (null != btnAction) {
                    if (false == _bConnected) {
                        btnAction.setText("Make Call");
                    } else {
                        btnAction.setText("Hang up");
                    }
                }
            }
        });
    }

}
