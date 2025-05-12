package stockChartViewer;

import javax.swing.*;
import javax.swing.Timer;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.filechooser.FileNameExtensionFilter;

import com.pretty_tools.dde.DDEException;
import com.pretty_tools.dde.DDEMLException;
import com.pretty_tools.dde.client.DDEClientConversation;
import com.pretty_tools.dde.client.DDEClientEventListener;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import stockChartViewer.StockChartApp_V11R01.OHLCVData;

public class StockChartApp_DualChart_Manus_v1 extends JFrame {
    /**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private CandlestickChart chartTop;
    private CandlestickChart chartBottom;
    private JLabel statusLabel;
    private List<OHLCVData> dataTop = new ArrayList<>();
    private List<OHLCVData> dataBottom = new ArrayList<>();
    private HttpServer server;
    private int serverPort = 8080;
    private int maxBufferSize = 1000; // Dimensione default del buffer
    private boolean realtimeUpdatesEnabled = false;
    private boolean autoScroll = true; // Auto-scroll sul grafico quando arrivano nuovi dati
    private JSplitPane splitPane; // Split pane per dividere i due grafici
    private int splitPosition = 50; // Percentuale di divisione tra i due grafici
    private boolean syncCharts = true; // Sincronizzazione dei due grafici (zoom, scroll)
    private boolean sendToTopChart = true; // Flag per determinare a quale grafico inviare i dati in tempo reale
    
    EventListnerDDE eLDDE = new EventListnerDDE();
    
    public StockChartApp_DualChart_Manus_v1() {
        setTitle("Visualizzatore Quotazioni di Borsa - Dual Chart");
        setSize(1200, 800);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        
        // Menu per caricare file
        JMenuBar menuBar = new JMenuBar();
        JMenu fileMenu = new JMenu("File");
        JMenuItem openTopItem = new JMenuItem("Apri file OHLCV (Grafico Superiore)...");
        JMenuItem openBottomItem = new JMenuItem("Apri file OHLCV (Grafico Inferiore)...");
        JMenuItem exitItem = new JMenuItem("Esci");
        
        openTopItem.addActionListener(e -> loadDataFromFile(true));
        openBottomItem.addActionListener(e -> loadDataFromFile(false));
        exitItem.addActionListener(e -> {
            stopServer();
            System.exit(0);
        });
        
        fileMenu.add(openTopItem);
        fileMenu.add(openBottomItem);
        fileMenu.addSeparator();
        fileMenu.add(exitItem);
        
        // Menu Server
        JMenu serverMenu = new JMenu("Server");
        JMenuItem startServerItem = new JMenuItem("Avvia Server REST");
        JMenuItem stopServerItem = new JMenuItem("Ferma Server REST");
        JMenuItem configServerItem = new JMenuItem("Configura Server...");
        JMenuItem startlistnerDDEItem = new JMenuItem("Avvia listner DDE...");
        
        startServerItem.addActionListener(e -> startServer());
        stopServerItem.addActionListener(e -> stopServer());
        configServerItem.addActionListener(e -> configureServer());
        startlistnerDDEItem.addActionListener(e -> startListnerDDE());
        
        serverMenu.add(startServerItem);
        serverMenu.add(stopServerItem);
        serverMenu.addSeparator();
        serverMenu.add(configServerItem);
        serverMenu.add(startlistnerDDEItem);
        
        // Menu View per configurare la visualizzazione
        JMenu viewMenu = new JMenu("Vista");
        JMenuItem resizeSplitItem = new JMenuItem("Configura divisione grafici...");
        JCheckBoxMenuItem syncChartsItem = new JCheckBoxMenuItem("Sincronizza grafici", syncCharts);
        JRadioButtonMenuItem sendTopItem = new JRadioButtonMenuItem("Invia dati a grafico superiore", sendToTopChart);
        JRadioButtonMenuItem sendBottomItem = new JRadioButtonMenuItem("Invia dati a grafico inferiore", !sendToTopChart);
        
        // Gruppo per i radio button
        ButtonGroup targetGroup = new ButtonGroup();
        targetGroup.add(sendTopItem);
        targetGroup.add(sendBottomItem);
        
        resizeSplitItem.addActionListener(e -> configureSplitPosition());
        syncChartsItem.addActionListener(e -> syncCharts = syncChartsItem.isSelected());
        sendTopItem.addActionListener(e -> sendToTopChart = true);
        sendBottomItem.addActionListener(e -> sendToTopChart = false);
        
        viewMenu.add(resizeSplitItem);
        viewMenu.add(syncChartsItem);
        viewMenu.addSeparator();
        viewMenu.add(sendTopItem);
        viewMenu.add(sendBottomItem);
        
        menuBar.add(fileMenu);
        menuBar.add(serverMenu);
        menuBar.add(viewMenu);
        setJMenuBar(menuBar);
        
        // Pannello principale
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        
        // Creazione dei due grafici
        chartTop = new CandlestickChart("Grafico Superiore");
        chartBottom = new CandlestickChart("Grafico Inferiore");
        
        // Split Pane per dividere i due grafici
        splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, createChartPanel(chartTop, "Grafico Superiore"), 
                                                        createChartPanel(chartBottom, "Grafico Inferiore"));
        splitPane.setOneTouchExpandable(true);
        splitPane.setResizeWeight(0.5); // Divisione 50/50 iniziale
        
        mainPanel.add(splitPane, BorderLayout.CENTER);
        
        // Barra di stato
        statusLabel = new JLabel("Pronto. Caricare un file OHLCV dal menu File.");
        statusLabel.setBorder(new EmptyBorder(5, 5, 5, 5));
        mainPanel.add(statusLabel, BorderLayout.SOUTH);
        
        add(mainPanel);
        setVisible(true);
        
        // Inizializza la posizione dello split pane dopo che il frame è visibile
        SwingUtilities.invokeLater(() -> {
            splitPane.setDividerLocation(0.5);
        });
    }
    
    private JPanel createChartPanel(CandlestickChart chart, String title) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), 
                        title, TitledBorder.LEFT, TitledBorder.TOP));
        panel.add(chart, BorderLayout.CENTER);
        return panel;
    }
    
    private void configureSplitPosition() {
        String input = JOptionPane.showInputDialog(this, 
            "Inserisci la percentuale per il grafico superiore (1-99):", 
            String.valueOf(splitPosition));
        
        if (input != null && !input.isEmpty()) {
            try {
                int value = Integer.parseInt(input);
                if (value > 0 && value < 100) {
                    splitPosition = value;
                    splitPane.setDividerLocation(splitPosition / 100.0);
                } else {
                    JOptionPane.showMessageDialog(this, 
                        "Il valore deve essere compreso tra 1 e 99",
                        "Valore non valido", JOptionPane.ERROR_MESSAGE);
                }
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(this, 
                    "Inserisci un numero valido",
                    "Errore di formato", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    private void loadDataFromFile(boolean isTopChart) {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Apri file OHLCV");
        fileChooser.setFileFilter(new FileNameExtensionFilter("File CSV", "csv", "txt"));
        
        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            try {
                List<OHLCVData> loadedData = readOHLCVFile(fileChooser.getSelectedFile());
                
                if (isTopChart) {
                    dataTop = loadedData;
                    chartTop.setData(dataTop);
                    statusLabel.setText("File caricato nel grafico superiore: " + fileChooser.getSelectedFile().getName() + 
                                        " - " + dataTop.size() + " record trovati.");
                } else {
                    dataBottom = loadedData;
                    chartBottom.setData(dataBottom);
                    statusLabel.setText("File caricato nel grafico inferiore: " + fileChooser.getSelectedFile().getName() + 
                                        " - " + dataBottom.size() + " record trovati.");
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, 
                    "Errore durante il caricamento del file: " + ex.getMessage(),
                    "Errore", JOptionPane.ERROR_MESSAGE);
                statusLabel.setText("Errore nel caricamento del file.");
            }
        }
    }
    
    private List<OHLCVData> readOHLCVFile(File file) throws IOException {
        List<OHLCVData> dataList = new ArrayList<>();
        BufferedReader reader = new BufferedReader(new FileReader(file));
        String line;
        boolean firstLine = true;
        
        while ((line = reader.readLine()) != null) {
            if (firstLine) {
                firstLine = false;
                continue; // Salta intestazione
            }
            
            String[] values = line.split(",");
            if (values.length < 6) {
                continue; // Skip line se non ha abbastanza colonne
            }
            
            try {
                // Presupposto: Date, Open, High, Low, Close, Volume
                String date = values[0].trim();
                double open = Double.parseDouble(values[1].trim());
                double high = Double.parseDouble(values[2].trim());
                double low = Double.parseDouble(values[3].trim());
                double close = Double.parseDouble(values[4].trim());
                long volume = Long.parseLong(values[5].trim());
                
                dataList.add(new OHLCVData(date, open, high, low, close, volume));
            } catch (NumberFormatException e) {
                System.err.println("Errore nella conversione dei dati: " + line);
            }
        }
        
        reader.close();
        return dataList;
    }
    
    private void startListnerDDE() {
    	Thread t1 = new Thread(new Runnable() {
    	    @Override
    	    public void run() {
    	    	eLDDE.initEventListner();
    	    }
    	});  
    	t1.start();
    }
    
    private void startServer() {
        if (server != null) {
            JOptionPane.showMessageDialog(this, 
                "Il server è già in esecuzione sulla porta " + serverPort,
                "Server già attivo", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        
        try {
            server = HttpServer.create(new InetSocketAddress(serverPort), 0);
            server.createContext("/api/ohlcv", new OHLCVHandler());
            server.setExecutor(Executors.newCachedThreadPool());
            server.start();
            
            realtimeUpdatesEnabled = true;
            statusLabel.setText("Server REST avviato sulla porta " + serverPort);
            
            JOptionPane.showMessageDialog(this, 
                "Server REST avviato sulla porta " + serverPort + "\n" +
                "Endpoint: http://localhost:" + serverPort + "/api/ohlcv",
                "Server avviato", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, 
                "Errore nell'avvio del server: " + e.getMessage(),
                "Errore Server", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private void stopServer() {
        if (server == null) {
            return;
        }
        
        server.stop(0);
        server = null;
        realtimeUpdatesEnabled = false;
        statusLabel.setText("Server REST fermato");
        
        JOptionPane.showMessageDialog(this, 
            "Server REST fermato",
            "Server fermato", JOptionPane.INFORMATION_MESSAGE);
    }
    
    private void configureServer() {
        JPanel panel = new JPanel(new GridLayout(3, 2, 10, 10));
        
        JSpinner portSpinner = new JSpinner(new SpinnerNumberModel(serverPort, 1024, 65535, 1));
        JSpinner bufferSpinner = new JSpinner(new SpinnerNumberModel(maxBufferSize, 10, 10000, 10));
        JComboBox<String> targetChartCombo = new JComboBox<>(new String[]{"Grafico Superiore", "Grafico Inferiore"});
        targetChartCombo.setSelectedIndex(sendToTopChart ? 0 : 1);
        
        panel.add(new JLabel("Porta del server:"));
        panel.add(portSpinner);
        panel.add(new JLabel("Dimensione buffer (barre):"));
        panel.add(bufferSpinner);
        panel.add(new JLabel("Invia dati a:"));
        panel.add(targetChartCombo);
        
        int result = JOptionPane.showConfirmDialog(this, panel, 
            "Configurazione Server", JOptionPane.OK_CANCEL_OPTION);
        
        if (result == JOptionPane.OK_OPTION) {
            boolean restartServer = server != null;
            
            if (restartServer) {
                stopServer();
            }
            
            serverPort = (Integer) portSpinner.getValue();
            maxBufferSize = (Integer) bufferSpinner.getValue();
            sendToTopChart = targetChartCombo.getSelectedIndex() == 0;
            
            if (restartServer) {
                startServer();
            }
        }
    }
    
//    class OHLCVHandlerCSV implements HttpHandler {
//        @Override
//        public void handle(HttpExchange exchange) throws IOException {
//            try {
//                // Consenti solo richieste POST
//                if ("POST".equals(exchange.getRequestMethod())) {
//                    InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), "UTF-8");
//                    BufferedReader br = new BufferedReader(isr);
//                    StringBuilder requestBody = new StringBuilder();
//                    String line;
//                    while ((line = br.readLine()) != null) {
//                        requestBody.append(line);
//                    }
//                    
//                    // Elabora i dati ricevuti
//                    final String receivedData = requestBody.toString();
//                    
//                    // Parse e aggiunta dei dati in modo thread-safe usando SwingUtilities
//                    SwingUtilities.invokeLater(() -> {
//                        try {
//                            OHLCVData data = parseOHLCVLine(receivedData);
//                            if (data != null) {
//                                addOHLCVData(data);
//                            }
//                        } catch (Exception e) {
//                            System.err.println("Errore nell'elaborazione dei dati ricevuti: " + e.getMessage());
//                        }
//                    });
//                    
//                    // Risposta con conferma
//                    String response = "Dati OHLCV ricevuti con successo";
//                    exchange.sendResponseHeaders(200, response.length());
//                    OutputStream os = exchange.getResponseBody();
//                    os.write(response.getBytes());
//                    os.close();
//                    
//                } else if ("GET".equals(exchange.getRequestMethod())) {
//                    // Fornire informazioni sull'endpoint o stato del server
//                    String response = "OHLCV REST Endpoint attivo. Utilizzare POST per inviare dati nel formato: data,open,high,low,close,volume";
//                    exchange.sendResponseHeaders(200, response.length());
//                    OutputStream os = exchange.getResponseBody();
//                    os.write(response.getBytes());
//                    os.close();
//                } else {
//                    // Metodo non supportato
//                    String response = "Metodo non supportato";
//                    exchange.sendResponseHeaders(405, response.length());
//                    OutputStream os = exchange.getResponseBody();
//                    os.write(response.getBytes());
//                    os.close();
//                }
//            } catch (Exception e) {
//                String response = "Errore nell'elaborazione della richiesta: " + e.getMessage();
//                exchange.sendResponseHeaders(500, response.length());
//                OutputStream os = exchange.getResponseBody();
//                os.write(response.getBytes());
//                os.close();
//                e.printStackTrace();
//            }
//        }
//    }
    
    // Handler per il server REST
    class OHLCVHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equals(exchange.getRequestMethod())) {
                // Leggi il corpo della richiesta
                InputStream is = exchange.getRequestBody();
                BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                final String body = sb.toString();
                
                // Elabora i dati OHLCV
                try {
                    final OHLCVData newData = parseOHLCVLine(body);
                    
                    // Aggiorna i dati nell'EDT (Event Dispatch Thread)
//                    SwingUtilities.invokeLater(() -> {
//                        addRealtimeData(newData);
//                    });
                    
                    // Parse e aggiunta dei dati in modo thread-safe usando SwingUtilities
                    SwingUtilities.invokeLater(() -> {
                        try {
                            OHLCVData data = parseOHLCVLine(body);
                            if (data != null) {
                                addOHLCVData(data);
                            }
                        } catch (Exception e) {
                            System.err.println("Errore nell'elaborazione dei dati ricevuti: " + e.getMessage());
                        }
                    });
                    
                    // Rispondi con successo
                    String response = "{\"status\":\"success\"}";
                    exchange.sendResponseHeaders(200, response.length());
                    OutputStream os = exchange.getResponseBody();
                    os.write(response.getBytes());
                    os.close();
                } catch (Exception e) {
                    // Rispondi con errore
                    String response = "{\"status\":\"error\",\"message\":\"" + e.getMessage() + "\"}";
                    exchange.sendResponseHeaders(400, response.length());
                    OutputStream os = exchange.getResponseBody();
                    os.write(response.getBytes());
                    os.close();
                }
            } else {
                // Metodo non permesso
                String response = "{\"status\":\"error\",\"message\":\"Solo metodo POST supportato\"}";
                exchange.sendResponseHeaders(405, response.length());
                OutputStream os = exchange.getResponseBody();
                os.write(response.getBytes());
                os.close();
            }
        }
    }
    
    private OHLCVData parseOHLCVLine(String line) {
        String[] values = line.split(";");
        if (values.length < 6) {
            return null; // Skip line se non ha abbastanza colonne
        }
        
        try {
            // Presupposto: Date, Open, High, Low, Close, Volume
            String date = values[0].trim();
            double open = numberConverterUtils(values[1]); //Double.parseDouble(values[1].replace(",", ".").trim());
            double high = numberConverterUtils(values[2]); //Double.parseDouble(values[2].trim());
            double low = numberConverterUtils(values[3]); //Double.parseDouble(values[3].trim());
            double close = numberConverterUtils(values[4]); //Double.parseDouble(values[4].trim());
            long volume = Long.parseLong(values[5].trim());
            
            return new OHLCVData(date, open, high, low, close, volume);
        } catch (NumberFormatException e) {
            System.err.println("Errore nella conversione dei dati: " + line);
            return null;
        }
    }
    
    private Double numberConverterUtils(String n) {return Double.parseDouble(n.replace(",", ".").trim());}
    
    // Aggiunge un nuovo dato OHLCV e mantiene la dimensione del buffer
    private synchronized void addOHLCVData(OHLCVData newData) {
    	
    	//----------------------------------------------------------------------------
    	if (newData == null) {
    		return;
    	}
    	 // 1. Aggiorna il lastClosePrice per ENTRAMBI i grafici
        //    Questo assicura che la linea gialla si muova su entrambi i grafici
        //    indipendentemente da quale grafico riceve i dati OHLCV.
//        if (chartTop != null) {
//            chartTop.setLastClosePrice(newData.getClose());
//        }
//        if (chartBottom != null) {
//            chartBottom.setLastClosePrice(newData.getClose());
//        }

        // 2. Aggiungi i dati OHLCV effettivi al grafico TARGET selezionato dall'utente
        List<OHLCVData> targetDataList;
        CandlestickChart targetChartToUpdateData;

        if (sendToTopChart) { // 'sendToTopChart' è un campo esistente gestito dall'UI
            targetDataList = dataTop;
            targetChartToUpdateData = chartTop;
        } else {
            targetDataList = dataBottom;
            targetChartToUpdateData = chartBottom;
        }
        
   	    // 1. Aggiorna il lastClosePrice solo per il grafico attivo
        if (targetChartToUpdateData != null) {
        	targetChartToUpdateData.setLastClosePrice(newData.getClose());
        }

        if (targetDataList != null && targetChartToUpdateData != null) {
            targetDataList.add(newData);
            // Gestione del buffer: rimuovi i dati più vecchi se si supera la dimensione massima
            if (maxBufferSize > 0 && targetDataList.size() > maxBufferSize) {
                targetDataList.remove(0);
            }
            // Aggiorna il grafico target con la nuova lista di dati.
            // Passare una nuova ArrayList assicura che il grafico rilevi il cambiamento se non è progettato
            // per osservare modifiche alla lista originale.
            targetChartToUpdateData.setData(new ArrayList<>(targetDataList)); 
        }

        // Aggiorna l'etichetta di stato (opzionale, ma utile per feedback)
        String activeChartName = sendToTopChart ? "Grafico Superiore" : "Grafico Inferiore";
        statusLabel.setText("Dati ricevuti per " + activeChartName + ": " +
                            newData.getDate() + " Close: " + String.format("%.2f", newData.getClose()) +
                            ". Linea ultimo prezzo aggiornata su entrambi i grafici.");
        
        // Eventuale logica di auto-scroll, se presente o desiderata:
        // if (autoScroll && targetChartToUpdateData != null) {
        //     // targetChartToUpdateData.scrollToEnd(); // Assumendo che esista un metodo del genere
        // }
        
        //----------------------------------------------------------------------------------
    	
//        if (sendToTopChart) {
//            if (dataTop == null) {
//                dataTop = new ArrayList<>();
//            }
//            
//            // Aggiungi il nuovo dato
//            dataTop.add(newData);
//            
//            // Limita la dimensione del buffer
//            if (dataTop.size() > maxBufferSize) {
//                dataTop = dataTop.subList(dataTop.size() - maxBufferSize, dataTop.size());
//            }
//            
//            // Aggiorna il grafico
//            chartTop.setData(dataTop);
//            
//            // Auto-scroll se attivato
//            if (autoScroll && dataTop.size() > 0) {
//                int visibleBars = chartTop.calculateVisibleBars();
//                int startBar = Math.max(0, dataTop.size() - visibleBars);
//                chartTop.setStartBar(startBar);
//            }
//        } else {
//            if (dataBottom == null) {
//                dataBottom = new ArrayList<>();
//            }
//            
//            // Aggiungi il nuovo dato
//            dataBottom.add(newData);
//            
//            // Limita la dimensione del buffer
//            if (dataBottom.size() > maxBufferSize) {
//                dataBottom = dataBottom.subList(dataBottom.size() - maxBufferSize, dataBottom.size());
//            }
//            
//            // Aggiorna il grafico
//            chartBottom.setData(dataBottom);
//            
//            // Auto-scroll se attivato
//            if (autoScroll && dataBottom.size() > 0) {
//                int visibleBars = chartBottom.calculateVisibleBars();
//                int startBar = Math.max(0, dataBottom.size() - visibleBars);
//                chartBottom.setStartBar(startBar);
//            }
//        }
        
        // Aggiorna la barra di stato
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText("Dati ricevuti: " + (sendToTopChart ? dataTop.size() : dataBottom.size()) + 
                            " record nel buffer del grafico " + (sendToTopChart ? "superiore" : "inferiore") + ".");
        });
    }
    
    // Parsing dei dati JSON in formato OHLCV
    private OHLCVData parseOHLCVJson(String json) {
        // Implementazione semplice del parsing JSON
        // In un'applicazione reale si potrebbe usare una libreria JSON
        json = json.trim();
        if (!json.startsWith("{") || !json.endsWith("}")) {
            throw new IllegalArgumentException("Formato JSON non valido");
        }
        
        String date = "";
        double open = 0, high = 0, low = 0, close = 0;
        long volume = 0;
        
        // Rimuovi le parentesi graffe
        json = json.substring(1, json.length() - 1);
        
        // Dividi per virgole non tra virgolette
        String[] pairs = json.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
        
        for (String pair : pairs) {
            String[] keyValue = pair.split(":");
            if (keyValue.length != 2) continue;
            
            String key = keyValue[0].trim().replace("\"", "");
            String value = keyValue[1].trim().replace("\"", "");
            
            switch (key.toLowerCase()) {
                case "date":
                    date = value;
                    break;
                case "open":
                    open = Double.parseDouble(value);
                    break;
                case "high":
                    high = Double.parseDouble(value);
                    break;
                case "low":
                    low = Double.parseDouble(value);
                    break;
                case "close":
                    close = Double.parseDouble(value);
                    break;
                case "volume":
                    volume = Long.parseLong(value);
                    break;
            }
        }
        
        return new OHLCVData(date, open, high, low, close, volume);
    }
    
    // Aggiunge dati in tempo reale al buffer
    private void addRealtimeData(OHLCVData newData) {
        // Aggiungi il nuovo dato al grafico selezionato
        if (sendToTopChart) {
            dataTop.add(newData);
            
            // Limita la dimensione del buffer
            if (dataTop.size() > maxBufferSize) {
                dataTop = dataTop.subList(dataTop.size() - maxBufferSize, dataTop.size());
            }
            
            // Aggiorna il grafico
            chartTop.setData(dataTop);
            
            // Scorri alla fine del grafico (mostra sempre l'ultimo dato)
            chartTop.scrollToEnd();
        } else {
            dataBottom.add(newData);
            
            // Limita la dimensione del buffer
            if (dataBottom.size() > maxBufferSize) {
                dataBottom = dataBottom.subList(dataBottom.size() - maxBufferSize, dataBottom.size());
            }
            
            // Aggiorna il grafico
            chartBottom.setData(dataBottom);
            
            // Scorri alla fine del grafico (mostra sempre l'ultimo dato)
            chartBottom.scrollToEnd();
        }
        
        // Sincronizza gli zoom e la visualizzazione se richiesto
        if (syncCharts) {
            syncChartViews();
        }
        
        statusLabel.setText("Aggiornamento in tempo reale: " + newData.date + 
                          " - Prezzo: " + newData.close + " - Grafico " + 
                          (sendToTopChart ? "superiore" : "inferiore"));
    }
    
    // Sincronizza le impostazioni di visualizzazione tra i due grafici
    private void syncChartViews() {
        if (sendToTopChart) {
            // Sincronizza il grafico inferiore con quello superiore
            chartBottom.setCandleWidth(chartTop.getCandleWidth());
            if (dataBottom.size() > 0) {
                int visibleBars = chartTop.calculateVisibleBars();
                double visibleRatio = (double)chartTop.getStartBar() / Math.max(1, dataTop.size() - visibleBars);
                int bottomStartBar = (int)(visibleRatio * Math.max(0, dataBottom.size() - visibleBars));
                chartBottom.setStartBar(bottomStartBar);
            }
        } else {
            // Sincronizza il grafico superiore con quello inferiore
            chartTop.setCandleWidth(chartBottom.getCandleWidth());
            if (dataTop.size() > 0) {
                int visibleBars = chartBottom.calculateVisibleBars();
                double visibleRatio = (double)chartBottom.getStartBar() / Math.max(1, dataBottom.size() - visibleBars);
                int topStartBar = (int)(visibleRatio * Math.max(0, dataTop.size() - visibleBars));
                chartTop.setStartBar(topStartBar);
            }
        }
    }
    
    public static void main(String[] args) {
    	
    	/**
		 * To run application set JVM ARGUMENTS:  -Djava.library.path=".\lib"
		 */

    	
    	
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        SwingUtilities.invokeLater(() -> new StockChartApp_DualChart_Manus_v1());
    }
    
    // Classe interna per dati OHLCV
    static class OHLCVData {
        String date;
        double open;
        double high;
        double low;
        double close;
        long volume;
        
        public OHLCVData(String date, double open, double high, double low, double close, long volume) {
            this.date = date;
            this.open = open;
            this.high = high;
            this.low = low;
            this.close = close;
            this.volume = volume;
        }
        
        public boolean isUp() {
            return close >= open;
        }
        
        public double getClose() {
            return close;
        }

		public double getHigh() {
			return high;
		}

		public void setHigh(double high) {
			this.high = high;
		}

		public double getLow() {
			return low;
		}

		public void setLow(double low) {
			this.low = low;
		}

		public String getDate() {
			return date;
		}

		public void setDate(String date) {
			this.date = date;
		}
        
    }
    
    // Classe per il grafico a candele
    class CandlestickChart extends JPanel {
    	private double lastClosePrice = Double.NaN; // Valore iniziale per indicare nessun prezzo ricevuto
    	
    	public void setLastClosePrice(double price) {
    		this.lastClosePrice = price;           
    		repaint(); // Richiede al componente di ridisegnarsi
    	}
    	
    	public int getCandleWidth() {
			return candleWidth;
		}

		public void setCandleWidth(int candleWidth) {
			this.candleWidth = candleWidth;
		}
        public int getStartBar() {
			return startBar;
		}

		private String chartTitle;
        private List<OHLCVData> chartData = new ArrayList<>();
        private double minPrice = Double.MAX_VALUE;
        private double maxPrice = Double.MIN_VALUE;
        private int mouseX = -1;
        private int mouseY = -1;
        private Color bullishColor = new Color(0, 150, 50);  // Verde per rialzo
        private Color bearishColor = new Color(200, 0, 0);    // Rosso per ribasso
        private int candleWidth = 8;
        private int candleGap = 2;
        private double priceScale;
        private int visibleBars;
        private int chartLeftMargin = 60;
        private int chartRightMargin = 30;
        private int chartTopMargin = 30;
        private int chartBottomMargin = 30;


		private int startBar = 0;  // Primo indice da mostrare
        private boolean autoScaleY = true;
        private double manualMinPrice = Double.MIN_VALUE;
        private double manualMaxPrice = Double.MAX_VALUE;
        private Timer autoScaleTimer;
        private boolean dynamicScaling = true;
        
        private static final int PADDING_TOP = 30;    // Spazio sopra il grafico (es. per titolo o etichette asse Y superiore)
        private static final int PADDING_BOTTOM = 40; // Spazio sotto il grafico (es. per etichette asse X)
        private static final int PADDING_LEFT = 70;   // Spazio a sinistra (es. per etichette asse Y)
        private static final int PADDING_RIGHT = 70;  // Spazio a destra (es. per etichetta ultimo prezzo)

        
        public CandlestickChart(String title) {
            this.chartTitle = title;
            setBackground(Color.BLACK);
            
            // Gestione degli eventi del mouse
            MouseAdapter mouseHandler = new MouseAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    mouseX = e.getX();
                    mouseY = e.getY();
                    repaint();
                }
                
                @Override
                public void mouseExited(MouseEvent e) {
                    mouseX = -1;
                    mouseY = -1;
                    repaint();
                }
                
                @Override
                public void mouseWheelMoved(MouseWheelEvent e) {
                    int notches = e.getWheelRotation();
                    if (notches < 0) {
                        // Zoom in
                        candleWidth = Math.min(30, candleWidth + 1);
                    } else {
                        // Zoom out
                        candleWidth = Math.max(2, candleWidth - 1);
                    }
                    repaint();
                }
                
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2) {
                        // Doppio click per attivare/disattivare scaling automatico
                        dynamicScaling = !dynamicScaling;
                        if (dynamicScaling) {
                            // Quando si riattiva lo scaling dinamico, ricaricare min/max correnti
                            recalculateMinMax();
                        }
                        repaint();
                    }
                }
            };
            
            addMouseMotionListener(mouseHandler);
            addMouseListener(mouseHandler);
            addMouseWheelListener(mouseHandler);
            
            // Timer per lo scaling dinamico lento e fluido (per evitare flickering)
            autoScaleTimer = new Timer(200, e -> {
                if (dynamicScaling && !chartData.isEmpty()) {
                    animatePriceScale();
                }
            });
            autoScaleTimer.start();
            
            // Popover menu contestuale
            JPopupMenu popupMenu = new JPopupMenu();
            JCheckBoxMenuItem autoScaleItem = new JCheckBoxMenuItem("Scaling Automatico Asse Y", dynamicScaling);
            autoScaleItem.addActionListener(e -> {
                dynamicScaling = autoScaleItem.isSelected();
                if (dynamicScaling) {
                    recalculateMinMax();
                }
                repaint();
            });
            popupMenu.add(autoScaleItem);
            
            setComponentPopupMenu(popupMenu);
        }
        
        private void animatePriceScale() {
            // Calcola min/max ideali in base ai dati visibili
            double idealMin = Double.MAX_VALUE;
            double idealMax = Double.MIN_VALUE;
            
            int endBar = Math.min(chartData.size(), startBar + visibleBars);
            for (int i = startBar; i < endBar; i++) {
                OHLCVData bar = chartData.get(i);
                idealMin = Math.min(idealMin, bar.low);
                idealMax = Math.max(idealMax, bar.high);
            }
            
            // Aggiungi un po' di margine
            double range = Math.max(0.1, idealMax - idealMin);
            idealMin -= range * 0.05;
            idealMax += range * 0.05;
            
            // Anima verso i valori ideali
            double currentRange = maxPrice - minPrice;
            double idealRange = idealMax - idealMin;
            
            // Se la differenza è piccola, applica subito
            if (Math.abs((currentRange / idealRange) - 1.0) < 0.001 && 
                Math.abs(minPrice - idealMin) < 0.001 * idealRange) {
                return; // Già abbastanza simili
            }
            
            // Animazione fluida
            minPrice = minPrice + (idealMin - minPrice) * 0.2;
            maxPrice = maxPrice + (idealMax - maxPrice) * 0.2;
            
            repaint();
        }
        
        private void recalculateMinMax() {
            if (chartData == null || chartData.isEmpty()) return;
            
            // Calcola il minimo e massimo per la scala considerando solo i dati visibili
            minPrice = Double.MAX_VALUE;
            maxPrice = Double.MIN_VALUE;
            
            int endBar = Math.min(chartData.size(), startBar + calculateVisibleBars());
            for (int i = startBar; i < endBar; i++) {
                if (i >= chartData.size()) break;
                OHLCVData bar = chartData.get(i);
                minPrice = Math.min(minPrice, bar.low);
                maxPrice = Math.max(maxPrice, bar.high);
            }
            
            // Aggiungi un po' di margine
            double range = maxPrice - minPrice;
            minPrice -= range * 0.05;
            maxPrice += range * 0.05;
        }
        
        public void setStartBar(int startBar) {
            this.startBar = startBar;
            repaint();
        }
        
        public void setData(List<OHLCVData> data) {
            this.chartData = data;
            
            if (data != null && !data.isEmpty()) {
                if (dynamicScaling) {
                    // Primo caricamento o reset della scala
                    if (minPrice == Double.MAX_VALUE || maxPrice == Double.MIN_VALUE) {
                        recalculateMinMax();
                    }
                }
                
                // Scorri alla fine 
                startBar = Math.max(0, data.size() - calculateVisibleBars());
            }
            
            repaint();
        }
        
        private int calculateVisibleBars() {
            if (getWidth() <= chartLeftMargin + chartRightMargin) return 0;
            return (getWidth() - chartLeftMargin - chartRightMargin) / (candleWidth + candleGap);
        }
        
        public void scrollToEnd() {
            if (chartData != null && !chartData.isEmpty()) {
                startBar = Math.max(0, chartData.size() - calculateVisibleBars());
                repaint();
            }
        }
        
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
                     
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            
            int width = getWidth();
            int height = getHeight();
            int chartWidth = width - chartLeftMargin - chartRightMargin;
            int chartHeight = height - chartTopMargin - chartBottomMargin;
            
            // Se non ci sono dati, mostra un messaggio
            if (chartData == null || chartData.isEmpty()) {
                g2.setColor(Color.WHITE);
                g2.setFont(new Font("SansSerif", Font.BOLD, 16));
                String message = "Nessun dato disponibile. Caricare un file OHLCV.";
                int messageWidth = g2.getFontMetrics().stringWidth(message);
                g2.drawString(message, (width - messageWidth) / 2, height / 2);
                return;
            }
            
            // Calcola quante barre possono essere visualizzate
            visibleBars = calculateVisibleBars();
            if (visibleBars <= 0) return;
            
            // Limita gli indici per evitare problemi
            startBar = Math.max(0, Math.min(startBar, chartData.size() - visibleBars));
            int endBar = Math.min(chartData.size(), startBar + visibleBars);
            
            // Calcola scala per i prezzi
            priceScale = chartHeight / (maxPrice - minPrice);
            
            // Disegna la griglia
            drawGrid(g2, chartWidth, chartHeight);
            
            // Disegna assi e label
            drawAxes(g2, chartWidth, chartHeight);
            
            // Disegna le candele
            for (int i = startBar; i < endBar; i++) {
                int candleIndex = i - startBar;
                drawCandle(g2, chartData.get(i), 
                          chartLeftMargin + candleIndex * (candleWidth + candleGap),
                          chartTopMargin, chartHeight);
            }
            
            // Disegna crosshair e info
            if (mouseX >= chartLeftMargin && mouseX <= width - chartRightMargin &&
                mouseY >= chartTopMargin && mouseY <= height - chartBottomMargin) {
                
                drawCrosshair(g2, width, height);
                
                // Calcola l'indice della candela puntata
                int candleIndex = (mouseX - chartLeftMargin) / (candleWidth + candleGap);
                if (startBar + candleIndex < chartData.size()) {
                    drawPriceInfo(g2, chartData.get(startBar + candleIndex));
                }
            }
            
            // Indica se lo scaling dinamico è attivo
            if (dynamicScaling) {
                g2.setColor(new Color(0, 200, 0));
                g2.setFont(new Font("SansSerif", Font.PLAIN, 12));
                g2.drawString("Auto-Scale: ON", width - 100, 20);
            } else {
                g2.setColor(new Color(200, 0, 0));
                g2.setFont(new Font("SansSerif", Font.PLAIN, 12));
                g2.drawString("Auto-Scale: OFF", width - 100, 20);
            }
            
            
           //------------------------------------------------------------------------------
            
            
            Graphics2D g2d = (Graphics2D) g.create(); // Usa create() per non modificare il Graphics originale permanentemente
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // Dimensioni effettive dell'area di disegno del grafico
//            chartWidth = getWidth() - PADDING_LEFT - PADDING_RIGHT;
//            chartHeight = getHeight() - PADDING_TOP - PADDING_BOTTOM;

            if (chartData == null || chartData.isEmpty()) {
                String noDataMsg = "Nessun dato da visualizzare"; // Potresti voler usare il titolo del grafico qui
                FontMetrics metrics = g2d.getFontMetrics();
                int x = (getWidth() - metrics.stringWidth(noDataMsg)) / 2;
                int y = getHeight() / 2;
                g2d.drawString(noDataMsg, x, y);
                
                // Anche se non ci sono dati OHLCV, disegna la linea dell'ultimo prezzo se disponibile
                // Per fare ciò, abbiamo bisogno di un range di prezzo. Se non ci sono dati, potremmo usare un range di default
                // o semplicemente non disegnarla. Per ora, la disegniamo solo se c'è una scala (vedi sotto).
                // La richiesta è che sia "sempre visibile". Se lastClosePrice è l'unico dato, la scala deve basarsi su di esso.
            }

            // Calcolo dei limiti di prezzo (minPrice, maxPrice) per l'asse Y
            double minPrice = Double.MAX_VALUE;
            double maxPrice = Double.MIN_VALUE;

            if (chartData != null && !chartData.isEmpty()) {
                for (OHLCVData d : chartData) {
                    minPrice = Math.min(minPrice, d.getLow());
                    maxPrice = Math.max(maxPrice, d.getHigh());
                }
            }

            // Includi lastClosePrice nel calcolo del range per assicurare che sia visibile
            if (!Double.isNaN(lastClosePrice)) {
                if (minPrice == Double.MAX_VALUE) { // Nessun dato OHLCV, usa solo lastClosePrice
                    minPrice = lastClosePrice - Math.abs(lastClosePrice * 0.1); // Aggiungi un piccolo range
                    maxPrice = lastClosePrice + Math.abs(lastClosePrice * 0.1);
                    if (minPrice == maxPrice) { // Se lastClosePrice è 0
                        minPrice = -1;
                        maxPrice = 1;
                    }
                } else {
                    minPrice = Math.min(minPrice, lastClosePrice);
                    maxPrice = Math.max(maxPrice, lastClosePrice);
                }
            }
            
            // Se dopo tutto non abbiamo ancora un range valido (es. nessun dato e nessun lastClosePrice)
            if (minPrice == Double.MAX_VALUE || maxPrice == Double.MIN_VALUE || minPrice == maxPrice) {
                // Non possiamo disegnare il grafico o la linea di prezzo senza una scala
                // Potresti disegnare un messaggio o ritornare.
                // Per ora, se minPrice e maxPrice sono uguali (es. solo lastClosePrice è valido e non c'è range)
                if (minPrice == maxPrice && !Double.isNaN(minPrice)) {
                    maxPrice = minPrice + Math.max(1.0, Math.abs(minPrice * 0.1)); // Aggiungi un piccolo delta
                    minPrice = minPrice - Math.max(1.0, Math.abs(minPrice * 0.1));
                    if (maxPrice == minPrice) { // Ancora uguali (es. prezzo 0)
                       maxPrice = 1;
                       minPrice = -1;
                   }
                } else {
                    // Se ancora non valido, non disegnare la linea di prezzo
                    g2d.dispose();
                    return;
                }
            }
            
         // DISEGNO DELLA LINEA ORIZZONTALE GIALLA TRATTEGGIATA
            if (!Double.isNaN(lastClosePrice) && (maxPrice - minPrice > 0)) { // Assicurati che ci sia un range valido
                g2d.setColor(Color.YELLOW);
                Stroke dashed = new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{7, 7}, 0);
                g2d.setStroke(dashed);

                // Calcola la posizione Y della linea basata sulla scala del grafico
                double yPixelPrice = PADDING_TOP + chartHeight - (((lastClosePrice - minPrice) / (maxPrice - minPrice)) * chartHeight);
                
                // Limita la linea all'interno dell'area del grafico verticale
                yPixelPrice = Math.max(PADDING_TOP, Math.min(yPixelPrice, PADDING_TOP + chartHeight));

                g2d.drawLine(PADDING_LEFT, (int) yPixelPrice, PADDING_LEFT + chartWidth, (int) yPixelPrice);

                // Disegna il valore del prezzo accanto alla linea (a destra)
                g2d.setStroke(new BasicStroke()); // Ripristina stroke solido per il testo
                g2d.setColor(Color.BLACK); // Colore del testo
                String priceText = String.format("%.2f", lastClosePrice); // Formatta a 2 decimali
                FontMetrics fm = g2d.getFontMetrics();
                int textWidth = fm.stringWidth(priceText);
                int textHeight = fm.getAscent();
                
                int rectX = PADDING_LEFT + chartWidth -25; // Posiziona a destra del grafico
                int rectY = (int) yPixelPrice - (textHeight / 2) - 2;
                int rectWidth = textWidth + 4;
                int rectHeight = textHeight + 2;

                g2d.setColor(new Color(255, 255, 220)); // Sfondo giallo pallido per il testo
                g2d.fillRect(rectX, rectY, rectWidth, rectHeight);
                g2d.setColor(Color.BLACK);
                g2d.drawRect(rectX, rectY, rectWidth, rectHeight); // Bordo per il box del testo
                g2d.drawString(priceText, rectX + 2, (int) yPixelPrice + (fm.getAscent() / 2) - 2);
            }
            
            g2d.dispose(); // Rilascia le risorse del Graphics2D creato
            
            //---------------------------------------------------------------------------------------
     
        }
        
        private void drawGrid(Graphics2D g2, int chartWidth, int chartHeight) {
            g2.setColor(new Color(50, 50, 50));  // Colore scuro per la griglia
            
            // Linee orizzontali
            int numHLines = 5;
            for (int i = 0; i <= numHLines; i++) {
                int y = chartTopMargin + (i * chartHeight / numHLines);
                g2.drawLine(chartLeftMargin, y, getWidth() - chartRightMargin, y);
                
                // Valore prezzo
                double price = maxPrice - (i * (maxPrice - minPrice) / numHLines);
                g2.setColor(new Color(200, 200, 200));
                g2.drawString(String.format("%.2f", price), 5, y + 5);
                g2.setColor(new Color(50, 50, 50));
            }
            
            // Linee verticali (solo alcune date)
            if (chartData.size() > 0) {
                int step = Math.max(1, visibleBars / 8);  // Mostra circa 8 etichette di date
                for (int i = startBar; i < Math.min(chartData.size(), startBar + visibleBars); i += step) {
                    int candleIndex = i - startBar;
                    int x = chartLeftMargin + candleIndex * (candleWidth + candleGap) + candleWidth / 2;
                    g2.drawLine(x, chartTopMargin, x, chartTopMargin + chartHeight);
                    
                    // Data
                    if (i < chartData.size()) {
                        g2.setColor(new Color(200, 200, 200));
                        String dateLabel = chartData.get(i).date;
                        // Abbreviamo la data per visualizzarla meglio
                        if (dateLabel.length() > 10) {
                            dateLabel = dateLabel.substring(0, 10);
                        }
                        g2.drawString(dateLabel, x - 25, getHeight() - 10);
                        g2.setColor(new Color(50, 50, 50));
                    }
                }
            }
        }
        
        private void drawAxes(Graphics2D g2, int chartWidth, int chartHeight) {
            g2.setColor(new Color(100, 100, 100));
            
            // Asse Y (prezzi)
            g2.drawLine(chartLeftMargin, chartTopMargin, 
                       chartLeftMargin, chartTopMargin + chartHeight);
            
            // Asse X (tempo)
            g2.drawLine(chartLeftMargin, chartTopMargin + chartHeight, 
                       chartLeftMargin + chartWidth, chartTopMargin + chartHeight);
        }
        
        private void drawCandle(Graphics2D g2, OHLCVData data, int x, int topMargin, int chartHeight) {
            // Converte i prezzi in coordinate
            int openY = topMargin + chartHeight - (int)((data.open - minPrice) * priceScale);
            int closeY = topMargin + chartHeight - (int)((data.close - minPrice) * priceScale);
            int highY = topMargin + chartHeight - (int)((data.high - minPrice) * priceScale);
            int lowY = topMargin + chartHeight - (int)((data.low - minPrice) * priceScale);
            
            // Scegli il colore in base al trend
            Color candleColor = data.isUp() ? bullishColor : bearishColor;
            g2.setColor(candleColor);
            
            // Disegna il corpo della candela
            int bodyTop = Math.min(openY, closeY);
            int bodyHeight = Math.max(1, Math.abs(closeY - openY));
            g2.fillRect(x, bodyTop, candleWidth, bodyHeight);
            
            // Disegna lo stoppino superiore e inferiore
            int centerX = x + candleWidth / 2;
            g2.drawLine(centerX, highY, centerX, bodyTop);
            g2.drawLine(centerX, bodyTop + bodyHeight, centerX, lowY);
        }
        
        private void drawCrosshair(Graphics2D g2, int width, int height) {
            g2.setColor(new Color(150, 150, 150, 150));
            
            // Linea orizzontale
            g2.drawLine(chartLeftMargin, mouseY, width - chartRightMargin, mouseY);
            
            // Linea verticale
            g2.drawLine(mouseX, chartTopMargin, mouseX, height - chartBottomMargin);
            
            // Prezzo attuale
            double price = maxPrice - (mouseY - chartTopMargin) * (maxPrice - minPrice) / 
                          (height - chartTopMargin - chartBottomMargin);
            
            int chartRightPriceText = chartRightMargin +70;
            g2.setColor(Color.WHITE);
            g2.fillRect(width - chartRightPriceText + 5, mouseY - 10, 55, 20);
            g2.setColor(Color.BLACK);
            g2.drawString(String.format("%.2f", price), width - chartRightPriceText + 10, mouseY + 5);
        }
        
        private void drawPriceInfo(Graphics2D g2, OHLCVData data) {
            String info = String.format("O: %.2f H: %.2f L: %.2f C: %.2f V: %d", 
                                       data.open, data.high, data.low, data.close, data.volume);
            
            g2.setColor(new Color(50, 50, 50, 200));
            g2.fillRect(mouseX + 15, mouseY - 25, 200, 20);
            
            g2.setColor(data.isUp() ? bullishColor : bearishColor);
            g2.drawString(info, mouseX + 20, mouseY - 10);
            
            g2.setColor(Color.WHITE);
            g2.drawString("Data: " + data.date, 10, 20);
        }
    }
    
    

}