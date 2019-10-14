package main.java.no.ntnu.datakomm;

import java.io.*;
import java.net.Socket;

public class ClientHandler extends Thread {
    private final Socket clientSocket;

    public ClientHandler(Socket clientSocket) {
        this.clientSocket = clientSocket;
    }

    @Override
    public void run() {
        while (!clientSocket.isClosed()) {
            String request = readRequestFromClient();
            if (request != null) {
                parseRequest(request);
            }
            else {
                sendResponseToClient("error");
            }
        }
    }

    /**
     * Reads the request from the client.
     * @return the request form the client as a string.
     */
    private String readRequestFromClient() {
        String response;
        try {
            InputStream in = clientSocket.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(in));
            response = reader.readLine().trim();
        }
        catch (IOException e) {
            System.out.println("Socket response error: " + e.getMessage());
            response = null;
        }
        return response;
    }

    /**
     * Parses the request from the client.
     * @param request the request fro the client.
     */
    private void parseRequest(String request) {
        if (request.equals("game over")) {
            try {
                clientSocket.close();
            }
            catch (IOException e) {
                e.printStackTrace();
            }
        }
        else {
            String[] requests = request.split("\\+");
            try {
                int number1 = Integer.parseInt(requests[0]);
                int number2 = Integer.parseInt(requests[1]);
                addNumbers(number1, number2);
            }
            catch (NumberFormatException e) {
                sendResponseToClient("error");
            }
        }
    }

    /**
     * Adds the numbers together and passes them on
     * @param num1 the first number
     * @param num2 the second number
     */
    private void addNumbers(int num1, int num2) {
        int sum = num1 + num2;
        sendResponseToClient(Integer.toString(sum));
    }

    /**
     * Sends a response to the client.
     * @param response the response to the client.
     */
    private void sendResponseToClient(String response) {
        try {
            OutputStream out = clientSocket.getOutputStream();
            PrintWriter writer = new PrintWriter(out, true);
            writer.println(response);
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }
}
