package br.espm.captura;

public class Movimento {
    int idViagem;
    long timestamp;
    float aX;
    float aY;
    float aZ;
    float gX;
    float gY;
    float gZ;

    public Movimento(int idViagem, long timestamp, float aX, float aY, float aZ, float gX, float gY, float gZ){
        this.idViagem = idViagem;
        this.timestamp = timestamp;
        this.aX = aX;
        this.aY = aY;
        this.aZ = aZ;
        this.gX = gX;
        this.gY = gY;
        this.gZ = gZ;

    }
}
