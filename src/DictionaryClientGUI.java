import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.temporal.ChronoUnit;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * ~ Dictionary Client GUI ~
 * This is the main driver for the Dictionary Client GUI.
 * This GUI integrates with the socket communication and
 * protocol implementation.
 *
 * @author Si Yong Lim
 */
public class DictionaryClientGUI extends JFrame {
    // GUI Components
    private JTextField wordField;
    private JTextArea meaningArea;
    private JTextField existingMeaningField;
    private JTextField newMeaningField;
    private JTextArea resultArea;
    private JButton searchButton;
    private JButton addWordButton;
    private JButton removeWordButton;
    private JButton addMeaningButton;
    private JButton updateMeaningButton;
    private JButton connectButton;
    private JLabel statusLabel;

    // Connections
    private static BufferedReader in = null;
    private static BufferedWriter out = null;
    private static int sleepDuration;
    private static String serverAddress;
    private static int port;
    private Socket socket;

    public DictionaryClientGUI() {
        initializeGUI();
    }

    /**
     * Initialize the GUI and its properties
     */
    private void initializeGUI() {
        setTitle("Dictionary Client");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        getContentPane().setLayout(new BorderLayout());

        // Create main panels
        getContentPane().add(createConnectionPanel(), BorderLayout.NORTH);
        getContentPane().add(createOperationsPanel(), BorderLayout.CENTER);
        getContentPane().add(createResultPanel(), BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(null);
        setResizable(true);
    }

    /**
     * Create the connection panel on the GUI
     */
    private JPanel createConnectionPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        panel.setBorder(new TitledBorder("Connection Status"));

        statusLabel = new JLabel("Not Connected");
        statusLabel.setForeground(Color.RED);
        panel.add(statusLabel);
        
        connectButton = new JButton("Connect");
        connectButton.addActionListener(e -> {
            if (connectButton.getText().equals("Connect")) {
                connectToServer();
            } else {
                disconnectFromServer();
            }
        });
        panel.add(connectButton);

        return panel;
    }

    /**
     * Creates a socket and initializes the input and output writers
     */
    private void connectToServer() {
        socket = null;
        try {
            socket = new Socket(serverAddress, port);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));

            displayResult("CLIENT: Connected to server at " + socket.getInetAddress().getHostAddress() + ":" + socket.getPort() + " with host name: " + socket.getInetAddress().getHostName());
            startServerListener();

            if (socket != null) {
                setConnectionStatus(true);
            }
        } catch (UnknownHostException e) {
            displayResult("ERROR: Server not found");
        } catch (SocketTimeoutException e) {
            displayResult("ERROR: Connection timed out");
        } catch (IOException e) {
            displayResult("ERROR: " + e.getMessage() + ". Make sure server is running and its address & port are correct");
        } catch (Exception e) {
            displayResult("ERROR: " + e.getMessage());
        }
    }

    /**
     * Spawns a new thread to listen to the server
     */
    private void startServerListener() {
        // Spawns new thread
        new Thread(() -> {
            try {
                String serverMsg;
                // Loop to continuously read from the server
                while ((serverMsg = in.readLine()) != null) {
                    try {
                        JSONObject js = new JSONObject(serverMsg);
                        String status = js.getString("status");

                        // Disconnect from the server if it shuts down
                        if (status.equals("Shutdown")) {
                            displayResult("SERVER: " + status);
                            disconnectFromServer();
                        } else {
                            // Logs server's output to client
                            String operation = js.getString("operation");
                            String message = js.getString("message");
                            displayResult("SERVER: " + operation + " " + status + ". " + message);

                            // Only execute if server's reply contains meanings for search operation
                            if (js.has("meanings")) {
                                JSONArray meanings = js.getJSONArray("meanings");

                                if (!meanings.isEmpty()) {
                                    for (int i = 0; i < meanings.length(); i++) {
                                        displayResult(i + 1 + ": " + meanings.getString(i));
                                    }
                                }
                            }
                        }
                    } catch (JSONException e) {
                        // Catch any exceptions when reading from client
                        displayResult("SERVER: " + e.getMessage());
                    }
                }
            } catch (IOException | NullPointerException e) {
                // Catches exception if either client closes connection or server shuts down
                disconnectFromServer();
                displayResult("CLIENT: Closed connection with server");
            }
        }).start();
    }

    /**
     * Disconnect from the server
     */
    private void disconnectFromServer() {
        try {
            // Closes the input, output writers
            if (out != null) out.close();
            if (in != null) in.close();

            // Closes the socket
            if (socket != null) {
                socket.close();
                socket = null;
            }
        } catch (IOException e) {
            displayResult("ERROR: While disconnecting: " + e.getMessage());
        } finally {
            setConnectionStatus(false);
        }
    }

    /**
     * Creates the operations panel for the GUI
     */
    private JPanel createOperationsPanel() {
        JPanel mainPanel = new JPanel(new GridLayout(2, 2, 10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Search Word Panel
        mainPanel.add(createSearchPanel());

        // Add Word Panel
        mainPanel.add(createAddWordPanel());

        // Remove Word Panel
        mainPanel.add(createRemoveWordPanel());

        // Update Operations Panel
        mainPanel.add(createUpdatePanel());

        return mainPanel;
    }

    /**
     * Creates the search panel for the GUI
     */
    private JPanel createSearchPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new TitledBorder("Search Word"));

        wordField = new JTextField();
        searchButton = new JButton("Search");

        panel.add(new JLabel("Word:"), BorderLayout.WEST);
        panel.add(wordField, BorderLayout.CENTER);
        panel.add(searchButton, BorderLayout.EAST);

        searchButton.addActionListener(e -> searchWord());

        return panel;
    }

    /**
     * Creates the add word panel for the GUI
     */
    private JPanel createAddWordPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new TitledBorder("Add New Word"));

        JPanel inputPanel = new JPanel(new GridLayout(3, 2, 5, 5));

        JTextField addWordField = new JTextField();
        meaningArea = new JTextArea(3, 20);
        meaningArea.setLineWrap(true);
        meaningArea.setWrapStyleWord(true);
        JScrollPane meaningScroll = new JScrollPane(meaningArea);

        addWordButton = new JButton("Add Word");

        inputPanel.add(new JLabel("Word:"));
        inputPanel.add(addWordField);
        inputPanel.add(new JLabel("Meaning(s):"));
        inputPanel.add(meaningScroll);
        inputPanel.add(new JLabel(""));
        inputPanel.add(addWordButton);

        panel.add(inputPanel, BorderLayout.CENTER);

        addWordButton.addActionListener(e -> addWord(addWordField.getText(), meaningArea.getText()));

        return panel;
    }

    /**
     * Creates the remove word panel for the GUI
     */
    private JPanel createRemoveWordPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new TitledBorder("Remove Word"));

        JTextField removeWordField = new JTextField();
        removeWordButton = new JButton("Remove");

        panel.add(new JLabel("Word:"), BorderLayout.WEST);
        panel.add(removeWordField, BorderLayout.CENTER);
        panel.add(removeWordButton, BorderLayout.EAST);

        removeWordButton.addActionListener(e -> removeWord(removeWordField.getText()));

        return panel;
    }

    /**
     * Creates the update panel for the GUI
     */
    private JPanel createUpdatePanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new TitledBorder("Update Operations"));

        JPanel operationsPanel = new JPanel(new GridLayout(3, 2, 5, 5));

        JTextField updateWordField = new JTextField();
        existingMeaningField = new JTextField();
        newMeaningField = new JTextField();

        addMeaningButton = new JButton("Add Meaning");
        updateMeaningButton = new JButton("Update Meaning");

        operationsPanel.add(new JLabel("Word:"));
        operationsPanel.add(updateWordField);
        operationsPanel.add(new JLabel("Existing Meaning:"));
        operationsPanel.add(existingMeaningField);
        operationsPanel.add(new JLabel("New Meaning:"));
        operationsPanel.add(newMeaningField);

        JPanel buttonPanel = new JPanel(new FlowLayout());
        buttonPanel.add(addMeaningButton);
        buttonPanel.add(updateMeaningButton);

        panel.add(operationsPanel, BorderLayout.CENTER);
        panel.add(buttonPanel, BorderLayout.SOUTH);

        addMeaningButton.addActionListener(e -> addMeaning(updateWordField.getText(), newMeaningField.getText()));

        updateMeaningButton.addActionListener(e -> updateMeaning(updateWordField.getText(),
                existingMeaningField.getText(),
                newMeaningField.getText()));

        return panel;
    }

    /**
     * Creates the result panel for the GUI
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
     * Sends a request to the server without any meanings parameter
     * @param word word to be requested
     * @param operation operation to be performed
     */
    private void sendRequestToServer(String word, String operation) {
        // Default with no meanings
        sendRequestToServer(word, operation, null);
    }

    /**
     * Sends a request to the server with extra meanings parameters
     * @param word word to be requested
     * @param operation operation to be performed
     * @param meanings word meanings to be added
     */
    private void sendRequestToServer(String word, String operation, String meanings) {
        // Call with meanings
        sendRequestToServer(word, operation, null, meanings);
    }

    /**
     * Sends a request to the server with extra meanings parameters
     * @param word word to be requested
     * @param operation operation to be performed
     * @param existingMeaning existing meaning to be removed
     * @param meanings new meaning to be added
     */
    private void sendRequestToServer(String word, String operation, String existingMeaning, String meanings) {
        try {
            // Package request into JSON object
            JSONObject json = new JSONObject();
            json.put("word", word);
            json.put("operation", operation);
            json.put("sleepDuration", sleepDuration);

            // If meanings are provided, include them in the JSON object
            if (existingMeaning != null) {
                json.put("existingMeaning", existingMeaning);
            }

            // If existing meanings are provided, include them in the JSON object
            if (meanings != null) {
                json.put("meanings", meanings);
            }

            // Send JSON object to server
            out.write(json + "\n");
            out.flush();
        } catch (IOException | NullPointerException e) {
            displayResult("ERROR: " + e.getMessage());
        }
    }

    /**
     * Search for a word in the dictionary
     */
    private void searchWord() {
        // Checks to see if word field is empty
        String word = wordField.getText().trim();
        if (word.isEmpty()) {
            displayResult("ERROR: Please enter a word to search.");
            return;
        }

        // Send search request to server
        sendRequestToServer(word, "Search");
        displayResult("CLIENT: Searching dictionary server for definition of word \"" + word + "\"");
    }

    /**
     * Add a new word with meanings to the dictionary
     * @param word word to be requested
     * @param meanings new meaning to be added
     */
    private void addWord(String word, String meanings) {
        // Checks to see if word field or meanings field is empty
        if (word.trim().isEmpty() || meanings.trim().isEmpty()) {
            displayResult("ERROR: Both word and meaning(s) are required.");
            return;
        }

        // Send add word request to server
        sendRequestToServer(word, "Add", meanings);
        displayResult("CLIENT: Adding word \"" + word + " and meanings \"" + meanings.replace('\n', ',') + "\" to dictionary server");
    }

    /**
     * Remove a word from the dictionary
     * @param word word to be requested
     */
    private void removeWord(String word) {
        // Checks to see if word field is empty
        if (word.trim().isEmpty()) {
            displayResult("ERROR: Please enter a word to remove.");
            return;
        }

        // Send remove word request to server
        sendRequestToServer(word, "Remove");
        displayResult("CLIENT: Removing word \"" + word + "\" from dictionary server" );
    }

    /**
     * Add a new meaning to an existing word
     * @param word word to be requested
     * @param newMeaning new meaning to be added
     */
    private void addMeaning(String word, String newMeaning) {
        // Checks to see if word field or new meaning field is empty
        if (word.trim().isEmpty() || newMeaning.trim().isEmpty()) {
            displayResult("ERROR: Both word and new meaning are required.");
            return;
        }

        // Send add meaning request to server
        sendRequestToServer(word, "Meaning", newMeaning);
        displayResult("CLIENT: Adding meaning \"" + newMeaning + "\" to word " + word + "\" to dictionary server");
    }

    /**
     * Update an existing meaning of a word
     * @param word word to be requested
     * @param existingMeaning existing meanings to be removed
     * @param newMeaning new meaning to be added
     */
    private void updateMeaning(String word, String existingMeaning, String newMeaning) {
        // Checks to see if word field, existing meaning field, or new meaning field is empty
        if (word.trim().isEmpty() || existingMeaning.trim().isEmpty() || newMeaning.trim().isEmpty()) {
            displayResult("ERROR: Word, existing meaning, and new meaning are all required.");
            return;
        }

        // Sends update request to server
        sendRequestToServer(word, "Update", existingMeaning, newMeaning);
        displayResult("CLIENT: Updating meaning of word \"" + word + "\" from existing meaning \"" + existingMeaning + "\" to new meaning \"" + newMeaning + "\" on dictionary server");
    }

    /**
     * Display result in the result area
     * @param result string to be displayed
     */
    private void displayResult(String result) {
        // Time is rounded to milliseconds for neater appearance
        resultArea.append(java.time.LocalTime.now().truncatedTo(ChronoUnit.MILLIS) + ": " + result + "\n");
        resultArea.setCaretPosition(resultArea.getDocument().getLength());
    }

    /**
     * Update connection status when connection status changes
     * @param connected connection status
     */
    public void setConnectionStatus(boolean connected) {
        if (connected) {
            statusLabel.setText("Connected");
            statusLabel.setForeground(Color.GREEN);
            connectButton.setText("Disconnect");
        } else {
            statusLabel.setText("Not Connected");
            statusLabel.setForeground(Color.RED);
            connectButton.setText("Connect");
        }

        // Enable/disable buttons based on connection status
        searchButton.setEnabled(connected);
        addWordButton.setEnabled(connected);
        removeWordButton.setEnabled(connected);
        addMeaningButton.setEnabled(connected);
        updateMeaningButton.setEnabled(connected);
    }

    /**
     * Main driver method for the Dictionary Client GUI
     */
    public static void main(String[] args) {
        // Parse command line arguments
        // Expected: java DictionaryClient.jar <server-address> <server-port> <sleep-duration>
        if (args.length != 3) {
            System.out.println("Expected: java DictionaryClient.jar <server-address> <server-port> <sleep-duration>");
            return;
        }

        serverAddress = args[0];

        // Check valid port
        try {
            port = Integer.parseInt(args[1]);
            if (port < 1 || port > 65535) {
                System.out.println("ERROR: Port number out of range");
                return;
            }
        } catch (NumberFormatException e) {
            System.out.println("ERROR: Port number must be a valid integer");
            return;
        }

        // Check valid sleep duration
        try {
            sleepDuration = Integer.parseInt(args[2]);
            if (sleepDuration < 0) {
                System.out.println("ERROR: Sleep duration is out of range");
                return;
            }
        } catch (NumberFormatException e) {
            System.out.println("ERROR: Sleep duration must be a valid integer");
            return;
        }

        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                System.out.println(e.getMessage());
            }
            DictionaryClientGUI gui = new DictionaryClientGUI();
            gui.setVisible(true);
            gui.setConnectionStatus(false);
        });
    }
}