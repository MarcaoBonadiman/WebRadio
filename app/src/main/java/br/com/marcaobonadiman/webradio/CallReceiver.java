package br.com.marcaobonadiman.webradio;

import static br.com.marcaobonadiman.webradio.MyService.getIsPlaying;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telephony.TelephonyManager;

public class CallReceiver extends BroadcastReceiver
{
    private boolean isPlaying = false;
    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
            if(state.equals(TelephonyManager.EXTRA_STATE_RINGING)){

            }else if(state.equals(TelephonyManager.EXTRA_STATE_OFFHOOK)){
                if (getIsPlaying()){
                    isPlaying = false;
                    MyService.setAcao(3); // Coloca em pause
                }

            }else if(state.equals(TelephonyManager.EXTRA_STATE_IDLE)){
                if(isPlaying) {
                    MyService.setAcao(1); // Volta a tocar a musica
                }
            }
        }
        catch (Exception e){
            e.printStackTrace();
        }
    }
}