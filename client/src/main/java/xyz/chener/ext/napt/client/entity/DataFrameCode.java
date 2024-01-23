package xyz.chener.ext.napt.client.entity;

/**
 * @Author: chenzp
 * @Date: 2023/05/22/09:47
 * @Email: chen@chener.xyz
 */
public class DataFrameCode {

    /**
     * 0-100    授权相关
     * 100-999  数据交换
     * 1000+    其它
     */

    // 授权
    public static final int ACCESS = 1;

    // 强制授权 踢出已登录
    public static final int ACCESS_FORCE = 2;

    // 授权失败 或者异常
    public static final int ACCESS_FAIL = 3;

    // 授权成功

    public static final int ACCESS_SUCCESS = 4;

    // 授权过多
    public static final int ACCESS_TOO_MANY = 5;


    // 服务端主动关闭
    public static final int CLOSE = 6;



    // 服务端 端口连接关闭通知
    public static final int REMOTE_CHANNEL_CLOSE = 100;


    // 服务端 端口接收到目标数据转发至客户端 TCP
    public static final int REMOTE_CHANNEL_ACCEPT_TCP = 101;

    // 客户端 发送数据至目标  TCP
    public static final int CLIENT_CHANNEL_ACCEPT_TCP = 102;

    // 服务端 端口接收到目标数据转发至客户端 UDP
    public static final int REMOTE_CHANNEL_ACCEPT_UDP = 106;

    // 客户端 发送数据至目标  UDP
    public static final int CLIENT_CHANNEL_ACCEPT_UDP = 107;




    // 服务端 启动端口代理类报错
    public static final int REMOTE_PORT_START_ERROR = 103;


    // 限制流量
    public static final int CLIENT_FLOW_LIMIT = 104;


    // 后端映射地址主动关闭remoteChannel
    public static final int CLIENT_CLOSE_REMOTE_CHANNEL = 105;


    // 心跳
    public static final int HEART_BEAT = 1001;


    // 获取客户端连接信息
    public static final int GET_CLIENT_CONNECTS = 1002;

}
