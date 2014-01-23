package com.samlanning.tools.cdst.tests;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import com.samlanning.tools.cdst.CDSTException;
import com.samlanning.tools.cdst.CDSTHandler;
import com.samlanning.tools.cdst.CDSTester;

public class SetupTest {

    @Before
    public void setUp() throws Exception {
    }

    @Test
    public void testNoParameters() {
        CDSTester<String, String> t = new CDSTester<String, String>();
        
        try {
            t.run();
            fail("Didn't Raise Exception");
        } catch (Exception e) {
        }
    }

    @Test
    public void testTooManyHandlers() throws CDSTException {
        CDSTester<String, String> t = new CDSTester<String, String>();
        
        t.setHandler(new CDSTHandler<String>(){
            public void fail(String message) {}
            public void writeToStream(String input) {}
        });
        
        try {
            t.setHandler(new CDSTHandler<String>(){
                public void fail(String message) {}
                public void writeToStream(String input) {}
            });
            fail("Didn't Raise Exception");
        } catch (Exception e) {
        }
    }

}
