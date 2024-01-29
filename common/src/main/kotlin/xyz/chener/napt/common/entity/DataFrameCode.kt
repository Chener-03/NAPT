package xyz.chener.napt.common.entity

enum class DataFrameCode(val code: Int,val message:String) {

    /**
     * 0-100    授权相关
     * 100-999  数据交换
     * 1000+    其它
     */

    ACCESS(1,"授权"),
    ACCESS_FORCE(2,"强制授权 踢出已登录"),
    ACCESS_FAIL(3,"授权失败 或者异常"),
    ACCESS_SUCCESS(4,"授权成功"),
    ACCESS_TOO_MANY(5,"授权过多"),
    CLOSE(6,"服务端主动关闭"),
    REMOTE_CHANNEL_CLOSE_TCP(100,"服务端 TCP 端口连接关闭通知"),
    REMOTE_CHANNEL_ACCEPT_TCP(101,"服务端 端口接收到目标数据转发至客户端 TCP"),
    CLIENT_CHANNEL_ACCEPT_TCP(102,"客户端 发送数据至目标  TCP"),
    REMOTE_CHANNEL_ACCEPT_UDP(106,"服务端 端口接收到目标数据转发至客户端 UDP"),
    CLIENT_CHANNEL_ACCEPT_UDP(107,"客户端 发送数据至目标  UDP"),
    REMOTE_PORT_START_ERROR(103,"服务端 启动端口代理类报错"),
    CLIENT_FLOW_LIMIT(104,"限制流量"),
    CLIENT_CLOSE_REMOTE_CHANNEL_TCP(105,"后端映射地址主动关闭remoteChannel"),
    HEART_BEAT(1001,"心跳"),
    GET_CLIENT_CONNECTS(1002,"获取客户端连接信息"),
    RESTART_CLIENT_CONNECT(1003,"重置客户端的连接")
    ;


}