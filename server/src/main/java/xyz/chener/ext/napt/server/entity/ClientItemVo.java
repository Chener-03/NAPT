package xyz.chener.ext.napt.server.entity;

/**
 * @Author: chenzp
 * @Date: 2023/05/25/16:19
 * @Email: chen@chener.xyz
 */
public class ClientItemVo extends ClientItem {

    private boolean online;

    private Long in;

    private Long out;

    public boolean isOnline() {
        return online;
    }

    public void setOnline(boolean online) {
        this.online = online;
    }

    public Long getIn() {
        return in;
    }

    public void setIn(Long in) {
        this.in = in;
    }

    public Long getOut() {
        return out;
    }

    public void setOut(Long out) {
        this.out = out;
    }
}
