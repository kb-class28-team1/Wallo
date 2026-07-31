package com.wallo.asset.exception;

import com.wallo.common.exception.CustomException;
import com.wallo.common.exception.ErrorCode;

public class InvalidDashboardRequestException extends CustomException {

    public InvalidDashboardRequestException() {
        super(ErrorCode.INVALID_DASHBOARD_REQUEST);
    }
}
