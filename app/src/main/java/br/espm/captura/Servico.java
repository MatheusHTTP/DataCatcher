package br.espm.captura;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Application;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationChannelGroup;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.ExecutionException;

// Referências extras:
// https://developer.android.com/guide/components/services
// https://developer.android.com/reference/android/location/LocationManager

public class Servico extends Service implements LocationListener, SensorEventListener {
	private static int idViagem = 1;
	static ArrayList<Localizacao> listaGPS = new ArrayList<>();
	static ArrayList<Movimento> listaSensor = new ArrayList<>();
	public interface Callback {
		void onEstadoAlterado(int estado);
		void onNovoPacoteGPS(int pacotesLidosTotal);
		void onNovoPacoteSensor(int pacotesLidosTotal);
	}

	private static final String CHANNEL_GROUP_ID = "capturag";
	private static final String CHANNEL_ID = "captura";

	private static Servico servico;
	private static Callback callback;

	public static final int ESTADO_NOVO = 0;
	public static final int ESTADO_INICIANDO = 1;
	public static final int ESTADO_INICIADO = 2;
	public static final int ESTADO_PARANDO = 3;
	private static volatile int estado = ESTADO_NOVO;

	private static boolean channelCriado = false;

	private PowerManager.WakeLock wakeLock;
	private Handler handler;
	private LocationManager locationManager;
	private SensorManager sensorManager;
	private int pacotesLidosTotal, pacotesLidosGPS, pacotesLidosSensor;
	private static final int TAMANHO_BUFFER_SEGUNDOS = 1000;
	private static final int LEITURAS_GPS_POR_SEGUNDO = 1;
	private static final int LEITURAS_SENSOR_POR_SEGUNDO = 10;
	private static final int INTERVALO_MINIMO_EM_MILISSEGUNOS_LEITURAS_SENSOR = 1000 / LEITURAS_SENSOR_POR_SEGUNDO;
	private static final int QUANTIDADE_PACOTES_GPS = TAMANHO_BUFFER_SEGUNDOS * LEITURAS_GPS_POR_SEGUNDO;
	private static final int QUANTIDADE_PACOTES_SENSOR = TAMANHO_BUFFER_SEGUNDOS * LEITURAS_SENSOR_POR_SEGUNDO;

	private long timestampInicial, ultimaTimestampSensor;
	private long[] timestampGPS, timestampSensor;
	private double[] bufferLat, bufferLng, bufferVel;
	private float[] bufferAccelX, bufferAccelY, bufferAccelZ, bufferGiroX, bufferGiroY, bufferGiroZ;

	public static void iniciarServico(Application application){
		if (estado != ESTADO_NOVO)
			return;

		estado = ESTADO_INICIANDO;
		defViagem();
		notificarEstado();

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
			application.startForegroundService(new Intent(application, Servico.class));
		else
			application.startService(new Intent(application, Servico.class));
	}

	public static void pararServico() {
		if (servico != null && estado == ESTADO_INICIADO) {
			estado = ESTADO_PARANDO;
			new dataFormat().execute(listaGPS);
			new dataFormat().execute(listaSensor);
			servico.stopForeground(true);
			servico.stopSelf();
			servico = null;

			notificarEstado();
		}
	}

	public static void setCallback(Callback callback) {
		Servico.callback = callback;
	}

	public static int getEstado() {
		return estado;
	}

	private static void notificarEstado() {
		if (callback != null)
			callback.onEstadoAlterado(estado);
	}

	private void notificarNovoPacoteGPS() {
		if (callback != null && estado == ESTADO_INICIADO)
			callback.onNovoPacoteGPS(pacotesLidosTotal);
	}

	private void notificarNovoPacoteSensor() {
		if (callback != null && estado == ESTADO_INICIADO)
			callback.onNovoPacoteSensor(pacotesLidosTotal);
	}

	@TargetApi(Build.VERSION_CODES.O)
	private static void criarChannel(Application application) {
		if (channelCriado)
			return;

		NotificationManager notificationManager = (NotificationManager)application.getSystemService(NOTIFICATION_SERVICE);

		if (notificationManager == null)
			return;

		String appName = application.getText(R.string.app_name).toString();

		notificationManager.createNotificationChannelGroup(new NotificationChannelGroup(CHANNEL_GROUP_ID, appName));

		NotificationChannel notificationChannel = new NotificationChannel(CHANNEL_ID, appName, NotificationManager.IMPORTANCE_LOW);
		notificationChannel.setGroup(CHANNEL_GROUP_ID);
		notificationChannel.enableLights(false);
		notificationChannel.enableVibration(false);
		notificationChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
		notificationChannel.setShowBadge(false);
		notificationManager.createNotificationChannel(notificationChannel);

		channelCriado = true;
	}

	@TargetApi(Build.VERSION_CODES.O)
	private static Notification criarNotificacaoComChannel(Application application) {
		return new Notification.Builder(application, CHANNEL_ID).setSmallIcon(R.drawable.ic_notification).build();
	}

	@SuppressLint({"WakelockTimeout", "MissingPermission"})
	@Override
	public void onCreate() {
		super.onCreate();

		estado = ESTADO_INICIADO;
		servico = this;

		Application application = servico.getApplication();

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
			criarChannel(application);

		Notification notification = ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ? criarNotificacaoComChannel(application) : new Notification());
		notification.icon = R.drawable.ic_notification;
		notification.when = 0;
		notification.flags = Notification.FLAG_FOREGROUND_SERVICE;

		Intent intent = new Intent(application, MainActivity.class);
		intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
		notification.contentIntent = PendingIntent.getActivity(application, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
		notification.contentView = new RemoteViews(application.getPackageName(), R.layout.notification_layout);

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
			notification.visibility = Notification.VISIBILITY_PUBLIC;

		startForeground(1, notification);

		handler = new Handler();

		PowerManager powerManager = (PowerManager)application.getSystemService(POWER_SERVICE);
		if (powerManager != null) {
			wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE, "br.espm.captura:wavelock");
			wakeLock.setReferenceCounted(false);
			wakeLock.acquire();
		}

		timestampInicial = SystemClock.elapsedRealtime();
		ultimaTimestampSensor = timestampInicial;

		timestampGPS = new long[QUANTIDADE_PACOTES_GPS];
		timestampSensor = new long[QUANTIDADE_PACOTES_SENSOR];
		bufferLat = new double[QUANTIDADE_PACOTES_GPS];
		bufferLng = new double[QUANTIDADE_PACOTES_GPS];
		bufferVel = new double[QUANTIDADE_PACOTES_GPS];

		bufferAccelX = new float[QUANTIDADE_PACOTES_SENSOR];
		bufferAccelY = new float[QUANTIDADE_PACOTES_SENSOR];
		bufferAccelZ = new float[QUANTIDADE_PACOTES_SENSOR];

		bufferGiroX = new float[QUANTIDADE_PACOTES_SENSOR];
		bufferGiroY = new float[QUANTIDADE_PACOTES_SENSOR];
		bufferGiroZ = new float[QUANTIDADE_PACOTES_SENSOR];

		listaGPS= new ArrayList<>();
		listaSensor= new ArrayList<>();
		pacotesLidosTotal = 0;
		pacotesLidosGPS = 0;
		pacotesLidosSensor = 0;
		locationManager = (LocationManager)getSystemService(LOCATION_SERVICE);
		if (locationManager != null)
			locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000 / LEITURAS_GPS_POR_SEGUNDO, 0, this);

		sensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);
		if (sensorManager != null) {
			Sensor sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
			Sensor sensor2 = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
			sensorManager.registerListener(this, sensor, 1000000 / LEITURAS_SENSOR_POR_SEGUNDO);
			sensorManager.registerListener(this, sensor2, 1000000 / LEITURAS_SENSOR_POR_SEGUNDO);
		}

		notificarEstado();
	}

	@Override
	public void onDestroy() {
		servico = null;
		estado = ESTADO_NOVO;
		if (locationManager != null) {
			locationManager.removeUpdates(this);
			locationManager = null;
		}

		if (sensorManager != null) {
			sensorManager.unregisterListener(this);
			sensorManager = null;
		}

		handler = null;

		if (wakeLock != null) {
			wakeLock.release();
			wakeLock = null;
		}

		timestampGPS = null;
		timestampSensor = null;
		bufferLat = null;
		bufferLng = null;
		bufferAccelX = null;
		bufferAccelY = null;
		bufferAccelZ = null;
		bufferGiroX = null;
		bufferGiroY = null;
		bufferGiroZ = null;
		listaGPS = null;
		listaSensor = null;
		notificarEstado();

		super.onDestroy();
	}

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	@TargetApi(Build.VERSION_CODES.O)
	@Override
	public void onLocationChanged(Location location) {
		if (location != null) {
			timestampGPS[pacotesLidosGPS] = SystemClock.elapsedRealtime() - timestampInicial;
			bufferLat[pacotesLidosGPS] = location.getLatitude();
			bufferLng[pacotesLidosGPS] = location.getLongitude();
			bufferVel[pacotesLidosGPS] = location.getSpeedAccuracyMetersPerSecond();
			listaGPS.add( new Localizacao (idViagem, timestampGPS[pacotesLidosGPS], bufferLat[pacotesLidosGPS], bufferLng[pacotesLidosGPS], bufferVel[pacotesLidosGPS]));
			pacotesLidosGPS++;
			if (pacotesLidosGPS >= QUANTIDADE_PACOTES_GPS) {
				// @@@ Envia os buffers para outra thread para enviar para o servidor.
				// @@@ Enquanto a outra thread envia os dados para o servidor, criamos
				// @@@ novos buffers para irmos preenchendo.
				new dataFormat().execute(listaGPS);
				listaGPS = new ArrayList<>();
				pacotesLidosGPS = 0;
				timestampGPS = new long[QUANTIDADE_PACOTES_GPS];
				bufferLat = new double[QUANTIDADE_PACOTES_GPS];
				bufferLng = new double[QUANTIDADE_PACOTES_GPS];
			}

			pacotesLidosTotal++;
			notificarNovoPacoteGPS();
		}
	}

	@Override
	public void onStatusChanged(String provider, int status, Bundle extras) {
	}

	@Override
	public void onProviderEnabled(String provider) {
	}

	@Override
	public void onProviderDisabled(String provider) {
	}

	@Override
	public void onSensorChanged(SensorEvent event) {
		if (event != null && event.values != null) {
			// Independente de pedirmos 10 leituras por segundo, o Android
			// envia as leituras a uma taxa bem maior... Por isso precisamos
			// "dar uma freada" aqui!
			long agora = SystemClock.elapsedRealtime();
			if (agora - ultimaTimestampSensor < INTERVALO_MINIMO_EM_MILISSEGUNOS_LEITURAS_SENSOR)
				return;
			ultimaTimestampSensor = agora;
			timestampSensor[pacotesLidosSensor] = agora - timestampInicial;
			if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
				bufferGiroX[pacotesLidosSensor] = event.values[0];
				bufferGiroY[pacotesLidosSensor] = event.values[1];
				bufferGiroZ[pacotesLidosSensor] = event.values[2];
			}
			if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
				bufferAccelX[pacotesLidosSensor] = event.values[0];
				bufferAccelY[pacotesLidosSensor] = event.values[1];
				bufferAccelZ[pacotesLidosSensor] = event.values[2];
			}
			listaSensor.add(new Movimento(idViagem, timestampSensor[pacotesLidosSensor], bufferAccelX[pacotesLidosSensor], bufferAccelY[pacotesLidosSensor],bufferAccelZ[pacotesLidosSensor], bufferGiroX[pacotesLidosSensor], bufferGiroY[pacotesLidosSensor], bufferGiroZ[pacotesLidosSensor]));
			pacotesLidosSensor++;
			if (pacotesLidosSensor >= QUANTIDADE_PACOTES_SENSOR) {
				// @@@ Envia os buffers para outra thread para enviar para o servidor.
				// @@@ Enquanto a outra thread envia os dados para o servidor, criamos
				// @@@ novos buffers para irmos preenchendo...
				new dataFormat().execute(listaSensor);
				listaSensor = new ArrayList<>();
				pacotesLidosSensor = 0;
				timestampSensor = new long[QUANTIDADE_PACOTES_SENSOR];
				bufferAccelX = new float[QUANTIDADE_PACOTES_SENSOR];
				bufferAccelY = new float[QUANTIDADE_PACOTES_SENSOR];
				bufferAccelZ = new float[QUANTIDADE_PACOTES_SENSOR];
				bufferGiroX = new float[QUANTIDADE_PACOTES_SENSOR];
				bufferGiroY = new float[QUANTIDADE_PACOTES_SENSOR];
				bufferGiroZ = new float[QUANTIDADE_PACOTES_SENSOR];
			}

			pacotesLidosTotal++;
			notificarNovoPacoteSensor();
		}
	}

	public static void defViagem(){
		final int min = 1000;
		final int max = 10000;
		final int random = new Random().nextInt((max - min) + 1) + min;
		String cod = "Local - " + random;
		JSONObject postData = new JSONObject();
		try {
			postData.put("idViagem", idViagem);
			postData.put("destino", cod);
			new NovaViagem().execute("http://10.0.2.2:5000/viagem", postData.toString());
		} catch (JSONException e) {
			Log.e("Get ID", "Falha ao criar viagem");;

		}
	}
	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {

	}
}
