package br.com.marcaobonadiman.webradio;

import static br.com.marcaobonadiman.webradio.MyService.getIsPlaying;
import static br.com.marcaobonadiman.webradio.MyService.getServiceAtivo;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.os.Process;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Objects;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    public static final String mBroadcast = "com.example.webradios";

    private String estacaoUrl = "";
    private String estacaoNome = "";
    private TextView tvEstacao, tvTitulo;
    ImageButton ibtnAdd, ibtnEdit, ibtnDel, ibtnPlay, ibtnStop, ibtnPower, ibtnSleep;
    private int ponteiroEstacao = -1;
    final private Context context = this;
    private SQLiteDatabase db;
    private ArrayList<radioData> listRadios;
    private LinearLayout layout;
    private int ultAcao = 0;
    private int sleep = 0;
    int tempoDecorrido = 0;
    private boolean estacaoForaAr = false;
    private String expEstacao="";

    private PermissionsHelper permissionsHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT); // Fixa a activity sempre nesta orientação

        // Monta a Toolbar
        Toolbar myToolbar = findViewById(R.id.my_toolbar);
        setSupportActionBar(myToolbar);
        Objects.requireNonNull(getSupportActionBar()).setTitle(R.string.app_name);

        db = new SQLiteCore(context).getWritableDatabase(); // Inicia/Cria o data base se não existe

        checkPermissions(); // Verifica as Permissões

        tvTitulo = findViewById(R.id.textViewTit);
        tvTitulo.setText("");

        tvEstacao = findViewById(R.id.textViewEstacao);
        tvEstacao.setText("");

        layout = findViewById(R.id.linearLayoutButtons);
        layout.setOrientation(LinearLayout.VERTICAL);

        ibtnPlay = findViewById(R.id.ibtnPlay);  // Botão Play
        ibtnPlay.setOnClickListener(view -> {
            if (ponteiroEstacao>-1){
                if (!getIsPlaying()){
                    if (ultAcao==3) {  // 3-Pause
                        MyService.setAcao(1);
                    }else{
                        ConnectarEstacao();
                    }
                    ibtnPlay.setImageResource(R.drawable.ic_baseline_pause_circle_outline_24);
                }else{
                    ibtnPlay.setImageResource(R.drawable.ic_baseline_play_circle_outline_24);
                    MyService.setAcao(3); // Pause
                }
            }
        });

        ibtnStop = findViewById(R.id.ibtnStop); // Botão Stop
        ibtnStop.setOnClickListener(view -> {
            if (ponteiroEstacao>-1){
                if (getIsPlaying()){
                    MyService.setAcao(4);
                    ibtnPlay.setImageResource(R.drawable.ic_baseline_play_circle_outline_24);
                }
            }
        });

        ibtnPower = findViewById(R.id.ibtnPower); // Botão Power Off
        ibtnPower.setOnClickListener(view -> {
            if (getIsPlaying()) {
                MyService.setAcao(2);
            }
            SystemClock.sleep(10);
            stopService();
        });

        ibtnSleep = findViewById(R.id.ibtnSleep); // Botão Sleep a cada toque (Curto) acrescenta 30 minutos
        ibtnSleep.setOnClickListener(view -> {
            MyService.setAcao(7); // Sleep+30
        });

        ibtnSleep.setOnLongClickListener(view -> { // Botão Sleep um toque (Longo) limpa a função Sleep
            if (sleep > 0) {
                MyService.setAcao(8); // Sleep=0
            }
            return  true;
        });

        ibtnAdd = findViewById(R.id.ibtnAdd);  // Botão para adicionar nova Estação
        ibtnAdd.setOnClickListener(view -> AddEstacao("","", "","New station"));

        ibtnEdit = findViewById(R.id.ibtnEdit); // Botão para editar uma Estação
        ibtnEdit.setOnClickListener(view -> {
            if (ponteiroEstacao != -1){
                AddEstacao(estacaoNome,estacaoUrl,String.valueOf(ponteiroEstacao),"Edit station");
            }
        });

        ibtnDel = findViewById(R.id.ibtnDelete); // Botão para apagar uma Estação
        ibtnDel.setOnClickListener(view -> {
            if (ponteiroEstacao != -1){
                if (!getIsPlaying()) {
                    deleteStation(estacaoNome,String.valueOf(ponteiroEstacao));
                }else{
                    Toast toast = Toast.makeText(context,"Station is Playing", Toast.LENGTH_LONG);toast.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL, 0, 0); toast.show();
                }
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        MyService.mainActivityIsActive = true;
        // Ligo a comunicação do Serviço com o MainActivity
        try {
            LocalBroadcastManager.getInstance(this).registerReceiver(myReceiver, new IntentFilter(mBroadcast));
        }catch (Exception e){
            e.printStackTrace();
        }

        addButoes(); // Cria os Botões programaticamente
        if (!getServiceAtivo()) {
            startService();  // Inicia o Serviço de MediaPlayer
            if (RecuperaPonteiro()){  // Recupera a última estação que estava escutando quando o app foi finalizado
                ConnectarEstacao(); // Coloca a estação para tocar
            }
        }else{
            RecuperaPonteiro();
        }
    }

    boolean RecuperaPonteiro(){ // Ler do banco de dados a última estação que estava tocando ao sair do app
        openDB();
        Cursor cursor = db.rawQuery("SELECT ponteiro FROM tb_ctrls WHERE id=1",null);
        if (cursor != null && cursor.getCount()==1){
            cursor.moveToFirst();
            ponteiroEstacao = cursor.getInt(0);
            try {
                for (int k = 0;k<listRadios.size();k++){
                    if (ponteiroEstacao==listRadios.get(k).id){
                        estacaoNome = listRadios.get(k).nome;
                        estacaoUrl = listRadios.get(k).url;
                        return true;
                    }
                }
            }catch (Exception ex){
                Log.e("Erro: ",ex.getMessage());
            }
            cursor.close();
        }
        return false;
    }

    @Override
    protected void onStop() {
        super.onStop();
        MyService.mainActivityIsActive = false;
        Sair();
    }

    @Override
    protected void onPause  () {
        super.onPause();
        delButoes();
    }

    public void startService() { // Inicializa o serviço
        MyService.setServiceAtivo(true);
        Intent serviceIntent = new Intent(this, MyService.class);
        ContextCompat.startForegroundService(this, serviceIntent);
    }

    public void stopService() { // Finaliza o serviço (Quando o PowerOff for pressionado)
        // Desligo a comunicação do Serviço com o MainActivity
        Sair();
        MyService.setServiceAtivo(false); // Interrompe o loop (Threads do Serviço)
        Intent serviceIntent = new Intent(this, MyService.class); // Finaliza o serviço
        stopService(serviceIntent);
        finish();
        System.exit(0);
        Process.killProcess(Process.myPid());
    }
    private void Sair(){
        try {
            LocalBroadcastManager.getInstance(this).unregisterReceiver( myReceiver);
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (ponteiroEstacao>-1){ // Se estiver tocando uma estação, grava no banco de dados o seu ponteiro
            ContentValues valores = new ContentValues();
            valores.put("ponteiro", ponteiroEstacao);
            openDB();
            db.update("tb_ctrls",valores,"id = ?",new String[]{"1"});
        }
        db.close();
    }

    private void openDB(){ // Abre o banco de dados se não estiver aberto
        if (db==null || !db.isOpen() ){
            db = new SQLiteCore(context).getWritableDatabase();
        }
    }

    // Funcão que recebe os dados enviado pelo comando "LocalBroadcast" lá do serviço e também da função TestURL()
    final private BroadcastReceiver myReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String url = intent.getStringExtra("url");
            if (url!=null){ // Se "url" for diferente de null, é porque a função TestURL() enviou dados, caso contrário foi o Serviço que enviou
                expEstacao = getString(R.string.connecting);
                //Log.e("URL connection",url);
                if(url.equals("OK")){  // Se a estação está ativa e online, informa o serviço para preparar e tocar a estação selecionada.
                    estacaoForaAr = false;
                    MyService.setURL(estacaoUrl);
                    tvTitulo.setText(estacaoNome);
                    MyService.setAcao(6);
                }else{ // Avisa que a estação está fora de Ar
                    estacaoForaAr = true;
                    tvEstacao.setText(R.string.off_the_air);
                }
            }else if(!estacaoForaAr){
                // Recebe informações passada pelo Serviço a cada 1 segundo
                ultAcao        = intent.getIntExtra("ultAcao",0);
                sleep          = intent.getIntExtra("sleep",0);
                tempoDecorrido = intent.getIntExtra("tempoDecorrido",0);
                //Log.e("onReceive",ultAcao+", "+sleep+", "+tempoDecorrido+", "+url);

                // Se foi colocado em Sleep e o tempo já se esgotou.
                if (sleep>0 &&  tempoDecorrido >= sleep || ultAcao == 2) { // ultAcao == 2 PowerOff
                    stopService();
                }

                if (getIsPlaying()){ // Se estiver tocando, exibe o nome da estção e a açao (Play)
                    tvTitulo.setText(estacaoNome);
                    expEstacao = getString(R.string.play);
                    ibtnPlay.setImageResource(R.drawable.ic_baseline_pause_circle_outline_24);
                }else{
                    if(ponteiroEstacao==-1){ // Se o ponteiro for -1 solicita a esolha de uma estação
                        tvTitulo.setText(R.string.select_radio);
                        expEstacao = "";
                    }else {
                        if (ultAcao == 3){  // Exibe que está em Pause
                            expEstacao = getString(R.string.pause);
                        }
                        else if (ultAcao == 4){ // Exibe que está em Stop
                            expEstacao = getString(R.string.stop);
                        }
                    }
                }
                String expSleep="";
                if (sleep>0){ // Se o valor do Sleep for maior que Zero, mostra quanto tempo o app vai ser finalizado
                    if (sleep>60){
                        expSleep = ", turn off in "+formatHoursAndMinutes((sleep - tempoDecorrido))+ " minutes";
                    }
                    else {
                        expSleep = ", turn off in "+(sleep - tempoDecorrido) + " minutes";
                    }
                }
                String expC = expEstacao+expSleep;
                tvEstacao.setText(expC);
            }
        }
    };

    private void ConnectarEstacao(){ // Conectar na estação
        expEstacao = getString(R.string.connecting);
        tvEstacao.setText(expEstacao);
        TestURL(estacaoUrl); // Antes, testa se a estação está ativa e online. Esta função retorna "OK" ou "ERR"
    }

    // Botoes do Menu - Escuta se algum dos botões das estações foi pressionado
    @Override
    public void onClick(View view) {
        Button b = (Button) view;
        int idx = -1;
        for (int k=0;k<listRadios.size();k++){
            if (listRadios.get(k).nome.contentEquals(b.getText())){
                idx=k;
            }
        }
        if (idx>-1){ // Se alguma estação foi selecionada, envia o comando de Stop (Sem atualizar o status da tela) e prepara para tocar a estação selecionada
            MyService.setAcao(44); // Stop sem Status
            estacaoNome = listRadios.get(idx).nome;
            estacaoUrl = listRadios.get(idx).url;
            ponteiroEstacao = listRadios.get(idx).id;
            tvTitulo.setText(listRadios.get(idx).nome);
            MyService.setURL(estacaoUrl);
            ibtnPlay.setImageResource(R.drawable.ic_baseline_pause_circle_outline_24);
            ConnectarEstacao();
            //Log.e("onClick",estacaoNome+" - "+estacaoUrl);
        }
    }

    private void addButoes(){  // Cria os botões programaticamente
        openDB();
        Cursor cursor = db.rawQuery("SELECT id,nome,url FROM tb_radios Order By id ",null);
        listRadios = new ArrayList<>();
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        layoutParams.setMargins(10, 20, 10, 0);
        while(cursor.moveToNext()){
            radioData rd = new radioData();
            rd.id = cursor.getInt(0);
            rd.nome = cursor.getString(1);
            rd.url = cursor.getString(2);
            listRadios.add(rd);

            Button btnTag = new Button(this);
            btnTag.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            btnTag.setText(rd.nome);
            btnTag.setTextColor(ContextCompat.getColor(this,R.color.black));
            btnTag.setBackgroundColor(ContextCompat.getColor(this,R.color.orange));
            btnTag.setId(rd.id);
            btnTag.setOnClickListener(this);
            layout.addView(btnTag,layoutParams);
        }
        cursor.close();
    }

    private void delButoes(){ // Apaga os botões (Tem de apagar para não haver duplicação das estações ao re-entrar no app)
        openDB();
        Cursor cursor = db.rawQuery("SELECT id,nome,url FROM tb_radios Order By nome ",null);
        if (cursor.getCount()>0) {
            while (cursor.moveToNext()) {
                int val = cursor.getInt(0);
                View v = findViewById(val);
                ((ViewManager) v.getParent()).removeView(v);
            }
        }
        cursor.close();
    }


    // Adiciona uma nova estação ao banco de dados
    private void AddEstacao(final String _nome,final String _url,final String _id, String titulo){
        LayoutInflater inflater = (LayoutInflater) this.getSystemService(LAYOUT_INFLATER_SERVICE);
        View layout = inflater.inflate(R.layout.popupwindow , findViewById(R.id.popup_element));

        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        int height = displayMetrics.heightPixels;
        int width = displayMetrics.widthPixels;

        final PopupWindow popupWin = new PopupWindow(layout, width-50, height/2, true);
        popupWin.showAtLocation(layout, Gravity.CENTER, 0, 0);

        TextView tvTit = layout.findViewById(R.id.textViewTit);
        tvTit.setText(titulo);

        final EditText etNome = layout.findViewById(R.id.editTextNome);
        if(!_nome.isEmpty()){
            etNome.setText(_nome);
        }
        final EditText etUrl = layout.findViewById(R.id.editTextUrl);
        if(!_url.isEmpty()){
            etUrl.setText(_url);
        }else{
            String url = "http://";
            etUrl.setText(url);
        }

        Button btGrv = layout.findViewById(R.id.buttonGravar);
        btGrv.setOnClickListener(view -> {
            String nome = etNome.getText().toString();
            String url  = etUrl.getText().toString();
            if (!nome.isEmpty() && !url.isEmpty()){
                try {
                    delButoes();
                    nome = nome.replace(",",".");
                    url = url.replace(",",".");
                    ContentValues valores = new ContentValues();
                    valores.put("nome", nome);
                    valores.put("url", url);
                    openDB();
                    if (_nome.isEmpty() && _url.isEmpty()) {
                        db.insert("tb_radios", null, valores);
                    }else{
                        db.update("tb_radios",valores,"id = ?",new String[]{""+_id});
                    }
                    addButoes();
                    popupWin.dismiss();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        Button btCan = layout.findViewById(R.id.buttonCancelar);
        btCan.setOnClickListener(view -> popupWin.dismiss());
    }

    // Exclui uma estação do banco de dados
    private void deleteStation(final String nome, final String id ){
        new AlertDialog.Builder(context)
                .setTitle("Delete")
                .setMessage("Delete: "+nome+"?")
                .setPositiveButton("Yes", (dialog, which) -> {
                    try {
                        delButoes();
                        openDB();
                        db.delete("tb_radios","id = ?",new String[]{""+id});
                        addButoes();
                        ponteiroEstacao = -1;
                        tvEstacao.setText("");
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                })
                .setNegativeButton("No", (dialog, which) -> {
                })
                .setIcon(R.drawable.ic_baseline_warning_24)
                .show();
    }


    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {  // Verifica se o key-Back foi pressionado
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (!getIsPlaying()){ // Se não estiver tocando, desliga o serviço
                stopService();
            }else{
                finish();  // Sai do app e continua tocando a estação
            }
            return true;
        } else {
            return super.onKeyDown(keyCode, event);
        }
    }

    // Função que testa se a URL da estação está online
    private void TestURL(String _url){
        Thread thread = new Thread(() -> {
            boolean flag = false;
            try {
                URL url = new URL(_url);
                HttpURLConnection huc = (HttpURLConnection) url.openConnection();
                try {
                    huc.setRequestMethod("HEAD");
                    int responseCode = huc.getResponseCode();
                    if (responseCode==200 || huc.getResponseCode()==400) flag = true;
                } catch (ProtocolException e) {
                    e.printStackTrace();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            Intent intent = new Intent(MainActivity.mBroadcast);
            if (flag) intent.putExtra("url", "OK");
            else intent.putExtra("url", "ERR");
            LocalBroadcastManager.getInstance(getBaseContext()).sendBroadcast(intent);
        });
        thread.start();
    }

    // Converte para Horas e Minutos um valor superior a 60 minutos
    public String formatHoursAndMinutes(int totalMinutes) {
        String minutes = Integer.toString(totalMinutes % 60);
        minutes = minutes.length() == 1 ? "0" + minutes : minutes;
        return (totalMinutes / 60) + ":" + minutes;
    }

    // Verificação das Permissões
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        permissionsHelper.onRequestPermissionsResult(this, requestCode, permissions, grantResults);
    }

    // Quais permissões devem ser solicitadas
    private void checkPermissions() {
        permissionsHelper = new PermissionsHelper();
        permissionsHelper.checkAndRequestPermissions(this,
                android.Manifest.permission.WAKE_LOCK,
                android.Manifest.permission.INTERNET,
                android.Manifest.permission.FOREGROUND_SERVICE,
                android.Manifest.permission.CALL_PHONE,
                android.Manifest.permission.READ_PHONE_STATE
        );
    }

}