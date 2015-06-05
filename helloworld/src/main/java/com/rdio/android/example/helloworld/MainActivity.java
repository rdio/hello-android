package com.rdio.android.example.helloworld;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;

import com.rdio.android.audioplayer.interfaces.AudioError;
import com.rdio.android.core.RdioApiResponse;
import com.rdio.android.sdk.OAuth2Credential;
import com.rdio.android.sdk.PlayRequest;
import com.rdio.android.sdk.PlayerListener;
import com.rdio.android.sdk.Rdio;
import com.rdio.android.sdk.RdioListener;
import com.rdio.android.sdk.RdioResponseListener;
import com.rdio.android.sdk.RdioService;
import com.rdio.android.sdk.activity.OAuth2WebViewActivity;
import com.rdio.android.sdk.model.Track;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Arrays;

public class MainActivity extends ActionBarActivity implements RdioListener {
    final private String LOG_NAME = "helloRdio";
    private TextView timerInfo;
    private TextView userInfo;
    private TextView trackInfo;
    private SeekBar timeProgress;
    private RdioService apiService;
    private Rdio rdio;
    /**
     * The redirectURI is included in the "Redirect URIs" over at
     * http://www.rdio.com/developers/app/YOUR_CLIENT_ID/
     * <p/>
     * NOTE : there's a bug that requires you to include a trailing / or things break
     */
//    final private String REDIRECT_URI = "http://bar.com/";
//    final private String CLIENT_ID = "YOUR_ID";
//    final private String CLIENT_SECRET = "YOUR_SECRET";
    private static final int AUTH_ACTIVITY_REQUEST_CODE = 5000;
    private boolean userIsSeeking = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        userInfo = (TextView) findViewById(R.id.current_user_id);
        trackInfo = (TextView) findViewById(R.id.current_track_info);
        timerInfo = (TextView) findViewById(R.id.current_time_info);
        timeProgress = (SeekBar) findViewById(R.id.time_progress_bar);
        timeProgress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            int lastSeenSeek = 0;

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    Log.i(LOG_NAME, "Seeking to > " + progress + " (userIsSeeking)");
                    lastSeenSeek = progress;
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                Log.i(LOG_NAME, " --- START tracking touch");
                userIsSeeking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                Log.i(LOG_NAME, " --- STOP tracking touch");
                userIsSeeking = false;
                rdio.getPlayerManager().seekTo(lastSeenSeek);
            }
        });

        SharedPreferences pref = getPreferences(Context.MODE_PRIVATE);
        OAuth2Credential credential = null;
        if (pref.contains("saved_credential_access")) {
            Log.i(LOG_NAME, "Saved credentials were found & will be used");
            credential = new OAuth2Credential(
                    pref.getString("saved_credential_access", null),
                    pref.getString("saved_credential_refresh", null),
                    pref.getLong("saved_credential_time", 0));
        }
        rdio = new Rdio(CLIENT_ID, CLIENT_SECRET, credential, getApplicationContext(), this);
        rdio.requestApiService();
    }

    public void onApiServiceReady(RdioService apiService) {
        this.apiService = apiService;
        rdio.prepareForPlayback();
        rdio.getPlayerManager().addPlayerListener(new PlayerListener() {
            @Override
            public void onPlayStateChanged(PlayState playState) {
                Log.i(LOG_NAME, ">>> MainActivity onPlayStateChanged " + playState);
                if (playState == PlayState.Playing) {
                    trackInfo.setBackgroundColor(0xff5cd2ff);
                } else {
                    trackInfo.setBackgroundColor(0x005cd2ff);
                }
                TextView playStateView = (TextView) findViewById(R.id.play_state);
                playStateView.setText(rdio.getPlayerManager().getState().toString());
            }

            @Override
            public void onPositionUpdate(int mSec) {
                Log.i(LOG_NAME, ">>> MainActivity onPositionUpdate " + mSec);
                if (mSec > 0 && !userIsSeeking) {
                    timeProgress.setVisibility(View.VISIBLE);
                    timerInfo.setText("Track is at " + rdio.getPlayerManager().getCurrentPosition() +
                            " of " + rdio.getPlayerManager().getCurrentDuration() + " (i was told " + mSec + ")");
                    timeProgress.setMax(rdio.getPlayerManager().getCurrentDuration());
                    timeProgress.setProgress(rdio.getPlayerManager().getCurrentPosition());
                }
            }

            @Override
            public void onPrepared() {
                Log.i(LOG_NAME, ">>> MainActivity onPrepared. We can now access " + rdio.getPlayerManager().getCurrentTrack());
                Track t = rdio.getPlayerManager().getCurrentTrack();
                trackInfo.setText("'" + t.name + "'[" + t.key + "] from" +
                        " '" + t.albumName + "'[" + t.albumKey + "] by" +
                        " '" + t.artistName + "'[" + t.artistKey + "]");
            }

            @Override
            public void onComplete() {
                Log.i(LOG_NAME, ">>> MainActivity onComplete");
                timerInfo.setText("No track playing");
                timeProgress.setVisibility(View.GONE);
                TextView toggleInfo = (TextView) findViewById(R.id.play_pause_button);
                toggleInfo.setText("Play/Pause");
            }

            @Override
            public void onSeekStarted() {
                Log.i(LOG_NAME, ">>> MainActivity onSeekStarted");
                timeProgress.setBackgroundColor(0xffff62ad);
            }

            @Override
            public void onSeekCompleted() {
                Log.i(LOG_NAME, ">>> MainActivity onSeekCompleted " + rdio.getPlayerManager().getCurrentPosition() + " / " + rdio.getPlayerManager().getCurrentDuration());
                timeProgress.setBackgroundColor(0x00ffffff);
            }

            @Override
            public void onError(AudioError error) {
                Log.i(LOG_NAME, ">>> MainActivity onError " + error + " : " + error.getDescription());
            }

            @Override
            public void onBufferingStarted() {
                Log.i(LOG_NAME, ">>> MainActivity onBufferingStarted");
                timerInfo.setBackgroundColor(0xffff7828);
            }

            @Override
            public void onBufferingEnded() {
                Log.i(LOG_NAME, ">>> MainActivity onBufferingEnded");
                timerInfo.setBackgroundColor(0x00ffffff);
            }
        });
    }

    public void handleSeek(View source) {
        EditText seekText = (EditText) findViewById(R.id.seek_position_input);
        if (seekText.getText().length() > 0) {
            int position = Integer.parseInt(seekText.getText().toString());
            rdio.getPlayerManager().seekTo(position);
        }

    }

    public void playPauseToggle(View source) {
        TextView toggleInfo = (TextView) findViewById(R.id.play_pause_button);
        if (rdio.getPlayerManager().isPlaying()) {
            rdio.getPlayerManager().pause();
            toggleInfo.setText("is paused");
        } else {
            rdio.getPlayerManager().play();
            toggleInfo.setText("is playing");
        }
    }

    public void loadAudio(View source) {
        EditText keyText = (EditText) findViewById(R.id.source_key_new);
        EditText indexText = (EditText) findViewById(R.id.source_index);
        EditText offsetText = (EditText) findViewById(R.id.time_offset);
        String key = (keyText.getText().length() > 0) ? keyText.getText().toString() : "t1";
        int sourceIndex = (indexText.getText().length() > 0) ? Integer.parseInt(indexText.getText().toString()) : 0;
        int timeOffset = (offsetText.getText().length() > 0) ? Integer.parseInt(offsetText.getText().toString()) : 0;
        try {
            rdio.getPlayerManager().play(new PlayRequest(key, sourceIndex, timeOffset));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void stopAudio(View source) {
        rdio.getPlayerManager().stop();
    }
    public void onPrev(View source) {
        rdio.getPlayerManager().skipPrev();
    }
    public void onNext(View source) {
        rdio.getPlayerManager().skipNext();
    }

    public void handleClearCreds(View source) {

        SharedPreferences pref = getPreferences(Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = pref.edit();
        editor.remove("saved_credential_access");
        editor.remove("saved_credential_refresh");
        editor.remove("saved_credential_time");
        editor.commit();
        Log.i(LOG_NAME, "Saved user credentials removed, user will be anonymous on next app launch");
    }

    public void handleClear(View source) {
        rdio.cleanup();
        Log.i(LOG_NAME, "Rdio instance has been cleaned up. Don't expect anything to work now.");
    }

    public void login(View source) {
        try {
            Log.i(LOG_NAME, "Trying to authorize the user....");
            Intent myIntent = new Intent(MainActivity.this, OAuth2WebViewActivity.class);
            myIntent.putExtra(OAuth2WebViewActivity.EXTRA_AUTH_URL, rdio.getAuthUrl(REDIRECT_URI));
            myIntent.putExtra(OAuth2WebViewActivity.EXTRA_REDIRECT_URI, REDIRECT_URI);
            startActivityForResult(myIntent, AUTH_ACTIVITY_REQUEST_CODE);
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(LOG_NAME, "We hit some totes random error when tyring to do old login " + e);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == AUTH_ACTIVITY_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            Log.i(LOG_NAME, "Passing along intent results for user auth");
            apiService.processWebViewActivity(data, REDIRECT_URI);
        } else if (requestCode == 1 && resultCode == RESULT_CANCELED) {
            Log.v(LOG_NAME, "User decided not to auth with the app");
        }
    }

    @Override
    public void onRdioReadyForPlayback() {
        Log.i(LOG_NAME, "Rdio SDK is ready for playback!!");
    }

    @Override
    public void onRdioUserPlayingElsewhere() {
        Log.i(LOG_NAME, "Rdio is now playing elsewhere, our music has been paused");
    }


    @Override
    public void onRdioAuthorised(OAuth2Credential credential) {
        SharedPreferences pref = getPreferences(Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = pref.edit();
        editor.putString("saved_credential_access", credential.accessToken);
        editor.putString("saved_credential_refresh", credential.refreshToken);
        editor.putLong("saved_credential_time", credential.expirationTimeMSec);
        editor.commit();

        rdio.prepareForPlayback();
        userInfo.setText("... calculating ...");

        apiService.currentUser(new RdioResponseListener() {
            @Override
            public void onResponse(RdioApiResponse response) {
                if (response.isSuccess()) {
                    JSONObject user = response.getResult();
                    try {
                        userInfo.setText("Authed as `" + user.getString("firstName") + " " + user.getString("lastName") + "`");
                    } catch (JSONException e) {
                        userInfo.setText("Authed -- error reading response");
                    }
                } else {
                    userInfo.setText("No user currently authorized");
                }
            }
        });
    }

    @Override
    public void onError(Rdio.RdioError error, String message) {
        Log.e(LOG_NAME, "Oh no, we just got an error : " + error + " w/ msg " + message);
    }

}
