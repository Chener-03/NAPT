package xyz.chener.ext.napt.server.entity;

import lombok.Getter;

@Getter
public enum RequestNtType {
    TCP(1),UDP(2);


    private final Integer code;

    RequestNtType(Integer code){
        this.code = code;
    }


}
