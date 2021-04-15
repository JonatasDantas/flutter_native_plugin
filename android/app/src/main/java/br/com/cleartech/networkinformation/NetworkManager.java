package br.com.cleartech.networkinformation;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkInfo;
import android.os.Build;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Base64;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Observable;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Callable;

import javax.net.ssl.HttpsURLConnection;

import io.flutter.Log;

public class NetworkManager {
    public static int NOT_REACHABLE = 0;
    public static int REACHABLE_VIA_CARRIER_DATA_NETWORK = 1;
    public static int REACHABLE_VIA_WIFI_NETWORK = 2;

    public static final String WIFI = "wifi";
    public static final String WIMAX = "wimax";
    // mobile
    public static final String MOBILE = "mobile";

    // Android L calls this Cellular, because I have no idea!
    public static final String CELLULAR = "cellular";
    // 2G network types
    public static final String TWO_G = "2g";
    public static final String GSM = "gsm";
    public static final String GPRS = "gprs";
    public static final String EDGE = "edge";
    // 3G network types
    public static final String THREE_G = "3g";
    public static final String CDMA = "cdma";
    public static final String UMTS = "umts";
    public static final String HSPA = "hspa";
    public static final String HSUPA = "hsupa";
    public static final String HSDPA = "hsdpa";
    public static final String ONEXRTT = "1xrtt";
    public static final String EHRPD = "ehrpd";
    public static final String HSPA_PLUS = "hspa+";
    // 4G network types
    public static final String FOUR_G = "4g";
    public static final String LTE = "lte";
    public static final String UMB = "umb";
    // 5G network types (NR: New Radio)
    public static final String FIVE_G = "5g";
    public static final String NR = "nr";
    // return type
    public static final String TYPE_UNKNOWN = "unknown";
    public static final String TYPE_ETHERNET = "ethernet";
    public static final String TYPE_ETHERNET_SHORT = "eth";
    public static final String TYPE_WIFI = "wifi";
    public static final String TYPE_2G = "2g";
    public static final String TYPE_3G = "3g";
    public static final String TYPE_4G = "4g";
    public static final String TYPE_5G = "5g";
    public static final String TYPE_NONE = "none";

    private static String TYPE_OLD = "";
    private static String NETWORK_IP = "";
    private static String EXTRA_INFO = "";
    private static final int INSPECT_IP_ATTEMPT_LIMIT = 2;
    private static final int INSPECT_IP_URL_LAST = 1;
    private static int INSPECT_IP_URL_INDEX = 0;
    private static int INSPECT_IP_ACTIVE = 0;

    private static final String PLTF = "android";
    private static final String LOG_TAG = "NetworkManager";

    private static final String INSPECT_IP_AUTH = "ah:$2a$10$TugnSYOroCaTxS0IRbJqQuHh4xj0DdkQ3anMUwPiRQnyKRgI20Pye";
    private static final String[] INSPECT_IP_URL = new String[] {
        "https://homol-speedtest.eaqbr.com.br:8443/stinfo-inspect/v1/" + PLTF + "/ip/get-number",
        "https://homol-speedtest.eaqbr.com.br:8443/stinfo-inspect/v1/" + PLTF + "/ip/get-number"
    };

    ConnectivityManager sockMan;
    BroadcastReceiver receiver;
    private Map<String, String> lastInfo = null;

    private java.util.Timer networkCheckTimer;
    private volatile long lastNetworkCheck;
    private static final long NETWORK_CHECK_TIMER_PERIOD = 2500;

    private String pluginResult;
    private Activity pluginActivity;
    private EventListener eventListener;


    public NetworkManager(Activity activity, EventListener eventListener) {
        this.initialize(activity, eventListener);
    }

    /**
     * Sets the context of the Command. This can then be used to do things like
     * get file paths associated with the Activity.
     *
     * @param activity The context of the main Activity.
     */
    public void initialize(Activity activity, EventListener eventListener) {
        this.pluginActivity = activity;
        this.eventListener = eventListener;
        this.sockMan = (ConnectivityManager) activity.getSystemService(Context.CONNECTIVITY_SERVICE);

        // We need to listen to connectivity events to update navigator.connection
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);

        if (this.receiver == null) {
            this.receiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    updateConnectionInfo(sockMan.getActiveNetworkInfo());
                    lastNetworkCheck = System.currentTimeMillis();
                }
            };

            // Registering receiver for connectivity events
            activity.registerReceiver(this.receiver, intentFilter);
        }

        TelephonyManager telephonyMan = (TelephonyManager) activity.getSystemService(Context.TELEPHONY_SERVICE);
        telephonyMan.listen(new PhoneStateListener() {
            @Override
            public void onDataConnectionStateChanged(int state, int networkType) {
                super.onDataConnectionStateChanged(state, networkType);
                NetworkInfo info = sockMan.getActiveNetworkInfo();
                Log.d(LOG_TAG, "onDataConnectionStateChanged:" + state + ":"+ networkType + " type [" + getType(info) + "]");
                updateConnectionInfo(info);
                lastNetworkCheck = System.currentTimeMillis();
            }

        }, PhoneStateListener.LISTEN_DATA_CONNECTION_STATE);

        networkCheckTimer = new java.util.Timer();
        networkCheckTimer.schedule(new java.util.TimerTask() {
            public void run() {
                if (System.currentTimeMillis() - lastNetworkCheck >= NETWORK_CHECK_TIMER_PERIOD) {
                    NetworkInfo info = NetworkManager.this.sockMan.getActiveNetworkInfo();
                    Log.d(LOG_TAG, "onCheckTimer: type [" + NetworkManager.this.getType(info) + "]");
                    NetworkManager.this.updateConnectionInfo(info);
                }

                NetworkManager.this.lastNetworkCheck = System.currentTimeMillis();
            }
        }, NETWORK_CHECK_TIMER_PERIOD, NETWORK_CHECK_TIMER_PERIOD);

        // Timer to monitor network change
        activateTimer();
    }

    /**
     * Executes the request and returns PluginResult.
     *
     * @param action            The action to execute.
     * @return                  True if the action was valid, false otherwise.
     */
    public boolean execute(String action) {
        if (action.equals("getConnectionInfo")) {
            NetworkInfo info = sockMan.getActiveNetworkInfo();
            String connectionType = "";

            try {
                connectionType = this.getConnectionInfo(info).get("type").toString();
            } catch (Exception e) {
                Log.d(LOG_TAG, e.getLocalizedMessage());
            }

            this.pluginResult = connectionType;

            //setChanged();
            //notifyObservers();
            this.eventListener.onUpdateEvent(connectionType);

            return true;
        }
        return false;
    }

    /**
     * Stop network receiver.
     */
    public void onDestroy() {
        networkCheckTimer.cancel();
        if (this.receiver != null) {
            try {
                this.pluginActivity.unregisterReceiver(this.receiver);
            } catch (Exception e) {
                Log.e(LOG_TAG, "Error unregistering network receiver: " + e.getMessage(), e);
            } finally {
                receiver = null;
            }
        }
    }

    public String getPluginResult() {
        return pluginResult;
    }

    //--------------------------------------------------------------------------
    // LOCAL METHODS
    //--------------------------------------------------------------------------

    /**
     * Updates Flutter side whenever the connection changes
     *
     * @param info the current active network info
     * @return
     */
    private void updateConnectionInfo(NetworkInfo info) {
        // send update to javascript "navigator.network.connection"
        // Jellybean sends its own info
        Map<String, String> thisInfo = this.getConnectionInfo(info);
        if(!thisInfo.equals(lastInfo))
        {
            String connectionType = "";

            try {
                connectionType = thisInfo.get("type").toString();
            } catch (Exception e) {
                Log.d(LOG_TAG, e.getLocalizedMessage());
            }

            sendUpdate(connectionType);
            lastInfo = thisInfo;
        }
    }

    /**
     * Get the latest network connection information
     *
     * @param info the current active network info
     * @return a Map<String, String> that represents the network info
     */
    private Map<String, String> getConnectionInfo(NetworkInfo info) {
        String type = TYPE_NONE;
        String extraInfo = "";

        if (info != null) {
            // If we are not connected to any network set type to none
            if (!info.isConnected()) {
                type = TYPE_NONE;
            }
            else {
                type = getType(info);
            }
            extraInfo = info.getExtraInfo();
            if (extraInfo != null) {
                EXTRA_INFO = extraInfo;
            } else {
                EXTRA_INFO = "";
            }
        }

        Log.d(LOG_TAG, "Connection Type: " + type);
        Log.d(LOG_TAG, "Connection Extra Info: " + extraInfo);

        Map<String, String> connectionInfo = new HashMap<>();

        try {
            connectionInfo.put("type", type);
            connectionInfo.put("extraInfo", extraInfo);
        } catch (Exception e) {
            Log.d(LOG_TAG, e.getLocalizedMessage());
        }

        return connectionInfo;
    }

    /**
     * Set plugin result and Notify Observers
     *
     * @param type the network info to set as navigator.connection
     */
    private void sendUpdate(String type) {
        this.pluginResult = type;

        //setChanged();
        //notifyObservers();
        this.eventListener.onUpdateEvent(type);
    }

    /**
     * Determine the type of connection
     *
     * @param info the network info so we can determine connection type.
     * @return the type of mobile network we are on, with the following elements:
     *            ("type" | "rawType" | "network ip" | "extra info")
     *         For example:
     *            ("4g" | "lte" | "187.26.178.75" | "java.claro.com.br")
     */
    private String getType(NetworkInfo info) {
        String ret = _getType(info);
        if (!TYPE_OLD.equals(ret)) {
            Log.d(LOG_TAG, "InspectIp: Network change detected: from [" + TYPE_OLD + "] to [" + ret + "]");
            TYPE_OLD = ret;
            NETWORK_IP = "";
            EXTRA_INFO = "";
            if (ret != null && !ret.equals("") && !ret.equals(TYPE_NONE)) {
                INSPECT_IP_ACTIVE = 1;
            }
        }
        try {
            if (NETWORK_IP != null && !NETWORK_IP.equals("")) {
                ret = ret  + "|" + NETWORK_IP;
                if (EXTRA_INFO != null && !EXTRA_INFO.equals("")) {
                    ret = ret  + "|" + EXTRA_INFO;
                }
            }
        } catch (Exception e) {
            Log.d(LOG_TAG, "InspectIp:error: " + e.getMessage());
        }
        return ret;
    }

    private String _getType(NetworkInfo info) {
        if (info != null) {
            String rawType = info.getTypeName().toLowerCase(Locale.US);
            Log.d(LOG_TAG, "network type : " + rawType);
            if (rawType.equals(WIFI)) {
                return TYPE_WIFI + "|" + rawType;
            }
            else if (rawType.toLowerCase().equals(TYPE_ETHERNET) || rawType.toLowerCase().startsWith(TYPE_ETHERNET_SHORT)) {
                return TYPE_ETHERNET + "|" + rawType;
            }
            else if (rawType.equals(MOBILE) || rawType.equals(CELLULAR)) {
                rawType = info.getSubtypeName().toLowerCase(Locale.US);
                Log.d(LOG_TAG, "network type from subtype : " + rawType);
                if (rawType.equals(GSM) ||
                        rawType.equals(GPRS) ||
                        rawType.equals(EDGE) ||
                        rawType.equals(TWO_G)) {
                    return TYPE_2G + "|" + rawType;
                }
                else if (rawType.startsWith(CDMA) ||
                        rawType.equals(UMTS) ||
                        rawType.equals(ONEXRTT) ||
                        rawType.equals(EHRPD) ||
                        rawType.equals(HSUPA) ||
                        rawType.equals(HSDPA) ||
                        rawType.equals(HSPA) ||
                        rawType.equals(HSPA_PLUS) ||
                        rawType.equals(THREE_G)) {
                    return TYPE_3G + "|" + rawType;
                }
                else if (rawType.equals(LTE) ||
                        rawType.equals(UMB) ||
                        rawType.equals(FOUR_G)) {
                    return TYPE_4G + "|" + rawType;
                }
                else if (rawType.equals(NR) ||
                        rawType.equals(FIVE_G)) {
                    return TYPE_5G + "|" + rawType;
                }
            }
        }
        else {
            return TYPE_NONE + "|" + TYPE_NONE;
        }
        return TYPE_UNKNOWN + "|" + TYPE_UNKNOWN;
    }


    private String _inspectIp(int attempt) {
        attempt++;
        Log.d(LOG_TAG, "InspectIp:attempt(" + attempt + ")");
        boolean android_available_version = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M;
        String response = "";
        ConnectivityManager connManager = null;
        Network network = null;
        URL endpoint = null;
        HttpsURLConnection httpsConn = null;
        try {
            Log.d(LOG_TAG, "InspectIp:attempt(" + attempt + "):url: PRD");
            //LOG.d(LOG_TAG, "***** InspectIp:attempt(" + attempt + "):url: " + INSPECT_IP_URL[INSPECT_IP_URL_INDEX]);
            endpoint = new URL(INSPECT_IP_URL[INSPECT_IP_URL_INDEX]);
            if (INSPECT_IP_URL_INDEX < INSPECT_IP_URL_LAST) {
                INSPECT_IP_URL_INDEX++;
            } else {
                INSPECT_IP_URL_INDEX = 0;
            }
            connManager = (ConnectivityManager) pluginActivity.getSystemService(Context.CONNECTIVITY_SERVICE);
            httpsConn = (HttpsURLConnection) endpoint.openConnection();
            try {
                response = consumerInspectIp(httpsConn);
            } catch (Exception e) {
                response = "";
                Log.d(LOG_TAG, "InspectIp:error: " + e.getMessage());
            }
            if (httpsConn != null) {
                httpsConn.disconnect();
            }
            if (response == null || response.length() == 0) {
                if (attempt <= INSPECT_IP_ATTEMPT_LIMIT) {
                    Log.d(LOG_TAG, "InspectIp:error: Attempt (" + attempt
                            + ") for consuming the inspect ip failed, a new attempt will be made.");
                    pause(50);
                    return _inspectIp(attempt);
                } else {
                    Log.d(LOG_TAG,
                            "InspectIp:error: Attempt (" + attempt
                                    + ") for consuming the ip inspect failed. Maximum number of attempts reached.");
                }
            }
        } catch (Exception e) {
            if (attempt <= INSPECT_IP_ATTEMPT_LIMIT) {
                Log.d(LOG_TAG, "InspectIp:error: Attempt (" + attempt
                        + ") for consuming the inspect ip failed, a new attempt will be made. - " + e.getMessage());
                pause(50);
                return _inspectIp(attempt);
            } else {
                Log.d(LOG_TAG,
                        "InspectIp:error: Attempt (" + attempt
                                + ") for consuming the ip inspect failed. Maximum number of attempts reached. " + e.getMessage());
            }
        }
        try {
            if (httpsConn != null) {
                httpsConn.disconnect();
            }
        } catch (Exception e) {
            connManager = null;
            network = null;
            endpoint = null;
            httpsConn = null;
        }
        attempt = 0;
        Log.d(LOG_TAG, "InspectIp:response: " + response);
        return response;
    }

    private String consumerInspectIp(HttpsURLConnection httpsConn) throws Exception {
        String response = "";
        httpsConn.setRequestMethod("POST");
        httpsConn.setRequestProperty("Content-Type", "application/json");
        httpsConn.setRequestProperty("Accept", "text/plain");
        httpsConn.setRequestProperty("Authorization",
                "Basic " + Base64.encodeToString(INSPECT_IP_AUTH.getBytes(), Base64.NO_WRAP));
        httpsConn.setDoInput(true);
        httpsConn.setDoOutput(true);
        httpsConn.setConnectTimeout(60 * 1000);
        httpsConn.connect();

        if (httpsConn.getResponseCode() == 200) {
            InputStream inputStream = httpsConn.getInputStream();
            InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
            BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
            String responseLine = bufferedReader.readLine();
            while (responseLine != null) {
                response += responseLine;
                responseLine = bufferedReader.readLine();
            }
            Log.d(LOG_TAG, "InspectIp:60:response-ok");
        } else {
            Log.d(LOG_TAG, "InspectIp:60:error: [code:"
                    + httpsConn.getResponseCode() + "] [message:" + httpsConn.getResponseMessage() + "]");
        }
        return response;
    }

    private void activateTimer() {
        Log.d(LOG_TAG, "InspectIp: ActivateTimer started");
        Timer timer = new Timer();
        TimerTask t = new TimerTask() {
            @Override
            public void run() {
                if (INSPECT_IP_ACTIVE == 1) {
                    INSPECT_IP_ACTIVE = 0;
                    NETWORK_IP = _inspectIp(0);
                    Log.d(LOG_TAG, "InspectIp: ActivateTimer response: " + NETWORK_IP);
                }
            }
        };

        timer.scheduleAtFixedRate(t,2000,2000);
    }

    private void pause(int millis) {
        try {
            Thread.sleep(millis);
        } catch (Exception e) {
            e.getLocalizedMessage();
        }
    }
}
