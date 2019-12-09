package com.stylefeng.guns.core.exception;


public enum RespExceptionEnum implements ServiceExceptionEnum {

    SYSTEM_ERROR(999, "系统繁忙"),
    PROMO_ORDER_ERROR(1, "下单失败");

    RespExceptionEnum(int code, String message) {
        this.code = code;
        this.message = message;
    }

    private Integer code;

    private String message;

    @Override
    public Integer getCode() {
        return code;
    }

    public void setCode(Integer code) {
        this.code = code;
    }

    @Override
    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
