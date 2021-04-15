package com.example.flutter_native_code;

import android.app.Activity;

import androidx.annotation.NonNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;

import br.com.cleartech.networkinformation.EventListener;
import br.com.cleartech.networkinformation.NetworkManager;
import io.flutter.Log;
import io.flutter.embedding.android.FlutterActivity;
import io.flutter.embedding.engine.FlutterEngine;
import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.MethodChannel;

public class MainActivity extends FlutterActivity implements Observer {
    private static final String CHANNEL = "NETWORK_INFORMATION";

  @Override
  public void configureFlutterEngine(@NonNull FlutterEngine flutterEngine) {
  super.configureFlutterEngine(flutterEngine);
  final Activity activity = this;

  new EventChannel(flutterEngine.getDartExecutor().getBinaryMessenger(), CHANNEL)
          .setStreamHandler(
                  new EventChannel.StreamHandler() {
                      NetworkManager manager;
                      EventListener listener;
                      int counter = 0;

                      @Override
                      public void onListen(Object args, EventChannel.EventSink events) {
                          EventListener listener = new EventListener() {
                              @Override
                              public void onUpdateEvent(String data) {
                                  Map<String, String> result = new HashMap<>();
                                  result.put("resultado" + counter++, data);

                                  runOnUiThread(() -> {
                                    events.success(result);
                                  });
                              }
                          };

                          this.listener = listener;
                          this.manager = new NetworkManager(activity, listener);
                          this.manager.execute("getConnectionInfo");
                      }

                      @Override
                      public void onCancel(Object args) {
                          this.manager.onDestroy();
                          System.out.println("stopped netowrk manager");
                      }
                  }
          );

    /*
    new MethodChannel(flutterEngine.getDartExecutor().getBinaryMessenger(), CHANNEL)
        .setMethodCallHandler(
          (call, result) -> {
              if (call.method.equals("getNetworkInfo")) {
                  //Map<String, String> data = new HashMap<>();
                  //data.put("resultado", "aaaa rolou");
                  //result.success(data);

                  NetworkManager plugin = new NetworkManager(this);
                  boolean startedSuccesfully = plugin.execute("getConnectionInfo");
                  System.out.println("start result : " + startedSuccesfully);
              } else {
                  result.notImplemented();
              }
          }
        );

     */
  }

  @Override
  public void update(Observable networkManagerSubject, Object arg1) {
    System.out.println("implementeddddd");
    Log.d("teste", "deu certoooo");
  }
}
