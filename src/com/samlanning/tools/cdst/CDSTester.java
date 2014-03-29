/**
 * ISC License (ISC)
 * 
 * Copyright (c) 2014, Sam Lanning <sam@samlanning.com>
 * 
 * Permission to use, copy, modify, and/or distribute this software for any
 * purpose with or without fee is hereby granted, provided that the above
 * copyright notice and this permission notice appear in all copies.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES WITH
 * REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF MERCHANTABILITY
 * AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY SPECIAL, DIRECT,
 * INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES WHATSOEVER RESULTING FROM
 * LOSS OF USE, DATA OR PROFITS, WHETHER IN AN ACTION OF CONTRACT, NEGLIGENCE OR
 * OTHER TORTIOUS ACTION, ARISING OUT OF OR IN CONNECTION WITH THE USE OR
 * PERFORMANCE OF THIS SOFTWARE.
 */

package com.samlanning.tools.cdst;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.NoSuchElementException;
import java.util.concurrent.Semaphore;

/**
 * Tool which allows you to test arbitrary duplex "streams" that run over
 * multiple threads.
 * 
 * You specify a way for the tester to send input to the stream, and to handle
 * failures, and then you can specify a synchronous list of "communications"
 * to test the stream.
 * 
 * @author Sam Lanning <sam@samlanning.com>
 *
 */
public class CDSTester<InputType, OutputType> {
    
    // Log Levels
    // Logs are written to stdout
    public static final int L_NONE = 0x0;
    public static final int L_INFO = 0x1;
    public static final int L_INPUT = 0x2;
    public static final int L_OUTPUT = 0x4;
    public static final int L_INTERNALS = 0x8;
    
    public static final int L_INTERACTION = L_INPUT | L_OUTPUT;
    public static final int L_ALL = L_INFO | L_INPUT | L_OUTPUT | L_INTERNALS;
    
    private int logLevel = L_NONE;

    /**
     * How long should the tester wait before writing to the input to try and
     * catch out invalid outputs from the stream?
     * 
     * In milliseconds
     */
    private long writeDelay = 20;

    private Semaphore lock = new Semaphore(1);
    private Semaphore readWait = new Semaphore(1);
    
    /**
     * Current state of the test
     */
    private TesterState state = TesterState.PREPARING;
    
    /**
     * Handler to forward requests on to
     */
    private CDSTHandler<InputType> handler = null;
    
    /**
     * List of Communications which should take place
     */
    private LinkedList<Communication> comms = new LinkedList<Communication>();
    
    /**
     * Iterator used during testing
     */
    private Iterator<Communication> iter;
    
    private Communication nextExpectedComm;
    
    /**
     * Start a tester with a specific delay before writing to stream input
     * (see CDSTester.writeDelay)
     * 
     * @param writeDelay
     */
    public CDSTester(long writeDelay){
        this();
        this.writeDelay = writeDelay;
    }
    
    public CDSTester(){
        try {
            this.lock.acquire();
            this.readWait.acquire();
            // Have Baton
            // this.lock: 0
            // this.readWait: 0
        } catch (InterruptedException e) {
        }
    }
    
    /**
     * Setup the correct handler for this tester
     * @param handler
     */
    public void setHandler(CDSTHandler<InputType> handler) throws CDSTException {
        
        this.assertPreparing();
        
        if(this.handler != null)
            throw new CDSTException("Already set Handler");
        
        this.handler = handler;
    }
    
    public void setLogLevel(int logLevel){
        this.logLevel = logLevel;
    }
    
    // ***************
    // Methods used to build up list of communications
    
    /**
     * Tell the tester to expect some output from the stream at this point.
     * @param object
     * @throws CDSTException 
     */
    public void addOutputRead(OutputType object) throws CDSTException {
        
        this.assertPreparing();
        comms.add(new Communication(CommunicationType.OUTPUT, object));
    }
    
    /**
     * Tell the tester to write to the stream at this point.
     * @param object
     * @throws CDSTException 
     */
    public void addInputWrite(InputType object) throws CDSTException {
        
        this.assertPreparing();
        comms.add(new Communication(CommunicationType.INPUT, object));
    }
    
    /**
     * Tell the tester to expect some output from the stream at this point, and
     * pass the object to the handler.
     * @param handler
     * @throws CDSTException 
     */
    public void addOutputRead(CDSTReadHandler<OutputType> handler)
            throws CDSTException {
        
        this.assertPreparing();
        comms.add(new Communication(handler));
    }
    
    /**
     * Tell the tester to write to the stream at this point, using a handler.
     * @param handler
     * @throws CDSTException 
     */
    public void addInputWrite(CDSTWriteHandler<InputType> handler)
            throws CDSTException {
        
        this.assertPreparing();
        comms.add(new Communication(handler));
    }
    
    // End
    // ***************
    
    // ***************
    // Methods used to communicate with the tester during testing
    
    /**
     * Tell the tester that there has been output received from the stream.
     * @param object
     * @throws InterruptedException 
     */
    public void readFromStream(OutputType object) throws CDSTException {
        this.doRead(object);
    }
    
    // End
    // ***************
    
    
    /**
     * Run the test
     * @throws CDSTException 
     */
    public void run() throws CDSTException {
        
        this.assertPreparing();
        
        this.log("Running", CDSTester.L_INFO);

        // Have Baton
        // this.lock: 0
        // this.readWait: 0
        
        if(this.handler == null)
            throw new CDSTException("Didn't set Handler");
        
        this.doRun();
    }
    
    // ***************
    // Internal methods used to actually run the test
    
    private void doRun() throws CDSTException {
        // Have Baton
        // this.lock: 0
        // this.readWait: 0
        
        this.state = TesterState.RUNNING;
        this.iter = this.comms.iterator();
        
        this.doLoop();
        
    }
    
    private void doRead(OutputType object) throws CDSTException {
        
        this.log("Read: " + object.toString(), CDSTester.L_OUTPUT);
        
        // this.lock: 1 -> 0
        // this.readWait: 0 -> 0
        this.acquire(this.lock);
        
        if(this.state == TesterState.STOPPED){
            // Don't need to return transfer back to main thread, only to self
            // this.lock: 0 -> 1
            // this.readWait: 0 -> 0
            this.release(this.lock);
            throw new CDSTException("Already Stopped Testing");
        }
        
        // Have lock
        if(!this.nextExpectedComm.isOutput()){
            // Have received output when not supposed to
            this.handler.fail("Received unexpected output from stream, was " +
                              "going to input: " +
                              this.nextExpectedComm.getInput().toString() +
                              " after delay, but instead received output: " + 
                              object.toString());
            
            // Stop testing
            this.state = TesterState.STOPPED;
            
            // Main Thread for testing is expecting this.lock to be released
            // and not waiting on this.readWait
            // this.lock: 0 -> 1
            // this.readWait: 0 -> 0
            this.release(this.lock);
        } else {
            // An output is expected, lets check it is the correct output

            if(!this.nextExpectedComm.isOutput(object)){
                // Invalid Output!
                
                if(this.nextExpectedComm.output != null)
                
                    // Have received output when not supposed to
                    this.handler.fail("Received incorrect output from " +
                                      "stream, was expecting: " +
                                      this.nextExpectedComm.output.toString() +
                                      " but instead received: " + 
                                      object.toString());
                
                else
                    
                    // Have received output when not supposed to
                    this.handler.fail("Received incorrect output from " +
                                      "stream, check handled by: " +
                                      this.nextExpectedComm.outputHandler +
                                      " but received: " + 
                                      object.toString());
                
                // Stop testing
                this.state = TesterState.STOPPED;
            }
            
            // Pass the Baton
            // this.lock: 0 -> 0
            // this.readWait: 0 -> 1
            this.release(this.readWait);
        }
    }
    
    private void doLoop() throws CDSTException {
        // Have Baton
        // this.lock: 0
        // this.readWait: 0
        
        while(true){
            
            this.log("Next Communication...", CDSTester.L_INTERNALS);
            
            try {
                this.nextExpectedComm = this.iter.next();
            } catch (NoSuchElementException e) {
                this.state = TesterState.STOPPED;
                this.log("Finished (success)", CDSTester.L_INFO);
                // Release Baton
                this.release(this.lock);
                return;
            }
            
            this.log("... is: " + this.nextExpectedComm.toString(),
                     CDSTester.L_INTERNALS);
            
            // Inspect what the next communication is
            if(this.nextExpectedComm.isInput()){
                // Need to send input to stream, first wait to see if an invalid
                // output will be sent first.

                // this.lock: 0 -> 1
                // this.readWait: 0 -> 0
                this.release(this.lock);
                this.sleep(this.writeDelay);
                // this.lock: 1 -> 0
                // this.readWait: 0 -> 0
                this.acquire(this.lock);
                
                // Check that we are still running
                if(this.state == TesterState.STOPPED){
                    // this.lock: 0 -> 1
                    // this.readWait: 0 -> 0
                    this.release(this.lock);
                    return;
                }
                
                // Now send input
                this.log("Writing: " + this.nextExpectedComm.getInput(),
                         CDSTester.L_INPUT);
                this.handler.writeToStream(this.nextExpectedComm.getInput());
                
                // And now loop back for next communication
                
            } else {
                // Next communication is an output, so we expect to have
                // readFromStream called next
                
                // transfer control to that thread and wait for transfer back
                
                // Pass the Baton
                // this.lock: 0 -> 1
                // this.readWait: 0 -> 0
                this.release(this.lock);
                
                // Wait for transfer back
                // this.lock: 0 -> 0
                // this.readWait: 1 -> 0
                this.acquire(this.readWait);
                
                // Check that we are still running
                if(this.state == TesterState.STOPPED){
                    // this.lock: 0 -> 1
                    // this.readWait: 0 -> 0
                    this.release(this.lock);
                    return;
                }
                
                // Now loop round again
                
            }
        }
        
    }
    
    // End
    // ***************
    
    // ***************
    // Helper Methods
    
    /**
     * Acquire the lock on the mutex
     * @throws CDSTException 
     */
    
    private void acquire(Semaphore s) throws CDSTException {
        try {
            s.acquire();
        } catch (InterruptedException e) {
            throw new CDSTException(e);
        }
    }
    
    private void release(Semaphore s) {
        s.release();
    }
    
    private void assertPreparing() throws CDSTException {
        if(this.state != TesterState.PREPARING)
            throw new CDSTException("Already run, can't perform action.");
    }
    
    private void sleep(long milliseconds) throws CDSTException{
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {
            throw new CDSTException(e);
        }
    }
    
    /**
     * Log something at a specific log level
     * @param msg
     * @param logLevel
     */
    private void log(String msg, int logLevel){
        if((logLevel & this.logLevel) > 0)
            System.out.println("[CDST] " + msg);
    }
    
    // End
    // ***************
    
    private enum TesterState {
        PREPARING,
        RUNNING,
        STOPPED
    }
    
    private class Communication {
        private InputType input;
        private OutputType output;
        private CDSTWriteHandler<InputType> inputHandler;
        private InputType inputHandlerCache;
        private CDSTReadHandler<OutputType> outputHandler;
        
        @SuppressWarnings("unchecked")
        public Communication(CommunicationType type, Object object){
            switch(type){
            case INPUT:
                this.input = (InputType) object;
                break;
            case OUTPUT:
                this.output = (OutputType) object;
                break;
            }
        }
        
        public Communication(CDSTWriteHandler<InputType> handler){
            this.inputHandler = handler;
        }
        
        public Communication(CDSTReadHandler<OutputType> handler){
            this.outputHandler = handler;
        }
        
        public boolean isInput(){
            return this.input != null || this.inputHandler != null;
        }
        
        public InputType getInput(){
            if(this.input != null)
                return this.input;
            else
                if(this.inputHandlerCache != null)
                    return this.inputHandlerCache;
                else
                    return this.inputHandlerCache = this.inputHandler.write();
            
        }
        
        public boolean isOutput(){
            return this.output != null || this.outputHandler != null;
        }
        
        public boolean isOutput(OutputType object){
            if(this.output != null)
                return this.output.equals(object);
            else
                return this.outputHandler.read(object);
        }
        
        public String toString(){
            if(this.input != null)
                return "INPUT (" + this.input.toString() + ")";
            else if(this.inputHandler != null)
                return "INPUT (" + this.inputHandler.toString() + ")";
            else if(this.output != null)
                return "OUTPUT (" + this.output.toString() + ")";
            else
                return "INPUT (" + this.outputHandler.toString() + ")";
                
        }
    }
    
    private enum CommunicationType {
        INPUT, OUTPUT
    }
}
