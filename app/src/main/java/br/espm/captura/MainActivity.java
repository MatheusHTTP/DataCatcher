package br.espm.captura;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.concurrent.ExecutionException;

public class MainActivity extends Activity implements Servico.Callback {

	private TextView txtPacotesLidos;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		txtPacotesLidos = findViewById(R.id.txtPacotesLidos);

		atualizarTextoBotao(Servico.getEstado());
	}

	@Override
	protected void onStart() {
		super.onStart();

		Servico.setCallback(this);
	}

	@Override
	protected void onStop() {
		super.onStop();

		Servico.setCallback(null);
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);

		if (requestCode != 1)
			return;

		for (int i = grantResults.length - 1; i >= 0; i--) {
			if (grantResults[i] != PackageManager.PERMISSION_GRANTED)
				return;
		}

		iniciarServico();
	}

	private void iniciarServico() {
		LocationManager locationManager = (LocationManager)getSystemService(LOCATION_SERVICE);

		if (locationManager == null || !locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
			Toast.makeText(this, R.string.habilite_gps, Toast.LENGTH_SHORT).show();
			return;
		}

		Servico.iniciarServico(getApplication());
	}

	private boolean verificarPermissao() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
				checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
				(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED)) {

				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
					requestPermissions(new String[] {
						Manifest.permission.ACCESS_COARSE_LOCATION,
						Manifest.permission.ACCESS_FINE_LOCATION,
						Manifest.permission.ACCESS_BACKGROUND_LOCATION
					}, 1);
				} else {
					requestPermissions(new String[] {
						Manifest.permission.ACCESS_COARSE_LOCATION,
						Manifest.permission.ACCESS_FINE_LOCATION
					}, 1);
				}

				return false;
			}
		}
		return true;
	}

	private void atualizarTextoBotao(int estado) {
		Button btnIniciarParar = findViewById(R.id.btnIniciarParar);

		txtPacotesLidos.setText("");

		switch (estado) {
		case Servico.ESTADO_NOVO:
			btnIniciarParar.setText(R.string.iniciar);
			break;
		case Servico.ESTADO_INICIADO:
			btnIniciarParar.setText(R.string.parar);
			break;
		default:
			btnIniciarParar.setText(R.string.aguarde);
			break;
		}
	}

	public void iniciarParar(View view) {
		switch (Servico.getEstado()) {
		case Servico.ESTADO_NOVO:
			if (verificarPermissao())
				iniciarServico();
			break;
		case Servico.ESTADO_INICIADO:
			Servico.pararServico();
			break;
		}
	}

	@Override
	public void onEstadoAlterado(int estado) {
		atualizarTextoBotao(estado);
	}

	@Override
	public void onNovoPacoteGPS(int pacotesLidosTotal) {
		txtPacotesLidos.setText(getString(R.string.pacotes_lidos, pacotesLidosTotal));
	}

	@Override
	public void onNovoPacoteSensor(int pacotesLidosTotal) {
		txtPacotesLidos.setText(getString(R.string.pacotes_lidos, pacotesLidosTotal));
	}
}
