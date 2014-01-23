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
 * @author sam@samlanning.com
 *
 */
public class CDSTester<InputType, OutputType> {

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
        
        System.out.println("Running Tester");

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
        
        System.out.println("Read: " + object.toString());
        
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
                              "expecting input: " +
                              this.nextExpectedComm.input.toString() +
                              " and instead output: " + object.toString());
            
            // Stop testing
            this.state = TesterState.STOPPED;
            
            // Main Thread for testing is expecting this.lock to be released
            // and not waiting on this.readWait
            // this.lock: 0 -> 1
            // this.readWait: 0 -> 0
            this.release(this.lock);
        }
        
        // Pass the Baton
        // this.lock: 0 -> 0
        // this.readWait: 0 -> 1
        this.release(this.readWait);
    }
    
    private void doLoop() throws CDSTException {
        // Have Baton
        // this.lock: 0
        // this.readWait: 0
        
        while(true){
            System.out.println("next");
            
            try {
                this.nextExpectedComm = this.iter.next();
            } catch (NoSuchElementException e) {
                this.state = TesterState.STOPPED;
                System.out.println("Finished");
                // Release Baton
                this.release(this.lock);
                return;
            }
            
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
                    this.release(this.lock);
                    return;
                }
                
                // Now send input
                this.handler.writeToStream(this.nextExpectedComm.input);
                
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
        
        public boolean isInput(){
            return this.input != null;
        }
        
        public boolean isOutput(OutputType object){
            return this.output == object;
        }
        
        public boolean isOutput(){
            return this.output != null;
        }
    }
    
    private enum CommunicationType {
        INPUT, OUTPUT
    }
}
