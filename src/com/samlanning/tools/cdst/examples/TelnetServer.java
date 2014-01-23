package com.samlanning.tools.cdst.examples;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

import com.samlanning.tools.cdst.CDSTException;
import com.samlanning.tools.cdst.CDSTHandler;
import com.samlanning.tools.cdst.CDSTester;

/**
 * This example program listens on a specific port for a single connection
 * which it then tests with CDSTester.
 * 
 * Run it and telnet to port 9999
 * @author sam
 *
 */
public class TelnetServer {

    public static final int PORT = 9999;
    
    public static Socket socket;
    public static BufferedReader out;
    public static PrintWriter in;
    
    public static CDSTester<String, String> tester;
    
    // Launcher
    public static void main(String[] args) throws Exception {
        
        ServerSocket server = new ServerSocket(PORT);
        
        // Wait for a single connection
        System.out.println("Listening");
        socket = server.accept();
        // Close immediately to accept only one connection
        server.close();
        System.out.println("Got Connection");
        
        // Setup Duplex Stream
        out = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        in = new PrintWriter(socket.getOutputStream(), true);
        
        // Initial Message
        in.println("Starting Telnet CDST Test");
        in.println("Try interacting with the console to see how each of the");
        in.println("different tests are validated...");
        
        // Setup the tester with 2 second delays before writes
        tester = new CDSTester<String, String>(2000);
        
        setupTester();
        
        // Start the thread accepting input
        (new OutputListener()).start();
        
        tester.run();
        
        server.close();
        socket.close();
    }
    
    public static void setupTester() throws CDSTException {
        
        tester.setHandler(new Handler());
        tester.setLogLevel(CDSTester.L_ALL);
        
        tester.addInputWrite("Hello");
        tester.addInputWrite("How are you today?");
        
        tester.addOutputRead("Good");
        
        tester.addInputWrite("Great");
        
        tester.addOutputRead("Yourself?");
        
        tester.addInputWrite("Great, thanks for asking!");
        
    }
    
    public static class TCPMessage {
        //TODO
    }
    
    public static class Handler implements CDSTHandler<String> {

        @Override
        public void fail(String message) {
            System.out.println("Error: " + message);
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void writeToStream(String input) {
            in.println(input);
        }
    }
    
    /**
     * Thread class to listen to the output from the socket (stream)
     * @author sam
     *
     */
    public static class OutputListener extends Thread {
        public void run(){
            while(true){
                try {
                    tester.readFromStream(out.readLine());
                } catch (Exception e) {
                    System.out.println("Socket Closed");
                    System.exit(1);
                }
            }
        }
    }
}
