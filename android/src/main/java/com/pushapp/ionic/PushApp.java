package com.pushapp.ionic;

import com.getcapacitor.Logger;

public class PushApp {

    public String echo(String value) {
        Logger.info("Echo", value);
        return value;
    }
}
