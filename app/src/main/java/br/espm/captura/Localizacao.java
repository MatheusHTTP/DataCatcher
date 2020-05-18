package br.espm.captura;

public class Localizacao {
    int idViagem;
    long timestamp;
    double latitude;
    double longitude;
    double velocidade;

    public Localizacao(int idViagem, long timestamp, double latitude, double longitude, double velocidade){
        this.idViagem = idViagem;
        this.timestamp = timestamp;
        this.latitude = latitude;
        this.longitude = longitude;
        this.velocidade = velocidade;
    }
}
