package xyz.chener.ext.napt.server.entity;

/**
 * @Author: chenzp
 * @Date: 2023/05/22/09:47
 * @Email: chen@chener.xyz
 */
public class DataFrameCode {

    // 授权
    public static final int ACCESS = 1;

    // 强制授权 踢出已登录
    public static final int ACCESS_FORCE = 0;

    // 授权失败 或者异常
    public static final int ACCESS_FAIL = 2;

    // 授权成功

    public static final int ACCESS_SUCCESS = 3;

    // 授权过多
    public static final int ACCESS_TOO_MANY = 4;


    // 服务端主动关闭
    public static final int CLOSE = 1000;



    // 服务端 端口连接关闭通知
    public static final int REMOTE_CHANNEL_CLOSE = 9;


    // 服务端 端口接收到目标数据转发至客户端
    public static final int REMOTE_CHANNEL_ACCEPT = 5;

    // 客户端 发送数据至目标
    public static final int CLIENT_CHANNEL_ACCEPT = 6;


    // 服务端 启动端口代理类报错
    public static final int REMOTE_PORT_START_ERROR = 15;


    // 限制流量
    public static final int CLIENT_FLOW_LIMIT = 16;


}
