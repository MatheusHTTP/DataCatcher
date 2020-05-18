package br.espm.captura;

import android.annotation.SuppressLint;
import android.os.AsyncTask;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import br.espm.captura.Localizacao;

import java.util.ArrayList;
import java.util.List;

public class dataFormat extends AsyncTask<ArrayList, Void, String> {

    @SuppressLint("WrongThread")
    @Override
    protected String doInBackground(ArrayList... params) {
        for(Object x: params[0]) {
            if (x instanceof Localizacao) {
                JSONObject postData = new JSONObject();
                try {

                    postData.put("idViagem", ((Localizacao) x).idViagem);
                    postData.put("timestamp", ((Localizacao) x).timestamp);
                    postData.put("latitude", ((Localizacao) x).latitude);
                    postData.put("longitude", ((Localizacao) x).longitude);
                    postData.put("velocidade", ((Localizacao) x).velocidade);
                    new sendJson().execute("http://10.0.2.2:5000/geo", postData.toString());
                    //sender.put(postData);

                } catch (JSONException e) {
                    Log.e("Send Post", "Erro ao adicionar itens");
                }
            }else if(x instanceof Movimento){
                try {
                    JSONObject postData = new JSONObject();
                    postData.put("idViagem", ((Movimento) x).idViagem);
                    postData.put("timestamp", ((Movimento) x).timestamp);
                    postData.put("acelerometro", ((Movimento) x).acelerometro);
                    postData.put("giroscopio", ((Movimento) x).giroscopio);
                    new sendJson().execute("http://10.0.2.2:5000/sensor", postData.toString());
                } catch (JSONException e) {
                    Log.e("Send Post", "Erro ao adicionar sensor");
                }
            }
        }
        return null;
    }
}
