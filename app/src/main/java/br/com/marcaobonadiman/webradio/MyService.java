package br.com.marcaobonadiman.webradio;

import static br.com.marcaobonadiman.webradio.App.CHANNEL_ID;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.MediaPlayer;
import android.net.wifi.WifiManager;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.io.IOException;

public class MyService extends Service implements MediaPlayer.OnPreparedListener, MediaPlayer.OnErrorListener {

    private int autoPlay = 0;
    private int sleep = 0;
    private int tempoDecorrido = 0;
    private static String estacaoUrl;
    private int ultAcao = 0;
    public static int acao = 0;   // 0-Nada, 1-Play, 2-PowerOff, 3-Pause, 4-Stop 5-Troca, 6-Troca + Play, 7-Sleep+30
    private static boolean serviceAtivo = false;
    public static MediaPlayer mediaPlayer = null;
    WifiManager.WifiLock wifiLock;

    // Função para receber o valor da URL (Estação de uma rádio) definida no MAinActivity
    public static void setURL(String url) {
        estacaoUrl = url;
    }
    public static void setServiceAtivo(boolean flag) {
        serviceAtivo = flag;
    }
    public static boolean getServiceAtivo() {
        return serviceAtivo;
    }
    public static void setAcao(int novaAcao) {
        acao = novaAcao;
    }
    public static boolean getIsPlaying() {
        return mediaPlayer.isPlaying();
    }

    @Override
    public void onCreate() {
        super.onCreate();

        // Não esquecer de declarar no manifest: android:name=".App"  logo abaixo do  "<application"
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("WebRádios")
                .setContentText(estacaoUrl == null ? "" : estacaoUrl)
                .setSmallIcon(R.drawable.ic_baseline_radio_24)
                .setContentIntent(pendingIntent)
                .build();


        startForeground(1, notification);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        //Log.e("MyService", " onStartCommand");
        mediaPlayer = new MediaPlayer();
        mediaPlayer.setWakeMode(getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK);
        mediaPlayer.setOnErrorListener(this);
        mediaPlayer.setOnPreparedListener(this);
        wifiLock = ((WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE)).createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "mylock");
        wifiLock.acquire();
        try {
            new Thread(() -> {
                try {
                    long tempo  = System.currentTimeMillis();
                    long minuto = System.currentTimeMillis();
                    while (serviceAtivo) {
                        //1-Play, 2-PowerOff, 3-Pause, 4-Stop 5-Troca, 6-Troca + Play, 7-Sleep+30, 8-Sleep=0
                        if (acao != 0) {
                            ultAcao = 0;
                            //Log.e("Ação", " -> " + acao+"  "+estacaoUrl);
                            if (acao == 1) { // Play
                                if (!mediaPlayer.isPlaying()) {
                                    mediaPlayer.start();
                                    ultAcao = acao;
                                }
                            } else if (acao == 2) { // PowerOff
                                if (mediaPlayer.isPlaying()) {
                                    mediaPlayer.stop();
                                    mediaPlayer.reset();
                                    ultAcao = acao;
                                }
                            } else if (acao == 3) { // Pause
                                if (mediaPlayer.isPlaying()) {
                                    mediaPlayer.pause();
                                    ultAcao = acao;
                                }
                            } else if (acao == 4) { // Stop
                                if (mediaPlayer.isPlaying()) {
                                    mediaPlayer.stop();
                                    mediaPlayer.reset();
                                    ultAcao = acao;
                                }
                            } else if (acao == 44) { // Stop sem enviar status de Stop
                                if (mediaPlayer.isPlaying()) {
                                    mediaPlayer.stop();
                                    mediaPlayer.reset();
                                }
                            } else if (acao == 5 || acao == 6) { // 5-Troca de Estação e 6-Troca e Play
                                //SystemClock.sleep(1000);
                                autoPlay = 0;
                                if (acao == 6) autoPlay = 1;
                                try {
                                    if (estacaoUrl != null && !estacaoUrl.isEmpty()) {
                                        if (mediaPlayer.isPlaying()) {
                                            mediaPlayer.stop();
                                        }
                                        mediaPlayer.reset();
                                        mediaPlayer.setDataSource(estacaoUrl);
                                        mediaPlayer.prepareAsync();
                                        ultAcao = acao;
                                    }
                                } catch (IOException e) {
                                    //Log.e("Erro", e.getMessage());
                                }
                            } else if (acao == 7) { // Sleep+30
                                if (sleep == 0) tempoDecorrido = 0;
                                sleep += 30;
                            } else if (acao == 8) { // Sleep=0
                                sleep = 0;
                                tempoDecorrido = 0;
                            }
                            tempo = System.currentTimeMillis()-1010;
                            acao = 0;
                        }
                        if (sleep > 0) {
                            if (System.currentTimeMillis() - minuto > (1000 * 60)) {
                                tempoDecorrido++;
                                if (tempoDecorrido >= sleep) {
                                    if (mediaPlayer.isPlaying()) {
                                        mediaPlayer.stop();
                                        mediaPlayer.reset();
                                    }
                                    acao = 2; // PowerOff
                                    tempo = System.currentTimeMillis()+2000;
                                }
                                minuto = System.currentTimeMillis();
                            }
                        }
                        if (System.currentTimeMillis() - tempo > 1000) {
                            sendBroadcast();
                            tempo = System.currentTimeMillis();
                        }
                        SystemClock.sleep(10); // Reduz o uso de CPU
                    } // End While
                    try {
                        if (mediaPlayer != null) {
                            mediaPlayer.release();
                            mediaPlayer = null;
                        }
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();

        } catch (Exception e) {
            e.printStackTrace();
        }
        return START_NOT_STICKY;
    }

    private void sendBroadcast() {
        Intent intent = new Intent(MainActivity.mBroadcast);
        intent.putExtra("ultAcao", ultAcao);
        intent.putExtra("sleep", sleep);
        intent.putExtra("tempoDecorrido", tempoDecorrido);
        LocalBroadcastManager.getInstance(getBaseContext()).sendBroadcast(intent);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onPrepared(MediaPlayer mediaPlayer) {
        if (autoPlay == 1) {
            if(mediaPlayer!=null && !mediaPlayer.isPlaying()){
                mediaPlayer.start();
            }
        }
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        return false;
    }
}
