import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import org.json.*;

/**
 * ~ Dictionary Server GUI ~
 * This is the main driver for the Dictionary Server GUI.
 * This GUI integrates with the socket communication and
 * protocol implementation.
 *
 * @author Si Yong Lim
 */
public class DictionaryServerGUI extends JFrame {
    // GUI Components
    private static JTextArea resultArea;
    private static JLabel numConnectionsLabel;
    private JLabel statusLabel;
    private JButton startButton;
    private JButton stopButton;

    private static int port;
    private static int numConnections = 0;
    static Dictionary dictionary;
    ServerSocket listeningSocket = null;
    static HashMap<String, Thread> clientThreads = new HashMap<>();
    static HashMap<String, ClientHandler> clientHandlers = new HashMap<>();

    public DictionaryServerGUI() {
        initializeGUI();
    }

    /**
     * Initialize the GUI and its properties
     */
    private void initializeGUI() {
        setTitle("Dictionary Server");
        setPreferredSize(new Dimension(700, 500));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        getContentPane().setLayout(new BorderLayout());

        // Adds window listener to that calls the shutdown sequence when window is closed
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                shutdownServer();
            }
        });

        // Create main panels
        getContentPane().add(createTopPanel(), BorderLayout.NORTH);
        getContentPane().add(createOperationsPanel(), BorderLayout.SOUTH);
        getContentPane().add(createResultPanel(), BorderLayout.CENTER);

        pack();
        setLocationRelativeTo(null);
        setResizable(true);
    }

    /**
     * Create the top panel including connections panel and
     * number of connections panel on the GUI
     */
    private JPanel createTopPanel() {
        JPanel topPanel = new JPanel();
        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));

        topPanel.add(createConnectionPanel());
        topPanel.add(createNumConnectionsLabelPanel());

        return topPanel;
    }

    /**
     * Creates the connection panel on the GUI
     */
    private JPanel createConnectionPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        panel.setBorder(new TitledBorder("Connection Status"));

        statusLabel = new JLabel("Not Connected");
        statusLabel.setForeground(Color.RED);
        panel.add(statusLabel);

        return panel;
    }

    /**
     * Creates the number of connections panel on the GUI
     */
    private JPanel createNumConnectionsLabelPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        panel.setBorder(new TitledBorder("# of active connections:"));
        
        numConnectionsLabel = new JLabel("0");
        numConnections = 0;
        panel.add(numConnectionsLabel);

        return panel;
    }

    /**
     * Creates the operations panel on the GUI
     */
    private JPanel createOperationsPanel() {
        JPanel mainPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));

        // Stop button to shut down the server listening socket and all its client connections
        stopButton = new JButton("Stop Server");
        stopButton.addActionListener(e -> shutdownServer());

        // Start button to start the server listening socket thread
        startButton = new JButton("Start Server");
        startButton.addActionListener(e -> startServer());

        mainPanel.add(startButton);
        mainPanel.add(stopButton);
        stopButton.setEnabled(false);

        return mainPanel;
    }

    /**
     * Create result panel on the GUI
     */
    private JPanel createResultPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new TitledBorder("Results"));

        resultArea = new JTextArea(8, 50);
        resultArea.setEditable(false);
        resultArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane scrollPane = new JScrollPane(resultArea);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);

        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }
 
    /**
     * Display result in the result area
     * @param result string to be displayed
     */
    public static void displayResult(String result) {
        // Ensure that multiple threads are synchronizing on the results
        // area be done on the EDT to avoid inconsistent states.
        SwingUtilities.invokeLater(() -> {
            // Time is rounded to milliseconds for neater appearance
            resultArea.append(LocalTime.now().truncatedTo(ChronoUnit.MILLIS) + ": " + result + "\n");
            resultArea.setCaretPosition(resultArea.getDocument().getLength());
        });
    }

    /**
     * Update connection status when connection status changes
     * @param connected connection status
     */
    public void setConnectionStatus(boolean connected) {
        if (connected) {
            statusLabel.setText("Connected");
            statusLabel.setForeground(Color.GREEN);
        } else {
            statusLabel.setText("Not Connected");
            statusLabel.setForeground(Color.RED);
            numConnectionsLabel.setText("0");
        }

        // Enable/disable buttons based on connection status
        stopButton.setEnabled(connected);
        startButton.setEnabled(!connected);
    }

    /**
     * Spawns new thread to start server listening socket
     */
    private void startServer() {
        // Spawns a new thread
        new Thread(() -> {
            try {
                // Create a server socket listening on port
                listeningSocket = new ServerSocket(port);
                displayResult("SERVER: Listening on port " + port + " for incoming connections");
                setConnectionStatus(true);

                // Listen for incoming connections forever
                while (!Thread.currentThread().isInterrupted() && listeningSocket != null) {
                    // Accept an incoming client connection request
                    Socket clientSocket = listeningSocket.accept();
                    numConnectionsLabel.setText(String.valueOf(++numConnections));

                    // Assign random 4-char UUID to client
                    String id = UUID.randomUUID().toString().substring(0, 4);
                    displayResult("SERVER: Client connection number " + id + " accepted");
                    displayResult("CLIENT " + id + ": Remote IP: " + clientSocket.getInetAddress().getHostAddress());
                    displayResult("CLIENT " + id + ": Remote Hostname: " + clientSocket.getInetAddress().getHostName());
                    displayResult("CLIENT " + id + ": Remote Port: " + clientSocket.getPort());

                    // Create new client handler to handle new client
                    ClientHandler handler = new ClientHandler(clientSocket, dictionary, id);
                    Thread thread = new Thread(handler);
                    thread.start();

                    clientThreads.put(id, thread);
                    clientHandlers.put(id, handler);
                }
            } catch (SocketTimeoutException e) {
                displayResult("ERROR: Connection timed out.");
            } catch (SocketException e) {
                displayResult("SERVER: Stopped listening to new incoming connections");
            } catch (IOException e) {
                displayResult("ERROR: " + e.getMessage());
            }
        }).start();
    }

    /**
     * Shut down the server and clears the threads and handlers
     */
    private void shutdownServer() {
        try {
            if (listeningSocket != null && !listeningSocket.isClosed()) {
                listeningSocket.close();
                listeningSocket = null;

                displayResult("SERVER: Terminating all connections with client");

                // Goes through each thread and client handler
                clientThreads.forEach((id, thread) -> thread.interrupt());
                clientHandlers.forEach((id, handler) -> handler.closeSocket());

                // Clears the array so that they can be reused
                clientThreads.clear();
                clientHandlers.clear();

                setConnectionStatus(false);
            }
        } catch (IOException e) {
            displayResult("ERROR: " + e.getMessage());
        }
    }

    /**
     * Remove the number of active connections on server side
     * and updates numConnections label on GUI
     */
    public static void removeActives(String id) {
        // Find the client handler and thread by id and remove it from the list
        clientHandlers.remove(id);
        clientThreads.remove(id);

        // Update the number of active connections label
        numConnectionsLabel.setText(String.valueOf(--numConnections));
    }

    /**
     * Main driver method for the Dictionary Server GUI
     */
    public static void main(String[] args) {
        // Parse command line arguments
        // Expected: java DictionaryServer.jar <port> <dictionary-file>
        if (args.length != 2) {
            System.out.println("Expected: java DictionaryServer.jar <port> <dictionary-file>");
            return;
        }

        // Check valid port
        try {
            port = Integer.parseInt(args[0]);
            if (port < 1 || port > 65535) {
                System.out.println("ERROR: Port number out of range");
                return;
            }
        } catch (NumberFormatException e) {
            System.out.println("ERROR: Port number must be a valid integer");
            return;
        }

        // Initialize dictionary
        java.lang.reflect.Type type = new TypeToken<HashMap<String, HashSet<String>>>() {}.getType();
        // Connections
        String filename = args[1];

        // Read dictionary from file
        try (Reader reader = new FileReader(filename)) {
            Gson gson = new Gson();
            dictionary = new Dictionary(gson.fromJson(reader, type), filename);
        } catch (FileNotFoundException e) {
            System.out.println("ERROR: File does not exist or cannot be opened");
            return;
        } catch (IOException e) {
            System.out.println("ERROR: Cannot read the file");
            return;
        } catch (JsonSyntaxException e) {
            System.out.println("ERROR: Malformed JSON data.");
            return;
        }

        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                System.out.println(e.getMessage());
            }
            DictionaryServerGUI gui = new DictionaryServerGUI();
            gui.setVisible(true);

            // Adds shutdown hook in case the server is forcefully shut down
            Runtime.getRuntime().addShutdownHook(new Thread(gui::shutdownServer));
        });
    }
}

/**
 * Class to handle a single client within a thread
 */
class ClientHandler implements Runnable {
    private final Socket clientSocket;
    private final Dictionary dictionary;
    private final String id;
    private BufferedWriter out;

    public ClientHandler(Socket socket, Dictionary dictionary, String id) {
        this.clientSocket = socket;
        this.dictionary = dictionary;
        this.id = id;
    }

    /**
     * Sends response to the client
     * @param status status of the operation
     * @param operation operation performed
     * @param word word to be requested
     * @param message detailed message
     * @param meanings meanings in case of an add word request
     */
    private void sendResponse(String status, String operation, String word, String message, HashSet<String> meanings) {
        try {
            JSONObject json = new JSONObject();
            json.put("status", status);
            json.put("operation", operation);
            json.put("word", word);
            json.put("message", message);
            if (meanings != null) json.put("meanings", meanings);
            out.write(json + "\n");
            out.flush();
        } catch (JSONException e) {
            DictionaryServerGUI.displayResult("ERROR: Sending response: " + e.getMessage());
        } catch (IOException e) {
            DictionaryServerGUI.displayResult("SERVER: Closed connection with Client " + id);
        }
    }

    /**
     * Format input to trim any extra whitespace
     * @param input input to be formatted
     * @return formatted input
     */
    public String formatInput(String input) {
        if (input == null) return "";
        String cleaned = input.trim();
        cleaned = cleaned.replaceAll("\\s+", " ");
        return cleaned;
    }

    /**
     * Handles search dictionary function
     * @param js object containing message from client
     * @param sleepDuration duration to sleep
     * @return String to be printed to GUI
     */
    private String handleSearch(JSONObject js, int sleepDuration) throws JSONException {
        String word = formatInput(js.getString("word"));
        DictionaryServerGUI.displayResult("CLIENT " + id + ": Search word \"" + word + "\"");

        String status, message;
        HashSet<String> result;
        try {
            result = dictionary.search(word, sleepDuration);
        } catch (InterruptedException e) {
            status = "fail";
            message = "Interrupted while searching for word \"" + word + "\"";
            sendResponse(status, "Search", word, message, null);
            return status + ". " + message;
        }

        if (result != null) {
            status = "success";
            message = "Definition for word \"" + word + "\" found";
            sendResponse(status, "Search", word, message, result);
        } else {
            status = "fail";
            message = "Definition for word \"" + word + "\" not found";
            sendResponse(status, "Search", word, message, null);
        }
        return status + ". " + message;
    }

    /**
     * Handles add word to dictionary function
     * @param js object containing message from client
     * @param sleepDuration duration to sleep
     * @return String to be printed to GUI
     */
    private String handleAdd(JSONObject js, int sleepDuration) throws JSONException {
        String word = formatInput(js.getString("word"));
        String meanings = js.getString("meanings");
        // Split string into meanings list
        ArrayList<String> meaningsList = new ArrayList<>(Arrays.asList(meanings.split("\\R")));
        for (int i = meaningsList.size() - 1; i >= 0; i--) {
            String formatted = formatInput(meaningsList.get(i));
            if (formatted.isEmpty() || formatted.equals(" ")) {
                meaningsList.remove(i);
            } else {
                meaningsList.set(i, formatted);
            }
        }

        DictionaryServerGUI.displayResult("CLIENT " + id + ": Add \"" + word + "\" with meanings: \"" + meanings.replace('\n', ',') + "\"");

        String status, message;
        String result;
        try {
            result = dictionary.add(word, sleepDuration, meaningsList);
        } catch (InterruptedException e) {
            status = "fail";
            message = "Interrupted while adding new word \"" + word + "\"";
            sendResponse(status, "Add", word, message, null);
            return status + ". " + message;
        }

        if (result != null) {
            if (result.equals("Duplicate meanings")) {
                status = "fail";
                message = "Duplicate meanings for word \"" + word + "\"";
                sendResponse(status, "Add", word, message, null);
            } else {
                status = "success";
                message = "Word \"" + word + "\" successfully added";
                sendResponse(status, "Add", word, message, null);
            }
        } else {
            status = "fail";
            message = "Word \"" + word + "\" already exists";
            sendResponse(status, "Add", word, message, null);
        }
        return status + ". " + message;
    }

    /**
     * Handles remove word from dictionary function
     * @param js object containing message from client
     * @param sleepDuration duration to sleep
     * @return String to be printed to GUI
     */
    private String handleRemove(JSONObject js, int sleepDuration) throws JSONException {
        String word = formatInput(js.getString("word"));
        DictionaryServerGUI.displayResult("CLIENT " + id + ": Remove \"" + word + "\"");

        String status, message;
        String result;
        try {
            result = dictionary.remove(word, sleepDuration);
        } catch (InterruptedException e) {
            status = "fail";
            message = "Interrupted while removing existing word \"" + word + "\"";
            sendResponse(status, "Remove", word, message, null);
            return status + ". " + message;
        }

        if (result != null) {
            status = "success";
            message = "Word \"" + word + "\" successfully removed";
            sendResponse(status, "Remove", word, message, null);
        } else {
            status = "fail";
            message = "Word \"" + word + "\" does not exist";
            sendResponse(status, "Remove", word, message, null);
        }
        return status + ". " + message;
    }

    /**
     * Handles add meaning to word in dictionary function
     * @param js object containing message from client
     * @param sleepDuration duration to sleep
     * @return String to be printed to GUI
     */
    private String handleMeaning(JSONObject js, int sleepDuration) throws JSONException {
        String word = formatInput(js.getString("word"));
        String newMeaning = formatInput(js.getString("meanings"));
        DictionaryServerGUI.displayResult("CLIENT " + id + ": Add meaning to word \"" + word + "\" with new meaning: \"" + newMeaning + "\"");

        String status, message;
        String result;
        try {
            result = dictionary.addMeaning(word, sleepDuration, newMeaning);
        } catch (InterruptedException e) {
            status = "fail";
            message = "Interrupted while adding new meaning to existing word \"" + word + "\"";
            sendResponse(status, "Add meaning", word, message, null);
            return status + ". " + message;
        }

        if (result != null) {
            if (result.equals("Duplicate")) {
                status = "fail";
                message = "New meaning: \"" + newMeaning + "\" identical to existing meanings for word \"" + word + "\"";
                sendResponse(status, "Add meaning", word, message, null);
            } else {
                status = "success";
                message = "Word \"" + word + "\" successfully added with new meaning \"" + newMeaning + "\"";
                sendResponse(status, "Add meaning", word, message, null);
            }
        } else {
            status = "fail";
            message = "Word \"" + word + "\" does not exist in dictionary";
            sendResponse(status, "Add meaning", word, message, null);
        }
        return status + ". " + message;
    }

    /**
     * Handles update dictionary function
     * @param js object containing message from client
     * @param sleepDuration duration to sleep
     * @return String to be printed to GUI
     */
    private String handleUpdate(JSONObject js, int sleepDuration) throws JSONException {
        String word = formatInput(js.getString("word"));
        String existingMeaning = js.getString("existingMeaning");
        String newMeaning = formatInput(js.getString("meanings"));
        DictionaryServerGUI.displayResult("CLIENT " + id + ": Update word \"" + word + "\" with existing meaning: \"" + existingMeaning + "\" to new meaning: \"" + newMeaning + "\"");

        String status, message;
        String result;
        try {
            result = dictionary.update(word, sleepDuration, existingMeaning, newMeaning);
        } catch (InterruptedException e) {
            status = "fail";
            message = "Interrupted while updating meaning of existing word \"" + word + "\"";
            sendResponse(status, "Update", word, message, null);
            return status + ". " + message;
        }

        if (result != null) {
            if (result.equals("Success")) {
                status = "success";
                message = "Word \"" + word + "\" successfully updated";
                sendResponse("success", "Update", word, message, null);
            } else {
                status = "fail";
                message = "New meaning \"" + newMeaning + "\" already exists";
                sendResponse(status, "Update", word, message, null);
            }
        } else {
            status = "fail";
            message = "Word \"" + word + "\" does not exist in dictionary";
            sendResponse(status, "Update", word, message, null);
        }
        return status + ". " + message;
    }

    @Override
    public void run() {
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            out = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()));

            // Loop to continuously listen to client's requests
            String clientMsg;
            while ((clientMsg = in.readLine()) != null) {
                try {
                    JSONObject js = new JSONObject(clientMsg);
                    String operation = js.getString("operation");
                    int sleepDuration = js.getInt("sleepDuration");
                    String message;
                    switch (operation) {
                        case "Search":
                            message = handleSearch(js, sleepDuration);
                            break;
                        case "Add":
                            message = handleAdd(js, sleepDuration);
                            break;
                        case "Remove":
                            message = handleRemove(js, sleepDuration);
                            break;
                        case "Meaning":
                            message = handleMeaning(js, sleepDuration);
                            break;
                        case "Update":
                            message = handleUpdate(js, sleepDuration);
                            break;
                        default:
                            String status = "fail";
                            message = "Unknown operation \"" + operation + "\"";
                            sendResponse("fail", operation, "", message, null);
                            message = status + ". " + message;
                            break;
                    }
                    DictionaryServerGUI.displayResult("SERVER: CLIENT " + id + ": " + operation + " " + message);
                } catch (JSONException e) {
                    // Catch malformed JSON exception in case of any errors
                    sendResponse("fail", "", "", "Error parsing message: " + e.getMessage(), null);
                    DictionaryServerGUI.displayResult("SERVER: CLIENT " + id + ": Error parsing message  " + e.getMessage());
                }
            }
        } catch (IOException e) {
            DictionaryServerGUI.displayResult("SERVER: CLIENT " + id + ": Error: " + e.getMessage());
        } finally {
            // Close connection with client and updates active connections
            DictionaryServerGUI.displayResult("CLIENT " + id + ": Closed connection");
            DictionaryServerGUI.removeActives(id);
            closeSocket();
        }
    }

    /**
     * Sends message to client for shut down and closes client socket
     */
    public void closeSocket() {
        try {
            sendResponse("Shutdown", "", "", "Shutting down", null);
            clientSocket.close();
        } catch (IOException e) {
            DictionaryServerGUI.displayResult("SERVER: Closed socket connection with Client " + id);
        }
    }
}