package com.samlanning.tools.cdst;

public class CDSTException extends Exception {

    private static final long serialVersionUID = 1L;

    public CDSTException(String msg){
        super(msg);
    }
    
    public CDSTException(Exception e){
        super(e);
    }
}
