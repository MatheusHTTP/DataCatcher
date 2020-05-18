package br.espm.captura;

public class Movimento {
    int idViagem;
    long timestamp;
    float acelerometro;
    float giroscopio;

    public Movimento(int idViagem, long timestamp, float acelerometro, float giroscopio){
        this.idViagem = idViagem;
        this.timestamp = timestamp;
        this.acelerometro = acelerometro;
        this.giroscopio = giroscopio;
    }
}
