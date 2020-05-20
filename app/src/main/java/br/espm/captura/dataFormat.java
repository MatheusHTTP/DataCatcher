package br.espm.captura;


import android.os.AsyncTask;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;

public class dataFormat extends AsyncTask<ArrayList, Void, String> {

    @Override
    protected String doInBackground(ArrayList... params) {
        int ctrl = 0;
        for(Object x: params[0]) {
            if (x instanceof Localizacao) {
                JSONObject postData = new JSONObject();
                try {

                    postData.put("idViagem", ((Localizacao) x).idViagem);
                    postData.put("timestamp", ((Localizacao) x).timestamp);
                    postData.put("latitude", ((Localizacao) x).latitude);
                    postData.put("longitude", ((Localizacao) x).longitude);
                    postData.put("velocidade", ((Localizacao) x).velocidade);
                    SendData.enviar("http://10.0.2.2:5000/geo", postData.toString());

                } catch (JSONException e) {
                    Log.e("Send Post", "Erro ao adicionar itens");
                }
            }else if(x instanceof Movimento){
                ctrl = 1;
                try {
                    JSONObject postData = new JSONObject();
                    postData.put("idViagem", ((Movimento) x).idViagem);
                    postData.put("timestamp", ((Movimento) x).timestamp);
                    postData.put("aX", ((Movimento) x).aX);
                    postData.put("aY", ((Movimento) x).aY);
                    postData.put("aZ", ((Movimento) x).aZ);
                    postData.put("gX", ((Movimento) x).gX);
                    postData.put("gY", ((Movimento) x).gY);
                    postData.put("gZ", ((Movimento) x).gZ);
                    SendData.enviar("http://10.0.2.2:5000/sensor", postData.toString());
                } catch (JSONException e) {
                    Log.e("Send Post", "Erro ao adicionar sensor");
                }
            }
        }
        return null;
    }
}
