package com.samlanning.tools.cdst;

public class CDSTException extends Exception {

    public CDSTException(String msg){
        super(msg);
    }
    
    public CDSTException(Exception e){
        super(e);
    }
}
