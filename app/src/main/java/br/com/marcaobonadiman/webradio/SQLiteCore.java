package br.com.marcaobonadiman.webradio;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class SQLiteCore extends SQLiteOpenHelper {
    private static final String NOMEDB =  "webradios.db";
    private static final int VERSAO = 1;

    public SQLiteCore(Context context) {
        super(context,NOMEDB,null,VERSAO); // Cria na pasta Default
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        CriaTabelas(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE tb_radios;");
        onCreate(db);
    }


    public static void CriaTabelas(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS tb_radios(id integer primary key, nome text, url text);");
        db.execSQL("CREATE TABLE IF NOT EXISTS tb_ctrls(id integer primary key, ponteiro int);");

        // Inclui o valor do ponteiro na tabela tb_ctrls (Registro único)
        ContentValues valores = new ContentValues();
        valores.put("ponteiro",-1);
        db.insert("tb_ctrls", null, valores);

        // Inclui algimas estações na tabela tb_radios
        valores = new ContentValues();
        valores.put("nome","e-Paraná" );
        valores.put("url","http://200.189.113.201/stream/educativafm.mp3");
        db.insert("tb_radios", null, valores);

        valores.put("nome","Instrumental  Gold" );
        valores.put("url","http://199.233.234.34:25373/;stream/1");
        db.insert("tb_radios", null, valores);

        valores.put("nome","MPB Máquina do Tempo" );
        valores.put("url","http://servidor28.brlogic.com:8032/live");
        db.insert("tb_radios", null, valores);

        valores.put("nome","Instrumental Leal Colombia" );
        valores.put("url","http://163.172.198.16:8366/;stream/1");
        db.insert("tb_radios", null, valores);

        valores.put("nome","Ouro Verde" );
        valores.put("url","http://servidor18.brlogic.com:7484/live");
        db.insert("tb_radios", null, valores);

        valores.put("nome","JB Rio" );
        valores.put("url","http://20283.live.streamtheworld.com/JBFMAAC1.aac");

        db.insert("tb_radios", null, valores);

        valores.put("nome","Onda Viva Portugal" );
        valores.put("url","http://centova.radios.pt:9524/stream");
        db.insert("tb_radios", null, valores);

        valores.put("nome","Nostalgie Paris" );
        valores.put("url","https://scdn.nrjaudio.fm/adwz2/fr/30601/mp3_128.mp3?origine=stream");
        db.insert("tb_radios", null, valores);

        valores.put("nome","Toca a Dançar - Portugal" );
        valores.put("url","https://sp0.redeaudio.com/9974/stream/;");
        db.insert("tb_radios", null, valores);

        valores.put("nome","Luanda Antena Comercial - Angola" );
        valores.put("url","https://radios.vpn.sapo.pt/AO/radio14.mp3");
        db.insert("tb_radios", null, valores);

        valores.put("nome","DE 80er Hits" );
        valores.put("url","https://stream.antenne.de/80er-kulthits/stream/mp3?aw_0_1st.playerid=radio.de");
        db.insert("tb_radios", null, valores);

        valores.put("nome","KSQM 91.5FM" );
        valores.put("url","https://video1.getstreamhosting.com:8182/stream");
        db.insert("tb_radios", null, valores);


    }
}
