package stockChartViewer;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Esempio di client che invia dati OHLCV al server REST dell'applicazione grafica
 * Questo client simula l'invio di dati di mercato in tempo reale ogni secondo
 */
public class RestClientExample {

	private static final String SERVER_URL = "http://localhost:8080/api/ohlcv";
	private static final Random random = new Random();
	private static double lastPrice = 100.0; // Prezzo iniziale

	public static void main(String[] args) {
		System.out.println("Client REST per invio dati OHLCV - Avvio");
		System.out.println("Invio dati a: " + SERVER_URL);

		// Schedulatore per inviare dati ogni secondo
		ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
		scheduler.scheduleAtFixedRate(RestClientExample::sendRandomOHLCVData, 0, 1, TimeUnit.SECONDS);

		System.out.println("Premi CTRL+C per terminare");
	}

	private static void sendRandomOHLCVData() {
		try {
			// Genera dati OHLCV casuali ma realistici
			String date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());

			// Il prezzo si sposta con un random walk
			double movement = (random.nextDouble() - 0.5) * 2.0; // Movimento tra -1 e +1
			lastPrice = Math.max(1.0, lastPrice + movement);

			// Genera dati open, high, low, close basati su lastPrice
			double open = lastPrice;
			double volatility = lastPrice * 0.01; // 1% di volatility
			double high = open + random.nextDouble() * volatility;
			double low = open - random.nextDouble() * volatility;
			double close = (open + high + low + open) / 4.0 + (random.nextDouble() - 0.5) * volatility;

			// Genera volume casuale
			long volume = 1000 + random.nextInt(10000);

			// Crea stringa CSV
			String ohlcvData = String.format("%s;%.2f;%.2f;%.2f;%.2f;%d", 
					date, open, high, low, close, volume);

			// Invia dati al server
			sendData(ohlcvData);

			System.out.println("Dati inviati: " + ohlcvData);

		} catch (Exception e) {
			System.err.println("Errore nell'invio dei dati: " + e.getMessage());
		}
	}

	private static void sendData(String data) throws Exception {
		URL url = new URL(SERVER_URL);
		HttpURLConnection connection = (HttpURLConnection) url.openConnection();

		// Configura connessione
		connection.setRequestMethod("POST");
		connection.setRequestProperty("Content-Type", "text/plain");
		connection.setDoOutput(true);

		// Invia dati
		try (OutputStream os = connection.getOutputStream()) {
			byte[] input = data.getBytes(StandardCharsets.UTF_8);
			os.write(input, 0, input.length);
		}

		// Leggi risposta
		int responseCode = connection.getResponseCode();
		if (responseCode != 200) {
			throw new RuntimeException("Errore HTTP: " + responseCode);
		}

		connection.disconnect();

	}
}