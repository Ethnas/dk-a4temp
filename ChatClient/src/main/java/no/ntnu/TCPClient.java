package no.ntnu;

import java.io.*;
import java.net.*;
import java.util.LinkedList;
import java.util.List;

public class TCPClient {
    private PrintWriter toServer;
    private BufferedReader fromServer;
    private Socket connection;

    // Hint: if you want to store a message for the last error, store it here
    private String lastError = null;

    private final List<ChatListener> listeners = new LinkedList<>();

    /**
     * Connect to a chat server.
     *
     * @param host host name or IP address of the chat server
     * @param port TCP port of the chat server
     * @return True on success, false otherwise
     */
    public boolean connect(String host, int port) {
        // Hint: Remember to process all exceptions and return false on error
        // Hint: Remember to set up all the necessary input/output stream variables
        boolean connected;
        try {
            connection = new Socket(host, port);
            toServer = new PrintWriter(connection.getOutputStream(), true);
            fromServer = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"));
            connected = true;
        }
        catch (IOException e) {
            connected = false;
        }
        return connected;
    }

    /**
     * Close the socket. This method must be synchronized, because several
     * threads may try to call it. For example: When "Disconnect" button is
     * pressed in the GUI thread, the connection will get closed. Meanwhile, the
     * background thread trying to read server's response will get error in the
     * input stream and may try to call this method when the socket is already
     * in the process of being closed. with "synchronized" keyword we make sure
     * that no two threads call this method in parallel.
     */
    public synchronized void disconnect() {
        // Hint: remember to check if connection is active
        if (!this.connection.isClosed()) {
            try {
                this.connection.close();
                this.onDisconnect();
            }
            catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * @return true if the connection is active (opened), false if not.
     */
    public boolean isConnectionActive() {
        boolean connectionActive;
        if (connection == null) {
            connectionActive = false;
        }
        else {
            connectionActive = true;
        }
        return connectionActive;
    }

    /**
     * Send a command to server.
     *
     * @param cmd A command. It should include the command word and optional attributes, according to the protocol.
     * @return true on success, false otherwise
     */
    private boolean sendCommand(String cmd) {
        // Hint: Remember to check if connection is active
        boolean sent;
        if (this.isConnectionActive() && !cmd.isEmpty()) {
            toServer.println(cmd);
            sent = true;
        }
        else {
            sent = false;
        }
        return sent;
    }

    /**
     * Send a public message to all the recipients.
     *
     * @param message Message to send
     * @return true if message sent, false on error
     */
    public boolean sendPublicMessage(String message) {
        // Hint: Reuse sendCommand() method
        // Hint: update lastError if you want to store the reason for the error.
        boolean sent;
        if (!message.isEmpty()) {
            String send = "msg " + message;
            this.sendCommand(send);
            sent = true;
        }
        else {
            lastError = "Message was empty string.";
            sent = false;
        }
        return sent;
    }

    /**
     * Send a login request to the chat server.
     *
     * @param username Username to use
     */
    public void tryLogin(String username) {
        // Hint: Reuse sendCommand() method
        if (!username.isEmpty()) {
            String message = "login " + username;
            this.sendCommand(message);
        }
    }

    /**
     * Send a request for latest user list to the server. To get the new users,
     * clear your current user list and use events in the listener.
     */
    public void refreshUserList() {
        // Hint: Use Wireshark and the provided chat client reference app to find out what commands the
        // client and server exchange for user listing.
        this.sendCommand("users");
    }

    /**
     * Send a private message to a single recipient.
     *
     * @param recipient username of the chat user who should receive the message
     * @param message   Message to send
     * @return true if message sent, false on error
     */
    public boolean sendPrivateMessage(String recipient, String message) {
        // Hint: Reuse sendCommand() method
        // Hint: update lastError if you want to store the reason for the error.

        boolean sent = false;
        if (recipient.isEmpty() || message.isEmpty()) {
            lastError = "Recipient or message not specified.";
        }
        else {
            String command = "privmsg " + recipient + " " + message;
            this.sendCommand(command);
            sent = true;
        }

        return sent;
    }


    /**
     * Send a request for the list of commands that server supports.
     */
    public void askSupportedCommands() {
        // Hint: Reuse sendCommand() method
        this.sendCommand("help");
    }


    /**
     * Wait for chat server's response
     *
     * @return one line of text (one command) received from the server
     */
    private String waitServerResponse() {
        String response = "";
        boolean gotResponse = false;
        while (this.isConnectionActive() && !gotResponse) {
            try {
                response = fromServer.readLine();
                if (!response.isEmpty()) {
                    gotResponse = true;
                }
            }
            catch (IOException e) {
                e.printStackTrace();
                this.disconnect();
                this.connection = null;
                this.fromServer = null;
                this.toServer = null;
            }
        }

        return response;
    }

    /**
     * Get the last error message
     *
     * @return Error message or "" if there has been no error
     */
    public String getLastError() {
        if (lastError != null) {
            return lastError;
        } else {
            return "";
        }
    }

    /**
     * Start listening for incoming commands from the server in a new CPU thread.
     */
    public void startListenThread() {
        // Call parseIncomingCommands() in the new thread.
        Thread t = new Thread(() -> {
            parseIncomingCommands();
        });
        t.start();
    }

    /**
     * Read incoming messages one by one, generate events for the listeners. A loop that runs until
     * the connection is closed.
     */
    private void parseIncomingCommands() {
        while (isConnectionActive()) {
            // Hint: Reuse waitServerResponse() method
            // Hint: Have a switch-case (or other way) to check what type of response is received from the server
            // and act on it.
            // Hint: In Step 3 you need to handle only login-related responses.
            // Hint: In Step 3 reuse onLoginResult() method
            String[] commandToParse = this.waitServerResponse().split(" ", 2);
            switch (commandToParse[0]) {
                case "loginok":
                    this.onLoginResult(true, " ");
                    break;

                case "loginerr":
                    this.onLoginResult(false, commandToParse[1]);
                    break;

                case "users":
                    String[] users = commandToParse[1].split(" ");
                    this.onUsersList(users);
                    break;

                case "msg":
                    String[] msgParts = commandToParse[1].split(" ", 2);
                    this.onMsgReceived(false, msgParts[0], msgParts[1]);
                    break;

                case "privmsg":
                    String[] privMsgParts = commandToParse[1].split(" ", 2);
                    this.onMsgReceived(true, privMsgParts[0], privMsgParts[1]);
                    break;

                case "msgerr":
                    this.onMsgError(commandToParse[1]);
                    break;

                case "cmderror":
                    this.onCmdError(commandToParse[1]);
                    break;

                case "supported":
                    String[] supportedCommands = commandToParse[1].split(" ");
                    this.onSupported(supportedCommands);
                    break;

                default:
                    this.onCmdError("The response from the server could not be recognized.");
            }
        }
    }

    /**
     * Register a new listener for events (login result, incoming message, etc)
     *
     * @param listener
     */
    public void addListener(ChatListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    /**
     * Unregister an event listener
     *
     * @param listener
     */
    public void removeListener(ChatListener listener) {
        listeners.remove(listener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////////////////////
    // The following methods are all event-notificators - notify all the listeners about a specific event.
    // By "event" here we mean "information received from the chat server".
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Notify listeners that login operation is complete (either with success or
     * failure)
     *
     * @param success When true, login successful. When false, it failed
     * @param errMsg  Error message if any
     */
    private void onLoginResult(boolean success, String errMsg) {
        for (ChatListener l : listeners) {
            l.onLoginResult(success, errMsg);
        }
    }

    /**
     * Notify listeners that socket was closed by the remote end (server or
     * Internet error)
     */
    private void onDisconnect() {
        // Hint: all the onXXX() methods will be similar to onLoginResult()
        for (ChatListener l : listeners) {
            l.onDisconnect();
        }
    }

    /**
     * Notify listeners that server sent us a list of currently connected users
     *
     * @param users List with usernames
     */
    private void onUsersList(String[] users) {
        for (ChatListener l : listeners) {
            l.onUserList(users);
        }
    }

    /**
     * Notify listeners that a message is received from the server
     *
     * @param priv   When true, this is a private message
     * @param sender Username of the sender
     * @param text   Message text
     */
    private void onMsgReceived(boolean priv, String sender, String text) {
        for (ChatListener l : listeners) {
            l.onMessageReceived(new TextMessage(sender, priv, text));
        }
    }

    /**
     * Notify listeners that our message was not delivered
     *
     * @param errMsg Error description returned by the server
     */
    private void onMsgError(String errMsg) {
        for (ChatListener l : listeners) {
            l.onMessageError(errMsg);
        }
    }

    /**
     * Notify listeners that command was not understood by the server.
     *
     * @param errMsg Error message
     */
    private void onCmdError(String errMsg) {
        for (ChatListener l : listeners) {
            l.onCommandError(errMsg);
        }
    }

    /**
     * Notify listeners that a help response (supported commands) was received
     * from the server
     *
     * @param commands Commands supported by the server
     */
    private void onSupported(String[] commands) {
        for (ChatListener l : listeners) {
            l.onSupportedCommands(commands);
        }
    }
}
