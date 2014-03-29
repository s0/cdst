package com.samlanning.tools.cdst;

/**
 * Implement this to be able to read objects on demand rather than
 * pre-define what you expect to see.
 * @author Sam Lanning <sam@samlanning.com>
 *
 * @param <OutputType>
 */
public interface CDSTReadHandler<OutputType> {
    
    /**
     * Test the output, and return true if it is valid
     * @param output
     * @return
     */
    public boolean read(OutputType output);
}