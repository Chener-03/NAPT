package xyz.chener.ext.napt.server.core.requestNt;

import xyz.chener.ext.napt.server.entity.RequestNtType;

public abstract class RequestNt {


    protected final RequestNtType type;


    protected RequestNt(RequestNtType type) {
        this.type = type;
    }
}
