package com.wallo.asset.exception;

import com.wallo.common.exception.CustomException;
import com.wallo.common.exception.ErrorCode;

public class ConnectionNotFoundException extends CustomException {

    public ConnectionNotFoundException() {
        super(ErrorCode.CONNECTION_NOT_FOUND);
    }
}
