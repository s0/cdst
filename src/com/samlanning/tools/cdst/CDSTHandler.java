package com.samlanning.tools.cdst;

public interface CDSTHandler<InputType> {

    /**
     * Handle the tester failing with a specific message.
     * @param message
     */
    public void fail(String message);
    
    /**
     * Fulfil a request from the tester to send input to the input stream.
     * @param input
     */
    public void writeToStream(InputType input);
    
}
