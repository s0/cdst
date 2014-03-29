package com.samlanning.tools.cdst;

/**
 * Implement this to be able to provide objects on demand rather than
 * before testing.
 * @author Sam Lanning <sam@samlanning.com>
 *
 * @param <InputType>
 *
 */
public interface CDSTWriteHandler<InputType> {
    public InputType write();
}